package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CachedIndexStore;
import de.spassglas.vitrine.catalog.CatalogClient;
import de.spassglas.vitrine.catalog.CatalogEndpoints;
import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.Featured;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.IndexPage;
import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.catalog.SearchIndex;
import de.spassglas.vitrine.catalog.SearchIndexEntry;
import de.spassglas.vitrine.pack.InstalledPackVersions;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.tile.TileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freies Steuerteil, das den echten Katalog-Client (ROADMAP K2) an die
 * Store-UI anschliesst und den Offline-Fall ehrlich behandelt (ROADMAP K4).
 * Keine Minecraft-Klasse -- ohne laufenden Client testbar; den Thread-Wechsel
 * macht der Aufrufer ({@code StoreScreen}), {@link #refresh()} ist synchron.
 *
 * <p>Zwei Nahtstellen im Konstruktor:</p>
 * <ul>
 *   <li>{@link IndexSource} -- der Katalog-Abruf. Produktion:
 *       {@code CatalogClient.production(configDir)::fetchIndex}.</li>
 *   <li>{@link CatalogCache} -- "gibt es einen Cache-Stand, von wann".
 *       Produktion: {@code CachedIndexStore} (ETag-Cache-Rumpf, ohne Netz).</li>
 * </ul>
 *
 * <p>{@link #refresh()} meldet den Zustand an den {@link CatalogAvailability}-
 * Halter (S7) und legt die Katalog-Kacheln bereit:</p>
 * <ul>
 *   <li>{@code ONLINE} &rarr; {@code READY}, Kacheln aus der frischen Seite.</li>
 *   <li>{@code NO_NETWORK} mit Cache &rarr; {@code OFFLINE} + Standdatum,
 *       Kacheln aus dem Cache; ohne Cache &rarr; {@code OFFLINE_NO_CACHE},
 *       keine Katalog-Kacheln (lokale Pakete bleiben davon unberuehrt).</li>
 *   <li>{@code CATALOG_BROKEN} &rarr; {@code CATALOG_BROKEN}, keine Kacheln.</li>
 * </ul>
 * In jedem Fall wird nichts angewendet, kein Paket angefasst -- reines Lesen.
 * Ein toter Katalog legt den Store nie lahm: die lokale/installierte Reihe baut
 * {@code StoreScreen} unabhaengig von diesem Controller (Charter-Regel 12).
 *
 * <p>ROADMAP X2: nur bei {@code ONLINE} werden zusaetzlich die drei
 * kuratierten Reihen "Featured" ({@link #featuredTiles()}, aus
 * {@code featured.json}s {@code items[]} ueber {@link FeaturedSource}),
 * "Beliebt" ({@link #popularTiles()}, aus {@code featured.json}s
 * {@code popular[]} -- derselbe Abruf, eine eigenstaendige Liste) und "Neu"
 * ({@link #newTiles()}, aus derselben Index-Seite nach {@code updatedAt}
 * sortiert) befuellt -- siehe {@link CuratedHomeSections}. Scheitert nur der
 * {@code featured.json}-Abruf (Index-Seite aber okay), bleiben
 * {@link #featuredTiles()} und {@link #popularTiles()} leer statt den ganzen
 * Store als kaputt zu melden; {@code StoreScreen} laesst eine leere Reihe
 * dann einfach aus (wie bei der lokalen Bibliothek).</p>
 *
 * <p>ROADMAP GATE72: {@link #catalogTiles()} ist nicht auf die erste
 * Index-Seite begrenzt -- bei {@code ONLINE} laedt {@link #doRefresh()} ueber
 * {@link IndexSource#fetchPage(int)} alle weiteren Seiten bis
 * {@code totalPages} nach (Obergrenze {@link CatalogEndpoints#MAX_INDEX_PAGES_PER_REFRESH}).
 * Zusaetzlich fuellt {@link #searchTiles()} aus dem flachen Suchindex
 * ({@code search.json}, {@link SearchIndexSource}) -- das ist die Kachelmenge,
 * die {@code StoreScreen} der Live-Suche zugrunde legt (docs/UI-SPEC.md
 * "1. Kopfleiste": "Live-Treffer aus {@code search.json}"), unabhaengig davon,
 * wie viele Index-Seiten schon geladen sind. Scheitert nur der
 * {@code search.json}-Abruf, faellt {@link #searchTiles()} auf die bereits
 * geladenen {@link #catalogTiles()} zurueck statt leer zu bleiben.</p>
 */
public final class StoreCatalogController {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/StoreCatalog");

	/** Fehlende Verdrahtung fuer aeltere Konstruktoren ohne eigenen {@link FeaturedSource} (ROADMAP X2). */
	private static final FeaturedSource NO_FEATURED_SOURCE =
			() -> CatalogResult.catalogBroken("kein Featured-Quell konfiguriert");

	/**
	 * Fehlende Verdrahtung fuer aeltere Konstruktoren ohne eigenen
	 * {@link PackStateSource} (ROADMAP GATE46) -- "nichts installiert", damit
	 * bestehende Tests ohne {@code state.json}-Fake unveraendert weiterlaufen.
	 */
	private static final PackStateSource NO_PACK_STATE_SOURCE = PackState::createDefault;

	/**
	 * Fehlende Verdrahtung fuer aeltere Konstruktoren ohne eigenen
	 * {@link SearchIndexSource} (ROADMAP GATE72) -- {@link #searchTiles()}
	 * faellt dann einfach auf {@link #catalogTiles()} zurueck.
	 */
	private static final SearchIndexSource NO_SEARCH_INDEX_SOURCE =
			() -> CatalogResult.catalogBroken("kein Suchindex-Quell konfiguriert");

	private final IndexSource indexSource;
	private final CatalogCache cache;
	private final JavaModOfferGate javaModOfferGate;
	private final FeaturedSource featuredSource;
	private final PackStateSource packStateSource;
	private final SearchIndexSource searchIndexSource;
	private final InstalledVersionLookup installedVersionLookup;

	private volatile List<TileData> catalogTiles = List.of();
	private volatile List<TileData> featuredTiles = List.of();
	private volatile List<TileData> popularTiles = List.of();
	private volatile List<TileData> newTiles = List.of();
	private volatile List<TileData> searchTiles = List.of();

	/**
	 * Ohne eigene {@link JavaModOfferGate}: Klasse-B-Angebote werden immer
	 * ausgeblendet (sicherer Default, ROADMAP J1). Die Produktion nutzt
	 * {@link #production(Path)} mit einer an {@code settings.json} gebundenen
	 * Schleuse.
	 */
	public StoreCatalogController(IndexSource indexSource, CatalogCache cache) {
		this(indexSource, cache, JavaModOfferGate.hidden());
	}

	public StoreCatalogController(IndexSource indexSource, CatalogCache cache, JavaModOfferGate javaModOfferGate) {
		this(indexSource, cache, javaModOfferGate, NO_FEATURED_SOURCE);
	}

	public StoreCatalogController(IndexSource indexSource, CatalogCache cache, JavaModOfferGate javaModOfferGate,
			FeaturedSource featuredSource) {
		this(indexSource, cache, javaModOfferGate, featuredSource, NO_PACK_STATE_SOURCE);
	}

	/**
	 * ROADMAP GATE46: mit einer eigenen {@link PackStateSource} -- Katalog-
	 * Kacheln (Hauptreihe/Featured/Beliebt/Neu) werden damit gegen
	 * {@code state.json} abgeglichen, wie es {@code LocalLibrarySource} fuer
	 * die lokale Bibliothek schon tut.
	 */
	public StoreCatalogController(IndexSource indexSource, CatalogCache cache, JavaModOfferGate javaModOfferGate,
			FeaturedSource featuredSource, PackStateSource packStateSource) {
		this(indexSource, cache, javaModOfferGate, featuredSource, packStateSource, NO_SEARCH_INDEX_SOURCE);
	}

	/**
	 * ROADMAP GATE72: mit einer eigenen {@link SearchIndexSource} --
	 * {@link #searchTiles()} kommt damit aus {@code search.json} statt nur aus
	 * den bereits geladenen Index-Seiten.
	 */
	public StoreCatalogController(IndexSource indexSource, CatalogCache cache, JavaModOfferGate javaModOfferGate,
			FeaturedSource featuredSource, PackStateSource packStateSource, SearchIndexSource searchIndexSource) {
		this(indexSource, cache, javaModOfferGate, featuredSource, packStateSource, searchIndexSource,
				InstalledVersionLookup.NONE);
	}

	/**
	 * ROADMAP GATE80: mit einem eigenen {@link InstalledVersionLookup} -- eine
	 * installierte Katalog-Kachel (Hauptreihe/Featured/Beliebt/Neu) zeigt damit
	 * {@link de.spassglas.vitrine.ui.tile.TileStatus#UPDATE_AVAILABLE} statt nur
	 * {@code INSTALLED}, wenn die lokale Version aelter ist als {@link
	 * IndexPackage#latestVersion()} (siehe {@link IndexPackageTiles}).
	 */
	public StoreCatalogController(IndexSource indexSource, CatalogCache cache, JavaModOfferGate javaModOfferGate,
			FeaturedSource featuredSource, PackStateSource packStateSource, SearchIndexSource searchIndexSource,
			InstalledVersionLookup installedVersionLookup) {
		this.indexSource = Objects.requireNonNull(indexSource, "indexSource");
		this.cache = Objects.requireNonNull(cache, "cache");
		this.javaModOfferGate = Objects.requireNonNull(javaModOfferGate, "javaModOfferGate");
		this.featuredSource = Objects.requireNonNull(featuredSource, "featuredSource");
		this.packStateSource = Objects.requireNonNull(packStateSource, "packStateSource");
		this.searchIndexSource = Objects.requireNonNull(searchIndexSource, "searchIndexSource");
		this.installedVersionLookup = Objects.requireNonNull(installedVersionLookup, "installedVersionLookup");
	}

	/**
	 * Produktions-Verdrahtung. {@code vitrineConfigDir} reicht der Aufrufer
	 * ueber den ueblichen Fabric-Weg herein
	 * ({@code FabricLoader.getInstance().getConfigDir().resolve("vitrine")}) --
	 * kein absoluter Pfad im Code.
	 */
	public static StoreCatalogController production(Path vitrineConfigDir) {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		CatalogClient client = CatalogClient.production(vitrineConfigDir);
		CachedIndexStore cacheStore = CachedIndexStore.production(vitrineConfigDir);
		CatalogCache cacheSeam = () -> cacheStore.firstPage().flatMap(page ->
				cacheStore.firstPageAsOf().map(asOf -> new CatalogCache.Cached(page.packages(), asOf)));
		return new StoreCatalogController(client::fetchIndexPage, cacheSeam,
				JavaModOfferGate.forConfigDir(vitrineConfigDir), client::fetchFeatured,
				() -> PackStateStore.load(vitrineConfigDir), client::fetchSearchIndex,
				InstalledPackVersions.forConfigDir(vitrineConfigDir));
	}

	/**
	 * Holt den Katalog (blockierend) und aktualisiert Zustand + Kacheln. Wirft
	 * nie -- ein unerwarteter Fehler wird geloggt und als "Katalog defekt"
	 * gemeldet, damit der Store fuer lokale Pakete nutzbar bleibt.
	 */
	public void refresh() {
		try {
			doRefresh();
		} catch (RuntimeException e) {
			LOGGER.error("Katalog-Aktualisierung unerwartet gescheitert -- Store bleibt fuer lokale Pakete nutzbar: {}",
					e.toString(), e);
			catalogTiles = List.of();
			featuredTiles = List.of();
			popularTiles = List.of();
			newTiles = List.of();
			searchTiles = List.of();
			CatalogAvailability.report(CatalogAvailability.State.CATALOG_BROKEN, null);
		}
	}

	private void doRefresh() {
		CatalogResult<IndexPage> result = indexSource.fetchPage(1);
		// ROADMAP GATE46: ein frischer Zustand je Refresh -- derselbe Abgleich
		// wie LocalLibrarySource.scan(PackState), damit ein gerade installiertes
		// Katalog-Paket beim naechsten Aufbau als "Installiert" erscheint.
		PackState installedState = packStateSource.currentState();
		switch (result.availability()) {
			case ONLINE -> {
				// ROADMAP GATE72: nicht nur die erste Seite -- alle Seiten bis
				// totalPages (oder die Sicherheitsobergrenze), damit die
				// Katalogreihe/Suche nicht dauerhaft auf den ersten pageSize
				// Eintraegen stehen bleibt.
				IndexPage firstPage = result.value().orElseThrow();
				List<IndexPackage> packages = fetchAllPackages(firstPage);
				catalogTiles = toTiles(packages, installedState);
				// ROADMAP X2: nur bei ONLINE ueberhaupt kuratierte Reihen -- ein
				// gecachter/kaputter Katalog zeigt weiterhin nur die bestehende
				// S7-Reihe statt einer moeglicherweise veralteten Kuration.
				Featured featuredOrNull = fetchFeaturedOrNull();
				featuredTiles = CuratedHomeSections.featured(packages, featuredOrNull, javaModOfferGate, installedState,
						installedVersionLookup);
				popularTiles = CuratedHomeSections.popular(packages, featuredOrNull, javaModOfferGate, installedState,
						installedVersionLookup);
				newTiles = CuratedHomeSections.newest(packages, javaModOfferGate, installedState, installedVersionLookup);
				searchTiles = fetchSearchTilesOrFallback(installedState);
				CatalogAvailability.report(CatalogAvailability.State.READY, null);
				LOGGER.info("Katalog online: {} Pakete, {} Kacheln, {} featured, {} beliebt, {} neu, {} suchbar.",
						packages.size(), catalogTiles.size(), featuredTiles.size(), popularTiles.size(), newTiles.size(),
						searchTiles.size());
			}
			case NO_NETWORK -> onNoNetwork(result.message().orElse(""), installedState);
			case CATALOG_BROKEN -> onCatalogBroken(result.message().orElse("keine Meldung"), installedState);
		}
	}

	/**
	 * Laedt alle Index-Seiten ab Seite 1 ({@code firstPage}) bis
	 * {@code totalPages} nach (ROADMAP GATE72) -- hoechstens
	 * {@link CatalogEndpoints#MAX_INDEX_PAGES_PER_REFRESH} Seiten, auch wenn
	 * der Index mehr meldet (Sicherheitsobergrenze gegen einen kompromittierten
	 * oder kaputten Index-Bau). Scheitert eine spaetere Seite, bricht das
	 * Nachladen dort ab und liefert, was bis dahin geladen wurde -- die erste
	 * Seite war ja bereits {@code ONLINE}, ein Teilstand ist ehrlicher als ein
	 * kompletter Fehlschlag.
	 */
	private List<IndexPackage> fetchAllPackages(IndexPage firstPage) {
		List<IndexPackage> packages = new ArrayList<>(firstPage.packages());
		int totalPages = Math.max(firstPage.totalPages(), 1);
		int pagesToFetch = Math.min(totalPages, CatalogEndpoints.MAX_INDEX_PAGES_PER_REFRESH);
		if (totalPages > CatalogEndpoints.MAX_INDEX_PAGES_PER_REFRESH) {
			LOGGER.warn("Index meldet {} Seiten, mehr als die Sicherheitsobergrenze {} -- nur die ersten {} werden geladen.",
					totalPages, CatalogEndpoints.MAX_INDEX_PAGES_PER_REFRESH, pagesToFetch);
		}
		for (int pageNumber = 2; pageNumber <= pagesToFetch; pageNumber++) {
			CatalogResult<IndexPage> pageResult = indexSource.fetchPage(pageNumber);
			if (!pageResult.isOnline()) {
				LOGGER.warn("Katalog-Seite {} von {} liess sich nicht nachladen ({}) -- Katalog zeigt nur die "
								+ "bisher geladenen {} Pakete.",
						pageNumber, pagesToFetch, pageResult.message().orElse("keine Meldung"), packages.size());
				break;
			}
			packages.addAll(pageResult.value().orElseThrow().packages());
		}
		return packages;
	}

	/**
	 * GATE40: eine kaputte Katalogdatei auf dem Server darf den Store nicht
	 * leerfegen, wenn ein frueher geladener Stand im Cache liegt -- der wird
	 * dann gezeigt, mit Datum und ehrlich als "Katalog kaputt" beschriftet
	 * (nicht als "kein Netz"). Nur ohne Cache bleibt der Katalogbereich leer.
	 * Kuratierte Reihen gibt es in beiden Faellen nicht: die kommen nur aus
	 * einem frischen, heilen Abruf.
	 */
	private void onCatalogBroken(String message, PackState installedState) {
		featuredTiles = List.of();
		popularTiles = List.of();
		newTiles = List.of();
		searchTiles = List.of();
		Optional<CatalogCache.Cached> cached = cache.latest();
		if (cached.isPresent()) {
			catalogTiles = toTiles(cached.get().packages(), installedState);
			CatalogAvailability.report(CatalogAvailability.State.CATALOG_BROKEN_CACHED, cached.get().asOf());
			LOGGER.warn("Katalog defekt ({}) -- zeige den gespeicherten Stand von {} ({} Kacheln).",
					message, cached.get().asOf(), catalogTiles.size());
		} else {
			catalogTiles = List.of();
			CatalogAvailability.report(CatalogAvailability.State.CATALOG_BROKEN, null);
			LOGGER.warn("Katalog defekt ({}) und kein gespeicherter Stand -- lokale Pakete bleiben bedienbar.",
					message);
		}
	}

	/**
	 * Holt {@code featured.json} zusaetzlich zur Index-Seite -- Grundlage
	 * fuer sowohl die "Featured"- als auch die "Beliebt"-Reihe ({@code items[]}
	 * bzw. {@code popular[]}). Scheitert nur dieser Abruf (Netz weg,
	 * {@code featured.json} kaputt), waehrend die Index-Seite selbst okay war,
	 * bleibt das ein geloggter Nebenbefund -- kein Grund, den ganzen Katalog
	 * als kaputt zu melden (Charter-Regel 12).
	 */
	private Featured fetchFeaturedOrNull() {
		CatalogResult<Featured> result = featuredSource.fetchFeatured();
		if (result.isOnline()) {
			return result.value().orElseThrow();
		}
		LOGGER.info("Kuratierte Auswahl (featured.json) nicht verfuegbar ({}) -- Featured- und Beliebt-Reihe "
				+ "bleiben leer, der uebrige Katalog ist davon unberuehrt.", result.message().orElse("keine Meldung"));
		return null;
	}

	private void onNoNetwork(String message, PackState installedState) {
		Optional<CatalogCache.Cached> cached = cache.latest();
		if (cached.isPresent()) {
			catalogTiles = toTiles(cached.get().packages(), installedState);
			CatalogAvailability.report(CatalogAvailability.State.OFFLINE, cached.get().asOf());
			LOGGER.info("Kein Netz -- Katalog aus dem Cache (Stand {}), {} Kacheln. {}",
					cached.get().asOf(), catalogTiles.size(), message);
		} else {
			catalogTiles = List.of();
			CatalogAvailability.report(CatalogAvailability.State.OFFLINE_NO_CACHE, null);
			LOGGER.info("Kein Netz und kein Katalog-Cache -- nur lokale Pakete. {}", message);
		}
		// ROADMAP X2: ohne einen frischen ONLINE-Abruf gibt es keine kuratierten
		// Reihen -- auch nicht aus dem Cache (der cacht nur die Index-Seite, nicht
		// featured.json). Die bestehende S7-Reihe bleibt maszgeblich.
		featuredTiles = List.of();
		popularTiles = List.of();
		newTiles = List.of();
		searchTiles = List.of();
	}

	/**
	 * Die zuletzt geladenen Katalog-Kacheln (aus der frischen Seite oder dem
	 * Cache). Leer bei {@code CATALOG_BROKEN} und bei "kein Netz ohne Cache".
	 * Unveraenderliche Momentaufnahme.
	 */
	public List<TileData> catalogTiles() {
		return catalogTiles;
	}

	/**
	 * ROADMAP X2: die "Featured"-Reihe, sortiert nach {@code featured.json}-
	 * Reihenfolge. Nur bei {@code ONLINE} befuellt; leer, wenn
	 * {@code featured.json} nicht geladen werden konnte oder keine Eintraege
	 * hat -- {@code StoreScreen} laesst die Reihe dann einfach aus.
	 */
	public List<TileData> featuredTiles() {
		return featuredTiles;
	}

	/**
	 * ROADMAP X2: die "Beliebt"-Reihe, aus {@code featured.json}s
	 * {@code popular[]} (redaktionell kuratiert, keine gemessene Downloadzahl),
	 * sortiert nach dessen Reihenfolge. Nur bei {@code ONLINE} befuellt; leer,
	 * wenn {@code featured.json} nicht geladen werden konnte oder
	 * {@code popular[]} keine Eintraege hat.
	 */
	public List<TileData> popularTiles() {
		return popularTiles;
	}

	/**
	 * ROADMAP X2: die "Neu"-Reihe, sortiert nach {@code updatedAt} absteigend.
	 * Nur bei {@code ONLINE} befuellt.
	 */
	public List<TileData> newTiles() {
		return newTiles;
	}

	/**
	 * ROADMAP K1/GATE72: die Kachelmenge, die die Live-Suche durchsucht --
	 * aus dem flachen Suchindex ({@code search.json}), unabhaengig davon, wie
	 * viele Index-Seiten geladen sind. Nur bei {@code ONLINE} befuellt; faellt
	 * dort auf {@link #catalogTiles()} zurueck, wenn {@code search.json}
	 * selbst nicht geladen werden konnte (siehe {@link #fetchSearchTilesOrFallback}).
	 * Leer bei {@code NO_NETWORK}/{@code CATALOG_BROKEN} wie die anderen
	 * kuratierten Reihen.
	 */
	public List<TileData> searchTiles() {
		return searchTiles;
	}

	/**
	 * Holt {@code search.json} zusaetzlich zur Index-Seite (ROADMAP K1/GATE72)
	 * -- die Grundlage der Live-Suche (docs/UI-SPEC.md "1. Kopfleiste":
	 * "Live-Treffer aus {@code search.json}"). Scheitert nur dieser Abruf
	 * (Netz weg, {@code search.json} kaputt), waehrend die Index-Seite selbst
	 * okay war, faellt die Suche auf die bereits geladenen/nachgeladenen
	 * {@link #catalogTiles} zurueck statt leer zu bleiben -- ein geloggter
	 * Nebenbefund, kein Grund, den ganzen Katalog als kaputt zu melden
	 * (Charter-Regel 12).
	 */
	private List<TileData> fetchSearchTilesOrFallback(PackState installedState) {
		CatalogResult<SearchIndex> result = searchIndexSource.fetchSearchIndex();
		if (!result.isOnline()) {
			LOGGER.info("Suchindex (search.json) nicht verfuegbar ({}) -- die Suche faellt auf die geladenen "
							+ "Katalog-Seiten ({} Kacheln) zurueck.",
					result.message().orElse("keine Meldung"), catalogTiles.size());
			return catalogTiles;
		}
		SearchIndex index = result.value().orElseThrow();
		List<TileData> tiles = new ArrayList<>(index.entries().size());
		for (SearchIndexEntry entry : index.entries()) {
			SearchIndexTiles.toTile(entry, installedState).ifPresent(tiles::add);
		}
		// ROADMAP J1: dieselbe Schleuse wie fuer die Katalogreihe -- auch wenn
		// search.json heute kein javaMod-Feld kennt (jeder Treffer ist Klasse A),
		// bleibt der Aufruf hier die eine Wahrheit statt einer zweiten Annahme.
		return javaModOfferGate.apply(List.copyOf(tiles));
	}

	private List<TileData> toTiles(List<IndexPackage> packages, PackState installedState) {
		List<TileData> tiles = new ArrayList<>(packages.size());
		for (IndexPackage pkg : packages) {
			// ROADMAP GATE80: derselbe Versionsabgleich wie fuer die kuratierten
			// Reihen -- eine installierte Katalog-Kachel zeigt UPDATE_AVAILABLE statt
			// nur INSTALLED, wenn die lokale Version aelter ist.
			IndexPackageTiles.toTile(pkg, installedState, installedVersionLookup).ifPresent(tiles::add);
		}
		// ROADMAP J1: ohne den Opt-in-Schalter faellt hier jeder Klasse-B-Eintrag
		// wieder heraus -- kein Jar in der Katalogreihe.
		return javaModOfferGate.apply(List.copyOf(tiles));
	}
}
