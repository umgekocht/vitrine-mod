// Vitrine panel: upload, moderation and catalog management.
//
// Security model
// - Login only through Google (OpenID Connect, authorization code + PKCE,
//   state and nonce). The ID token is verified locally (signature, issuer,
//   audience, expiry, nonce, verified email).
// - Sessions: random 256-bit token in an HttpOnly, SameSite=Lax, __Host-
//   cookie; the database stores only its SHA-256. 7 days, ended on logout.
// - Every state-changing request needs the per-session CSRF token in a header
//   and a same-origin Origin header.
// - Moderators are the e-mail addresses in the MODERATOR_EMAILS secret,
//   re-checked on every request.
// - Uploads are checked (zip safety, manifest, ownership of the package id,
//   rate limit) before they are stored in a private R2 bucket. Nothing is
//   public until a moderator approves it; only then is the file signed with
//   the registry key and published.
// - All user text is rendered with textContent in the UI; strict CSP.

import { CATEGORIES, ZipRejected, inspectVpkg } from "./zip.js";
import {
	harden, json, parseCookies, randomToken, safeEqual, sessionCookie, sessionCookieName,
	sha256Bytes, sha256Hex, signDigest, verifyGoogleIdToken,
} from "./security.js";
import { cmpSemver, deleteEntry, nextEntry, readEntry, uploadRelease, writeEntry } from "./github.js";
import { hasValidPass, isGateExempt, makePass, verifyTurnstile } from "./gate.js";
import { NICKNAME_RE, SUGGESTION_KINDS, SUGGESTION_STATUS, cleanText, publicCatalog, publicPackage } from "./community.js";

const SESSION_SECONDS = 7 * 24 * 3600;
const UPLOADS_PER_DAY = 10;
const RESERVED_NAMESPACES = new Set(["spassglas", "vitrine", "umgekocht", "minecraft", "mojang", "microsoft", "modrinth", "fabric", "official", "admin"]);
const LICENSES = new Set(["CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "MIT", "Apache-2.0", "Unlicense"]);

const now = () => Date.now();

class HttpError extends Error {
	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

export default {
	async fetch(request, env, ctx) {
		let response;
		try {
			response = await route(request, env, ctx);
		} catch (err) {
			if (err instanceof HttpError) {
				response = json({ error: err.message }, err.status);
			} else if (err instanceof ZipRejected) {
				response = json({ error: err.message }, 422);
			} else {
				console.error(err);
				response = json({ error: "Internal error." }, 500);
			}
		}
		return harden(response);
	},
};

async function route(request, env, ctx) {
	const url = new URL(request.url);
	const path = url.pathname;
	const method = request.method;

	// Bot gate: nothing but the gate itself is reachable without a solved challenge.
	if (path === "/gate" && method === "GET") return gatePage(env);
	if (path === "/gate/verify" && method === "POST") return gateVerify(request, env);
	if (!isGateExempt(path) && !(await hasValidPass(env, parseCookies(request.headers.get("Cookie"))))) {
		if (path.startsWith("/api/") || method !== "GET") throw new HttpError(403, "Please confirm you are human first.");
		return Response.redirect(`${env.PUBLIC_URL}/gate?next=${encodeURIComponent(path)}`, 302);
	}

	if (path === "/auth/login" && method === "GET") return login(request, env);
	if (path === "/auth/callback" && method === "GET") return callback(request, env, url);
	if (path === "/auth/logout" && method === "POST") return logout(request, env);
	if (!path.startsWith("/api/")) return env.ASSETS.fetch(request);

	const session = await currentSession(request, env);
	if (method !== "GET") checkCsrf(request, env, session);

	if (path === "/api/me" && method === "GET") {
		if (!session) return json({ user: null });
		const nick = await env.DB.prepare("SELECT nickname FROM nicknames WHERE user_id = ?").bind(session.user.id).first();
		return json({ user: { ...publicUser(session.user), nickname: nick?.nickname ?? null }, moderator: isModerator(env, session.user), csrf: session.csrf });
	}

	// ---- public (no login) ----
	if (path === "/api/public/catalog" && method === "GET") return json(await publicCatalog(ctx));
	let m = path.match(/^\/api\/public\/packages\/([a-z][a-z0-9-]*(?:\.[a-z][a-z0-9-]*){1,3})$/);
	if (m && method === "GET") {
		const pkg = await publicPackage(m[1]);
		if (!pkg) throw new HttpError(404, "Package not found.");
		return json(pkg);
	}
	if (path === "/api/suggestions" && method === "GET") return listSuggestions(env, url, session);

	const user = requireUser(session);

	if (path === "/api/suggestions" && method === "POST") return createSuggestion(request, env, user);
	m = path.match(/^\/api\/suggestions\/([A-Za-z0-9_-]{10,64})\/vote$/);
	if (m && method === "POST") return voteSuggestion(env, user, m[1]);
	if (path === "/api/me/nickname" && method === "POST") {
		const body = await request.json().catch(() => ({}));
		return json({ nickname: await setNickname(env, user, body.nickname) });
	}

	if (path === "/api/uploads" && method === "POST") return upload(request, env, user);
	if (path === "/api/my/submissions" && method === "GET") {
		const rows = await env.DB.prepare(
			"SELECT id, package_id, version, name, category, status, reason, created_at, decided_at FROM submissions WHERE user_id = ? ORDER BY created_at DESC LIMIT 200",
		).bind(user.id).all();
		return json({ submissions: rows.results });
	}
	m = path.match(/^\/api\/submissions\/([A-Za-z0-9_-]{10,64})\/withdraw$/);
	if (m && method === "POST") {
		const r = await env.DB.prepare("UPDATE submissions SET status = 'withdrawn', decided_at = ? WHERE id = ? AND user_id = ? AND status = 'pending'")
			.bind(now(), m[1], user.id).run();
		if (!r.meta.changes) throw new HttpError(409, "Only your own pending uploads can be withdrawn.");
		await audit(env, user.id, "withdraw", m[1]);
		return json({ ok: true });
	}

	if (!path.startsWith("/api/mod/")) throw new HttpError(404, "Not found.");
	if (!isModerator(env, user)) throw new HttpError(403, "Moderators only.");

	if (path === "/api/mod/queue" && method === "GET") {
		const status = url.searchParams.get("status") || "pending";
		if (!["pending", "processing", "approved", "rejected", "withdrawn", "removed"].includes(status)) throw new HttpError(400, "Unknown status.");
		const rows = await env.DB.prepare(
			`SELECT s.id, s.package_id, s.version, s.name, s.category, s.description, s.license, s.author, s.capabilities, s.changelog,
			        s.sha256, s.size, s.file_count, s.status, s.reason, s.created_at, s.decided_at, s.download_url,
			        u.email AS uploader_email, u.id AS uploader_id, u.banned AS uploader_banned
			   FROM submissions s JOIN users u ON u.id = s.user_id
			  WHERE s.status = ? ORDER BY s.created_at ASC LIMIT 200`,
		).bind(status).all();
		return json({ submissions: rows.results });
	}
	m = path.match(/^\/api\/mod\/submissions\/([A-Za-z0-9_-]{10,64})\/file$/);
	if (m && method === "GET") {
		const sub = await env.DB.prepare("SELECT r2_key, package_id, version FROM submissions WHERE id = ?").bind(m[1]).first();
		if (!sub) throw new HttpError(404, "Not found.");
		const obj = await env.UPLOADS.get(sub.r2_key);
		if (!obj) throw new HttpError(404, "File is gone.");
		await audit(env, user.id, "download", m[1]);
		return new Response(obj.body, {
			headers: {
				"Content-Type": "application/octet-stream",
				"Content-Disposition": `attachment; filename="${sub.package_id}-${sub.version}.vpkg"`,
			},
		});
	}
	m = path.match(/^\/api\/mod\/submissions\/([A-Za-z0-9_-]{10,64})\/(approve|reject)$/);
	if (m && method === "POST") {
		return m[2] === "approve" ? approve(env, user, m[1]) : reject(request, env, user, m[1]);
	}
	if (path === "/api/mod/catalog" && method === "GET") return catalog(env);
	m = path.match(/^\/api\/mod\/catalog\/([a-z0-9.-]{3,160})\/delete$/);
	if (m && method === "POST") return removePackage(request, env, user, m[1]);
	m = path.match(/^\/api\/mod\/users\/([A-Za-z0-9_-]{10,64})\/(ban|unban)$/);
	if (m && method === "POST") {
		await env.DB.prepare("UPDATE users SET banned = ? WHERE id = ?").bind(m[2] === "ban" ? 1 : 0, m[1]).run();
		if (m[2] === "ban") await env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(m[1]).run();
		await audit(env, user.id, m[2], m[1]);
		return json({ ok: true });
	}
	m = path.match(/^\/api\/mod\/suggestions\/([A-Za-z0-9_-]{10,64})$/);
	if (m && method === "POST") return moderateSuggestion(request, env, user, m[1]);
	if (path === "/api/mod/audit" && method === "GET") {
		const rows = await env.DB.prepare(
			"SELECT a.action, a.target, a.detail, a.created_at, u.email AS actor FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id ORDER BY a.id DESC LIMIT 200",
		).all();
		return json({ entries: rows.results });
	}
	throw new HttpError(404, "Not found.");
}

// ---- auth -----------------------------------------------------------------

async function login(request, env) {
	await rateLimit(env, `login:${clientIp(request)}`, 20, 3600);
	const state = randomToken();
	const verifier = randomToken(48);
	const nonce = randomToken();
	await env.DB.prepare("DELETE FROM oauth_states WHERE expires_at < ?").bind(now()).run();
	await env.DB.prepare("INSERT INTO oauth_states (state, verifier, nonce, expires_at) VALUES (?, ?, ?, ?)")
		.bind(state, verifier, nonce, now() + 10 * 60 * 1000).run();
	const challenge = (await sha256Bytes(new TextEncoder().encode(verifier)));
	const params = new URLSearchParams({
		client_id: env.GOOGLE_CLIENT_ID,
		redirect_uri: `${env.PUBLIC_URL}/auth/callback`,
		response_type: "code",
		scope: "openid email profile",
		state,
		nonce,
		code_challenge: b64urlBytes(challenge),
		code_challenge_method: "S256",
		prompt: "select_account",
	});
	return Response.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params}`, 302);
}

function b64urlBytes(bytes) {
	let s = "";
	for (const x of bytes) s += String.fromCharCode(x);
	return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function callback(request, env, url) {
	await rateLimit(env, `callback:${clientIp(request)}`, 30, 3600);
	const state = url.searchParams.get("state") || "";
	const code = url.searchParams.get("code") || "";
	const row = await env.DB.prepare("SELECT verifier, nonce, expires_at FROM oauth_states WHERE state = ?").bind(state).first();
	await env.DB.prepare("DELETE FROM oauth_states WHERE state = ?").bind(state).run();
	if (!row || row.expires_at < now() || !code) return redirect(env, "/?error=login");

	const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({
			code,
			client_id: env.GOOGLE_CLIENT_ID,
			client_secret: env.GOOGLE_CLIENT_SECRET,
			redirect_uri: `${env.PUBLIC_URL}/auth/callback`,
			grant_type: "authorization_code",
			code_verifier: row.verifier,
		}),
	});
	if (!tokenRes.ok) return redirect(env, "/?error=login");
	const { id_token: idToken } = await tokenRes.json();
	let claims;
	try {
		claims = await verifyGoogleIdToken(idToken || "", { clientId: env.GOOGLE_CLIENT_ID, nonce: row.nonce });
	} catch {
		return redirect(env, "/?error=login");
	}

	let user = await env.DB.prepare("SELECT * FROM users WHERE google_sub = ?").bind(claims.sub).first();
	const email = claims.email.toLowerCase();
	const name = String(claims.name || email.split("@")[0]).slice(0, 80);
	if (!user) {
		user = { id: randomToken(16), google_sub: claims.sub, email, name, banned: 0 };
		await env.DB.prepare("INSERT INTO users (id, google_sub, email, name, created_at) VALUES (?, ?, ?, ?, ?)")
			.bind(user.id, claims.sub, email, name, now()).run();
	} else {
		await env.DB.prepare("UPDATE users SET email = ?, name = ? WHERE id = ?").bind(email, name, user.id).run();
	}
	if (user.banned) return redirect(env, "/?error=banned");

	const token = randomToken();
	await env.DB.prepare("DELETE FROM sessions WHERE expires_at < ?").bind(now()).run();
	await env.DB.prepare("INSERT INTO sessions (token_hash, user_id, csrf, expires_at, created_at) VALUES (?, ?, ?, ?, ?)")
		.bind(await sha256Hex(token), user.id, randomToken(), now() + SESSION_SECONDS * 1000, now()).run();
	await audit(env, user.id, "login", email);
	return redirect(env, "/", sessionCookie(env, token, SESSION_SECONDS));
}

function redirect(env, to, cookie) {
	const headers = { Location: `${env.PUBLIC_URL}${to}` };
	if (cookie) headers["Set-Cookie"] = cookie;
	return new Response(null, { status: 302, headers });
}

async function logout(request, env) {
	const session = await currentSession(request, env);
	if (session) {
		checkCsrf(request, env, session);
		await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(session.tokenHash).run();
	}
	return json({ ok: true }, 200, { "Set-Cookie": sessionCookie(env, "", 0) });
}

async function currentSession(request, env) {
	const token = parseCookies(request.headers.get("Cookie"))[sessionCookieName(env)];
	if (!token || token.length < 20 || token.length > 100) return null;
	const tokenHash = await sha256Hex(token);
	const row = await env.DB.prepare(
		`SELECT s.csrf, s.expires_at, u.id, u.email, u.name, u.banned
		   FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?`,
	).bind(tokenHash).first();
	if (!row || row.expires_at < now() || row.banned) return null;
	return { tokenHash, csrf: row.csrf, user: { id: row.id, email: row.email, name: row.name } };
}

function checkCsrf(request, env, session) {
	const origin = request.headers.get("Origin");
	if (origin !== new URL(env.PUBLIC_URL).origin) throw new HttpError(403, "Cross-site request blocked.");
	if (session && !safeEqual(request.headers.get("X-CSRF-Token") || "", session.csrf)) {
		throw new HttpError(403, "Missing or invalid CSRF token.");
	}
}

function requireUser(session) {
	if (!session) throw new HttpError(401, "Please sign in.");
	return session.user;
}

function isModerator(env, user) {
	const list = (env.MODERATOR_EMAILS || "").split(",").map((e) => e.trim().toLowerCase()).filter(Boolean);
	return list.includes(user.email.toLowerCase());
}

function publicUser(u) {
	return { id: u.id, email: u.email, name: u.name };
}

async function audit(env, actorId, action, target, detail = "") {
	await env.DB.prepare("INSERT INTO audit_log (actor_id, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?)")
		.bind(actorId, action, target, String(detail).slice(0, 1000), now()).run();
}

// ---- uploads ----------------------------------------------------------------

async function upload(request, env, user) {
	const length = Number(request.headers.get("Content-Length") || 0);
	if (length > 11 * 1024 * 1024) throw new HttpError(413, "Upload too large (max 10 MB).");
	const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM submissions WHERE user_id = ? AND created_at > ?")
		.bind(user.id, now() - 24 * 3600 * 1000).first();
	if (recent.n >= UPLOADS_PER_DAY) throw new HttpError(429, `At most ${UPLOADS_PER_DAY} uploads per day.`);

	const form = await request.formData();
	const file = form.get("file");
	if (!(file instanceof File)) throw new HttpError(400, "No file uploaded.");
	const bytes = new Uint8Array(await file.arrayBuffer());
	const { manifest, fileCount } = await inspectVpkg(bytes);

	const license = String(form.get("license") || manifest.license || "");
	if (!LICENSES.has(license)) throw new HttpError(422, "Please choose one of the allowed licenses.");
	const changelog = String(form.get("changelog") || "").slice(0, 2000);

	// The first part of an id ("anna" in "anna.clear-stone") is a namespace: it belongs to
	// whoever used it first, and official prefixes are reserved for moderators.
	const namespace = manifest.id.split(".")[0];
	if (RESERVED_NAMESPACES.has(namespace) && !isModerator(env, user)) {
		throw new HttpError(403, `The prefix '${namespace}.' is reserved. Use your own name as prefix.`);
	}
	const nsOwner = await env.DB.prepare("SELECT owner_id FROM packages WHERE (id = ? OR id LIKE ?) AND owner_id != ? LIMIT 1")
		.bind(namespace, `${namespace}.%`, user.id).first();
	if (nsOwner) throw new HttpError(403, `The prefix '${namespace}.' belongs to someone else. Use your own name as prefix.`);

	const owner = await env.DB.prepare("SELECT owner_id, deleted FROM packages WHERE id = ?").bind(manifest.id).first();
	if (owner && owner.owner_id !== user.id) throw new HttpError(403, `The id '${manifest.id}' belongs to someone else. Choose your own id.`);
	if (owner?.deleted) throw new HttpError(403, `The package '${manifest.id}' was removed by a moderator.`);
	if (!owner) {
		const { entry } = await readEntry(env, manifest.id).catch(() => ({ entry: null }));
		if (entry) throw new HttpError(403, `The id '${manifest.id}' is already used in the catalog.`);
	}
	const dup = await env.DB.prepare("SELECT status FROM submissions WHERE package_id = ? AND version = ? AND status IN ('pending','processing','approved')")
		.bind(manifest.id, manifest.version).first();
	if (dup) throw new HttpError(409, `Version ${manifest.version} was already submitted. Raise the version number.`);
	const latest = await env.DB.prepare("SELECT version FROM submissions WHERE package_id = ? AND status = 'approved'").bind(manifest.id).all();
	if (latest.results.some((r) => cmpSemver(r.version, manifest.version) >= 0)) {
		throw new HttpError(409, "The version must be higher than the last approved one.");
	}

	const sha256 = await sha256Hex(bytes);
	const id = randomToken(16);
	const key = `pending/${id}.vpkg`;
	await env.UPLOADS.put(key, bytes, { httpMetadata: { contentType: "application/octet-stream" }, customMetadata: { sha256 } });
	if (!owner) {
		await env.DB.prepare("INSERT INTO packages (id, owner_id, name, category, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
			.bind(manifest.id, user.id, manifest.name, manifest.category, now(), now()).run();
	}
	await env.DB.prepare(
		`INSERT INTO submissions (id, package_id, user_id, version, name, category, description, license, author, capabilities,
		                          changelog, r2_key, sha256, size, file_count, status, created_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)`,
	).bind(id, manifest.id, user.id, manifest.version, manifest.name, manifest.category, manifest.description, license,
		manifest.author, JSON.stringify(manifest.capabilities), changelog, key, sha256, bytes.length, fileCount, now()).run();
	await audit(env, user.id, "upload", `${manifest.id}@${manifest.version}`);
	return json({ ok: true, id, package: manifest.id, version: manifest.version }, 201);
}

// ---- moderation -----------------------------------------------------------

async function approve(env, moderator, id) {
	if (!env.REGISTRY_SIGNING_KEY || !env.GITHUB_TOKEN) {
		throw new HttpError(503, "Publishing is not configured yet (REGISTRY_SIGNING_KEY or GITHUB_TOKEN is missing).");
	}
	const claim = await env.DB.prepare("UPDATE submissions SET status = 'processing' WHERE id = ? AND status = 'pending'").bind(id).run();
	if (!claim.meta.changes) throw new HttpError(409, "This upload is not pending (maybe someone else is handling it).");
	const sub = await env.DB.prepare("SELECT * FROM submissions WHERE id = ?").bind(id).first();
	try {
		if (!CATEGORIES.has(sub.category)) throw new Error("Unknown category.");
		const obj = await env.UPLOADS.get(sub.r2_key);
		if (!obj) throw new Error("The uploaded file is missing.");
		const bytes = new Uint8Array(await obj.arrayBuffer());
		const digest = await sha256Bytes(bytes);
		const hex = [...digest].map((x) => x.toString(16).padStart(2, "0")).join("");
		if (hex !== sub.sha256) throw new Error("Stored file does not match its checksum.");
		await inspectVpkg(bytes);

		const signature = await signDigest(env.REGISTRY_SIGNING_KEY, digest);
		const downloadUrl = await uploadRelease(env, sub.package_id, sub.version, bytes);
		const { entry: previous, sha } = await readEntry(env, sub.package_id);
		const pkg = await env.DB.prepare("SELECT owner_id FROM packages WHERE id = ?").bind(sub.package_id).first();
		if (previous && (!pkg || pkg.owner_id !== sub.user_id)) throw new Error("The catalog already has a package with this id from someone else.");
		const entry = nextEntry(previous, sub, { name: sub.author, uploadsRepo: env.UPLOADS_REPO }, {
			version: sub.version,
			vpkgUrl: downloadUrl,
			sha256: sub.sha256,
			signature,
			size: sub.size,
			publishedAt: new Date().toISOString(),
		});
		await writeEntry(env, sub.package_id, entry, sha, `Publish ${sub.package_id} ${sub.version} (approved in panel)`);

		await env.DB.prepare("UPDATE submissions SET status = 'approved', reviewer_id = ?, decided_at = ?, download_url = ? WHERE id = ?")
			.bind(moderator.id, now(), downloadUrl, id).run();
		await env.DB.prepare("UPDATE packages SET published = 1, name = ?, category = ?, updated_at = ? WHERE id = ?")
			.bind(sub.name, sub.category, now(), sub.package_id).run();
		await audit(env, moderator.id, "approve", `${sub.package_id}@${sub.version}`);
		return json({ ok: true, downloadUrl });
	} catch (err) {
		await env.DB.prepare("UPDATE submissions SET status = 'pending' WHERE id = ? AND status = 'processing'").bind(id).run();
		await audit(env, moderator.id, "approve-failed", `${sub.package_id}@${sub.version}`, err.message);
		throw new HttpError(502, `Publishing failed: ${err.message}`);
	}
}

async function readReason(request) {
	const body = await request.json().catch(() => ({}));
	const reason = String(body.reason || "").trim().slice(0, 1000);
	if (reason.length < 3) throw new HttpError(400, "Please give a reason.");
	return reason;
}

async function reject(request, env, moderator, id) {
	const reason = await readReason(request);
	const r = await env.DB.prepare("UPDATE submissions SET status = 'rejected', reason = ?, reviewer_id = ?, decided_at = ? WHERE id = ? AND status = 'pending'")
		.bind(reason, moderator.id, now(), id).run();
	if (!r.meta.changes) throw new HttpError(409, "This upload is not pending.");
	const sub = await env.DB.prepare("SELECT r2_key, package_id, version FROM submissions WHERE id = ?").bind(id).first();
	await env.UPLOADS.delete(sub.r2_key);
	await audit(env, moderator.id, "reject", `${sub.package_id}@${sub.version}`, reason);
	return json({ ok: true });
}

async function catalog(env) {
	const base = "https://umgekocht.github.io/vitrine-registry/index/v1/";
	let next = "index.json";
	const items = [];
	for (let page = 0; next && page < 50; page++) {
		const res = await fetch(new URL(next, base).href + `?t=${now()}`);
		if (!res.ok) throw new HttpError(502, "Could not load the catalog.");
		const data = await res.json();
		for (const p of data.packages || []) {
			items.push({ id: p.id, name: p.name, category: p.category, author: p.author?.name ?? p.author, latestVersion: p.latestVersion });
		}
		next = typeof data.nextPage === "string" ? data.nextPage : null;
	}
	return json({ packages: items });
}

async function removePackage(request, env, moderator, packageId) {
	const reason = await readReason(request);
	const removed = await deleteEntry(env, packageId, `Remove ${packageId} (moderator: ${reason.slice(0, 200)})`);
	if (!removed) throw new HttpError(404, "Package is not in the catalog.");
	await env.DB.prepare("UPDATE packages SET deleted = 1, published = 0, updated_at = ? WHERE id = ?").bind(now(), packageId).run();
	await env.DB.prepare("UPDATE submissions SET status = 'removed', reason = ?, decided_at = ? WHERE package_id = ? AND status = 'approved'")
		.bind(reason, now(), packageId).run();
	await audit(env, moderator.id, "remove", packageId, reason);
	return json({ ok: true });
}

// ---- bot gate -----------------------------------------------------------------

function escapeAttr(s) {
	return String(s).replace(/[^A-Za-z0-9_-]/g, "");
}

function gatePage(env) {
	const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Vitrine Panel – quick check</title><link rel="stylesheet" href="/app.css">
<script src="/gate.js"></script>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script></head>
<body><main><div class="hero">
<img src="/icon.png" alt="" width="64" height="64" class="hero-icon">
<h1>Quick check</h1><p>Please confirm you are a human. This keeps bots away from uploads and suggestions.</p>
<div class="cf-turnstile" data-sitekey="${escapeAttr(env.TURNSTILE_SITE_KEY)}" data-callback="vitrinePassed" data-action="gate"></div>
<p id="gate-msg" class="fine" role="status"></p>
<p class="fine"><a href="/privacy">Privacy</a> · <a href="/terms">Terms</a></p>
</div></main></body></html>`;
	return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
}

async function gateVerify(request, env) {
	if (request.headers.get("Origin") !== new URL(env.PUBLIC_URL).origin) throw new HttpError(403, "Cross-site request blocked.");
	const live = env.PUBLIC_URL.startsWith("https://");
	if (!env.GATE_SECRET || env.GATE_SECRET.length < 32 || !env.TURNSTILE_SECRET
		|| (live && /^[123]x0000/.test(env.TURNSTILE_SITE_KEY || ""))) {
		throw new HttpError(503, "The human check is not configured on this server.");
	}
	await rateLimit(env, `gate:${clientIp(request)}`, 30, 3600);
	const body = await request.json().catch(() => ({}));
	if (!(await verifyTurnstile(env, body.token, clientIp(request)))) throw new HttpError(403, "Check failed. Please try again.");
	return json({ ok: true }, 200, { "Set-Cookie": await makePass(env) });
}

function clientIp(request) {
	return request.headers.get("CF-Connecting-IP") || "local";
}

/** Fixed-window limiter backed by D1. Throws 429 when the limit is reached. */
async function rateLimit(env, key, limit, windowSeconds) {
	const t = now();
	const row = await env.DB.prepare(
		`INSERT INTO rate_limits (key, count, window_end) VALUES (?, 1, ?)
		 ON CONFLICT(key) DO UPDATE SET
		   count = CASE WHEN window_end < ? THEN 1 ELSE count + 1 END,
		   window_end = CASE WHEN window_end < ? THEN ? ELSE window_end END
		 RETURNING count`,
	).bind(key, t + windowSeconds * 1000, t, t, t + windowSeconds * 1000).first();
	if (row.count > limit) throw new HttpError(429, "Too many requests. Please wait a while.");
}

// ---- suggestions -----------------------------------------------------------------

async function nicknameOf(env, userId) {
	const row = await env.DB.prepare("SELECT nickname FROM nicknames WHERE user_id = ?").bind(userId).first();
	return row?.nickname ?? null;
}

async function setNickname(env, user, value) {
	const nick = String(value ?? "").trim();
	if (!NICKNAME_RE.test(nick)) throw new HttpError(400, "Nicknames are 3–20 letters, digits or underscores.");
	if (/^(admin|moderator|mod|vitrine|umgekocht|official|support)/i.test(nick)) throw new HttpError(400, "This nickname is reserved.");
	const taken = await env.DB.prepare("SELECT user_id FROM nicknames WHERE nickname = ?").bind(nick).first();
	if (taken && taken.user_id !== user.id) throw new HttpError(409, "This nickname is taken.");
	await env.DB.prepare("INSERT INTO nicknames (user_id, nickname) VALUES (?, ?) ON CONFLICT(user_id) DO UPDATE SET nickname = excluded.nickname")
		.bind(user.id, nick).run();
	await audit(env, user.id, "nickname", nick);
	return nick;
}

async function listSuggestions(env, url, session) {
	const sort = url.searchParams.get("sort") === "new" ? "s.created_at DESC" : "s.votes DESC, s.created_at DESC";
	const status = url.searchParams.get("status");
	const where = ["s.hidden = 0"];
	const binds = [];
	if (status && SUGGESTION_STATUS.has(status)) {
		where.push("s.status = ?");
		binds.push(status);
	}
	const rows = await env.DB.prepare(
		`SELECT s.id, s.kind, s.title, s.body, s.status, s.mod_note, s.votes, s.created_at, n.nickname AS author,
		        ${session ? "EXISTS(SELECT 1 FROM suggestion_votes v WHERE v.suggestion_id = s.id AND v.user_id = ?)" : "0"} AS voted
		   FROM suggestions s LEFT JOIN nicknames n ON n.user_id = s.user_id
		  WHERE ${where.join(" AND ")} ORDER BY ${sort} LIMIT 200`,
	).bind(...(session ? [session.user.id] : []), ...binds).all();
	return json({ suggestions: rows.results });
}

async function createSuggestion(request, env, user) {
	await rateLimit(env, `suggest:${user.id}`, 5, 24 * 3600);
	const body = await request.json().catch(() => ({}));
	if (!(await nicknameOf(env, user.id))) throw new HttpError(400, "Please choose a nickname first.");
	let title, text;
	try {
		title = cleanText(body.title, 5, 120, "Title");
		text = cleanText(body.body, 10, 4000, "Description");
	} catch (err) {
		throw new HttpError(400, err.message);
	}
	const kind = SUGGESTION_KINDS.has(body.kind) ? body.kind : "other";
	const id = randomToken(16);
	await env.DB.prepare("INSERT INTO suggestions (id, user_id, kind, title, body, votes, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)")
		.bind(id, user.id, kind, title, text, now(), now()).run();
	await env.DB.prepare("INSERT INTO suggestion_votes (suggestion_id, user_id, created_at) VALUES (?, ?, ?)").bind(id, user.id, now()).run();
	await audit(env, user.id, "suggest", id, title);
	return json({ ok: true, id }, 201);
}

async function voteSuggestion(env, user, id) {
	await rateLimit(env, `vote:${user.id}`, 200, 24 * 3600);
	const s = await env.DB.prepare("SELECT id FROM suggestions WHERE id = ? AND hidden = 0").bind(id).first();
	if (!s) throw new HttpError(404, "Suggestion not found.");
	const existing = await env.DB.prepare("SELECT 1 FROM suggestion_votes WHERE suggestion_id = ? AND user_id = ?").bind(id, user.id).first();
	if (existing) {
		await env.DB.batch([
			env.DB.prepare("DELETE FROM suggestion_votes WHERE suggestion_id = ? AND user_id = ?").bind(id, user.id),
			env.DB.prepare("UPDATE suggestions SET votes = votes - 1 WHERE id = ?").bind(id),
		]);
	} else {
		await env.DB.batch([
			env.DB.prepare("INSERT INTO suggestion_votes (suggestion_id, user_id, created_at) VALUES (?, ?, ?)").bind(id, user.id, now()),
			env.DB.prepare("UPDATE suggestions SET votes = votes + 1 WHERE id = ?").bind(id),
		]);
	}
	return json({ voted: !existing });
}

async function moderateSuggestion(request, env, moderator, id) {
	const body = await request.json().catch(() => ({}));
	const s = await env.DB.prepare("SELECT id FROM suggestions WHERE id = ?").bind(id).first();
	if (!s) throw new HttpError(404, "Suggestion not found.");
	if (body.hidden === true) {
		await env.DB.prepare("UPDATE suggestions SET hidden = 1, updated_at = ? WHERE id = ?").bind(now(), id).run();
		await audit(env, moderator.id, "suggestion-hide", id);
		return json({ ok: true });
	}
	if (!SUGGESTION_STATUS.has(body.status)) throw new HttpError(400, "Unknown status.");
	const note = String(body.note ?? "").trim().slice(0, 1000);
	await env.DB.prepare("UPDATE suggestions SET status = ?, mod_note = ?, updated_at = ? WHERE id = ?").bind(body.status, note, now(), id).run();
	await audit(env, moderator.id, "suggestion-status", id, `${body.status} ${note}`);
	return json({ ok: true });
}
