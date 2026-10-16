"use strict";

/*
 * Testet `node cli/vitrine.js init <ordner>`: legt Geruest an, das sofort
 * `validate` besteht -- keine echten Assets (Charter-Regel 6), nur Manifest +
 * README + leerer assets-Platzhalterordner.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const vitrineJs = path.join(__dirname, "..", "vitrine.js");

test("init legt vitrine.json + README.md + assets/ an, validate besteht sofort ohne Fehler", () => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), "vitrine-cli-init-"));
  const projectDir = path.join(parent, "mein-paket");

  const initOutput = execFileSync(process.execPath, [vitrineJs, "init", projectDir], { encoding: "utf8" });
  assert.match(initOutput, /Projekt angelegt/);

  assert.ok(fs.existsSync(path.join(projectDir, "vitrine.json")));
  assert.ok(fs.existsSync(path.join(projectDir, "README.md")));
  assert.ok(fs.statSync(path.join(projectDir, "assets")).isDirectory());

  const manifest = JSON.parse(fs.readFileSync(path.join(projectDir, "vitrine.json"), "utf8"));
  assert.equal(manifest.schemaVersion, 1);
  assert.match(manifest.id, /^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/);

  const validateOutput = execFileSync(process.execPath, [vitrineJs, "validate", projectDir], { encoding: "utf8" });
  assert.match(validateOutput, /ist gueltig/);

  fs.rmSync(parent, { recursive: true, force: true });
});

test("init bricht ab, wenn der Zielordner bereits existiert und nicht leer ist (kein Ueberschreiben)", () => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), "vitrine-cli-init-nonempty-"));
  const projectDir = path.join(parent, "vorhanden");
  fs.mkdirSync(projectDir, { recursive: true });
  fs.writeFileSync(path.join(projectDir, "irgendwas.txt"), "schon da", "utf8");

  let exitCode = 0;
  let stderr = "";
  try {
    execFileSync(process.execPath, [vitrineJs, "init", projectDir], { encoding: "utf8" });
  } catch (e) {
    exitCode = e.status;
    stderr = e.stderr;
  }
  assert.equal(exitCode, 1);
  assert.match(stderr, /existiert bereits/);
  assert.ok(!fs.existsSync(path.join(projectDir, "vitrine.json")), "darf nichts anlegen, wenn der Ordner schon Inhalt hat");

  fs.rmSync(parent, { recursive: true, force: true });
});
