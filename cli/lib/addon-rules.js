"use strict";

/*
 * Vitrine CLI -- Bruecke zwischen `regeln/*.json` (AD2, Live-Verhaltensregeln
 * eines Addons) und dem Auswerter, den der Designer bereits mitbringt
 * (designer/addon-rules-validate.js, selbst eine JS-Portierung von
 * AddonRule.java). Dieselbe Bruecken-Logik wie cli/lib/schema.js fuer das
 * Manifest (Charter-Regel 8 sinngemaess): kein dritter Auswerter, derselbe
 * Code laeuft unter Node (hier) und im Browser (Designer).
 */

const fs = require("node:fs");
const path = require("node:path");

const RulesValidator = require("../../designer/addon-rules-validate.js");

/** Verzeichnisname, in dem ein Addon seine Live-Verhaltensregeln ablegt (AddonRule.java). */
const RULES_DIR_NAME = "regeln";

/**
 * Findet alle `*.json`-Dateien unter `<projectDir>/regeln/` (nicht rekursiv --
 * AddonRule.java liest ebenfalls nur diese flache Ebene). Leeres Array, wenn
 * der Ordner fehlt (kein Addon-Paket oder ein Addon ohne Live-Regeln, beides
 * gueltig).
 */
function findRuleFiles(projectDir) {
  const rulesDir = path.join(projectDir, RULES_DIR_NAME);
  if (!fs.existsSync(rulesDir) || !fs.statSync(rulesDir).isDirectory()) {
    return [];
  }
  return fs
    .readdirSync(rulesDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith(".json"))
    .map((entry) => path.join(rulesDir, entry.name))
    .sort();
}

/**
 * Validiert jede `regeln/*.json`-Datei unter `projectDir` gegen dieselbe
 * Ereignis-/Aktions-/Befehls-Whitelist wie AddonRule.java (via
 * designer/addon-rules-validate.js). Liefert eine flache Liste von Meldungen
 * im Format "regeln/<datei>.json: <meldung>" -- leer, wenn alles (oder gar
 * nichts) vorhanden ist.
 */
function validateRuleFiles(projectDir) {
  const errors = [];
  for (const filePath of findRuleFiles(projectDir)) {
    const relName = RULES_DIR_NAME + "/" + path.basename(filePath);
    const raw = fs.readFileSync(filePath, "utf8");
    const fileErrors = RulesValidator.validateRulesJson(raw);
    fileErrors.forEach((message) => {
      errors.push(relName + ": " + message);
    });
  }
  return errors;
}

module.exports = { RULES_DIR_NAME, findRuleFiles, validateRuleFiles };
