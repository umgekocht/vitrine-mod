package de.spassglas.vitrine.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freie Geometrie des Vorschlagsfelds unter der Suchleiste (ROADMAP S6,
 * docs/UI-SPEC.md „1. Kopfleiste": „letzte Suchen, dann Live-Treffer ... jeweils
 * mit kleinem Bild und Typ"). Rechnet aus {@link SearchSuggestions} die
 * Zeilenrechtecke aus und beantwortet, welche Zeile unter einem Zeigerpunkt
 * liegt -- damit Zeichnen <em>und</em> Klick-Treffer in {@code StoreScreen} ohne
 * laufenden Client pruefbar sind (Muster wie {@code ui.focus.StoreFocusOrder}).
 *
 * <p>Die Reihenfolge ist fest: erst alle „letzten Suchen" ({@link RowKind#RECENT}),
 * dann alle Live-Treffer ({@link RowKind#LIVE}), jede Zeile {@code rowHeight} hoch.
 * {@link #dividerY()} nennt die Kante zwischen den beiden Gruppen fuer die
 * Trennlinie.</p>
 */
public final class SearchSuggestionsLayout {

	/** Art einer Vorschlagszeile. */
	public enum RowKind {
		/** Ein Eintrag aus dem Suchverlauf. */
		RECENT,
		/** Ein Live-Treffer aus dem Katalog. */
		LIVE
	}

	/** Ein Zeilenrechteck des Vorschlagsfelds. {@code index} zaehlt innerhalb der jeweiligen {@link RowKind}. */
	public record Row(RowKind kind, int index, int top, int height) {
		public int bottom() {
			return top + height;
		}

		public boolean containsY(double y) {
			return y >= top && y < top + height;
		}
	}

	private final int panelTop;
	private final int rowHeight;
	private final List<Row> rows;
	private final int recentCount;
	private final int liveCount;

	public SearchSuggestionsLayout(SearchSuggestions suggestions, int panelTop, int rowHeight) {
		Objects.requireNonNull(suggestions, "suggestions");
		if (rowHeight <= 0) {
			throw new IllegalArgumentException("rowHeight muss > 0 sein: " + rowHeight);
		}
		this.panelTop = panelTop;
		this.rowHeight = rowHeight;
		this.recentCount = suggestions.recentSearches().size();
		this.liveCount = suggestions.liveMatches().size();

		List<Row> built = new ArrayList<>(recentCount + liveCount);
		int y = panelTop;
		for (int i = 0; i < recentCount; i++) {
			built.add(new Row(RowKind.RECENT, i, y, rowHeight));
			y += rowHeight;
		}
		for (int i = 0; i < liveCount; i++) {
			built.add(new Row(RowKind.LIVE, i, y, rowHeight));
			y += rowHeight;
		}
		this.rows = List.copyOf(built);
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	public List<Row> rows() {
		return rows;
	}

	public int rowCount() {
		return rows.size();
	}

	public int panelTop() {
		return panelTop;
	}

	/** Untere Kante des Vorschlagsfelds. */
	public int panelBottom() {
		return panelTop + rows.size() * rowHeight;
	}

	/** Gesamthoehe des Vorschlagsfelds. */
	public int panelHeight() {
		return rows.size() * rowHeight;
	}

	/**
	 * Die y-Kante zwischen „letzten Suchen" und Live-Treffern fuer die
	 * Trennlinie, oder {@code -1}, wenn nur eine der beiden Gruppen Zeilen hat.
	 */
	public int dividerY() {
		if (recentCount == 0 || liveCount == 0) {
			return -1;
		}
		return panelTop + recentCount * rowHeight;
	}

	/**
	 * Die Zeile unter {@code (x, y)}, sofern {@code x} in {@code [panelLeft, panelRight]}
	 * liegt und {@code y} eine Zeile trifft -- sonst {@link Optional#empty()}.
	 */
	public Optional<Row> rowAt(double x, double y, int panelLeft, int panelRight) {
		if (x < panelLeft || x > panelRight) {
			return Optional.empty();
		}
		for (Row row : rows) {
			if (row.containsY(y)) {
				return Optional.of(row);
			}
		}
		return Optional.empty();
	}
}
