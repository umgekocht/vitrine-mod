// GATE121: Katalog-/Cache-Teil des A1-Frisch-Tests ohne GUI-Fenster und ohne
// erhoehte Rechte.
//
// scripts/fresh-test.ps1 startet ein echtes Minecraft-Fenster und braucht fuer
// -SimulateNoNetwork ein erhoehtes PowerShell-Fenster (Firewall-Regel) --
// beides laesst sich nicht automatisiert rot/gruen fahren. Was aber ganz ohne
// Fenster und ohne Administrator prueffbar ist: der HTTP-Vertrag, auf den sich
// de.spassglas.vitrine.catalog.HttpCatalogClient/EtagCache (mod-seitig, hier
// NICHT angefasst) verlassen:
//   1. die einkompilierte Index-URL ist erreichbar und liefert gueltiges,
//      schema-konformes JSON (dasselbe Schema, das der Designer schreibt und
//      der Mod liest -- registry/schema/registry-index-page.schema.json,
//      Charter-Regel 8),
//   2. ein zweiter Abruf mit If-None-Match liefert 304 (das ist exakt, wie
//      EtagCache.java den Cache treffen laesst),
//   3. ein nicht aufloesbarer Host wirft einen Netzfehler, OHNE
//      New-NetFirewallRule/Administrator -- die ".invalid"-TLD ist nach
//      RFC 2606 reserviert und loest garantiert nie auf.
//
// Was dieses Skript NICHT prueft: ob der Mod-Client selbst (Java,
// HttpCatalogClient/EtagCache) mit dieser Antwort richtig umgeht -- das
// decken die Mod-eigenen Tests ab (mod/src/test/.../catalog/, dort NICHT
// angefasst). Dieses Skript prueft nur den externen Vertrag, den jener Code
// voraussetzt -- und die Klickstrecke "installieren, hot sehen,
// deinstallieren" im echten Fenster bleibt weiterhin eine Mensch-Aufgabe
// (siehe PROGRESS.md, Abschnitt "Menschen-Aufgaben").
//
// Aufruf: node registry/scripts/check-live-catalog.mjs [--url <index-url>]
//         oder: npm run check-live-catalog --prefix registry
//         oder ueber den duennen Wrapper: scripts/fresh-test-catalog.ps1
// Exit 0 bei drei gruenen Pruefungen, sonst Exit 1.

import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");

// Muss zur in
// mod/src/main/java/de/spassglas/vitrine/catalog/CatalogEndpoints.java
// einkompilierten DEFAULT_INDEX_URL passen -- wie scripts/fresh-test.ps1 das
// fuer seinen $NoNetworkHost schon haelt. Aendert sich die Mod-Konstante,
// muss diese Zeile mitgezogen werden.
const DEFAULT_INDEX_URL = "https://umgekocht.github.io/vitrine-registry/index/v1/index.json";

const urlFlagIndex = process.argv.indexOf("--url");
const indexUrl = urlFlagIndex !== -1 ? process.argv[urlFlagIndex + 1] : DEFAULT_INDEX_URL;

const REQUEST_TIMEOUT_MS = 10_000;

function step(label) {
	console.log(`\n=== ${label} ===`);
}

let failures = 0;
function fail(msg) {
	console.error(`FAIL: ${msg}`);
	failures++;
}
function pass(msg) {
	console.log(`PASS: ${msg}`);
}

async function fetchWithTimeout(url, options = {}) {
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
	try {
		return await fetch(url, { ...options, signal: controller.signal });
	} finally {
		clearTimeout(timer);
	}
}

step("1/3 Index-URL erreichbar, gueltiges JSON, schema-konform");
let firstEtag = null;
try {
	const response = await fetchWithTimeout(indexUrl);
	if (response.status !== 200) {
		fail(`HTTP ${response.status} von ${indexUrl} (erwartet 200)`);
	} else {
		firstEtag = response.headers.get("etag");
		const bodyText = await response.text();
		let json = null;
		try {
			json = JSON.parse(bodyText);
		} catch (e) {
			fail(`Antwort von ${indexUrl} ist kein gueltiges JSON: ${e.message}`);
		}
		if (json !== null) {
			const schema = JSON.parse(
				readFileSync(path.join(registryRoot, "schema", "registry-index-page.schema.json"), "utf8"),
			);
			const validator = new Validator(schema, "2020-12");
			const result = validator.validate(json);
			if (result.valid) {
				pass(
					`${indexUrl} ist erreichbar und erfuellt registry-index-page.schema.json ` +
						`(${Array.isArray(json.packages) ? json.packages.length : "?"} Paket(e))`,
				);
			} else {
				fail(
					`${indexUrl} verletzt registry-index-page.schema.json:\n` +
						JSON.stringify(result.errors, null, 2),
				);
			}
		}
	}
} catch (e) {
	fail(`${indexUrl} nicht erreichbar: ${e.message}`);
}

step("2/3 Cache-Verhalten: zweiter Abruf mit If-None-Match liefert 304");
if (!firstEtag) {
	fail(
		`Erste Antwort von ${indexUrl} hat keinen ETag-Header -- EtagCache.java (mod-seitig) kann ohne ` +
			"ETag nie bedingt abfragen, jeder Start laedt den vollen Rumpf neu",
	);
} else {
	try {
		const response = await fetchWithTimeout(indexUrl, { headers: { "If-None-Match": firstEtag } });
		if (response.status === 304) {
			pass(`Zweiter Abruf mit If-None-Match: ${firstEtag} liefert 304 Not Modified (Cache greift)`);
		} else {
			fail(
				`Zweiter Abruf mit If-None-Match liefert HTTP ${response.status} statt 304 -- der ` +
					"Mod-seitige ETag-Cache wuerde nie einen Treffer sehen",
			);
		}
	} catch (e) {
		fail(`Zweiter Abruf (ETag-Test) schlug fehl: ${e.message}`);
	}
}

step("3/3 Verhalten bei nicht erreichbarer URL (ohne Firewall/Administrator)");
// ".invalid" ist nach RFC 2606 reserviert und loest garantiert nie auf --
// simuliert "Katalog nicht erreichbar" ohne New-NetFirewallRule/erhoehtes
// Fenster. Entspricht dem Pfad, den CatalogUnavailableException im Mod
// abdeckt (dort nicht angefasst).
const unreachableUrl = "https://vitrine-registry-unreachable.invalid/index/v1/index.json";
try {
	await fetchWithTimeout(unreachableUrl);
	fail(
		`Erwartete einen Netzfehler fuer ${unreachableUrl}, aber der Abruf kam durch -- Testannahme ` +
			"(nicht aufloesbarer Host) stimmt nicht mehr",
	);
} catch (e) {
	pass(`Nicht aufloesbarer Host wirft wie erwartet einen Netzfehler: ${e.message}`);
}

console.log("");
if (failures > 0) {
	console.error(`${failures} Pruefung(en) fehlgeschlagen.`);
	// Kein process.exit(1) hier: ein erzwungenes Schliessen offener
	// Timer/DNS-Handles direkt nach einem fetch()-Fehlschlag loeste unter
	// Node 24 auf Windows eine native libuv-Assertion aus
	// ("UV_HANDLE_CLOSING", Exit 127 statt 1). process.exitCode laesst den
	// Event-Loop stattdessen von selbst (und sauber) auslaufen.
	process.exitCode = 1;
} else {
	console.log("Alle Katalog-/Cache-Pruefungen gruen.");
	process.exitCode = 0;
}
