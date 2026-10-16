package de.spassglas.vitrine.manifest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimaler, in sich geschlossener JSON-Schema-Auswerter (Draft-2020-12-
 * Teilmenge) -- keine neue Abhaengigkeit, weil {@code com.google.gson}
 * bereits transitiv ueber {@code com.mojang:minecraft} vorhanden ist (siehe
 * {@code THIRD-PARTY.md}, Zeile "Gson").
 *
 * <p>Bewusste Entscheidung gegen eine dedizierte Schema-Bibliothek wie
 * {@code networknt/json-schema-validator}: deren neuere Versionen ziehen
 * mehrere zusaetzliche transitive Abhaengigkeiten nach (Jackson-Databind,
 * ein YAML-Dataformat, eine RFC-3339-Datumsbibliothek) -- pro Abhaengigkeit
 * verlangt Charter-Regel 5 eine eigene Lizenzpruefung an der Quelle, obwohl
 * {@code vitrine.schema.json} nur eine kleine, feste Teilmenge von JSON-
 * Schema-Schluesselwoertern benutzt. Dieser Validator interpretiert genau
 * diese Teilmenge generisch aus der echten Schema-Datei (kein Abschreiben
 * der Feldregeln in Java -- Charter-Regel 8 "Ein Schema, zwei Seiten" bleibt
 * damit auch auf der Java-Seite eingehalten, {@link ManifestParserResourceSyncTest}
 * in den Tests haelt die eingebettete Kopie synchron zur Quelle in
 * {@code registry/schema/}).</p>
 *
 * <p>Unterstuetzte Schluesselwoerter: {@code type}, {@code required},
 * {@code properties} (auch verschachtelt, mit Punkt-Pfad in der Meldung,
 * z. B. {@code javaMod.jar.sha256}), {@code additionalProperties} (nur als
 * {@code false}), {@code pattern}, {@code enum}, {@code const},
 * {@code minLength}, {@code maxLength}, {@code minimum}, {@code maximum},
 * {@code minItems}, {@code maxItems}, {@code uniqueItems}, {@code items},
 * sowie die Kombinatoren {@code allOf}, {@code if}/{@code then}/{@code else}
 * und {@code not} (GATE6: die Klasse-A/B-Regel verknuepft mehrere Felder).
 * Das deckt genau die Schluesselwoerter ab, die {@code vitrine.schema.json}
 * tatsaechlich benutzt.</p>
 *
 * <p><b>Warum die Kombinatoren hier Pflicht sind:</b> ein Auswerter, der ein
 * unbekanntes Schluesselwort still ueberspringt, wuerde ein Klasse-A-Paket
 * mit eingeschmuggeltem {@code javaMod}-Block durchwinken -- also genau die
 * .jar im angeblich hot anwendbaren Paket, die das Schema verhindern soll.</p>
 */
final class ManifestSchemaValidator {

	/**
	 * Menschenlesbare Kurzbeschreibung des Musters je Feldname (statt der
	 * rohen Regex in der Fehlermeldung) -- Feldnamen von Array-Eintraegen
	 * werden ohne den Index-Suffix ({@code "capabilities[0]"} ->
	 * {@code "capabilities"}) nachgeschlagen.
	 */
	private static final Map<String, String> PATTERN_HINTS = new HashMap<>();

	static {
		PATTERN_HINTS.put("id", "Namespace-Bezeichner mit mindestens zwei durch Punkt getrennten "
				+ "Kleinbuchstaben/Ziffern/Bindestrich-Segmenten, z. B. 'autor.paketname'");
		PATTERN_HINTS.put("version", "Semantic Versioning 2.0.0, z. B. '1.0.0'");
		// capabilities hat kein 'pattern' mehr, sondern ein geschlossenes 'enum'
		// (ROADMAP G2 / Charter-Regel 8) -- die Whitelist-Meldung kommt aus dem
		// enum-Zweig von validateValue(), kein PATTERN_HINTS-Eintrag noetig.
		PATTERN_HINTS.put("icon", "relativer Pfad ohne '..' und ohne fuehrenden '/', endet auf '.png'");
		PATTERN_HINTS.put("screenshots", "relativer Pfad ohne '..' und ohne fuehrenden '/', "
				+ "endet auf '.png', '.jpg' oder '.jpeg'");
		PATTERN_HINTS.put("homepage", "muss mit 'https://' beginnen");
	}

	private ManifestSchemaValidator() {
	}

	/**
	 * Validiert {@code instance} gegen die Wurzel von {@code schema} und
	 * liefert eine Liste unterscheidbarer Fehlermeldungen (leer bei einem
	 * gueltigen Dokument).
	 */
	static List<String> validate(JsonObject schema, JsonElement instance) {
		List<String> violations = new ArrayList<>();
		if (!instance.isJsonObject()) {
			violations.add("Wurzelelement ist kein JSON-Objekt, sondern " + describeType(instance));
			return violations;
		}
		validateObject("", schema, instance.getAsJsonObject(), violations);
		applyCombinators("", schema, instance, violations);
		return violations;
	}

	/**
	 * Prueft ein Objekt gegen {@code properties}/{@code required}/
	 * {@code additionalProperties}. {@code objectPath} ist leer fuer die
	 * Wurzel -- dort bleiben die Meldungen wortgleich wie vor GATE6
	 * ({@code "Pflichtfeld 'author' fehlt"}); fuer verschachtelte Objekte
	 * traegt jeder Feldname den Punkt-Pfad davor ({@code 'javaMod.jar.sha256'}).
	 */
	private static void validateObject(String objectPath, JsonObject schema, JsonObject instance,
			List<String> violations) {
		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();

		if (schema.has("required")) {
			List<String> missing = new ArrayList<>();
			for (JsonElement requiredField : schema.getAsJsonArray("required")) {
				String fieldName = requiredField.getAsString();
				if (!instance.has(fieldName) || instance.get(fieldName).isJsonNull()) {
					missing.add(childPath(objectPath, fieldName));
				}
			}
			if (missing.size() == 1) {
				violations.add("Pflichtfeld '" + missing.get(0) + "' fehlt");
			} else if (missing.size() > 1) {
				violations.add("Pflichtfelder fehlen: " + String.join(", ", quoteAll(missing)));
			}
		}

		if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
			for (String key : instance.keySet()) {
				if (!properties.has(key)) {
					violations.add("Unbekanntes Feld '" + childPath(objectPath, key) + "' ist nicht erlaubt "
							+ "(zusaetzliche Felder sind in diesem Schema nicht zugelassen)");
				}
			}
		}

		for (Map.Entry<String, JsonElement> propertyEntry : properties.entrySet()) {
			String fieldName = propertyEntry.getKey();
			if (instance.has(fieldName) && !instance.get(fieldName).isJsonNull()) {
				validateValue(childPath(objectPath, fieldName), propertyEntry.getValue().getAsJsonObject(),
						instance.get(fieldName), violations);
			}
		}
	}

	/**
	 * {@code allOf}, {@code if}/{@code then}/{@code else} und {@code not} --
	 * werden auf denselben Wert und denselben Pfad angewendet wie das
	 * umgebende Schema.
	 *
	 * <p>{@code if} und {@code not} werden in eine Wegwerf-Liste ausgewertet:
	 * dort zaehlt nur, ob das Teilschema <em>passt</em>, nicht seine einzelnen
	 * Meldungen. Fuer {@code not} kommt die sichtbare Meldung aus der
	 * {@code description} des Teilschemas -- dieselbe Quelle, aus der auch
	 * {@code designer/schema-validate.js} sie nimmt (ein Schema, zwei Seiten).</p>
	 */
	private static void applyCombinators(String path, JsonObject schema, JsonElement value,
			List<String> violations) {
		if (schema.has("allOf")) {
			for (JsonElement part : schema.getAsJsonArray("allOf")) {
				validateAgainstSubschema(path, part.getAsJsonObject(), value, violations);
			}
		}

		if (schema.has("if")) {
			boolean conditionHolds = matches(path, schema.getAsJsonObject("if"), value);
			String branch = conditionHolds ? "then" : "else";
			if (schema.has(branch)) {
				validateAgainstSubschema(path, schema.getAsJsonObject(branch), value, violations);
			}
		}

		if (schema.has("not")) {
			JsonObject forbidden = schema.getAsJsonObject("not");
			if (matches(path, forbidden, value)) {
				violations.add(forbidden.has("description")
						? forbidden.get("description").getAsString()
						: (path.isEmpty() ? "Dokument" : "Feld '" + path + "'")
								+ " erfuellt eine Bedingung, die das Schema ausdruecklich verbietet");
			}
		}
	}

	/**
	 * Wendet ein Teilschema (aus {@code allOf}/{@code then}/{@code else}) auf
	 * denselben Wert an. Teilschemata tragen oft kein {@code type} -- deshalb
	 * entscheidet hier der Wert selbst, ob die Objekt-Regeln greifen.
	 */
	private static void validateAgainstSubschema(String path, JsonObject subschema, JsonElement value,
			List<String> violations) {
		if (subschema.has("type")) {
			validateValue(path, subschema, value, violations);
			return;
		}
		if (value.isJsonObject() && hasObjectKeywords(subschema)) {
			validateObject(path, subschema, value.getAsJsonObject(), violations);
		}
		applyCombinators(path, subschema, value, violations);
	}

	private static boolean matches(String path, JsonObject subschema, JsonElement value) {
		List<String> scratch = new ArrayList<>();
		validateAgainstSubschema(path, subschema, value, scratch);
		return scratch.isEmpty();
	}

	private static boolean hasObjectKeywords(JsonObject schema) {
		return schema.has("properties") || schema.has("required") || schema.has("additionalProperties");
	}

	private static String childPath(String parentPath, String fieldName) {
		return parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;
	}

	private static void validateValue(String fieldPath, JsonObject schema, JsonElement value, List<String> violations) {
		String type = schema.has("type") ? schema.get("type").getAsString() : null;
		if (type != null && !matchesType(type, value)) {
			violations.add("Feld '" + fieldPath + "' hat falschen Typ: erwartet " + type
					+ ", gefunden " + describeType(value));
			return;
		}

		if (schema.has("const")) {
			JsonElement expected = schema.get("const");
			if (!expected.equals(value)) {
				violations.add("Feld '" + fieldPath + "' hat ungueltigen Wert: erwartet genau "
						+ expected + ", gefunden " + value);
			}
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
				List<String> allowedNames = new ArrayList<>();
				for (JsonElement candidate : allowed) {
					allowedNames.add(candidate.getAsString());
				}
				violations.add("Feld '" + fieldPath + "' hat ungueltigen Wert '" + value.getAsString()
						+ "', erlaubt sind: " + String.join(", ", allowedNames));
			}
		}

		if ("string".equals(type)) {
			validateString(fieldPath, schema, value.getAsString(), violations);
		} else if ("array".equals(type)) {
			validateArray(fieldPath, schema, value.getAsJsonArray(), violations);
		} else if ("integer".equals(type) || "number".equals(type)) {
			validateNumber(fieldPath, schema, value.getAsDouble(), violations);
		} else if (value.isJsonObject() && hasObjectKeywords(schema)) {
			// "object" -- vor GATE6 stieg der Auswerter hier nicht ab, verschachtelte
			// Pflichtfelder (z. B. javaMod.jar.sha256) blieben ungeprueft.
			validateObject(fieldPath, schema, value.getAsJsonObject(), violations);
		}

		applyCombinators(fieldPath, schema, value, violations);
	}

	private static void validateNumber(String fieldPath, JsonObject schema, double actual, List<String> violations) {
		if (schema.has("minimum") && actual < schema.get("minimum").getAsDouble()) {
			violations.add("Feld '" + fieldPath + "' ist zu klein: mindestens "
					+ formatNumber(schema.get("minimum").getAsDouble()) + ", hat " + formatNumber(actual));
		}
		if (schema.has("maximum") && actual > schema.get("maximum").getAsDouble()) {
			violations.add("Feld '" + fieldPath + "' ist zu gross: hoechstens "
					+ formatNumber(schema.get("maximum").getAsDouble()) + ", hat " + formatNumber(actual));
		}
	}

	private static String formatNumber(double value) {
		return value == Math.rint(value) && !Double.isInfinite(value)
				? Long.toString((long) value)
				: Double.toString(value);
	}

	private static void validateString(String fieldPath, JsonObject schema, String actual, List<String> violations) {
		if (schema.has("minLength")) {
			int minLength = schema.get("minLength").getAsInt();
			if (actual.length() < minLength) {
				violations.add(minLength <= 1 && actual.isEmpty()
						? "Feld '" + fieldPath + "' darf nicht leer sein"
						: "Feld '" + fieldPath + "' ist zu kurz: mindestens " + minLength
								+ " Zeichen erforderlich, hat " + actual.length());
			}
		}
		if (schema.has("maxLength")) {
			int maxLength = schema.get("maxLength").getAsInt();
			if (actual.length() > maxLength) {
				violations.add("Feld '" + fieldPath + "' ist zu lang: hoechstens " + maxLength
						+ " Zeichen erlaubt, hat " + actual.length());
			}
		}
		if (schema.has("pattern")) {
			String pattern = schema.get("pattern").getAsString();
			if (!Pattern.compile(pattern).matcher(actual).matches()) {
				String hint = PATTERN_HINTS.get(baseFieldName(fieldPath));
				violations.add("Feld '" + fieldPath + "' entspricht nicht dem erwarteten Muster"
						+ (hint != null ? " (" + hint + ")" : " (" + pattern + ")") + ": '" + actual + "'");
			}
		}
	}

	private static void validateArray(String fieldPath, JsonObject schema, JsonArray actual, List<String> violations) {
		if (schema.has("minItems")) {
			int minItems = schema.get("minItems").getAsInt();
			if (actual.size() < minItems) {
				violations.add("Feld '" + fieldPath + "' hat zu wenige Eintraege: mindestens " + minItems
						+ " erforderlich, hat " + actual.size());
			}
		}
		if (schema.has("maxItems")) {
			int maxItems = schema.get("maxItems").getAsInt();
			if (actual.size() > maxItems) {
				violations.add("Feld '" + fieldPath + "' hat zu viele Eintraege: hoechstens " + maxItems
						+ " erlaubt, hat " + actual.size());
			}
		}
		if (schema.has("uniqueItems") && schema.get("uniqueItems").getAsBoolean()) {
			Set<JsonElement> seen = new HashSet<>();
			for (JsonElement item : actual) {
				if (!seen.add(item)) {
					violations.add("Feld '" + fieldPath + "' enthaelt den doppelten Eintrag '" + item + "'");
					break;
				}
			}
		}
		if (schema.has("items")) {
			JsonObject itemSchema = schema.getAsJsonObject("items");
			for (int i = 0; i < actual.size(); i++) {
				validateValue(fieldPath + "[" + i + "]", itemSchema, actual.get(i), violations);
			}
		}
	}

	private static boolean matchesType(String type, JsonElement value) {
		switch (type) {
			case "object":
				return value.isJsonObject();
			case "array":
				return value.isJsonArray();
			case "string":
				return isPrimitiveOfKind(value, JsonPrimitive::isString);
			case "integer":
				return isPrimitiveOfKind(value, primitive -> primitive.isNumber() && isIntegral(primitive));
			case "number":
				return isPrimitiveOfKind(value, JsonPrimitive::isNumber);
			case "boolean":
				return isPrimitiveOfKind(value, JsonPrimitive::isBoolean);
			default:
				return true;
		}
	}

	private static boolean isIntegral(JsonPrimitive primitive) {
		double asDouble = primitive.getAsDouble();
		return asDouble == Math.floor(asDouble) && !Double.isInfinite(asDouble);
	}

	private static boolean isPrimitiveOfKind(JsonElement value, java.util.function.Predicate<JsonPrimitive> check) {
		return value.isJsonPrimitive() && check.test(value.getAsJsonPrimitive());
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
		if (primitive.isNumber()) {
			return isIntegral(primitive) ? "integer" : "number";
		}
		return "unbekannt";
	}

	private static String baseFieldName(String fieldPath) {
		int bracket = fieldPath.indexOf('[');
		return bracket >= 0 ? fieldPath.substring(0, bracket) : fieldPath;
	}

	private static List<String> quoteAll(List<String> names) {
		List<String> quoted = new ArrayList<>();
		for (String name : names) {
			quoted.add("'" + name + "'");
		}
		return quoted;
	}
}
