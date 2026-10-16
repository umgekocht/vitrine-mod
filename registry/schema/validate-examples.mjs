// Validiert die Beispiele unter registry/schema/examples/ gegen
// registry/schema/vitrine.schema.json. Kein Netzzugriff zur Laufzeit --
// der Validator laedt das Schema nur von der lokalen Platte.
//
// Aufruf: node registry/schema/validate-examples.mjs
// (vorher einmalig: npm install --prefix registry)
//
// Erwartung: alle Dateien unter examples/valid/ sind gueltig, alle unter
// examples/invalid/ sind ungueltig. Das Skript bricht mit Exit-Code 1 ab,
// wenn eine Erwartung nicht zutrifft.
//
// Nutzt @cfworker/json-schema (MIT, keine eigenen Laufzeit-Abhaengigkeiten
// -- siehe THIRD-PARTY.md) statt Ajv: Ajv 8.17+ zieht mit `fast-uri` eine
// transitive BSD-3-Clause-Abhaengigkeit fuer die URI-Aufloesung, was gegen
// Charter-Regel 5 ("nur MIT/Apache, transitive Abhaengigkeiten zaehlen mit")
// verstossen wuerde.

import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const schemaPath = path.join(here, "vitrine.schema.json");
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));

const validator = new Validator(schema, "2020-12");

function jsonFilesIn(dir) {
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(dir, f));
}

let failures = 0;

function check(dir, expectValid) {
	for (const file of jsonFilesIn(dir)) {
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		const rel = path.relative(here, file);
		if (result.valid === expectValid) {
			const errs = result.valid ? "" : ` (${result.errors.length} Fehler, wie erwartet)`;
			console.log(`OK    ${rel}${errs}`);
		} else {
			failures++;
			if (expectValid) {
				console.error(`FEHLT ${rel} sollte gueltig sein, ist es aber nicht:`);
				console.error(JSON.stringify(result.errors, null, 2));
			} else {
				console.error(`FEHLT ${rel} sollte ungueltig sein, wurde aber akzeptiert.`);
			}
		}
	}
}

check(path.join(here, "examples", "valid"), true);
check(path.join(here, "examples", "invalid"), false);

if (failures > 0) {
	console.error(`\n${failures} Erwartung(en) nicht erfuellt.`);
	process.exit(1);
} else {
	console.log("\nAlle Beispiele verhalten sich wie erwartet.");
}
