"use strict";

/*
 * Node-Test fuer designer/theme-tokens.js + designer/theme-editor.js (D3),
 * ohne echten Browser -- gleiches Vorbild wie schema-validate.test.js und
 * manifest-form.test.js.
 *
 * Deckt ab:
 *  1. Kontrastberechnung selbst (bekannte Werte: schwarz/weiss = 21:1,
 *     gleiche Farbe = 1:1).
 *  2. computeContrastWarnings gegen das Standard-Token-Set (DEFAULT_THEME,
 *     seit GATE22 identisch zu registry/schema/examples/theme/valid/klarglas.json)
 *     -- inklusive eines echten, erklaerbaren Befunds (dunkel: Fehlertext auf
 *     Flaeche liegt bei 3.05:1, besteht damit registry/test/theme-contrast.test.mjs'
 *     3:1-Grafikschwelle, unterschreitet aber die pauschale 4.5:1-Editor-Schwelle
 *     knapp) sowie zweier bewusst verschlechterter Paare, die eine Verletzung
 *     provozieren, um die Erkennung selbst zu beweisen.
 *  3. Formular <-> Variant-Instanz Rundreise (buildVariantInstance /
 *     variantToFormValues), inklusive des color-alpha-Sonderfalls.
 *  4. fillVariantDefaults: fehlende/kaputte Werte werden durch den Default
 *     ersetzt, gueltige Werte bleiben erhalten.
 *  5. cssVarsFromVariant: liefert fuer jedes Token einen CSS-Custom-Property-
 *     Eintrag mit der richtigen Einheit/Umrechnung (px, Hex, rgba()).
 *  6. validateVariantByField: Bereichs-/Formatpruefung je Feld.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const Tokens = require("../theme-tokens.js");
const Editor = require("../theme-editor.js");

// --- 1. Kontrastberechnung ---

test("contrastRatio: schwarz auf weiss ist 21:1", () => {
  const ratio = Editor.contrastRatio("#000000", "#ffffff");
  assert.ok(Math.abs(ratio - 21) < 0.01, ratio);
});

test("contrastRatio: gleiche Farbe ist 1:1", () => {
  assert.equal(Editor.contrastRatio("#3a6ff0", "#3a6ff0"), 1);
});

test("contrastRatio: ungueltige Hex-Werte liefern null", () => {
  assert.equal(Editor.contrastRatio("nicht-hex", "#ffffff"), null);
  assert.equal(Editor.contrastRatio("#fff", "#ffffff"), null); // nur #rrggbb wird unterstuetzt
});

// --- 2. computeContrastWarnings gegen das Standard-Token-Set ---

test("computeContrastWarnings: Standard-Set hell -- Text auf Hintergrund besteht AA deutlich", () => {
  const warnings = Editor.computeContrastWarnings(Tokens.DEFAULT_THEME.light);
  const entry = warnings.find((w) => w.id === "text-on-bg");
  assert.ok(entry.passes);
  assert.ok(entry.ratio > 4.5);
});

// GATE22: DEFAULT_THEME kommt jetzt 1:1 aus registry/schema/examples/theme/valid/klarglas.json
// (dem bereits gegen registry/test/theme-contrast.test.mjs geprueften Standard-Theme). Jener
// Test benutzt aber je Paar eine ANDERE Schwelle (4.5:1 fuer Text, 3.0:1 fuer
// Bedienelement-/Grafikpaare wie danger/disabledText/focus.ring.color) -- der Editor hier
// prueft (bewusst konservativer, siehe AA_THRESHOLD) alle Paare pauschal gegen 4.5:1. Daraus
// folgt ein echter, erklaerbarer Befund: dunkel "danger-on-surface" liegt bei 3.05:1 --
// besteht die 3:1-Schwelle fuer Grafik/Bedienelemente (registry/test/theme-contrast.test.mjs),
// unterschreitet aber die vom Editor pauschal angelegte 4.5:1-Textschwelle knapp.

test("computeContrastWarnings: Standard-Set (Klarglas) hell -- alle Paare bestehen die Editor-Schwelle (4.5:1)", () => {
  const warnings = Editor.computeContrastWarnings(Tokens.DEFAULT_THEME.light);
  warnings.forEach((w) => {
    assert.equal(w.passes, true, `${w.id}: ${w.ratio}`);
  });
});

test("computeContrastWarnings: Standard-Set (Klarglas) dunkel -- Fehlertext auf Flaeche unterschreitet die pauschale 4.5:1-Editor-Schwelle knapp (echter, erklaerbarer Befund)", () => {
  const warnings = Editor.computeContrastWarnings(Tokens.DEFAULT_THEME.dark);
  const entry = warnings.find((w) => w.id === "danger-on-surface");
  assert.equal(entry.passes, false);
  assert.ok(entry.ratio >= 3.0 && entry.ratio < 4.5, entry.ratio);
  warnings.filter((w) => w.id !== "danger-on-surface").forEach((w) => {
    assert.equal(w.passes, true, `${w.id}: ${w.ratio}`);
  });
});

test("computeContrastWarnings: absichtlich verschlechtertes Paar (Knopftext auf Akzentflaeche) wird als Verstoss erkannt", () => {
  const variant = Object.assign({}, Tokens.DEFAULT_THEME.light, {
    "color.accent": "#c9d6ff",
    "color.accentContrast": "#ffffff"
  });
  const warnings = Editor.computeContrastWarnings(variant);
  const entry = warnings.find((w) => w.id === "accentcontrast-on-accent");
  assert.equal(entry.passes, false);
  assert.ok(entry.message.includes("UNTER WCAG AA"));
});

test("computeContrastWarnings: absichtlich verschlechtertes Paar (Fehlertext auf Flaeche) wird als Verstoss erkannt", () => {
  const variant = Object.assign({}, Tokens.DEFAULT_THEME.dark, {
    "color.danger": "#3a3a3a",
    "color.surfaceSolid": "#232328"
  });
  const warnings = Editor.computeContrastWarnings(variant);
  const entry = warnings.find((w) => w.id === "danger-on-surface");
  assert.equal(entry.passes, false);
  assert.ok(entry.ratio < 3, entry.ratio);
});

test("computeContrastWarnings: jedes konfigurierte Paar liefert genau einen Eintrag, auch bestandene", () => {
  const warnings = Editor.computeContrastWarnings(Tokens.DEFAULT_THEME.light);
  assert.equal(warnings.length, Tokens.CONTRAST_PAIRS.length);
  warnings.forEach((w) => {
    assert.equal(typeof w.passes, "boolean");
    assert.equal(typeof w.message, "string");
  });
});

test("computeContrastWarnings: absichtlich schlechtes Paar (hellgrau auf weiss) wird erkannt", () => {
  const variant = Object.assign({}, Tokens.DEFAULT_THEME.light, {
    "color.text": "#cccccc",
    "color.bg": "#ffffff"
  });
  const warnings = Editor.computeContrastWarnings(variant);
  const entry = warnings.find((w) => w.id === "text-on-bg");
  assert.equal(entry.passes, false);
  assert.ok(entry.ratio < 4.5);
});

// --- 3. Formular <-> Variant-Instanz ---

test("buildVariantInstance + variantToFormValues: Rundreise fuer das Standard-Set ist verlustfrei", () => {
  const values = Editor.variantToFormValues(Tokens.DEFAULT_THEME.light);
  const rebuilt = Editor.buildVariantInstance(values);
  assert.deepEqual(rebuilt, Tokens.DEFAULT_THEME.light);
});

test("buildVariantInstance: Zahl aus Formular-String wird zu echter Zahl", () => {
  const instance = Editor.buildVariantInstance({ "space.unit": "6" });
  assert.equal(instance["space.unit"], 6);
  assert.equal(typeof instance["space.unit"], "number");
});

test("buildVariantInstance: leere/fehlende Felder erscheinen nicht in der Instanz", () => {
  const instance = Editor.buildVariantInstance({ "space.unit": "", "radius.card": undefined });
  assert.ok(!Object.prototype.hasOwnProperty.call(instance, "space.unit"));
  assert.ok(!Object.prototype.hasOwnProperty.call(instance, "radius.card"));
});

test("buildVariantInstance: color-alpha aus getrennten hex/alpha-Feldern zusammengesetzt", () => {
  const names = Editor.alphaFieldNames("color.surfaceTranslucent");
  const instance = Editor.buildVariantInstance({
    [names.hex]: "#112233",
    [names.alpha]: "0.5"
  });
  assert.deepEqual(instance["color.surfaceTranslucent"], { hex: "#112233", alpha: 0.5 });
});

test("buildVariantInstance: Alpha ausserhalb 0..1 wird begrenzt", () => {
  const names = Editor.alphaFieldNames("color.borderTranslucent");
  const instance = Editor.buildVariantInstance({ [names.hex]: "#000000", [names.alpha]: "5" });
  assert.equal(instance["color.borderTranslucent"].alpha, 1);
});

// --- 4. fillVariantDefaults ---

test("fillVariantDefaults: leeres Objekt wird komplett mit dem Default der Variante aufgefuellt", () => {
  const filled = Editor.fillVariantDefaults({}, "dark");
  assert.deepEqual(filled, Tokens.DEFAULT_THEME.dark);
});

test("fillVariantDefaults: gueltige Werte bleiben erhalten, kaputte/fehlende werden ersetzt", () => {
  const filled = Editor.fillVariantDefaults({ "space.unit": 8, "color.bg": "keine-farbe" }, "light");
  assert.equal(filled["space.unit"], 8);
  assert.equal(filled["color.bg"], Tokens.DEFAULT_THEME.light["color.bg"]);
});

test("fillVariantDefaults: liefert fuer jedes bekannte Token einen Wert (kein Token fehlt in der Vorschau)", () => {
  const filled = Editor.fillVariantDefaults({ "space.unit": 8 }, "light");
  Tokens.TOKENS.forEach((t) => {
    assert.ok(Object.prototype.hasOwnProperty.call(filled, t.id), t.id + " fehlt");
  });
});

// --- 5. cssVarsFromVariant ---

test("cssVarsFromVariant: Zahlen-Token bekommt seine Einheit angehaengt", () => {
  const vars = Editor.cssVarsFromVariant(Tokens.DEFAULT_THEME.light);
  assert.equal(vars["--vt-space-unit"], "4px");
  assert.equal(vars["--vt-type-title-large"], "28px");
});

test("cssVarsFromVariant: Solidfarben-Token wird unveraendert uebernommen", () => {
  const vars = Editor.cssVarsFromVariant(Tokens.DEFAULT_THEME.light);
  assert.equal(vars["--vt-color-bg"], "#f3f3f4");
});

test("cssVarsFromVariant: color-alpha-Token wird zu rgba() aufgeloest", () => {
  const vars = Editor.cssVarsFromVariant(Tokens.DEFAULT_THEME.light);
  assert.equal(vars["--vt-color-surface-translucent"], "rgba(255, 255, 255, 0.72)");
});

test("cssVarsFromVariant: aendert sich live mit dem Wert (Tokens aendern -> Vorschau aendert sich)", () => {
  const changed = Object.assign({}, Tokens.DEFAULT_THEME.light, { "radius.card": 20 });
  const vars = Editor.cssVarsFromVariant(changed);
  assert.equal(vars["--vt-radius-card"], "20px");
});

// --- 6. validateVariantByField ---

test("validateVariantByField: Standard-Set liefert keine Feldfehler", () => {
  assert.deepEqual(Editor.validateVariantByField(Tokens.DEFAULT_THEME.light), {});
});

test("validateVariantByField: Zahl ausserhalb des erlaubten Bereichs wird gemeldet", () => {
  const byField = Editor.validateVariantByField({ "space.unit": 999 });
  assert.ok(byField["space.unit"][0].includes("999"));
});

test("validateVariantByField: ungueltige Farbe wird gemeldet", () => {
  const byField = Editor.validateVariantByField({ "color.bg": "blau" });
  assert.ok(byField["color.bg"][0].includes("Farbe"));
});

test("validateVariantByField: color-alpha mit Deckkraft ausserhalb 0..1 wird gemeldet", () => {
  const byField = Editor.validateVariantByField({ "color.surfaceTranslucent": { hex: "#ffffff", alpha: 2 } });
  assert.ok(byField["color.surfaceTranslucent"][0].includes("Deckkraft"));
});
