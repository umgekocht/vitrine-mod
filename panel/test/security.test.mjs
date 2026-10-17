import { test } from "node:test";
import assert from "node:assert/strict";
import { deflateRawSync } from "node:zlib";
import { generateKeyPairSync, createPublicKey, verify, createHash } from "node:crypto";
import { inspectVpkg, ZipRejected } from "../src/zip.js";
import { verifyGoogleIdToken, signDigest, b64url, safeEqual } from "../src/security.js";
import { nextEntry, cmpSemver } from "../src/github.js";

// --- minimal ZIP writer for test fixtures ---------------------------------
function crc32(buf) {
	let c, crc = 0xffffffff;
	for (const b of buf) {
		c = (crc ^ b) & 0xff;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		crc = (crc >>> 8) ^ c;
	}
	return (crc ^ 0xffffffff) >>> 0;
}

function zip(files, { symlink, localNameOverride } = {}) {
	const locals = [];
	const centrals = [];
	let offset = 0;
	for (const [name, content] of Object.entries(files)) {
		const data = Buffer.from(content);
		const comp = deflateRawSync(data);
		const nameBuf = Buffer.from(name);
		const localName = Buffer.from(localNameOverride?.[name] ?? name);
		const local = Buffer.alloc(30);
		local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x800, 6); local.writeUInt16LE(8, 8);
		local.writeUInt32LE(crc32(data), 14); local.writeUInt32LE(comp.length, 18); local.writeUInt32LE(data.length, 22);
		local.writeUInt16LE(localName.length, 26);
		locals.push(local, localName, comp);
		const central = Buffer.alloc(46);
		central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6); central.writeUInt16LE(0x800, 8);
		central.writeUInt16LE(8, 10); central.writeUInt32LE(crc32(data), 16); central.writeUInt32LE(comp.length, 20);
		central.writeUInt32LE(data.length, 24); central.writeUInt16LE(nameBuf.length, 28);
		central.writeUInt32LE(symlink === name ? (0o120777 << 16) >>> 0 : 0, 38);
		central.writeUInt32LE(offset, 42);
		centrals.push(central, nameBuf);
		offset += 30 + localName.length + comp.length;
	}
	const cd = Buffer.concat(centrals);
	const eocd = Buffer.alloc(22);
	eocd.writeUInt32LE(0x06054b50, 0);
	eocd.writeUInt16LE(Object.keys(files).length, 8); eocd.writeUInt16LE(Object.keys(files).length, 10);
	eocd.writeUInt32LE(cd.length, 12); eocd.writeUInt32LE(offset, 16);
	return new Uint8Array(Buffer.concat([...locals, cd, eocd]));
}

const manifest = (over = {}) => JSON.stringify({ schemaVersion: 1, id: "anna.clear-stone", name: "Clear Stone", version: "1.0.0",
	category: "texturen", author: "anna", capabilities: ["texturen"], ...over });

async function rejects(bytes, pattern) {
	await assert.rejects(inspectVpkg(bytes), (e) => e instanceof ZipRejected && pattern.test(e.message));
}

test("accepts a normal package and reads its manifest", async () => {
	const r = await inspectVpkg(zip({ "vitrine.json": manifest(), "assets/minecraft/textures/block/stone.png": "png", "LICENSE": "x" }));
	assert.equal(r.manifest.id, "anna.clear-stone");
	assert.equal(r.manifest.version, "1.0.0");
	assert.equal(r.fileCount, 3);
});

test("rejects path traversal", () => rejects(zip({ "vitrine.json": manifest(), "../evil.png": "x" }), /Invalid path/));
test("rejects absolute paths", () => rejects(zip({ "vitrine.json": manifest(), "/etc/x.png": "x" }), /Invalid path/));
test("rejects backslashes", () => rejects(zip({ "vitrine.json": manifest(), "a\\b.png": "x" }), /Invalid path/));
test("rejects executables", () => rejects(zip({ "vitrine.json": manifest(), "run.exe": "x" }), /not allowed/));
test("rejects jar files", () => rejects(zip({ "vitrine.json": manifest(), "mods/x.jar": "x" }), /not allowed/));
test("rejects symlinks", () => rejects(zip({ "vitrine.json": manifest(), "a.png": "x" }, { symlink: "a.png" }), /Symbolic/));
test("rejects zip bombs", () => rejects(zip({ "vitrine.json": manifest(), "a.txt": "a".repeat(2_000_000) }), /compression ratio/));
test("rejects duplicate names", () => rejects(zip({ "vitrine.json": manifest(), "A.png": "1", "a.png": "2" }), /Duplicate/));
test("rejects name smuggling between headers", () =>
	rejects(zip({ "vitrine.json": manifest(), "a.png": "x" }, { localNameOverride: { "a.png": "a.exe" } }), /mismatch/));
test("requires vitrine.json", () => rejects(zip({ "a.png": "x" }), /vitrine\.json is missing/));
test("rejects bad ids", () => rejects(zip({ "vitrine.json": manifest({ id: "Bad Id" }) }), /'id'/));
test("rejects bad versions", () => rejects(zip({ "vitrine.json": manifest({ version: "1.0" }) }), /'version'/));
test("rejects java mods", () => rejects(zip({ "vitrine.json": manifest({ contentClass: "javamod" }) }), /Java mods/));
test("rejects unknown capabilities", () => rejects(zip({ "vitrine.json": manifest({ capabilities: ["netzwerk"] }) }), /capabilities/));
test("rejects non-zip data", () => rejects(new Uint8Array(100), /Not a valid ZIP/));

// --- Google ID token -------------------------------------------------------
const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: "jwk" }), kid: "k1", alg: "RS256", use: "sig" };

async function token(claims, kid = "k1") {
	const { sign } = await import("node:crypto");
	const h = b64url(Buffer.from(JSON.stringify({ alg: "RS256", kid })));
	const p = b64url(Buffer.from(JSON.stringify(claims)));
	const s = sign("RSA-SHA256", Buffer.from(`${h}.${p}`), privateKey);
	return `${h}.${p}.${b64url(s)}`;
}

const good = () => ({ iss: "https://accounts.google.com", aud: "client", exp: Math.floor(Date.now() / 1000) + 600,
	nonce: "n1", sub: "123", email: "a@example.com", email_verified: true });
const loader = async () => [jwk];

test("accepts a valid Google ID token", async () => {
	const c = await verifyGoogleIdToken(await token(good()), { clientId: "client", nonce: "n1" }, loader);
	assert.equal(c.email, "a@example.com");
});
for (const [label, change, msg] of [
	["wrong audience", { aud: "other" }, /audience/],
	["wrong issuer", { iss: "https://evil.example" }, /issuer/],
	["expired", { exp: 1000 }, /expired/],
	["wrong nonce", { nonce: "n2" }, /Nonce/],
	["unverified email", { email_verified: false }, /verified/],
]) {
	test(`rejects ID token with ${label}`, async () => {
		await assert.rejects(verifyGoogleIdToken(await token({ ...good(), ...change }), { clientId: "client", nonce: "n1" }, loader), msg);
	});
}
test("rejects a tampered ID token", async () => {
	const t = (await token(good())).split(".");
	const p = b64url(Buffer.from(JSON.stringify({ ...good(), email: "boss@example.com" })));
	await assert.rejects(verifyGoogleIdToken(`${t[0]}.${p}.${t[2]}`, { clientId: "client", nonce: "n1" }, loader), /signature/);
});

// --- registry signature ----------------------------------------------------
test("signature verifies the way the mod checks it (Ed25519 over SHA-256 digest)", async () => {
	const kp = generateKeyPairSync("ed25519");
	const pem = kp.privateKey.export({ format: "pem", type: "pkcs8" });
	const file = Buffer.from("package bytes");
	const digest = createHash("sha256").update(file).digest();
	const sig = await signDigest(pem, new Uint8Array(digest));
	assert.ok(verify(null, digest, kp.publicKey, Buffer.from(sig, "base64")));
});

test("safeEqual", () => {
	assert.ok(safeEqual("abc", "abc"));
	assert.ok(!safeEqual("abc", "abd"));
	assert.ok(!safeEqual("abc", "ab"));
});

test("catalog entry keeps old versions and sorts by version", () => {
	const prev = { repository: "https://github.com/u/r", versions: [{ version: "1.0.0" }], icon: "https://x/i.png" };
	const e = nextEntry(prev, { package_id: "a.b", name: "N", category: "hud", description: "", capabilities: "[\"hud\"]",
		license: "MIT", version: "1.1.0" }, { name: "anna", uploadsRepo: "u/up" }, { version: "1.1.0" });
	assert.deepEqual(e.versions.map((v) => v.version), ["1.0.0", "1.1.0"]);
	assert.equal(e.latestVersion, "1.1.0");
	assert.equal(e.icon, "https://x/i.png");
	assert.ok(cmpSemver("1.10.0", "1.9.0") > 0);
});
