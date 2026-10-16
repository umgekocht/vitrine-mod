#!/usr/bin/env node
"use strict";
/*
 * GATE52: erzeugt mit dem echten Designer-Exportpfad (theme-wiring.js#ensureTheme
 * + vpkg-export.js#buildVpkgPackage, genau wie app.js) ein Theme-Paket und legt
 * es als Test-Ressource in den Mod. Der Mod-Test ThemePackCoordinatorTest
 * installiert genau diese Bytes -- damit ist bewiesen, dass ein im Browser
 * gebautes Theme im Spiel wirkt, nicht nur ein handgebautes Fixture.
 *
 * Neu erzeugen nach Aenderungen am Export:
 *   node designer/tools/make-mod-theme-fixture.js
 */
const fs = require("node:fs");
const path = require("node:path");

const JSZip = require("../vendor/jszip.min.js");
const VpkgExport = require("../vpkg-export.js");
const Validator = require("../schema-validate.js");
const ThemeWiring = require("../theme-wiring.js");

const repoRoot = path.join(__dirname, "..", "..");
const schema = JSON.parse(fs.readFileSync(path.join(repoRoot, "registry", "schema", "vitrine.schema.json"), "utf8"));
const outDir = path.join(repoRoot, "mod", "src", "test", "resources", "de", "spassglas", "vitrine", "pack");

// Werte, an denen der Mod-Test das Theme wiedererkennt.
const ACCENT = "#12ab34";
const HEADER_HEIGHT = 77;

const project = {
  manifest: {
    schemaVersion: 1,
    id: "designer.exporttheme",
    name: "Designer-Exporttheme",
    version: "1.0.0",
    category: "theme",
    author: "Vitrine-Testfixture",
    license: "CC0-1.0",
    capabilities: ["theme"]
  },
  textures: [],
  theme: ThemeWiring.ensureTheme(undefined)
};
for (const variant of ["light", "dark"]) {
  project.theme[variant]["color.accent"] = ACCENT;
  project.theme[variant]["header.height"] = HEADER_HEIGHT;
}

VpkgExport.buildVpkgPackage(project, schema, { JSZip: JSZip, validator: Validator }).then((result) => {
  if (!result.ok) {
    console.error("Export abgelehnt:\n" + result.errors.join("\n"));
    process.exit(1);
  }
  fs.mkdirSync(outDir, { recursive: true });
  const target = path.join(outDir, "designer-export-theme.vpkg");
  fs.writeFileSync(target, Buffer.from(result.zipBytes));
  console.log("geschrieben:", path.relative(repoRoot, target), result.zipBytes.length, "Bytes, sha256", result.sha256Hex);
});
