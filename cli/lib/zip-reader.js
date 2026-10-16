"use strict";

/*
 * Vitrine CLI -- minimaler ZIP-Leser, nur um einen einzelnen benannten
 * Eintrag (in der Praxis: "vitrine.json") aus einem bereits gebauten .vpkg
 * zu lesen, damit `cli publish <paket.vpkg>` (D9) Id/Name/Kategorie/Autor
 * kennt, ohne dass der Aufrufer zusaetzlich den Projektordner angeben muss.
 *
 * Bewusst kein vollwertiger ZIP-Reader und keine neue Abhaengigkeit (Charter-
 * Regel 5): liest nur das zentrale Verzeichnis und die lokalen Kopfdaten des
 * gesuchten Eintrags, unterstuetzt nur STORE(0)/DEFLATE(8) -- exakt die
 * beiden Methoden, die cli/lib/zip-writer.js erzeugt (buildZip()). Jedes
 * .vpkg, das mit `cli/vitrine.js pack` gebaut wurde (auch das vendorte CLI in
 * templates/package-repo, siehe D8), ist damit lesbar. Diese Datei ist NICHT
 * die sicherheitsgepruefte Lesart aus M1/P2/P3 (die bleibt Sache des Mods,
 * mod/-Code fasst dieses Repo-Teil ohnehin nicht an) -- sie dient nur dem
 * eigenen, bereits als vertrauenswuerdig angenommenen Build-Ergebnis dieser
 * CLI/des Designers vor dem Veroeffentlichen.
 */

const fs = require("node:fs");
const zlib = require("node:zlib");

/**
 * Liest den zentralen Verzeichniseintrag fuer `entryName` aus `buffer` und
 * liefert dessen unkomprimierte Bytes, oder null, wenn der Eintrag fehlt.
 */
function readZipEntry(buffer, entryName) {
  let eocd = -1;
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) {
    throw new Error("keine End-of-Central-Directory-Signatur gefunden -- keine gueltige ZIP-Datei");
  }
  const totalEntries = buffer.readUInt16LE(eocd + 10);
  let p = buffer.readUInt32LE(eocd + 16);

  for (let i = 0; i < totalEntries; i++) {
    if (buffer.readUInt32LE(p) !== 0x02014b50) {
      throw new Error("zentraler Verzeichniseintrag " + i + " hat falsche Signatur -- keine gueltige ZIP-Datei");
    }
    const method = buffer.readUInt16LE(p + 10);
    const compressedSize = buffer.readUInt32LE(p + 20);
    const nameLength = buffer.readUInt16LE(p + 28);
    const extraLength = buffer.readUInt16LE(p + 30);
    const commentLength = buffer.readUInt16LE(p + 32);
    const localHeaderOffset = buffer.readUInt32LE(p + 42);
    const name = buffer.toString("utf8", p + 46, p + 46 + nameLength);

    if (name === entryName) {
      return extractLocalEntryData(buffer, localHeaderOffset, method, compressedSize);
    }
    p += 46 + nameLength + extraLength + commentLength;
  }
  return null;
}

function extractLocalEntryData(buffer, localHeaderOffset, method, compressedSize) {
  const p = localHeaderOffset;
  if (buffer.readUInt32LE(p) !== 0x04034b50) {
    throw new Error("lokaler Dateikopf hat falsche Signatur -- keine gueltige ZIP-Datei");
  }
  const nameLength = buffer.readUInt16LE(p + 26);
  const extraLength = buffer.readUInt16LE(p + 28);
  const dataStart = p + 30 + nameLength + extraLength;
  const raw = buffer.subarray(dataStart, dataStart + compressedSize);
  if (method === 0) {
    return Buffer.from(raw);
  }
  if (method === 8) {
    return zlib.inflateRawSync(raw);
  }
  throw new Error("nicht unterstuetzte ZIP-Kompressionsmethode " + method + " (nur STORE/DEFLATE unterstuetzt)");
}

/** Liest und parst "vitrine.json" aus einem .vpkg-Pfad. Wirft, wenn die Datei fehlt oder kein JSON ist. */
function readManifestFromVpkg(vpkgPath) {
  const buffer = fs.readFileSync(vpkgPath);
  const entry = readZipEntry(buffer, "vitrine.json");
  if (!entry) {
    throw new Error("'" + vpkgPath + "' enthaelt keinen Eintrag 'vitrine.json'");
  }
  try {
    return JSON.parse(entry.toString("utf8"));
  } catch (e) {
    throw new Error("'vitrine.json' in '" + vpkgPath + "' ist kein gueltiges JSON: " + e.message);
  }
}

module.exports = { readZipEntry, readManifestFromVpkg };
