// Safety check for uploaded .vpkg files (ZIP archives), run before anything
// is stored. Mirrors the limits of the mod's VpkgSecurityValidator so that a
// package accepted here also installs in game. Only the central directory and
// vitrine.json are read, which keeps the CPU cost per upload small.

export const LIMITS = {
	maxUploadBytes: 10 * 1024 * 1024,
	maxTotalUncompressed: 256 * 1024 * 1024,
	maxRatio: 100,
	maxEntries: 20000,
	maxDepth: 32,
	maxNameLength: 512,
	maxManifestBytes: 64 * 1024,
};

const ALLOWED_EXT = new Set(["png", "jpg", "jpeg", "json", "ogg", "txt", "mcmeta", "ttf", "otf", "md",
	"mcfunction", "nbt", "lua", "js", "vs"]);
const ROOT_FILES = new Set(["LICENSE", "LICENCE", "NOTICE", "COPYING", "README"]);

export const CATEGORIES = new Set(["texturen", "theme", "hud", "shader", "sound", "sprache", "cosmetics", "bundle", "addon"]);
export const CAPABILITIES = new Set(["texturen", "theme", "hud", "shader", "sound", "sprache", "cosmetics", "verhalten", "skript"]);
const ID_RE = /^[a-z][a-z0-9-]{1,40}(\.[a-z][a-z0-9-]{1,40}){1,3}$/;
const SEMVER_RE = /^(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})$/;

export class ZipRejected extends Error {}

function fail(msg) {
	throw new ZipRejected(msg);
}

function u16(b, o) {
	return b[o] | (b[o + 1] << 8);
}

function u32(b, o) {
	return (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) >>> 0;
}

function findEocd(b) {
	const min = Math.max(0, b.length - 22 - 0xffff);
	for (let i = b.length - 22; i >= min; i--) {
		if (u32(b, i) === 0x06054b50) return i;
	}
	fail("Not a valid ZIP archive (end of central directory missing).");
}

async function inflateRaw(data, limit) {
	const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
	const reader = stream.getReader();
	const chunks = [];
	let total = 0;
	for (;;) {
		const { done, value } = await reader.read();
		if (done) break;
		total += value.length;
		if (total > limit) {
			await reader.cancel();
			fail("vitrine.json is too large.");
		}
		chunks.push(value);
	}
	const out = new Uint8Array(total);
	let o = 0;
	for (const c of chunks) {
		out.set(c, o);
		o += c.length;
	}
	return out;
}

/**
 * Validates a .vpkg and returns its manifest and entry list.
 * @param {Uint8Array} bytes
 */
export async function inspectVpkg(bytes) {
	if (bytes.length > LIMITS.maxUploadBytes) fail(`File is larger than ${LIMITS.maxUploadBytes / 1024 / 1024} MB.`);
	if (bytes.length < 22) fail("File is too small to be a package.");
	const eocd = findEocd(bytes);
	const count = u16(bytes, eocd + 10);
	const cdSize = u32(bytes, eocd + 12);
	const cdOffset = u32(bytes, eocd + 16);
	if (u16(bytes, eocd + 4) !== 0 || u16(bytes, eocd + 6) !== 0) fail("Multi-part archives are not allowed.");
	if (count === 0xffff || cdOffset === 0xffffffff) fail("ZIP64 archives are not allowed.");
	if (count > LIMITS.maxEntries) fail(`Too many files (${count}).`);
	if (cdOffset + cdSize > eocd) fail("Broken central directory.");

	const decoder = new TextDecoder("utf-8", { fatal: true });
	const entries = [];
	const seen = new Set();
	let total = 0;
	let p = cdOffset;
	for (let i = 0; i < count; i++) {
		if (u32(bytes, p) !== 0x02014b50) fail("Broken central directory entry.");
		const flags = u16(bytes, p + 8);
		const method = u16(bytes, p + 10);
		const csize = u32(bytes, p + 20);
		const usize = u32(bytes, p + 24);
		const nameLen = u16(bytes, p + 28);
		const extraLen = u16(bytes, p + 30);
		const commentLen = u16(bytes, p + 32);
		const extAttr = u32(bytes, p + 38);
		const localOffset = u32(bytes, p + 42);
		let name;
		try {
			name = decoder.decode(bytes.subarray(p + 46, p + 46 + nameLen));
		} catch {
			fail("A file name is not valid UTF-8.");
		}
		p += 46 + nameLen + extraLen + commentLen;

		if (flags & 0x1) fail(`Encrypted entries are not allowed: ${name}`);
		if (csize === 0xffffffff || usize === 0xffffffff || localOffset === 0xffffffff) fail("ZIP64 entries are not allowed.");
		if (method !== 0 && method !== 8) fail(`Unsupported compression in ${name}.`);
		if (!name || name.startsWith("/") || name.includes("\\") || name.includes(":") || name.includes("\0")) fail(`Invalid path: ${name}`);
		const parts = name.split("/");
		if (parts.some((s) => s === ".." || s === ".")) fail(`Invalid path: ${name}`);
		if (name.length > LIMITS.maxNameLength || parts.length > LIMITS.maxDepth) fail(`Path too long or too deep: ${name}`);
		const unixMode = extAttr >>> 16;
		if ((unixMode & 0o170000) === 0o120000) fail(`Symbolic links are not allowed: ${name}`);
		const lower = name.toLowerCase();
		if (seen.has(lower)) fail(`Duplicate file: ${name}`);
		seen.add(lower);
		if (u32(bytes, localOffset) !== 0x04034b50) fail(`Broken local header for ${name}.`);
		const localNameLen = u16(bytes, localOffset + 26);
		const localName = new TextDecoder().decode(bytes.subarray(localOffset + 30, localOffset + 30 + localNameLen));
		if (localName !== name) fail(`File name mismatch inside the archive: ${name}`);

		if (name.endsWith("/")) continue;
		total += usize;
		if (total > LIMITS.maxTotalUncompressed) fail("Unpacked size exceeds 256 MB.");
		if (csize > 0 && usize / csize > LIMITS.maxRatio) fail(`Suspicious compression ratio in ${name}.`);
		if (csize === 0 && usize > 0) fail(`Suspicious compression ratio in ${name}.`);
		const base = parts[parts.length - 1];
		const ext = base.includes(".") ? base.slice(base.lastIndexOf(".") + 1).toLowerCase() : "";
		if (!ALLOWED_EXT.has(ext) && !(parts.length === 1 && ROOT_FILES.has(base))) fail(`File type not allowed: ${name}`);
		entries.push({ name, method, csize, usize, localOffset, localNameLen });
	}

	const manifestEntry = entries.find((e) => e.name === "vitrine.json");
	if (!manifestEntry) fail("vitrine.json is missing at the root of the package.");
	if (manifestEntry.usize > LIMITS.maxManifestBytes) fail("vitrine.json is too large.");
	const localExtra = u16(bytes, manifestEntry.localOffset + 28);
	const start = manifestEntry.localOffset + 30 + manifestEntry.localNameLen + localExtra;
	const raw = bytes.subarray(start, start + manifestEntry.csize);
	const data = manifestEntry.method === 0 ? raw : await inflateRaw(raw, LIMITS.maxManifestBytes);
	let manifest;
	try {
		manifest = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(data));
	} catch {
		fail("vitrine.json is not valid JSON.");
	}
	return { manifest: checkManifest(manifest), fileCount: entries.length };
}

function str(v, field, max) {
	if (typeof v !== "string" || v.trim() === "" || v.length > max) fail(`vitrine.json: '${field}' is missing or longer than ${max} characters.`);
	return v.trim();
}

export function checkManifest(m) {
	if (!m || typeof m !== "object" || Array.isArray(m)) fail("vitrine.json must be an object.");
	if (m.schemaVersion !== 1) fail("vitrine.json: 'schemaVersion' must be 1.");
	const id = str(m.id, "id", 120);
	if (!ID_RE.test(id)) fail("vitrine.json: 'id' must look like 'yourname.package-name' (lowercase letters, digits, hyphens).");
	const version = str(m.version, "version", 32);
	if (!SEMVER_RE.test(version)) fail("vitrine.json: 'version' must look like 1.0.0.");
	const category = str(m.category, "category", 32);
	if (!CATEGORIES.has(category)) fail(`vitrine.json: unknown category '${category}'.`);
	if (m.contentClass === "javamod" || m.javaMod) fail("Java mods cannot be uploaded through the panel.");
	const caps = m.capabilities ?? [];
	if (!Array.isArray(caps) || caps.length > 10 || caps.some((c) => typeof c !== "string" || !CAPABILITIES.has(c))) {
		fail("vitrine.json: 'capabilities' contains an unknown entry.");
	}
	return {
		id,
		version,
		category,
		name: str(m.name, "name", 80),
		author: str(m.author, "author", 80),
		description: typeof m.description === "string" ? m.description.slice(0, 2000) : "",
		license: typeof m.license === "string" ? m.license.slice(0, 64) : "",
		capabilities: [...new Set(caps)],
	};
}
