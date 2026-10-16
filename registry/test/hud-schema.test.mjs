// Test fuer registry/schema/hud.schema.json (M5/T5 -- Schema-Haelfte).
//
// Prueft:
//  1. Beide gueltigen Beispiel-HUD-Pakete (examples/hud/valid/*.json)
//     bestehen die Validierung.
//  2. Jedes ungueltige Beispiel (examples/hud/invalid/*.json) faellt durch.
//  3. Explizit das Fertig-Kriterium-Szenario aus der ROADMAP-Zeile T5 ("Ein
//     HUD-Paket zeigt FPS und Koordinaten, ohne eine Zeile Java im Paket"):
//     examples/hud/valid/fps-and-coordinates.json enthaelt eine FPS-Anzeige
//     oben links und eine Koordinaten-Liste unten links, beide beziehen ihre
//     Werte ausschliesslich ueber "source" aus der im Schema enumerierten
//     Datenquellen-Whitelist -- keine Java-Klasse, kein Dateipfad zu Code
//     kommt in der Datei vor.
//  4. Dass diese Datenquellen-Whitelist tatsaechlich geschlossen ist: ein
//     Widget, das statt eines Enum-Werts eine freie Formel als "source"
//     eintraegt (examples/hud/invalid/06-free-formula-as-source.json), wird
//     abgelehnt, und der Validierungsfehler betrifft nachweislich das
//     "source"-Feld (kein Zufallstreffer durch eine andere Regelverletzung).
//
// Geteilter Fixture-Anker: examples/hud/valid/designer-canonical-export.json
// wird vom Vitrine Designer (D5, HUD-Editor) byte-fuer-byte erzeugt --
// designer/test/hud-editor.test.js prueft die Byte-Identitaet gegen genau
// diese Datei. Der hier laufende Referenz-Validator (@cfworker/json-schema)
// bestaetigt zusaetzlich, dass dieser Designer-Export gegen hud.schema.json
// gueltig ist. Damit ist "im Designer gebautes HUD laedt der Mod unveraendert"
// auf Schema-Ebene geschlossen; die Mod-Laufzeit (coder-mod, T5-Mod-Teil)
// baut ihren HUD-Renderer direkt gegen diese Datei.
//
// Aufruf: node --test registry/test/hud-schema.test.mjs
// (vorher einmalig: npm install --prefix registry)

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");

const schemaPath = path.join(registryRoot, "schema", "hud.schema.json");
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));

const validDir = path.join(registryRoot, "schema", "examples", "hud", "valid");
const invalidDir = path.join(registryRoot, "schema", "examples", "hud", "invalid");

function jsonFilesIn(dir) {
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(dir, f));
}

test("hud.schema.json akzeptiert jedes gueltige Beispiel-HUD-Paket", () => {
	const files = jsonFilesIn(validDir);
	assert.ok(files.length >= 1, "erwarte mindestens ein gueltiges Beispiel-HUD-Paket");
	for (const file of files) {
		const validator = new Validator(schema, "2020-12");
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(
			result.valid,
			true,
			`${path.basename(file)} sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`,
		);
	}
});

test("hud.schema.json lehnt jedes ungueltige Beispiel ab", () => {
	const files = jsonFilesIn(invalidDir);
	assert.ok(files.length >= 3, "erwarte mindestens drei ungueltige Beispiele");
	for (const file of files) {
		const validator = new Validator(schema, "2020-12");
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(result.valid, false, `${path.basename(file)} sollte ungueltig sein, wurde aber akzeptiert.`);
	}
});

test("T5-Fertig-Szenario: FPS oben links + Koordinaten unten links, nur ueber die Datenquellen-Enum, keine Java-Referenz", () => {
	const file = path.join(validDir, "fps-and-coordinates.json");
	const data = JSON.parse(readFileSync(file, "utf8"));

	const validator = new Validator(schema, "2020-12");
	const result = validator.validate(data);
	assert.equal(result.valid, true, `Szenario-Datei sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`);

	const allowedSources = new Set(schema.$defs.dataSource.enum);
	assert.ok(allowedSources.has("fps"), "Datenquellen-Enum muss 'fps' enthalten");
	assert.ok(allowedSources.has("coordinates.x"), "Datenquellen-Enum muss 'coordinates.x' enthalten");
	assert.ok(allowedSources.has("coordinates.y"), "Datenquellen-Enum muss 'coordinates.y' enthalten");
	assert.ok(allowedSources.has("coordinates.z"), "Datenquellen-Enum muss 'coordinates.z' enthalten");

	const fpsWidget = data.widgets.find((w) => w.id === "fps-counter");
	assert.ok(fpsWidget, "erwarte ein Widget mit id 'fps-counter'");
	assert.equal(fpsWidget.anchor, "top-left");
	assert.equal(fpsWidget.content.kind, "dynamic");
	assert.equal(fpsWidget.content.source, "fps");
	assert.ok(allowedSources.has(fpsWidget.content.source));

	const coordsWidget = data.widgets.find((w) => w.id === "coordinates-list");
	assert.ok(coordsWidget, "erwarte ein Widget mit id 'coordinates-list'");
	assert.equal(coordsWidget.anchor, "bottom-left");
	assert.equal(coordsWidget.type, "list");
	assert.ok(coordsWidget.entries.length >= 3, "erwarte mindestens X/Y/Z als Eintraege");
	for (const entry of coordsWidget.entries) {
		assert.equal(entry.kind, "dynamic");
		assert.ok(allowedSources.has(entry.source), `Quelle '${entry.source}' muss in der Enum stehen`);
	}

	// Kein Java-Bezug irgendwo in der Datei (kein Klassenname, kein .java/.class-Pfad).
	const raw = JSON.stringify(data);
	assert.doesNotMatch(raw, /\.java|\.class|de\.spassglas/i, "HUD-Paket darf keine Java-Referenz enthalten");
});

test("die Datenquellen-Whitelist ist geschlossen: eine freie Formel als 'source' wird abgelehnt (nicht aus einem anderen Grund)", () => {
	const file = path.join(invalidDir, "06-free-formula-as-source.json");
	const data = JSON.parse(readFileSync(file, "utf8"));
	assert.equal(
		data.widgets[0].content.source,
		"player.x * 2 + 1",
		"Testdatei sollte tatsaechlich eine formelartige Zeichenkette als source enthalten",
	);

	const validator = new Validator(schema, "2020-12");
	const result = validator.validate(data);
	assert.equal(result.valid, false, "eine freie Formel als 'source' darf nicht als gueltiger Wert durchgehen");

	const touchesSource = result.errors.some((e) => e.instanceLocation.includes("/source") || e.keywordLocation.includes("dataSource"));
	assert.ok(
		touchesSource,
		`erwarte, dass mindestens ein Validierungsfehler das 'source'-Feld bzw. die dataSource-Enum betrifft: ${JSON.stringify(result.errors, null, 2)}`,
	);
});
