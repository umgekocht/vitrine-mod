"use strict";

/*
 * Vitrine CLI -- `pack <ordner> <ausgabe.vpkg>` (D7). Liest einen
 * Projektordner rekursiv ein und baut daraus ein .vpkg (ZIP mit vitrine.json
 * an der Wurzel, siehe mod/.../VpkgArchive.MANIFEST_ENTRY_NAME).
 *
 * Ausgeschlossen werden: versteckte Werkzeug-/Systemdateien (.git,
 * node_modules, .DS_Store, Thumbs.db, desktop.ini), Symlinks (werden von
 * fs.readdirSync({withFileTypes:true}) als weder Datei noch Verzeichnis
 * erkannt und damit automatisch uebersprungen -- kein Symlink-Eintrag kann
 * so ueberhaupt erst entstehen, siehe VpkgZipCentralDirectoryReader-Kommentar
 * im Mod: "ein von Vitrine selbst erzeugtes .vpkg kann nie einen echten
 * Symlink-Eintrag enthalten"), sowie jede bereits vorhandene .vpkg-Datei und
 * .vpkg.integrity.json-Begleitdatei im Projektordner selbst (verhindert, dass
 * ein vorheriger Bau sich selbst mit einpackt) und die konkrete Zieldatei
 * dieses Aufrufs, falls sie zufaellig innerhalb des Projektordners liegt.
 */

const fs = require("node:fs");
const path = require("node:path");

const { buildZip } = require("./zip-writer.js");
const { validateManifest } = require("./schema.js");

const IGNORED_NAMES = new Set([".git", "node_modules", ".DS_Store", "Thumbs.db", "desktop.ini"]);

function isIgnoredExtension(name) {
  const lower = name.toLowerCase();
  return lower.endsWith(".vpkg") || lower.endsWith(".vpkg.integrity.json");
}

/**
 * Sammelt alle Dateien unter `rootDir` als ZIP-Eintraege {name, data} --
 * `name` ist immer ein Slash-getrennter, relativer Pfad ohne fuehrenden '/'
 * und ohne '..'-Segment, weil er direkt aus echten Unterordnernamen des
 * Dateisystems zusammengesetzt wird. Eintraege sind alphabetisch sortiert
 * fuer ein reproduzierbares Archiv.
 */
function collectEntries(rootDir, excludeAbsolutePaths) {
  const excluded = new Set((excludeAbsolutePaths || []).map((p) => path.resolve(p)));
  const result = [];

  function walk(dir, relPrefix) {
    const items = fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name));
    for (const item of items) {
      if (IGNORED_NAMES.has(item.name)) {
        continue;
      }
      const absPath = path.join(dir, item.name);
      if (excluded.has(path.resolve(absPath))) {
        continue;
      }
      const relPath = relPrefix ? relPrefix + "/" + item.name : item.name;
      if (item.isDirectory()) {
        walk(absPath, relPath);
      } else if (item.isFile()) {
        if (isIgnoredExtension(item.name)) {
          continue;
        }
        result.push({ name: relPath, data: fs.readFileSync(absPath) });
      }
      // Symlinks: item.isSymbolicLink() waere hier true, faellt aber in
      // keinen der beiden obigen Zweige -- wird also nie eingepackt.
    }
  }

  walk(rootDir, "");
  result.sort((a, b) => a.name.localeCompare(b.name));
  return result;
}

const MANIFEST_ENTRY_NAME = "vitrine.json";

/**
 * Baut ein .vpkg aus `sourceDir` nach `outputPath`. Wirft eine Error mit
 * `.manifestErrors` (Array von Meldungen aus schema.js/validateManifest),
 * wenn das Manifest nicht valide ist -- Charter-Regel 3 ("Sicherheit vor
 * Bequemlichkeit"): kein Paket ohne gueltiges Manifest wird gebaut.
 */
function packDirectory(sourceDir, outputPath) {
  const manifestPath = path.join(sourceDir, MANIFEST_ENTRY_NAME);
  if (!fs.existsSync(manifestPath)) {
    throw new Error("Datei '" + manifestPath + "' existiert nicht -- kein " + MANIFEST_ENTRY_NAME + " im Projektordner");
  }

  let manifestRaw;
  let manifestInstance;
  try {
    manifestRaw = fs.readFileSync(manifestPath, "utf8");
    manifestInstance = JSON.parse(manifestRaw);
  } catch (e) {
    throw new Error("'" + manifestPath + "' ist kein gueltiges JSON: " + e.message);
  }

  const manifestErrors = validateManifest(manifestInstance);
  if (manifestErrors.length > 0) {
    const err = new Error(
      "Manifest '" + manifestPath + "' verletzt registry/schema/vitrine.schema.json:\n  - " +
        manifestErrors.join("\n  - ")
    );
    err.manifestErrors = manifestErrors;
    throw err;
  }

  const entries = collectEntries(sourceDir, [outputPath, outputPath + ".integrity.json"]);
  if (!entries.some((e) => e.name === MANIFEST_ENTRY_NAME)) {
    // Kann bei einem regulaeren Ordner nicht passieren (wir haben die Datei
    // gerade gelesen), ausser vitrine.json waere z.B. ein Symlink.
    throw new Error("'" + MANIFEST_ENTRY_NAME + "' fehlt unter den eingesammelten Eintraegen (Symlink?)");
  }

  const zipBuffer = buildZip(entries);
  fs.writeFileSync(outputPath, zipBuffer);
  return { entries, outputPath, byteLength: zipBuffer.length };
}

module.exports = { collectEntries, packDirectory, MANIFEST_ENTRY_NAME };
