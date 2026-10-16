package de.spassglas.vitrine.catalog;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * ETag-Cache in {@code config/vitrine/cache/} (ROADMAP K2): atomarer
 * Roundtrip, und eine kaputte oder unvollstaendige Cache-Datei wird wie ein
 * fehlender Eintrag behandelt -- nie ein Crash.
 */
public final class EtagCacheTest {

	private Path cacheDir;
	private EtagCache cache;

	@BeforeMethod
	public void setUp() throws IOException {
		cacheDir = Files.createTempDirectory("vitrine-etag-cache-test-");
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

	@Test
	public void storeThenLoadRoundTrips() {
		byte[] body = "{\"page\":1}".getBytes(StandardCharsets.UTF_8);
		cache.store("index-v1-p1", body, "\"etag-abc\"", "Wed, 10 Sep 2026 12:00:00 GMT");

		Optional<EtagCache.CacheEntry> loaded = cache.load("index-v1-p1");
		assertTrue(loaded.isPresent());
		assertEquals(loaded.get().body(), body);
		assertEquals(loaded.get().etag(), "\"etag-abc\"");
		assertEquals(loaded.get().lastModified(), "Wed, 10 Sep 2026 12:00:00 GMT");
	}

	@Test
	public void loadOfMissingEntryReturnsEmpty() {
		assertFalse(cache.load("noch-nie-geschrieben").isPresent());
	}

	@Test
	public void nullEtagRoundTripsAsNull() {
		cache.store("k", new byte[] {1, 2, 3}, null, null);
		Optional<EtagCache.CacheEntry> loaded = cache.load("k");
		assertTrue(loaded.isPresent());
		assertNull(loaded.get().etag());
		assertNull(loaded.get().lastModified());
	}

	@Test
	public void brokenMetaFileIsTreatedAsMissing() throws IOException {
		cache.store("k", new byte[] {1}, "\"e\"", null);
		Files.writeString(cacheDir.resolve("k.meta.json"), "{ das ist kein json", StandardCharsets.UTF_8);
		assertFalse(cache.load("k").isPresent());
	}

	@Test
	public void bodyWithoutMetaIsTreatedAsMissing() throws IOException {
		Files.write(cacheDir.resolve("k.body"), new byte[] {1, 2});
		assertFalse(cache.load("k").isPresent());
	}

	@Test
	public void storeOverwritesPreviousEntry() {
		cache.store("k", "alt".getBytes(StandardCharsets.UTF_8), "\"v1\"", null);
		cache.store("k", "neu".getBytes(StandardCharsets.UTF_8), "\"v2\"", null);
		EtagCache.CacheEntry loaded = cache.load("k").orElseThrow();
		assertEquals(new String(loaded.body(), StandardCharsets.UTF_8), "neu");
		assertEquals(loaded.etag(), "\"v2\"");
	}

	@Test
	public void sanitizeReplacesPathSeparatorsAndRejectsTraversal() {
		assertEquals(EtagCache.sanitize("index/v1/../p1"), "index_v1_.._p1");
		assertThrows(IllegalArgumentException.class, () -> EtagCache.sanitize(".."));
		assertThrows(IllegalArgumentException.class, () -> EtagCache.sanitize(""));
	}
}
