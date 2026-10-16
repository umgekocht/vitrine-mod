package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.nav.NavRailEntry;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist den client-freien Zustand der Store-Suche (ROADMAP S6): Live-Text
 * speist nur das Vorschlagsfeld, {@code submit} den Verlauf; Kategorie kommt
 * aus der Nav-Rail; Filter „ohne Neustart" und Sortier-Knopf wirken ohne
 * Neuaufbau auf die Ergebnisliste.
 */
public final class StoreSearchControllerTest {

	private static TileData tile(String id, String name, String author, TilePackageType type, TileStatus status) {
		// GATE16: "Neustart noetig" gibt es nur fuer Klasse-B-Angebote -- eine
		// Kachel mit diesem Status ist hier also immer ein Java-Mod.
		return status == TileStatus.RESTART_REQUIRED
				? TileData.javaModOffer(id, name, author, type, status)
				: TileData.withoutImage(id, name, author, type, status);
	}

	/**
	 * Diese Tests pruefen Suche, Kategorie und den "ohne Neustart"-Filter --
	 * nicht die J1-Schleuse. Die Java-Mod-Kachel ist seit GATE16 ein echtes
	 * Klasse-B-Angebot, also braucht es den Opt-in, damit sie ueberhaupt im
	 * Katalog steht (die Schleuse selbst prueft StoreSearchControllerJavaModOfferTest).
	 */
	private static final de.spassglas.vitrine.ui.classb.JavaModOfferGate JAVA_MODS_SHOWN =
			new de.spassglas.vitrine.ui.classb.JavaModOfferGate(() -> true);

	private static List<TileData> catalog() {
		return List.of(
				tile("th1", "Klarglas", "spassglas", TilePackageType.THEME, TileStatus.NONE),
				tile("th2", "Pastel UI", "lena", TilePackageType.THEME, TileStatus.NONE),
				tile("tx1", "Smooth Stone", "wolf", TilePackageType.TEXTUREN, TileStatus.NONE),
				tile("hd1", "Aurora HUD", "mira", TilePackageType.HUD, TileStatus.NONE),
				tile("jr1", "Java Mod", "dev", TilePackageType.HUD, TileStatus.RESTART_REQUIRED));
	}

	private static List<String> ids(List<TileData> tiles) {
		return tiles.stream().map(TileData::id).collect(Collectors.toList());
	}

	// --- Vorschlagsfeld: Live-Text vs. Verlauf ---------------------------

	@Test
	public void liveTextFeedsSuggestionsButNeverEntersHistory() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);

		c.setLiveText("aur");

		assertEquals(c.liveText(), "aur");
		assertTrue(c.recentSearches().isEmpty(), "Live-Tippen landet NICHT im Verlauf");
		assertEquals(ids(c.suggestions().liveMatches()), List.of("hd1"),
				"Live-Treffer zum aktuellen Live-Text");
		assertFalse(c.hasActiveSearch(), "Live-Text alleine startet keine Ergebnis-Suche");
	}

	@Test
	public void submitAddsToHistoryAndBecomesActiveSearch() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);

		c.submit("  stone  ");

		assertEquals(c.recentSearches(), List.of("stone"), "getrimmt in den Verlauf");
		assertEquals(c.submittedText(), "stone");
		assertTrue(c.hasActiveSearch());
		assertEquals(ids(c.results()), List.of("tx1"));
	}

	@Test
	public void suggestionsShowRecentSearchesNewestFirstThenLiveMatches() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);
		c.submit("klarglas");
		c.submit("pastel");
		c.setLiveText("aur");

		SearchSuggestions s = c.suggestions();
		assertEquals(s.recentSearches(), List.of("pastel", "klarglas"));
		assertEquals(ids(s.liveMatches()), List.of("hd1"));
	}

	@Test
	public void applyRecentReRunsAPreviousSearch() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);
		c.submit("stone");
		c.submit("hud");

		c.applyRecent("stone");

		assertEquals(c.submittedText(), "stone");
		assertEquals(c.recentSearches(), List.of("stone", "hud"), "erneut nach vorn, keine Dublette");
		assertEquals(ids(c.results()), List.of("tx1"));
	}

	@Test
	public void clearActiveSearchLeavesHistoryIntact() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);
		c.submit("stone");

		c.clearActiveSearch();

		assertFalse(c.hasActiveSearch());
		assertEquals(c.recentSearches(), List.of("stone"));
	}

	// --- Kategorie ueber die Nav-Rail ----------------------------------

	@Test
	public void categoryFromNavRailRestrictsResultsAndStartClearsIt() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);

		c.setCategories(NavRailCategories.typesFor(NavRailEntry.THEMES));
		assertEquals(ids(c.results()), List.of("th1", "th2"), "nur Theme-Kacheln");

		c.setCategories(NavRailCategories.typesFor(NavRailEntry.START));
		assertEquals(c.results().size(), catalog().size(), "Start = keine Einschraenkung");
	}

	// --- Filter „ohne Neustart" --------------------------------------

	@Test
	public void restartFreeFilterDefaultsOffAndTogglesWithoutRebuild() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);

		assertFalse(c.hideRestartRequired());
		assertTrue(ids(c.results()).contains("jr1"), "Default: Klasse-B-Angebot sichtbar");

		assertTrue(c.toggleHideRestartRequired());
		assertFalse(ids(c.results()).contains("jr1"), "jetzt ausgeblendet");

		assertFalse(c.toggleHideRestartRequired());
		assertTrue(ids(c.results()).contains("jr1"));
	}

	// --- Sortier-Knopf durchschalten ---------------------------------

	@Test
	public void cycleSortAdvancesThroughTheRingAndReordersResults() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);
		c.setCategories(NavRailCategories.typesFor(NavRailEntry.THEMES));

		assertEquals(c.sort(), SearchSort.NAME_ASC);
		assertEquals(ids(c.results()), List.of("th1", "th2"), "Name: Klarglas vor Pastel UI");

		assertEquals(c.cycleSort(), SearchSort.AUTHOR_ASC);
		assertEquals(ids(c.results()), List.of("th2", "th1"), "Autor: lena vor spassglas");

		assertEquals(c.cycleSort(), SearchSort.TYPE);
		assertEquals(c.cycleSort(), SearchSort.NAME_ASC, "Ring schaltet wieder auf Anfang");
	}

	@Test
	public void catalogSizeIsReportedForTheStatusLine() {
		assertEquals(new StoreSearchController(catalog(), JAVA_MODS_SHOWN).catalogSize(), catalog().size());
	}

	// --- ROADMAP GATE5: Katalog nachtraeglich austauschen ---------------

	@Test
	public void setCatalogFindsNewTilesButKeepsHistorySortAndCategory() {
		StoreSearchController c = new StoreSearchController(catalog(), JAVA_MODS_SHOWN);
		c.submit("stone");
		c.setCategories(NavRailCategories.typesFor(NavRailEntry.THEMES));
		c.cycleSort(); // AUTHOR_ASC
		c.setHideRestartRequired(true);

		TileData catalogTile = tile("cat1", "Katalog-Kachel", "vitrine", TilePackageType.THEME, TileStatus.NONE);
		List<TileData> merged = new ArrayList<>(catalog());
		merged.add(catalogTile);
		c.setCatalog(merged);

		assertEquals(c.catalogSize(), merged.size(), "die neue Kachel zaehlt jetzt mit");
		assertEquals(c.submittedText(), "stone", "der abgeschickte Suchtext ueberlebt den Katalog-Tausch");
		assertEquals(c.recentSearches(), List.of("stone"), "der Verlauf ueberlebt den Katalog-Tausch");
		assertEquals(c.sort(), SearchSort.AUTHOR_ASC, "die Sortierung ueberlebt den Katalog-Tausch");
		assertTrue(c.hideRestartRequired(), "der Neustart-Filter ueberlebt den Katalog-Tausch");

		c.setCategories(NavRailCategories.typesFor(NavRailEntry.THEMES));
		c.clearActiveSearch();
		assertTrue(ids(c.results()).contains("cat1"),
				"eine per setCatalog eingespeiste Katalog-Kachel muss durchsuchbar sein");
	}
}
