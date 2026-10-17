// "Are you human?" gate in front of the whole site, using Cloudflare
// Turnstile (free, privacy friendly, no image puzzles). A solved challenge
// gives the browser a short-lived pass cookie signed with HMAC-SHA256; without
// a valid pass, pages redirect to /gate and API calls are refused.

const PASS_SECONDS = 12 * 3600;
const enc = new TextEncoder();

function b64url(bytes) {
	let s = "";
	for (const x of bytes) s += String.fromCharCode(x);
	return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function hmac(secret, data) {
	const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
	return b64url(new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(data))));
}

export function passCookieName(env) {
	return env.PUBLIC_URL.startsWith("https://") ? "__Host-vitrine-pass" : "vitrine-pass";
}

/** Cookie value "<expiry>.<random>.<hmac>". */
export async function makePass(env) {
	const exp = Math.floor(Date.now() / 1000) + PASS_SECONDS;
	const nonce = b64url(crypto.getRandomValues(new Uint8Array(12)));
	const body = `${exp}.${nonce}`;
	const value = `${body}.${await hmac(env.GATE_SECRET, body)}`;
	const secure = env.PUBLIC_URL.startsWith("https://");
	return `${passCookieName(env)}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${PASS_SECONDS}${secure ? "; Secure" : ""}`;
}

export async function hasValidPass(env, cookies) {
	const value = cookies[passCookieName(env)];
	if (!value || !env.GATE_SECRET) return false;
	const parts = value.split(".");
	if (parts.length !== 3) return false;
	const [exp, nonce, mac] = parts;
	if (!/^\d+$/.test(exp) || Number(exp) < Date.now() / 1000) return false;
	const expected = await hmac(env.GATE_SECRET, `${exp}.${nonce}`);
	if (expected.length !== mac.length) return false;
	let diff = 0;
	for (let i = 0; i < mac.length; i++) diff |= mac.charCodeAt(i) ^ expected.charCodeAt(i);
	return diff === 0;
}

/** Verifies a Turnstile token with Cloudflare. */
export async function verifyTurnstile(env, token, ip) {
	if (typeof token !== "string" || token.length < 10 || token.length > 4096) return false;
	const body = new URLSearchParams({ secret: env.TURNSTILE_SECRET, response: token });
	if (ip) body.set("remoteip", ip);
	const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", { method: "POST", body });
	if (!res.ok) return false;
	const data = await res.json();
	if (data.success !== true) return false;
	// Make sure the token was issued for this site and not replayed from another page.
	const host = new URL(env.PUBLIC_URL).hostname;
	return !data.hostname || data.hostname === host || host === "localhost";
}

/** Paths that must work without a pass (the gate itself and what it needs). */
export function isGateExempt(path) {
	return path === "/gate" || path === "/gate/verify" || path === "/gate.js" || path === "/app.css" || path === "/icon.png";
}
