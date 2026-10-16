// Test fuer registry/schema/vitrine.schema.json (Paketmanifest).
//
// Deckt zwei Dinge ab, die validate-examples.mjs (Skript, kein --test) nur
// als CLI-Lauf prueft:
//   1. Alle Beispiele unter examples/valid|invalid/ verhalten sich wie
//      erwartet (mind. 13 invalide, siehe README).
//   2. G2 -- `capabilities` ist ein geschlossenes enum aus genau neun
//      Werten (die `category`-Werte ohne `bundle`, plus `verhalten` und
//      `skript` fuer Addons, AD4). `["hud"]` gueltig, `["hud.read"]` bzw.
//      `["unbekannt"]` ungueltig, Meldung an #/capabilities/0. Dieselben
//      neun Werte prueft die Mod-Laufzeit (VpkgCapabilityGrant /
//      ManifestParser).

import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const schemaDir = path.join(here, "..", "schema");
const schema = JSON.parse(readFileSync(path.join(schemaDir, "vitrine.schema.json"), "utf8"));
const validator = new Validator(schema, "2020-12");

function examplesIn(sub) {
	const dir = path.join(schemaDir, "examples", sub);
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => ({ name: f, data: JSON.parse(readFileSync(path.join(dir, f), "utf8")) }));
}

const CAPABILITY_ENUM = ["texturen", "theme", "hud", "shader", "sound", "sprache", "cosmetics", "verhalten", "skript"];

test("capabilities.items ist ein enum mit genau den neun erlaubten Werten (category ohne bundle, plus verhalten/skript fuer Addons)", () => {
	assert.deepEqual(schema.properties.capabilities.items.enum, CAPABILITY_ENUM);
	assert.equal(schema.properties.capabilities.items.pattern, undefined, "kein pattern mehr -- geschlossenes enum (G2)");
	assert.equal(schema.properties.capabilities.uniqueItems, true);
});

test("alle examples/valid sind gueltig", () => {
	for (const { name, data } of examplesIn("valid")) {
		const r = validator.validate(data);
		assert.ok(r.valid, name + " sollte gueltig sein: " + JSON.stringify(r.errors));
	}
});

test("alle examples/invalid sind ungueltig (mind. 17 Dateien)", () => {
	const files = examplesIn("invalid");
	assert.ok(files.length >= 17, "erwarte mind. 17 kaputte Beispielmanifeste, gefunden: " + files.length);
	for (const { name, data } of files) {
		assert.ok(!validator.validate(data).valid, name + " sollte ungueltig sein");
	}
});

// T7: auslieferbare Beispiel-Paketquellen (unentpackte Ordner) unter
// examples/<kategorie>/valid/<id>/. Jeder Ordner mit einer vitrine.json ist
// ein vollstaendiges Paketmanifest und muss gegen vitrine.schema.json
// gueltig sein; der Ordnername ist die id (gleiche Konvention wie
// build-index.js fuer registry/packages/). Kategorien wie hud/theme haben
// hier nur Config-JSON und keine vitrine.json -- die werden uebersprungen.
function packageExampleManifests() {
	const examplesRoot = path.join(schemaDir, "examples");
	const found = [];
	for (const kat of readdirSync(examplesRoot, { withFileTypes: true })) {
		if (!kat.isDirectory()) continue;
		const validDir = path.join(examplesRoot, kat.name, "valid");
		if (!existsSync(validDir)) continue;
		for (const entry of readdirSync(validDir, { withFileTypes: true })) {
			if (!entry.isDirectory()) continue;
			const manifestPath = path.join(validDir, entry.name, "vitrine.json");
			if (!existsSync(manifestPath)) continue;
			found.push({
				rel: `${kat.name}/valid/${entry.name}/vitrine.json`,
				dirName: entry.name,
				data: JSON.parse(readFileSync(manifestPath, "utf8")),
			});
		}
	}
	return found;
}

test("T7: examples/*/valid/<id>/vitrine.json sind gueltige Paketmanifeste, Ordnername == id, sound + sprache vertreten", () => {
	const manifests = packageExampleManifests();
	const cats = new Set(manifests.map((m) => m.data.category));
	assert.ok(cats.has("sound"), "erwarte eine Beispiel-Paketquelle mit category 'sound'");
	assert.ok(cats.has("sprache"), "erwarte eine Beispiel-Paketquelle mit category 'sprache'");
	for (const { rel, dirName, data } of manifests) {
		const r = validator.validate(data);
		assert.ok(r.valid, rel + " sollte gueltig sein: " + JSON.stringify(r.errors));
		assert.equal(data.id, dirName, rel + ": Ordnername muss der Paket-id entsprechen");
	}
});

test("13-capabilities-dotted-namespace: 'hud.read' wird abgelehnt, Fehler an #/capabilities/0", () => {
	const data = JSON.parse(readFileSync(path.join(schemaDir, "examples", "invalid", "13-capabilities-dotted-namespace.json"), "utf8"));
	const r = validator.validate(data);
	assert.ok(!r.valid);
	assert.ok(r.errors.some((e) => e.instanceLocation === "#/capabilities/0" && e.keyword === "enum"), JSON.stringify(r.errors));
});

// GATE6 -- Klasse B (Java-Mod, .jar, Neustart noetig). Dieselbe Vokabel wie im
// Katalog-Eintrag: contentClass 'resource' (Default) gegen 'javamod'. Die
// Regeln stehen im 'allOf' am Ende von vitrine.schema.json; sie sind der
// einzige Ort im Manifest-Schema, an dem mehrere Felder verknuepft sind.

const KLASSE_B_BASIS = {
	schemaVersion: 1,
	id: "beispiel.klasse-b",
	name: "Klasse B",
	version: "1.0.0",
	category: "hud",
	author: "A",
	license: "MIT",
	contentClass: "javamod",
	hotApply: false,
	javaMod: {
		loader: "fabric",
		minecraftVersions: ["1.21.1"],
		jar: {
			fileName: "klasse-b-1.0.0.jar",
			sha256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			size: 20480,
		},
	},
};

function ohne(obj, ...pfad) {
	const kopie = structuredClone(obj);
	let node = kopie;
	for (const segment of pfad.slice(0, -1)) node = node[segment];
	delete node[pfad[pfad.length - 1]];
	return kopie;
}

test("GATE6: contentClass ist ein geschlossenes enum ['resource','javamod'], gleiche Werte wie registry-package.schema.json", () => {
	assert.deepEqual(schema.properties.contentClass.enum, ["resource", "javamod"]);
	const registryPackage = JSON.parse(readFileSync(path.join(schemaDir, "registry-package.schema.json"), "utf8"));
	assert.deepEqual(
		schema.properties.contentClass.enum,
		registryPackage.properties.contentClass.enum,
		"eine Vokabel, zwei Schemas -- kein zweites Wort fuer dieselbe Sache (Charter-Regel 8)"
	);
});

test("GATE6: ein vollstaendiges Klasse-B-Manifest ist gueltig", () => {
	const r = validator.validate(KLASSE_B_BASIS);
	assert.ok(r.valid, JSON.stringify(r.errors));
});

test("GATE6: fehlendes contentClass bleibt Klasse A -- jedes bestehende Manifest gilt unveraendert weiter", () => {
	const klasseA = { schemaVersion: 1, id: "beispiel.alt", name: "Alt", version: "1.0.0", category: "texturen", author: "A", license: "CC0-1.0" };
	assert.ok(validator.validate(klasseA).valid);
	assert.ok(validator.validate({ ...klasseA, contentClass: "resource" }).valid);
	assert.ok(validator.validate({ ...klasseA, hotApply: true }).valid);
});

test("GATE6: Klasse A mit .jar wird abgelehnt -- 'javaMod' ist nur bei contentClass 'javamod' erlaubt", () => {
	const geschmuggelt = { ...KLASSE_B_BASIS, contentClass: "resource", hotApply: true };
	assert.ok(!validator.validate(geschmuggelt).valid);

	const ohneContentClass = ohne(geschmuggelt, "contentClass");
	assert.ok(!validator.validate(ohneContentClass).valid, "auch ohne contentClass gilt Klasse A");
});

test("GATE6: Klasse B ohne jar.sha256 wird abgelehnt (ohne Pruefsumme wird nie gestagt)", () => {
	const r = validator.validate(ohne(KLASSE_B_BASIS, "javaMod", "jar", "sha256"));
	assert.ok(!r.valid);
	assert.ok(
		r.errors.some((e) => e.instanceLocation === "#/javaMod/jar" && e.keyword === "required"),
		JSON.stringify(r.errors)
	);
});

test("GATE6: Klasse B muss hotApply false sagen -- true und 'fehlt' sind beide ungueltig", () => {
	assert.ok(!validator.validate({ ...KLASSE_B_BASIS, hotApply: true }).valid);
	assert.ok(!validator.validate(ohne(KLASSE_B_BASIS, "hotApply")).valid);
});

test("GATE6: Klasse B ohne javaMod-Block wird abgelehnt", () => {
	assert.ok(!validator.validate(ohne(KLASSE_B_BASIS, "javaMod")).valid);
});

test("GATE6: javaMod.jar.fileName haertet wie JavaModOffer -- Pfadtrenner, '..' und Nicht-Jar werden abgelehnt", () => {
	for (const boeser of ["../evil.jar", "unter/ordner.jar", "C:evil.jar", ".versteckt.jar", "keine-jar.zip"]) {
		const daten = structuredClone(KLASSE_B_BASIS);
		daten.javaMod.jar.fileName = boeser;
		assert.ok(!validator.validate(daten).valid, boeser + " sollte abgelehnt werden");
	}
});

test("GATE6: javaMod verlangt loader aus dem enum und mindestens eine Minecraft-Version", () => {
	const falscherLoader = structuredClone(KLASSE_B_BASIS);
	falscherLoader.javaMod.loader = "forge";
	assert.ok(!validator.validate(falscherLoader).valid);

	const ohneVersion = structuredClone(KLASSE_B_BASIS);
	ohneVersion.javaMod.minecraftVersions = [];
	assert.ok(!validator.validate(ohneVersion).valid);
});

test("GATE6: javaMod ist geschlossen -- ein unbekanntes Feld (z. B. 'signature') wird abgelehnt", () => {
	const mitSignatur = structuredClone(KLASSE_B_BASIS);
	mitSignatur.javaMod.signature = "cUUn6jiavQB8sqnWFcXQvmZWp/yQEp1h1sAuHnW0fCE=";
	assert.ok(
		!validator.validate(mitSignatur).valid,
		"signiert wird von der Registry, nicht vom Paketautor -- ein Signaturfeld hier waere ein Vertrauensanker, den niemand haelt"
	);
});

test("G2: capabilities ['hud'] gueltig, ['hud.read'] und ['unbekannt'] ungueltig am Feld capabilities", () => {
	const base = { schemaVersion: 1, id: "beispiel.caps", name: "Caps", version: "1.0.0", category: "hud", author: "A", license: "CC0-1.0" };

	assert.ok(validator.validate({ ...base, capabilities: ["hud"] }).valid);

	for (const bad of [["hud.read"], ["unbekannt"]]) {
		const r = validator.validate({ ...base, capabilities: bad });
		assert.ok(!r.valid, JSON.stringify(bad) + " sollte ungueltig sein");
		assert.ok(
			r.errors.some((e) => e.instanceLocation === "#/capabilities/0" && e.keyword === "enum"),
			JSON.stringify(bad) + ": " + JSON.stringify(r.errors)
		);
	}
});
