"use strict";

/*
 * Deckt D7s woertliches Fertig-Kriterium ab: "node cli/vitrine.js validate
 * <ordner> liefert dieselben Fehler wie Designer und Mod." Zwei Ebenen:
 *
 *   1. Unit-Ebene: cli/lib/schema.js#validateManifest liefert fuer alle
 *      zwoelf kaputten Beispielmanifeste exakt dieselbe Meldungsliste wie
 *      ein direkter Aufruf von designer/schema-validate.js#validate mit
 *      demselben Schema/derselben Instanz -- das beweist Byte-fuer-Byte-
 *      Gleichheit, nicht nur inhaltliche Aehnlichkeit (beide rufen sogar
 *      dieselbe Funktion auf, siehe cli/lib/schema.js-Kopfkommentar).
 *   2. Prozess-Ebene: der echte CLI-Prozess (node cli/vitrine.js validate
 *      <ordner>) wird fuer ein paar Faelle tatsaechlich gestartet und die
 *      stderr-Ausgabe auf dieselben Meldungen geprueft -- damit ist auch der
 *      Kommandozeilen-Pfad (Argumentverarbeitung, Ausgabeformat) abgedeckt,
 *      nicht nur die Bibliotheksfunktion.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const designerValidator = require("../../designer/schema-validate.js");
const { validateManifest, loadSchema } = require("../lib/schema.js");

const repoRoot = path.join(__dirname, "..", "..");
const vitrineJs = path.join(__dirname, "..", "vitrine.js");
const examplesDir = path.join(repoRoot, "registry", "schema", "examples");

function readExample(sub, name) {
  return JSON.parse(fs.readFileSync(path.join(examplesDir, sub, name), "utf8"));
}

function makeTempProject(manifestInstance) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "vitrine-cli-validate-"));
  fs.writeFileSync(path.join(dir, "vitrine.json"), JSON.stringify(manifestInstance, null, 2), "utf8");
  return dir;
}

function invalidExampleFiles() {
  return fs
    .readdirSync(path.join(examplesDir, "invalid"))
    .filter((f) => f.endsWith(".json"))
    .sort();
}

test("beide gueltigen Beispielmanifeste werden von cli/lib/schema.js akzeptiert", () => {
  assert.deepEqual(validateManifest(readExample("valid", "minimal.json")), []);
  assert.deepEqual(validateManifest(readExample("valid", "full.json")), []);
});

test("capabilities: ['hud'] gueltig, ['hud.read'] und ['unbekannt'] ungueltig mit Meldung am Feld 'capabilities' (G2)", () => {
  const base = readExample("valid", "minimal.json");

  assert.deepEqual(validateManifest(Object.assign({}, base, { capabilities: ["hud"] })), []);

  for (const bad of [["hud.read"], ["unbekannt"]]) {
    const messages = validateManifest(Object.assign({}, base, { capabilities: bad }));
    assert.ok(
      messages.some((m) => m.includes("capabilities[0]") && m.includes("erlaubt sind")),
      JSON.stringify(bad) + ": " + messages.join(" | ")
    );
  }
});

test("fuer alle zwoelf kaputten Beispielmanifeste liefert cli/lib/schema.js exakt dieselbe Meldungsliste wie designer/schema-validate.js", () => {
  const schema = loadSchema();
  const files = invalidExampleFiles();
  assert.ok(files.length >= 12, "erwarte mindestens 12 kaputte Beispielmanifeste, gefunden: " + files.length);

  for (const file of files) {
    const instance = readExample("invalid", file);
    const cliMessages = validateManifest(instance);
    const designerMessages = designerValidator.validate(schema, instance);
    assert.deepEqual(cliMessages, designerMessages, file + ": CLI und Designer muessen wortgleiche Meldungen liefern");
    assert.ok(cliMessages.length > 0, file + " haette abgelehnt werden muessen");
  }
});

test("CLI-Prozess (node cli/vitrine.js validate <ordner>) meldet fuer ein gueltiges Manifest Erfolg, exit 0", () => {
  const dir = makeTempProject(readExample("valid", "full.json"));
  const output = execFileSync(process.execPath, [vitrineJs, "validate", dir], { encoding: "utf8" });
  assert.match(output, /ist gueltig/);
});

test("CLI-Prozess liefert fuer 01-missing-required-field.json dieselbe Meldung wie designer/schema-validate.js, exit 1", () => {
  const instance = readExample("invalid", "01-missing-required-field.json");
  const dir = makeTempProject(instance);
  const schema = loadSchema();
  const expected = designerValidator.validate(schema, instance);

  let stderr = "";
  let exitCode = 0;
  try {
    execFileSync(process.execPath, [vitrineJs, "validate", dir], { encoding: "utf8" });
  } catch (e) {
    stderr = e.stderr;
    exitCode = e.status;
  }
  assert.equal(exitCode, 1);
  for (const message of expected) {
    assert.ok(stderr.includes(message), "stderr sollte enthalten: '" + message + "', war: " + stderr);
  }
});

test("CLI-Prozess liefert fuer 12-multiple-missing-fields.json dieselbe zusammengefasste Meldung wie designer/schema-validate.js", () => {
  const instance = readExample("invalid", "12-multiple-missing-fields.json");
  const dir = makeTempProject(instance);
  const schema = loadSchema();
  const expected = designerValidator.validate(schema, instance);

  let stderr = "";
  try {
    execFileSync(process.execPath, [vitrineJs, "validate", dir], { encoding: "utf8" });
  } catch (e) {
    stderr = e.stderr;
  }
  for (const message of expected) {
    assert.ok(stderr.includes(message), "stderr sollte enthalten: '" + message + "', war: " + stderr);
  }
});

test("validate: fehlender Ordner liefert klare Fehlermeldung statt Absturz", () => {
  const missing = path.join(os.tmpdir(), "vitrine-cli-does-not-exist-" + Date.now());
  let stderr = "";
  let exitCode = 0;
  try {
    execFileSync(process.execPath, [vitrineJs, "validate", missing], { encoding: "utf8" });
  } catch (e) {
    stderr = e.stderr;
    exitCode = e.status;
  }
  assert.equal(exitCode, 1);
  assert.ok(stderr.includes("existiert nicht"));
});
