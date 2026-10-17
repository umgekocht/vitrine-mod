// Publishing to GitHub: approved .vpkg files become release assets in
// UPLOADS_REPO (a github.com URL, which the mod's download whitelist accepts),
// and the catalog entry is written to REGISTRY_REPO/packages/<id>.json. The
// registry's own "Index-Bau" workflow then rebuilds the index the mod reads.

const API = "https://api.github.com";

async function gh(env, method, path, body, extraHeaders = {}) {
	const res = await fetch(path.startsWith("https://") ? path : API + path, {
		method,
		headers: {
			Authorization: `Bearer ${env.GITHUB_TOKEN}`,
			Accept: "application/vnd.github+json",
			"X-GitHub-Api-Version": "2022-11-28",
			"User-Agent": "vitrine-panel",
			...(body && !(body instanceof Uint8Array) ? { "Content-Type": "application/json" } : {}),
			...extraHeaders,
		},
		body: body instanceof Uint8Array ? body : body ? JSON.stringify(body) : undefined,
	});
	if (res.status === 404 && method === "GET") return null;
	if (!res.ok) {
		const text = (await res.text()).slice(0, 300);
		throw new Error(`GitHub ${method} ${path.split("?")[0]} failed: ${res.status} ${text}`);
	}
	return res.status === 204 ? null : res.json();
}

function b64(str) {
	return btoa(String.fromCharCode(...new TextEncoder().encode(str)));
}

function fromB64(str) {
	const bin = atob(str.replace(/\n/g, ""));
	return new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
}

/** Creates (or reuses) the release for one package version and uploads the file. Returns the public download URL. */
export async function uploadRelease(env, packageId, version, bytes) {
	const tag = `${packageId}-v${version}`;
	const fileName = `${packageId}-${version}.vpkg`;
	let release = await gh(env, "GET", `/repos/${env.UPLOADS_REPO}/releases/tags/${encodeURIComponent(tag)}`);
	if (!release) {
		release = await gh(env, "POST", `/repos/${env.UPLOADS_REPO}/releases`, {
			tag_name: tag,
			name: `${packageId} ${version}`,
			body: "Published through the Vitrine panel after moderation.",
		});
	}
	const existing = (release.assets || []).find((a) => a.name === fileName);
	if (!existing) {
		const uploadUrl = release.upload_url.replace(/\{.*\}$/, "") + `?name=${encodeURIComponent(fileName)}`;
		await gh(env, "POST", uploadUrl, bytes, { "Content-Type": "application/octet-stream" });
	}
	return `https://github.com/${env.UPLOADS_REPO}/releases/download/${encodeURIComponent(tag)}/${encodeURIComponent(fileName)}`;
}

export async function readEntry(env, packageId) {
	const file = await gh(env, "GET", `/repos/${env.REGISTRY_REPO}/contents/packages/${packageId}.json?ref=main`);
	if (!file) return { entry: null, sha: null };
	return { entry: JSON.parse(fromB64(file.content)), sha: file.sha };
}

export async function writeEntry(env, packageId, entry, sha, message) {
	await gh(env, "PUT", `/repos/${env.REGISTRY_REPO}/contents/packages/${packageId}.json`, {
		message,
		content: b64(JSON.stringify(entry, null, 2) + "\n"),
		branch: "main",
		...(sha ? { sha } : {}),
	});
}

export async function deleteEntry(env, packageId, message) {
	const { sha } = await readEntry(env, packageId);
	if (!sha) return false;
	await gh(env, "DELETE", `/repos/${env.REGISTRY_REPO}/contents/packages/${packageId}.json`, { message, sha, branch: "main" });
	return true;
}

/** Builds the next catalog entry from the previous one plus an approved submission. */
export function nextEntry(previous, sub, owner, version) {
	const versions = (previous?.versions || []).filter((v) => v.version !== sub.version);
	versions.push(version);
	versions.sort((a, b) => cmpSemver(a.version, b.version));
	const entry = {
		schemaVersion: 1,
		id: sub.package_id,
		name: sub.name,
		category: sub.category,
		author: { name: owner.name.slice(0, 80) },
		description: sub.description,
		capabilities: JSON.parse(sub.capabilities),
		license: sub.license,
		repository: previous?.repository || `https://github.com/${owner.uploadsRepo}`,
		versions,
		latestVersion: versions[versions.length - 1].version,
		updatedAt: new Date().toISOString(),
	};
	if (previous?.icon) entry.icon = previous.icon;
	if (previous?.homepage) entry.homepage = previous.homepage;
	if (previous?.tags) entry.tags = previous.tags;
	return entry;
}

export function cmpSemver(a, b) {
	const pa = a.split(".").map(Number);
	const pb = b.split(".").map(Number);
	for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] - pb[i];
	return 0;
}
