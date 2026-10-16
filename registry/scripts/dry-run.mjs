// Lokaler Trockenlauf fuer registry/ (M4/K6).
//
// Simuliert genau die Schritte, die spaeter die beiden GitHub-Actions
// ausfuehren (.github/workflows/pr-check.yml, .github/workflows/
// build-index.yml) -- ohne 'gh', ohne echten Push, ohne Netzzugriff. Fertig-
// Kriterium K6: "Pruefskript laeuft lokal gruen ueber die Beispielpakete;
// Index-Builder erzeugt index/v1/* reproduzierbar."
//
// Schritte:
//   1. Manifest-Beispiele gegen vitrine.schema.json (schema/validate-examples.mjs)
//   2. Automatisierte Tests (test/*.test.mjs)
//   3. Registry-Pakete gegen registry-package.schema.json (scripts/validate-changed-packages.mjs,
//      im Trockenlauf-Modus: alle Pakete, da kein PR-Diff vorliegt)
//   4. Index-Bau zweimal in getrennte temporaere Verzeichnisse mit fest
//      eingefrorenem --now, danach Byte-fuer-Byte-Vergleich aller erzeugten
//      Dateien unter index/v1/ -- beweist Reproduzierbarkeit.
//
// Aufruf: node registry/scripts/dry-run.mjs  (oder: npm run dry-run --prefix registry)
// Bricht bei jedem Schritt mit Exit-Code 1 ab, sobald einer fehlschlaegt.

import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { buildIndex } from "../build-index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const packagesDir = path.join(registryRoot, "packages");

function step(label, fn) {
	console.log(`\n=== ${label} ===`);
	fn();
}

function run(cmd, args) {
	execFileSync(cmd, args, { cwd: registryRoot, stdio: "inherit" });
}

step("1/4 Manifest-Beispiele gegen Schema", () => {
	run(process.execPath, ["schema/validate-examples.mjs"]);
});

step("2/4 Automatisierte Tests", () => {
	run(process.execPath, ["--test", "test/*.test.mjs"]);
});

step("3/4 Registry-Pakete gegen registry-package.schema.json (Trockenlauf: alle Pakete)", () => {
	run(process.execPath, ["scripts/validate-changed-packages.mjs"]);
});

function collectFiles(dir) {
	const out = [];
	for (const entry of readdirSync(dir)) {
		const full = path.join(dir, entry);
		if (statSync(full).isDirectory()) out.push(...collectFiles(full));
		else out.push(full);
	}
	return out.sort();
}

step("4/4 Index-Bau zweimal, Reproduzierbarkeit pruefen", () => {
	const now = "2026-09-03T00:00:00.000Z";
	const outA = mkdtempSync(path.join(tmpdir(), "vitrine-registry-dryrun-a-"));
	const outB = mkdtempSync(path.join(tmpdir(), "vitrine-registry-dryrun-b-"));
	try {
		const resultA = buildIndex({ packagesDir, outDir: outA, now });
		const resultB = buildIndex({ packagesDir, outDir: outB, now });

		const filesA = collectFiles(path.join(outA, "index", "v1")).map((f) => path.relative(outA, f));
		const filesB = collectFiles(path.join(outB, "index", "v1")).map((f) => path.relative(outB, f));
		if (JSON.stringify(filesA) !== JSON.stringify(filesB)) {
			throw new Error(`Unterschiedliche Dateilisten zwischen den zwei Laeufen:\n${filesA}\nvs.\n${filesB}`);
		}

		let mismatches = 0;
		for (const rel of filesA) {
			const contentA = readFileSync(path.join(outA, rel), "utf8");
			const contentB = readFileSync(path.join(outB, rel), "utf8");
			if (contentA !== contentB) {
				mismatches++;
				console.error(`ABWEICHUNG in ${rel} zwischen den zwei Laeufen.`);
			} else {
				console.log(`OK    ${rel} (byte-identisch)`);
			}
		}
		if (mismatches > 0) throw new Error(`${mismatches} Datei(en) nicht reproduzierbar.`);

		console.log(
			`\nOK: ${resultA.packages.length} Paket(e), ${resultA.pages.length} Index-Seite(n), ` +
				`${filesA.length} erzeugte Datei(en) unter index/v1/, alle byte-identisch bei wiederholtem Lauf.`,
		);
	} finally {
		rmSync(outA, { recursive: true, force: true });
		rmSync(outB, { recursive: true, force: true });
	}
});

console.log("\nTrockenlauf komplett gruen.");
