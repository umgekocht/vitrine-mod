// Erzeugt designer/schema-data.js und designer/hud-schema-data.js aus den
// jeweiligen registry/schema/*.schema.json-Dateien.
//
// Grund fuer diesen Umweg statt fetch()/XHR im Browser: der Designer laeuft
// ohne Server direkt von der Platte (file://). fetch() scheitert dort an
// CORS (siehe designer/app.js-Kopfkommentar aus D1). Synchrones
// XMLHttpRequest waere zwar in Firefox ueber file:// erlaubt, in Chromium
// jedoch standardmaessig geblockt (verlangt den Kommandozeilen-Flag
// --allow-file-access-from-files) -- eine eingebettete JS-Konstante
// funktioniert dagegen browserunabhaengig ohne Sonderfall. Diese Kopien sind
// byte-identisch (als JSON) zur jeweiligen Quelle -- kein zweites
// Schema-Format (Charter-Regel 8 "Ein Schema, zwei Seiten").
//
// D5-Ergaenzung: urspruenglich (D2) nur fuer vitrine.schema.json geschrieben.
// Jetzt eine kleine Liste von { schemaFile, outFile, varName }-Eintraegen,
// damit ein weiteres Schema (hud.schema.json, D5-Editor) nicht ein zweites,
// paralleles Generator-Skript braucht.
//
// Aufruf (nach jeder Aenderung an einem der Quell-Schemas erneut ausfuehren):
//   node registry/schema/generate-designer-schema.mjs
// oder: npm run generate-designer-schema --prefix registry

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const designerDir = path.join(here, "..", "..", "designer");

const TARGETS = [
  {
    schemaFile: "vitrine.schema.json",
    outFile: "schema-data.js",
    varName: "VITRINE_MANIFEST_SCHEMA",
    comment: "Mod (ManifestParser, P1) validieren inhaltlich dieselbe Datei\n * registry/schema/vitrine.schema.json."
  },
  {
    schemaFile: "hud.schema.json",
    outFile: "hud-schema-data.js",
    varName: "VITRINE_HUD_SCHEMA",
    comment: "Mod (HUD-Renderer, T5, folgt separat) validieren\n * inhaltlich dieselbe Datei registry/schema/hud.schema.json."
  }
];

for (const target of TARGETS) {
  const schemaPath = path.join(here, target.schemaFile);
  const outPath = path.join(designerDir, target.outFile);

  const schemaText = readFileSync(schemaPath, "utf8");
  // Rundtrip durch JSON.parse/stringify, damit ein kaputtes Quell-JSON hier
  // auffliegt statt erst im Browser.
  const schema = JSON.parse(schemaText);

  const output = `"use strict";

/*
 * AUTO-GENERIERT -- nicht von Hand bearbeiten.
 *
 * Erzeugt aus registry/schema/${target.schemaFile} durch
 * registry/schema/generate-designer-schema.mjs. Bei jeder Aenderung an
 * ${target.schemaFile} dieses Skript erneut laufen lassen:
 *   node registry/schema/generate-designer-schema.mjs
 *
 * Diese Datei enthaelt nur die Schema-Daten als eingebettete JS-Konstante --
 * kein zweites Schema-Format, nur ein anderer Transportweg fuer file://
 * (siehe Kommentar im Generator-Skript). Designer (dieses Skript) und
 * ${target.comment}
 */

var ${target.varName} = ${JSON.stringify(schema, null, 2)};

if (typeof module === "object" && module.exports) {
  module.exports = ${target.varName};
}
`;

  writeFileSync(outPath, output, "utf8");
  console.log("Geschrieben: " + path.relative(path.join(here, "..", ".."), outPath));
}
