package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Direkte Abdeckung des eingebetteten Draft-2020-12-Teilmengen-Validators fuer
 * {@code registry-index-page.schema.json} (Charter-Regel 8).
 */
public final class IndexPageSchemaValidatorTest {

	private static List<String> validate(String json) {
		JsonElement instance = JsonParser.parseString(json);
		return IndexPageSchemaValidator.validate(instance);
	}

	@Test
	public void acceptsValidIndexPage() {
		List<String> violations = validate(CatalogTestSupport.VALID_INDEX_PAGE_JSON);
		assertTrue(violations.isEmpty(), violations.toString());
	}

	@Test
	public void rejectsWrongSchemaVersionConst() {
		List<String> violations = validate(
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9"));
		assertFalse(violations.isEmpty());
		assertTrue(violations.get(0).contains("schemaVersion"), violations.toString());
	}

	@Test
	public void toleratesUnknownFieldsFromNewerCatalogs() {
		// 2026-10-05: 'license' kam neu in den Live-Katalog, jede ausgelieferte
		// Version lehnte daraufhin den ganzen Katalog ab. Unbekannte Felder --
		// oben und je Paket -- duerfen den Katalog nicht mehr kippen.
		List<String> violations = validate(CatalogTestSupport.VALID_INDEX_PAGE_JSON
				.replace("\"page\": 1,", "\"page\": 1,\n  \"bogus\": true,")
				.replace("\"category\": \"theme\"", "\"category\": \"theme\", \"spaeterNeu\": \"x\""));
		assertTrue(violations.isEmpty(), violations.toString());
	}

	@Test
	public void rejectsUnknownCategoryEnumInPackage() {
		List<String> violations = validate(
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"category\": \"theme\"", "\"category\": \"spielzeug\""));
		assertTrue(violations.stream().anyMatch(v -> v.contains("category")), violations.toString());
	}

	@Test
	public void rejectsPackageIdPatternViolation() {
		List<String> violations = validate(
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"umgekocht.klarglas\"", "\"Kein Gueltiger Id\""));
		assertTrue(violations.stream().anyMatch(v -> v.contains("Muster")), violations.toString());
	}

	@Test
	public void rejectsWrongTypeForNullableNextPage() {
		List<String> violations = validate(
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"nextPage\": null", "\"nextPage\": 7"));
		assertEquals(violations.size(), 1, violations.toString());
		assertTrue(violations.get(0).contains("nextPage"), violations.toString());
	}

	@Test
	public void acceptsStringValueForNullableNextPage() {
		assertTrue(validate(
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"nextPage\": null", "\"nextPage\": \"index-2.json\""))
				.isEmpty());
	}

	/**
	 * GATE116-Sonde: 5000 schema-widrige Pakete durften vorher jeweils eine
	 * eigene Zeichenkette in {@code violations} erzeugen -- 5000 Zeilen, in
	 * Summe 1,3 MB, zweimal pro Katalog-Refresh von {@link CatalogClient}
	 * geloggt. Beweist: das Ergebnis bleibt klein (Deckel + "... und N
	 * weitere"), nicht alle 5000 Verstoesse werden einzeln aufgefuehrt.
	 */
	@Test
	public void capsViolationsForManyBrokenPackages() {
		int brokenPackageCount = 5000;
		JsonArray packages = new JsonArray();
		for (int i = 0; i < brokenPackageCount; i++) {
			JsonObject pkg = new JsonObject();
			pkg.addProperty("id", "umgekocht.item" + i);
			pkg.addProperty("name", "Item " + i);
			pkg.addProperty("author", "Umgekocht");
			// Absichtlich ausserhalb des erlaubten enum -- ein garantierter
			// Verstoss je Eintrag.
			pkg.addProperty("category", "nicht-erlaubte-kategorie-" + i);
			pkg.addProperty("license", "CC-BY-4.0");
			pkg.addProperty("latestVersion", "1.0.0");
			pkg.addProperty("updatedAt", "2026-09-01T08:30:00Z");
			packages.add(pkg);
		}

		JsonObject indexPage = new JsonObject();
		indexPage.addProperty("schemaVersion", 1);
		indexPage.addProperty("generatedAt", "2026-09-10T12:00:00Z");
		indexPage.addProperty("page", 1);
		indexPage.addProperty("pageSize", brokenPackageCount);
		indexPage.addProperty("totalPages", 1);
		indexPage.addProperty("totalCount", brokenPackageCount);
		indexPage.add("nextPage", com.google.gson.JsonNull.INSTANCE);
		indexPage.add("previousPage", com.google.gson.JsonNull.INSTANCE);
		indexPage.add("packages", packages);

		List<String> violations = IndexPageSchemaValidator.validate(indexPage);

		// Deckel: hoechstens MAX_VIOLATIONS echte Verstoesse plus ein
		// einziger Platzhaltersatz -- nicht 5000 einzelne Zeilen.
		assertTrue(violations.size() <= IndexPageSchemaValidator.MAX_VIOLATIONS + 1, violations.size() + " Eintraege");

		String last = violations.get(violations.size() - 1);
		assertTrue(last.startsWith("... und "), last);
		assertTrue(last.endsWith(" weitere"), last);

		// Die 1,3-MB-Sonde aus der ROADMAP-Zeile: die zusammengefuegte
		// Meldung (wie CatalogClient sie loggt) muss klein bleiben.
		String joined = String.join("; ", violations);
		assertTrue(joined.length() < 5_000, "Meldung ist " + joined.length() + " Zeichen lang: " + joined);
	}
}
