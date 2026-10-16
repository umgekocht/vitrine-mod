"use strict";

/*
 * Node-Test fuer den Browser-Validator (designer/schema-validate.js), ohne
 * echten Browser -- D1s Vorbild ("kein echter Browser in dieser Umgebung
 * verfuegbar: stattdessen node --check ... und ein Node-Test"). Die Datei
 * hat kein DOM-Bezug, laeuft deshalb unveraendert unter Node.
 *
 * Prueft D2s Fertig-Kriterium woertlich: fuer alle zwoelf kaputten
 * Beispielmanifeste unter registry/schema/examples/invalid/ (dieselben
 * Dateien, die ManifestParserTest.java auf der Mod-Seite prueft) nennt
 * dieser Validator dieselbe Problembenennung wie der Java-Parser --
 * dieselben Assertions (welches Feld, welche Verstossart) wie in
 * mod/src/test/java/de/spassglas/vitrine/manifest/ManifestParserTest.java,
 * nicht zwingend derselbe Wortlaut.
 *
 * Aufruf: node --test designer/test/schema-validate.test.js
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const validator = require("../schema-validate.js");

const repoRoot = path.join(__dirname, "..", "..");
const schemaPath = path.join(repoRoot, "registry", "schema", "vitrine.schema.json");
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));
const generatedSchema = require("../schema-data.js");

const examplesDir = path.join(repoRoot, "registry", "schema", "examples");

function readExample(sub, name) {
  return JSON.parse(fs.readFileSync(path.join(examplesDir, sub, name), "utf8"));
}

test("designer/schema-data.js ist inhaltlich identisch zu registry/schema/vitrine.schema.json", () => {
  assert.deepEqual(generatedSchema, schema, "schema-data.js muss neu erzeugt werden: node registry/schema/generate-designer-schema.mjs");
});

test("beide gueltigen Beispielmanifeste werden akzeptiert (keine Verstoesse)", () => {
  assert.deepEqual(validator.validate(schema, readExample("valid", "minimal.json")), []);
  assert.deepEqual(validator.validate(schema, readExample("valid", "full.json")), []);
});

test("alle ungueltigen Beispiele liefern je eine unterscheidbare, spezifische Meldung (keine pauschale 'ungueltig')", () => {
  const files = fs.readdirSync(path.join(examplesDir, "invalid")).filter((f) => f.endsWith(".json")).sort();
  assert.ok(files.length >= 10, "erwarte mindestens 10 kaputte Beispielmanifeste, gefunden: " + files.length);

  const seenMessages = new Set();
  for (const file of files) {
    const instance = readExample("invalid", file);
    const messages = validator.validate(schema, instance);
    assert.ok(messages.length > 0, file + " haette abgelehnt werden muessen");
    for (const message of messages) {
      const normalized = message.trim().toLowerCase();
      assert.notEqual(normalized, "invalid", file + " lieferte nur eine pauschale Meldung");
      assert.notEqual(normalized, "ungueltig", file + " lieferte nur eine pauschale Meldung");
    }
    const joined = messages.join("; ");
    assert.ok(!seenMessages.has(joined), file + " lieferte dieselbe Meldung wie eine andere Datei: " + joined);
    seenMessages.add(joined);
  }
  assert.equal(seenMessages.size, files.length);
});

// Die folgenden Faelle spiegeln 1:1 die Assertions aus
// ManifestParserTest.java (gleiches Feld, gleiche Verstossart je Datei) --
// das ist die Pruefung fuer "dieselbe Problembenennung wie der Java-Parser".

test("01-missing-required-field: benennt 'author' + 'fehlt' (wie Java missingRequiredFieldIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "01-missing-required-field.json"));
  assert.ok(messages.some((m) => m.includes("author") && m.includes("fehlt")), messages.join(" | "));
});

test("02-wrong-type-schemaversion: benennt 'schemaVersion' + 'Typ' (wie Java wrongTypeIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "02-wrong-type-schemaversion.json"));
  assert.ok(messages.some((m) => m.includes("schemaVersion") && m.includes("Typ")), messages.join(" | "));
});

test("03-unknown-additional-field: benennt 'preis' + 'Unbekannt' (wie Java unknownAdditionalFieldIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "03-unknown-additional-field.json"));
  assert.ok(messages.some((m) => m.includes("preis") && m.includes("Unbekannt")), messages.join(" | "));
});

test("04-invalid-category-enum: benennt 'category' + 'textures' (wie Java invalidCategoryEnumIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "04-invalid-category-enum.json"));
  assert.ok(messages.some((m) => m.includes("category") && m.includes("textures")), messages.join(" | "));
});

test("05-invalid-semver: benennt 'version' + 'Muster' (wie Java invalidSemverIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "05-invalid-semver.json"));
  assert.ok(messages.some((m) => m.includes("version") && m.includes("Muster")), messages.join(" | "));
});

test("06-invalid-id-pattern: benennt 'id' + 'Muster' (wie Java invalidIdPatternIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "06-invalid-id-pattern.json"));
  assert.ok(messages.some((m) => m.includes("'id'") && m.includes("Muster")), messages.join(" | "));
});

test("07-empty-name: benennt 'name' + 'leer' (wie Java emptyNameIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "07-empty-name.json"));
  assert.ok(messages.some((m) => m.includes("name") && m.includes("leer")), messages.join(" | "));
});

test("08-capabilities-wrong-type: benennt 'capabilities' + 'Typ' (wie Java capabilitiesWrongTypeIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "08-capabilities-wrong-type.json"));
  assert.ok(messages.some((m) => m.includes("capabilities") && m.includes("Typ")), messages.join(" | "));
});

test("09-capabilities-item-invalid-pattern: benennt 'capabilities[0]' konkret + Enum-Verstoss (wie Java capabilitiesItemNotWhitelistedIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "09-capabilities-item-invalid-pattern.json"));
  assert.ok(messages.some((m) => m.includes("capabilities[0]") && m.includes("erlaubt sind")), messages.join(" | "));
});

test("13-capabilities-dotted-namespace: 'hud.read' (nach altem Schema gueltig) wird als 'capabilities[0]' + Enum-Verstoss abgelehnt (G2)", () => {
  const messages = validator.validate(schema, readExample("invalid", "13-capabilities-dotted-namespace.json"));
  assert.ok(messages.some((m) => m.includes("capabilities[0]") && m.includes("hud.read") && m.includes("erlaubt sind")), messages.join(" | "));
});

test("capabilities: ['hud'] (einer der sieben Whitelist-Werte) ist gueltig", () => {
  const instance = readExample("valid", "minimal.json");
  instance.capabilities = ["hud"];
  assert.deepEqual(validator.validate(schema, instance), []);
});

test("capabilities: ['unbekannt'] ist ungueltig, Meldung haengt an 'capabilities[0]'", () => {
  const instance = readExample("valid", "minimal.json");
  instance.capabilities = ["unbekannt"];
  const messages = validator.validate(schema, instance);
  assert.ok(messages.some((m) => m.includes("capabilities[0]") && m.includes("erlaubt sind")), messages.join(" | "));
});

test("10-schemaversion-wrong-value: benennt 'schemaVersion' + '2' (wie Java schemaVersionWrongValueIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "10-schemaversion-wrong-value.json"));
  assert.ok(messages.some((m) => m.includes("schemaVersion") && m.includes("2")), messages.join(" | "));
});

test("11-icon-path-traversal: benennt 'icon' + '..' (wie Java iconPathTraversalIsNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "11-icon-path-traversal.json"));
  assert.ok(messages.some((m) => m.includes("icon") && m.includes("..")), messages.join(" | "));
});

test("12-multiple-missing-fields: benennt 'id','name','version' gemeinsam + 'fehlen' (wie Java multipleMissingFieldsAreAllNamedExplicitly)", () => {
  const messages = validator.validate(schema, readExample("invalid", "12-multiple-missing-fields.json"));
  assert.ok(
    messages.some((m) => m.includes("id") && m.includes("name") && m.includes("version") && m.includes("fehlen")),
    messages.join(" | ")
  );
});

test("validateDetailed liefert bei mehreren fehlenden Pflichtfeldern je einen eigenen Eintrag (fuer die Live-Anzeige am Feld)", () => {
  const entries = validator.validateDetailed(schema, readExample("invalid", "12-multiple-missing-fields.json"));
  const requiredFields = entries.filter((e) => e.kind === "required").map((e) => e.field).sort();
  assert.deepEqual(requiredFields, ["id", "name", "version"]);
});

test("baseFieldName loest Array-Indizes auf (capabilities[0] -> capabilities)", () => {
  assert.equal(validator.baseFieldName("capabilities[0]"), "capabilities");
  assert.equal(validator.baseFieldName("id"), "id");
});

/* ------------------------------------------------------------------ *
 * GATE6 -- Klasse B (Java-Mod, .jar, Neustart noetig).
 *
 * Der Validator hier und der Registry-Validator (@cfworker, Node) lesen
 * dieselbe Datei registry/schema/vitrine.schema.json. Dieser Block prueft
 * deshalb nicht das Schema (das tut registry/test/vitrine-schema.test.mjs),
 * sondern dass der Browser-Auswerter die dafuer noetigen Schluesselwoerter
 * allOf/if/then/else/not/minimum/maximum tatsaechlich auswertet -- ohne sie
 * wuerde er ein Klasse-A-Paket mit heimlicher .jar stillschweigend
 * durchwinken, und das waere schlimmer als gar keine Pruefung.
 * ------------------------------------------------------------------ */

const KLASSE_B = {
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
      size: 20480
    }
  }
};

test("GATE6: vollstaendiges Klasse-B-Manifest ist gueltig, Klasse A ohne contentClass bleibt gueltig", () => {
  assert.deepEqual(validator.validate(schema, KLASSE_B), []);
  assert.deepEqual(
    validator.validate(schema, {
      schemaVersion: 1, id: "beispiel.alt", name: "Alt", version: "1.0.0",
      category: "texturen", author: "A", license: "CC0-1.0"
    }),
    []
  );
});

test("GATE6: 'not' schlaegt an -- Klasse A mit javaMod-Block wird mit Klartext abgelehnt", () => {
  const geschmuggelt = JSON.parse(JSON.stringify(KLASSE_B));
  geschmuggelt.contentClass = "resource";
  geschmuggelt.hotApply = true;

  const entries = validator.validateDetailed(schema, geschmuggelt);
  const treffer = entries.find((e) => e.kind === "not");
  assert.ok(treffer, JSON.stringify(entries));
  assert.equal(treffer.field, "javaMod", "die Meldung muss am Feld javaMod haengen, nicht an der Wurzel");
  assert.match(treffer.message, /javaMod/);
});

test("GATE6: 'then' schlaegt an -- Klasse B ohne Pruefsumme und ohne javaMod-Block wird abgelehnt", () => {
  const ohneSha = JSON.parse(JSON.stringify(KLASSE_B));
  delete ohneSha.javaMod.jar.sha256;
  const entries = validator.validateDetailed(schema, ohneSha);
  assert.ok(
    entries.some((e) => e.kind === "required" && e.field === "javaMod.jar.sha256"),
    JSON.stringify(entries)
  );

  const ohneBlock = JSON.parse(JSON.stringify(KLASSE_B));
  delete ohneBlock.javaMod;
  assert.ok(
    validator.validateDetailed(schema, ohneBlock).some((e) => e.kind === "required" && e.field === "javaMod"),
    "Klasse B ohne javaMod-Block muss auffallen"
  );
});

test("GATE6: Klasse B darf hotApply nicht auf true luegen", () => {
  const luegt = JSON.parse(JSON.stringify(KLASSE_B));
  luegt.hotApply = true;
  const entries = validator.validateDetailed(schema, luegt);
  assert.ok(
    entries.some((e) => e.kind === "const" && e.field === "hotApply"),
    JSON.stringify(entries)
  );
});

test("GATE6: minimum/maximum werden ausgewertet (javaMod.jar.size)", () => {
  const zuKlein = JSON.parse(JSON.stringify(KLASSE_B));
  zuKlein.javaMod.jar.size = 0;
  assert.ok(
    validator.validateDetailed(schema, zuKlein).some((e) => e.kind === "minimum" && e.field === "javaMod.jar.size"),
    "size 0 muss auffallen"
  );
});

test("GATE6: derselbe Befund fuer alle vier Klasse-B-Beispieldateien wie im Registry-Validator", () => {
  const examplesDir = path.join(repoRoot, "registry", "schema", "examples");
  const erwartetUngueltig = [
    "14-klasse-a-mit-jar.json",
    "15-klasse-b-ohne-sha256.json",
    "16-klasse-b-hotapply-true.json",
    "17-klasse-b-ohne-javamod-block.json"
  ];
  for (const datei of erwartetUngueltig) {
    const daten = JSON.parse(fs.readFileSync(path.join(examplesDir, "invalid", datei), "utf8"));
    assert.ok(validator.validate(schema, daten).length > 0, datei + " muss abgelehnt werden");
  }
  const gueltig = JSON.parse(fs.readFileSync(path.join(examplesDir, "valid", "klasse-b-javamod.json"), "utf8"));
  assert.deepEqual(validator.validate(schema, gueltig), []);
});
