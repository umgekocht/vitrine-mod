"use strict";

/*
 * Node-Test fuer die Formular-Anbindung (designer/manifest-form.js), ohne
 * echten Browser -- gleiches Vorbild wie schema-validate.test.js.
 *
 * Zwei Dinge werden hier geprueft, die die 17 Tests in schema-validate.test.js
 * NICHT abdecken (die pruefen nur den Validator selbst, nicht die Anbindung
 * ans Formular):
 *
 *  1. groupMessagesByField/validateManifestByField: wenn man die zwoelf
 *     kaputten Beispielmanifeste direkt (ohne Formular-Rekonstruktion) durch
 *     die Anbindung schickt, landet die Meldung unter demselben Feldnamen,
 *     den auch ManifestParserTest.java fuer diese Datei prueft -- das ist
 *     die Zusicherung aus D2s Fertig-Kriterium ("dasselbe Feld").
 *  2. buildManifestInstance: ein paar realistische Formular-Eingaben (rohe
 *     Strings, wie sie <input>/<textarea> liefern wuerden) erzeugen ein
 *     Manifest-Objekt, das beim Validieren dieselbe Fehlerart ausloest wie
 *     der Java-Parser fuer das entsprechende Beispielmanifest.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const validator = require("../schema-validate.js");
const form = require("../manifest-form.js");

const repoRoot = path.join(__dirname, "..", "..");
const schemaPath = path.join(repoRoot, "registry", "schema", "vitrine.schema.json");
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));

const examplesDir = path.join(repoRoot, "registry", "schema", "examples");

function readExample(sub, name) {
  return JSON.parse(fs.readFileSync(path.join(examplesDir, sub, name), "utf8"));
}

// --- 1. Anbindung: Beispiel-Manifest direkt -> Meldung landet am richtigen Feld ---

test("01-missing-required-field: Meldung landet unter Feld 'author' (wie Java missingRequiredFieldIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "01-missing-required-field.json"), schema, validator);
  assert.ok(byField.author, JSON.stringify(byField));
  assert.ok(byField.author.some((m) => m.includes("fehlt")));
});

test("02-wrong-type-schemaversion: Meldung landet unter Feld 'schemaVersion' (wie Java wrongTypeIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "02-wrong-type-schemaversion.json"), schema, validator);
  assert.ok(byField.schemaVersion.some((m) => m.includes("Typ")));
});

test("03-unknown-additional-field: Meldung landet unter Feld 'preis' (wie Java unknownAdditionalFieldIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "03-unknown-additional-field.json"), schema, validator);
  assert.ok(byField.preis.some((m) => m.includes("Unbekannt")));
});

test("04-invalid-category-enum: Meldung landet unter Feld 'category' (wie Java invalidCategoryEnumIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "04-invalid-category-enum.json"), schema, validator);
  assert.ok(byField.category.some((m) => m.includes("textures")));
});

test("05-invalid-semver: Meldung landet unter Feld 'version' (wie Java invalidSemverIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "05-invalid-semver.json"), schema, validator);
  assert.ok(byField.version.some((m) => m.includes("Muster")));
});

test("06-invalid-id-pattern: Meldung landet unter Feld 'id' (wie Java invalidIdPatternIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "06-invalid-id-pattern.json"), schema, validator);
  assert.ok(byField.id.some((m) => m.includes("Muster")));
});

test("07-empty-name: Meldung landet unter Feld 'name' (wie Java emptyNameIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "07-empty-name.json"), schema, validator);
  assert.ok(byField.name.some((m) => m.includes("leer")));
});

test("08-capabilities-wrong-type: Meldung landet unter Feld 'capabilities' (wie Java capabilitiesWrongTypeIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "08-capabilities-wrong-type.json"), schema, validator);
  assert.ok(byField.capabilities.some((m) => m.includes("Typ")));
});

test("09-capabilities-item-invalid-pattern: Meldung landet unter Feld 'capabilities' (Index zusammengefasst, Enum-Verstoss, wie Java capabilitiesItemNotWhitelistedIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "09-capabilities-item-invalid-pattern.json"), schema, validator);
  assert.ok(byField.capabilities, JSON.stringify(byField));
  assert.ok(byField.capabilities.some((m) => m.includes("erlaubt sind")));
});

test("13-capabilities-dotted-namespace: 'hud.read' landet als Enum-Verstoss unter Feld 'capabilities' (G2)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "13-capabilities-dotted-namespace.json"), schema, validator);
  assert.ok(byField.capabilities, JSON.stringify(byField));
  assert.ok(byField.capabilities.some((m) => m.includes("hud.read") && m.includes("erlaubt sind")));
});

test("10-schemaversion-wrong-value: Meldung landet unter Feld 'schemaVersion' (wie Java schemaVersionWrongValueIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "10-schemaversion-wrong-value.json"), schema, validator);
  assert.ok(byField.schemaVersion.some((m) => m.includes("2")));
});

test("11-icon-path-traversal: Meldung landet unter Feld 'icon' (wie Java iconPathTraversalIsNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "11-icon-path-traversal.json"), schema, validator);
  assert.ok(byField.icon.some((m) => m.includes("..")));
});

test("12-multiple-missing-fields: je ein eigener Eintrag unter 'id', 'name', 'version' (wie Java multipleMissingFieldsAreAllNamedExplicitly)", () => {
  const byField = form.validateManifestByField(readExample("invalid", "12-multiple-missing-fields.json"), schema, validator);
  assert.ok(byField.id.some((m) => m.includes("fehlt")));
  assert.ok(byField.name.some((m) => m.includes("fehlt")));
  assert.ok(byField.version.some((m) => m.includes("fehlt")));
});

test("gueltige Beispiele liefern keine Feld-Meldungen", () => {
  assert.deepEqual(form.validateManifestByField(readExample("valid", "minimal.json"), schema, validator), {});
  assert.deepEqual(form.validateManifestByField(readExample("valid", "full.json"), schema, validator), {});
});

// --- 2. buildManifestInstance: rohe Formular-Strings -> dieselbe Fehlerart ---

test("buildManifestInstance: leeres Pflichtfeld 'author' im Formular -> 'Pflichtfeld fehlt' (wie 01)", () => {
  const formValues = {
    schemaVersion: "1", id: "beispiel.ohneautor", name: "Paket ohne Autor",
    version: "1.0.0", category: "texturen", author: ""
  };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.ok(!Object.prototype.hasOwnProperty.call(instance, "author"));
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.author.some((m) => m.includes("fehlt")));
});

test("buildManifestInstance: falsch getippte category 'textures' -> ungueltiger Enum-Wert (wie 04)", () => {
  const formValues = {
    schemaVersion: "1", id: "beispiel.kategorie", name: "Kategorie-Test",
    version: "1.0.0", category: "textures", author: "Testfixture"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.category.some((m) => m.includes("textures")));
});

test("buildManifestInstance: falsche SemVer-Eingabe -> Musterfehler an 'version' (wie 05)", () => {
  const formValues = {
    schemaVersion: "1", id: "beispiel.semver", name: "SemVer-Test",
    version: "v1.0", category: "texturen", author: "Testfixture"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.version.some((m) => m.includes("Muster")));
});

test("buildManifestInstance: schemaVersion als Text 'eins' statt Zahl -> Typ-Fehler (wie 02)", () => {
  const formValues = {
    schemaVersion: "eins", id: "beispiel.falschertyp", name: "Falscher Typ",
    version: "1.0.0", category: "texturen", author: "Testfixture"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.equal(typeof instance.schemaVersion, "string");
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.schemaVersion.some((m) => m.includes("Typ")));
});

test("buildManifestInstance: schemaVersion '2' -> ungueltiger Wert, echte Ganzzahl (wie 10)", () => {
  const formValues = {
    schemaVersion: "2", id: "beispiel.falscheschemaversion", name: "Falsche Schema-Version",
    version: "1.0.0", category: "texturen", author: "Testfixture"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.equal(instance.schemaVersion, 2);
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.schemaVersion.some((m) => m.includes("2")));
});

test("buildManifestInstance: capabilities als mehrzeiliger Text -> Array, nicht gelisteter Eintrag -> Enum-Verstoss (wie 09/13)", () => {
  const formValues = {
    schemaVersion: "1", id: "beispiel.caps", name: "Capabilities-Test",
    version: "1.0.0", category: "hud", author: "Testfixture",
    capabilities: "hud.read\nUngueltig!!"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.deepEqual(instance.capabilities, ["hud.read", "Ungueltig!!"]);
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.capabilities.some((m) => m.includes("erlaubt sind")));
});

test("buildManifestInstance: capabilities 'hud' (Whitelist-Wert) -> keine Feld-Meldung", () => {
  const formValues = {
    schemaVersion: "1", id: "beispiel.capsok", name: "Capabilities-OK",
    version: "1.0.0", category: "hud", author: "Testfixture",
    capabilities: "hud"
  };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.deepEqual(instance.capabilities, ["hud"]);
  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(!byField.capabilities, JSON.stringify(byField));
});

test("buildManifestInstance: alle Felder leer -> gar keine Schluessel im Instanzobjekt", () => {
  const formValues = { schemaVersion: "", id: "", name: "", version: "", category: "", author: "", capabilities: "   \n  " };
  const instance = form.buildManifestInstance(formValues, schema);
  assert.deepEqual(instance, {});
});

test("buildManifestInstance + formValuesFromInstance: Rundreise fuer ein gueltiges volles Manifest ist verlustfrei", () => {
  const full = readExample("valid", "full.json");
  const values = form.formValuesFromInstance(full, schema);
  const rebuilt = form.buildManifestInstance(values, schema);
  assert.deepEqual(rebuilt, full);
  assert.deepEqual(form.validateManifestByField(rebuilt, schema, validator), {});
});

test("formValuesFromInstance: fehlende optionale Felder werden zu leeren Strings (fuer die Anzeige im leeren Feld)", () => {
  const minimal = readExample("valid", "minimal.json");
  const values = form.formValuesFromInstance(minimal, schema);
  // license ist seit GATE23 Pflichtfeld -- minimal.json enthaelt daher einen
  // Wert, kein Leerstring mehr. homepage bleibt echt optional.
  assert.equal(values.license, "CC0-1.0");
  assert.equal(values.homepage, "");
  assert.equal(values.schemaVersion, "1");
});

/* ------------------------------------------------------------------ *
 * GATE6 -- verschachtelte Felder (javaMod) und das erste Boolean-Feld
 * (hotApply). Das Formular kennt die Feldnamen nicht selbst, es leitet sie
 * aus dem Schema ab -- deshalb wird hier zuerst genau das geprueft.
 * ------------------------------------------------------------------ */

test("GATE6: manifestFieldPaths klappt verschachtelte Objekte auf, Arrays bleiben ein Feld", () => {
  const pfade = form.manifestFieldPaths(schema);

  assert.ok(pfade.includes("id"));
  assert.ok(pfade.includes("contentClass"));
  assert.ok(pfade.includes("hotApply"));
  assert.ok(pfade.includes("javaMod.loader"));
  assert.ok(pfade.includes("javaMod.jar.sha256"));
  assert.ok(pfade.includes("javaMod.minecraftVersions"), "Arrays bleiben ein einzelnes Feld");
  assert.ok(!pfade.includes("javaMod"), "das Objekt selbst ist kein Formularfeld");
  assert.ok(!pfade.includes("javaMod.jar"), "auch das verschachtelte Objekt selbst nicht");
});

test("GATE6: buildManifestInstance baut aus Formularstrings ein gueltiges Klasse-B-Manifest", () => {
  const instance = form.buildManifestInstance({
    schemaVersion: "1",
    id: "beispiel.klasse-b",
    name: "Klasse B",
    version: "1.0.0",
    category: "hud",
    contentClass: "javamod",
    hotApply: "false",
    "javaMod.loader": "fabric",
    "javaMod.minecraftVersions": "1.21.1\n1.21.4",
    "javaMod.modId": "klasseb",
    "javaMod.dependencies": "fabric-api",
    "javaMod.jar.fileName": "klasse-b-1.0.0.jar",
    "javaMod.jar.sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "javaMod.jar.size": "20480",
    author: "A",
    license: "MIT"
  }, schema);

  assert.equal(instance.hotApply, false, "'false' muss ein echtes Boolean werden, kein String");
  assert.deepEqual(instance.javaMod.minecraftVersions, ["1.21.1", "1.21.4"]);
  assert.equal(instance.javaMod.jar.size, 20480);
  assert.equal(instance.javaMod.jar.sha256.length, 64);
  assert.deepEqual(validator.validate(schema, instance), []);
});

test("GATE6: ein leer gelassener Klasse-B-Abschnitt legt gar kein javaMod-Objekt an", () => {
  const instance = form.buildManifestInstance({
    schemaVersion: "1",
    id: "beispiel.klasse-a",
    name: "Klasse A",
    version: "1.0.0",
    category: "texturen",
    contentClass: "",
    hotApply: "",
    "javaMod.loader": "",
    "javaMod.minecraftVersions": "",
    "javaMod.jar.fileName": "",
    "javaMod.jar.sha256": "",
    "javaMod.jar.size": "",
    author: "A",
    license: "CC0-1.0"
  }, schema);

  assert.ok(!("javaMod" in instance), "ein leeres javaMod-Objekt waere nach dem Schema sofort ein Verstoss");
  assert.ok(!("hotApply" in instance));
  assert.ok(!("contentClass" in instance));
  assert.deepEqual(validator.validate(schema, instance), []);
});

test("GATE6: formValuesFromInstance fuellt verschachtelte Felder und Booleans wieder zurueck", () => {
  const instance = {
    schemaVersion: 1,
    id: "beispiel.klasse-b",
    name: "Klasse B",
    version: "1.0.0",
    category: "hud",
    contentClass: "javamod",
    hotApply: false,
    javaMod: {
      loader: "fabric",
      minecraftVersions: ["1.21.1", "1.21.4"],
      jar: { fileName: "klasse-b-1.0.0.jar", sha256: "a".repeat(64), size: 20480 }
    },
    author: "A",
    license: "MIT"
  };

  const values = form.formValuesFromInstance(instance, schema);
  assert.equal(values.hotApply, "false");
  assert.equal(values["javaMod.loader"], "fabric");
  assert.equal(values["javaMod.minecraftVersions"], "1.21.1\n1.21.4");
  assert.equal(values["javaMod.jar.size"], "20480");
  assert.equal(values["javaMod.modId"], "", "nicht gesetzte optionale Felder kommen als Leerstring zurueck");

  // Rundreise: zurueck ins Formular und wieder heraus ergibt dasselbe Manifest.
  assert.deepEqual(form.buildManifestInstance(values, schema), instance);
});

test("GATE6: eine getarnte .jar in einem Klasse-A-Formular landet am Feld javaMod", () => {
  const instance = form.buildManifestInstance({
    schemaVersion: "1",
    id: "beispiel.getarnt",
    name: "Getarnt",
    version: "1.0.0",
    category: "texturen",
    contentClass: "resource",
    "javaMod.loader": "fabric",
    "javaMod.minecraftVersions": "1.21.1",
    "javaMod.jar.fileName": "getarnt.jar",
    "javaMod.jar.sha256": "b".repeat(64),
    "javaMod.jar.size": "20480",
    author: "A",
    license: "CC0-1.0"
  }, schema);

  const byField = form.validateManifestByField(instance, schema, validator);
  assert.ok(byField.javaMod && byField.javaMod.length > 0, JSON.stringify(byField));
});

test("GATE6: zu jedem Schema-Feld gibt es ein Formularfeld in index.html (keine stille Luecke)", () => {
  // Kein DOM noetig -- index.html wird als Text geprueft. Das faengt den Fall
  // ab, dass ein neues Feld im Schema landet, aber niemand es ins Formular
  // haengt: app.js wuerde es dann stillschweigend ueberspringen.
  const html = fs.readFileSync(path.join(repoRoot, "designer", "index.html"), "utf8");
  const fehlend = [];
  for (const pfad of form.manifestFieldPaths(schema)) {
    for (const art of ["field", "error", "label"]) {
      if (!html.includes('id="manifest-' + art + "-" + pfad + '"')) {
        fehlend.push("manifest-" + art + "-" + pfad);
      }
    }
    if (!html.includes('data-field="' + pfad + '"')) {
      fehlend.push('data-field="' + pfad + '"');
    }
  }
  assert.deepEqual(fehlend, []);
});
