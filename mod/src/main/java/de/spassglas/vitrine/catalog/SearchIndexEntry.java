package de.spassglas.vitrine.catalog;

import java.util.List;

/**
 * Ein einzelner Eintrag aus {@code search.json} (ROADMAP K1/GATE72). Felder
 * eins zu eins aus {@code registry-search.schema.json}: {@code id},
 * {@code name}, {@code author}, {@code category}, {@code license},
 * {@code tags} (optional), {@code keywords}.
 *
 * <p>Bewusst schlanker als {@link IndexPackage}: kein {@code icon}, keine
 * {@code latestVersion}, kein {@code updatedAt} -- {@code search.json} ist
 * fuer die Live-Suche gedacht (Treffer-Rangfolge ueber Name/Autor/Schlagworte),
 * nicht fuer die volle Kachelansicht.</p>
 */
public record SearchIndexEntry(
		String id,
		String name,
		String author,
		String category,
		String license,
		List<String> tags,
		List<String> keywords) {

	public SearchIndexEntry {
		tags = List.copyOf(tags);
		keywords = List.copyOf(keywords);
	}
}
