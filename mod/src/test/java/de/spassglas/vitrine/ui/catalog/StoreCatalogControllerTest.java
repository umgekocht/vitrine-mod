package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogEndpoints;
import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.Featured;
import de.spassglas.vitrine.catalog.FeaturedItem;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.IndexPage;
import de.spassglas.vitrine.catalog.SearchIndex;
import de.spassglas.vitrine.catalog.SearchIndexEntry;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.source.LocalLibrarySource;
import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.state.StoreState;
import de.spassglas.vitrine.ui.state.StoreStateActionId;
import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.tile.TileData;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP K4 (headless-Teil). Der {@link StoreCatalogController} schliesst den
 * echten Katalog-Client an die Store-UI an und behandelt den Offline-Fall
 * ehrlich: Store aus dem Cache, lokale/installierte Pakete bleiben bedienbar,
 * ein S7-Zustand statt eines Fehlerdialogs. Beide Nahtstellen sind Fakes -- kein
 * laufender Client, kein Netz.
 *
 * <p>{@link #offlineShowsCachedCatalogAndKeepsLocalPackagesUsable()} ist der
 * Netzstecker-Ersatz aus dem Fertig-Kriterium.</p>
 */
public final class StoreCatalogControllerTest {

	private Path libraryDir;

	@BeforeMethod
	public void setUp() throws IOException {
		libraryDir = Files.createTempDirectory("vitrine-k4-controller-test-");
		CatalogAvailability.reset();
	}

	@AfterMethod
	public void tearDown() throws IOException {
		CatalogAvailability.reset();
		if (libraryDir == null || !Files.exists(libraryDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(libraryDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// --- Fakes -------------------------------------------------------------

	private static final class FakeIndexSource implements IndexSource {
		private final CatalogResult<IndexPage> result;
		private final AtomicInteger calls = new AtomicInteger();

		FakeIndexSource(CatalogResult<IndexPage> result) {
			this.result = result;
		}

		@Override
		public CatalogResult<IndexPage> fetchPage(int pageNumber) {
			calls.incrementAndGet();
			return result;
		}
	}

	/**
	 * ROADMAP GATE72: liefert mehrere Index-Seiten aus einer vorbereiteten
	 * Liste, eine je 1-basierter Seitennummer -- beweist, dass
	 * {@link StoreCatalogController} tatsaechlich ueber die erste Seite hinaus
	 * nachlaedt.
	 */
	private static final class MultiPageIndexSource implements IndexSource {
		private final List<IndexPage> pages;
		private final AtomicInteger calls = new AtomicInteger();

		MultiPageIndexSource(List<IndexPage> pages) {
			this.pages = pages;
		}

		@Override
		public CatalogResult<IndexPage> fetchPage(int pageNumber) {
			calls.incrementAndGet();
			if (pageNumber < 1 || pageNumber > pages.size()) {
				return CatalogResult.catalogBroken("keine Seite " + pageNumber);
			}
			return CatalogResult.online(pages.get(pageNumber - 1));
		}
	}

	private static SearchIndexSource searchSourceOf(CatalogResult<SearchIndex> result) {
		return () -> result;
	}

	private static CatalogCache cacheOf(Optional<CatalogCache.Cached> value) {
		return () -> value;
	}

	private static IndexPage pageWith(int n) {
		List<IndexPackage> packages = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			packages.add(new IndexPackage("kat.paket" + i, "Katalogpaket " + i, "autorin",
					"theme", "1.0.0", null, null, "2026-09-01T00:00:00Z", false));
		}
		return new IndexPage(1, "2026-09-10T00:00:00Z", 1, 50, 1, n, null, null, packages);
	}

	private int localLibrarySize() {
		return LocalLibrarySource.forLibraryDir(libraryDir).scan(PackState.createDefault()).size();
	}

	// --- Tests -----------------------------------------------------------

	@Test
	public void offlineShowsCachedCatalogAndKeepsLocalPackagesUsable() throws IOException {
		LocalDate cacheDate = LocalDate.of(2026, 8, 15);
		FakeIndexSource source =
				new FakeIndexSource(CatalogResult.noNetwork("kein Netz -- Netzstecker gezogen"));
		CatalogCache cache = cacheOf(
				Optional.of(new CatalogCache.Cached(pageWith(3).packages(), cacheDate)));
		StoreCatalogController controller = new StoreCatalogController(source, cache);

		Files.write(libraryDir.resolve("lokal.vpkg"), VpkgFixtures.minimalValidPackage());

		controller.refresh(); // wirft nichts

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.OFFLINE);
		assertEquals(CatalogAvailability.cacheAsOf(), cacheDate, "genau das Cache-Datum, kein erfundenes");
		assertEquals(controller.catalogTiles().size(), 3, "die Cache-Pakete erscheinen als Kacheln");

		StoreStateView view = CatalogAvailability.currentStateView().orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.heading("de_de").contains(cacheDate.toString()), "Hinweiszeile nennt den Stand");

		assertEquals(localLibrarySize(), 1, "die lokale Bibliotheksreihe ist trotzdem befuellt");
	}

	@Test
	public void noNetworkWithoutCacheShowsOfflinePanelWithoutCrashAndKeepsLocalPackages() throws IOException {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.noNetwork("kein Netz")),
				cacheOf(Optional.empty()));

		Files.write(libraryDir.resolve("lokal.vpkg"), VpkgFixtures.minimalValidPackage());

		controller.refresh();

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.OFFLINE_NO_CACHE);
		assertTrue(controller.catalogTiles().isEmpty(), "ohne Cache keine Katalog-Kacheln");

		StoreStateView view = CatalogAvailability.currentStateView().orElseThrow();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.asOfDate().isEmpty(), "kein erfundenes Standdatum");
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
		assertEquals(localLibrarySize(), 1, "lokale/installierte Pakete bleiben bedienbar");
	}

	/**
	 * GATE40: vorher schrieb dieser Test das Fehlverhalten fest ("keine Kacheln
	 * -- auch nicht aus dem Cache"). Eine kaputte Datei auf dem Server legte
	 * damit den ganzen Katalog lahm, obwohl ein heiler Stand lokal lag.
	 */
	@Test
	public void catalogBrokenWithCacheShowsTheCachedStandHonestlyLabelled() throws IOException {
		LocalDate stand = LocalDate.of(2026, 1, 1);
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.catalogBroken("Index-Seite verletzt das Schema: schemaVersion")),
				cacheOf(Optional.of(new CatalogCache.Cached(pageWith(2).packages(), stand))));

		Files.write(libraryDir.resolve("lokal.vpkg"), VpkgFixtures.minimalValidPackage());

		controller.refresh();

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.CATALOG_BROKEN_CACHED);
		assertEquals(controller.catalogTiles().size(), 2, "der gespeicherte Stand bleibt als Kacheln bedienbar");
		StoreStateView view = CatalogAvailability.currentStateView().orElseThrow();
		assertEquals(view.headingKey(), "vitrine.state.catalog_broken_cached.title",
				"ehrlich als 'Katalog kaputt' beschriftet, nicht als 'kein Netz'");
		assertEquals(view.asOfDate(), Optional.of(stand), "das Standdatum wird mitgefuehrt");
		assertTrue(controller.featuredTiles().isEmpty(), "keine Kuration aus einem kaputten Abruf");
		assertEquals(localLibrarySize(), 1);
	}

	@Test
	public void catalogBrokenWithoutCacheStaysEmptyAndKeepsLocalPackages() throws IOException {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.catalogBroken("Index-Seite verletzt das Schema: schemaVersion")),
				cacheOf(Optional.empty()));

		Files.write(libraryDir.resolve("lokal.vpkg"), VpkgFixtures.minimalValidPackage());

		controller.refresh();

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.CATALOG_BROKEN);
		assertTrue(controller.catalogTiles().isEmpty(), "ohne Cache gibt es keinen ehrlichen Stand zum Zeigen");
		assertTrue(CatalogAvailability.currentStateView().isPresent());
		assertEquals(localLibrarySize(), 1);
	}

	@Test
	public void onlineMapsPackagesToTilesAndReportsReadyWithNoPanel() {
		FakeIndexSource source = new FakeIndexSource(CatalogResult.online(pageWith(5)));
		StoreCatalogController controller = new StoreCatalogController(source, cacheOf(Optional.empty()));

		controller.refresh();

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.READY);
		assertTrue(CatalogAvailability.currentStateView().isEmpty(), "READY zeigt kein Zustands-Panel");
		assertEquals(controller.catalogTiles().size(), 5, "N Pakete -> N Kacheln");
		assertEquals(controller.catalogTiles().get(0).id(), "kat.paket0");
		assertEquals(source.calls.get(), 1, "genau ein Abruf, kein doppelter Request");
	}

	@Test
	public void unknownCategoryPackagesAreSkippedNotCrash() {
		List<IndexPackage> packages = List.of(
				new IndexPackage("kat.gut", "Gut", "a", "theme", "1.0.0", null, null, "2026-09-01T00:00:00Z", false),
				new IndexPackage("kat.krude", "Krude", "a", "voll-erfundene-kategorie", "1.0.0", null, null,
						"2026-09-01T00:00:00Z", false));
		IndexPage page = new IndexPage(1, "2026-09-10T00:00:00Z", 1, 50, 1, 2, null, null, packages);
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(page)), cacheOf(Optional.empty()));

		controller.refresh();

		assertEquals(controller.catalogTiles().size(), 1, "nur die bekannte Kategorie wird zur Kachel");
		assertEquals(controller.catalogTiles().get(0).id(), "kat.gut");
	}

	@Test
	public void refreshNeverThrowsEvenIfTheIndexSourceFails() {
		IndexSource boom = pageNumber -> {
			throw new IllegalStateException("Seam kaputt");
		};
		StoreCatalogController controller = new StoreCatalogController(boom, cacheOf(Optional.empty()));

		controller.refresh(); // darf nicht werfen

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.CATALOG_BROKEN);
		assertTrue(controller.catalogTiles().isEmpty());
	}

	@Test
	public void staleCatalogTilesAreReplacedOnTheNextRefresh() {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(pageWith(4))), cacheOf(Optional.empty()));
		controller.refresh();
		assertEquals(controller.catalogTiles().size(), 4);

		StoreCatalogController broken = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.catalogBroken("kaputt")), cacheOf(Optional.empty()));
		broken.refresh();
		assertTrue(broken.catalogTiles().isEmpty());

		List<TileData> firstStillHeld = controller.catalogTiles();
		assertEquals(firstStillHeld.size(), 4, "jeder Controller haelt seine eigene Momentaufnahme");
	}

	// --- ROADMAP X2: kuratierte Reihen (Featured/Neu) ----------------------

	private static FeaturedSource featuredSourceOf(CatalogResult<Featured> result) {
		return () -> result;
	}

	@Test
	public void onlineWithFeaturedSortsFeaturedRowByOrder() {
		IndexPage page = pageWith(3); // kat.paket0, kat.paket1, kat.paket2
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z", List.of(
				new FeaturedItem("kat.paket2", 0, null),
				new FeaturedItem("kat.paket0", 1, null)), List.of());
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(page)), cacheOf(Optional.empty()),
				JavaModOfferGate.hidden(), featuredSourceOf(CatalogResult.online(featured)));

		controller.refresh();

		List<TileData> featuredTiles = controller.featuredTiles();
		assertEquals(featuredTiles.size(), 2);
		assertEquals(featuredTiles.get(0).id(), "kat.paket2", "order 0 zuerst");
		assertEquals(featuredTiles.get(1).id(), "kat.paket0", "order 1 danach");
	}

	/**
	 * ROADMAP X2: "Beliebt" kommt aus {@code featured.json}s {@code popular[]}
	 * -- derselbe Abruf wie "Featured", aber eine eigenstaendige Liste. Bewusst
	 * ein Paket, das nur in {@code popular} steht (nicht in {@code items}),
	 * genau wie die echten Registry-Beispielpakete das jetzt tun.
	 */
	@Test
	public void onlineWithFeaturedSortsPopularRowByOrderIndependentlyOfFeatured() {
		IndexPage page = pageWith(3); // kat.paket0, kat.paket1, kat.paket2
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z",
				List.of(new FeaturedItem("kat.paket0", 0, null)),
				List.of(
						new FeaturedItem("kat.paket2", 0, null),
						new FeaturedItem("kat.paket1", 1, null)));
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(page)), cacheOf(Optional.empty()),
				JavaModOfferGate.hidden(), featuredSourceOf(CatalogResult.online(featured)));

		controller.refresh();

		List<TileData> popularTiles = controller.popularTiles();
		assertEquals(popularTiles.size(), 2);
		assertEquals(popularTiles.get(0).id(), "kat.paket2", "order 0 zuerst");
		assertEquals(popularTiles.get(1).id(), "kat.paket1", "order 1 danach");
		assertTrue(popularTiles.stream().noneMatch(t -> t.id().equals("kat.paket0")),
				"kat.paket0 ist featured, aber nicht beliebt-kuratiert");

		List<TileData> featuredTiles = controller.featuredTiles();
		assertEquals(featuredTiles.size(), 1);
		assertEquals(featuredTiles.get(0).id(), "kat.paket0");
	}

	@Test
	public void onlineFillsNewRowSortedByUpdatedAtDescending() {
		List<IndexPackage> packages = List.of(
				new IndexPackage("kat.alt", "Alt", "a", "theme", "1.0.0", null, null,
						"2026-01-01T00:00:00Z", false),
				new IndexPackage("kat.neu", "Neu", "a", "theme", "1.0.0", null, null,
						"2026-09-01T00:00:00Z", false));
		IndexPage page = new IndexPage(1, "2026-09-10T00:00:00Z", 1, 50, 1, 2, null, null, packages);
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(page)), cacheOf(Optional.empty()));

		controller.refresh();

		List<TileData> newTiles = controller.newTiles();
		assertEquals(newTiles.size(), 2);
		assertEquals(newTiles.get(0).id(), "kat.neu", "neuestes Paket zuerst");
		assertEquals(newTiles.get(1).id(), "kat.alt");
	}

	@Test
	public void featuredFetchFailureLeavesFeaturedRowEmptyWithoutBreakingTheRestOfTheCatalog() {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(pageWith(3))), cacheOf(Optional.empty()),
				JavaModOfferGate.hidden(),
				featuredSourceOf(CatalogResult.catalogBroken("featured.json verletzt das Schema")));

		controller.refresh();

		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.READY,
				"ein kaputtes featured.json allein macht den Katalog nicht insgesamt kaputt");
		assertTrue(controller.featuredTiles().isEmpty(), "Featured-Reihe bleibt leer, ohne Absturz");
		assertTrue(controller.popularTiles().isEmpty(), "Beliebt-Reihe bleibt leer, ohne Absturz");
		assertEquals(controller.catalogTiles().size(), 3, "die normale Katalogreihe ist unberuehrt");
		assertEquals(controller.newTiles().size(), 3, "die Neu-Reihe kommt weiterhin aus der Index-Seite");
	}

	@Test
	public void offlineOrBrokenCatalogLeavesCuratedRowsEmpty() {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.noNetwork("kein Netz")), cacheOf(Optional.empty()));

		controller.refresh();

		assertTrue(controller.featuredTiles().isEmpty());
		assertTrue(controller.popularTiles().isEmpty());
		assertTrue(controller.newTiles().isEmpty());
		assertTrue(controller.searchTiles().isEmpty(), "ohne frischen ONLINE-Abruf gibt es auch keine Suchkacheln");
	}

	// --- ROADMAP GATE72: Paginierung + search.json -------------------------

	private static IndexPackage packageN(int i) {
		return new IndexPackage("kat.paket" + i, "Katalogpaket " + i, "autorin", "theme", "1.0.0", null, null,
				"2026-09-01T00:00:00Z", false);
	}

	/** Baut {@code totalPages} Seiten mit je {@code pageSize} Paketen, 1-basiert. */
	private static List<IndexPage> pagesOf(int totalPages, int pageSize) {
		List<IndexPage> pages = new ArrayList<>();
		int totalCount = totalPages * pageSize;
		for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
			List<IndexPackage> packages = new ArrayList<>();
			int start = (pageNumber - 1) * pageSize;
			for (int i = start; i < start + pageSize; i++) {
				packages.add(packageN(i));
			}
			pages.add(new IndexPage(1, "2026-09-10T00:00:00Z", pageNumber, pageSize, totalPages, totalCount,
					null, null, packages));
		}
		return pages;
	}

	/**
	 * Woertliches Fertig-Kriterium von GATE72: "Test beweist &gt;24
	 * Katalog-Eintraege erreichbar." Das Live-{@code index.json} hat
	 * {@code pageSize: 24} -- diese Fixtur bildet genau das nach (2 Seiten
	 * a 24 Paketen, 48 insgesamt) und beweist, dass
	 * {@link StoreCatalogController#catalogTiles()} nicht bei der ersten
	 * Seite stehen bleibt.
	 */
	@Test
	public void catalogTilesIncludeEntriesBeyondTheFirstPage() {
		List<IndexPage> pages = pagesOf(2, 24);
		MultiPageIndexSource source = new MultiPageIndexSource(pages);
		StoreCatalogController controller = new StoreCatalogController(source, cacheOf(Optional.empty()));

		controller.refresh();

		List<TileData> tiles = controller.catalogTiles();
		assertEquals(tiles.size(), 48, "beide Seiten zusammen, nicht nur die erste (pageSize 24)");
		assertTrue(tiles.size() > 24, "mehr als die erste Seite allein liefern koennte");
		assertTrue(tiles.stream().anyMatch(t -> t.id().equals("kat.paket25")),
				"ein Paket, das nur auf Seite 2 steht, muss erreichbar sein");
		assertEquals(source.calls.get(), 2, "genau zwei Seitenabrufe, keiner mehr als notwendig");
	}

	/**
	 * Die Sicherheitsobergrenze ({@link de.spassglas.vitrine.catalog.CatalogEndpoints#MAX_INDEX_PAGES_PER_REFRESH})
	 * begrenzt das Nachladen, statt den Client bei einem kaputten/boesartigen
	 * {@code totalPages}-Wert zu hunderttausenden Anfragen zu verleiten.
	 */
	@Test
	public void pageFetchingStopsAtTheSafetyCeilingEvenIfTotalPagesClaimsMore() {
		int ceiling = CatalogEndpoints.MAX_INDEX_PAGES_PER_REFRESH;
		// Mehr tatsaechlich verfuegbare Seiten als die Obergrenze -- damit ein
		// Abbruch wegen "Seite fehlt" nicht faelschlich den Ceiling-Test besteht.
		List<IndexPage> pages = pagesOf(ceiling + 5, 1);
		List<IndexPage> hugeTotalPagesClaim = new ArrayList<>();
		for (IndexPage page : pages) {
			hugeTotalPagesClaim.add(new IndexPage(page.schemaVersion(), page.generatedAt(), page.page(),
					page.pageSize(), 100_000, page.totalCount(), page.nextPage(), page.previousPage(),
					page.packages()));
		}
		MultiPageIndexSource source = new MultiPageIndexSource(hugeTotalPagesClaim);
		StoreCatalogController controller = new StoreCatalogController(source, cacheOf(Optional.empty()));

		controller.refresh();

		assertEquals(source.calls.get(), ceiling,
				"nicht mehr Seitenabrufe als die Obergrenze, trotz behaupteter 100000 Seiten und genug echter Fixtur-Seiten");
		assertEquals(controller.catalogTiles().size(), ceiling, "ein Paket je geladener Seite (pageSize 1)");
	}

	/**
	 * Scheitert eine Folgeseite, bricht das Nachladen ab, der Store zeigt
	 * ehrlich einen Teilstand statt eines kompletten Fehlschlags.
	 */
	@Test
	public void catalogTilesStopAtTheLastSuccessfulPageIfALaterPageFails() {
		List<IndexPage> pages = new ArrayList<>(pagesOf(3, 2));
		StoreCatalogController controller = new StoreCatalogController(
				new MultiPageIndexSource(pages.subList(0, 2)), cacheOf(Optional.empty()));
		// pagesOf(3, 2) behauptet totalPages=3, aber die Fake-Quelle hat nur 2
		// Seiten -- die dritte liefert CATALOG_BROKEN (siehe MultiPageIndexSource).

		controller.refresh();

		assertEquals(controller.catalogTiles().size(), 4, "die ersten zwei Seiten (je 2 Pakete) bleiben erhalten");
		assertEquals(CatalogAvailability.current(), CatalogAvailability.State.READY,
				"die erste Seite war online -- ein Teilstand ist kein 'Katalog kaputt'");
	}

	/**
	 * Woertliches Fertig-Kriterium von GATE72: "search.json wird fuer die
	 * Live-Suche abgerufen." Das Suchindex-Paket ist absichtlich eins, das in
	 * keiner Index-Seite steht -- ein Treffer dafuer kann nur aus
	 * {@code search.json} stammen.
	 */
	@Test
	public void searchTilesComeFromSearchIndexNotJustTheLoadedPages() {
		SearchIndex searchIndex = new SearchIndex(
				1, "2026-09-10T00:00:00Z", 1, List.of(
						new SearchIndexEntry("such.nur-im-suchindex",
								"Nur im Suchindex", "autorin", "theme", "CC0-1.0", List.of(), List.of("suche"))));
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(pageWith(2))), cacheOf(Optional.empty()),
				JavaModOfferGate.hidden(), featuredSourceOf(CatalogResult.catalogBroken("kein Featured")),
				() -> PackState.createDefault(), searchSourceOf(CatalogResult.online(searchIndex)));

		controller.refresh();

		List<TileData> searchTiles = controller.searchTiles();
		assertEquals(searchTiles.size(), 1, "search.json wurde tatsaechlich abgerufen und ausgewertet");
		assertEquals(searchTiles.get(0).id(), "such.nur-im-suchindex",
				"ein Paket, das es nur in search.json gibt, nicht in der Index-Seite");
		assertTrue(controller.catalogTiles().stream().noneMatch(t -> t.id().equals("such.nur-im-suchindex")),
				"zum Vergleich: dieses Paket steht nicht in der Index-Seite");
	}

	/**
	 * Scheitert nur {@code search.json} (Index-Seite okay), faellt die Suche
	 * auf die bereits geladenen Katalog-Seiten zurueck statt leer zu bleiben.
	 */
	@Test
	public void searchTilesFallBackToCatalogTilesWhenSearchIndexFetchFails() {
		StoreCatalogController controller = new StoreCatalogController(
				new FakeIndexSource(CatalogResult.online(pageWith(3))), cacheOf(Optional.empty()),
				JavaModOfferGate.hidden(), NO_FEATURED, () -> PackState.createDefault(),
				searchSourceOf(CatalogResult.catalogBroken("search.json verletzt das Schema")));

		controller.refresh();

		assertEquals(controller.searchTiles().size(), 3, "Rueckfall auf die bereits geladenen Katalog-Kacheln");
		assertEquals(controller.catalogTiles().size(), 3);
	}

	private static final FeaturedSource NO_FEATURED = () -> CatalogResult.catalogBroken("kein Featured-Quell");
}
