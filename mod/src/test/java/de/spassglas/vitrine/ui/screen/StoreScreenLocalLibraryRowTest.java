package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileRowWidget;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE84-Beweis: {@code StoreScreen.buildStartContent()} kehrte vorher
 * im Zustand LOADING (Lade-Skeleton, {@link StoreStateView#hasAction()} ==
 * {@code false}) zurueck, ohne {@code buildLocalLibraryRow()} aufzurufen --
 * der {@code if (hasAction()) { ... }}-Zweig hatte kein {@code else}, das die
 * y-Koordinate fuer die lokale Bibliotheksreihe fuer diesen Fall bestimmt.
 * Ein 15s-Netz-Timeout (CatalogEndpoints.java:49) verdeckte damit minutenlang
 * bereits installierte Pakete (Charter-Regel 12).
 *
 * <p>{@code StoreScreen} selbst laesst sich ohne laufenden MinecraftClient
 * nicht aufbauen/rendern (kein Screen-Test im gesamten Projekt tut das, siehe
 * {@code PackageDetailScreenInstallErrorTest}); die Entscheidung "wo setzt die
 * lokale Bibliotheksreihe unter dem Zustands-Panel an" ist deshalb als reine,
 * client-freie Funktion {@link StoreScreen#localLibraryRowTopY} ausgelagert --
 * genau die Stelle, die vorher unerreichbar war. Dieser Test treibt sie fuer
 * einen simulierten haengenden Katalog-Request (LOADING, wie waehrend des
 * 15s-Timeouts) und beweist, dass sie eine echte, erreichbare Position
 * liefert statt den Aufrufer (= den Aufbau der lokalen Bibliotheksreihe)
 * ueberhaupt nicht zu erreichen.</p>
 */
public final class StoreScreenLocalLibraryRowTest {

	@AfterMethod
	public void resetSeam() {
		CatalogAvailability.reset();
	}

	@Test
	public void hangingCatalogRequestStillPlacesTheLocalLibraryRowUnderTheSkeleton() {
		// Simuliert den haengenden/langsamen Katalog-Request aus dem Bug-
		// Bericht: solange CatalogEndpoints noch auf eine Antwort wartet (bis
		// zu 15s), haelt StoreCatalogController den Halter auf LOADING.
		CatalogAvailability.report(CatalogAvailability.State.LOADING, null);
		Optional<StoreStateView> view = CatalogAvailability.currentStateView();
		assertTrue(view.isPresent(), "LOADING muss ein Zustands-Panel ausloesen");
		assertTrue(view.get().isSkeleton());
		assertTrue(!view.get().hasAction(), "Lade-Skeleton hat keinen Knopf -- genau der vorher uebersprungene Zweig");

		int panelTopY = 100;
		int rowY = StoreScreen.localLibraryRowTopY(view.get(), panelTopY, false);

		// Vorher: dieser Aufruf lief nie, weil buildLocalLibraryRow() im
		// hasAction()==false-Zweig ueberhaupt nicht erreicht wurde (frueher
		// Rueckkehr). Jetzt: eine konkrete, unter dem Skeleton liegende
		// y-Koordinate -- die lokale Bibliotheksreihe bleibt bedienbar.
		assertTrue(rowY > panelTopY, "die lokale Bibliotheksreihe muss unter dem Lade-Skeleton stehen");
		assertEquals(rowY, panelTopY + StoreStatePanel.skeletonContentAdvance());
	}

	@Test
	public void loadingNeverFallsBackToTheButtonLayoutMath() {
		// Das Lade-Skeleton hat kein Symbol+Titel+Satz-Layout wie die
		// Knopf-Zustaende (OFFLINE/CATALOG_BROKEN) -- faellt die Berechnung
		// faelschlich auf StoreStatePanel.contentAdvance() zurueck, sitzt die
		// Bibliotheksreihe entweder zu hoch (ueberlappt die Platzhalter-
		// Kacheln) oder zu tief.
		StoreStateView loading = StoreStateView.loading();
		int viaSkeleton = StoreScreen.localLibraryRowTopY(loading, 0, false);
		assertEquals(viaSkeleton, StoreStatePanel.skeletonContentAdvance());
		assertTrue(viaSkeleton != StoreStatePanel.contentAdvance(),
				"Skeleton- und Knopf-Layout duerfen nicht zufaellig dieselbe Hoehe ergeben, sonst beweist der "
						+ "Test nichts");
	}

	@Test
	public void actionStateWithoutCachedCatalogPlacesTheRowRightUnderTheButton() {
		StoreStateView offlineNoCache = StoreStateView.offlineNoCache();
		assertTrue(offlineNoCache.hasAction());

		int panelTopY = 50;
		int rowY = StoreScreen.localLibraryRowTopY(offlineNoCache, panelTopY, false);

		int expectedButtonY = panelTopY + StoreStatePanel.contentAdvance();
		int expected = expectedButtonY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE;
		assertEquals(rowY, expected);
	}

	@Test
	public void actionStateWithCachedCatalogRowMakesRoomForItFirst() {
		StoreStateView catalogBrokenCached = StoreStateView.catalogBrokenCached(LocalDate.of(2026, 1, 1));
		assertTrue(catalogBrokenCached.hasAction());

		int panelTopY = 50;
		int withoutCachedRow = StoreScreen.localLibraryRowTopY(catalogBrokenCached, panelTopY, false);
		int withCachedRow = StoreScreen.localLibraryRowTopY(catalogBrokenCached, panelTopY, true);

		int expectedGap = TileRowWidget.preferredHeight() + VitrineTheme.SPACE_GUTTER;
		assertEquals(withCachedRow - withoutCachedRow, expectedGap,
				"eine gecachte Katalogreihe muss die lokale Bibliotheksreihe genau um ihre eigene Hoehe "
						+ "nach unten schieben");
	}
}
