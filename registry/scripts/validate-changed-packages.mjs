// PR-Pruefung fuer den Vitrine-Katalog (M4/K6).
//
// Validiert jede geaenderte Datei unter 'packages/<id>.json' gegen
// 'schema/registry-package.schema.json' -- unabhaengig vom Index-Builder,
// damit ein PR schon abgelehnt wird, bevor irgendein Index gebaut wird.
// Zusaetzlich, als Absicherung gegen doppelte 'id's, falsche Dateinamen und
// eine 'latestVersion', die zu keiner 'versions'-Eintrag passt: ein
// vollstaendiger Trockenlauf von build-index.js ueber ALLE Pakete (nicht nur
// die geaenderten) in ein temporaeres Verzeichnis -- derselbe Code, den
// registry/test/build-index.test.mjs und der Index-Bau-Workflow nutzen.
//
// Ermittelt geaenderte Dateien per 'git diff' gegen eine Basis-Ref. In
// GitHub Actions (pr-check.yml): BASE_REF/HEAD_REF aus dem Pull-Request-
// Ereignis. Lokal (dry-run.mjs, kein PR-Kontext): faellt zurueck auf "alle
// Dateien unter packages/", damit der Trockenlauf ohne Git-Historie läuft.
//
// Aufruf:
//   node registry/scripts/validate-changed-packages.mjs
//   BASE_REF=<sha> HEAD_REF=<sha> node registry/scripts/validate-changed-packages.mjs
//
// Exit-Code 1 bei jeder Verletzung -- kein halb gepruefter PR gilt als gruen.

import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";
import { buildIndex } from "../build-index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const packagesDir = path.join(registryRoot, "packages");
const schemaDir = path.join(registryRoot, "schema");

const packageSchema = JSON.parse(readFileSync(path.join(schemaDir, "registry-package.schema.json"), "utf8"));
const packageValidator = new Validator(packageSchema, "2020-12");

function changedPackageFiles() {
	const base = process.env.BASE_REF;
	const head = process.env.HEAD_REF ?? "HEAD";
	if (!base) return null; // kein PR-Kontext -- Aufrufer prueft dann alle Pakete
	try {
		const out = execFileSync(
			"git",
			["diff", "--name-only", "--relative", "--diff-filter=ACMR", `${base}...${head}`],
			{ cwd: registryRoot, encoding: "utf8" },
		);
		return out
			.split("\n")
			.map((line) => line.trim())
			.filter((line) => line.startsWith("packages/") && line.endsWith(".json"))
			.map((line) => path.join(registryRoot, line));
	} catch (err) {
		console.error(`git diff fehlgeschlagen (${err.message}) -- pruefe stattdessen alle Pakete.`);
		return null;
	}
}

let failures = 0;

function checkFile(file) {
	if (!existsSync(file)) {
		// Datei wurde im Diff geloescht -- nichts zu pruefen.
		console.log(`UEBERSPRUNGEN ${path.relative(registryRoot, file)} (geloescht)`);
		return;
	}
	const rel = path.relative(registryRoot, file);
	let data;
	try {
		data = JSON.parse(readFileSync(file, "utf8"));
	} catch (err) {
		failures++;
		console.error(`FEHLT ${rel}: ungueltiges JSON (${err.message})`);
		return;
	}

	const result = packageValidator.validate(data);
	if (!result.valid) {
		failures++;
		console.error(`FEHLT ${rel} verletzt registry-package.schema.json:`);
		for (const e of result.errors) console.error(`  - ${e.instanceLocation}: ${e.error}`);
		return;
	}

	const expectedBase = `${data.id}.json`;
	if (path.basename(file) !== expectedBase) {
		failures++;
		console.error(`FEHLT ${rel}: Dateiname passt nicht zu id '${data.id}' (erwartet '${expectedBase}')`);
		return;
	}

	console.log(`OK    ${rel}`);
}

const fs = await import("node:fs");
const diffFiles = changedPackageFiles();
let filesToCheck;

if (diffFiles === null) {
	console.log("Kein BASE_REF gesetzt -- pruefe alle Pakete unter packages/ (Trockenlauf-Modus).");
	filesToCheck = fs
		.readdirSync(packagesDir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(packagesDir, f));
} else if (diffFiles.length === 0) {
	console.log("Keine geaenderten packages/<id>.json-Dateien in diesem Diff -- Einzeldatei-Pruefung uebersprungen.");
	filesToCheck = [];
} else {
	filesToCheck = diffFiles;
}

for (const file of filesToCheck) checkFile(file);

if (failures > 0) {
	console.error(`\n${failures} Registry-Paket(e) verletzen ihr Schema oder ihren Dateinamen.`);
	process.exit(1);
}

// Zweite Stufe: vollstaendiger Builder-Lauf ueber ALLE Pakete (Duplikate,
// latestVersion-Konsistenz -- das kann eine Einzeldatei-Pruefung nicht
// sehen). Schreibt in ein temporaeres Verzeichnis, nichts im Repo aendert sich.
const tmpOut = mkdtempSync(path.join(tmpdir(), "vitrine-registry-pr-check-"));
try {
	const result = buildIndex({ packagesDir, outDir: tmpOut, now: "1970-01-01T00:00:00.000Z" });
	console.log(
		`\nOK: vollstaendiger Index-Bau ueber ${result.packages.length} Paket(e) erfolgreich (Dateinamen, Duplikate, latestVersion konsistent).`,
	);
} catch (err) {
	console.error(`\nFEHLT: vollstaendiger Index-Bau schlaegt fehl:\n${err.message}`);
	process.exit(1);
} finally {
	rmSync(tmpOut, { recursive: true, force: true });
}

console.log("\nAlle Pruefungen bestanden.");
