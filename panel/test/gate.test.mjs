import { test } from "node:test";
import assert from "node:assert/strict";
import { hasValidPass, makePass, passCookieName } from "../src/gate.js";
import { cleanText, NICKNAME_RE } from "../src/community.js";

const env = { PUBLIC_URL: "https://panel.example", GATE_SECRET: "x".repeat(40) };
const cookieValue = (setCookie) => setCookie.split(";")[0].split("=").slice(1).join("=");

test("a fresh pass is valid and uses a __Host- secure cookie", async () => {
	const set = await makePass(env);
	assert.match(set, /^__Host-vitrine-pass=/);
	assert.match(set, /HttpOnly/);
	assert.match(set, /Secure/);
	assert.ok(await hasValidPass(env, { [passCookieName(env)]: cookieValue(set) }));
});

test("a pass signed with another secret is rejected", async () => {
	const set = await makePass({ ...env, GATE_SECRET: "y".repeat(40) });
	assert.equal(await hasValidPass(env, { [passCookieName(env)]: cookieValue(set) }), false);
});

test("a tampered expiry is rejected", async () => {
	const [, nonce, mac] = cookieValue(await makePass(env)).split(".");
	assert.equal(await hasValidPass(env, { [passCookieName(env)]: `9999999999.${nonce}.${mac}` }), false);
});

test("an expired pass is rejected", async () => {
	assert.equal(await hasValidPass(env, { [passCookieName(env)]: "1000.abc.def" }), false);
});

test("missing secret never validates", async () => {
	const set = await makePass(env);
	assert.equal(await hasValidPass({ ...env, GATE_SECRET: "" }, { [passCookieName(env)]: cookieValue(set) }), false);
});

test("text cleaning strips control characters and enforces length", () => {
	assert.equal(cleanText("  hi\u0000 there  ", 3, 20, "Title"), "hi there");
	assert.throws(() => cleanText("a", 3, 20, "Title"), /Title/);
	assert.ok(NICKNAME_RE.test("Pixel_Panda"));
	assert.ok(!NICKNAME_RE.test("<b>x</b>"));
});
