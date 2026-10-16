"use strict";

/*
 * Node-Test fuer designer/theme-wiring.js (D3), ohne echten DOM -- gleiches
 * Vorbild wie manifest-form.test.js/theme-editor.test.js. Deckt die
 * Verdrahtungslogik ab, die app.js benutzt, um das dynamisch erzeugte
 * Theme-Formular an ein Projekt anzubinden:
 *
 *  1. ensureTheme: fehlendes/kaputtes/unvollstaendiges project.theme wird
 *     immer zu einem vollstaendigen, vom Default unabhaengigen Objekt.
 *  2. fieldId/errorId: stabile, eindeutige HTML-id-Konvention -- keine zwei
 *     Tokens/Varianten/Teile kollidieren.
 *  3. groupedTokens: jedes Token erscheint in genau einer Gruppe, keines
 *     fehlt, keines doppelt.
 *  4. collectFormValues: liest ueber eine Mock-getValue()-Funktion die
 *     erwarteten Schluessel fuer buildVariantInstance ein, inklusive des
 *     color-alpha-Sonderfalls -- Ergebnis ist per buildVariantInstance direkt
 *     zu einer Variant-Instanz verarbeitbar (End-zu-End ueber den Mock).
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const Tokens = require("../theme-tokens.js");
const Editor = require("../theme-editor.js");
const Wiring = require("../theme-wiring.js");

// --- 1. ensureTheme ---

test("ensureTheme: fehlendes Theme wird zum vollstaendigen Default, aber keine geteilte Referenz", () => {
  const theme = Wiring.ensureTheme(undefined);
  assert.deepEqual(theme.light, Tokens.DEFAULT_THEME.light);
  assert.deepEqual(theme.dark, Tokens.DEFAULT_THEME.dark);
  assert.notEqual(theme.light, Tokens.DEFAULT_THEME.light);
  assert.equal(theme.schemaVersion, 1);
});

test("ensureTheme: kaputtes Objekt (falscher Typ) wird wie fehlend behandelt", () => {
  const theme = Wiring.ensureTheme("nicht-mal-ein-objekt");
  assert.deepEqual(theme.light, Tokens.DEFAULT_THEME.light);
});

test("ensureTheme: unvollstaendiges Theme wird pro Variante aufgefuellt, gueltige Werte bleiben", () => {
  const theme = Wiring.ensureTheme({ light: { "space.unit": 9 } });
  assert.equal(theme.light["space.unit"], 9);
  assert.equal(theme.light["radius.card"], Tokens.DEFAULT_THEME.light["radius.card"]);
  assert.deepEqual(theme.dark, Tokens.DEFAULT_THEME.dark);
});

test("ensureTheme: kaputter Wert in einer Variante faellt einzeln auf den Default zurueck", () => {
  const theme = Wiring.ensureTheme({ light: { "color.bg": "keine-farbe" }, dark: {} });
  assert.equal(theme.light["color.bg"], Tokens.DEFAULT_THEME.light["color.bg"]);
});

// --- 2. fieldId / errorId ---

test("fieldId: eindeutige id je Token und Variante", () => {
  const seen = new Set();
  Tokens.TOKENS.forEach((t) => {
    ["light", "dark"].forEach((variant) => {
      if (t.type === "color-alpha") {
        ["hex", "alpha"].forEach((part) => {
          const id = Wiring.fieldId(variant, t, part);
          assert.ok(!seen.has(id), "doppelte id: " + id);
          seen.add(id);
        });
      } else {
        const id = Wiring.fieldId(variant, t);
        assert.ok(!seen.has(id), "doppelte id: " + id);
        seen.add(id);
      }
    });
  });
  assert.ok(seen.size > 0);
});

test("fieldId: Punkte in der Token-Id werden zu Bindestrichen", () => {
  assert.equal(Wiring.fieldId("light", { id: "space.unit" }), "theme-field-light-space-unit");
});

test("fieldId: color-alpha-Teile haengen als Suffix an", () => {
  assert.equal(Wiring.fieldId("dark", { id: "color.surface" }, "hex"), "theme-field-dark-color-surface-hex");
  assert.equal(Wiring.fieldId("dark", { id: "color.surface" }, "alpha"), "theme-field-dark-color-surface-alpha");
});

test("errorId: eine id je Token und Variante (keine Aufspaltung bei color-alpha)", () => {
  const seen = new Set();
  Tokens.TOKENS.forEach((t) => {
    ["light", "dark"].forEach((variant) => {
      const id = Wiring.errorId(variant, t.id);
      assert.ok(!seen.has(id), "doppelte id: " + id);
      seen.add(id);
    });
  });
});

// --- 3. groupedTokens ---

test("groupedTokens: jedes Token erscheint in genau einer Gruppe", () => {
  const groups = Wiring.groupedTokens();
  const total = groups.reduce((sum, g) => sum + g.tokens.length, 0);
  assert.equal(total, Tokens.TOKENS.length);

  const seenIds = new Set();
  groups.forEach((g) => {
    g.tokens.forEach((t) => {
      assert.ok(!seenIds.has(t.id), "Token doppelt gruppiert: " + t.id);
      seenIds.add(t.id);
    });
  });
  Tokens.TOKENS.forEach((t) => {
    assert.ok(seenIds.has(t.id), "Token fehlt in Gruppierung: " + t.id);
  });
});

test("groupedTokens: Gruppenreihenfolge entspricht TOKEN_GROUPS", () => {
  const groups = Wiring.groupedTokens();
  assert.deepEqual(groups.map((g) => g.id), Tokens.TOKEN_GROUPS.map((g) => g.id));
});

// --- 4. collectFormValues ---

test("collectFormValues: liest normale und color-alpha-Felder ueber ein Mock-getValue ein", () => {
  const formValues = Wiring.variantToFormValuesMockHelper
    ? null
    : (() => {
        const raw = Editor.variantToFormValues(Tokens.DEFAULT_THEME.light);
        const store = {};
        Tokens.TOKENS.forEach((t) => {
          if (t.type === "color-alpha") {
            const names = Editor.alphaFieldNames(t.id);
            store[Wiring.fieldId("light", t, "hex")] = raw[names.hex];
            store[Wiring.fieldId("light", t, "alpha")] = raw[names.alpha];
          } else {
            store[Wiring.fieldId("light", t)] = raw[t.id];
          }
        });
        return store;
      })();

  const collected = Wiring.collectFormValues("light", (id) => formValues[id]);
  const instance = Editor.buildVariantInstance(collected);
  assert.deepEqual(instance, Tokens.DEFAULT_THEME.light);
});

test("collectFormValues: unterschiedliche Varianten greifen auf unterschiedliche ids zu", () => {
  const calls = [];
  Wiring.collectFormValues("dark", (id) => {
    calls.push(id);
    return "";
  });
  assert.ok(calls.every((id) => id.indexOf("theme-field-dark-") === 0));
});

// --- 5. otherVariant ---

test("otherVariant: schaltet zwischen light und dark um", () => {
  assert.equal(Wiring.otherVariant("light"), "dark");
  assert.equal(Wiring.otherVariant("dark"), "light");
});
