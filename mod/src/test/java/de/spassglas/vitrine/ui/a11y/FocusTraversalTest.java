package de.spassglas.vitrine.ui.a11y;

import de.spassglas.vitrine.ui.a11y.FocusTraversal.DismissLayer;
import de.spassglas.vitrine.ui.a11y.FocusTraversal.FocusStop;
import de.spassglas.vitrine.ui.a11y.FocusTraversal.StoreState;
import de.spassglas.vitrine.ui.nav.NavRailLayout;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Belegt das Fertig-Kriterium von ROADMAP S9: "Store einmal vollstaendig ohne
 * Maus bedienbar" -- als reiner, headless-Test ueber {@link FocusTraversal}.
 *
 * <p>Der {@link #REPRESENTATIVE} Zustand entspricht der Vorgabe: Hero-Karussell
 * plus mehrere Kachelreihen plus offenes Download-Flyout, dazu eine Rueckkehr
 * und die volle Nav-Rail. Die Tests zeigen: die Tab-Reihenfolge erreicht
 * <em>jedes</em> aktionierbare Element genau einmal, Pfeiltasten wandern
 * innerhalb einer Reihe, {@code Tab} springt an deren Ende zur naechsten Reihe,
 * und {@code ESC} schliesst Overlays/Screens in umgekehrter Oeffnungsreihenfolge.</p>
 */
public final class FocusTraversalTest {

	private static final StoreState REPRESENTATIVE = new StoreState(
			true, NavRailLayout.EXPANDED, false,
			3, List.of(5, 4, 6),
			true, 2,
			false, 0);

	private static final int EXPECTED_STOPS =
			4    // Kopfleiste: Zurueck, Suche, Einstellungen, Downloads
			+ 9  // Nav-Rail
			+ 5  // Hero: Pfeil links, 3 Punkte, Pfeil rechts
			+ 5 + 4 + 6  // drei Kachelreihen
			+ 2; // Flyout-Zeilen

	// --- Vollstaendigkeit: jedes Element genau einmal --------------------

	@Test
	public void tabOrderReachesEveryActionableElementExactlyOnce() {
		List<FocusStop> order = FocusTraversal.order(REPRESENTATIVE);

		assertEquals(order.size(), EXPECTED_STOPS,
				"jedes aktionierbare Element muss ein Tab-Stopp sein: " + ids(order));

		long distinct = order.stream().map(FocusStop::id).distinct().count();
		assertEquals(distinct, EXPECTED_STOPS, "kein Element darf doppelt vorkommen: " + ids(order));
	}

	@Test
	public void tabOrderContainsEveryNamedGroupRepresentative() {
		List<String> ids = ids(FocusTraversal.order(REPRESENTATIVE));

		assertTrue(ids.containsAll(List.of(
				"header.back", "header.search", "header.settings", "header.downloads",
				"nav.start", "nav.library", "nav.settings",
				"hero.prev", "hero.dot.0", "hero.dot.2", "hero.next",
				"tilerow.0.tile.0", "tilerow.0.tile.4",
				"tilerow.1.tile.3", "tilerow.2.tile.5",
				"flyout.row.0", "flyout.row.1")), ids.toString());
		assertFalse(ids.contains("header.navToggle"), "ausgeklappte Rail hat keinen Umschalter");
	}

	@Test
	public void readingOrderRunsHeaderThenNavThenHeroThenRowsThenFlyout() {
		List<String> ids = ids(FocusTraversal.order(REPRESENTATIVE));

		assertTrue(ids.indexOf("header.search") < ids.indexOf("nav.start"));
		assertTrue(ids.indexOf("nav.settings") < ids.indexOf("hero.prev"));
		assertTrue(ids.indexOf("hero.next") < ids.indexOf("tilerow.0.tile.0"));
		assertTrue(ids.indexOf("tilerow.0.tile.4") < ids.indexOf("tilerow.1.tile.0"));
		assertTrue(ids.indexOf("tilerow.2.tile.5") < ids.indexOf("flyout.row.0"));
	}

	@Test
	public void walkingTabFromTheFirstStopVisitsEveryStopOnceThenWraps() {
		List<FocusStop> order = FocusTraversal.order(REPRESENTATIVE);
		List<String> visited = new ArrayList<>();
		String current = order.get(0).id();
		visited.add(current);
		for (int i = 1; i < order.size(); i++) {
			current = FocusTraversal.tabMove(REPRESENTATIVE, current, +1).orElseThrow().id();
			visited.add(current);
		}
		assertEquals(visited, ids(order), "Tab-Rundlauf muss die Reihenfolge exakt abgehen");

		String afterFullLoop = FocusTraversal.tabMove(REPRESENTATIVE, current, +1).orElseThrow().id();
		assertEquals(afterFullLoop, order.get(0).id(), "hinter dem letzten Stopp kommt wieder der erste");
	}

	@Test
	public void shiftTabFromFirstStopWrapsToLast() {
		List<FocusStop> order = FocusTraversal.order(REPRESENTATIVE);
		String prev = FocusTraversal.tabMove(REPRESENTATIVE, order.get(0).id(), -1).orElseThrow().id();
		assertEquals(prev, order.get(order.size() - 1).id());
	}

	// --- Pfeiltasten innerhalb einer Reihe -----------------------------

	@Test
	public void arrowKeysMoveWithinATileRowAndClampAtItsEnds() {
		assertEquals(FocusTraversal.arrowMove(REPRESENTATIVE, "tilerow.0.tile.0", +1).orElseThrow().id(),
				"tilerow.0.tile.1");
		assertEquals(FocusTraversal.arrowMove(REPRESENTATIVE, "tilerow.0.tile.2", -1).orElseThrow().id(),
				"tilerow.0.tile.1");

		assertTrue(FocusTraversal.arrowMove(REPRESENTATIVE, "tilerow.0.tile.4", +1).isEmpty(),
				"am rechten Reihenende bewegt die Pfeiltaste nichts -- Tab wechselt die Reihe");
		assertTrue(FocusTraversal.arrowMove(REPRESENTATIVE, "tilerow.0.tile.0", -1).isEmpty(),
				"am linken Reihenende ebenso");
	}

	@Test
	public void tabAtRowEndJumpsToTheNextRow() {
		assertEquals(FocusTraversal.tabMove(REPRESENTATIVE, "tilerow.0.tile.4", +1).orElseThrow().id(),
				"tilerow.1.tile.0");
	}

	@Test
	public void arrowKeysTraverseTheHeroGroupIncludingArrowsAndDots() {
		assertEquals(FocusTraversal.arrowMove(REPRESENTATIVE, "hero.prev", +1).orElseThrow().id(), "hero.dot.0");
		assertEquals(FocusTraversal.arrowMove(REPRESENTATIVE, "hero.dot.0", +1).orElseThrow().id(), "hero.dot.1");
		assertEquals(FocusTraversal.arrowMove(REPRESENTATIVE, "hero.dot.2", +1).orElseThrow().id(), "hero.next");
		assertTrue(FocusTraversal.arrowMove(REPRESENTATIVE, "hero.next", +1).isEmpty());
	}

	@Test
	public void arrowKeysDoNotCrossFromOneRowIntoAnother() {
		assertTrue(FocusTraversal.arrowMove(REPRESENTATIVE, "hero.next", +1).isEmpty(),
				"hinter dem Hero beginnt eine andere Gruppe -- kein Pfeil-Uebergang");
	}

	// --- ESC schliesst in umgekehrter Reihenfolge ---------------------

	@Test
	public void escClosesOnlyTheOpenFlyoutThenLeavesTheStore() {
		assertEquals(FocusTraversal.dismissOrder(REPRESENTATIVE),
				List.of(DismissLayer.DOWNLOADS_FLYOUT, DismissLayer.SCREEN));
	}

	@Test
	public void escClosesEveryOpenLayerInReverseOfOpeningOrder() {
		StoreState allOpen = new StoreState(
				true, NavRailLayout.OVERLAY, true,
				0, List.of(),
				true, 1,
				true, 3);

		List<DismissLayer> openingOrder = List.of(
				DismissLayer.SCREEN, DismissLayer.NAV_OVERLAY,
				DismissLayer.DOWNLOADS_FLYOUT, DismissLayer.SUGGESTIONS);
		List<DismissLayer> reversed = new ArrayList<>(openingOrder);
		java.util.Collections.reverse(reversed);

		assertEquals(FocusTraversal.dismissOrder(allOpen), reversed,
				"wiederholtes ESC schliesst zuletzt Geoeffnetes zuerst, am Ende den Store");
	}

	@Test
	public void escWithNothingOpenLeavesTheStore() {
		StoreState nothingOpen = new StoreState(
				false, NavRailLayout.EXPANDED, false, 2, List.of(3), false, 0, false, 0);
		assertEquals(FocusTraversal.dismissOrder(nothingOpen), List.of(DismissLayer.SCREEN));
	}

	// --- Zustandsabhaengige Sichtbarkeit -----------------------------

	@Test
	public void openNavOverlayHidesContentAndFlyoutStops() {
		StoreState overlayOpen = new StoreState(
				true, NavRailLayout.OVERLAY, true,
				3, List.of(5, 4),
				true, 2,
				false, 0);
		List<String> ids = ids(FocusTraversal.order(overlayOpen));

		assertTrue(ids.contains("header.navToggle"));
		assertTrue(ids.contains("nav.start"), "die offene Ueberlagerung zeigt die Nav-Eintraege");
		assertFalse(ids.stream().anyMatch(id -> id.startsWith("hero.")), "Inhalt liegt verdeckt darunter");
		assertFalse(ids.stream().anyMatch(id -> id.startsWith("tilerow.")));
		assertFalse(ids.stream().anyMatch(id -> id.startsWith("flyout.")));
	}

	@Test
	public void closedNavOverlayShowsHeaderAndContentButNoNavEntries() {
		StoreState overlayClosed = new StoreState(
				false, NavRailLayout.OVERLAY, false,
				0, List.of(3),
				false, 0,
				false, 0);
		List<String> ids = ids(FocusTraversal.order(overlayClosed));

		assertEquals(ids, List.of(
				"header.navToggle", "header.search", "header.settings", "header.downloads",
				"tilerow.0.tile.0", "tilerow.0.tile.1", "tilerow.0.tile.2"));
	}

	@Test
	public void suggestionRowsSitRightAfterTheSearchFieldAndBeforeSettings() {
		StoreState searching = new StoreState(
				false, NavRailLayout.EXPANDED, false,
				0, List.of(),
				false, 0,
				true, 3);
		List<String> ids = ids(FocusTraversal.order(searching));

		assertEquals(ids.subList(0, 6), List.of(
				"header.search", "suggestion.0", "suggestion.1", "suggestion.2",
				"header.settings", "header.downloads"));
	}

	@Test
	public void withoutHeroNoHeroStopsAppear() {
		StoreState noHero = new StoreState(
				false, NavRailLayout.EXPANDED, false, 0, List.of(2), false, 0, false, 0);
		assertFalse(ids(FocusTraversal.order(noHero)).stream().anyMatch(id -> id.startsWith("hero.")));
	}

	private static List<String> ids(List<FocusStop> stops) {
		return stops.stream().map(FocusStop::id).toList();
	}
}
