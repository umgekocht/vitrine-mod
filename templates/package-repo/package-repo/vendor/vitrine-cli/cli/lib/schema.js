"use strict";

/*
 * Vitrine CLI -- Bruecke zwischen dem einen Manifest-Schema
 * (registry/schema/vitrine.schema.json, Charter-Regel 8: "Ein Schema, zwei
 * Seiten") und dem Auswerter, den der Designer bereits mitbringt
 * (designer/schema-validate.js). Diese Datei erzeugt bewusst KEINEN dritten
 * Auswerter -- sie laedt denselben Node-lauffaehigen Validator, den auch
 * designer/test/schema-validate.test.js unter Node testet, direkt per
 * require(). Damit liefert `node cli/vitrine.js validate <ordner>` exakt
 * dieselben Meldungen wie der Designer (wortgleich, nicht nur sinngemaess),
 * weil buchstaeblich derselbe Code laeuft.
 */

const fs = require("node:fs");
const path = require("node:path");

const validator = require("../../designer/schema-validate.js");

const REPO_ROOT = path.join(__dirname, "..", "..");
const SCHEMA_PATH = path.join(REPO_ROOT, "registry", "schema", "vitrine.schema.json");

/** Laedt registry/schema/vitrine.schema.json frisch von der Platte (kein Caching -- die Datei ist klein). */
function loadSchema() {
  const raw = fs.readFileSync(SCHEMA_PATH, "utf8");
  return JSON.parse(raw);
}

/**
 * Validiert ein bereits geparstes Manifest-Objekt gegen das Schema und
 * liefert dieselbe flache Meldungsliste wie designer/schema-validate.js
 * (validate) bzw. ManifestSchemaValidator#validate auf der Java-Seite.
 */
function validateManifest(instance) {
  const schema = loadSchema();
  return validator.validate(schema, instance);
}

module.exports = { loadSchema, validateManifest, SCHEMA_PATH, REPO_ROOT };
