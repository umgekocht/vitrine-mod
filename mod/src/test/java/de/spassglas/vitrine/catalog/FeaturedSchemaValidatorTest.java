package de.spassglas.vitrine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Direkte Abdeckung des eingebetteten Draft-2020-12-Teilmengen-Validators fuer
 * {@code registry-featured.schema.json} (Charter-Regel 8) -- insbesondere die
 * {@code $ref}-Aufloesung von {@code $defs/curationItem}, die {@code items[]}
 * und {@code popular[]} (ROADMAP X2) teilen. Ein Verstoss, der nur ueber den
 * aufgeloesten Verweis erkennbar ist (z. B. ein zusaetzliches Feld in einem
 * {@code popular}-Eintrag), muss genauso erkannt werden wie bei einem direkt
 * ausgeschriebenen Schema -- sonst waere die Pruefung von {@code popular} ein
 * Blindgaenger, der Klasse-A/B-Sicherheitspruefungen vorgelagert ist
 * (Charter-Regel: nichts wird angewendet, bevor validiert ist).
 */
public final class FeaturedSchemaValidatorTest {

	/**
	 * Der {@code popular}-Eintrag aus {@link CatalogTestSupport#VALID_FEATURED_JSON},
	 * so wie er nach dem Entzuckern des Text-Blocks tatsaechlich in der Datei
	 * steht -- Grundlage fuer die gezielten Verstoss-Varianten unten.
	 */
	private static final String POPULAR_ENTRY_BLOCK = "    {\n"
			+ "      \"id\": \"umgekocht.klarglas\",\n"
			+ "      \"order\": 0,\n"
			+ "      \"license\": \"CC-BY-4.0\",\n"
			+ "      \"note\": \"Meistgenutzt\"\n"
			+ "    }";

	private static List<String> validate(String json) {
		JsonElement instance = JsonParser.parseString(json);
		return FeaturedSchemaValidator.validate(instance);
	}

	@Test
	public void acceptsValidFeaturedWithItemsAndPopular() {
		assertTrue(CatalogTestSupport.VALID_FEATURED_JSON.contains(POPULAR_ENTRY_BLOCK),
				"Testvoraussetzung: die Fixture muss den erwarteten popular-Block woertlich enthalten");
		List<String> violations = validate(CatalogTestSupport.VALID_FEATURED_JSON);
		assertTrue(violations.isEmpty(), violations.toString());
	}

	@Test
	public void rejectsMissingPopularField() {
		String withoutPopular = CatalogTestSupport.VALID_FEATURED_JSON.replace(
				"],\n  \"popular\": [\n" + POPULAR_ENTRY_BLOCK + "\n  ]", "]");
		assertFalse(withoutPopular.contains("popular"), "Testvoraussetzung: 'popular' muss komplett entfernt sein");

		List<String> violations = validate(withoutPopular);

		assertFalse(violations.isEmpty());
		assertTrue(violations.stream().anyMatch(v -> v.contains("popular")), violations.toString());
	}

	@Test
	public void toleratesUnknownFieldSoNewerCatalogsDoNotBreakOldClients() {
		// Bis 2026-10-05 lehnte der Client unbekannte Felder ab -- ein neues
		// Feld im Live-Katalog legte damit jede ausgelieferte Version lahm.
		// Unbekannte Felder werden jetzt ignoriert; dass $ref-Verweise weiter
		// aufgeloest werden, beweist rejectsPopularEntryMissingRequiredOrderViaResolvedRef.
		String withNewField = CatalogTestSupport.VALID_FEATURED_JSON.replace(
				"\"note\": \"Meistgenutzt\"", "\"note\": \"Meistgenutzt\", \"downloads\": 12345");
		List<String> violations = validate(withNewField);

		assertTrue(violations.isEmpty(), "ein neueres, unbekanntes Feld darf den Katalog nicht ablehnen: " + violations);
	}

	@Test
	public void rejectsPopularEntryMissingRequiredOrderViaResolvedRef() {
		String withoutOrder = CatalogTestSupport.VALID_FEATURED_JSON.replace(POPULAR_ENTRY_BLOCK,
				"    {\n      \"id\": \"umgekocht.klarglas\",\n      \"note\": \"Meistgenutzt\"\n    }");
		List<String> violations = validate(withoutOrder);

		assertFalse(violations.isEmpty(), "ueber $ref aufgeloestes Schema muss 'required' durchsetzen");
		assertTrue(violations.stream().anyMatch(v -> v.contains("order")), violations.toString());
	}

	@Test
	public void rejectsPopularEntryIdPatternViolationViaResolvedRef() {
		String withBadId = CatalogTestSupport.VALID_FEATURED_JSON.replace(POPULAR_ENTRY_BLOCK,
				"    {\n      \"id\": \"Kein Gueltiger Id\",\n      \"order\": 0,\n      \"note\": \"Meistgenutzt\"\n    }");
		List<String> violations = validate(withBadId);

		assertFalse(violations.isEmpty(), "ueber $ref aufgeloestes Schema muss 'pattern' durchsetzen");
		assertTrue(violations.stream().anyMatch(v -> v.contains("Muster")), violations.toString());
	}
}
