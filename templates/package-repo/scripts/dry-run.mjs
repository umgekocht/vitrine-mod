#!/usr/bin/env node
"use strict";

/*
 * Lokaler Trockenlauf fuer templates/package-repo/ (D8, im Stil von
 * registry/scripts/dry-run.mjs aus K6).
 *
 * Simuliert genau die Schritte, die spaeter package-repo/.github/workflows/
 * release.yml in einem aus dem Template erzeugten Repo ausfuehrt -- OHNE
 * `gh`, OHNE echten Tag, OHNE Push, OHNE Netzzugriff:
 *
 *   1. `node cli/vitrine.js validate` gegen eine Staging-Kopie der Vorlage,
 *      die OHNE `.github/` und OHNE `vendor/` auskommt -- CI-Werkzeug soll
 *      nicht im ausgelieferten .vpkg landen (cli/lib/pack.js packt sonst den
 *      gesamten Ordner rekursiv ein, siehe dessen Kopfkommentar).
 *   2. `node cli/vitrine.js pack` baut aus dieser Staging-Kopie ein .vpkg in
 *      ein temporaeres Verzeichnis (die Vorlage in
 *      templates/package-repo/package-repo/ bleibt unveraendert).
 *   3. Das gebaute .vpkg besteht dieselben M1-Pruefungen, die auch
 *      cli/test/pack.test.js einfordert: `vitrine.json` liegt an der Wurzel,
 *      kein Zip-Slip, kein absoluter Pfad, kein Drive-Letter, kein
 *      Unix-Host-System-Byte (verhindert falsch gelesene Symlink-Flags im
 *      Mod). Der Zip-Leser hier ist bewusst UNABHAENGIG von
 *      cli/lib/zip-writer.js nachgebaut, aus demselben Grund wie in
 *      cli/test/pack.test.js: ein Test, der denselben Code liest, den er
 *      schreibt, koennte denselben Fehler zweimal machen.
 *   4. `node cli/vitrine.js sign` signiert das .vpkg mit einem
 *      Wegwerf-Schluessel (VITRINE_SIGNING_KEY_DIR zeigt auf ein temporaeres
 *      Verzeichnis, ruehrt NIE den echten `~/.vitrine`-Schluessel an). Die
 *      Begleitdatei wird gegen dieselben Muster geprueft, die
 *      registry/schema/registry-package.schema.json fuer sha256/signature
 *      verlangt.
 *
 * Zusaetzlich (Schritt 0): prueft, dass package-repo/vendor/vitrine-cli/
 * noch byte-identisch mit cli/, designer/schema-validate.js und
 * registry/schema/vitrine.schema.json ist -- das im veroeffentlichten Repo
 * benutzte release.yml verwendet DIESE vendor-Kopie (nicht cli/vitrine.js
 * direkt), weil das oeffentliche Hauptrepo umgekocht/vitrine-mod noch nicht
 * existiert (A4 offen). Siehe scripts/sync-vendor.mjs.
 *
 * Aufruf: node templates/package-repo/scripts/dry-run.mjs
 * Bricht bei jedem Schritt mit einer geworfenen Exception (Exit-Code != 0) ab.
 */

import { execFileSync } from "node:child_process";
import { cpSync, mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const templateRoot = path.join(here, "..");
const repoRoot = path.join(templateRoot, "..", "..");
const scaffoldDir = path.join(templateRoot, "package-repo");
const cliPath = path.join(repoRoot, "cli", "vitrine.js");

if (!statSync(cliPath, { throwIfNoEntry: false })) {
	throw new Error(
		`'${cliPath}' fehlt -- dieser Trockenlauf muss innerhalb des vitrine-Hauptrepos laufen ` +
			`(im veroeffentlichten Vorlagen-Repo holt release.yml sich cli/ per actions/checkout aus dem Hauptrepo).`,
	);
}

function step(label, fn) {
	console.log(`\n=== ${label} ===`);
	return fn();
}

function run(args) {
	return execFileSync(process.execPath, args, { encoding: "utf8" });
}

let workDir;
try {
	workDir = mkdtempSync(path.join(tmpdir(), "vitrine-package-template-dryrun-"));
	const vpkgPath = path.join(workDir, "dein-name.dein-erstes-paket-0.1.0.vpkg");
	const keyDir = path.join(workDir, "signing-key");
	const stagingDir = path.join(workDir, "paket-inhalt");

	step("0/4 vendor/vitrine-cli/ auf Synchronitaet pruefen", () => {
		const output = run([path.join(here, "sync-vendor.mjs"), "--check"]);
		process.stdout.write(output);
	});

	step("staging: Paketinhalt ohne .github/ und vendor/ kopieren (wie release.yml)", () => {
		cpSync(scaffoldDir, stagingDir, {
			recursive: true,
			filter: (src) => {
				const rel = path.relative(scaffoldDir, src).split(path.sep).join("/");
				if (rel === ".github" || rel.startsWith(".github/")) return false;
				if (rel === "vendor" || rel.startsWith("vendor/")) return false;
				return true;
			},
		});
		console.log(`OK: Staging-Kopie unter ${stagingDir} angelegt (ohne .github/, ohne vendor/).`);
	});

	step("1/4 Manifest gegen vitrine.schema.json validieren", () => {
		const output = run([cliPath, "validate", stagingDir]);
		process.stdout.write(output);
		if (!output.includes("ist gueltig")) {
			throw new Error("validate hat keine Erfolgsmeldung ausgegeben");
		}
	});

	step("2/4 .vpkg bauen", () => {
		const output = run([cliPath, "pack", stagingDir, vpkgPath]);
		process.stdout.write(output);
		if (statSync(vpkgPath).size === 0) {
			throw new Error("gebautes .vpkg ist leer");
		}
	});

	step("3/4 .vpkg gegen M1-Sicherheitsregeln pruefen (unabhaengiger Zip-Leser)", () => {
		const buffer = readFileSync(vpkgPath);

		let eocd = -1;
		for (let i = buffer.length - 22; i >= 0; i--) {
			if (buffer.readUInt32LE(i) === 0x06054b50) {
				eocd = i;
				break;
			}
		}
		if (eocd < 0) throw new Error("End-of-Central-Directory-Signatur nicht gefunden");

		const totalEntries = buffer.readUInt16LE(eocd + 10);
		const centralDirOffset = buffer.readUInt32LE(eocd + 16);

		const names = [];
		let p = centralDirOffset;
		for (let i = 0; i < totalEntries; i++) {
			if (buffer.readUInt32LE(p) !== 0x02014b50) {
				throw new Error(`zentraler Verzeichniseintrag ${i} hat falsche Signatur`);
			}
			const versionMadeBy = buffer.readUInt16LE(p + 4);
			const hostSystem = (versionMadeBy >> 8) & 0xff;
			const nameLength = buffer.readUInt16LE(p + 28);
			const extraLength = buffer.readUInt16LE(p + 30);
			const commentLength = buffer.readUInt16LE(p + 32);
			const name = buffer.toString("utf8", p + 46, p + 46 + nameLength);

			if (name.startsWith("/") || name.startsWith("\\")) {
				throw new Error(`Eintrag beginnt mit '/' oder '\\': ${name}`);
			}
			if (/^[a-zA-Z]:[\\/]/.test(name)) {
				throw new Error(`Eintrag ist ein Laufwerksbuchstabe: ${name}`);
			}
			if (name.split(/[/\\]+/).includes("..")) {
				throw new Error(`Eintrag enthaelt ein '..'-Segment: ${name}`);
			}
			if (hostSystem === 3) {
				throw new Error(`Host-System ist Unix(3), koennte im Mod als Symlink gelesen werden: ${name}`);
			}

			names.push(name);
			p += 46 + nameLength + extraLength + commentLength;
		}

		if (!names.includes("vitrine.json")) {
			throw new Error("'vitrine.json' fehlt an der Wurzel des .vpkg");
		}
		const leaked = names.filter((n) => n.startsWith(".github/") || n.startsWith("vendor/"));
		if (leaked.length > 0) {
			throw new Error(`CI-Werkzeug im .vpkg gelandet (Staging-Ausschluss griff nicht): ${leaked.join(", ")}`);
		}
		console.log(`OK: ${names.length} Eintraege, alle bestehen die M1-Pruefungen (Zip-Slip/absoluter Pfad/Unix-Host).`);
	});

	step("4/4 .vpkg signieren (Wegwerf-Schluessel, ruehrt ~/.vitrine nicht an)", () => {
		const output = execFileSync(process.execPath, [cliPath, "sign", vpkgPath], {
			encoding: "utf8",
			env: { ...process.env, VITRINE_SIGNING_KEY_DIR: keyDir },
		});
		process.stdout.write(output);

		const sidecarPath = vpkgPath + ".integrity.json";
		const sidecar = JSON.parse(readFileSync(sidecarPath, "utf8"));

		if (!/^[0-9a-f]{64}$/.test(sidecar.sha256)) {
			throw new Error(`sha256 '${sidecar.sha256}' passt nicht auf das Muster aus registry-package.schema.json`);
		}
		if (!/^[A-Za-z0-9+/]+={0,2}$/.test(sidecar.signature) || sidecar.signature.length < 20) {
			throw new Error(`signature '${sidecar.signature}' passt nicht auf das Muster aus registry-package.schema.json`);
		}
		console.log("OK: Begleitdatei hat gueltiges sha256- und signature-Format.");
	});

	console.log("\nTrockenlauf komplett gruen -- so wie release.yml es (mit echtem Tag und ggf. echtem Schluessel-Secret) auch taete.");
} finally {
	if (workDir) rmSync(workDir, { recursive: true, force: true });
}
