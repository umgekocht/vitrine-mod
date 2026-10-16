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
 * Minimaler JSON-Schema-Auswerter fuer {@code registry-package.schema.json}
 * (ROADMAP G3: "Registry-Paket-Eintrag holen") -- dasselbe Muster wie
 * {@link IndexPageSchemaValidator} (K2): keine neue Abhaengigkeit, die
 * eingebettete, byte-identische Kopie liegt neben dieser Klasse ({@code
 * mod/src/main/resources/de/spassglas/vitrine/catalog/registry-package.schema.json});
 * {@code RegistryPackageSchemaResourceSyncTest} haelt sie synchron zur Quelle
 * in {@code registry/schema/}. Die eigentliche Auswertung wird an
 * {@link IndexPageSchemaValidator#validate(JsonObject, JsonElement)}
 * delegiert -- ein Schema-Auswerter, zwei Schema-Dateien.
 */
final class RegistryPackageSchemaValidator {

	static final String SCHEMA_RESOURCE_NAME = "registry-package.schema.json";

	private static volatile JsonObject cachedSchema;

	private RegistryPackageSchemaValidator() {
	}

	static JsonObject embeddedSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (RegistryPackageSchemaValidator.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = RegistryPackageSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME
						+ "' im Package " + RegistryPackageSchemaValidator.class.getPackageName()
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
