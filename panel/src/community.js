// Public catalog browsing and the suggestions board.

const CATALOG_BASE = "https://umgekocht.github.io/vitrine-registry/index/v1/";
const CACHE_SECONDS = 300;

/** All published packages, cached at the edge for a few minutes. */
export async function publicCatalog(ctx) {
	const cache = caches.default;
	const key = new Request("https://vitrine-panel.internal/catalog-v1");
	const hit = await cache.match(key);
	if (hit) return hit.json();

	const items = [];
	let next = "index.json";
	for (let page = 0; next && page < 50; page++) {
		const res = await fetch(new URL(next, CATALOG_BASE).href);
		if (!res.ok) throw new Error("Catalog unavailable.");
		const data = await res.json();
		for (const p of data.packages || []) {
			items.push({
				id: String(p.id),
				name: String(p.name || p.id),
				author: String(p.author?.name ?? p.author ?? ""),
				category: String(p.category || ""),
				license: String(p.license || ""),
				latestVersion: String(p.latestVersion || ""),
				updatedAt: String(p.updatedAt || ""),
				description: String(p.shortDescription || p.description || "").slice(0, 600),
				icon: /^https:\/\/raw\.githubusercontent\.com\/[^\s"']+\.png$/.test(p.icon || "") ? p.icon : null,
				tags: Array.isArray(p.tags) ? p.tags.slice(0, 8).map(String) : [],
			});
		}
		next = typeof data.nextPage === "string" ? data.nextPage : null;
	}
	const body = { packages: items, generatedAt: new Date().toISOString() };
	const response = new Response(JSON.stringify(body), { headers: { "Cache-Control": `max-age=${CACHE_SECONDS}` } });
	ctx.waitUntil(cache.put(key, response));
	return body;
}

/** Details of one package straight from the registry (versions, download links). */
export async function publicPackage(id) {
	const res = await fetch(`https://raw.githubusercontent.com/umgekocht/vitrine-registry/main/packages/${id}.json`);
	if (res.status === 404) return null;
	if (!res.ok) throw new Error("Catalog unavailable.");
	const p = await res.json();
	const safeUrl = (u) => (typeof u === "string" && /^https:\/\/github\.com\/[^\s"']+$/.test(u) ? u : null);
	return {
		id: String(p.id),
		name: String(p.name),
		author: String(p.author?.name ?? ""),
		category: String(p.category || ""),
		license: String(p.license || ""),
		description: String(p.description || "").slice(0, 2000),
		capabilities: Array.isArray(p.capabilities) ? p.capabilities.map(String) : [],
		repository: safeUrl(p.repository),
		versions: (p.versions || []).slice(-20).reverse().map((v) => ({
			version: String(v.version),
			publishedAt: String(v.publishedAt || ""),
			size: Number(v.size) || 0,
			download: safeUrl(v.vpkgUrl),
		})),
	};
}

export const SUGGESTION_KINDS = new Set(["feature", "bug", "content", "other"]);
export const SUGGESTION_STATUS = new Set(["open", "planned", "done", "declined"]);
export const NICKNAME_RE = /^[A-Za-z0-9_]{3,20}$/;

export function cleanText(value, min, max, field) {
	const text = String(value ?? "").replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "").trim();
	if (text.length < min || text.length > max) throw new RangeError(`${field} must be ${min}–${max} characters.`);
	return text;
}
