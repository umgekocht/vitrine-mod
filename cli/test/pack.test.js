"use strict";

/*
 * Testet cli/lib/pack.js: gueltiges Manifest -> .vpkg mit vitrine.json an der
 * Wurzel, kein Zip-Slip, ungueltiges Manifest -> Abbruch ohne Datei.
 *
 * Der ZIP-Reader in diesem Test ist bewusst UNABHAENGIG von
 * cli/lib/zip-writer.js nachgebaut (eigene Central-Directory-Auswertung) --
 * ein Test, der denselben Code liest, den er schreibt, koennte denselben
 * Fehler zweimal machen und trotzdem gruen sein.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const zlib = require("node:zlib");

const { packDirectory, collectEntries } = require("../lib/pack.js");

function makeProjectDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "vitrine-cli-pack-"));
}

function validManifest(id) {
  return {
    schemaVersion: 1,
    id: id || "beispiel.pack-test",
    name: "Pack-Test",
    version: "1.0.0",
    category: "texturen",
    author: "Test",
    license: "CC0-1.0"
  };
}

/** Unabhaengiger Mini-ZIP-Reader (nur fuer diesen Test), liest das zentrale Verzeichnis roh. */
function readZipEntries(buffer) {
  let eocd = -1;
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  assert.ok(eocd >= 0, "End-of-Central-Directory-Signatur nicht gefunden");
  const totalEntries = buffer.readUInt16LE(eocd + 10);
  const centralDirOffset = buffer.readUInt32LE(eocd + 16);

  const entries = [];
  let p = centralDirOffset;
  for (let i = 0; i < totalEntries; i++) {
    assert.equal(buffer.readUInt32LE(p), 0x02014b50, "zentraler Verzeichniseintrag " + i + " hat falsche Signatur");
    const versionMadeBy = buffer.readUInt16LE(p + 4);
    const hostSystem = (versionMadeBy >> 8) & 0xff;
    const method = buffer.readUInt16LE(p + 10);
    const crc = buffer.readUInt32LE(p + 16);
    const compressedSize = buffer.readUInt32LE(p + 20);
    const uncompressedSize = buffer.readUInt32LE(p + 24);
    const nameLength = buffer.readUInt16LE(p + 28);
    const extraLength = buffer.readUInt16LE(p + 30);
    const commentLength = buffer.readUInt16LE(p + 32);
    const externalAttrs = buffer.readUInt32LE(p + 38);
    const localHeaderOffset = buffer.readUInt32LE(p + 42);
    const name = buffer.toString("utf8", p + 46, p + 46 + nameLength);
    entries.push({
      name,
      hostSystem,
      method,
      crc,
      compressedSize,
      uncompressedSize,
      externalAttrs,
      localHeaderOffset
    });
    p += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function readLocalEntryData(buffer, entry) {
  const p = entry.localHeaderOffset;
  assert.equal(buffer.readUInt32LE(p), 0x04034b50, "lokaler Dateikopf hat falsche Signatur");
  const nameLength = buffer.readUInt16LE(p + 26);
  const extraLength = buffer.readUInt16LE(p + 28);
  const dataStart = p + 30 + nameLength + extraLength;
  const raw = buffer.subarray(dataStart, dataStart + entry.compressedSize);
  if (entry.method === 0) {
    return Buffer.from(raw);
  }
  assert.equal(entry.method, 8, "nur STORE(0) oder DEFLATE(8) unterstuetzt");
  return zlib.inflateRawSync(raw);
}

test("pack baut ein .vpkg mit vitrine.json an der Wurzel, ohne Zip-Slip/absolute Pfade", () => {
  const dir = makeProjectDir();
  fs.writeFileSync(path.join(dir, "vitrine.json"), JSON.stringify(validManifest(), null, 2), "utf8");
  fs.mkdirSync(path.join(dir, "assets", "vitrine", "textures"), { recursive: true });
  fs.writeFileSync(path.join(dir, "assets", "vitrine", "textures", "example.txt"), "textur-platzhalter", "utf8");

  const outputPath = path.join(dir, "..", path.basename(dir) + "-out.vpkg");
  const result = packDirectory(dir, outputPath);
  assert.ok(result.entries.length >= 2);

  const buffer = fs.readFileSync(outputPath);
  const entries = readZipEntries(buffer);
  const names = entries.map((e) => e.name).sort();

  assert.ok(names.includes("vitrine.json"), "vitrine.json muss an der Wurzel liegen");
  for (const name of names) {
    assert.ok(!name.startsWith("/"), "kein Eintrag darf mit '/' beginnen: " + name);
    assert.ok(!name.startsWith("\\"), "kein Eintrag darf mit '\\\\' beginnen: " + name);
    assert.ok(!/^[a-zA-Z]:[\\/]/.test(name), "kein Eintrag darf ein Laufwerksbuchstabe sein: " + name);
    const segments = name.split(/[/\\]+/);
    assert.ok(!segments.includes(".."), "kein Eintrag darf ein '..'-Segment enthalten: " + name);
    // "version made by" darf kein Unix-Host sein -- sonst koennten "external
    // file attributes" faelschlich als Symlink gelesen werden (siehe
    // VpkgZipCentralDirectoryReader im Mod).
    const entry = entries.find((e) => e.name === name);
    assert.notEqual(entry.hostSystem, 3, "Host-System darf nicht Unix(3) sein: " + name);
  }

  const manifestEntry = entries.find((e) => e.name === "vitrine.json");
  const manifestBytes = readLocalEntryData(buffer, manifestEntry);
  assert.deepEqual(JSON.parse(manifestBytes.toString("utf8")), validManifest());

  fs.rmSync(dir, { recursive: true, force: true });
  fs.rmSync(outputPath, { force: true });
});

test("pack lehnt ein ungueltiges Manifest ab, ohne eine Ausgabedatei zu schreiben", () => {
  const dir = makeProjectDir();
  fs.writeFileSync(path.join(dir, "vitrine.json"), JSON.stringify({ schemaVersion: 1 }, null, 2), "utf8");
  const outputPath = path.join(dir, "..", path.basename(dir) + "-invalid-out.vpkg");

  assert.throws(() => packDirectory(dir, outputPath), /verletzt registry\/schema\/vitrine\.schema\.json/);
  assert.ok(!fs.existsSync(outputPath), "bei ungueltigem Manifest darf keine Ausgabedatei entstehen");

  fs.rmSync(dir, { recursive: true, force: true });
});

test("pack schliesst vorhandene .vpkg/.vpkg.integrity.json-Dateien im Projektordner selbst aus (kein Selbst-Einpacken)", () => {
  const dir = makeProjectDir();
  fs.writeFileSync(path.join(dir, "vitrine.json"), JSON.stringify(validManifest(), null, 2), "utf8");
  fs.writeFileSync(path.join(dir, "altbau.vpkg"), "alter-bau-platzhalter", "utf8");
  fs.writeFileSync(path.join(dir, "altbau.vpkg.integrity.json"), "{}", "utf8");

  const outputPath = path.join(dir, "neubau.vpkg");
  const result = packDirectory(dir, outputPath);
  const names = result.entries.map((e) => e.name);
  assert.ok(!names.includes("altbau.vpkg"));
  assert.ok(!names.includes("altbau.vpkg.integrity.json"));
  assert.ok(!names.includes("neubau.vpkg"), "die eigene Zieldatei darf sich nicht selbst einpacken");

  fs.rmSync(dir, { recursive: true, force: true });
});

test("collectEntries ignoriert .git/node_modules/OS-Artefakte", () => {
  const dir = makeProjectDir();
  fs.writeFileSync(path.join(dir, "vitrine.json"), "{}", "utf8");
  fs.mkdirSync(path.join(dir, ".git"));
  fs.writeFileSync(path.join(dir, ".git", "config"), "sollte nicht auftauchen", "utf8");
  fs.mkdirSync(path.join(dir, "node_modules"));
  fs.writeFileSync(path.join(dir, "node_modules", "x.txt"), "sollte nicht auftauchen", "utf8");
  fs.writeFileSync(path.join(dir, ".DS_Store"), "sollte nicht auftauchen", "utf8");
  fs.writeFileSync(path.join(dir, "Thumbs.db"), "sollte nicht auftauchen", "utf8");

  const entries = collectEntries(dir, []);
  const names = entries.map((e) => e.name);
  assert.deepEqual(names, ["vitrine.json"]);

  fs.rmSync(dir, { recursive: true, force: true });
});
