"use strict";

/*
 * Node-Test fuer designer/vpkg-export.js (D6), ohne echten Browser -- gleiches
 * Vorbild wie texture-studio.test.js/manifest-form.test.js. JSZip wird aus
 * designer/vendor/jszip.min.js per require() geladen (funktioniert unter
 * Node genauso wie im Browser per <script>, siehe Kopfkommentar in
 * vpkg-export.js) -- Dependency Injection statt fixem globalem Zugriff.
 *
 * Deckt das Fertig-Kriterium aus ROADMAP D6 ab ("Exportiertes Paket besteht
 * alle Pruefungen aus M1"):
 *  1. Gueltiger Fall: Manifest + zwei Texturen -> Export gelingt, das
 *     zurueckgelesene ZIP (ueber JSZip.loadAsync, ohne zu entpacken, wie P2)
 *     enthaelt ein schema-gueltiges vitrine.json (P1) und beide Texturen
 *     unter sauberen, relativen Pfaden ohne verbotene Endung (P3), PNG-Masse
 *     im 4096x4096-Limit (P3), SHA-256 stimmt mit einer unabhaengigen
 *     node:crypto-Berechnung ueberein (P4-Vorstufe -- Signatur ist nicht
 *     Teil von D6).
 *  2. Boesartiger Fall: ein Zielpfad mit '../' (am Texture-Studio-Formular
 *     unmoeglich, aber ein manipulierter Projektstand kann ihn enthalten) --
 *     der Export bricht VORHER ab, es wird kein ZIP gebaut, kein Byte
 *     geschrieben.
 *  3. Weitere P3-Faelle einzeln: verbotene Dateiendung, absoluter Pfad,
 *     ungueltiges Manifest (Pflichtfeld fehlt), Textur ueber der
 *     PNG-Groessengrenze -- jeweils Ablehnung mit Klartext-Meldung statt
 *     stillem Sanieren.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const nodeCrypto = require("node:crypto");

const JSZip = require("../vendor/jszip.min.js");
const VpkgExport = require("../vpkg-export.js");
const TextureStudio = require("../texture-studio.js");
const Validator = require("../schema-validate.js");
const ThemeTokens = require("../theme-tokens.js");
const ThemeWiring = require("../theme-wiring.js");

const repoRoot = path.join(__dirname, "..", "..");
const schemaPath = path.join(repoRoot, "registry", "schema", "vitrine.schema.json");
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));

const themeSchemaPath = path.join(repoRoot, "registry", "schema", "theme.schema.json");
const themeSchema = JSON.parse(fs.readFileSync(themeSchemaPath, "utf8"));

const examplesDir = path.join(repoRoot, "registry", "schema", "examples");

function readExample(sub, name) {
  return JSON.parse(fs.readFileSync(path.join(examplesDir, sub, name), "utf8"));
}

/** Baut ein synthetisches, aber echtes PNG-Byte-Array (Signatur + IHDR + Breite/Hoehe), wie in texture-studio.test.js. */
function buildPngBytes(width, height, extraBytes) {
  const bytes = [
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d,
    0x49, 0x48, 0x44, 0x52
  ];
  bytes.push((width >>> 24) & 0xff, (width >>> 16) & 0xff, (width >>> 8) & 0xff, width & 0xff);
  bytes.push((height >>> 24) & 0xff, (height >>> 16) & 0xff, (height >>> 8) & 0xff, height & 0xff);
  for (let i = 0; i < (extraBytes || 0); i++) {
    bytes.push(i % 256);
  }
  return bytes;
}

function pngTexture(targetPath, fileName, width, height, extraBytes) {
  const bytes = buildPngBytes(width, height, extraBytes || 8);
  return {
    targetPath: targetPath,
    fileName: fileName,
    dataUrl: TextureStudio.dataUrlFromPngBytes(bytes),
    width: width,
    height: height
  };
}

function baseProject() {
  return {
    manifest: readExample("valid", "minimal.json"),
    textures: [
      pngTexture("textures/block/stone.png", "stone.png", 16, 16),
      pngTexture("textures/item/diamond.png", "diamond.png", 32, 32)
    ]
  };
}

const options = { JSZip: JSZip, validator: Validator };

// --- 1. Gueltiger Fall: Ende-zu-Ende ---

test("buildVpkgPackage: gueltiges Projekt exportiert ein Paket, das alle M1-Pruefungen besteht", async () => {
  const project = baseProject();
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);

  assert.equal(result.ok, true, "Export sollte gelingen: " + JSON.stringify(result.errors));
  assert.equal(result.errors.length, 0);
  assert.ok(result.zipBytes instanceof Uint8Array);
  assert.match(result.sha256Hex, /^[0-9a-f]{64}$/);

  // P4-Vorstufe: SHA-256 unabhaengig mit node:crypto nachrechnen.
  const expectedHash = nodeCrypto.createHash("sha256").update(Buffer.from(result.zipBytes)).digest("hex");
  assert.equal(result.sha256Hex, expectedHash);

  // Sidecar-Format: byte-kompatibel zu cli/lib/signing.js (nur "sha256", kein "signature"/"publicKey").
  const sidecar = JSON.parse(result.sidecarJson);
  assert.deepEqual(Object.keys(sidecar), ["sha256"]);
  assert.equal(sidecar.sha256, result.sha256Hex);
  assert.equal(VpkgExport.sidecarFileNameFor("paket.vpkg"), "paket.vpkg.integrity.json");

  // P2: das Ergebnis ist ein stinknormales ZIP -- JSZip liest es zurueck, OHNE es zu entpacken.
  const readBack = await JSZip.loadAsync(result.zipBytes);
  const entryNames = Object.keys(readBack.files).sort();
  assert.deepEqual(entryNames, ["textures/block/stone.png", "textures/item/diamond.png", "vitrine.json"]);

  // P1: vitrine.json liest sich zurueck und ist schema-gueltig.
  const manifestText = await readBack.file("vitrine.json").async("string");
  const manifestInstance = JSON.parse(manifestText);
  assert.deepEqual(manifestInstance, project.manifest);
  assert.deepEqual(Validator.validate(schema, manifestInstance), []);

  // P3: Pfade sauber/relativ, keine verbotene Endung, PNG-Masse im Limit.
  for (const name of entryNames) {
    assert.deepEqual(VpkgExport.checkEntryPathSafety(name), []);
  }
  const stoneBytes = await readBack.file("textures/block/stone.png").async("uint8array");
  const stoneHeader = TextureStudio.readPngHeader(stoneBytes);
  assert.equal(stoneHeader.valid, true);
  assert.equal(TextureStudio.validateDimensions(stoneHeader.width, stoneHeader.height).valid, true);
});

// --- GATE52: ein echtes Designer-Theme-Projekt (via theme-wiring.js#ensureTheme,
// genau wie app.js es bei onProjectChanged() aufbaut) muss eine theme.json
// im Archiv-Root bekommen, mit genau den Feldnamen aus
// registry/schema/theme.schema.json (keine zweite, abweichende Benennung --
// siehe GATE22-Historie zu Feldnamen-Mismatches). ---

test("buildVpkgPackage: Theme-Projekt exportiert eine schema-gueltige theme.json im Archiv-Root", async () => {
  const project = baseProject();
  // Wie app.js#onProjectChanged: project.theme wird ueber ensureTheme()
  // aufgebaut, nicht von Hand zusammengebastelt -- das ist der echte
  // Designer-Pfad, kein Test-only-Fixture.
  project.theme = ThemeWiring.ensureTheme(undefined);

  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, true, "Export sollte gelingen: " + JSON.stringify(result.errors));

  const readBack = await JSZip.loadAsync(result.zipBytes);
  const entryNames = Object.keys(readBack.files).sort();
  assert.ok(entryNames.includes(VpkgExport.THEME_ENTRY_NAME), "theme.json fehlt im Archiv: " + entryNames.join(", "));

  const themeText = await readBack.file(VpkgExport.THEME_ENTRY_NAME).async("string");
  const themeInstance = JSON.parse(themeText);
  assert.deepEqual(themeInstance, project.theme);

  // Feldnamen MUESSEN exakt die aus theme.schema.json sein (GATE22) -- echte
  // Schema-Validierung statt nur Stichproben einzelner Schluessel.
  assert.deepEqual(Validator.validate(themeSchema, themeInstance), []);
  assert.equal(themeInstance.schemaVersion, 1);
  assert.ok(Object.prototype.hasOwnProperty.call(themeInstance.light, "space.unit"));
  assert.ok(Object.prototype.hasOwnProperty.call(themeInstance.dark, "color.accent"));
});

test("buildVpkgPackage: Projekt ohne project.theme bekommt KEINE theme.json", async () => {
  const project = baseProject();
  assert.equal(project.theme, undefined);

  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, true);

  const readBack = await JSZip.loadAsync(result.zipBytes);
  assert.ok(!Object.keys(readBack.files).includes(VpkgExport.THEME_ENTRY_NAME));
});

test("buildZipEntries: project.theme landet unter THEME_ENTRY_NAME, huebsch eingerueckt wie vitrine.json", () => {
  const project = { manifest: readExample("valid", "minimal.json"), textures: [], theme: ThemeTokens.DEFAULT_THEME };
  const entries = VpkgExport.buildZipEntries(project);
  const themeEntry = entries.find((e) => e.name === VpkgExport.THEME_ENTRY_NAME);
  assert.ok(themeEntry, "theme.json-Eintrag fehlt");
  const text = Buffer.from(themeEntry.bytes).toString("utf8");
  assert.deepEqual(JSON.parse(text), ThemeTokens.DEFAULT_THEME);
  assert.equal(text, JSON.stringify(ThemeTokens.DEFAULT_THEME, null, 2) + "\n");
});

test("buildVpkgPackage: Manifest-only-Projekt (keine Texturen) ist ebenfalls ein gueltiger Export", async () => {
  const project = { manifest: readExample("valid", "minimal.json"), textures: [] };
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, true);

  const readBack = await JSZip.loadAsync(result.zipBytes);
  assert.deepEqual(Object.keys(readBack.files), ["vitrine.json"]);
});

// --- 2. Boesartiger Fall: '..' im Zielpfad wird VOR dem Bauen abgelehnt ---

test("buildVpkgPackage: Zielpfad mit '../' wird abgelehnt, kein ZIP wird gebaut", async () => {
  const project = baseProject();
  project.textures.push(pngTexture("../../../etc/passwd.png", "boese.png", 16, 16));

  const result = await VpkgExport.buildVpkgPackage(project, schema, options);

  assert.equal(result.ok, false);
  assert.equal(result.zipBytes, undefined, "bei Ablehnung darf keine ZIP gebaut worden sein");
  assert.ok(result.errors.some((m) => m.includes("..") || m.includes("Zip-Slip")));
});

test("checkEntryPathSafety: '..'-Segment wird erkannt, auch mitten im Pfad", () => {
  assert.ok(VpkgExport.checkEntryPathSafety("textures/../secret.png").length > 0);
  assert.ok(VpkgExport.checkEntryPathSafety("../secret.png").length > 0);
});

// --- 3. Weitere P3-Faelle einzeln ---

test("buildVpkgPackage: verbotene Dateiendung wird abgelehnt", async () => {
  const project = baseProject();
  // Kann am echten Texture-Studio-Formular nicht entstehen (nur .png erlaubt) --
  // simuliert einen manipulierten Projektstand, der D6 unabhaengig abfangen muss.
  project.textures.push({
    targetPath: "scripts/evil.sh",
    fileName: "evil.sh",
    dataUrl: "data:application/octet-stream;base64,IyEvYmluL3NoCg==",
    width: 1,
    height: 1
  });
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes(".sh")));
});

test("buildVpkgPackage: absoluter Zielpfad wird abgelehnt", async () => {
  const project = baseProject();
  project.textures.push(pngTexture("/etc/passwd.png", "boese.png", 16, 16));
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("absolut")));
});

test("buildVpkgPackage: ungueltiges Manifest (Pflichtfeld fehlt) wird abgelehnt", async () => {
  const project = { manifest: readExample("invalid", "01-missing-required-field.json"), textures: [] };
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.startsWith("Manifest:")));
});

test("buildVpkgPackage: Textur ueber der 4096x4096-Grenze wird abgelehnt", async () => {
  const project = baseProject();
  project.textures.push(pngTexture("textures/block/riesig.png", "riesig.png", 5000, 16));
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("4096")));
});

test("buildVpkgPackage: doppelt vergebener Zielpfad wird abgelehnt", async () => {
  const project = baseProject();
  project.textures.push(pngTexture("textures/block/stone.png", "zweite-version.png", 16, 16));
  const result = await VpkgExport.buildVpkgPackage(project, schema, options);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("mehrfach vergeben")));
});

test("buildVpkgPackage: fehlendes JSZip liefert Klartext-Fehler statt Absturz", async () => {
  const project = baseProject();
  const result = await VpkgExport.buildVpkgPackage(project, schema, { validator: Validator });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.includes("JSZip")));
});

// --- validateProjectForExport direkt (ohne JSZip) ---

test("validateProjectForExport: leeres Projekt liefert Manifest-Pflichtfeldfehler", () => {
  const result = VpkgExport.validateProjectForExport({}, schema, Validator);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => m.startsWith("Manifest:")));
});

test("validateProjectForExport: gueltiges Projekt ist ok", () => {
  const result = VpkgExport.validateProjectForExport(baseProject(), schema, Validator);
  assert.deepEqual(result.errors, []);
  assert.equal(result.ok, true);
});

// GATE6: Klasse B hat kein .vpkg -- die .jar geht als eigenes Release-Asset
// raus (registry-package.schema.json, $defs/javamodVersionItem mit 'jarUrl').
test("validateProjectForExport: Klasse-B-Projekt wird nicht als .vpkg exportiert, sondern mit Klartext abgelehnt", () => {
  const project = baseProject();
  project.manifest.contentClass = "javamod";
  project.manifest.hotApply = false;
  project.manifest.javaMod = {
    loader: "fabric",
    minecraftVersions: ["1.21.1"],
    jar: { fileName: "klasse-b-1.0.0.jar", sha256: "c".repeat(64), size: 20480 }
  };

  const result = VpkgExport.validateProjectForExport(project, schema, Validator);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((m) => /javamod/.test(m) && /Release-Asset/.test(m)), result.errors.join(" | "));
});
