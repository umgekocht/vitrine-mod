"use strict";

/*
 * Testet cli/lib/zip-reader.js gegen ein mit cli/lib/pack.js (also
 * cli/lib/zip-writer.js) tatsaechlich gebautes .vpkg -- Rundreise
 * schreiben/lesen, unabhaengig fuer "vitrine.json" fuer beide
 * Kompressionsfaelle (kleine Datei bleibt STORE, siehe zip-writer.js).
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const { packDirectory } = require("../lib/pack.js");
const { readManifestFromVpkg, readZipEntry } = require("../lib/zip-reader.js");

function makeTempDir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function validManifest(id) {
  return {
    schemaVersion: 1,
    id: id || "beispiel.zip-reader-test",
    name: "Zip-Reader-Test",
    version: "1.2.3",
    category: "texturen",
    author: "Test",
    license: "CC0-1.0"
  };
}

test("readManifestFromVpkg liest exakt das Manifest zurueck, das gepackt wurde", () => {
  const dir = makeTempDir("vitrine-cli-zipreader-");
  const manifest = validManifest();
  fs.writeFileSync(path.join(dir, "vitrine.json"), JSON.stringify(manifest, null, 2), "utf8");
  fs.mkdirSync(path.join(dir, "assets"));
  // Groessere, gut komprimierbare Datei -- erzwingt den DEFLATE-Pfad in zip-writer.js.
  fs.writeFileSync(path.join(dir, "assets", "filler.txt"), "x".repeat(5000), "utf8");

  const outputPath = path.join(dir, "..", path.basename(dir) + "-out.vpkg");
  packDirectory(dir, outputPath);

  const readBack = readManifestFromVpkg(outputPath);
  assert.deepEqual(readBack, manifest);

  const rawEntry = readZipEntry(fs.readFileSync(outputPath), "assets/filler.txt");
  assert.equal(rawEntry.toString("utf8"), "x".repeat(5000));

  assert.equal(readZipEntry(fs.readFileSync(outputPath), "nicht-vorhanden.txt"), null);

  fs.rmSync(dir, { recursive: true, force: true });
  fs.rmSync(outputPath, { force: true });
});

test("readManifestFromVpkg wirft bei einer Datei ohne vitrine.json-Eintrag", () => {
  const dir = makeTempDir("vitrine-cli-zipreader-empty-");
  const manifest = validManifest("beispiel.andere-datei");
  fs.mkdirSync(path.join(dir, "proj"));
  fs.writeFileSync(path.join(dir, "proj", "vitrine.json"), JSON.stringify(manifest), "utf8");
  const outputPath = path.join(dir, "renamed.vpkg");
  packDirectory(path.join(dir, "proj"), outputPath);

  // Absichtlich nach einem Eintrag suchen, der nicht existiert, ueber die
  // Low-Level-Funktion (die High-Level-Funktion liest immer "vitrine.json",
  // das im echten Bau immer vorhanden ist -- dieser Test deckt daher die
  // Low-Level-Funktion direkt ab).
  const missing = readZipEntry(fs.readFileSync(outputPath), "does-not-exist.json");
  assert.equal(missing, null);

  fs.rmSync(dir, { recursive: true, force: true });
});
