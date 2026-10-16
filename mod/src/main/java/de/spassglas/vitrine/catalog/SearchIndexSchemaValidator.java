package de.spassglas.vitrine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Schema-Pruefung fuer {@code registry-search.schema.json} (ROADMAP K1/GATE72,
 * {@code search.json}) -- gleiche Begruendung wie {@link FeaturedSchemaValidator}
 * (keine neue Abhaengigkeit, Feldregeln aus der echten Schema-Datei statt nach
 * Java abgeschrieben, Charter-Regel 8). Nutzt denselben generischen
 * Draft-2020-12-Teilmengen-Auswerter aus {@link IndexPageSchemaValidator} --
 * {@code registry-search.schema.json} braucht keine zusaetzlichen
 * Schluesselwoerter.
 *
 * <p>Die eingebettete, byte-identische Kopie liegt neben dieser Klasse
 * ({@code mod/src/main/resources/de/spassglas/vitrine/catalog/registry-search.schema.json});
 * {@code RegistrySearchSchemaResourceSyncTest} haelt sie synchron zur Quelle
 * in {@code registry/schema/}.</p>
 */
final class SearchIndexSchemaValidator {

	static final String SCHEMA_RESOURCE_NAME = "registry-search.schema.json";

	private static volatile JsonObject cachedSchema;

	private SearchIndexSchemaValidator() {
	}

	static JsonObject embeddedSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (SearchIndexSchemaValidator.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = SearchIndexSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME
						+ "' im Package " + SearchIndexSchemaValidator.class.getPackageName()
						+ " fehlt im Klassenpfad -- Build defekt.");
			}
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(
					"Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME + "' liess sich nicht lesen", e);
		}
	}

	/** Validiert {@code instance} gegen die eingebettete Schema-Kopie. */
	static List<String> validate(JsonElement instance) {
		return IndexPageSchemaValidator.validate(embeddedSchema(), instance);
	}
}
