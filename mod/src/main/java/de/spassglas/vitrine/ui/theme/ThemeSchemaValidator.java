package de.spassglas.vitrine.ui.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimaler, in sich geschlossener JSON-Schema-Auswerter (Draft-2020-12-
 * Teilmenge) fuer {@code registry/schema/theme.schema.json} -- gleiche
 * Begruendung und gleiches Muster wie
 * {@code de.spassglas.vitrine.manifest.ManifestSchemaValidator} (P1) und
 * {@code de.spassglas.vitrine.hud.HudSchemaValidator} (T5): keine neue
 * Abhaengigkeit ({@code com.google.gson} ist ueber Minecraft schon da,
 * {@code THIRD-PARTY.md} Zeile "Gson"), und die Feldregeln werden generisch
 * aus der echten Schema-Datei gelesen statt nach Java abgeschrieben
 * (Charter-Regel 8, "ein Schema, zwei Seiten"). Die eigene Klasse statt einer
 * gemeinsamen: jeder Schema-Konsument benutzt genau die Schluesselwort-
 * Teilmenge, die seine Datei braucht -- {@code theme.schema.json} braucht
 * {@code $ref} auf {@code #/$defs/*} und {@code minimum}/{@code maximum} fuer
 * Zahlen (wie HUD), aber kein {@code oneOf}/{@code enum}/{@code items}/
 * String-Laengen (anders als HUD/Manifest).
 *
 * <p>Die eingebettete, byte-identische Kopie liegt neben dieser Klasse
 * ({@code mod/src/main/resources/de/spassglas/vitrine/ui/theme/theme.schema.json});
 * {@code ThemeSchemaResourceSyncTest} haelt sie synchron zur Quelle in
 * {@code registry/schema/}.</p>
 *
 * <p>Unterstuetzte Schluesselwoerter: {@code type} (object/integer/number/
 * string/boolean), {@code required}, {@code properties},
 * {@code additionalProperties} (nur {@code false}), {@code $ref}
 * ({@code #/$defs/<name>}), {@code $defs}, {@code const}, {@code pattern},
 * {@code minimum}, {@code maximum}. Das deckt exakt ab, was
 * {@code theme.schema.json} benutzt.</p>
 */
final class ThemeSchemaValidator {

	/** Resource-Name der eingebetteten, byte-identischen Schema-Kopie, im selben Package. */
	static final String SCHEMA_RESOURCE_NAME = "theme.schema.json";

	private static volatile JsonObject cachedSchema;

	private final JsonObject rootSchema;
	private final Set<String> tolerateMissing;

	private ThemeSchemaValidator(JsonObject rootSchema, Set<String> tolerateMissing) {
		this.rootSchema = rootSchema;
		this.tolerateMissing = tolerateMissing;
	}

	/**
	 * Laedt die eingebettete Schema-Kopie einmalig aus dem Klassenpfad.
	 * Byte-identisch zu {@code registry/schema/theme.schema.json}.
	 */
	static JsonObject embeddedSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (ThemeSchemaValidator.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = ThemeSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME
						+ "' im Package " + ThemeSchemaValidator.class.getPackageName() + " fehlt -- Build defekt.");
			}
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new UncheckedIOException(
					"Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME + "' liess sich nicht lesen", e);
		}
	}

	/**
	 * Strikte Validierung von {@code instance} gegen {@code schema} (dessen
	 * Wurzel auch die {@code $defs} traegt) -- jeder {@code required}-Eintrag
	 * ist Pflicht. Spiegelt den Node-Validator in
	 * {@code registry/test/theme-schema.test.mjs}.
	 */
	static List<String> validate(JsonObject schema, JsonElement instance) {
		return validate(schema, instance, Set.of());
	}

	/**
	 * Wie {@link #validate(JsonObject, JsonElement)}, aber die in
	 * {@code tolerateMissing} genannten {@code required}-Feldnamen zaehlen
	 * nicht als fehlend, wenn sie im Dokument fehlen. Der {@link ThemeManager}
	 * nutzt das fuer die T2-Regel "Fallback bei fehlendem Token": ein einzelner
	 * fehlender Variant-Token ist kein kaputtes Theme, sein Wert kommt dann aus
	 * dem Standard-Theme. Falscher Typ, unbekanntes Feld oder eine ganz
	 * fehlende Variante ({@code light}/{@code dark}) bleiben Fehler.
	 */
	static List<String> validate(JsonObject schema, JsonElement instance, Set<String> tolerateMissing) {
		ThemeSchemaValidator validator = new ThemeSchemaValidator(schema, tolerateMissing);
		List<String> violations = new ArrayList<>();
		validator.validateValue("(Wurzel)", schema, instance, violations);
		return violations;
	}

	private void validateValue(String path, JsonObject schemaNode, JsonElement value, List<String> violations) {
		JsonObject schema = resolveRefs(schemaNode);

		String type = schema.has("type") ? schema.get("type").getAsString() : null;
		if (type != null && !matchesType(type, value)) {
			violations.add("Feld '" + path + "' hat falschen Typ: erwartet " + type
					+ ", gefunden " + describeType(value));
			return;
		}

		if (schema.has("const") && !schema.get("const").equals(value)) {
			violations.add("Feld '" + path + "' hat ungueltigen Wert: erwartet genau " + schema.get("const")
					+ ", gefunden " + value);
		}

		if ("string".equals(type) && schema.has("pattern")) {
			String pattern = schema.get("pattern").getAsString();
			if (!Pattern.compile(pattern).matcher(value.getAsString()).matches()) {
				violations.add("Feld '" + path + "' entspricht nicht dem erwarteten Muster (" + pattern + "): '"
						+ value.getAsString() + "'");
			}
		}

		if (("integer".equals(type) || "number".equals(type))) {
			double actual = value.getAsDouble();
			if (schema.has("minimum") && actual < schema.get("minimum").getAsDouble()) {
				violations.add("Feld '" + path + "' ist zu klein: Minimum "
						+ trimNumber(schema.get("minimum").getAsDouble()) + ", ist " + trimNumber(actual));
			}
			if (schema.has("maximum") && actual > schema.get("maximum").getAsDouble()) {
				violations.add("Feld '" + path + "' ist zu grosz: Maximum "
						+ trimNumber(schema.get("maximum").getAsDouble()) + ", ist " + trimNumber(actual));
			}
		}

		if (value.isJsonObject() && (schema.has("properties") || schema.has("required")
				|| schema.has("additionalProperties"))) {
			validateObject(path, schema, value.getAsJsonObject(), violations);
		}
	}

	private void validateObject(String path, JsonObject schema, JsonObject instance, List<String> violations) {
		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();

		if (schema.has("required")) {
			List<String> missing = new ArrayList<>();
			for (JsonElement requiredField : schema.getAsJsonArray("required")) {
				String field = requiredField.getAsString();
				if ((!instance.has(field) || instance.get(field).isJsonNull()) && !tolerateMissing.contains(field)) {
					missing.add(field);
				}
			}
			if (missing.size() == 1) {
				violations.add("Pflichtfeld '" + missing.get(0) + "'" + inSuffix(path) + " fehlt");
			} else if (missing.size() > 1) {
				violations.add("Pflichtfelder" + inSuffix(path) + " fehlen: " + String.join(", ", quoteAll(missing)));
			}
		}

		if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
			for (String key : instance.keySet()) {
				if (!properties.has(key)) {
					violations.add("Unbekanntes Feld '" + key + "'" + inSuffix(path)
							+ " ist nicht erlaubt (zusaetzliche Felder sind in diesem Schema nicht zugelassen)");
				}
			}
		}

		for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
			String field = property.getKey();
			if (instance.has(field) && !instance.get(field).isJsonNull()) {
				validateValue(childPath(path, field), property.getValue().getAsJsonObject(), instance.get(field),
						violations);
			}
		}
	}

	/** Loest ein reines {@code {"$ref": "#/$defs/<name>"}} (ggf. mehrstufig) gegen die Schema-Wurzel auf. */
	private JsonObject resolveRefs(JsonObject schema) {
		JsonObject current = schema;
		int guard = 0;
		while (current.has("$ref")) {
			if (guard++ > 32) {
				throw new IllegalStateException("theme.schema.json: $ref-Kette laeuft im Kreis bei " + schema);
			}
			String ref = current.get("$ref").getAsString();
			if (!ref.startsWith("#/$defs/")) {
				throw new IllegalStateException("theme.schema.json: nicht unterstuetzter $ref '" + ref
						+ "' -- dieser Auswerter kann nur '#/$defs/<name>' aufloesen");
			}
			String name = ref.substring("#/$defs/".length());
			JsonObject defs = rootSchema.getAsJsonObject("$defs");
			if (defs == null || !defs.has(name)) {
				throw new IllegalStateException("theme.schema.json: $ref '" + ref + "' zeigt auf kein bekanntes $defs");
			}
			current = defs.getAsJsonObject(name);
		}
		return current;
	}

	private static boolean matchesType(String type, JsonElement value) {
		return switch (type) {
			case "object" -> value.isJsonObject();
			case "array" -> value.isJsonArray();
			case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
			case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
			case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
			case "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && isIntegral(value);
			default -> true;
		};
	}

	private static boolean isIntegral(JsonElement value) {
		double asDouble = value.getAsDouble();
		return !Double.isInfinite(asDouble) && asDouble == Math.floor(asDouble);
	}

	private static String describeType(JsonElement value) {
		if (value.isJsonNull()) {
			return "null";
		}
		if (value.isJsonArray()) {
			return "array";
		}
		if (value.isJsonObject()) {
			return "object";
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isString()) {
			return "string";
		}
		if (primitive.isBoolean()) {
			return "boolean";
		}
		return isIntegral(value) ? "integer" : "number";
	}

	private static String trimNumber(double value) {
		return value == Math.floor(value) && !Double.isInfinite(value)
				? Long.toString((long) value)
				: Double.toString(value);
	}

	private static String childPath(String parent, String field) {
		return "(Wurzel)".equals(parent) ? field : parent + "." + field;
	}

	private static String inSuffix(String path) {
		return "(Wurzel)".equals(path) ? "" : " in '" + path + "'";
	}

	private static List<String> quoteAll(List<String> names) {
		List<String> quoted = new ArrayList<>(names.size());
		for (String name : names) {
			quoted.add("'" + name + "'");
		}
		return quoted;
	}
}
