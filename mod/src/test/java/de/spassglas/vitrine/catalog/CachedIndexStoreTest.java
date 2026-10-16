package de.spassglas.vitrine.catalog;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP K4: {@link CachedIndexStore} liest die zuletzt gespeicherte erste
 * Index-Seite ohne Netz aus dem {@link EtagCache} -- geparst, schema-geprueft,
 * mit dem Datei-Aenderungsdatum als "Stand von". Nie eine Exception: ohne oder
 * mit kaputtem Cache kommt schlicht {@code empty} zurueck.
 */
public final class CachedIndexStoreTest {

	private Path cacheDir;

	@BeforeMethod
	public void setUp() throws IOException {
		cacheDir = Files.createTempDirectory("vitrine-cached-index-test-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (cacheDir == null || !Files.exists(cacheDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(cacheDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	@Test
	public void readsAndSchemaChecksTheCachedFirstPage() {
		EtagCache cache = new EtagCache(cacheDir);
		cache.store(CatalogClient.indexCacheKey(1), CatalogTestSupport.validIndexPageBytes(), "\"rev-1\"", null);

		CachedIndexStore store = new CachedIndexStore(cache);

		IndexPage page = store.firstPage().orElseThrow();
		assertEquals(page.packages().size(), 2);
		assertEquals(page.packages().get(0).id(), "umgekocht.klarglas");

		LocalDate asOf = store.firstPageAsOf().orElseThrow();
		assertFalse(asOf.isAfter(LocalDate.now()), "Stand liegt nicht in der Zukunft");
		assertFalse(asOf.isBefore(LocalDate.now().minusDays(1)), "Stand ist die eben geschriebene Datei");
	}

	@Test
	public void missingCacheYieldsEmptyWithoutError() {
		CachedIndexStore store = new CachedIndexStore(new EtagCache(cacheDir.resolve("existiert-nicht")));

		assertTrue(store.firstPage().isEmpty());
		assertTrue(store.firstPageAsOf().isEmpty());
	}

	@Test
	public void corruptCacheBodyIsTreatedAsMissing() {
		EtagCache cache = new EtagCache(cacheDir);
		cache.store(CatalogClient.indexCacheKey(1), "kein json".getBytes(StandardCharsets.UTF_8), null, null);

		assertTrue(new CachedIndexStore(cache).firstPage().isEmpty());
	}

	@Test
	public void schemaViolatingCacheBodyIsTreatedAsMissing() {
		EtagCache cache = new EtagCache(cacheDir);
		cache.store(CatalogClient.indexCacheKey(1),
				CatalogTestSupport.VALID_INDEX_PAGE_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")
						.getBytes(StandardCharsets.UTF_8), null, null);

		assertTrue(new CachedIndexStore(cache).firstPage().isEmpty());
	}
}
