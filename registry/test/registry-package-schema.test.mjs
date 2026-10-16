// Test fuer registry/schema/registry-package.schema.json (GATE6/J2).
//
// Deckt die contentClass-Unterscheidung zwischen Klasse A ("resource", oder
// das Feld fehlt -- Rueckwaertskompatibilitaet) und Klasse B ("javamod", ein
// .jar-Angebot mit jarUrl/fileName statt vpkgUrl) ab:
//  1. Ein bestehendes Klasse-A-Paket ohne 'contentClass' (registry/packages/)
//     validiert unveraendert.
//  2. Die Beispiele unter schema/examples/registry-package/valid/ und
//     .../invalid/ verhalten sich wie ihr Ordnername verspricht.
//  3. Explizite Kreuzproben: ein 'javamod'-Paket mit 'vpkgUrl' statt
//     'jarUrl' wird abgelehnt, ein 'resource'-Paket mit 'jarUrl' statt
//     'vpkgUrl' wird abgelehnt.
//
// Aufruf: node --test registry/test/registry-package-schema.test.mjs
// (vorher einmalig: npm install --prefix registry)

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const schemaPath = path.join(registryRoot, "schema", "registry-package.schema.json");
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));

function jsonFilesIn(dir) {
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(dir, f));
}

function validPackage(overrides = {}) {
	return {
		schemaVersion: 1,
		id: "beispiel.testfixture",
		name: "Testfixture",
		category: "hud",
		author: { name: "Vitrine-Testfixture" },
		license: "MIT",
		repository: "https://example.com/vitrine-testfixture/testfixture",
		versions: [
			{
				version: "1.0.0",
				vpkgUrl: "https://example.com/vitrine-testfixture/testfixture/releases/download/v1.0.0/testfixture.vpkg",
				sha256: "a".repeat(64),
				signature: "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCGQkYh8q2I6cv8covRmS3g4bJS5VWl4zeQ6c54ilFrkZQ==",
				size: 1024,
				publishedAt: "2026-01-01T00:00:00.000Z",
			},
		],
		latestVersion: "1.0.0",
		updatedAt: "2026-01-01T00:00:00.000Z",
		...overrides,
	};
}

test("bestehendes Klasse-A-Paket ohne 'contentClass' validiert unveraendert (Rueckwaertskompatibilitaet)", () => {
	const validator = new Validator(schema, "2020-12");
	const packagesDir = path.join(registryRoot, "packages");
	const files = jsonFilesIn(packagesDir);
	assert.ok(files.length >= 1, "erwarte mindestens ein bestehendes Registry-Paket");
	for (const file of files) {
		const data = JSON.parse(readFileSync(file, "utf8"));
		assert.equal(data.contentClass, undefined, `${path.basename(file)} sollte kein 'contentClass' haben (Fixture-Annahme)`);
		const result = validator.validate(data);
		assert.equal(
			result.valid,
			true,
			`${path.basename(file)} sollte weiterhin gueltig sein: ${JSON.stringify(result.errors, null, 2)}`,
		);
	}
});

test("registry-package.schema.json akzeptiert jedes Beispiel unter examples/registry-package/valid/", () => {
	const validator = new Validator(schema, "2020-12");
	const validDir = path.join(registryRoot, "schema", "examples", "registry-package", "valid");
	const files = jsonFilesIn(validDir);
	assert.ok(files.length >= 1, "erwarte mindestens ein gueltiges javamod-Beispiel");
	for (const file of files) {
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(
			result.valid,
			true,
			`${path.basename(file)} sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`,
		);
	}
});

test("registry-package.schema.json lehnt jedes Beispiel unter examples/registry-package/invalid/ ab", () => {
	const invalidDir = path.join(registryRoot, "schema", "examples", "registry-package", "invalid");
	const files = jsonFilesIn(invalidDir);
	assert.ok(files.length >= 2, "erwarte mindestens zwei ungueltige Beispiele");
	for (const file of files) {
		const validator = new Validator(schema, "2020-12");
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(result.valid, false, `${path.basename(file)} sollte ungueltig sein, wurde aber akzeptiert.`);
	}
});

test("ein gueltiges 'javamod'-Paket (jarUrl/fileName statt vpkgUrl) validiert", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({
		contentClass: "javamod",
		versions: [
			{
				version: "1.0.0",
				jarUrl: "https://example.com/vitrine-testfixture/testfixture/releases/download/v1.0.0/testfixture.jar",
				fileName: "testfixture.jar",
				sha256: "a".repeat(64),
				signature: "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCGQkYh8q2I6cv8covRmS3g4bJS5VWl4zeQ6c54ilFrkZQ==",
				size: 1024,
				publishedAt: "2026-01-01T00:00:00.000Z",
				dependencies: ["fabric-api"],
			},
		],
	});
	const result = validator.validate(data);
	assert.equal(result.valid, true, `sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`);
});

test("ein 'javamod'-Paket mit 'vpkgUrl' statt 'jarUrl' wird abgelehnt", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({
		contentClass: "javamod",
		versions: [
			{
				version: "1.0.0",
				vpkgUrl: "https://example.com/vitrine-testfixture/testfixture/releases/download/v1.0.0/testfixture.vpkg",
				sha256: "a".repeat(64),
				signature: "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCGQkYh8q2I6cv8covRmS3g4bJS5VWl4zeQ6c54ilFrkZQ==",
				size: 1024,
				publishedAt: "2026-01-01T00:00:00.000Z",
			},
		],
	});
	const result = validator.validate(data);
	assert.equal(result.valid, false, "sollte ungueltig sein (javamod braucht jarUrl+fileName, nicht vpkgUrl)");
});

test("ein 'resource'-Paket mit 'jarUrl' statt 'vpkgUrl' wird abgelehnt", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({
		versions: [
			{
				version: "1.0.0",
				jarUrl: "https://example.com/vitrine-testfixture/testfixture/releases/download/v1.0.0/testfixture.jar",
				fileName: "testfixture.jar",
				sha256: "a".repeat(64),
				signature: "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCGQkYh8q2I6cv8covRmS3g4bJS5VWl4zeQ6c54ilFrkZQ==",
				size: 1024,
				publishedAt: "2026-01-01T00:00:00.000Z",
			},
		],
	});
	const result = validator.validate(data);
	assert.equal(result.valid, false, "sollte ungueltig sein (resource/Klasse A braucht vpkgUrl, nicht jarUrl)");
});

test("ein 'resource'-Paket mit explizitem contentClass:'resource' validiert wie ohne das Feld", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({ contentClass: "resource", versions: [{ ...validPackage().versions[0], vpkgUrl: "https://example.com/vitrine-testfixture/testfixture/releases/download/v1.0.0/testfixture.vpkg" }] });
	const result = validator.validate(data);
	assert.equal(result.valid, true, `sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`);
});

// GATE70-FOLLOW: 'minecraftVersions' ist optional, nicht an 'contentClass'
// gebunden -- siehe registry-package.schema.json.
test("ein Paket ohne 'minecraftVersions' validiert weiterhin (optionales Feld)", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage();
	assert.equal(data.minecraftVersions, undefined);
	const result = validator.validate(data);
	assert.equal(result.valid, true, `sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`);
});

test("ein Paket mit gueltigem 'minecraftVersions' validiert", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({ minecraftVersions: ["1.21.1", "1.21.4"] });
	const result = validator.validate(data);
	assert.equal(result.valid, true, `sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`);
});

test("ein Paket mit leerem 'minecraftVersions' (minItems 1) wird abgelehnt", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({ minecraftVersions: [] });
	const result = validator.validate(data);
	assert.equal(result.valid, false, "sollte ungueltig sein (minItems 1)");
});

test("ein Paket mit falsch getyptem 'minecraftVersions'-Eintrag (Zahl statt String) wird abgelehnt", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({ minecraftVersions: [1.21] });
	const result = validator.validate(data);
	assert.equal(result.valid, false, "sollte ungueltig sein (Eintrag muss ein String sein)");
});

test("ein Paket mit falsch gemustertem 'minecraftVersions'-Eintrag wird abgelehnt", () => {
	const validator = new Validator(schema, "2020-12");
	const data = validPackage({ minecraftVersions: ["v1.21.1"] });
	const result = validator.validate(data);
	assert.equal(result.valid, false, "sollte ungueltig sein (kein 'v'-Praefix erlaubt)");
});
