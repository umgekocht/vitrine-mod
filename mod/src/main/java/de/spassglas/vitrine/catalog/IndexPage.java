package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine geparste, gegen {@code registry-index-page.schema.json} validierte
 * Index-Seite des Katalogs (ROADMAP K2). Felder eins zu eins aus dem Schema:
 * {@code schemaVersion}, {@code generatedAt}, {@code page}, {@code pageSize},
 * {@code totalPages}, {@code totalCount}, {@code nextPage}, {@code previousPage},
 * {@code packages[]}.
 *
 * <p>{@code nextPage} ist {@code null} auf der letzten Seite,
 * {@code previousPage} {@code null} auf Seite 1.</p>
 */
public record IndexPage(
		int schemaVersion,
		String generatedAt,
		int page,
		int pageSize,
		int totalPages,
		int totalCount,
		String nextPage,
		String previousPage,
		List<IndexPackage> packages) {

	public IndexPage {
		packages = List.copyOf(packages);
	}

	/**
	 * Baut das Modell aus einem bereits schema-validierten JSON-Objekt. Erwartet
	 * gueltige Struktur -- die Pruefung passiert vorher in
	 * {@link IndexPageSchemaValidator}.
	 */
	static IndexPage fromJson(JsonObject root) {
		List<IndexPackage> packages = new ArrayList<>();
		JsonArray array = root.getAsJsonArray("packages");
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			packages.add(new IndexPackage(
					entry.get("id").getAsString(),
					entry.get("name").getAsString(),
					entry.get("author").getAsString(),
					entry.get("category").getAsString(),
					entry.get("latestVersion").getAsString(),
					optionalString(entry, "icon"),
					optionalString(entry, "shortDescription"),
					entry.get("updatedAt").getAsString(),
					optionalBool(entry, "javaMod")));
		}
		return new IndexPage(
				root.get("schemaVersion").getAsInt(),
				root.get("generatedAt").getAsString(),
				root.get("page").getAsInt(),
				root.get("pageSize").getAsInt(),
				root.get("totalPages").getAsInt(),
				root.get("totalCount").getAsInt(),
				optionalString(root, "nextPage"),
				optionalString(root, "previousPage"),
				packages);
	}

	private static String optionalString(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
	}

	/**
	 * Optionales Bool-Feld (ROADMAP J1 {@code javaMod}). Fehlt es oder ist es
	 * {@code null}, gilt {@code false} -- das aktuelle Registry-Schema kennt das
	 * Feld noch nicht (Seam fuer eine spaetere {@code coder-tooling}-Zeile).
	 */
	private static boolean optionalBool(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() && object.get(field).getAsBoolean();
	}
}
