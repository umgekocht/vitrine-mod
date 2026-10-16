package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die Freitext-Rangfolge, den Kategorie-Filter, den Filter „ohne
 * Neustart" und die Sortier-Stabilitaet des client-freien {@link SearchModel}
 * (ROADMAP S6) -- Muster wie {@code ui.library.LibraryModelTest}.
 */
public final class SearchModelTest {

	private static TileData tile(String id, String name, String author, TilePackageType type, TileStatus status) {
		// GATE16: "Neustart noetig" gibt es nur fuer Klasse-B-Angebote -- eine
		// Kachel mit diesem Status ist hier also immer ein Java-Mod.
		return status == TileStatus.RESTART_REQUIRED
				? TileData.javaModOffer(id, name, author, type, status)
				: TileData.withoutImage(id, name, author, type, status);
	}

	private static List<String> ids(List<TileData> tiles) {
		return tiles.stream().map(TileData::id).collect(Collectors.toList());
	}

	// --- Freitext-Rangfolge -------------------------------------------------

	@Test
	public void prefixBeatsSubstringAndNameBeatsAuthorBeatsType() {
		SearchModel model = new SearchModel(List.of(
				tile("substr", "My Aura Ring", "zed", TilePackageType.TEXTUREN, TileStatus.NONE),
				tile("author", "Blue Sky", "aurelia", TilePackageType.HUD, TileStatus.NONE),
				tile("name", "Aurora", "mira", TilePackageType.THEME, TileStatus.NONE)));

		List<TileData> hits = model.search(new SearchQuery("aur", Set.of(), false, SearchSort.NAME_ASC));

		assertEquals(ids(hits), List.of("name", "author", "substr"),
				"Name-Praefix (0) vor Autor-Praefix (1) vor Name-Teilstring (3)");
	}

	@Test
	public void nonMatchingTilesAreDropped() {
		SearchModel model = new SearchModel(List.of(
				tile("a", "Aurora", "mira", TilePackageType.THEME, TileStatus.NONE),
				tile("b", "Nothing", "nobody", TilePackageType.HUD, TileStatus.NONE)));

		List<TileData> hits = model.search(new SearchQuery("aur", Set.of(), false, SearchSort.NAME_ASC));

		assertEquals(ids(hits), List.of("a"));
	}

	@Test
	public void freeTextIsCaseInsensitiveAndTrimmed() {
		SearchModel model = new SearchModel(List.of(
				tile("a", "Aurora", "mira", TilePackageType.THEME, TileStatus.NONE)));

		assertEquals(ids(model.search(new SearchQuery("  AUR  ", Set.of(), false, SearchSort.NAME_ASC))),
				List.of("a"));
	}

	// --- Sortierung: stabil innerhalb gleichen Rangs ----------------------

	@Test
	public void equalRankKeepsRequestedSortOrder() {
		SearchModel model = new SearchModel(List.of(
				tile("zoom", "Alpha Zoom", "wolf", TilePackageType.THEME, TileStatus.NONE),
				tile("area", "Alpha Area", "bee", TilePackageType.THEME, TileStatus.NONE)));

		// Beide starten mit „alpha" -> gleicher Rang 0. Sortierung entscheidet.
		assertEquals(ids(model.search(new SearchQuery("alpha", Set.of(), false, SearchSort.NAME_ASC))),
				List.of("area", "zoom"), "Name A-Z");
		assertEquals(ids(model.search(new SearchQuery("alpha", Set.of(), false, SearchSort.AUTHOR_ASC))),
				List.of("area", "zoom"), "Autor A-Z: bee vor wolf");
	}

	@Test
	public void withoutFreeTextTheWholeCatalogIsReturnedInSortOrder() {
		SearchModel model = new SearchModel(List.of(
				tile("c", "Charlie", "x", TilePackageType.HUD, TileStatus.NONE),
				tile("a", "Alpha", "z", TilePackageType.THEME, TileStatus.NONE),
				tile("b", "Bravo", "y", TilePackageType.THEME, TileStatus.NONE)));

		assertEquals(ids(model.search(new SearchQuery("", Set.of(), false, SearchSort.NAME_ASC))),
				List.of("a", "b", "c"));
		assertEquals(ids(model.search(new SearchQuery("", Set.of(), false, SearchSort.AUTHOR_ASC))),
				List.of("c", "b", "a"));
		assertEquals(ids(model.search(new SearchQuery("", Set.of(), false, SearchSort.TYPE))),
				List.of("a", "b", "c"), "TYPE gruppiert nach TilePackageType.ordinal (THEME vor HUD)");
	}

	// --- Kategorie-Filter (Nav-Rail) -------------------------------------

	@Test
	public void categoryFilterKeepsOnlySelectedTypes() {
		SearchModel model = new SearchModel(List.of(
				tile("t1", "Stone", "a", TilePackageType.TEXTUREN, TileStatus.NONE),
				tile("th1", "Klarglas", "b", TilePackageType.THEME, TileStatus.NONE),
				tile("th2", "Pastel", "c", TilePackageType.THEME, TileStatus.NONE),
				tile("h1", "Aurora HUD", "d", TilePackageType.HUD, TileStatus.NONE)));

		assertEquals(ids(model.search(new SearchQuery("", Set.of(TilePackageType.THEME), false, SearchSort.NAME_ASC))),
				List.of("th1", "th2"));
		assertEquals(model.search(new SearchQuery("", Set.of(), false, SearchSort.NAME_ASC)).size(), 4,
				"leere Kategorienmenge = keine Einschraenkung");
	}

	// --- Filter „ohne Neustart" ----------------------------------------

	@Test
	public void hideRestartRequiredExcludesRestartTilesButDefaultShowsEverything() {
		SearchModel model = new SearchModel(List.of(
				tile("hot", "Live Pack", "a", TilePackageType.TEXTUREN, TileStatus.NONE),
				tile("jar", "Java Mod", "b", TilePackageType.HUD, TileStatus.RESTART_REQUIRED)));

		assertEquals(ids(model.search(new SearchQuery("", Set.of(), false, SearchSort.NAME_ASC))),
				List.of("jar", "hot"), "Default false -> alles sichtbar (ehrlich beschriftet)");
		assertEquals(ids(model.search(new SearchQuery("", Set.of(), true, SearchSort.NAME_ASC))),
				List.of("hot"), "true blendet TileStatus.RESTART_REQUIRED aus");
	}

	// --- Live-Treffer fuers Vorschlagsfeld ------------------------------

	@Test
	public void liveMatchesRespectLimitAndIgnoreBlankInput() {
		SearchModel model = new SearchModel(List.of(
				tile("a", "Aqua", "x", TilePackageType.THEME, TileStatus.NONE),
				tile("b", "Aquamarine", "y", TilePackageType.THEME, TileStatus.NONE),
				tile("c", "Aquifer", "z", TilePackageType.THEME, TileStatus.NONE)));

		assertTrue(model.liveMatches("  ", 6).isEmpty(), "blanke Eingabe -> keine Live-Treffer");
		assertTrue(model.liveMatches(null, 6).isEmpty());
		assertTrue(model.liveMatches("aq", 0).isEmpty(), "limit 0 -> leer");
		assertEquals(ids(model.liveMatches("aq", 2)), List.of("a", "b"), "auf das Limit gekuerzt");
		assertEquals(model.liveMatches("aq", 6).size(), 3);
	}

	@Test
	public void catalogSizeReportsTheEntryCount() {
		assertEquals(new SearchModel(List.of(
				tile("a", "A", "a", TilePackageType.THEME, TileStatus.NONE))).catalogSize(), 1);
	}

	@Test
	public void resultListIsImmutable() {
		SearchModel model = new SearchModel(List.of(
				tile("a", "Aurora", "mira", TilePackageType.THEME, TileStatus.NONE)));
		List<TileData> hits = model.search(SearchQuery.all());
		try {
			hits.add(hits.get(0));
			assertFalse(true, "Ergebnisliste muss unveraenderlich sein");
		} catch (UnsupportedOperationException expected) {
			// gewuenscht
		}
	}
}
