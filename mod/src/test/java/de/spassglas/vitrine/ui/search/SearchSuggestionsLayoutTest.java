package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die Geometrie des Vorschlagsfelds (ROADMAP S6, docs/UI-SPEC.md
 * „1. Kopfleiste"): erst die letzten Suchen, dann die Live-Treffer, jede Zeile
 * gleich hoch, Trennlinie nur wenn beide Gruppen da sind, Klick-Treffer nur
 * innerhalb der Feldbreite.
 */
public final class SearchSuggestionsLayoutTest {

	private static final int TOP = 56;
	private static final int ROW_H = 20;

	private static TileData tile(String id, String name) {
		return TileData.withoutImage(id, name, "author", TilePackageType.THEME, TileStatus.NONE);
	}

	private static SearchSuggestions suggestions(List<String> recent, List<TileData> live) {
		return new SearchSuggestions(recent, live);
	}

	@Test
	public void emptySuggestionsProduceNoRowsNoDivider() {
		SearchSuggestionsLayout layout = new SearchSuggestionsLayout(SearchSuggestions.empty(), TOP, ROW_H);

		assertTrue(layout.isEmpty());
		assertEquals(layout.rowCount(), 0);
		assertEquals(layout.panelHeight(), 0);
		assertEquals(layout.panelBottom(), TOP);
		assertEquals(layout.dividerY(), -1);
		assertEquals(layout.rowAt(10, TOP + 1, 0, 100), Optional.empty());
	}

	@Test
	public void recentRowsComeFirstThenLiveRowsEachOneRowHigh() {
		SearchSuggestionsLayout layout = new SearchSuggestionsLayout(
				suggestions(List.of("stone", "glass"), List.of(tile("a", "Aurora"), tile("b", "Bright"))),
				TOP, ROW_H);

		List<SearchSuggestionsLayout.Row> rows = layout.rows();
		assertEquals(rows.size(), 4);
		assertEquals(rows.get(0).kind(), SearchSuggestionsLayout.RowKind.RECENT);
		assertEquals(rows.get(0).index(), 0);
		assertEquals(rows.get(0).top(), TOP);
		assertEquals(rows.get(1).kind(), SearchSuggestionsLayout.RowKind.RECENT);
		assertEquals(rows.get(1).index(), 1);
		assertEquals(rows.get(1).top(), TOP + ROW_H);
		assertEquals(rows.get(2).kind(), SearchSuggestionsLayout.RowKind.LIVE);
		assertEquals(rows.get(2).index(), 0, "Live-Index zaehlt bei 0 neu, nicht weiter");
		assertEquals(rows.get(2).top(), TOP + 2 * ROW_H);
		assertEquals(rows.get(3).kind(), SearchSuggestionsLayout.RowKind.LIVE);
		assertEquals(rows.get(3).index(), 1);

		assertEquals(layout.panelHeight(), 4 * ROW_H);
		assertEquals(layout.panelBottom(), TOP + 4 * ROW_H);
	}

	@Test
	public void dividerSitsBetweenRecentAndLiveOnlyWhenBothPresent() {
		assertEquals(new SearchSuggestionsLayout(
				suggestions(List.of("stone", "glass"), List.of(tile("a", "Aurora"))), TOP, ROW_H).dividerY(),
				TOP + 2 * ROW_H);

		assertEquals(new SearchSuggestionsLayout(
				suggestions(List.of("stone"), List.of()), TOP, ROW_H).dividerY(), -1,
				"nur letzte Suchen -> keine Trennlinie");
		assertEquals(new SearchSuggestionsLayout(
				suggestions(List.of(), List.of(tile("a", "Aurora"))), TOP, ROW_H).dividerY(), -1,
				"nur Live-Treffer -> keine Trennlinie");
	}

	@Test
	public void rowAtHitsTheRowUnderThePointerWithinTheFieldWidth() {
		SearchSuggestionsLayout layout = new SearchSuggestionsLayout(
				suggestions(List.of("stone"), List.of(tile("a", "Aurora"))), TOP, ROW_H);
		int left = 100;
		int right = 340;

		Optional<SearchSuggestionsLayout.Row> recentHit = layout.rowAt(200, TOP + 5, left, right);
		assertTrue(recentHit.isPresent());
		assertEquals(recentHit.get().kind(), SearchSuggestionsLayout.RowKind.RECENT);

		Optional<SearchSuggestionsLayout.Row> liveHit = layout.rowAt(200, TOP + ROW_H + 5, left, right);
		assertTrue(liveHit.isPresent());
		assertEquals(liveHit.get().kind(), SearchSuggestionsLayout.RowKind.LIVE);
		assertEquals(liveHit.get().index(), 0);

		assertFalse(layout.rowAt(left - 1, TOP + 5, left, right).isPresent(), "links neben dem Feld -> kein Treffer");
		assertFalse(layout.rowAt(right + 1, TOP + 5, left, right).isPresent(), "rechts neben dem Feld -> kein Treffer");
		assertFalse(layout.rowAt(200, TOP - 1, left, right).isPresent(), "ueber der ersten Zeile -> kein Treffer");
		assertFalse(layout.rowAt(200, TOP + 2 * ROW_H + 1, left, right).isPresent(),
				"unter der letzten Zeile -> kein Treffer");
	}

	@Test
	public void nonPositiveRowHeightIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new SearchSuggestionsLayout(SearchSuggestions.empty(), TOP, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new SearchSuggestionsLayout(SearchSuggestions.empty(), TOP, -4));
	}
}
