// Regressionstest fuer GATE114.
//
// Am 2026-10-04 wurde festgestellt: die veroeffentlichte
// 'index/v1/featured.json' in https://github.com/umgekocht/vitrine-registry
// hinkte dem Builder hinterher -- 'build-index.js' erzeugt das Pflichtfeld
// 'popular' seit X2, die live ausgelieferte Datei (generatedAt 2026-10-04)
// stammte aber noch aus einem aelteren Builder-Stand ohne dieses Feld. Der
// Mod-Client (StoreCatalogController, mod-seitig) schluckte den daraus
// folgenden Schemafehler nur als INFO -- die Startreihen "Featured" und
// "Beliebt" blieben dadurch auf jedem fremden Rechner dauerhaft leer, ohne
// dass irgendein Test das aufgefangen haette.
//
// Dieser Test ist dem Muster von
// mod/.../vpkg/EmbeddedKeyMatchesLiveCatalogTest.java nachgebaut (siehe dort
// fuer die Mod-Seite, hier NICHT angefasst): er prueft nicht nur den
// Builder-Code gegen Fixtures (das macht bereits build-index.test.mjs),
// sondern den tatsaechlichen, echten Katalog-Inhalt gegen das Schema -- also
// genau die Datei, die ausgeliefert wird/werden wuerde.
//
// Zwei Quellen, in dieser Reihenfolge:
//   1. Ein lokaler Checkout von 'vitrine-registry' (Pfad per Umgebungsvariable
//      VITRINE_REGISTRY_CHECKOUT, sonst die ueblichen Geschwisterordner-Namen)
//      -- falls vorhanden, wird dessen bereits veroeffentlichte
//      'index/v1/featured.json' direkt gegen das Schema geprueft. Das ist der
//      schaerfste Test: er prueft genau das, was online steht.
//   2. Sonst: frisch mit buildIndex() aus den ECHTEN 'registry/packages/'
//      erzeugt (nicht aus den 3 K1-Fixtures wie build-index.test.mjs) --
//      das ist das, was der naechste Push veroeffentlichen wuerde. Kein
//      Netzzugriff hier, damit der Test auch ohne Internet (z.B. in
//      pr-check.yml) deterministisch laeuft.
//
// Aufruf: node --test registry/test/featured-live-catalog.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Validator } from "@cfworker/json-schema";
import { buildIndex } from "../build-index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const realPackagesDir = path.join(registryRoot, "packages");
const schemaDir = path.join(registryRoot, "schema");

function loadSchema(name) {
	return JSON.parse(readFileSync(path.join(schemaDir, name), "utf8"));
}

// Sucht einen lokalen Checkout von 'vitrine-registry' (das separate, live
// geschaltete Katalog-Repo, siehe ALLOWED-REMOTES.md) -- zuerst per
// Umgebungsvariable, sonst an den Stellen, an denen ein manuelles
// `git clone` neben diesem Monorepo typischerweise landet. Gibt null zurueck,
// wenn keiner existiert -- dann greift der Fallback (frischer Bau unten).
function findLiveCheckoutFeaturedJson() {
	const candidates = [];
	if (process.env.VITRINE_REGISTRY_CHECKOUT) {
		candidates.push(process.env.VITRINE_REGISTRY_CHECKOUT);
	}
	candidates.push(
		path.join(registryRoot, "..", "..", "vitrine-registry"),
		path.join(registryRoot, "..", "vitrine-registry"),
		path.join(registryRoot, "..", "vitrine-registry-checkout"),
	);
	for (const candidate of candidates) {
		const featuredPath = path.join(candidate, "index", "v1", "featured.json");
		if (existsSync(featuredPath)) return featuredPath;
	}
	return null;
}

function validateOrThrow(validator, data, label) {
	const result = validator.validate(data);
	if (!result.valid) {
		throw new Error(`${label} verletzt sein Schema:\n${JSON.stringify(result.errors, null, 2)}`);
	}
}

test("der echte/aktuelle featured.json-Inhalt haelt registry-featured.schema.json ein (GATE114)", async (t) => {
	const validator = new Validator(loadSchema("registry-featured.schema.json"), "2020-12");

	const liveCheckoutPath = findLiveCheckoutFeaturedJson();
	let featured;
	let source;

	if (liveCheckoutPath) {
		featured = JSON.parse(readFileSync(liveCheckoutPath, "utf8"));
		source = `lokaler vitrine-registry-Checkout (${liveCheckoutPath})`;
	} else {
		// Fallback: frisch aus den ECHTEN registry/packages/ bauen (nicht aus
		// Fixtures) -- das ist exakt das, was der naechste Push nach
		// 'vitrine-registry' erzeugen wuerde.
		const outDir = mkdtempSync(path.join(tmpdir(), "vitrine-registry-featured-live-test-"));
		t.after(() => rmSync(outDir, { recursive: true, force: true }));
		const result = buildIndex({ packagesDir: realPackagesDir, outDir, now: "2026-10-05T00:00:00.000Z" });
		featured = result.featured;
		source = "frisch gebaut aus registry/packages/ (kein lokaler vitrine-registry-Checkout gefunden)";
	}

	// Das eigentliche Regressionsziel: 'popular' ist ein Pflichtfeld, GATE114
	// brach genau daran. Separate, sprechende Assertion VOR der generischen
	// Schema-Pruefung, damit ein Fehlschlag sofort die Ursache nennt.
	assert.ok(
		Object.prototype.hasOwnProperty.call(featured, "popular"),
		`featured.json (${source}) hat kein Pflichtfeld 'popular' -- genau der GATE114-Fehler`,
	);

	validateOrThrow(validator, featured, `featured.json (${source})`);
});
