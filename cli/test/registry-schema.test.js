"use strict";

/*
 * Testet cli/lib/registry-schema.js gegen registry/schema/registry-
 * package.schema.json -- dieselben echten Registry-Eintraege wie unter
 * registry/packages/ (K1/K6), plus gezielt kaputte Varianten fuer
 * verschachtelte Objekte (author/versions[]), um die D9-Erweiterung von
 * designer/schema-validate.js abzudecken.
 */

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const { validateRegistryEntry, REGISTRY_PACKAGE_SCHEMA_PATH } = require("../lib/registry-schema.js");

const repoRoot = path.join(__dirname, "..", "..");
const packagesDir = path.join(repoRoot, "registry", "packages");

test("registry-schema.js zeigt auf registry/schema/registry-package.schema.json", () => {
  assert.ok(fs.existsSync(REGISTRY_PACKAGE_SCHEMA_PATH));
});

test("alle echten Eintraege unter registry/packages/*.json sind gueltig", () => {
  const files = fs.readdirSync(packagesDir).filter((f) => f.endsWith(".json"));
  assert.ok(files.length >= 1);
  for (const file of files) {
    const instance = JSON.parse(fs.readFileSync(path.join(packagesDir, file), "utf8"));
    const errors = validateRegistryEntry(instance);
    assert.deepEqual(errors, [], file + ": " + errors.join("; "));
  }
});

test("fehlendes verschachteltes Pflichtfeld 'author.name' wird konkret benannt", () => {
  const instance = JSON.parse(fs.readFileSync(path.join(packagesDir, fs.readdirSync(packagesDir)[0]), "utf8"));
  delete instance.author.name;
  const errors = validateRegistryEntry(instance);
  assert.ok(errors.some((m) => m.includes("author.name") && m.includes("fehlt")), errors.join(" | "));
});

test("fehlendes verschachteltes Pflichtfeld in versions[] wird mit Index benannt", () => {
  const instance = JSON.parse(fs.readFileSync(path.join(packagesDir, fs.readdirSync(packagesDir)[0]), "utf8"));
  delete instance.versions[0].signature;
  const errors = validateRegistryEntry(instance);
  assert.ok(errors.some((m) => m.includes("versions[0].signature") && m.includes("fehlt")), errors.join(" | "));
});

test("unbekanntes Feld in einem verschachtelten Objekt wird abgelehnt", () => {
  const instance = JSON.parse(fs.readFileSync(path.join(packagesDir, fs.readdirSync(packagesDir)[0]), "utf8"));
  instance.author.nickname = "wird nicht unterstuetzt";
  const errors = validateRegistryEntry(instance);
  assert.ok(errors.some((m) => m.includes("author.nickname") && m.includes("Unbekannt")), errors.join(" | "));
});

test("ungueltige sha256 (falsche Laenge) in versions[] wird ueber das Musterfeld abgelehnt", () => {
  const instance = JSON.parse(fs.readFileSync(path.join(packagesDir, fs.readdirSync(packagesDir)[0]), "utf8"));
  instance.versions[0].sha256 = "zu-kurz";
  const errors = validateRegistryEntry(instance);
  assert.ok(errors.some((m) => m.includes("versions[0].sha256")), errors.join(" | "));
});
