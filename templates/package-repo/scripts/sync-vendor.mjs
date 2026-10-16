#!/usr/bin/env node
"use strict";

/*
 * Haelt package-repo/vendor/vitrine-cli/ als byte-identische Kopie der drei
 * Dateien/Ordner, die `cli/vitrine.js` zur Laufzeit braucht (D8, im Stil des
 * Java-Sync-Tests aus P1, der die eingebettete Schema-Kopie im Jar gegen
 * registry/schema/vitrine.schema.json haelt):
 *
 *   - cli/vitrine.js + cli/lib/*.js   (die eigentliche CLI, keine
 *     Laufzeit-Abhaengigkeiten -- reine node:*-Bordmittel)
 *   - designer/schema-validate.js     (derselbe Auswerter wie im Designer,
 *     Charter-Regel 8 "ein Schema, zwei Seiten")
 *   - registry/schema/vitrine.schema.json (das eine Schema selbst)
 *
 * Warum ueberhaupt vendern statt per actions/checkout aus dem Hauptrepo
 * `umgekocht/vitrine-mod` zu holen: dieses Repo ist oeffentlich noch nicht
 * verfuegbar (ROADMAP A4 steht noch aus, ausserhalb des D8-Auftrags). Ein
 * Paket-Autor, der HEUTE "Use this template" benutzt, darf darauf nicht
 * warten muessen -- release.yml muss aus sich selbst heraus funktionieren.
 * Vendern ist der etablierte Umgang mit genau diesem Problem in diesem
 * Projekt (siehe P1: der Mod-Jar traegt ebenfalls eine eigene Kopie des
 * Schemas statt es zur Laufzeit nachzuladen).
 *
 * Aufruf:
 *   node templates/package-repo/scripts/sync-vendor.mjs           -- kopiert
 *   node templates/package-repo/scripts/sync-vendor.mjs --check   -- prueft nur,
 *     wirft bei Abweichung (nutzt scripts/dry-run.mjs vor jedem Trockenlauf)
 *
 * Sobald `cli/`, `designer/schema-validate.js` oder
 * `registry/schema/vitrine.schema.json` sich aendern, muss dieses Skript
 * erneut ohne `--check` laufen und die neue vendor/-Kopie mit veroeffentlicht
 * werden (Commit im veroeffentlichten `vitrine-package-template`-Repo).
 */

import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const templateRoot = path.join(here, "..");
const repoRoot = path.join(templateRoot, "..", "..");
const vendorRoot = path.join(templateRoot, "package-repo", "vendor", "vitrine-cli");

const checkOnly = process.argv.includes("--check");

/** Relative Pfade (vom jeweiligen Repo-Wurzel aus), die 1:1 vendert werden. */
const FILES = [
	"cli/vitrine.js",
	"cli/lib/init-template.js",
	"cli/lib/pack.js",
	"cli/lib/schema.js",
	"cli/lib/signing.js",
	"cli/lib/zip-writer.js",
	"designer/schema-validate.js",
	"registry/schema/vitrine.schema.json",
];

let mismatches = 0;

for (const rel of FILES) {
	const srcPath = path.join(repoRoot, ...rel.split("/"));
	const dstPath = path.join(vendorRoot, ...rel.split("/"));
	const srcBytes = readFileSync(srcPath);

	let dstBytes = null;
	try {
		dstBytes = readFileSync(dstPath);
	} catch {
		// dstPath fehlt noch -- gilt unten als Abweichung
	}

	const inSync = dstBytes !== null && Buffer.compare(srcBytes, dstBytes) === 0;

	if (checkOnly) {
		if (!inSync) {
			console.error(`ABWEICHUNG: ${rel} (Quelle vs. ${path.relative(templateRoot, dstPath)})`);
			mismatches++;
		}
		continue;
	}

	if (inSync) continue;
	mkdirSync(path.dirname(dstPath), { recursive: true });
	writeFileSync(dstPath, srcBytes);
	console.log(`kopiert: ${rel}`);
}

if (checkOnly) {
	if (mismatches > 0) {
		throw new Error(
			`${mismatches} vendor-Datei(en) nicht mehr synchron -- 'node templates/package-repo/scripts/sync-vendor.mjs' ausfuehren.`,
		);
	}
	console.log(`OK: ${FILES.length} vendor-Dateien synchron mit dem Hauptrepo.`);
} else {
	console.log(`Fertig. ${FILES.length} Dateien in vendor/vitrine-cli/ geprueft/aktualisiert.`);
}
