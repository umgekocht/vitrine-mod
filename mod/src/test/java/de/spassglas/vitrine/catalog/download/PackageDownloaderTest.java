package de.spassglas.vitrine.catalog.download;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt ROADMAP K3 ab: Download mit Fortschritt, Abbruch, Wiederaufnahme,
 * {@code .part}-Datei, atomarem Umbenennen und Verifikation danach.
 *
 * <p>Der wichtigste Test ist {@link #cancelMidStreamLeavesNoPartAndNoInstalledFile()}
 * -- er belegt das Fertig-Kriterium der Zeile ("Abbruch mitten im Download
 * hinterlaesst keine halbe Installation").</p>
 */
public final class PackageDownloaderTest {

	private static final Predicate<URI> ALLOW_ANY = uri -> true;
	private static final URI FAKE_URL =
			URI.create("https://github.com/umgekocht/vitrine-registry/releases/download/v1/paket.vpkg");

	private Path tempDir;
	private Path target;
	private Path part;
	private KeyPair signer;
	private KeyPair attacker;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-k3-download-test-");
		target = tempDir.resolve("packs").resolve("paket.vpkg");
		part = target.resolveSibling("paket.vpkg" + DownloadEndpoints.PART_SUFFIX);
		signer = VpkgFixtures.generateEd25519KeyPair();
		attacker = VpkgFixtures.generateEd25519KeyPair();
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (var paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	// --- 1. Happy Path ueber FileUriDownloadTransport --------------------

	@Test
	public void happyPathDownloadsAndVerifiesRealSignedPackage() throws Exception {
		byte[] pkg = VpkgFixtures.minimalValidPackage();
		Path source = tempDir.resolve("quelle.vpkg");
		Files.write(source, pkg);

		DownloadRequest request = requestFor(pkg, source.toUri());
		PackageDownloader downloader = PackageDownloader.forTesting(
				new FileUriDownloadTransport(), ALLOW_ANY, signer.getPublic());

		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP);

		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		assertFalse(outcome.alreadyPresent(), "frisch heruntergeladen, nicht schon vorhanden");
		assertFalse(Files.exists(part), ".part muss nach Erfolg weg sein");
		assertTrue(Files.exists(target), "Zieldatei muss existieren");
		assertEquals(Files.readAllBytes(target), pkg, "Zieldatei muss byte-identisch zur Quelle sein");
	}

	// --- 2. Abbruch mitten im Download (Fertig-Kriterium) ---------------

	@Test
	public void cancelMidStreamLeavesNoPartAndNoInstalledFile() throws Exception {
		byte[] payload = randomBytes(256 * 1024);
		FakeDownloadTransport.GatedBody body = new FakeDownloadTransport.GatedBody(payload, 8 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueue((uri, offset) ->
				FakeDownloadTransport.response(200, body, "content-length", Integer.toString(payload.length)));

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		CancellationToken token = new CancellationToken();
		DownloadRequest request = requestFor(payload, FAKE_URL);

		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<DownloadOutcome> future = pool.submit(
					() -> downloader.download(request, target, token, DownloadProgress.NOOP));

			body.awaitGateReached();                 // ein paar KiB sind durch
			assertTrue(Files.exists(part), ".part liegt waehrend des Transfers vor");
			assertFalse(Files.exists(target), "Zieldatei darf waehrend des Transfers NICHT existieren");

			token.cancel();                          // Abbruch aus einem anderen Thread

			DownloadOutcome outcome = future.get(10, TimeUnit.SECONDS);
			assertEquals(outcome.status(), DownloadOutcome.Status.CANCELLED);
			assertFalse(Files.exists(part), "nach Abbruch darf KEINE .part zurueckbleiben");
			assertFalse(Files.exists(target), "nach Abbruch darf KEINE fertige Datei zurueckbleiben");
		} finally {
			pool.shutdownNow();
		}
	}

	// --- 3. Resume nach Netz-Abbruch ----------------------------------

	@Test
	public void networkFailureKeepsPartAndSecondCallResumesFromOffset() throws Exception {
		byte[] payload = randomBytes(256 * 1024);
		int deliveredBeforeBreak = 4096;

		FakeDownloadTransport transport = new FakeDownloadTransport()
				.enqueue((uri, offset) -> FakeDownloadTransport.response(200,
						new FakeDownloadTransport.ThrowingBody(payload, deliveredBeforeBreak),
						"content-length", Integer.toString(payload.length)))
				.enqueue206(payload, deliveredBeforeBreak);

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		DownloadException first = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(first.kind(), DownloadException.Kind.NETWORK);
		assertTrue(Files.exists(part), ".part bleibt nach Netz-Abbruch fuer die Wiederaufnahme liegen");
		assertEquals(Files.size(part), deliveredBeforeBreak, ".part enthaelt die bereits empfangenen Bytes");

		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP);
		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		assertEquals(transport.openedOffset(1), deliveredBeforeBreak, "zweiter open() muss ab dem .part-Ende fortsetzen");
		assertFalse(Files.exists(part), ".part ist nach dem Abschluss weg");
		assertEquals(Files.readAllBytes(target), payload, "die zusammengesetzte Datei ist vollstaendig und korrekt");
	}

	// --- 4. Atomares Umbenennen: Ziel taucht nie halbfertig auf --------

	@Test
	public void targetNeverAppearsUntilFinishedAndVerified() throws Exception {
		byte[] payload = randomBytes(128 * 1024);
		FakeDownloadTransport.GatedBody body = new FakeDownloadTransport.GatedBody(payload, 8 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueue((uri, offset) ->
				FakeDownloadTransport.response(200, body, "content-length", Integer.toString(payload.length)));

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<DownloadOutcome> future = pool.submit(
					() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));

			body.awaitGateReached();
			assertTrue(Files.exists(part), "waehrend des Schreibens liegt nur die .part vor");
			assertFalse(Files.exists(target), "das Ziel darf erst nach vollstaendigem Schreiben + Pruefung erscheinen");

			body.openGate();
			DownloadOutcome outcome = future.get(10, TimeUnit.SECONDS);

			assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
			assertFalse(Files.exists(part), ".part ist weg");
			assertEquals(Files.readAllBytes(target), payload);
		} finally {
			pool.shutdownNow();
		}
	}

	// --- 5. Fremd-Redirect wird abgelehnt und NICHT verfolgt ----------

	@Test
	public void foreignRedirectIsRejectedAndNotFollowed() {
		byte[] payload = randomBytes(4096);
		FakeDownloadTransport transport = new FakeDownloadTransport()
				.enqueueRedirect(302, "https://evil.example/paket.vpkg");

		PackageDownloader downloader = PackageDownloader.forTesting(
				transport, DownloadHostWhitelist.RELEASE_ASSETS, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));

		assertEquals(ex.kind(), DownloadException.Kind.BROKEN);
		assertEquals(transport.openCount(), 1, "der fremde Host darf nie geoeffnet werden");
		assertFalse(Files.exists(part), ".part wird bei einem Protokoll-/Whitelist-Verstoss geloescht");
	}

	// --- 6. Whitelist-Redirect wird verfolgt -------------------------

	@Test
	public void whitelistedRedirectIsFollowedToCompletion() throws Exception {
		byte[] payload = randomBytes(64 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport()
				.enqueueRedirect(302, "https://objects.githubusercontent.com/gh/abc/paket.vpkg")
				.enqueueOk(payload);

		PackageDownloader downloader = PackageDownloader.forTesting(
				transport, DownloadHostWhitelist.RELEASE_ASSETS, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP);

		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		assertEquals(transport.openCount(), 2, "erst der github.com-Link, dann der Asset-Host");
		assertEquals(transport.openedUri(1).getHost(), "objects.githubusercontent.com");
		assertEquals(Files.readAllBytes(target), payload);
	}

	// --- 7. Content-Length-/declaredSize-Luege ----------------------

	@Test
	public void contentLengthAboveDeclaredSizeIsRejectedBeforeStreaming() {
		byte[] payload = randomBytes(32 * 1024);
		byte[] tooMuch = randomBytes(payload.length + 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueue((uri, offset) ->
				FakeDownloadTransport.response(200, new ByteArrayInputStream(tooMuch),
						"content-length", Integer.toString(tooMuch.length)));

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = DownloadRequest.of(FAKE_URL, VpkgFixtures.sha256Hex(payload),
				base64(sign(payload, signer)), payload.length);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(ex.kind(), DownloadException.Kind.BROKEN);
		assertFalse(Files.exists(part), ".part weg");
	}

	@Test
	public void serverSendingMoreBytesThanAnnouncedIsRejectedMidStream() {
		byte[] payload = randomBytes(32 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueue((uri, offset) ->
				FakeDownloadTransport.response(200, new FakeDownloadTransport.OversizeBody(randomBytes(payload.length * 2)),
						"content-length", Integer.toString(payload.length)));

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = DownloadRequest.of(FAKE_URL, VpkgFixtures.sha256Hex(payload),
				base64(sign(payload, signer)), payload.length);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(ex.kind(), DownloadException.Kind.BROKEN);
		assertFalse(Files.exists(part), ".part weg nach Content-Length-Luege");
	}

	// --- 8. Falsche Pruefsumme -> VERIFICATION, alles weg -----------

	@Test
	public void wrongChecksumIsVerificationErrorAndCleansUpBothFiles() throws Exception {
		byte[] payload = randomBytes(16 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueueOk(payload);

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		String wrongHash = VpkgFixtures.sha256Hex(randomBytes(100));
		DownloadRequest request = DownloadRequest.of(FAKE_URL, wrongHash, base64(sign(payload, signer)), payload.length);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(ex.kind(), DownloadException.Kind.VERIFICATION);
		assertFalse(Files.exists(target), "die bereits umbenannte Zieldatei wird nach fehlgeschlagener Pruefung geloescht");
		assertFalse(Files.exists(part), ".part ist ebenfalls weg");
	}

	// --- 9. Falsche Signatur -> VERIFICATION, alles weg -------------

	@Test
	public void signatureFromWrongKeyIsVerificationErrorAndCleansUp() throws Exception {
		byte[] payload = randomBytes(16 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueueOk(payload);

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = DownloadRequest.of(FAKE_URL, VpkgFixtures.sha256Hex(payload),
				base64(sign(payload, attacker)), payload.length);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(ex.kind(), DownloadException.Kind.VERIFICATION);
		assertFalse(Files.exists(target));
		assertFalse(Files.exists(part));
	}

	// --- 10. Idempotenz: gueltiges Ziel liegt schon da -------------

	@Test
	public void alreadyPresentValidTargetSkipsTransportEntirely() throws Exception {
		byte[] payload = randomBytes(16 * 1024);
		Files.createDirectories(target.getParent());
		Files.write(target, payload);

		FakeDownloadTransport transport = new FakeDownloadTransport(); // keine Handler -> jeder open() wuerde werfen
		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP);

		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		assertTrue(outcome.alreadyPresent(), "die schon vorhandene, gueltige Datei wird nicht neu geladen");
		assertEquals(transport.openCount(), 0, "der Transport wird nie geoeffnet");
	}

	// --- 11. 404 -> BROKEN, .part weg ------------------------------

	@Test
	public void httpNotFoundIsBrokenAndLeavesNoPart() {
		byte[] payload = randomBytes(4096);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueueStatus(404);

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		DownloadException ex = expectDownloadException(
				() -> downloader.download(request, target, new CancellationToken(), DownloadProgress.NOOP));
		assertEquals(ex.kind(), DownloadException.Kind.BROKEN);
		assertTrue(ex.getMessage().contains("404"));
		assertFalse(Files.exists(part));
	}

	// --- 12. Fortschritt: echte, monoton steigende Byte-Zahlen -----

	@Test
	public void progressReportsRealMonotonicByteCountsEndingAtTotal() throws Exception {
		byte[] payload = randomBytes(200 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueueOk(payload);
		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = requestFor(payload, FAKE_URL);

		RecordingProgress progress = new RecordingProgress();
		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), progress);

		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		assertFalse(progress.reads.isEmpty(), "es kamen Fortschrittsmeldungen an");
		long previous = -1;
		for (long[] sample : progress.reads) {
			assertTrue(sample[0] >= previous, "Byte-Zahl darf nicht sinken (" + previous + " -> " + sample[0] + ")");
			assertTrue(sample[0] <= payload.length, "nie mehr als die echte Dateigroesse");
			assertEquals(sample[1], payload.length, "totalBytes = angekuendigte Gesamtgroesse");
			previous = sample[0];
		}
		assertEquals(progress.reads.get(progress.reads.size() - 1)[0], payload.length, "endet bei der Gesamtgroesse");
	}

	@Test
	public void progressReportsMinusOneTotalWhenSizeIsUnknown() throws Exception {
		byte[] payload = randomBytes(64 * 1024);
		FakeDownloadTransport transport = new FakeDownloadTransport().enqueue((uri, offset) ->
				FakeDownloadTransport.response(200, new ByteArrayInputStream(payload))); // kein content-length

		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signer.getPublic());
		DownloadRequest request = DownloadRequest.withoutDeclaredSize(FAKE_URL, VpkgFixtures.sha256Hex(payload),
				base64(sign(payload, signer)));

		RecordingProgress progress = new RecordingProgress();
		DownloadOutcome outcome = downloader.download(request, target, new CancellationToken(), progress);

		assertEquals(outcome.status(), DownloadOutcome.Status.COMPLETED);
		for (long[] sample : progress.reads) {
			assertEquals(sample[1], -1L, "unbekannte Gesamtgroesse -> total == -1, nie ein erfundener Wert");
		}
		assertEquals(progress.reads.get(progress.reads.size() - 1)[0], payload.length);
	}

	// --- Hilfen -------------------------------------------------------

	private DownloadRequest requestFor(byte[] bytes, URI url) {
		return DownloadRequest.of(url, VpkgFixtures.sha256Hex(bytes), base64(sign(bytes, signer)), bytes.length);
	}

	private static byte[] sign(byte[] bytes, KeyPair keyPair) {
		return VpkgFixtures.signSha256Digest(bytes, keyPair.getPrivate());
	}

	private static String base64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static byte[] randomBytes(int length) {
		byte[] out = new byte[length];
		new Random(length * 2654435761L).nextBytes(out);
		return out;
	}

	private interface ThrowingCall {
		void run() throws DownloadException;
	}

	private static DownloadException expectDownloadException(ThrowingCall call) {
		try {
			call.run();
			fail("erwartet wurde eine DownloadException");
			throw new AssertionError("unerreichbar");
		} catch (DownloadException e) {
			return e;
		}
	}

	private static final class RecordingProgress implements DownloadProgress {
		private final List<long[]> reads = new ArrayList<>();

		@Override
		public synchronized void onProgress(long bytesRead, long totalBytes) {
			reads.add(new long[] { bytesRead, totalBytes });
		}
	}
}
