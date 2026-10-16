package de.spassglas.vitrine.catalog;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Fertig-Kriterium K2: "Test mit lokalem Datei-URL und simulierten Fehlern;
 * keine Weiterleitung auf fremde Hosts wird gefolgt." Prueft den High-Level-
 * {@link CatalogClient} auf typisierte Ergebnisse statt Exceptions.
 */
public final class CatalogClientTest {

	private static final Predicate<URI> ALLOW_ANY = uri -> true;

	private Path workDir;
	private Path cacheDir;

	@BeforeMethod
	public void setUp() throws IOException {
		workDir = Files.createTempDirectory("vitrine-catalog-client-test-");
		cacheDir = workDir.resolve("cache");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (workDir == null || !Files.exists(workDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(workDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	private CatalogClient fileClient(Path indexFile) {
		HttpCatalogClient http = new HttpCatalogClient(new FileUriTransport(), new EtagCache(cacheDir), ALLOW_ANY);
		return new CatalogClient(http, indexFile.toUri());
	}

	private CatalogClient transportClient(RecordingTransport transport, Predicate<URI> hostAllowed) {
		HttpCatalogClient http = new HttpCatalogClient(transport, new EtagCache(cacheDir), hostAllowed);
		return new CatalogClient(http, CatalogEndpoints.DEFAULT_INDEX_URL);
	}

	@Test
	public void loadsAndParsesIndexPageFromFileUri() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);

		CatalogResult<IndexPage> result = fileClient(indexFile).fetchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		IndexPage page = result.value().orElseThrow();
		assertEquals(page.schemaVersion(), 1);
		assertEquals(page.totalCount(), 2);
		assertEquals(page.packages().size(), 2);
		IndexPackage first = page.packages().get(0);
		assertEquals(first.id(), "umgekocht.klarglas");
		assertEquals(first.category(), "theme");
		assertEquals(first.latestVersion(), "1.0.0");
		assertEquals(page.packages().get(1).icon(), null, "icon ist optional und fehlt beim zweiten Paket");
	}

	@Test
	public void invalidJsonFromFileBecomesCatalogBroken() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, "das ist kein json", StandardCharsets.UTF_8);

		CatalogResult<IndexPage> result = fileClient(indexFile).fetchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertTrue(result.message().orElseThrow().contains("JSON"));
	}

	@Test
	public void schemaViolationBecomesCatalogBrokenWithDistinctMessage() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile,
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
				StandardCharsets.UTF_8);

		CatalogResult<IndexPage> result = fileClient(indexFile).fetchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		String message = result.message().orElseThrow();
		assertTrue(message.contains("Schema"), message);
		assertTrue(message.contains("schemaVersion"), message);
	}

	@Test
	public void networkErrorBecomesNoNetwork() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new IOException("no route to host"));

		CatalogResult<IndexPage> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.NO_NETWORK);
	}

	@Test
	public void foreignRedirectBecomesCatalogBrokenAndIsNotFollowed() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(302, null, "Location", "https://evil.example/x.json");

		CatalogResult<IndexPage> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertEquals(transport.requestCount(), 1);
	}

	@Test
	public void etagRoundTripAcrossTwoFetchesParsesIdentically() {
		EtagCache sharedCache = new EtagCache(cacheDir);

		RecordingTransport first = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validIndexPageBytes(), "ETag", "\"rev-1\"");
		CatalogClient client1 = new CatalogClient(
				new HttpCatalogClient(first, sharedCache, HostWhitelist.PRODUCTION),
				CatalogEndpoints.DEFAULT_INDEX_URL);
		CatalogResult<IndexPage> firstResult = client1.fetchIndex();

		RecordingTransport second = new RecordingTransport().enqueueResponse(304, null);
		CatalogClient client2 = new CatalogClient(
				new HttpCatalogClient(second, sharedCache, HostWhitelist.PRODUCTION),
				CatalogEndpoints.DEFAULT_INDEX_URL);
		CatalogResult<IndexPage> secondResult = client2.fetchIndex();

		assertEquals(second.headersOfRequest(0).get("If-None-Match"), "\"rev-1\"");
		assertEquals(firstResult.availability(), CatalogResult.Availability.ONLINE);
		assertEquals(secondResult.availability(), CatalogResult.Availability.ONLINE);
		assertEquals(secondResult.value().orElseThrow().packages(),
				firstResult.value().orElseThrow().packages(),
				"304 aus dem Cache ergibt dasselbe geparste Modell");
	}

	@Test
	public void secondIndexPageUsesSiblingUrl() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validIndexPageBytes());

		CatalogResult<IndexPage> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchIndexPage(2);

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		assertTrue(transport.requestedUris().get(0).toString().endsWith("/index/v1/index-2.json"));
	}

	@Test
	public void invalidPageNumberIsCatalogBrokenWithoutRequest() {
		RecordingTransport transport = new RecordingTransport();

		CatalogResult<IndexPage> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchIndexPage(0);

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertEquals(transport.requestCount(), 0);
	}

	// --- ROADMAP X2: featured.json --------------------------------------

	@Test
	public void loadsAndParsesFeaturedFromFileUriSortedByOrder() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("featured.json"), CatalogTestSupport.VALID_FEATURED_JSON,
				StandardCharsets.UTF_8);

		CatalogResult<Featured> result = fileClient(indexFile).fetchFeatured();

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		Featured featured = result.value().orElseThrow();
		assertEquals(featured.schemaVersion(), 1);
		assertEquals(featured.items().size(), 2);
		// Die Datei selbst ist absichtlich nicht sortiert -- das Modell gibt
		// unveraendert wieder, was drinsteht; Sortierung passiert erst beim
		// Uebersetzen in Kacheln (CuratedHomeSections).
		assertEquals(featured.items().get(0).id(), "umgekocht.pixelscharf");
		assertEquals(featured.items().get(0).order(), 1);
		assertEquals(featured.items().get(1).id(), "umgekocht.klarglas");
		assertEquals(featured.items().get(1).note(), "Cleanes Standard-Theme");
	}

	@Test
	public void loadsAndParsesPopularSeparatelyFromItems() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("featured.json"), CatalogTestSupport.VALID_FEATURED_JSON,
				StandardCharsets.UTF_8);

		Featured featured = fileClient(indexFile).fetchFeatured().value().orElseThrow();

		// ROADMAP X2: "popular" (Beliebt) ist eine eigenstaendige Liste --
		// umgekocht.pixelscharf steht nur in "items" (Featured), nicht in
		// "popular" (Beliebt); umgekocht.klarglas steht in beiden.
		assertEquals(featured.popular().size(), 1);
		assertEquals(featured.popular().get(0).id(), "umgekocht.klarglas");
		assertEquals(featured.popular().get(0).note(), "Meistgenutzt");
		assertTrue(featured.popular().stream().noneMatch(item -> item.id().equals("umgekocht.pixelscharf")),
				"pixelscharf ist featured, aber nicht beliebt-kuratiert");
	}

	@Test
	public void featuredResolvesAsSiblingOfTheIndexUrl() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validFeaturedBytes());

		CatalogResult<Featured> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchFeatured();

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		assertEquals(transport.requestedUris().get(0), CatalogEndpoints.DEFAULT_FEATURED_URL,
				"featured.json wird als Geschwisterdatei von index.json aufgeloest");
	}

	@Test
	public void invalidFeaturedJsonBecomesCatalogBroken() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("featured.json"), "das ist kein json", StandardCharsets.UTF_8);

		CatalogResult<Featured> result = fileClient(indexFile).fetchFeatured();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertTrue(result.message().orElseThrow().contains("JSON"));
	}

	@Test
	public void featuredSchemaViolationBecomesCatalogBroken() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("featured.json"),
				CatalogTestSupport.VALID_FEATURED_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
				StandardCharsets.UTF_8);

		CatalogResult<Featured> result = fileClient(indexFile).fetchFeatured();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		String message = result.message().orElseThrow();
		assertTrue(message.contains("Schema"), message);
		assertTrue(message.contains("schemaVersion"), message);
	}

	@Test
	public void featuredNetworkErrorBecomesNoNetwork() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new IOException("no route to host"));

		CatalogResult<Featured> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchFeatured();

		assertEquals(result.availability(), CatalogResult.Availability.NO_NETWORK);
	}

	// --- ROADMAP K1/GATE72: search.json ---------------------------------

	@Test
	public void loadsAndParsesSearchIndexFromFileUri() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("search.json"), CatalogTestSupport.VALID_SEARCH_INDEX_JSON,
				StandardCharsets.UTF_8);

		CatalogResult<SearchIndex> result = fileClient(indexFile).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		SearchIndex index = result.value().orElseThrow();
		assertEquals(index.schemaVersion(), 1);
		assertEquals(index.totalCount(), 2);
		assertEquals(index.entries().size(), 2);
		assertEquals(index.entries().get(0).id(), "umgekocht.klarglas");
		// ROADMAP GATE72: dieser Eintrag existiert in keiner Index-Seite des
		// Testgeruests -- er kommt beweisbar nur aus search.json.
		assertEquals(index.entries().get(1).id(), "umgekocht.nurimsuchindex");
	}

	@Test
	public void searchIndexResolvesAsSiblingOfTheIndexUrl() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validSearchIndexBytes());

		CatalogResult<SearchIndex> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		assertEquals(transport.requestedUris().get(0), CatalogEndpoints.DEFAULT_SEARCH_URL,
				"search.json wird als Geschwisterdatei von index.json aufgeloest");
	}

	@Test
	public void invalidSearchIndexJsonBecomesCatalogBroken() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("search.json"), "das ist kein json", StandardCharsets.UTF_8);

		CatalogResult<SearchIndex> result = fileClient(indexFile).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertTrue(result.message().orElseThrow().contains("JSON"));
	}

	@Test
	public void searchIndexSchemaViolationBecomesCatalogBroken() throws IOException {
		Path indexFile = workDir.resolve("index.json");
		Files.writeString(indexFile, CatalogTestSupport.VALID_INDEX_PAGE_JSON, StandardCharsets.UTF_8);
		Files.writeString(workDir.resolve("search.json"),
				CatalogTestSupport.VALID_SEARCH_INDEX_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
				StandardCharsets.UTF_8);

		CatalogResult<SearchIndex> result = fileClient(indexFile).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		String message = result.message().orElseThrow();
		assertTrue(message.contains("Schema"), message);
		assertTrue(message.contains("schemaVersion"), message);
	}

	@Test
	public void searchIndexNetworkErrorBecomesNoNetwork() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new IOException("no route to host"));

		CatalogResult<SearchIndex> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.NO_NETWORK);
	}

	@Test
	public void searchIndexForeignRedirectBecomesCatalogBrokenAndIsNotFollowed() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(302, null, "Location", "https://evil.example/search.json");

		CatalogResult<SearchIndex> result = transportClient(transport, HostWhitelist.PRODUCTION).fetchSearchIndex();

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertEquals(transport.requestCount(), 1);
	}
}
