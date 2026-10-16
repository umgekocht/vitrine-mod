package de.spassglas.vitrine.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimaler, in sich geschlossener JSON-Schema-Auswerter (Draft-2020-12-
 * Teilmenge) fuer {@code registry/schema/hud.schema.json} -- keine neue
 * Abhaengigkeit, weil {@code com.google.gson} bereits transitiv ueber
 * {@code com.mojang:minecraft} vorhanden ist (siehe {@code THIRD-PARTY.md},
 * Zeile "Gson").
 *
 * <p><b>Warum eine eigene Klasse neben
 * {@code de.spassglas.vitrine.manifest.ManifestSchemaValidator} (P1) und nicht
 * eine gemeinsame:</b> {@code ManifestSchemaValidator} ist bewusst auf genau
 * die Schluesselwoerter beschraenkt, die {@code vitrine.schema.json} benutzt
 * ({@code type}/{@code required}/{@code properties}/{@code additionalProperties}/
 * {@code pattern}/{@code enum}/{@code const}/{@code min|maxLength}/
 * {@code min|maxItems}/{@code uniqueItems}/{@code items}), package-private, und
 * der genaue Wortlaut seiner Fehlermeldungen ist durch die P1-Tests
 * festgenagelt. {@code hud.schema.json} braucht zusaetzlich {@code $ref} auf
 * {@code #/$defs/*}, {@code oneOf} (mit Discriminator ueber ein {@code const}-
 * Feld) sowie {@code minimum}/{@code maximum} fuer Zahlen -- Schluesselwoerter,
 * die {@code vitrine.schema.json} nirgends verwendet. Diese Klasse
 * interpretiert genau diese erweiterte Teilmenge generisch aus der echten
 * Schema-Datei (kein Abschreiben der Feldregeln nach Java -- Charter-Regel 8
 * "Ein Schema, zwei Seiten"; {@link HudSchemaResourceSyncTest} haelt die
 * eingebettete Kopie byte-identisch zur Quelle in {@code registry/schema/}).
 * Eine spaetere Zusammenlegung beider Auswerter bleibt moeglich, ist aber
 * nicht Teil von T5.</p>
 *
 * <p>Unterstuetzte Schluesselwoerter: {@code type} (object/array/string/
 * integer/number), {@code required}, {@code properties},
 * {@code additionalProperties} (nur {@code false}), {@code $ref}
 * ({@code #/$defs/<name>}), {@code $defs}, {@code oneOf}, {@code const},
 * {@code enum}, {@code pattern}, {@code minLength}, {@code maxLength},
 * {@code minItems}, {@code maxItems}, {@code items}, {@code minimum},
 * {@code maximum}. Das deckt exakt ab, was {@code hud.schema.json} benutzt.</p>
 */
final class HudSchemaValidator {

	private final JsonObject rootSchema;

	private HudSchemaValidator(JsonObject rootSchema) {
		this.rootSchema = rootSchema;
	}

	/**
	 * Validiert {@code instance} gegen {@code schema} (dessen Wurzel auch die
	 * {@code $defs} traegt, auf die {@code $ref} zeigt) und liefert eine Liste
	 * unterscheidbarer Fehlermeldungen (leer bei einem gueltigen Dokument).
	 */
	static List<String> validate(JsonObject schema, JsonElement instance) {
		HudSchemaValidator validator = new HudSchemaValidator(schema);
		List<String> violations = new ArrayList<>();
		validator.validateValue("(Wurzel)", schema, instance, violations);
		return violations;
	}

	private void validateValue(String path, JsonObject schemaNode, JsonElement value, List<String> violations) {
		JsonObject schema = resolveRefs(schemaNode);

		if (schema.has("oneOf")) {
			validateOneOf(path, schema.getAsJsonArray("oneOf"), value, violations);
			return;
		}

		String type = schema.has("type") ? schema.get("type").getAsString() : null;
		if (type != null && !matchesType(type, value)) {
			violations.add("Feld '" + path + "' hat falschen Typ: erwartet " + type + ", gefunden " + describeType(value));
			return;
		}

		if (schema.has("const") && !schema.get("const").equals(value)) {
			violations.add("Feld '" + path + "' hat ungueltigen Wert: erwartet genau " + schema.get("const")
					+ ", gefunden " + value);
		}

		if (schema.has("enum")) {
			JsonArray allowed = schema.getAsJsonArray("enum");
			boolean matches = false;
			for (JsonElement candidate : allowed) {
				if (candidate.equals(value)) {
					matches = true;
					break;
				}
			}
			if (!matches) {
				List<String> names = new ArrayList<>();
				allowed.forEach(candidate -> names.add(candidate.getAsString()));
				violations.add("Feld '" + path + "' hat ungueltigen Wert '" + primitiveText(value)
						+ "', erlaubt sind: " + String.join(", ", names));
			}
		}

		if ("string".equals(type)) {
			validateString(path, schema, value.getAsString(), violations);
		} else if ("array".equals(type)) {
			validateArray(path, schema, value.getAsJsonArray(), violations);
		} else if ("integer".equals(type) || "number".equals(type)) {
			validateNumberBounds(path, schema, value.getAsDouble(), violations);
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
				if (!instance.has(field) || instance.get(field).isJsonNull()) {
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

	private void validateOneOf(String path, JsonArray branches, JsonElement value, List<String> violations) {
		int matchCount = 0;
		for (JsonElement branch : branches) {
			List<String> throwaway = new ArrayList<>();
			validateValue(path, branch.getAsJsonObject(), value, throwaway);
			if (throwaway.isEmpty()) {
				matchCount++;
			}
		}
		if (matchCount == 1) {
			return;
		}
		if (matchCount > 1) {
			violations.add("Feld '" + path + "' passt auf mehrere sich gegenseitig ausschliessende Varianten");
			return;
		}

		// Kein Treffer: die per const-Discriminator "gemeinte" Variante finden
		// und deren echte Verstoesse melden -- statt eines pauschalen
		// "passt zu keiner Variante", das fuer jede Fehlerart gleich klaenge.
		JsonObject discriminatorBranch = findDiscriminatorBranch(branches, value);
		if (discriminatorBranch != null) {
			validateValue(path, discriminatorBranch, value, violations);
			return;
		}
		violations.add(describeOneOfMismatch(path, branches, value));
	}

	private JsonObject findDiscriminatorBranch(JsonArray branches, JsonElement value) {
		if (!value.isJsonObject()) {
			return null;
		}
		JsonObject object = value.getAsJsonObject();
		for (JsonElement branchElement : branches) {
			JsonObject branch = resolveRefs(branchElement.getAsJsonObject());
			if (!branch.has("properties")) {
				continue;
			}
			for (Map.Entry<String, JsonElement> property : branch.getAsJsonObject("properties").entrySet()) {
				JsonObject propSchema = resolveRefs(property.getValue().getAsJsonObject());
				if (propSchema.has("const") && object.has(property.getKey())
						&& propSchema.get("const").equals(object.get(property.getKey()))) {
					return branch;
				}
			}
		}
		return null;
	}

	private String describeOneOfMismatch(String path, JsonArray branches, JsonElement value) {
		Map<String, List<String>> constsByProp = new LinkedHashMap<>();
		for (JsonElement branchElement : branches) {
			JsonObject branch = resolveRefs(branchElement.getAsJsonObject());
			if (!branch.has("properties")) {
				continue;
			}
			for (Map.Entry<String, JsonElement> property : branch.getAsJsonObject("properties").entrySet()) {
				JsonObject propSchema = resolveRefs(property.getValue().getAsJsonObject());
				if (propSchema.has("const")) {
					constsByProp.computeIfAbsent(property.getKey(), key -> new ArrayList<>())
							.add(primitiveText(propSchema.get("const")));
				}
			}
		}
		if (value.isJsonObject()) {
			JsonObject object = value.getAsJsonObject();
			for (Map.Entry<String, List<String>> discriminator : constsByProp.entrySet()) {
				String prop = discriminator.getKey();
				if (object.has(prop)) {
					return "Feld '" + childPath(path, prop) + "' hat ungueltigen Wert '"
							+ primitiveText(object.get(prop)) + "', erlaubt sind: "
							+ String.join(", ", discriminator.getValue());
				}
			}
		}
		return "Feld '" + path + "' passt zu keiner der erlaubten Varianten";
	}

	private void validateString(String path, JsonObject schema, String actual, List<String> violations) {
		if (schema.has("minLength") && actual.length() < schema.get("minLength").getAsInt()) {
			int min = schema.get("minLength").getAsInt();
			violations.add(min <= 1 && actual.isEmpty()
					? "Feld '" + path + "' darf nicht leer sein"
					: "Feld '" + path + "' ist zu kurz: mindestens " + min + " Zeichen, hat " + actual.length());
		}
		if (schema.has("maxLength") && actual.length() > schema.get("maxLength").getAsInt()) {
			violations.add("Feld '" + path + "' ist zu lang: hoechstens " + schema.get("maxLength").getAsInt()
					+ " Zeichen, hat " + actual.length());
		}
		if (schema.has("pattern")) {
			String pattern = schema.get("pattern").getAsString();
			if (!Pattern.compile(pattern).matcher(actual).matches()) {
				violations.add("Feld '" + path + "' entspricht nicht dem erwarteten Muster (" + pattern + "): '"
						+ actual + "'");
			}
		}
	}

	private void validateArray(String path, JsonObject schema, JsonArray actual, List<String> violations) {
		if (schema.has("minItems") && actual.size() < schema.get("minItems").getAsInt()) {
			violations.add("Feld '" + path + "' hat zu wenige Eintraege: mindestens " + schema.get("minItems").getAsInt()
					+ ", hat " + actual.size());
		}
		if (schema.has("maxItems") && actual.size() > schema.get("maxItems").getAsInt()) {
			violations.add("Feld '" + path + "' hat zu viele Eintraege: hoechstens " + schema.get("maxItems").getAsInt()
					+ ", hat " + actual.size());
		}
		if (schema.has("items")) {
			JsonObject itemSchema = schema.getAsJsonObject("items");
			for (int i = 0; i < actual.size(); i++) {
				validateValue(path + "[" + i + "]", itemSchema, actual.get(i), violations);
			}
		}
	}

	private void validateNumberBounds(String path, JsonObject schema, double actual, List<String> violations) {
		if (schema.has("minimum") && actual < schema.get("minimum").getAsDouble()) {
			violations.add("Feld '" + path + "' ist zu klein: Minimum " + trimNumber(schema.get("minimum").getAsDouble())
					+ ", ist " + trimNumber(actual));
		}
		if (schema.has("maximum") && actual > schema.get("maximum").getAsDouble()) {
			violations.add("Feld '" + path + "' ist zu gross: Maximum " + trimNumber(schema.get("maximum").getAsDouble())
					+ ", ist " + trimNumber(actual));
		}
	}

	/** Loest ein reines {@code {"$ref": "#/$defs/<name>"}} (ggf. mehrstufig) gegen die Schema-Wurzel auf. */
	private JsonObject resolveRefs(JsonObject schema) {
		JsonObject current = schema;
		int guard = 0;
		while (current.has("$ref")) {
			if (guard++ > 32) {
				throw new IllegalStateException("hud.schema.json: $ref-Kette laeuft im Kreis bei " + schema);
			}
			String ref = current.get("$ref").getAsString();
			if (!ref.startsWith("#/$defs/")) {
				throw new IllegalStateException("hud.schema.json: nicht unterstuetzter $ref '" + ref
						+ "' -- dieser Auswerter kann nur '#/$defs/<name>' aufloesen");
			}
			String name = ref.substring("#/$defs/".length());
			JsonObject defs = rootSchema.getAsJsonObject("$defs");
			if (defs == null || !defs.has(name)) {
				throw new IllegalStateException("hud.schema.json: $ref '" + ref + "' zeigt auf kein bekanntes $defs");
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

	private static String primitiveText(JsonElement value) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			return value.getAsString();
		}
		return value.toString();
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
