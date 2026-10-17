// Small security helpers: random tokens, hashing, cookies, response headers,
// Google ID token verification and Ed25519 signing. Everything uses WebCrypto,
// no third-party code.

const enc = new TextEncoder();

export function randomToken(bytes = 32) {
	const b = new Uint8Array(bytes);
	crypto.getRandomValues(b);
	return b64url(b);
}

export function b64url(bytes) {
	let s = "";
	for (const x of bytes) s += String.fromCharCode(x);
	return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecode(str) {
	const s = atob(str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4));
	const out = new Uint8Array(s.length);
	for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
	return out;
}

export async function sha256Hex(data) {
	const digest = await crypto.subtle.digest("SHA-256", typeof data === "string" ? enc.encode(data) : data);
	return [...new Uint8Array(digest)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

export async function sha256Bytes(data) {
	return new Uint8Array(await crypto.subtle.digest("SHA-256", data));
}

/** Constant-time comparison of two strings. */
export function safeEqual(a, b) {
	if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	return diff === 0;
}

export function parseCookies(header) {
	const out = {};
	for (const part of (header || "").split(";")) {
		const i = part.indexOf("=");
		if (i > 0) out[part.slice(0, i).trim()] = part.slice(i + 1).trim();
	}
	return out;
}

/** Session cookie. "__Host-" forces Secure, Path=/ and no Domain on HTTPS. */
export function sessionCookie(env, value, maxAge) {
	const secure = env.PUBLIC_URL.startsWith("https://");
	const name = secure ? "__Host-vitrine" : "vitrine";
	return `${name}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${secure ? "; Secure" : ""}`;
}

export function sessionCookieName(env) {
	return env.PUBLIC_URL.startsWith("https://") ? "__Host-vitrine" : "vitrine";
}

const CSP = [
	"default-src 'none'",
	"script-src 'self' https://challenges.cloudflare.com",
	"frame-src https://challenges.cloudflare.com",
	"style-src 'self'",
	"img-src 'self' data: https://raw.githubusercontent.com https://lh3.googleusercontent.com",
	"connect-src 'self' https://challenges.cloudflare.com",
	"font-src 'self'",
	"form-action 'self' https://accounts.google.com",
	"frame-ancestors 'none'",
	"base-uri 'none'",
].join("; ");

/** Adds strict security headers to every response. */
export function harden(response) {
	const r = new Response(response.body, response);
	const h = r.headers;
	h.set("Content-Security-Policy", CSP);
	h.set("X-Content-Type-Options", "nosniff");
	h.set("X-Frame-Options", "DENY");
	h.set("Referrer-Policy", "no-referrer");
	h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
	h.set("Cross-Origin-Opener-Policy", "same-origin");
	h.set("Cross-Origin-Resource-Policy", "same-origin");
	h.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
	if (!h.has("Cache-Control")) h.set("Cache-Control", "no-store");
	return r;
}

export function json(data, status = 200, headers = {}) {
	return new Response(JSON.stringify(data), {
		status,
		headers: { "Content-Type": "application/json; charset=utf-8", ...headers },
	});
}

// ---- Google ID token ------------------------------------------------------

let jwksCache = { keys: null, until: 0 };

async function googleKeys() {
	if (jwksCache.keys && Date.now() < jwksCache.until) return jwksCache.keys;
	const res = await fetch("https://www.googleapis.com/oauth2/v3/certs");
	if (!res.ok) throw new Error("Could not load Google signing keys.");
	const { keys } = await res.json();
	jwksCache = { keys, until: Date.now() + 60 * 60 * 1000 };
	return keys;
}

/**
 * Verifies a Google ID token (RS256) and returns its claims.
 * Checks signature, issuer, audience, expiry, nonce and email_verified.
 */
export async function verifyGoogleIdToken(idToken, { clientId, nonce, now = Date.now() }, keyLoader = googleKeys) {
	const parts = idToken.split(".");
	if (parts.length !== 3) throw new Error("Malformed ID token.");
	const header = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[0])));
	const claims = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[1])));
	if (header.alg !== "RS256") throw new Error("Unexpected token algorithm.");
	const jwk = (await keyLoader()).find((k) => k.kid === header.kid);
	if (!jwk) throw new Error("Unknown signing key.");
	const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlDecode(parts[2]), enc.encode(`${parts[0]}.${parts[1]}`));
	if (!ok) throw new Error("Invalid token signature.");
	if (claims.iss !== "https://accounts.google.com" && claims.iss !== "accounts.google.com") throw new Error("Wrong issuer.");
	if (claims.aud !== clientId) throw new Error("Wrong audience.");
	if (typeof claims.exp !== "number" || claims.exp * 1000 < now - 60_000) throw new Error("Token expired.");
	if (!safeEqual(claims.nonce, nonce)) throw new Error("Nonce mismatch.");
	if (claims.email_verified !== true) throw new Error("Email address is not verified.");
	if (typeof claims.sub !== "string" || typeof claims.email !== "string") throw new Error("Token is missing claims.");
	return claims;
}

// ---- Registry signature ---------------------------------------------------

export async function importSigningKey(pem) {
	const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
	const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
	return crypto.subtle.importKey("pkcs8", der, { name: "Ed25519" }, false, ["sign"]);
}

/** Ed25519 signature over the raw 32-byte SHA-256 digest, as the mod expects (base64). */
export async function signDigest(pem, digest) {
	const key = await importSigningKey(pem);
	const sig = new Uint8Array(await crypto.subtle.sign({ name: "Ed25519" }, key, digest));
	let s = "";
	for (const x of sig) s += String.fromCharCode(x);
	return btoa(s);
}
