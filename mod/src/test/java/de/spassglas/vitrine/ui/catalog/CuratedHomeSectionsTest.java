package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.Featured;
import de.spassglas.vitrine.catalog.FeaturedItem;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP X2 (headless-Teil des Fertig-Kriteriums): "gueltige featured.json-
 * Antwort ... die drei Abschnitte kommen typisiert und in der richtigen
 * Reihenfolge an; kaputte/leere Antwort -&gt; ehrlicher Fallback ohne Crash."
 * Deckt alle drei Reihen ab, die {@link CuratedHomeSections} aus dem Index
 * baut: Featured ({@code featured.json}s {@code items[]}), Beliebt
 * ({@code featured.json}s {@code popular[]}) und Neu (nach
 * {@code updatedAt}).
 */
public final class CuratedHomeSectionsTest {

	private static IndexPackage pkg(String id, String updatedAt) {
		return new IndexPackage(id, "Name " + id, "autorin", "theme", "1.0.0", null, null, updatedAt, false);
	}

	private static Featured featuredWith(List<FeaturedItem> items) {
		return new Featured(1, "2026-09-10T00:00:00Z", items, List.of());
	}

	private static Featured popularWith(List<FeaturedItem> popular) {
		return new Featured(1, "2026-09-10T00:00:00Z", List.of(), popular);
	}

	@Test
	public void featuredIsSortedByOrderAscendingRegardlessOfInputOrder() {
		List<IndexPackage> packages = List.of(
				pkg("a.one", "2026-01-01T00:00:00Z"),
				pkg("a.two", "2026-01-02T00:00:00Z"),
				pkg("a.three", "2026-01-03T00:00:00Z"));
		Featured featured = featuredWith(List.of(
				new FeaturedItem("a.three", 2, null),
				new FeaturedItem("a.one", 0, "erste Wahl"),
				new FeaturedItem("a.two", 1, null)));

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 3);
		assertEquals(tiles.get(0).id(), "a.one");
		assertEquals(tiles.get(1).id(), "a.two");
		assertEquals(tiles.get(2).id(), "a.three");
	}

	@Test
	public void featuredItemsMissingFromTheIndexAreSkippedNotCrashed() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));
		Featured featured = featuredWith(List.of(
				new FeaturedItem("a.one", 0, null),
				new FeaturedItem("a.verschwunden", 1, null)));

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 1, "das nicht (mehr) vorhandene Paket wird ausgelassen, kein Crash");
		assertEquals(tiles.get(0).id(), "a.one");
	}

	@Test
	public void nullFeaturedBecomesEmptyListWithoutCrash() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));

		List<TileData> tiles = CuratedHomeSections.featured(packages, null, JavaModOfferGate.hidden());

		assertTrue(tiles.isEmpty());
	}

	@Test
	public void emptyFeaturedItemsBecomeEmptyList() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));
		Featured featured = featuredWith(List.of());

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden());

		assertTrue(tiles.isEmpty());
	}

	// --- ROADMAP X2: "Beliebt" (featured.json.popular[]) --------------------

	@Test
	public void popularIsSortedByOrderAscendingRegardlessOfInputOrder() {
		List<IndexPackage> packages = List.of(
				pkg("a.one", "2026-01-01T00:00:00Z"),
				pkg("a.two", "2026-01-02T00:00:00Z"),
				pkg("a.three", "2026-01-03T00:00:00Z"));
		Featured featured = popularWith(List.of(
				new FeaturedItem("a.three", 2, null),
				new FeaturedItem("a.one", 0, "meistgenutzt"),
				new FeaturedItem("a.two", 1, null)));

		List<TileData> tiles = CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 3);
		assertEquals(tiles.get(0).id(), "a.one");
		assertEquals(tiles.get(1).id(), "a.two");
		assertEquals(tiles.get(2).id(), "a.three");
	}

	@Test
	public void popularItemsMissingFromTheIndexAreSkippedNotCrashed() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));
		Featured featured = popularWith(List.of(
				new FeaturedItem("a.one", 0, null),
				new FeaturedItem("a.verschwunden", 1, null)));

		List<TileData> tiles = CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 1, "das nicht (mehr) vorhandene Paket wird ausgelassen, kein Crash");
		assertEquals(tiles.get(0).id(), "a.one");
	}

	@Test
	public void nullFeaturedBecomesEmptyPopularListWithoutCrash() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));

		List<TileData> tiles = CuratedHomeSections.popular(packages, null, JavaModOfferGate.hidden());

		assertTrue(tiles.isEmpty());
	}

	@Test
	public void emptyPopularItemsBecomeEmptyList() {
		List<IndexPackage> packages = List.of(pkg("a.one", "2026-01-01T00:00:00Z"));
		Featured featured = popularWith(List.of());

		List<TileData> tiles = CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden());

		assertTrue(tiles.isEmpty());
	}

	/**
	 * Das wörtliche Verhalten aus ROADMAP X2: "Beliebt" kommt aus
	 * {@code featured.json.popular}, unabhaengig von "Featured" --
	 * {@code a.nur-featured} steht nur in {@code items}, {@code a.nur-beliebt}
	 * nur in {@code popular}, {@code a.beides} in beiden. Genau wie bei den
	 * echten Registry-Beispielpaketen (spassglas.mitternacht-theme steht nur
	 * in {@code featured}, spassglas.kompakt-hud nur in {@code popular}).
	 */
	@Test
	public void popularAndFeaturedAreIndependentListsThatCanDiverge() {
		List<IndexPackage> packages = List.of(
				pkg("a.nur-featured", "2026-01-01T00:00:00Z"),
				pkg("a.nur-beliebt", "2026-01-02T00:00:00Z"),
				pkg("a.beides", "2026-01-03T00:00:00Z"));
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z",
				List.of(
						new FeaturedItem("a.nur-featured", 0, null),
						new FeaturedItem("a.beides", 1, null)),
				List.of(
						new FeaturedItem("a.beides", 0, null),
						new FeaturedItem("a.nur-beliebt", 1, null)));

		List<TileData> featuredTiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden());
		List<TileData> popularTiles = CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden());

		assertEquals(featuredTiles.size(), 2);
		assertEquals(featuredTiles.stream().map(TileData::id).toList(), List.of("a.nur-featured", "a.beides"));

		assertEquals(popularTiles.size(), 2);
		assertEquals(popularTiles.stream().map(TileData::id).toList(), List.of("a.beides", "a.nur-beliebt"));

		assertTrue(featuredTiles.stream().noneMatch(t -> t.id().equals("a.nur-beliebt")),
				"a.nur-beliebt ist nicht featured");
		assertTrue(popularTiles.stream().noneMatch(t -> t.id().equals("a.nur-featured")),
				"a.nur-featured ist nicht beliebt-kuratiert");
	}

	@Test
	public void newestSortsPackagesByUpdatedAtDescending() {
		List<IndexPackage> packages = List.of(
				pkg("a.old", "2026-01-01T00:00:00Z"),
				pkg("a.newest", "2026-09-01T00:00:00Z"),
				pkg("a.middle", "2026-05-01T00:00:00Z"));

		List<TileData> tiles = CuratedHomeSections.newest(packages, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 3);
		assertEquals(tiles.get(0).id(), "a.newest");
		assertEquals(tiles.get(1).id(), "a.middle");
		assertEquals(tiles.get(2).id(), "a.old");
	}

	@Test
	public void newestPackageWithUnreadableDateSortsLastInsteadOfCrashing() {
		List<IndexPackage> packages = List.of(
				pkg("a.gut", "2026-01-01T00:00:00Z"),
				pkg("a.kaputt", "nicht-ein-datum"));

		List<TileData> tiles = CuratedHomeSections.newest(packages, JavaModOfferGate.hidden());

		assertEquals(tiles.size(), 2, "kein Absturz bei unlesbarem Datum");
		assertEquals(tiles.get(0).id(), "a.gut");
		assertEquals(tiles.get(1).id(), "a.kaputt");
	}

	@Test
	public void unknownCategoryIsSkippedInAllThreeRows() {
		List<IndexPackage> packages = List.of(
				new IndexPackage("a.krude", "Krude", "autorin", "voll-erfundene-kategorie", "1.0.0", null, null,
						"2026-01-01T00:00:00Z", false));
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z",
				List.of(new FeaturedItem("a.krude", 0, null)),
				List.of(new FeaturedItem("a.krude", 0, null)));

		assertTrue(CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden()).isEmpty());
		assertTrue(CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden()).isEmpty());
		assertTrue(CuratedHomeSections.newest(packages, JavaModOfferGate.hidden()).isEmpty());
	}

	@Test
	public void javaModOfferGateHidesRestrictedTilesInAllThreeRows() {
		List<IndexPackage> packages = List.of(
				new IndexPackage("a.jar", "Jar", "autorin", "hud", "1.0.0", null, null,
						"2026-01-01T00:00:00Z", true));
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z",
				List.of(new FeaturedItem("a.jar", 0, null)),
				List.of(new FeaturedItem("a.jar", 0, null)));

		assertTrue(CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden()).isEmpty(),
				"ohne Opt-in bleibt die Featured-Reihe ohne das Klasse-B-Angebot");
		assertTrue(CuratedHomeSections.popular(packages, featured, JavaModOfferGate.hidden()).isEmpty(),
				"ohne Opt-in bleibt die Beliebt-Reihe ohne das Klasse-B-Angebot");
		assertTrue(CuratedHomeSections.newest(packages, JavaModOfferGate.hidden()).isEmpty(),
				"ohne Opt-in bleibt die Neu-Reihe ohne das Klasse-B-Angebot");
	}

	// --- ROADMAP GATE80: echter Update-Status statt nur "Installiert" -------

	private static IndexPackage pkgWithVersion(String id, String latestVersion) {
		return new IndexPackage(id, "Name " + id, "autorin", "theme", latestVersion, null, null,
				"2026-01-01T00:00:00Z", false);
	}

	private static PackState installed(String... manifestIds) {
		PackState state = PackState.createDefault();
		for (String id : manifestIds) {
			state.addOrUpdate("vitrine/" + id, true);
		}
		return state;
	}

	@Test
	public void installedPackageWithOlderVersionThanCatalogShowsUpdateAvailable() {
		List<IndexPackage> packages = List.of(pkgWithVersion("a.veraltet", "1.2.0"));
		Featured featured = featuredWith(List.of(new FeaturedItem("a.veraltet", 0, null)));
		PackState installedState = installed("a.veraltet");
		InstalledVersionLookup olderLocalVersion = manifestId -> Optional.of("1.0.0");

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden(),
				installedState, olderLocalVersion);

		assertEquals(tiles.size(), 1);
		assertEquals(tiles.get(0).status(), TileStatus.UPDATE_AVAILABLE,
				"eine echt neuere Katalogversion muss UPDATE_AVAILABLE zeigen, nicht nur INSTALLED");
	}

	@Test
	public void installedPackageAlreadyOnTheLatestVersionStaysInstalledNotUpdateAvailable() {
		List<IndexPackage> packages = List.of(pkgWithVersion("a.aktuell", "1.2.0"));
		Featured featured = featuredWith(List.of(new FeaturedItem("a.aktuell", 0, null)));
		PackState installedState = installed("a.aktuell");
		InstalledVersionLookup sameLocalVersion = manifestId -> Optional.of("1.2.0");

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden(),
				installedState, sameLocalVersion);

		assertEquals(tiles.size(), 1);
		assertEquals(tiles.get(0).status(), TileStatus.INSTALLED,
				"auf dem neuesten Stand installiert darf kein Update vorgaukeln");
	}

	@Test
	public void installedPackageWithoutKnownLocalVersionStaysInstalledNotUpdateAvailable() {
		List<IndexPackage> packages = List.of(pkgWithVersion("a.unbekannt", "1.2.0"));
		Featured featured = featuredWith(List.of(new FeaturedItem("a.unbekannt", 0, null)));
		PackState installedState = installed("a.unbekannt");

		List<TileData> tiles = CuratedHomeSections.featured(packages, featured, JavaModOfferGate.hidden(),
				installedState, InstalledVersionLookup.NONE);

		assertEquals(tiles.size(), 1);
		assertEquals(tiles.get(0).status(), TileStatus.INSTALLED,
				"ohne bekannte lokale Version (Vor-GATE80-Verhalten) darf nichts vorgegaukelt werden "
						+ "(Charter-Regel 2)");
	}
}
