package de.spassglas.vitrine.ui.state;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP S7: der {@link CatalogAvailability}-Seam loest "kein Netz" /
 * "Katalog kaputt" / "Lade-Skeleton" aus, bis der echte Katalog-Client (K2)
 * und Offline-Modus (K4) stehen -- ehrlich, kein Fake. Geprueft wird die
 * reine Abbildung Zustand -&gt; {@link StoreStateView} und der Prozess-Halter.
 */
public final class CatalogAvailabilityTest {

	@AfterMethod
	public void resetSeam() {
		CatalogAvailability.reset();
	}

	@Test
	public void defaultIsReadyAndShowsNoPanel() {
		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.READY);
		assertTrue(CatalogAvailability.currentStateView().isEmpty(),
				"READY zeigt Kacheln, kein Zustands-Panel");
	}

	@Test
	public void loadingMapsToASkeletonWithoutButtonOrSpinner() {
		Optional<StoreStateView> view =
				CatalogAvailability.stateViewFor(CatalogAvailability.State.LOADING, null);
		assertTrue(view.isPresent());
		assertTrue(view.get().isSkeleton());
		assertFalse(view.get().hasAction(), "Lade-Skeleton hat keinen Knopf");
		assertFalse(view.get().showsSpinner());
	}

	@Test
	public void offlineMapsToADatedRetryPanel() {
		LocalDate date = LocalDate.of(2026, 1, 2);
		StoreStateView view =
				CatalogAvailability.stateViewFor(CatalogAvailability.State.OFFLINE, date).orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertEquals(view.asOfDate().orElseThrow(), date);
		assertTrue(view.heading("de_de").contains("2026-01-02"));
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
	}

	@Test(expectedExceptions = NullPointerException.class)
	public void offlineWithoutACacheDateIsRejected() {
		CatalogAvailability.stateViewFor(CatalogAvailability.State.OFFLINE, null);
	}

	@Test
	public void offlineNoCacheMapsToADatelessRetryPanel() {
		StoreStateView view = CatalogAvailability
				.stateViewFor(CatalogAvailability.State.OFFLINE_NO_CACHE, null).orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.asOfDate().isEmpty(), "kein Cache -> kein erfundenes Standdatum");
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
	}

	@Test
	public void reportedOfflineNoCacheShowsThePanelWithoutADate() {
		CatalogAvailability.report(CatalogAvailability.State.OFFLINE_NO_CACHE, null);
		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.OFFLINE_NO_CACHE);
		StoreStateView view = CatalogAvailability.currentStateView().orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.asOfDate().isEmpty());
	}

	@Test
	public void catalogBrokenMapsToARetryPanel() {
		StoreStateView view =
				CatalogAvailability.stateViewFor(CatalogAvailability.State.CATALOG_BROKEN, null).orElseThrow();
		assertEquals(view.state(), StoreState.CATALOG_BROKEN);
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
	}

	@Test
	public void reportThenResetRoundTrips() {
		CatalogAvailability.report(CatalogAvailability.State.CATALOG_BROKEN, LocalDate.of(2026, 5, 5));
		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.CATALOG_BROKEN);
		assertEquals(CatalogAvailability.cacheAsOf(), LocalDate.of(2026, 5, 5));
		assertTrue(CatalogAvailability.currentStateView().isPresent());

		CatalogAvailability.reset();
		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.READY);
		assertTrue(CatalogAvailability.currentStateView().isEmpty());
	}

	@Test
	public void reportedOfflineProducesADatedPanelFromTheHeldDate() {
		CatalogAvailability.report(CatalogAvailability.State.OFFLINE, LocalDate.of(2025, 12, 31));
		StoreStateView view = CatalogAvailability.currentStateView().orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.heading("en_us").contains("2025-12-31"));
	}
}
