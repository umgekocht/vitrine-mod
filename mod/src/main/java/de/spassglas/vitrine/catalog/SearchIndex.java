package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine geparste, gegen {@code registry-search.schema.json} validierte
 * {@code search.json}-Antwort (ROADMAP K1/GATE72): eine flache, nicht
 * paginierte Liste <em>aller</em> Katalog-Pakete mit Suchfeldern, unabhaengig
 * von der Seitengroesse der paginierten Index-Seiten ({@code index.json},
 * {@code index-2.json}, ...). Grundlage fuer die Live-Suche
 * (docs/UI-SPEC.md "1. Kopfleiste": "Live-Treffer aus {@code search.json}").
 */
public record SearchIndex(int schemaVersion, String generatedAt, int totalCount, List<SearchIndexEntry> entries) {

	public SearchIndex {
		entries = List.copyOf(entries);
	}

	/**
	 * Baut das Modell aus einem bereits schema-validierten JSON-Objekt. Erwartet
	 * gueltige Struktur -- die Pruefung passiert vorher in
	 * {@link SearchIndexSchemaValidator}.
	 */
	static SearchIndex fromJson(JsonObject root) {
		List<SearchIndexEntry> entries = new ArrayList<>();
		JsonArray array = root.getAsJsonArray("entries");
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			entries.add(new SearchIndexEntry(
					entry.get("id").getAsString(),
					entry.get("name").getAsString(),
					entry.get("author").getAsString(),
					entry.get("category").getAsString(),
					entry.get("license").getAsString(),
					readStrings(entry, "tags"),
					readStrings(entry, "keywords")));
		}
		return new SearchIndex(
				root.get("schemaVersion").getAsInt(),
				root.get("generatedAt").getAsString(),
				root.get("totalCount").getAsInt(),
				entries);
	}

	private static List<String> readStrings(JsonObject object, String field) {
		if (!object.has(field) || object.get(field).isJsonNull()) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray(field)) {
			values.add(element.getAsString());
		}
		return values;
	}
}
