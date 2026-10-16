package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
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
import java.util.regex.Pattern;

/**
 * Minimaler, in sich geschlossener JSON-Schema-Auswerter (Draft-2020-12-
 * Teilmenge) fuer {@code registry-index-page.schema.json} -- gleiche
 * Begruendung und gleiches Muster wie {@code ManifestSchemaValidator} (P1) und
 * {@code ThemeSchemaValidator} (T2): keine neue Abhaengigkeit ({@code gson} ist
 * ueber Minecraft schon da), Feldregeln werden generisch aus der echten
 * Schema-Datei gelesen statt nach Java abgeschrieben (Charter-Regel 8, "ein
 * Schema, zwei Seiten").
 *
 * <p>Die eingebettete, byte-identische Kopie liegt neben dieser Klasse
 * ({@code mod/src/main/resources/de/spassglas/vitrine/catalog/registry-index-page.schema.json});
 * {@code RegistryIndexPageSchemaResourceSyncTest} haelt sie synchron zur Quelle
 * in {@code registry/schema/}.</p>
 *
 * <p>Unterstuetzte Schluesselwoerter: {@code type} (auch als Liste, z. B.
 * {@code ["string","null"]}), {@code required}, {@code properties},
 * {@code additionalProperties} (nur {@code false}), {@code const}, {@code enum},
 * {@code pattern}, {@code minLength}, {@code maxLength}, {@code minimum},
 * {@code maximum}, {@code minItems}, {@code maxItems}, {@code items},
 * {@code $ref} (nur lokale {@code #/$defs/<name>}-Verweise, wie sie
 * {@code registry-featured.schema.json} fuer {@code $defs/curationItem}
 * benutzt -- eine Ebene tief, keine Ref-Ketten). Das deckt exakt ab, was
 * {@code registry-index-page.schema.json} und {@code registry-featured.schema.json}
 * benutzen; {@code format} (date-time) wird bewusst nicht erzwungen.</p>
 */
final class IndexPageSchemaValidator {

	/**
	 * Deckel fuer die gesammelten Schema-Verstoesse (GATE116). Ein Katalog mit
	 * z. B. 5000 schema-widrigen Eintraegen liess vorher eine 1,3-MB-
	 * Zeichenkette entstehen, die {@link CatalogClient} pro fehlgeschlagener
	 * Seite zusammenbaute und loggte. {@link BoundedViolationList} begrenzt
	 * das <em>Sammeln</em> selbst (nicht erst die Anzeige) -- die Rekursion
	 * laeuft weiter ueber alle Eintraege, aber ab dem 21. Verstoss wird nur
	 * noch ein einziger Platzhaltersatz aktualisiert statt neuer Text
	 * angehaengt.
	 */
	static final int MAX_VIOLATIONS = 20;

	static final String SCHEMA_RESOURCE_NAME = "registry-index-page.schema.json";

	private static volatile JsonObject cachedSchema;

	private IndexPageSchemaValidator() {
	}

	static JsonObject embeddedSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (IndexPageSchemaValidator.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = IndexPageSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME
						+ "' im Package " + IndexPageSchemaValidator.class.getPackageName()
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
		return validate(embeddedSchema(), instance);
	}

	static List<String> validate(JsonObject schema, JsonElement instance) {
		List<String> violations = new BoundedViolationList();
		validateValue("(Wurzel)", schema, schema, instance, violations);
		return violations;
	}

	/**
	 * {@code ArrayList}, die nach {@link #MAX_VIOLATIONS} Eintraegen keine
	 * weiteren Zeichenketten mehr anhaengt, sondern einen einzelnen
	 * Platzhalter ("... und N weitere") am Ende pflegt. So bleibt die Liste
	 * (und jede Meldung, die sie zusammenfuegt, z. B. {@code CatalogClient})
	 * auch bei Tausenden Verstoessen klein -- es wird nicht erst spaeter
	 * gekuerzt, sondern von Anfang an nicht unbegrenzt gesammelt.
	 */
	private static final class BoundedViolationList extends ArrayList<String> {

		private int overflow;

		@Override
		public boolean add(String violation) {
			if (size() < MAX_VIOLATIONS) {
				return super.add(violation);
			}
			overflow++;
			String marker = "... und " + overflow + " weitere";
			if (!isEmpty() && super.get(size() - 1).startsWith("... und ")) {
				super.set(size() - 1, marker);
			} else {
				super.add(marker);
			}
			return true;
		}
	}

	private static void validateValue(String path, JsonObject root, JsonObject schema, JsonElement value,
			List<String> out) {
		schema = resolveRef(root, schema);
		List<String> types = readTypes(schema);
		if (!types.isEmpty() && types.stream().noneMatch(type -> matchesType(type, value))) {
			out.add("Feld '" + path + "' hat falschen Typ: erwartet " + String.join("/", types)
					+ ", gefunden " + describeType(value));
			return;
		}

		if (schema.has("const") && !schema.get("const").equals(value)) {
			out.add("Feld '" + path + "' hat ungueltigen Wert: erwartet genau " + schema.get("const")
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
				for (JsonElement candidate : allowed) {
					names.add(candidate.getAsString());
				}
				out.add("Feld '" + path + "' hat ungueltigen Wert " + value + ", erlaubt sind: "
						+ String.join(", ", names));
			}
		}

		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			validateString(path, schema, value.getAsString(), out);
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			validateNumber(path, schema, value.getAsDouble(), out);
		}
		if (value.isJsonArray()) {
			validateArray(path, root, schema, value.getAsJsonArray(), out);
		}
		if (value.isJsonObject() && (schema.has("properties") || schema.has("required")
				|| schema.has("additionalProperties"))) {
			validateObject(path, root, schema, value.getAsJsonObject(), out);
		}

		applyCombinators(path, root, schema, value, out);
	}

	/**
	 * {@code allOf}, {@code if}/{@code then}/{@code else}, {@code not} (GATE6).
	 * {@code registry-package.schema.json} waehlt ueber {@code if}/{@code then}
	 * je nach {@code contentClass} das Schema der Versionseintraege
	 * ({@code javamodVersionItem} mit {@code jarUrl}/{@code sha256} fuer
	 * Klasse B, {@code resourceVersionItem} sonst). Ohne diese Auswertung
	 * wuerde ein Klasse-B-Eintrag ohne Pruefsumme still akzeptiert.
	 */
	private static void applyCombinators(String path, JsonObject root, JsonObject schema, JsonElement value,
			List<String> out) {
		if (schema.has("allOf")) {
			for (JsonElement part : schema.getAsJsonArray("allOf")) {
				validateValue(path, root, part.getAsJsonObject(), value, out);
			}
		}
		if (schema.has("if")) {
			List<String> scratch = new ArrayList<>();
			validateValue(path, root, schema.getAsJsonObject("if"), value, scratch);
			String branch = scratch.isEmpty() ? "then" : "else";
			if (schema.has(branch)) {
				validateValue(path, root, schema.getAsJsonObject(branch), value, out);
			}
		}
		if (schema.has("not")) {
			JsonObject forbidden = schema.getAsJsonObject("not");
			List<String> scratch = new ArrayList<>();
			validateValue(path, root, forbidden, value, scratch);
			if (scratch.isEmpty()) {
				out.add(forbidden.has("description")
						? forbidden.get("description").getAsString()
						: "Feld '" + path + "' erfuellt eine Bedingung, die das Schema ausdruecklich verbietet");
			}
		}
	}

	private static void validateString(String path, JsonObject schema, String actual, List<String> out) {
		if (schema.has("minLength") && actual.length() < schema.get("minLength").getAsInt()) {
			int min = schema.get("minLength").getAsInt();
			out.add(min <= 1 && actual.isEmpty()
					? "Feld '" + path + "' darf nicht leer sein"
					: "Feld '" + path + "' ist zu kurz: mindestens " + min + " Zeichen, hat " + actual.length());
		}
		if (schema.has("maxLength") && actual.length() > schema.get("maxLength").getAsInt()) {
			out.add("Feld '" + path + "' ist zu lang: hoechstens " + schema.get("maxLength").getAsInt()
					+ " Zeichen, hat " + actual.length());
		}
		if (schema.has("pattern")) {
			String pattern = schema.get("pattern").getAsString();
			if (!Pattern.compile(pattern).matcher(actual).matches()) {
				out.add("Feld '" + path + "' entspricht nicht dem erwarteten Muster (" + pattern + "): '"
						+ actual + "'");
			}
		}
	}

	private static void validateNumber(String path, JsonObject schema, double actual, List<String> out) {
		if (schema.has("minimum") && actual < schema.get("minimum").getAsDouble()) {
			out.add("Feld '" + path + "' ist zu klein: Minimum " + trim(schema.get("minimum").getAsDouble())
					+ ", ist " + trim(actual));
		}
		if (schema.has("maximum") && actual > schema.get("maximum").getAsDouble()) {
			out.add("Feld '" + path + "' ist zu grosz: Maximum " + trim(schema.get("maximum").getAsDouble())
					+ ", ist " + trim(actual));
		}
	}

	private static void validateArray(String path, JsonObject root, JsonObject schema, JsonArray actual,
			List<String> out) {
		if (schema.has("minItems") && actual.size() < schema.get("minItems").getAsInt()) {
			out.add("Feld '" + path + "' hat zu wenige Eintraege: mindestens " + schema.get("minItems").getAsInt()
					+ ", hat " + actual.size());
		}
		if (schema.has("maxItems") && actual.size() > schema.get("maxItems").getAsInt()) {
			out.add("Feld '" + path + "' hat zu viele Eintraege: hoechstens " + schema.get("maxItems").getAsInt()
					+ ", hat " + actual.size());
		}
		if (schema.has("items")) {
			JsonObject itemSchema = schema.getAsJsonObject("items");
			for (int i = 0; i < actual.size(); i++) {
				validateValue(path + "[" + i + "]", root, itemSchema, actual.get(i), out);
			}
		}
	}

	private static void validateObject(String path, JsonObject root, JsonObject schema, JsonObject instance,
			List<String> out) {
		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();

		if (schema.has("required")) {
			List<String> missing = new ArrayList<>();
			for (JsonElement required : schema.getAsJsonArray("required")) {
				String field = required.getAsString();
				// JSON-Schema 'required' prueft nur Schluessel-Praesenz. Ein
				// explizites null erfuellt die Pflicht -- 'nextPage'/'previousPage'
				// sind laut Schema 'type: ["string","null"]'.
				if (!instance.has(field)) {
					missing.add(field);
				}
			}
			if (missing.size() == 1) {
				out.add("Pflichtfeld '" + missing.get(0) + "'" + inSuffix(path) + " fehlt");
			} else if (missing.size() > 1) {
				out.add("Pflichtfelder" + inSuffix(path) + " fehlen: " + String.join(", ", quoteAll(missing)));
			}
		}

		// additionalProperties:false wird im Client bewusst NICHT durchgesetzt:
		// unbekannte Felder werden ignoriert. Am 2026-10-05 kam ein neues Feld
		// ('license') in den Live-Katalog, und jede bereits ausgelieferte
		// Vitrine-Version lehnte daraufhin den ganzen Katalog ab ("The catalog
		// could not be read"). Die strenge Pruefung gehoert in die CI des
		// Katalog-Repos; ein Spieler-Client muss neuere Kataloge vertragen.
		// Bekannte Felder werden weiterhin voll geprueft.

		for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
			String field = property.getKey();
			if (instance.has(field) && !instance.get(field).isJsonNull()) {
				validateValue(childPath(path, field), root, property.getValue().getAsJsonObject(),
						instance.get(field), out);
			}
		}
	}

	/**
	 * Loest {@code {"$ref": "#/$defs/<name>"}} gegen {@code root} auf --
	 * genuegt fuer {@code registry-featured.schema.json} ({@code $defs/curationItem},
	 * eine Ebene tief, keine Ref-Ketten). Ohne {@code $ref} kommt {@code schema}
	 * unveraendert zurueck.
	 */
	private static JsonObject resolveRef(JsonObject root, JsonObject schema) {
		if (!schema.has("$ref")) {
			return schema;
		}
		String ref = schema.get("$ref").getAsString();
		if (!ref.startsWith("#/")) {
			throw new IllegalStateException("Nicht unterstuetzter '$ref': '" + ref
					+ "' -- nur lokale '#/...'-Verweise sind implementiert.");
		}
		JsonObject current = root;
		for (String segment : ref.substring(2).split("/")) {
			if (current == null || !current.has(segment)) {
				throw new IllegalStateException("'$ref' zeigt ins Leere: '" + ref + "' (Segment '" + segment
						+ "' nicht gefunden).");
			}
			current = current.getAsJsonObject(segment);
		}
		return current;
	}

	private static List<String> readTypes(JsonObject schema) {
		if (!schema.has("type")) {
			return List.of();
		}
		JsonElement type = schema.get("type");
		if (type.isJsonArray()) {
			List<String> types = new ArrayList<>();
			for (JsonElement element : type.getAsJsonArray()) {
				types.add(element.getAsString());
			}
			return types;
		}
		return List.of(type.getAsString());
	}

	private static boolean matchesType(String type, JsonElement value) {
		return switch (type) {
			case "object" -> value.isJsonObject();
			case "array" -> value.isJsonArray();
			case "null" -> value.isJsonNull();
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

	private static String trim(double value) {
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
