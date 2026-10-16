package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;

import java.util.List;

/**
 * Inhalt des Vorschlagsfelds unter der Suchleiste (ROADMAP S6,
 * docs/UI-SPEC.md „1. Kopfleiste"): erst die letzten Suchen, dann die
 * Live-Treffer aus dem Katalog, jeweils in Anzeigereihenfolge.
 */
public record SearchSuggestions(List<String> recentSearches, List<TileData> liveMatches) {

	public SearchSuggestions {
		recentSearches = List.copyOf(recentSearches);
		liveMatches = List.copyOf(liveMatches);
	}

	public static SearchSuggestions empty() {
		return new SearchSuggestions(List.of(), List.of());
	}

	public boolean isEmpty() {
		return recentSearches.isEmpty() && liveMatches.isEmpty();
	}

	/** Gesamtzahl auswaehlbarer Zeilen (letzte Suchen + Live-Treffer). */
	public int totalRows() {
		return recentSearches.size() + liveMatches.size();
	}
}
