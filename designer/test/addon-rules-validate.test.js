"use strict";

/*
 * Node-Test fuer designer/addon-rules-validate.js (AD4) -- JS-Portierung von
 * mod/src/main/java/de/spassglas/vitrine/addon/AddonRule.java#parse. Deckt
 * dieselben Faelle ab, die der Java-Parser ablehnt bzw. akzeptiert, damit
 * Designer und CLI (cli/lib/addon-rules.js nutzt dieselbe Datei) vorab exakt
 * dieselben Luecken finden wie der Mod zur Laufzeit.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const RulesValidator = require("../addon-rules-validate.js");

test("validateRulesInstance: gueltiges Regelwerk (Array-Form) liefert keine Fehler", () => {
  const errors = RulesValidator.validateRulesInstance([
    {
      wenn: "block_abgebaut",
      ziel: "minecraft:diamond_ore",
      dann: [
        { nachricht: "Diamant!", actionbar: true },
        { effekt: "minecraft:haste", sekunden: 10, stufe: 1 }
      ]
    }
  ]);
  assert.deepEqual(errors, []);
});

test("validateRulesInstance: gueltiges Regelwerk (Objekt-Form { regeln: [...] }) liefert keine Fehler", () => {
  const errors = RulesValidator.validateRulesInstance({
    regeln: [{ wenn: "beigetreten", dann: [{ geben: "minecraft:bread", anzahl: 4 }] }]
  });
  assert.deepEqual(errors, []);
});

test("validateRulesInstance: fehlendes 'regeln' (kein Array, kein { regeln })", () => {
  assert.deepEqual(RulesValidator.validateRulesInstance({}), ["'regeln' fehlt"]);
  assert.deepEqual(RulesValidator.validateRulesInstance("nope"), ["'regeln' fehlt"]);
});

test("validateRulesInstance: mehr als MAX_RULES Regeln wird abgelehnt", () => {
  const regeln = [];
  for (let i = 0; i < RulesValidator.MAX_RULES + 1; i++) {
    regeln.push({ wenn: "timer", dann: [{ xp: 1 }] });
  }
  const errors = RulesValidator.validateRulesInstance(regeln);
  assert.deepEqual(errors, ["mehr als " + RulesValidator.MAX_RULES + " Regeln"]);
});

test("validateRulesInstance: unbekanntes Ereignis wird mit der Whitelist benannt", () => {
  const errors = RulesValidator.validateRulesInstance([{ wenn: "etwas_anderes", dann: [{ xp: 1 }] }]);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /unbekanntes Ereignis 'etwas_anderes'/);
  RulesValidator.EVENTS.forEach((event) => assert.ok(errors[0].includes(event)));
});

test("validateRulesInstance: fehlendes 'wenn'", () => {
  assert.deepEqual(RulesValidator.validateRulesInstance([{ dann: [{ xp: 1 }] }]), ["'wenn' fehlt"]);
});

test("validateRulesInstance: 'dann' fehlt/leer/zu lang", () => {
  assert.deepEqual(RulesValidator.validateRulesInstance([{ wenn: "timer" }]), [
    "'dann' braucht 1 bis " + RulesValidator.MAX_ACTIONS + " Aktionen"
  ]);
  assert.deepEqual(RulesValidator.validateRulesInstance([{ wenn: "timer", dann: [] }]), [
    "'dann' braucht 1 bis " + RulesValidator.MAX_ACTIONS + " Aktionen"
  ]);
  const zuViele = [];
  for (let i = 0; i < RulesValidator.MAX_ACTIONS + 1; i++) {
    zuViele.push({ xp: 1 });
  }
  assert.deepEqual(RulesValidator.validateRulesInstance([{ wenn: "timer", dann: zuViele }]), [
    "'dann' braucht 1 bis " + RulesValidator.MAX_ACTIONS + " Aktionen"
  ]);
});

test("validateRulesInstance: jede Aktionsform wird erkannt (nachricht/geben/effekt/xp/befehl)", () => {
  const forms = [
    { nachricht: "hi" },
    { geben: "minecraft:bread", anzahl: 1 },
    { effekt: "minecraft:haste", sekunden: 1, stufe: 1 },
    { xp: 5 },
    { befehl: "give @s minecraft:bread 1" }
  ];
  forms.forEach((action) => {
    const errors = RulesValidator.validateRulesInstance([{ wenn: "timer", dann: [action] }]);
    assert.deepEqual(errors, [], JSON.stringify(action) + " sollte gueltig sein: " + errors.join(" | "));
  });
});

test("validateRulesInstance: unbekannte Aktion (kein erkannter Schluessel) wird abgelehnt", () => {
  const errors = RulesValidator.validateRulesInstance([{ wenn: "timer", dann: [{ irgendwas: 1 }] }]);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /unbekannte Aktion/);
});

test("validateRulesInstance: Befehl ausserhalb der Whitelist wird abgelehnt (z. B. 'kill')", () => {
  const errors = RulesValidator.validateRulesInstance([{ wenn: "timer", dann: [{ befehl: "/kill @e" }] }]);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /Befehl 'kill' ist fuer Addons nicht erlaubt/);
});

test("validateRulesInstance: erlaubter Befehl mit fuehrendem '/' wird akzeptiert", () => {
  assert.deepEqual(
    RulesValidator.validateRulesInstance([{ wenn: "timer", dann: [{ befehl: "/give @s minecraft:bread 1" }] }]),
    []
  );
});

test("validateRulesJson: kaputtes JSON wird separat gemeldet", () => {
  const errors = RulesValidator.validateRulesJson("{ nicht json");
  assert.equal(errors.length, 1);
  assert.match(errors[0], /ist kein gueltiges JSON/);
});

test("validateRulesJson: gueltiger Dateitext wie ihn ein echtes Addon mitbringt", () => {
  const text = JSON.stringify({
    regeln: [
      {
        wenn: "block_abgebaut",
        ziel: "minecraft:stone",
        chance: 0.05,
        dann: [{ geben: "minecraft:raw_iron", anzahl: 1 }, { nachricht: "Gluecksfund!", actionbar: true }]
      }
    ]
  });
  assert.deepEqual(RulesValidator.validateRulesJson(text), []);
});
