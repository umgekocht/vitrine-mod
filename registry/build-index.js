// Index-Builder fuer den Vitrine-Katalog (M4/K1, Pfad versioniert seit K6).
//
// Liest alle 'packages/<id>.json'-Dateien (Format:
// registry/schema/registry-package.schema.json) und erzeugt daraus, alle
// unter dem festen, versionierten Pfad 'index/v1/' (ROADMAP, Technische
// Festlegungen: "Fester Katalog-Pfad im Jar... .../index/v1/index.json"):
//   <out>/index/v1/index.json, <out>/index/v1/index-2.json, ...  (paginiert,
//     Format: registry/schema/registry-index-page.schema.json)
//   <out>/index/v1/search.json                                   (Format:
//     registry/schema/registry-search.schema.json)
//   <out>/index/v1/featured.json                                 (Format:
//     registry/schema/registry-featured.schema.json; zwei redaktionell
//     kuratierte Listen darin, 'items' (Featured) und 'popular' (Beliebt) --
//     aus den optionalen 'featured'-/'popular'-Feldern je packages/<id>.json,
//     keine echte Downloadzahl, Vitrine hat bewusst kein Backend)
//
// 'v1' ist der API-Pfad des Katalogformats (CATALOG_VERSION unten), nicht
// identisch mit 'schemaVersion' in den einzelnen Dateien, faellt aktuell aber
// zusammen. Eine spaetere, nicht abwaertskompatible Formataenderung bekommt
// 'index/v2/' als neuen, zusaetzlichen Pfad -- 'index/v1/' bleibt fuer alte
// Clients bestehen.
//
// Charter-Regel 8 ("ein Schema, zwei Seiten"): dieser Builder ist die
// Referenzimplementierung fuer das Format, das der Katalog-Client im Mod
// (K2) spaeter liest. Kein zweites Format.
//
// Aufruf (CLI):
//   node registry/build-index.js --packages <dir> --out <dir> [--page-size N] [--now ISO]
// Ohne Argumente: --packages = registry/packages, --out = registry/
// (Alle Argumente sind auch fuer Tests als Funktionsaufruf nutzbar, siehe
// buildIndex() unten -- registry/test/build-index.test.mjs importiert diese
// Funktion direkt, statt einen Kindprozess zu starten.)
//
// Nutzt @cfworker/json-schema (MIT, siehe THIRD-PARTY.md) -- dieselbe
// Bibliothek wie registry/schema/validate-examples.mjs, aus
// Konsistenzgruenden und weil Ajv wegen der transitiven BSD-3-Clause-
// Abhaengigkeit 'fast-uri' gegen Charter-Regel 5 verstoesst.

import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";
import { licenseWhitelistViolation } from "./license-policy.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const schemaDir = path.join(here, "schema");

function loadSchema(name) {
	return JSON.parse(readFileSync(path.join(schemaDir, name), "utf8"));
}

const packageSchema = loadSchema("registry-package.schema.json");
const packageValidator = new Validator(packageSchema, "2020-12");

// Fester, versionierter Katalog-Pfad -- siehe Kommentar am Dateianfang.
export const CATALOG_VERSION = "v1";

const SEMVER_RE =
	/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

function parseSemver(version) {
	const m = SEMVER_RE.exec(version);
	if (!m) return null;
	return {
		major: Number(m[1]),
		minor: Number(m[2]),
		patch: Number(m[3]),
		prerelease: m[4] ?? null,
	};
}

// Vergleich nach SemVer-2.0.0-Praezedenzregeln, ohne Build-Metadaten.
// Reicht fuer diesen Builder (nur zur Bestimmung von 'latestVersion'-
// Konsistenz und Sortierung innerhalb eines Pakets) -- kein vollstaendiger
// SemVer-Ranking-Vergleich mit gemischten alphanumerischen Praerelease-
// Identifiern noetig, da echte Registry-Eintraege durchgehend numerische
// oder rein alphabetische Praerelease-Segmente verwenden.
function compareSemver(a, b) {
	const pa = parseSemver(a);
	const pb = parseSemver(b);
	if (pa.major !== pb.major) return pa.major - pb.major;
	if (pa.minor !== pb.minor) return pa.minor - pb.minor;
	if (pa.patch !== pb.patch) return pa.patch - pb.patch;
	if (pa.prerelease === pb.prerelease) return 0;
	if (pa.prerelease === null) return 1; // Release schlaegt Praerelease
	if (pb.prerelease === null) return -1;
	return pa.prerelease < pb.prerelease ? -1 : 1;
}

function jsonFilesIn(dir) {
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(dir, f))
		.sort();
}

function truncate(text, maxLength) {
	if (text.length <= maxLength) return text;
	return text.slice(0, maxLength - 3).trimEnd() + "...";
}

function keywordsFor(pkg) {
	const words = new Set();
	const add = (s) => {
		for (const part of String(s).toLowerCase().split(/[^a-z0-9]+/)) {
			if (part.length > 0) words.add(part);
		}
	};
	add(pkg.id);
	add(pkg.name);
	add(pkg.author.name);
	for (const tag of pkg.tags ?? []) add(tag);
	return [...words].sort();
}

// Liest und validiert alle packages/<id>.json-Dateien. Wirft mit einer
// zusammengefassten Fehlermeldung, wenn eine Datei das Registry-Paket-
// Schema verletzt, der Dateiname nicht 'id.json' entspricht, eine 'id'
// doppelt vorkommt, oder 'latestVersion' zu keinem Eintrag in 'versions'
// passt -- ein kaputter Registry-Eintrag darf keinen halben Index bauen.
function loadPackages(packagesDir) {
	const packages = [];
	const seenIds = new Set();
	const errors = [];

	for (const file of jsonFilesIn(packagesDir)) {
		const base = path.basename(file, ".json");
		let data;
		try {
			data = JSON.parse(readFileSync(file, "utf8"));
		} catch (err) {
			errors.push(`${file}: ungueltiges JSON (${err.message})`);
			continue;
		}

		const result = packageValidator.validate(data);
		if (!result.valid) {
			errors.push(
				`${file}: verletzt registry-package.schema.json:\n` +
					result.errors.map((e) => `  - ${e.instanceLocation}: ${e.error}`).join("\n"),
			);
			continue;
		}

		if (data.id !== base) {
			errors.push(`${file}: Dateiname passt nicht zu id '${data.id}' (erwartet '${data.id}.json')`);
			continue;
		}
		if (seenIds.has(data.id)) {
			errors.push(`${file}: doppelte id '${data.id}'`);
			continue;
		}
		seenIds.add(data.id);

		const licenseIssue = licenseWhitelistViolation(data.license);
		if (licenseIssue) {
			errors.push(`${file}: ${licenseIssue}`);
			continue;
		}

		const versionValues = data.versions.map((v) => v.version);
		if (!versionValues.includes(data.latestVersion)) {
			errors.push(
				`${file}: latestVersion '${data.latestVersion}' ist in keinem Eintrag von 'versions' (${versionValues.join(", ")})`,
			);
			continue;
		}
		const maxVersion = versionValues.reduce((max, v) => (compareSemver(v, max) > 0 ? v : max));
		if (maxVersion !== data.latestVersion) {
			errors.push(
				`${file}: latestVersion '${data.latestVersion}' ist nicht die hoechste Version (hoechste waere '${maxVersion}')`,
			);
			continue;
		}

		packages.push(data);
	}

	if (errors.length > 0) {
		throw new Error(`Registry-Pakete ungueltig:\n${errors.join("\n")}`);
	}

	packages.sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
	return packages;
}

function buildIndexPages(packages, { pageSize, now }) {
	const totalCount = packages.length;
	const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
	const pages = [];

	for (let page = 1; page <= totalPages; page++) {
		const start = (page - 1) * pageSize;
		const slice = packages.slice(start, start + pageSize);
		const fileName = page === 1 ? "index.json" : `index-${page}.json`;
		const nextPage = page < totalPages ? `index-${page + 1}.json` : null;
		const previousPage = page > 1 ? (page - 1 === 1 ? "index.json" : `index-${page - 1}.json`) : null;

		pages.push({
			fileName,
			content: {
				schemaVersion: 1,
				generatedAt: now,
				page,
				pageSize,
				totalPages,
				totalCount,
				nextPage,
				previousPage,
				packages: slice.map((pkg) => {
					const summary = {
						id: pkg.id,
						name: pkg.name,
						author: pkg.author.name,
						category: pkg.category,
						// GATE101: 'license' in die Kurzuebersicht uebernehmen, damit
						// die Kachelansicht im Mod sie zeigen kann, ohne vorher die
						// Detailseite zu laden.
						license: pkg.license,
						latestVersion: pkg.latestVersion,
						updatedAt: pkg.updatedAt,
					};
					if (pkg.icon) summary.icon = pkg.icon;
					if (pkg.description) summary.shortDescription = truncate(pkg.description, 160);
					// GATE6/J2: 'javaMod' nur setzen, wenn true -- fehlendes Feld und
					// 'false' bedeuten fuer den Mod-Client dasselbe (Klasse A), siehe
					// registry-index-page.schema.json.
					if (pkg.contentClass === "javamod") summary.javaMod = true;
					// GATE70-FOLLOW: optionales Feld, nur setzen, wenn das Paket es
					// traegt -- siehe registry-package.schema.json, 'minecraftVersions'.
					if (pkg.minecraftVersions) summary.minecraftVersions = pkg.minecraftVersions;
					return summary;
				}),
			},
		});
	}

	return pages;
}

function buildSearch(packages, { now }) {
	return {
		schemaVersion: 1,
		generatedAt: now,
		totalCount: packages.length,
		entries: packages.map((pkg) => {
			const entry = {
				id: pkg.id,
				name: pkg.name,
				author: pkg.author.name,
				category: pkg.category,
				// GATE101: siehe buildIndexPages oben, derselbe Grund.
				license: pkg.license,
				keywords: keywordsFor(pkg),
			};
			if (pkg.tags) entry.tags = pkg.tags;
			return entry;
		}),
	};
}

// Baut eine kuratierte Liste aus dem gegebenen Paketfeld ('featured' oder
// 'popular') -- beide haben dieselbe Form {order, note?} im Paketeintrag und
// erzeugen dieselbe Listenform {id, order, note?} in featured.json (Schema:
// registry-featured.schema.json, $defs/curationItem). 'popular' ist -- wie
// 'featured' -- redaktionell von den Registry-Maintainern gepflegt, keine
// echte, gemessene Downloadzahl (Vitrine hat bewusst kein Backend).
function buildCurationList(packages, fieldName) {
	return packages
		.filter((pkg) => pkg[fieldName])
		.sort((a, b) => a[fieldName].order - b[fieldName].order)
		.map((pkg) => {
			// GATE101: 'license' auch hier mitgeben, siehe buildIndexPages oben.
			const item = { id: pkg.id, order: pkg[fieldName].order, license: pkg.license };
			if (pkg[fieldName].note) item.note = pkg[fieldName].note;
			return item;
		});
}

function buildFeatured(packages, { now }) {
	const items = buildCurationList(packages, "featured");
	const popular = buildCurationList(packages, "popular");
	return { schemaVersion: 1, generatedAt: now, items, popular };
}

function writeJson(filePath, data) {
	mkdirSync(path.dirname(filePath), { recursive: true });
	writeFileSync(filePath, JSON.stringify(data, null, 2) + "\n", "utf8");
}

// Baut den kompletten Katalog aus 'packagesDir' und schreibt ihn nach
// 'outDir'. Gibt die erzeugten Strukturen zurueck (fuer Tests), ohne dass
// erneut von der Platte gelesen werden muss.
export function buildIndex({ packagesDir, outDir, pageSize = 24, now = new Date().toISOString() }) {
	const packages = loadPackages(packagesDir);
	const pages = buildIndexPages(packages, { pageSize, now });
	const search = buildSearch(packages, { now });
	const featured = buildFeatured(packages, { now });

	const versionDir = path.join(outDir, "index", CATALOG_VERSION);
	for (const page of pages) {
		writeJson(path.join(versionDir, page.fileName), page.content);
	}
	writeJson(path.join(versionDir, "search.json"), search);
	writeJson(path.join(versionDir, "featured.json"), featured);

	return { packages, pages, search, featured };
}

function parseArgs(argv) {
	const args = { packagesDir: path.join(here, "packages"), outDir: here, pageSize: 24, now: undefined };
	for (let i = 0; i < argv.length; i++) {
		const arg = argv[i];
		if (arg === "--packages") args.packagesDir = path.resolve(argv[++i]);
		else if (arg === "--out") args.outDir = path.resolve(argv[++i]);
		else if (arg === "--page-size") args.pageSize = Number(argv[++i]);
		else if (arg === "--now") args.now = argv[++i];
		else throw new Error(`Unbekanntes Argument: ${arg}`);
	}
	return args;
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);
if (isMain) {
	const args = parseArgs(process.argv.slice(2));
	try {
		const result = buildIndex({
			packagesDir: args.packagesDir,
			outDir: args.outDir,
			pageSize: args.pageSize,
			now: args.now ?? new Date().toISOString(),
		});
		console.log(
			`OK: ${result.packages.length} Paket(e), ${result.pages.length} Index-Seite(n), ` +
				`${result.search.entries.length} Sucheintraege, ${result.featured.items.length} featured, ` +
				`${result.featured.popular.length} beliebt.`,
		);
		console.log(`Geschrieben nach: ${args.outDir}`);
	} catch (err) {
		console.error(err.message);
		process.exit(1);
	}
}
