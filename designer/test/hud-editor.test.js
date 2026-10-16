"use strict";

/*
 * Node-Test fuer den HUD-Editor (designer/hud-editor.js + designer/
 * schema-validate.js), ohne echten Browser -- gleiches Vorbild wie
 * manifest-form.test.js / schema-validate.test.js (die dortige Umgebung hat
 * keinen Browser, stattdessen node --check + node --test).
 *
 * Prueft D5s Fertig-Kriterium "Im Designer gebautes HUD laedt der Mod
 * unveraendert" ueber die Schema-Konformitaet (die Mod-Laufzeit fuer HUD ist
 * noch nicht gebaut, siehe hud.schema.json-Kopfkommentar): der Editor-Export
 * validiert gegen registry/schema/hud.schema.json -- dieselbe Datei und
 * derselbe Auswerter, den auch Manifest-Formular (D2) und CLI (D7) nutzen.
 *
 * Vier Bloecke:
 *  1. Ein im Editor-Datenmodell zusammengebautes HUD -> Export -> validiert
 *     ohne Meldung gegen das Quell-Schema.
 *  2. Bewusst kaputte Widget-Entwuerfe (unbekannte Datenquelle, unbekannter
 *     Anker, Formel als "source") werden abgelehnt, und die Meldung haengt am
 *     selben Feldpfad, den auch die invalid/-Beispiele in
 *     registry/schema/examples/hud/ treffen.
 *  3. Rundreise: examples/hud/valid/*.json importieren -> Editor-Modell ->
 *     exportieren -> deckungsgleich (fps-and-coordinates.json byte-/
 *     deep-identisch; all-widget-types.json semantisch identisch, d. h. bis
 *     auf explizit ausgeschriebenes visibility:{type:"always"}).
 *  4. Die eingebettete Schema-Kopie designer/hud-schema-data.js ist inhaltlich
 *     deckungsgleich zur Quelle registry/schema/hud.schema.json (Charter-
 *     Regel 8, "ein Schema, zwei Seiten"; die Byte-Identitaet stellt der
 *     Generator generate-designer-schema.mjs sicher).
 *
 * Aufruf: node --test designer/test/hud-editor.test.js
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const validator = require("../schema-validate.js");
const editor = require("../hud-editor.js");
const embeddedSchema = require("../hud-schema-data.js");

const repoRoot = path.join(__dirname, "..", "..");
const schemaPath = path.join(repoRoot, "registry", "schema", "hud.schema.json");
// Bewusst die QUELL-Datei laden, nicht die eingebettete Kopie -- der Test
// beweist damit die Konformitaet gegen genau die Datei, die der Mod liest.
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));

const hudExamplesDir = path.join(repoRoot, "registry", "schema", "examples", "hud");

function readExample(sub, name) {
  return JSON.parse(fs.readFileSync(path.join(hudExamplesDir, sub, name), "utf8"));
}

/** Ein rohes HUD-Dokument (aus einer Datei) einmal komplett durchs Editor-Datenmodell schicken. */
function roundTripThroughEditor(rawHud) {
  let hud = editor.ensureHud({ widgets: [] });
  for (const widget of rawHud.widgets) {
    const formValues = editor.widgetToFormValues(widget);
    hud = editor.addWidget(hud, editor.buildWidgetInstance(formValues));
  }
  return hud;
}

// --- 1. Im Editor gebautes HUD -> Export -> validiert gruen ---

test("im Editor zusammengebautes HUD (alle vier Widget-Typen) exportiert und validiert ohne Meldung gegen die Quell-Schemadatei", () => {
  let hud = editor.ensureHud(undefined);

  // text (dynamisch, mit Label + Nachkommastellen)
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "fps", anchor: "top-left", offsetX: "4", offsetY: "4", scale: "1",
      visibilityType: "always", contentKind: "dynamic", contentSource: "fps",
      contentLabel: "FPS: ", contentDecimals: "0" }
  )));

  // icon
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("icon"),
    { id: "marker", anchor: "top-center", offsetX: "0", offsetY: "4",
      visibilityType: "debugScreen", visibilityDebugScreen: "off",
      texture: "textures/gui/marker.png", width: "16", height: "16" }
  )));

  // bar
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("bar"),
    { id: "load-bar", anchor: "bottom-center", offsetX: "0", offsetY: "-20",
      visibilityType: "dimension", visibilityDimension: "minecraft:overworld",
      source: "fps", min: "0", max: "144", width: "120", height: "6", color: "#3a6ff0" }
  )));

  // list (zwei Eintraege: statisch + dynamisch)
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("list"),
    { id: "coords", anchor: "bottom-left", visibilityType: "outsideMenu",
      entries: [
        { kind: "static", value: "Koordinaten" },
        { kind: "dynamic", source: "coordinates.x", label: "X: ", decimals: "1" }
      ] }
  )));

  const exported = JSON.parse(editor.toJsonText(hud));
  assert.deepEqual(validator.validate(schema, exported), []);
  assert.deepEqual(editor.validateHudDocument(exported, schema, validator), []);
  assert.equal(exported.schemaVersion, 1);
  assert.equal(exported.widgets.length, 4);
});

// --- 2. Kaputte Widget-Entwuerfe -> Ablehnung am erwarteten Feld ---

test("unbekannte Datenquelle: Meldung haengt an 'content.source' (wie invalid/03-unknown-data-source.json)", () => {
  const draft = editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "health-text", anchor: "top-left", contentKind: "dynamic", contentSource: "player.health" }
  ));
  const byField = editor.validateDraftWidgetByField(draft, [], -1, schema, validator);
  assert.ok(byField["content.source"], JSON.stringify(byField));
  assert.ok(byField["content.source"].some((m) => m.includes("player.health")));
  assert.ok(byField["content.source"].some((m) => m.includes("fps")));
});

test("unbekannter Anker: Meldung haengt an 'anchor' (wie invalid/04-unknown-anchor.json)", () => {
  const draft = editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "fps-counter", anchor: "middle-left", contentKind: "dynamic", contentSource: "fps" }
  ));
  const byField = editor.validateDraftWidgetByField(draft, [], -1, schema, validator);
  assert.ok(byField.anchor, JSON.stringify(byField));
  assert.ok(byField.anchor.some((m) => m.includes("middle-left")));
});

test("Formel als 'source': Meldung haengt an 'content.source' (wie invalid/06-free-formula-as-source.json)", () => {
  const draft = editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "hacked-widget", anchor: "top-left", contentKind: "dynamic", contentSource: "player.x * 2 + 1" }
  ));
  const byField = editor.validateDraftWidgetByField(draft, [], -1, schema, validator);
  assert.ok(byField["content.source"], JSON.stringify(byField));
  assert.ok(byField["content.source"].some((m) => m.includes("player.x * 2 + 1")));
});

test("unbekannter Widget-Typ: Meldung haengt an 'type' (wie invalid/02-unknown-widget-type.json)", () => {
  const draft = { id: "player-video", type: "video", anchor: "top-left" };
  const byField = editor.validateDraftWidgetByField(draft, [], -1, schema, validator);
  assert.ok(byField.type, JSON.stringify(byField));
  assert.ok(byField.type.some((m) => m.includes("video")));
});

test("unbekannte Sichtbarkeitsbedingung: Meldung haengt an 'visibility.type' (wie invalid/05-unknown-visibility-condition.json)", () => {
  const draft = {
    id: "fps-counter", type: "text", anchor: "top-left",
    visibility: { type: "always_true" },
    content: { kind: "dynamic", source: "fps" }
  };
  const byField = editor.validateDraftWidgetByField(draft, [], -1, schema, validator);
  assert.ok(byField["visibility.type"], JSON.stringify(byField));
  assert.ok(byField["visibility.type"].some((m) => m.includes("always_true")));
});

test("doppelte Widget-Id: Meldung haengt an 'id' (reine Editor-/Mod-Laufzeitpruefung, JSON Schema kann das nicht)", () => {
  const existing = [{ id: "fps", type: "text", anchor: "top-left", content: { kind: "dynamic", source: "fps" } }];
  const draft = editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "fps", anchor: "top-right", contentKind: "dynamic", contentSource: "time" }
  ));
  const byField = editor.validateDraftWidgetByField(draft, existing, -1, schema, validator);
  assert.ok(byField.id, JSON.stringify(byField));
  assert.ok(byField.id.some((m) => m.toLowerCase().includes("eindeutig")));
});

test("jedes invalid/-Beispiel wird vom Editor-Dokumentvalidator abgelehnt", () => {
  const dir = path.join(hudExamplesDir, "invalid");
  const files = fs.readdirSync(dir).filter((f) => f.endsWith(".json"));
  assert.ok(files.length >= 3);
  for (const name of files) {
    const doc = readExample("invalid", name);
    const messages = editor.validateHudDocument(doc, schema, validator);
    assert.ok(messages.length > 0, `${name} sollte abgelehnt werden`);
  }
});

// --- 3. Rundreise: importieren -> Editor-Modell -> exportieren ---

test("Rundreise fps-and-coordinates.json: importieren -> Editor-Modell -> exportieren ist byte-/deep-identisch", () => {
  const original = readExample("valid", "fps-and-coordinates.json");
  const exported = JSON.parse(editor.toJsonText(roundTripThroughEditor(original)));
  assert.deepEqual(exported, original);
  assert.equal(JSON.stringify(exported), JSON.stringify(original));
  assert.deepEqual(validator.validate(schema, exported), []);
});

test("Rundreise all-widget-types.json: importieren -> Editor-Modell -> exportieren ist semantisch identisch und weiter gueltig", () => {
  const original = readExample("valid", "all-widget-types.json");
  const exported = JSON.parse(editor.toJsonText(roundTripThroughEditor(original)));

  assert.deepEqual(validator.validate(schema, exported), []);

  // Semantische Gleichheit: fehlendes visibility == { type: "always" }
  // (hud.schema.json: "Fehlt dieses Feld an einem Widget, gilt 'always'").
  const normalise = (doc) => ({
    schemaVersion: doc.schemaVersion,
    widgets: doc.widgets.map((w) => Object.assign({}, w, {
      visibility: w.visibility || { type: "always" }
    }))
  });
  assert.deepEqual(normalise(exported), normalise(original));
});

test("im Editor gebautes HUD ist BYTE-fuer-BYTE ein committetes valid/-Beispiel (staerkste tooling-seitige Zusicherung fuer 'laedt der Mod unveraendert')", () => {
  // Vier Widgets (text/icon/bar/list) exakt so im Editor-Datenmodell
  // zusammengesetzt, wie es der Designer aus dem Formular tut.
  let hud = editor.ensureHud(undefined);
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("text"),
    { id: "fps-counter", anchor: "top-left", offsetX: "4", offsetY: "4", scale: "1",
      visibilityType: "always", contentKind: "dynamic", contentSource: "fps",
      contentLabel: "FPS: ", contentDecimals: "0" }
  )));
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("icon"),
    { id: "biome-marker", anchor: "top-center", offsetX: "0", offsetY: "4", scale: "1.5",
      visibilityType: "debugScreen", visibilityDebugScreen: "off",
      texture: "textures/gui/biome-marker.png", width: "16", height: "16" }
  )));
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("bar"),
    { id: "speed-bar", anchor: "bottom-center", offsetX: "0", offsetY: "-20",
      visibilityType: "dimension", visibilityDimension: "minecraft:overworld",
      source: "fps", min: "0", max: "144", width: "120", height: "6", color: "#3a6ff0" }
  )));
  hud = editor.addWidget(hud, editor.buildWidgetInstance(Object.assign(
    editor.defaultWidgetFormValues("list"),
    { id: "coordinates-list", anchor: "bottom-left", offsetX: "4", offsetY: "-4",
      visibilityType: "outsideMenu", entries: [
        { kind: "static", value: "Koordinaten" },
        { kind: "dynamic", source: "coordinates.x", label: "X: ", decimals: "1" },
        { kind: "dynamic", source: "coordinates.y", label: "Y: ", decimals: "1" },
        { kind: "dynamic", source: "coordinates.z", label: "Z: ", decimals: "1" }
      ] }
  )));

  const exportText = editor.toJsonText(hud);
  const fileText = fs.readFileSync(
    path.join(hudExamplesDir, "valid", "designer-canonical-export.json"), "utf8"
  );

  // (c) BYTE-fuer-BYTE identisch zu der Datei, die auch registry/test/
  //     hud-schema.test.mjs mit dem Referenz-Validator (@cfworker/json-schema)
  //     als gueltig prueft -- der Designer-Export IST diese Datei.
  assert.equal(exportText, fileText);
  // (a) validiert ohne Meldung gegen die Quell-Schemadatei.
  assert.deepEqual(validator.validate(schema, JSON.parse(exportText)), []);
  // (b) verlustfreie Rundreise: die Datei importieren -> Editor-Modell ->
  //     exportieren ergibt exakt dieselben Bytes.
  const reexported = editor.toJsonText(roundTripThroughEditor(JSON.parse(fileText)));
  assert.equal(reexported, fileText);
});

test("editor.importHud liefert dasselbe wie der manuelle Rundreise-Aufbau und ist idempotent", () => {
  const original = readExample("valid", "all-widget-types.json");
  const once = editor.importHud(original);
  const twice = editor.importHud(once);
  assert.deepEqual(validator.validate(schema, once), []);
  assert.deepEqual(twice, once);
  assert.deepEqual(once, roundTripThroughEditor(original));
});

// --- 4. Ein Schema, zwei Seiten ---

test("die eingebettete Schema-Kopie (hud-schema-data.js) ist deckungsgleich zur Quelldatei", () => {
  assert.deepEqual(embeddedSchema, schema);
  // Byte-identisch als JSON (gleiche Schluesselreihenfolge) -- genau die Form,
  // die generate-designer-schema.mjs schreibt. Schlaegt fehl, wenn jemand
  // hud.schema.json aendert, ohne den Generator erneut laufen zu lassen.
  assert.equal(
    JSON.stringify(embeddedSchema, null, 2),
    JSON.stringify(schema, null, 2),
    "hud-schema-data.js neu erzeugen: node registry/schema/generate-designer-schema.mjs"
  );
});

test("Enum-Helfer lesen ihre Werte aus dem Schema (keine Dublette im Editor-Code)", () => {
  assert.deepEqual(editor.anchors(schema), schema.$defs.anchor.enum);
  assert.deepEqual(editor.dataSources(schema), schema.$defs.dataSource.enum);
  assert.deepEqual(editor.widgetTypes(schema), ["text", "icon", "bar", "list"]);
  assert.deepEqual(editor.visibilityTypes(schema), ["always", "dimension", "debugScreen", "outsideMenu"]);
  // Auch die Unter-Enums (debugScreen-Zustand, textContent-Art) kommen aus dem
  // Schema, nicht aus einer Editor-Dublette -- app.js#hudOptionValues liest sie
  // ueber diese Helfer statt ["on","off"]/["static","dynamic"] hart zu setzen.
  const dbgBranch = schema.$defs.visibility.oneOf.find((b) => b.properties.debugScreen);
  assert.deepEqual(editor.debugScreenStates(schema), dbgBranch.properties.debugScreen.enum);
  assert.deepEqual(
    editor.textContentKinds(schema),
    schema.$defs.textContent.oneOf.map((b) => b.properties.kind.const)
  );
});
