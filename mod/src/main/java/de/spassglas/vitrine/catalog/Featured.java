package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine geparste, gegen {@code registry-featured.schema.json} validierte
 * {@code featured.json}-Antwort (ROADMAP K1/X2). Felder eins zu eins aus dem
 * Schema: {@code schemaVersion}, {@code generatedAt}, {@code items[]}
 * (Startseiten-Reihe "Featured"), {@code popular[]} (Startseiten-Reihe
 * "Beliebt" -- ebenfalls redaktionell kuratiert, keine gemessene
 * Downloadzahl, siehe Schema-Beschreibung). Beide Listen teilen dieselbe
 * Eintragsform ({@link FeaturedItem}) und sind unabhaengig voneinander
 * gepflegt -- ein Paket kann in der einen Liste stehen, in der anderen
 * fehlen, in beiden stehen oder in keiner.
 *
 * <p>Keine der beiden Listen ist zwingend nach {@code order} sortiert -- das
 * Schema erlaubt jede Reihenfolge in der Datei; die Sortierung passiert erst
 * dort, wo die Eintraege in Kacheln uebersetzt werden
 * ({@code de.spassglas.vitrine.ui.catalog.CuratedHomeSections}).</p>
 */
public record Featured(int schemaVersion, String generatedAt, List<FeaturedItem> items, List<FeaturedItem> popular) {

	public Featured {
		items = List.copyOf(items);
		popular = List.copyOf(popular);
	}

	/**
	 * Baut das Modell aus einem bereits schema-validierten JSON-Objekt. Erwartet
	 * gueltige Struktur -- die Pruefung passiert vorher in
	 * {@link FeaturedSchemaValidator}.
	 */
	static Featured fromJson(JsonObject root) {
		return new Featured(
				root.get("schemaVersion").getAsInt(),
				root.get("generatedAt").getAsString(),
				readItems(root, "items"),
				readItems(root, "popular"));
	}

	private static List<FeaturedItem> readItems(JsonObject root, String field) {
		List<FeaturedItem> items = new ArrayList<>();
		JsonArray array = root.getAsJsonArray(field);
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			items.add(new FeaturedItem(
					entry.get("id").getAsString(),
					entry.get("order").getAsInt(),
					optionalString(entry, "note")));
		}
		return items;
	}

	private static String optionalString(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
	}
}
