"use strict";

/*
 * Vitrine CLI -- Bruecke zum Registry-Format (registry/schema/
 * registry-package.schema.json), analog zu cli/lib/schema.js fuer das
 * Paketmanifest (vitrine.schema.json). Charter-Regel 8 "Ein Schema, zwei
 * Seiten": `cli publish` (D9) validiert den erzeugten packages/<id>.json-
 * Eintrag mit demselben Auswerter (designer/schema-validate.js), den auch
 * die Manifest-Validierung nutzt -- kein zweiter, abweichender Auswerter.
 * designer/schema-validate.js wurde fuer D9 um rekursive Pruefung
 * verschachtelter "type": "object"-Felder erweitert (author/versions[]/
 * featured), siehe dortigen Kopfkommentar.
 */

const fs = require("node:fs");
const path = require("node:path");

const validator = require("../../designer/schema-validate.js");

const REPO_ROOT = path.join(__dirname, "..", "..");
const REGISTRY_PACKAGE_SCHEMA_PATH = path.join(REPO_ROOT, "registry", "schema", "registry-package.schema.json");

/** Laedt registry/schema/registry-package.schema.json frisch von der Platte. */
function loadRegistryPackageSchema() {
  const raw = fs.readFileSync(REGISTRY_PACKAGE_SCHEMA_PATH, "utf8");
  return JSON.parse(raw);
}

/** Validiert einen bereits geparsten packages/<id>.json-Eintrag, liefert eine flache Meldungsliste. */
function validateRegistryEntry(instance) {
  const schema = loadRegistryPackageSchema();
  return validator.validate(schema, instance);
}

module.exports = { loadRegistryPackageSchema, validateRegistryEntry, REGISTRY_PACKAGE_SCHEMA_PATH };
