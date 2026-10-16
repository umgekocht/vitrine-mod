package de.spassglas.vitrine.catalog.github;

import de.spassglas.vitrine.catalog.EtagCache;
import de.spassglas.vitrine.catalog.HttpCatalogClient;
import de.spassglas.vitrine.catalog.Transport;
import de.spassglas.vitrine.catalog.TransportResponse;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE70, docs/UI-SPEC.md:118-120: "Sterne/Downloads aus der
 * GitHub-API (read-only)". Deckt das woertliche Fertig-Kriterium ab: ein
 * echter, ehrlicher Leer-/Fehlerzustand bei fehlendem Netz oder einem nicht
 * erkennbaren Repo, nie eine Exception bis zur Oberflaeche.
 */
public final class GitHubRepoStatsClientTest {

	private Path cacheDir;

	@BeforeMethod
	public void setUp() throws IOException {
		cacheDir = Files.createTempDirectory("vitrine-github-stats-test-");
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

	private GitHubRepoStatsClient client(FakeTransport transport) {
		return new GitHubRepoStatsClient(
				new HttpCatalogClient(transport, new EtagCache(cacheDir), GitHubRepoStatsHostWhitelist.PRODUCTION));
	}

	@Test
	public void unknownRepoMakesNoRequestAtAll() {
		FakeTransport transport = new FakeTransport();
		GitHubRepoStats stats = client(transport).fetch("https://gitlab.com/mira/aurora-hud");
		assertEquals(stats, GitHubRepoStats.UNKNOWN);
		assertEquals(transport.requestCount(), 0, "kein erkennbares Repo -> kein Request");
	}

	@Test
	public void nullRepositoryUrlMakesNoRequestAtAll() {
		FakeTransport transport = new FakeTransport();
		GitHubRepoStats stats = client(transport).fetch(null);
		assertEquals(stats, GitHubRepoStats.UNKNOWN);
		assertEquals(transport.requestCount(), 0);
	}

	@Test
	public void successfulResponsesParseStarsAndSumDownloadsAcrossReleases() {
		FakeTransport transport = new FakeTransport()
				.enqueueResponse(200, "{\"stargazers_count\": 42}")
				.enqueueResponse(200, "[{\"assets\":[{\"download_count\":10},{\"download_count\":5}]},"
						+ "{\"assets\":[{\"download_count\":7}]}]");

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertEquals(stats.stargazerCount().orElseThrow(), Long.valueOf(42));
		assertEquals(stats.downloadCount().orElseThrow(), Long.valueOf(22));
		assertEquals(transport.requestCount(), 2);
		assertTrue(transport.requestedUris().get(0).toString().endsWith("/repos/mira/aurora-hud"));
		assertTrue(transport.requestedUris().get(1).toString().endsWith("/repos/mira/aurora-hud/releases"));
		assertEquals(transport.headersOfRequest(0).get("User-Agent"), "Vitrine-Minecraft-Mod",
				"GitHub verlangt einen User-Agent, sonst HTTP 403");
	}

	@Test
	public void repoWithoutAnyReleasesYetReportsZeroDownloadsNotUnknown() {
		FakeTransport transport = new FakeTransport()
				.enqueueResponse(200, "{\"stargazers_count\": 3}")
				.enqueueResponse(200, "[]");

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertEquals(stats.stargazerCount().orElseThrow(), Long.valueOf(3));
		assertEquals(stats.downloadCount().orElseThrow(), Long.valueOf(0),
				"ein bekanntes Repo ohne Releases hat 0 Downloads -- ein echter, bekannter Wert");
	}

	@Test
	public void repoNotFoundLeavesStarsUnknownButDownloadsStillIndependentlyKnown() {
		FakeTransport transport = new FakeTransport()
				.enqueueResponse(404, "{\"message\":\"Not Found\"}")
				.enqueueResponse(200, "[{\"assets\":[{\"download_count\":1}]}]");

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertTrue(stats.stargazerCount().isEmpty(), "404 -> Sterne unbekannt");
		assertEquals(stats.downloadCount().orElseThrow(), Long.valueOf(1),
				"ein Fehlschlag bei den Sternen darf die unabhaengig gelesenen Downloads nicht wegwerfen");
	}

	@Test
	public void malformedJsonBecomesUnknownNotACrash() {
		FakeTransport transport = new FakeTransport()
				.enqueueResponse(200, "not json at all")
				.enqueueResponse(200, "also not json");

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertTrue(stats.stargazerCount().isEmpty());
		assertTrue(stats.downloadCount().isEmpty());
	}

	@Test
	public void noNetworkBecomesUnknownNotACrash() {
		FakeTransport transport = new FakeTransport()
				.enqueueFailure(new IOException("connection reset"))
				.enqueueFailure(new IOException("connection reset"));

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertEquals(stats, GitHubRepoStats.UNKNOWN);
	}

	@Test
	public void missingStargazersFieldBecomesUnknown() {
		FakeTransport transport = new FakeTransport()
				.enqueueResponse(200, "{}")
				.enqueueResponse(200, "[]");

		GitHubRepoStats stats = client(transport).fetch("https://github.com/mira/aurora-hud");

		assertFalse(stats.stargazerCount().isPresent());
		assertEquals(stats.downloadCount().orElseThrow(), Long.valueOf(0));
	}

	/** Schlankes Test-{@link Transport}: gibt vorab eingereihte Antworten/Fehler in Reihenfolge zurueck. */
	private static final class FakeTransport implements Transport {

		private interface Handler {
			TransportResponse handle() throws IOException;
		}

		private final Deque<Handler> handlers = new ArrayDeque<>();
		private final List<URI> requestedUris = new ArrayList<>();
		private final List<Map<String, String>> capturedHeaders = new ArrayList<>();

		FakeTransport enqueueResponse(int status, String jsonBody) {
			byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
			handlers.add(() -> new TransportResponse(status, Map.of(), body));
			return this;
		}

		FakeTransport enqueueFailure(IOException failure) {
			handlers.add(() -> {
				throw failure;
			});
			return this;
		}

		@Override
		public TransportResponse get(URI uri, Map<String, String> requestHeaders) throws IOException {
			requestedUris.add(uri);
			capturedHeaders.add(new HashMap<>(requestHeaders));
			Handler handler = handlers.poll();
			if (handler == null) {
				throw new AssertionError("FakeTransport: unerwarteter Request an " + uri);
			}
			return handler.handle();
		}

		int requestCount() {
			return requestedUris.size();
		}

		List<URI> requestedUris() {
			return requestedUris;
		}

		Map<String, String> headersOfRequest(int index) {
			return capturedHeaders.get(index);
		}
	}
}
