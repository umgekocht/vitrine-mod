"use strict";

/*
 * Node-Test fuer designer/texture-studio.js (D4), ohne echten Browser --
 * gleiches Vorbild wie theme-editor.test.js/theme-editor-wiring.test.js.
 * Statt echter PNG-Dateien werden synthetische Byte-Arrays gebaut, die genau
 * das enthalten, was readPngHeader() liest (Signatur + IHDR-Chunk-Typ +
 * Breite/Hoehe als Big-Endian-int32) -- der Rest der "Datei" ist beliebiger
 * Fuellinhalt, weil die Funktion bewusst nie mehr als den Kopf anschaut
 * (siehe Kopfkommentar in texture-studio.js).
 *
 * Deckt ab:
 *  1. readPngHeader: gueltiger Kopf, zu kurze Datei, falsche Signatur,
 *     fehlendes IHDR.
 *  2. validateDimensions: 4096x4096-Grenze (P3) -- erlaubt an der Grenze,
 *     abgelehnt knapp darueber.
 *  3. powerOfTwoWarning: kein Hinweis bei 16x16, Hinweis bei 100x50.
 *  4. validateTargetPath: gueltige/ungueltige Zielpfade (absolut, "..",
 *     falsche Endung, leer).
 *  5. ensureTextures/upsertTexture/removeTexture: Projekt-Array pflegen ohne
 *     die Originalreferenz zu veraendern.
 *  6. bytesToBase64/dataUrlFromPngBytes: bekannte Testvektoren (RFC 4648).
 *  7. buildTextureEntry: End-zu-End -- gueltiger Fall liefert eine komplette
 *     Eintragsform, jeder Fehlerfall liefert eine Klartext-Meldung.
 */

const test = require("node:test");
const assert = require("node:assert/strict");

const TextureStudio = require("../texture-studio.js");

/** Baut ein synthetisches PNG-Byte-Array: Signatur(8) + Laenge(4, egal) + "IHDR"(4) + Breite(4) + Hoehe(4) + Fuellbytes. */
function buildPngBytes(width, height, extraBytes) {
  const bytes = [
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // Signatur
    0x00, 0x00, 0x00, 0x0d, // Chunk-Laenge (13, wie bei echtem IHDR) -- wird nicht gelesen
    0x49, 0x48, 0x44, 0x52 // "IHDR"
  ];
  bytes.push((width >>> 24) & 0xff, (width >>> 16) & 0xff, (width >>> 8) & 0xff, width & 0xff);
  bytes.push((height >>> 24) & 0xff, (height >>> 16) & 0xff, (height >>> 8) & 0xff, height & 0xff);
  for (let i = 0; i < (extraBytes || 0); i++) {
    bytes.push(i % 256);
  }
  return bytes;
}

// --- 1. readPngHeader ---

test("readPngHeader: gueltiger Kopf liefert Breite/Hoehe", () => {
  const bytes = buildPngBytes(64, 32);
  const result = TextureStudio.readPngHeader(bytes);
  assert.equal(result.valid, true);
  assert.equal(result.width, 64);
  assert.equal(result.height, 32);
});

test("readPngHeader: zu kurze Datei wird abgelehnt", () => {
  const result = TextureStudio.readPngHeader([0x89, 0x50, 0x4e, 0x47]);
  assert.equal(result.valid, false);
  assert.match(result.error, /zu kurz/);
});

test("readPngHeader: falsche Signatur wird abgelehnt", () => {
  const bytes = buildPngBytes(64, 32);
  bytes[0] = 0x00; // Signatur kaputt machen
  const result = TextureStudio.readPngHeader(bytes);
  assert.equal(result.valid, false);
  assert.match(result.error, /Signatur/);
});

test("readPngHeader: fehlendes IHDR wird abgelehnt", () => {
  const bytes = buildPngBytes(64, 32);
  bytes[12] = 0x00; // "IHDR" kaputt machen
  const result = TextureStudio.readPngHeader(bytes);
  assert.equal(result.valid, false);
  assert.match(result.error, /IHDR/);
});

// --- 2. validateDimensions (P3-Grenze) ---

test("validateDimensions: 4096x4096 ist noch erlaubt", () => {
  const result = TextureStudio.validateDimensions(4096, 4096);
  assert.equal(result.valid, true);
});

test("validateDimensions: 4097 in einer Dimension wird abgelehnt", () => {
  const result = TextureStudio.validateDimensions(4097, 16);
  assert.equal(result.valid, false);
  assert.match(result.message, /4096/);
});

test("validateDimensions: MAX_PNG_DIMENSION ist die einzige benannte Konstante", () => {
  assert.equal(TextureStudio.MAX_PNG_DIMENSION, 4096);
});

// --- 3. powerOfTwoWarning ---

test("powerOfTwoWarning: 16x16 (Zweierpotenz) hat keinen Hinweis", () => {
  assert.equal(TextureStudio.powerOfTwoWarning(16, 16), null);
});

test("powerOfTwoWarning: 100x50 hat einen Hinweis", () => {
  const warning = TextureStudio.powerOfTwoWarning(100, 50);
  assert.ok(typeof warning === "string" && warning.length > 0);
});

// --- 4. validateTargetPath ---

test("validateTargetPath: gueltiger relativer Pfad", () => {
  const result = TextureStudio.validateTargetPath("textures/block/stone.png");
  assert.equal(result.valid, true);
});

test("validateTargetPath: leerer Pfad wird abgelehnt", () => {
  assert.equal(TextureStudio.validateTargetPath("").valid, false);
  assert.equal(TextureStudio.validateTargetPath("   ").valid, false);
});

test("validateTargetPath: absoluter Pfad wird abgelehnt", () => {
  assert.equal(TextureStudio.validateTargetPath("/textures/block/stone.png").valid, false);
});

test("validateTargetPath: '..' wird abgelehnt", () => {
  assert.equal(TextureStudio.validateTargetPath("textures/../../../etc/passwd.png").valid, false);
});

test("validateTargetPath: falsche Endung wird abgelehnt", () => {
  assert.equal(TextureStudio.validateTargetPath("textures/block/stone.jpg").valid, false);
});

// --- 5. ensureTextures/upsertTexture/removeTexture ---

test("ensureTextures: fehlendes Feld wird zu leerem Array, keine geteilte Referenz", () => {
  assert.deepEqual(TextureStudio.ensureTextures(undefined), []);
  assert.deepEqual(TextureStudio.ensureTextures(null), []);
  const original = [{ targetPath: "a.png" }];
  const copy = TextureStudio.ensureTextures(original);
  assert.deepEqual(copy, original);
  assert.notEqual(copy, original);
});

test("upsertTexture: neuer targetPath wird angehaengt", () => {
  const list = [{ targetPath: "a.png", width: 16 }];
  const next = TextureStudio.upsertTexture(list, { targetPath: "b.png", width: 32 });
  assert.equal(next.length, 2);
  assert.equal(list.length, 1); // Original unveraendert
});

test("upsertTexture: gleicher targetPath ersetzt den bestehenden Eintrag", () => {
  const list = [{ targetPath: "a.png", width: 16 }, { targetPath: "b.png", width: 32 }];
  const next = TextureStudio.upsertTexture(list, { targetPath: "a.png", width: 999 });
  assert.equal(next.length, 2);
  assert.equal(next.find((t) => t.targetPath === "a.png").width, 999);
});

test("removeTexture: entfernt genau den passenden Eintrag", () => {
  const list = [{ targetPath: "a.png" }, { targetPath: "b.png" }];
  const next = TextureStudio.removeTexture(list, "a.png");
  assert.deepEqual(next.map((t) => t.targetPath), ["b.png"]);
  assert.equal(list.length, 2); // Original unveraendert
});

// --- 6. bytesToBase64/dataUrlFromPngBytes ---

test("bytesToBase64: bekannter Testvektor 'Man' -> 'TWFu' (RFC 4648)", () => {
  const bytes = [0x4d, 0x61, 0x6e]; // "Man"
  assert.equal(TextureStudio.bytesToBase64(bytes), "TWFu");
});

test("bytesToBase64: Padding bei Laenge nicht durch 3 teilbar", () => {
  assert.equal(TextureStudio.bytesToBase64([0x4d]), "TQ==");
  assert.equal(TextureStudio.bytesToBase64([0x4d, 0x61]), "TWE=");
});

test("dataUrlFromPngBytes: liefert eine data:image/png;base64,-URL", () => {
  const url = TextureStudio.dataUrlFromPngBytes([0x4d, 0x61, 0x6e]);
  assert.equal(url, "data:image/png;base64,TWFu");
});

// --- 7. buildTextureEntry (End-zu-End) ---

test("buildTextureEntry: gueltiger Fall liefert eine vollstaendige Eintragsform", () => {
  const bytes = buildPngBytes(16, 16, 4);
  const result = TextureStudio.buildTextureEntry({
    targetPath: "textures/block/stone.png",
    fileName: "meine-textur.png",
    bytes: bytes
  });
  assert.equal(result.ok, true);
  assert.equal(result.entry.targetPath, "textures/block/stone.png");
  assert.equal(result.entry.fileName, "meine-textur.png");
  assert.equal(result.entry.width, 16);
  assert.equal(result.entry.height, 16);
  assert.equal(result.entry.dataUrl, TextureStudio.dataUrlFromPngBytes(bytes));
  assert.equal(result.warning, null); // 16x16 ist eine Zweierpotenz
});

test("buildTextureEntry: zu grosse Textur wird mit Klartext-Fehler abgelehnt", () => {
  const bytes = buildPngBytes(5000, 16);
  const result = TextureStudio.buildTextureEntry({
    targetPath: "textures/block/stone.png",
    fileName: "riesig.png",
    bytes: bytes
  });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("4096")));
});

test("buildTextureEntry: ungueltiger Zielpfad wird mit Klartext-Fehler abgelehnt", () => {
  const bytes = buildPngBytes(16, 16);
  const result = TextureStudio.buildTextureEntry({
    targetPath: "/etc/passwd.png",
    fileName: "x.png",
    bytes: bytes
  });
  assert.equal(result.ok, false);
  assert.ok(result.errors.length > 0);
});

test("buildTextureEntry: Nicht-PNG-Datei wird mit Klartext-Fehler abgelehnt", () => {
  const bytes = [0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09];
  const result = TextureStudio.buildTextureEntry({
    targetPath: "textures/block/stone.png",
    fileName: "x.png",
    bytes: bytes
  });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("Signatur") || m.includes("kurz")));
});
