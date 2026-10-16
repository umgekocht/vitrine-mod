package de.spassglas.vitrine.catalog;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Weiterleitungs-, Whitelist-, Timeout-, Groessen- und ETag-Verhalten der
 * mittleren Schicht (ROADMAP K2). Deckt das woertliche Fertig-Kriterium ab:
 * "keine Weiterleitung auf fremde Hosts wird gefolgt".
 */
public final class HttpCatalogClientTest {

	private static final URI INDEX_URI = CatalogEndpoints.DEFAULT_INDEX_URL;

	private Path cacheDir;
	private EtagCache cache;

	@BeforeMethod
	public void setUp() throws IOException {
		cacheDir = Files.createTempDirectory("vitrine-http-catalog-test-");
		cache = new EtagCache(cacheDir);
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (cacheDir == null || !Files.exists(cacheDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(cacheDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	private HttpCatalogClient client(RecordingTransport transport) {
		return new HttpCatalogClient(transport, cache, HostWhitelist.PRODUCTION);
	}

	@Test
	public void redirectToForeignHostIsRejectedAndNotFollowed() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(302, null, "Location", "https://evil.example/catalog/index.json");

		CatalogBrokenException error = expectThrows(CatalogBrokenException.class,
				() -> client(transport).fetch(INDEX_URI, "index-v1-p1"));
		assertTrue(error.getMessage().contains("evil.example"));

		assertEquals(transport.requestCount(), 1, "kein zweiter Request an den fremden Host");
		assertEquals(transport.requestedUris().get(0), INDEX_URI);
	}

	@Test
	public void nonWhitelistHostIsRejectedBeforeAnyRequest() {
		RecordingTransport transport = new RecordingTransport();

		assertThrows(CatalogBrokenException.class,
				() -> client(transport).fetch(URI.create("https://evil.example/index.json"), "k"));
		assertEquals(transport.requestCount(), 0, "Whitelist-Verstoss darf keinen Request ausloesen");
	}

	@Test
	public void redirectWithinWhitelistedHostIsFollowed() throws Exception {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(301, null, "Location", "index-2.json")
				.enqueueResponse(200, CatalogTestSupport.validIndexPageBytes());

		byte[] body = client(transport).fetch(INDEX_URI, "index-v1-p1");

		assertEquals(new String(body, java.nio.charset.StandardCharsets.UTF_8),
				CatalogTestSupport.VALID_INDEX_PAGE_JSON);
		assertEquals(transport.requestCount(), 2);
		assertTrue(transport.requestedUris().get(1).toString().endsWith("/index/v1/index-2.json"));
	}

	@Test
	public void ioExceptionBecomesUnavailable() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new IOException("connection reset"));
		assertThrows(CatalogUnavailableException.class,
				() -> client(transport).fetch(INDEX_URI, "k"));
	}

	@Test
	public void timeoutBecomesUnavailable() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new HttpTimeoutException("request timed out"));
		assertThrows(CatalogUnavailableException.class,
				() -> client(transport).fetch(INDEX_URI, "k"));
	}

	@Test
	public void oversizedResponseBecomesBroken() {
		RecordingTransport transport = new RecordingTransport()
				.enqueueFailure(new ResponseTooLargeException("body over limit"));
		assertThrows(CatalogBrokenException.class,
				() -> client(transport).fetch(INDEX_URI, "k"));
	}

	@Test
	public void httpErrorStatusBecomesBroken() {
		RecordingTransport transport = new RecordingTransport().enqueueResponse(503, null);
		CatalogBrokenException error = expectThrows(CatalogBrokenException.class,
				() -> client(transport).fetch(INDEX_URI, "k"));
		assertTrue(error.getMessage().contains("503"));
	}

	@Test
	public void okResponseStoresBodyAndEtagInCache() throws Exception {
		RecordingTransport transport = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validIndexPageBytes(), "ETag", "\"rev-1\"");

		byte[] body = client(transport).fetch(INDEX_URI, "index-v1-p1");
		assertEquals(new String(body, java.nio.charset.StandardCharsets.UTF_8),
				CatalogTestSupport.VALID_INDEX_PAGE_JSON);

		EtagCache.CacheEntry cached = cache.load("index-v1-p1").orElseThrow();
		assertEquals(cached.etag(), "\"rev-1\"");
		assertEquals(cached.body(), CatalogTestSupport.validIndexPageBytes());
	}

	@Test
	public void secondFetchSendsIfNoneMatchAndUses304Cache() throws Exception {
		RecordingTransport first = new RecordingTransport()
				.enqueueResponse(200, CatalogTestSupport.validIndexPageBytes(), "ETag", "\"rev-1\"");
		byte[] firstBody = client(first).fetch(INDEX_URI, "index-v1-p1");
		assertFalse(first.headersOfRequest(0).containsKey("If-None-Match"),
				"erster Abruf schickt noch kein If-None-Match");

		RecordingTransport second = new RecordingTransport().enqueueResponse(304, null);
		byte[] secondBody = client(second).fetch(INDEX_URI, "index-v1-p1");

		assertEquals(second.headersOfRequest(0).get("If-None-Match"), "\"rev-1\"");
		assertEquals(secondBody, firstBody, "304 liefert den identischen Rumpf aus dem Cache");
	}

	@Test
	public void notModifiedWithoutCacheBecomesBroken() {
		RecordingTransport transport = new RecordingTransport().enqueueResponse(304, null);
		assertThrows(CatalogBrokenException.class,
				() -> client(transport).fetch(INDEX_URI, "index-v1-p1"));
	}
}
