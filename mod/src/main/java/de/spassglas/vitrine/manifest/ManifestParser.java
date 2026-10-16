package de.spassglas.vitrine.manifest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parst und validiert eine {@code vitrine.json}-Manifestzeichenkette gegen
 * das Schema {@code vitrine.schema.json} (P1). Reines Manifest-Parsing --
 * kein ZIP-Handling (P2), keine Sicherheitsschicht (P3), keine
 * Signaturpruefung (P4). Ein Aufrufer bekommt entweder ein geprueftes
 * {@link Manifest} oder eine {@link ManifestParseException} mit einer
 * konkreten, auf das jeweilige Manifest zugeschnittenen Fehlermeldung.
 */
public final class ManifestParser {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Manifest");

	/** Resource-Name der eingebetteten Schema-Kopie, im selben Package. */
	private static final String SCHEMA_RESOURCE_NAME = "vitrine.schema.json";

	/**
	 * 1:1-Java-Pendant der {@code DENY_PATTERNS} aus
	 * {@code registry/license-policy.mjs} (GATE98). Dort erzwingt
	 * {@code licenseWhitelistViolation} dieselben Muster beim Index-Bau --
	 * diese Liste muss bei einer Aenderung dort synchron nachgezogen werden.
	 */
	private static final List<Pattern> LICENSE_DENY_PATTERNS = List.of(
			Pattern.compile("all rights reserved", Pattern.CASE_INSENSITIVE),
			Pattern.compile("alle rechte vorbehalten", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^cc-by(-sa)?-nc", Pattern.CASE_INSENSITIVE),
			Pattern.compile("nur f[uü]r vitrine", Pattern.CASE_INSENSITIVE),
			Pattern.compile("only for vitrine", Pattern.CASE_INSENSITIVE),
			Pattern.compile("proprietary", Pattern.CASE_INSENSITIVE));

	private static volatile JsonObject cachedSchema;

	private ManifestParser() {
	}

	/**
	 * Parst {@code json} (Bytes einer {@code vitrine.json}, UTF-8) und
	 * validiert sie gegen das Schema.
	 *
	 * @throws ManifestParseException wenn {@code json} kein gueltiges JSON
	 *                                  ist oder das Schema verletzt -- die
	 *                                  Meldung nennt konkret, welche Regel(n)
	 *                                  verletzt wurden.
	 */
	public static Manifest parse(byte[] json) throws ManifestParseException {
		return parse(new String(json, StandardCharsets.UTF_8));
	}

	/**
	 * Parst {@code json} (Inhalt einer {@code vitrine.json}) und validiert
	 * sie gegen das Schema.
	 *
	 * @throws ManifestParseException wenn {@code json} kein gueltiges JSON
	 *                                  ist oder das Schema verletzt -- die
	 *                                  Meldung nennt konkret, welche Regel(n)
	 *                                  verletzt wurden.
	 */
	public static Manifest parse(String json) throws ManifestParseException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new ManifestParseException("vitrine.json ist kein gueltiges JSON: " + e.getMessage());
		}

		if (parsed == null || !parsed.isJsonObject()) {
			throw new ManifestParseException(
					"vitrine.json muss ein JSON-Objekt als Wurzelelement haben, ist aber "
							+ (parsed == null || parsed.isJsonNull() ? "null" : parsed.getClass().getSimpleName()));
		}

		JsonObject schema = loadSchema();
		List<String> violations = ManifestSchemaValidator.validate(schema, parsed);
		if (!violations.isEmpty()) {
			String message = "vitrine.json entspricht nicht dem Schema: " + String.join("; ", violations);
			LOGGER.debug("Manifest abgelehnt: {}", message);
			throw new ManifestParseException(message, violations);
		}

		JsonObject rootObject = parsed.getAsJsonObject();
		checkLicenseWhitelist(rootObject);
		return toManifest(rootObject);
	}

	/**
	 * Lehnt Manifeste ab, deren {@code license}-Wert einem der
	 * {@link #LICENSE_DENY_PATTERNS} entspricht (GATE98) -- dieselben Muster,
	 * die {@code registry/license-policy.mjs} beim Index-Bau erzwingt. Das
	 * Schema stellt bereits sicher, dass {@code license} als Pflichtfeld
	 * vorhanden und ein String ist.
	 */
	private static void checkLicenseWhitelist(JsonObject root) throws ManifestParseException {
		String license = root.get("license").getAsString();
		for (Pattern pattern : LICENSE_DENY_PATTERNS) {
			if (pattern.matcher(license.trim()).find()) {
				String message = "license '" + license
						+ "' steht ausdruecklich auf der Verbotsliste (siehe registry/LICENSE-WHITELIST.md, Abschnitt \"Nicht erlaubt\")";
				LOGGER.debug("Manifest abgelehnt: {}", message);
				throw new ManifestParseException(message, List.of(message));
			}
		}
	}

	private static Manifest toManifest(JsonObject root) {
		return new Manifest(
				root.get("schemaVersion").getAsInt(),
				root.get("id").getAsString(),
				root.get("name").getAsString(),
				root.get("version").getAsString(),
				root.get("category").getAsString(),
				root.get("author").getAsString(),
				optionalString(root, "description"),
				optionalStringArray(root, "capabilities"),
				optionalString(root, "icon"),
				optionalStringArray(root, "screenshots"),
				optionalString(root, "homepage"),
				optionalString(root, "license"));
	}

	private static String optionalString(JsonObject root, String field) {
		if (!root.has(field) || root.get(field).isJsonNull()) {
			return null;
		}
		return root.get(field).getAsString();
	}

	private static List<String> optionalStringArray(JsonObject root, String field) {
		if (!root.has(field) || root.get(field).isJsonNull()) {
			return List.of();
		}
		JsonArray array = root.getAsJsonArray(field);
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			values.add(element.getAsString());
		}
		return List.copyOf(values);
	}

	/**
	 * Laedt die eingebettete Schema-Kopie einmalig aus dem Klassenpfad
	 * (selbes Package wie diese Klasse, siehe
	 * {@code mod/src/main/resources/de/spassglas/vitrine/manifest/vitrine.schema.json}).
	 * Diese Kopie ist byte-identisch zu {@code registry/schema/vitrine.schema.json}
	 * -- ein Test haelt beide synchron.
	 */
	private static JsonObject loadSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (ManifestParser.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = ManifestParser.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException(
						"Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME + "' im Package "
								+ ManifestParser.class.getPackageName() + " fehlt im Klassenpfad -- Build defekt.");
			}
			String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return JsonParser.parseString(content).getAsJsonObject();
		} catch (IOException e) {
			LOGGER.error("Konnte eingebettete Schema-Resource '{}' nicht lesen: {}", SCHEMA_RESOURCE_NAME,
					e.toString(), e);
			throw new UncheckedIOException(
					"Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME + "' liess sich nicht lesen", e);
		}
	}
}
