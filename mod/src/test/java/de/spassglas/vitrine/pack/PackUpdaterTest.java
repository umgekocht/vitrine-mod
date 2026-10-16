package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.catalog.download.DownloadException;
import de.spassglas.vitrine.catalog.download.DownloadRequest;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import net.minecraft.SharedConstants;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP-K5-Fertig-Kriterium: <b>Test mit zwei Versionen desselben Pakets</b>.
 *
 * <ul>
 *   <li>{@link #happyUpdateSwapsToNewVersionAndTrashesTheOld()} -- v1 (echtes
 *       signiertes Fixture) installiert, Fake-{@code PackageVersionSource} bietet
 *       v2 (zweites gültiges Fixture) ⇒ nach {@code update()} liegt v2 in
 *       {@code packs/}, ist aktiv, die alte Datei ist im Papierkorb, {@code
 *       state.json} bleibt konsistent.</li>
 *   <li>{@link #downloadFailureKeepsOldVersionActiveAndMarksThePack()} und
 *       {@link #brokenReloadOfNewVersionFallsBackToOldVersion()} -- der Rückfall:
 *       scheitert Download bzw. Reload der v2, ist v1 weiterhin installiert und
 *       aktiv, der Store bedienbar, das Paket als „Update fehlgeschlagen"
 *       markiert, {@code packs/} ohne v2-Fragment.</li>
 *   <li>{@link #updateAllLeavesTheOthersUntouchedWhenOneFails()} -- {@code
 *       updateAll()} mit einem scheiternden von drei Paketen.</li>
 * </ul>
 */
public final class PackUpdaterTest {

	private Path configDir;
	private Path packsDir;

	private RecordingActivation activation;
	private ScriptedReload reload;
	private VitrinePackProvider provider;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-packupdater-test-");
		packsDir = Files.createDirectories(configDir.resolve("packs"));
		activation = new RecordingActivation();
		reload = new ScriptedReload();
		provider = VitrinePackProvider.forConfigDir(configDir);

		// GATE29: Fixtures muessen gegen den Test-Override von
		// SigningKeyTestSupport signieren, nicht mehr gegen einen frei
		// erfundenen Sidecar-Schluessel.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (configDir == null || !Files.exists(configDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(configDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// --- happy: zwei Versionen desselben Pakets --------------------------

	@Test
	public void happyUpdateSwapsToNewVersionAndTrashesTheOld() throws IOException {
		String id = "beispiel.paket";
		String profileId = "vitrine/" + id;
		installVersion(id, "1.0.0", true);

		Fixture v2 = buildFixture(id, "2.0.0");
		PackUpdater updater = new PackUpdater(configDir, provider,
				fakeSource(Map.of(id, v2)), fakeFetcher(v2), activation, reload);

		PackUpdater.UpdateResult result = updater.update(id).join();

		assertEquals(result.outcome(), PackUpdater.Outcome.UPDATED);
		assertEquals(result.installedVersion(), "1.0.0");
		assertEquals(result.targetVersion(), "2.0.0");

		assertEquals(installedVersionInPacks(profileId), "2.0.0", "v2 muss jetzt in packs/ liegen");
		assertEquals(vpkgFilesIn(packsDir).size(), 1, "genau eine .vpkg in packs/ -- kein Doppel");
		PackState state = PackStateStore.load(configDir);
		assertTrue(state.isInstalled(profileId));
		assertTrue(state.isActive(profileId), "die neue Version läuft aktiv weiter");
		assertFalse(state.hasFailedUpdate(profileId), "kein Fehlschlag-Marker nach erfolgreichem Update");
		assertEquals(state.packs.stream().filter(e -> e.packId.equals(profileId)).findFirst().orElseThrow().source,
				PackState.SOURCE_CATALOG_PREFIX + "example.invalid",
				"ROADMAP X3: nach einem erfolgreichen Update muss die Quelle den tatsaechlichen Download-Host tragen");
		assertTrue(activation.activated.contains(profileId));
		assertFalse(vpkgFilesIn(configDir.resolve("trash")).isEmpty(), "die alte Version liegt im Papierkorb");
		assertEquals(reload.calls, 1, "genau ein Reload für das erfolgreiche Update");
	}

	// --- Rückfall 1: Download scheitert --------------------------------

	@Test
	public void downloadFailureKeepsOldVersionActiveAndMarksThePack() throws IOException {
		String id = "beispiel.paket";
		String profileId = "vitrine/" + id;
		byte[] v1Bytes = installVersion(id, "1.0.0", true);

		Fixture v2 = buildFixture(id, "2.0.0");
		PackUpdater.VpkgFetcher throwingFetcher = (request, target) -> {
			throw new DownloadException(DownloadException.Kind.NETWORK, "Verbindung abgerissen (Test)");
		};
		PackUpdater updater = new PackUpdater(configDir, provider,
				fakeSource(Map.of(id, v2)), throwingFetcher, activation, reload);

		PackUpdater.UpdateResult result = updater.update(id).join();

		assertEquals(result.outcome(), PackUpdater.Outcome.FAILED_ROLLED_BACK);
		assertTrue(result.rolledBack());

		assertEquals(installedVersionInPacks(profileId), "1.0.0", "v1 bleibt unverändert in packs/");
		assertEquals(bytesOfSingleVpkg(packsDir), v1Bytes, "die v1-Datei ist byte-für-byte unangetastet");
		assertEquals(vpkgFilesIn(packsDir).size(), 1, "kein v2-Fragment in packs/");
		PackState state = PackStateStore.load(configDir);
		assertTrue(state.isActive(profileId), "die alte Version läuft weiter aktiv");
		assertTrue(state.hasFailedUpdate(profileId), "das Paket ist als 'Update fehlgeschlagen' markiert");
		assertEquals(reload.calls, 0, "ein gescheiterter Download stößt keinen Reload an");
	}

	// --- Rückfall 2: Reload der neuen Version scheitert ---------------

	@Test
	public void brokenReloadOfNewVersionFallsBackToOldVersion() throws IOException {
		String id = "beispiel.paket";
		String profileId = "vitrine/" + id;
		byte[] v1Bytes = installVersion(id, "1.0.0", true);

		Fixture v2 = buildFixture(id, "2.0.0");
		reload.failFirstThenSucceed(); // erster Reload (v2) schlägt fehl, der Wiederherstellungs-Reload klappt
		PackUpdater updater = new PackUpdater(configDir, provider,
				fakeSource(Map.of(id, v2)), fakeFetcher(v2), activation, reload);

		PackUpdater.UpdateResult result = updater.update(id).join();

		assertEquals(result.outcome(), PackUpdater.Outcome.FAILED_ROLLED_BACK);
		assertEquals(installedVersionInPacks(profileId), "1.0.0", "Rückfall: v1 wieder in packs/");
		assertEquals(bytesOfSingleVpkg(packsDir), v1Bytes, "die wiederhergestellte Datei ist die originale v1");
		assertEquals(vpkgFilesIn(packsDir).size(), 1, "kein v2-Fragment in packs/");
		PackState state = PackStateStore.load(configDir);
		assertTrue(state.isActive(profileId), "v1 ist wieder aktiv");
		assertTrue(state.hasFailedUpdate(profileId), "als 'Update fehlgeschlagen' markiert");
		assertEquals(reload.calls, 2, "ein Reload für v2 (fehlgeschlagen) + ein Wiederherstellungs-Reload");
		assertTrue(activation.activated.contains(profileId));
	}

	// --- updateAll: eines von dreien scheitert ----------------------

	@Test
	public void updateAllLeavesTheOthersUntouchedWhenOneFails() throws IOException {
		installVersion("beispiel.pa", "1.0.0", true);
		installVersion("beispiel.pb", "1.0.0", true);
		installVersion("beispiel.pc", "1.0.0", true);

		Fixture a2 = buildFixture("beispiel.pa", "2.0.0");
		Fixture b2 = buildFixture("beispiel.pb", "2.0.0");
		Fixture c2 = buildFixture("beispiel.pc", "2.0.0");

		Map<String, Fixture> byId = Map.of("beispiel.pa", a2, "beispiel.pb", b2, "beispiel.pc", c2);
		Set<String> failing = Set.of("beispiel.pb");
		PackUpdater.VpkgFetcher partlyFailing = (request, target) -> {
			String mid = manifestIdFromStagedName(target);
			if (failing.contains(mid)) {
				throw new DownloadException(DownloadException.Kind.NETWORK, "Test: '" + mid + "' scheitert");
			}
			writeFixture(byId.get(mid), target);
		};

		PackUpdater updater = new PackUpdater(configDir, provider,
				fakeSource(byId), partlyFailing, activation, reload);

		List<PackUpdater.UpdateResult> results = updater.updateAll().join();

		assertEquals(results.size(), 3);
		assertEquals(outcomeFor(results, "beispiel.pa"), PackUpdater.Outcome.UPDATED);
		assertEquals(outcomeFor(results, "beispiel.pb"), PackUpdater.Outcome.FAILED_ROLLED_BACK);
		assertEquals(outcomeFor(results, "beispiel.pc"), PackUpdater.Outcome.UPDATED);

		assertEquals(installedVersionInPacks("vitrine/beispiel.pa"), "2.0.0");
		assertEquals(installedVersionInPacks("vitrine/beispiel.pb"), "1.0.0", "das gescheiterte Paket bleibt auf v1");
		assertEquals(installedVersionInPacks("vitrine/beispiel.pc"), "2.0.0");

		PackState state = PackStateStore.load(configDir);
		assertFalse(state.hasFailedUpdate("vitrine/beispiel.pa"));
		assertTrue(state.hasFailedUpdate("vitrine/beispiel.pb"));
		assertFalse(state.hasFailedUpdate("vitrine/beispiel.pc"));
		assertEquals(vpkgFilesIn(packsDir).size(), 3, "kein Fragment übrig, genau drei Pakete");
	}

	@Test
	public void alreadyUpToDateIsANoOp() throws IOException {
		String id = "beispiel.paket";
		installVersion(id, "2.0.0", true);
		Fixture same = buildFixture(id, "2.0.0");

		PackUpdater updater = new PackUpdater(configDir, provider,
				fakeSource(Map.of(id, same)), fakeFetcher(same), activation, reload);

		PackUpdater.UpdateResult result = updater.update(id).join();

		assertEquals(result.outcome(), PackUpdater.Outcome.ALREADY_UP_TO_DATE);
		assertEquals(reload.calls, 0);
		assertEquals(installedVersionInPacks("vitrine/" + id), "2.0.0");
	}

	@Test
	public void noCatalogEntryIsSkippedNotFailed() throws IOException {
		String id = "beispiel.paket";
		installVersion(id, "1.0.0", true);

		PackUpdater updater = new PackUpdater(configDir, provider,
				manifestId -> Optional.empty(), fakeFetcher(null), activation, reload);

		PackUpdater.UpdateResult result = updater.update(id).join();
		assertEquals(result.outcome(), PackUpdater.Outcome.NO_CATALOG_ENTRY);
		assertFalse(PackStateStore.load(configDir).hasFailedUpdate("vitrine/" + id));
	}

	// --- Fixture-Bau + Seams -----------------------------------------

	private record Fixture(String manifestId, String version, byte[] vpkgBytes, String sha256Hex,
			String signatureBase64, byte[] publicKeyEncoded, java.net.URI url) {
	}

	private Fixture buildFixture(String manifestId, String version) {
		byte[] bytes = VpkgFixtures.packageWithVersion(manifestId, "Paket " + version, version,
				List.of("assets/minecraft/textures/block/stone.png"));
		byte[] signature = VpkgFixtures.signSha256Digest(bytes, trustedKeyPair.getPrivate());
		return new Fixture(manifestId, version, bytes, VpkgFixtures.sha256Hex(bytes),
				java.util.Base64.getEncoder().encodeToString(signature),
				trustedKeyPair.getPublic().getEncoded(),
				java.net.URI.create("https://example.invalid/" + manifestId + "-" + version + ".vpkg"));
	}

	/** Schreibt die .vpkg + eine dazu passende .integrity.json (wie ein echter Download-Abschluss). */
	private static void writeFixture(Fixture fixture, Path targetVpkg) {
		try {
			Files.createDirectories(targetVpkg.getParent());
			Files.write(targetVpkg, fixture.vpkgBytes());
			JsonObject json = new JsonObject();
			json.addProperty("sha256", fixture.sha256Hex());
			json.addProperty("signature", fixture.signatureBase64());
			json.addProperty("publicKey", java.util.Base64.getEncoder().encodeToString(fixture.publicKeyEncoded()));
			Files.writeString(VpkgIntegritySidecar.sidecarPathFor(targetVpkg), json.toString(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private PackUpdater.PackageVersionSource fakeSource(Map<String, Fixture> byId) {
		return manifestId -> Optional.ofNullable(byId.get(manifestId)).map(f -> new PackUpdater.PackageVersion(
				f.version(), f.url(), f.sha256Hex(), f.signatureBase64(), f.vpkgBytes().length));
	}

	private PackUpdater.VpkgFetcher fakeFetcher(Fixture fixture) {
		return (request, target) -> writeFixture(fixture, target);
	}

	private static String manifestIdFromStagedName(Path stagedVpkg) {
		String name = stagedVpkg.getFileName().toString();
		if (name.endsWith(".vpkg")) {
			name = name.substring(0, name.length() - ".vpkg".length());
		}
		int at = name.indexOf('@');
		return at >= 0 ? name.substring(0, at) : name;
	}

	private static PackUpdater.Outcome outcomeFor(List<PackUpdater.UpdateResult> results, String manifestId) {
		return results.stream().filter(r -> r.manifestId().equals(manifestId)).findFirst().orElseThrow().outcome();
	}

	// --- Bibliothek-Ausgangszustand herstellen ----------------------

	private byte[] installVersion(String manifestId, String version, boolean active) throws IOException {
		Fixture fixture = buildFixture(manifestId, version);
		Path vpkg = packsDir.resolve(manifestId + "-" + version + ".vpkg");
		Files.write(vpkg, fixture.vpkgBytes());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", fixture.sha256Hex());
		json.addProperty("signature", fixture.signatureBase64());
		json.addProperty("publicKey", java.util.Base64.getEncoder().encodeToString(fixture.publicKeyEncoded()));
		Files.writeString(VpkgIntegritySidecar.sidecarPathFor(vpkg), json.toString(), StandardCharsets.UTF_8);

		PackState state = PackStateStore.load(configDir);
		state.addOrUpdate("vitrine/" + manifestId, active);
		try {
			PackStateStore.save(configDir, state);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return fixture.vpkgBytes();
	}

	// --- Lese-Hilfen ----------------------------------------------

	private String installedVersionInPacks(String profileId) {
		Path file = provider.findPackFile(profileId).orElseThrow(() ->
				new AssertionError("keine packs/-Datei für " + profileId));
		try (VpkgArchive archive = VpkgArchive.open(file)) {
			return ManifestParser.parse(archive.readManifestBytes()).version();
		} catch (Exception e) {
			throw new AssertionError("Manifest von " + file + " nicht lesbar", e);
		}
	}

	private static List<Path> vpkgFilesIn(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(p -> p.getFileName().toString().endsWith(".vpkg")).toList();
		}
	}

	private static byte[] bytesOfSingleVpkg(Path dir) throws IOException {
		List<Path> files = vpkgFilesIn(dir);
		assertEquals(files.size(), 1, "erwartet genau eine .vpkg in " + dir);
		return Files.readAllBytes(files.get(0));
	}

	// --- Test-Doubles -------------------------------------------

	private static final class RecordingActivation implements PackActivation {
		private final List<String> activated = new ArrayList<>();
		private final Set<String> active = new HashSet<>();

		@Override
		public boolean activate(String packId) {
			activated.add(packId);
			return active.add(packId);
		}

		@Override
		public boolean deactivate(String packId) {
			return active.remove(packId);
		}
	}

	/** Reload-Trigger, der sich pro Aufruf skripten lässt (Erfolg / einmaliger Fehlschlag). */
	private static final class ScriptedReload implements ResourceReloadTrigger {
		private int calls;
		private boolean failNext;

		void failFirstThenSucceed() {
			failNext = true;
		}

		@Override
		public CompletableFuture<Void> reloadResources() {
			calls++;
			if (failNext) {
				failNext = false;
				return CompletableFuture.failedFuture(new IllegalStateException("Reload kaputt (Test)"));
			}
			return CompletableFuture.completedFuture(null);
		}
	}
}
