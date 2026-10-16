package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Client-freies Suchmodell (ROADMAP S6). Nimmt eine feste {@link TileData}-Liste
 * (Katalog) entgegen und liefert gefilterte, sortierte Treffer -- ohne eine
 * einzige Minecraft-Klasse, damit Rangfolge, Filter und Sortierung ohne
 * laufenden Client beweisbar sind (Muster wie {@code ui.library.LibraryModel}
 * und {@code ui.detail.PackageDetailModel}).
 *
 * <p><b>Freitext-Rangfolge</b> (bewusst einfach und testbar): pro Kachel wird
 * der beste Treffer ueber die drei Felder Name, Autor und Typ-Bezeichner
 * bestimmt (alles case-insensitiv, Teilstring). Praefix schlaegt Teilstring,
 * und innerhalb dessen kommt Name vor Autor vor Typ:</p>
 *
 * <pre>
 *   0 Name  beginnt mit der Eingabe      3 Name  enthaelt die Eingabe
 *   1 Autor beginnt mit der Eingabe      4 Autor enthaelt die Eingabe
 *   2 Typ   beginnt mit der Eingabe      5 Typ   enthaelt die Eingabe
 * </pre>
 *
 * <p>Ergebnisse werden zuerst nach der gewuenschten {@link SearchSort} stabil
 * sortiert und danach -- bei gesetztem Freitext -- stabil nach dem Rang, sodass
 * bessere Treffer oben stehen, gleiche Raenge aber die Sortierordnung behalten.</p>
 */
public final class SearchModel {

	/** Standard-Obergrenze fuer die Live-Treffer im Vorschlagsfeld (docs/UI-SPEC.md „1. Kopfleiste"). */
	public static final int LIVE_MATCH_LIMIT = 6;

	private static final int NO_MATCH = -1;

	private final List<TileData> catalog;

	public SearchModel(List<TileData> catalog) {
		this.catalog = List.copyOf(Objects.requireNonNull(catalog, "catalog"));
	}

	/** Anzahl der Katalogeintraege, ueber die gesucht wird. */
	public int catalogSize() {
		return catalog.size();
	}

	/**
	 * Wendet Kategorie-Filter, „ohne Neustart"-Filter und -- falls gesetzt --
	 * den Freitext an und sortiert das Ergebnis. Bei Freitext stehen bessere
	 * Raenge oben, bei gleichem Rang gilt die {@link SearchQuery#sort()};
	 * ohne Freitext gilt allein die Sortierung. Immer stabil.
	 */
	public List<TileData> search(SearchQuery query) {
		Objects.requireNonNull(query, "query");
		String needle = query.text().trim().toLowerCase(Locale.ROOT);
		boolean hasText = !needle.isEmpty();

		List<Ranked> matches = new ArrayList<>();
		for (TileData tile : catalog) {
			if (!query.categories().isEmpty() && !query.categories().contains(tile.type())) {
				continue;
			}
			if (query.hideRestartRequired() && tile.status() == TileStatus.RESTART_REQUIRED) {
				continue;
			}
			int rank = hasText ? rankOf(tile, needle) : 0;
			if (rank == NO_MATCH) {
				continue;
			}
			matches.add(new Ranked(tile, rank));
		}

		// Stabil: erst nach dem Sortierfeld, dann (bei Freitext) nach dem Rang.
		matches.sort((a, b) -> query.sort().comparator().compare(a.tile(), b.tile()));
		if (hasText) {
			matches.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
		}

		List<TileData> result = new ArrayList<>(matches.size());
		for (Ranked ranked : matches) {
			result.add(ranked.tile());
		}
		return List.copyOf(result);
	}

	/**
	 * Die besten {@code limit} Freitext-Treffer fuer das Vorschlagsfeld, nach
	 * Rang und dann Name aufsteigend geordnet. Leere/blanke Eingabe liefert
	 * eine leere Liste.
	 */
	public List<TileData> liveMatches(String text, int limit) {
		if (text == null || text.isBlank() || limit <= 0) {
			return List.of();
		}
		List<TileData> ranked = search(new SearchQuery(text, java.util.Set.of(), false, SearchSort.NAME_ASC));
		return ranked.size() <= limit ? ranked : List.copyOf(ranked.subList(0, limit));
	}

	private static int rankOf(TileData tile, String needle) {
		String name = tile.name().toLowerCase(Locale.ROOT);
		String author = tile.author().toLowerCase(Locale.ROOT);
		String type = tile.type().id().toLowerCase(Locale.ROOT);

		if (name.startsWith(needle)) {
			return 0;
		}
		if (author.startsWith(needle)) {
			return 1;
		}
		if (type.startsWith(needle)) {
			return 2;
		}
		if (name.contains(needle)) {
			return 3;
		}
		if (author.contains(needle)) {
			return 4;
		}
		if (type.contains(needle)) {
			return 5;
		}
		return NO_MATCH;
	}

	private record Ranked(TileData tile, int rank) {
	}
}
