// Test fuer registry/build-index.js (M4/K1).
//
// Laesst den Builder gegen die drei Beispielpakete unter registry/packages/
// laufen (Textur, Theme, HUD -- Klasse-A-Inhalte) und prueft jede erzeugte
// Datei gegen ihr Schema unter registry/schema/. Schreibt in ein temporaeres
// Verzeichnis, nicht nach registry/index/v1/ -- der Builder-Output ist kein
// eingecheckter Zustand dieses Repos (das Katalog-Repo 'vitrine-registry'
// entsteht erst in K7).
//
// Aufruf: node --test registry/test/build-index.test.mjs
// (vorher einmalig: npm install --prefix registry)

import { test } from "node:test";
import assert from "node:assert/strict";
import { copyFileSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Validator } from "@cfworker/json-schema";
import { buildIndex } from "../build-index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const livePackagesDir = path.join(registryRoot, "packages");
const schemaDir = path.join(registryRoot, "schema");

// Die 3 urspruenglichen K1-Beispielpakete, namentlich fest -- NICHT einfach
// "alles in registry/packages/" lesen. Sobald echte Pakete per `cli publish`
// (D9) per PR dazukommen, waechst registry/packages/ ueber diese 3 hinaus;
// dieser Test bleibt trotzdem bei den 3 urspruenglichen Fixtures, indem er
// sie in ein isoliertes Temp-Verzeichnis kopiert, statt registry/packages/
// direkt zu lesen.
const FIXTURE_PACKAGE_FILES = [
	"spassglas.kompakt-hud.json",
	"spassglas.mitternacht-theme.json",
	"spassglas.steinbrocken-textur.json",
];

function makeFixturePackagesDir(t) {
	const dir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-fixture-packages-"));
	t.after(() => rmSync(dir, { recursive: true, force: true }));
	for (const fileName of FIXTURE_PACKAGE_FILES) {
		copyFileSync(path.join(livePackagesDir, fileName), path.join(dir, fileName));
	}
	return dir;
}

function loadSchema(name) {
	return JSON.parse(readFileSync(path.join(schemaDir, name), "utf8"));
}

function validateOrThrow(validator, data, label) {
	const result = validator.validate(data);
	if (!result.valid) {
		throw new Error(`${label} verletzt sein Schema:\n${JSON.stringify(result.errors, null, 2)}`);
	}
}

test("Builder erzeugt aus den 3 Beispielpaketen einen gueltigen, paginierten Index", async (t) => {
	const packagesDir = makeFixturePackagesDir(t);
	const outDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-test-"));
	t.after(() => rmSync(outDir, { recursive: true, force: true }));

	const now = "2026-09-03T00:00:00.000Z";
	const result = buildIndex({ packagesDir, outDir, pageSize: 24, now });

	await t.test("liest genau 3 Beispielpakete", () => {
		assert.equal(result.packages.length, 3);
		assert.deepEqual(
			result.packages.map((p) => p.id),
			["spassglas.kompakt-hud", "spassglas.mitternacht-theme", "spassglas.steinbrocken-textur"],
		);
	});

	await t.test("index/index.json ist die einzige Seite (3 Pakete < pageSize) und gueltig", () => {
		assert.equal(result.pages.length, 1);
		const page = result.pages[0];
		assert.equal(page.fileName, "index.json");
		assert.equal(page.content.totalCount, 3);
		assert.equal(page.content.totalPages, 1);
		assert.equal(page.content.nextPage, null);
		assert.equal(page.content.previousPage, null);
		assert.equal(page.content.packages.length, 3);
		// GATE101: Kachelansicht im Mod braucht 'license' schon im Index, ohne
		// vorher die Detailseite zu laden.
		assert.ok(page.content.packages.every((p) => p.license === "CC-BY-4.0"));
		// GATE70-FOLLOW: 'minecraftVersions' ist optional -- nur
		// steinbrocken-textur traegt es in der Quelle, die anderen beiden
		// Fixtures bleiben ohne das Feld (kein erfundener Wert).
		const steinbrocken = page.content.packages.find((p) => p.id === "spassglas.steinbrocken-textur");
		assert.deepEqual(steinbrocken.minecraftVersions, ["1.21.1", "1.21.4"]);
		const others = page.content.packages.filter((p) => p.id !== "spassglas.steinbrocken-textur");
		assert.ok(others.every((p) => p.minecraftVersions === undefined));

		// GATE90-FOLLOW: 'capabilities' ist optional -- nur kompakt-hud traegt
		// es in der Quelle (registry/packages/spassglas.kompakt-hud.json), die
		// anderen beiden Fixtures bleiben ohne das Feld.
		const kompaktHud = page.content.packages.find((p) => p.id === "spassglas.kompakt-hud");
		assert.deepEqual(kompaktHud.capabilities, ["hud"]);
		const othersWithoutCapabilities = page.content.packages.filter((p) => p.id !== "spassglas.kompakt-hud");
		assert.ok(othersWithoutCapabilities.every((p) => p.capabilities === undefined));

		const validator = new Validator(loadSchema("registry-index-page.schema.json"), "2020-12");
		validateOrThrow(validator, page.content, "index/index.json");

		const onDisk = JSON.parse(readFileSync(path.join(outDir, "index", "v1", "index.json"), "utf8"));
		assert.deepEqual(onDisk, page.content);
	});

	await t.test("search.json ist gueltig und enthaelt alle 3 Pakete", () => {
		const validator = new Validator(loadSchema("registry-search.schema.json"), "2020-12");
		validateOrThrow(validator, result.search, "search.json");
		assert.equal(result.search.entries.length, 3);
		assert.ok(result.search.entries.every((e) => e.keywords.length > 0));
		// GATE101: siehe index/index.json oben, derselbe Grund.
		assert.ok(result.search.entries.every((e) => e.license === "CC-BY-4.0"));
		// GATE90-FOLLOW: siehe index/index.json oben, derselbe Grund.
		const hudEntry = result.search.entries.find((e) => e.id === "spassglas.kompakt-hud");
		assert.deepEqual(hudEntry.capabilities, ["hud"]);
		const otherEntries = result.search.entries.filter((e) => e.id !== "spassglas.kompakt-hud");
		assert.ok(otherEntries.every((e) => e.capabilities === undefined));

		const onDisk = JSON.parse(readFileSync(path.join(outDir, "index", "v1", "search.json"), "utf8"));
		assert.deepEqual(onDisk, result.search);
	});

	await t.test("featured.json ist gueltig und enthaelt die 2 'featured'-Pakete in order-Reihenfolge", () => {
		const validator = new Validator(loadSchema("registry-featured.schema.json"), "2020-12");
		validateOrThrow(validator, result.featured, "featured.json");
		assert.deepEqual(
			result.featured.items.map((i) => i.id),
			["spassglas.steinbrocken-textur", "spassglas.mitternacht-theme"],
		);
		// GATE101: siehe index/index.json oben, derselbe Grund.
		assert.ok(result.featured.items.every((i) => i.license === "CC-BY-4.0"));

		const onDisk = JSON.parse(readFileSync(path.join(outDir, "index", "v1", "featured.json"), "utf8"));
		assert.deepEqual(onDisk, result.featured);
	});

	await t.test("featured.json enthaelt die 2 'popular'-Pakete getrennt, in order-Reihenfolge", () => {
		assert.deepEqual(
			result.featured.popular.map((i) => i.id),
			["spassglas.mitternacht-theme", "spassglas.kompakt-hud"],
		);
		assert.equal(result.featured.popular[1].note, "Meistgenutztes HUD-Modul aus den Starter-Paketen.");
		// 'featured' und 'popular' sind unabhaengig: steinbrocken-textur ist
		// featured, aber nicht popular; mitternacht-theme ist beides.
		assert.ok(!result.featured.popular.some((i) => i.id === "spassglas.steinbrocken-textur"));
		// GATE90-FOLLOW: kompakt-hud ist 'popular' und traegt 'capabilities' in
		// der Quelle -- siehe index/index.json oben, derselbe Grund.
		const hudPopular = result.featured.popular.find((i) => i.id === "spassglas.kompakt-hud");
		assert.deepEqual(hudPopular.capabilities, ["hud"]);
	});
});

test("Builder paginiert bei kleiner pageSize ueber mehrere Seiten", async (t) => {
	const packagesDir = makeFixturePackagesDir(t);
	const outDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-test-page-"));
	t.after(() => rmSync(outDir, { recursive: true, force: true }));

	const now = "2026-09-03T00:00:00.000Z";
	const result = buildIndex({ packagesDir, outDir, pageSize: 1, now });
	const pageSchema = loadSchema("registry-index-page.schema.json");
	const validator = new Validator(pageSchema, "2020-12");

	assert.equal(result.pages.length, 3);
	for (const page of result.pages) {
		validateOrThrow(validator, page.content, page.fileName);
	}

	assert.equal(result.pages[0].fileName, "index.json");
	assert.equal(result.pages[0].content.nextPage, "index-2.json");
	assert.equal(result.pages[0].content.previousPage, null);

	assert.equal(result.pages[1].fileName, "index-2.json");
	assert.equal(result.pages[1].content.nextPage, "index-3.json");
	assert.equal(result.pages[1].content.previousPage, "index.json");

	assert.equal(result.pages[2].fileName, "index-3.json");
	assert.equal(result.pages[2].content.nextPage, null);
	assert.equal(result.pages[2].content.previousPage, "index-2.json");
});

test("Builder lehnt ein Registry-Paket ab, das sein Schema verletzt", async (t) => {
	const badPackagesDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-bad-packages-"));
	t.after(() => rmSync(badPackagesDir, { recursive: true, force: true }));

	const outDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-bad-out-"));
	t.after(() => rmSync(outDir, { recursive: true, force: true }));

	const fs = await import("node:fs");
	fs.writeFileSync(
		path.join(badPackagesDir, "kaputt.paket.json"),
		JSON.stringify({ schemaVersion: 1, id: "kaputt.paket" }),
	);

	assert.throws(() => buildIndex({ packagesDir: badPackagesDir, outDir }), /verletzt registry-package\.schema\.json/);
});

// GATE23: license ist Pflichtfeld UND muss auf der Whitelist stehen
// (registry/LICENSE-WHITELIST.md) -- geprueft ueber registry/license-policy.mjs,
// dieselbe Funktion laeuft in build-index.js UND (ueber buildIndex) in der
// PR-Pruefung validate-changed-packages.mjs.
test("Builder lehnt ein Registry-Paket mit nicht-whitelisteter license ab", async (t) => {
	const badPackagesDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-bad-license-"));
	t.after(() => rmSync(badPackagesDir, { recursive: true, force: true }));

	const outDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-bad-license-out-"));
	t.after(() => rmSync(outDir, { recursive: true, force: true }));

	const fs = await import("node:fs");
	fs.writeFileSync(
		path.join(badPackagesDir, "kaputt.lizenz.json"),
		JSON.stringify({
			schemaVersion: 1,
			id: "kaputt.lizenz",
			name: "Kaputte Lizenz",
			category: "texturen",
			author: { name: "Testautor" },
			license: "Alle Rechte vorbehalten",
			repository: "https://example.com/repo",
			versions: [
				{
					version: "1.0.0",
					vpkgUrl: "https://example.com/repo/releases/download/v1.0.0/kaputt.lizenz.vpkg",
					sha256: "a".repeat(64),
					signature: "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCGQkYh8q2I6cv8covRmS3g4bJS5VWl4zeQ6c54ilFrkZQ==",
					size: 1024,
					publishedAt: "2026-01-01T00:00:00.000Z",
				},
			],
			latestVersion: "1.0.0",
			updatedAt: "2026-01-01T00:00:00.000Z",
		}),
	);

	assert.throws(() => buildIndex({ packagesDir: badPackagesDir, outDir }), /steht ausdruecklich auf der Verbotsliste|nicht auf der Whitelist/);
});
