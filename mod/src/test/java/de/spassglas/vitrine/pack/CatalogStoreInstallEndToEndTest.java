package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.catalog.CatalogTestSupportBridge;
import de.spassglas.vitrine.catalog.RegistryPackageClient;
import de.spassglas.vitrine.catalog.download.DownloadRequest;
import de.spassglas.vitrine.catalog.download.FileUriDownloadTransport;
import de.spassglas.vitrine.catalog.download.PackageDownloader;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.detail.CatalogPackageDetailSource;
import de.spassglas.vitrine.ui.detail.PackageActionState;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP G3, woertliches Fertig-Kriterium: ein Test installiert ein
 * Fixture-Paket, das nur als Katalog-Eintrag bekannt ist (NICHT in {@code
 * library/}), Ende-zu-Ende ueber den Store-Weg -- Detailseite oeffnen
 * ({@code CatalogPackageDetailSource}) -&gt; Aktionsknopf-Aequivalent
 * ({@code CatalogPackInstaller.install}) -&gt; Download -&gt; Sicherheits-
 * kette -&gt; Installation. Danach zeigt {@code state.json} das Paket
 * installiert+aktiv mit {@code source} beginnend {@code catalog:}, und die
 * Signatur wurde tatsaechlich gegen {@link EmbeddedSigningKey#load()}
 * geprueft (kein Sidecar-Schluessel aus der Testdatei selbst): {@link
 * #installationFailsAgainstTheRealEmbeddedKeyWithoutTheTestOverride} zeigt,
 * dass genau derselbe Installationsweg mit dem echten eingebetteten
 * Schluessel (statt des Test-Overrides) am Fixture scheitert -- die Pruefung
 * findet also wirklich gegen {@link EmbeddedSigningKey#load()} statt, nicht
 * gegen einen unabhaengigen zweiten Pfad.
 *
 * <p>Der private Schluessel, der zum echten eingebetteten oeffentlichen
 * Schluessel passt, liegt laut Charter nie im Repo -- deshalb ersetzt dieser
 * Test {@link EmbeddedSigningKey#load()} ueber den dafuer vorgesehenen
 * Test-Seam ({@link SigningKeyTestSupport#install}) durch ein frisches
 * Test-Schluesselpaar. Der Katalog-Metadaten-Abruf ({@code packages/&lt;id&gt;.json},
 * schema-gueltig mit einer {@code https://}-vpkgUrl, wie es das echte Schema
 * verlangt) laeuft ueber eine lokale {@code file://}-Quelle
 * ({@code FileUriTransport}); der tatsaechliche Download der {@code .vpkg}
 * laeuft ueber {@link FileUriDownloadTransport} auf eine echte, mit dem
 * Test-Schluesselpaar signierte Datei -- derselbe {@code file://}-Test-Seam,
 * den auch K2/K3 fuer HTTPS-Endpunkte benutzen.</p>
 */
public final class CatalogStoreInstallEndToEndTest {

	private static final String MANIFEST_ID = "beispiel.minimalpaket";

	private Path vitrineConfigDir;
	private Path packsDir;
	private Path packagesDir;
	private Path releaseDir;

	private byte[] vpkgBytes;
	private KeyPair testKeyPair;
	private String realSha256Hex;
	private String realSignatureBase64;

	private RecordingPackActivation packActivation;
	private CountingReloadTrigger reloadTrigger;
	private VitrinePackProvider provider;
	private HotApplyCoordinator hotApply;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-g3-e2e-config-");
		packsDir = vitrineConfigDir.resolve("packs");
		packagesDir = Files.createTempDirectory("vitrine-g3-e2e-packages-");
		releaseDir = Files.createTempDirectory("vitrine-g3-e2e-release-");
		Files.createDirectories(packsDir);

		testKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(testKeyPair.getPublic());

		vpkgBytes = VpkgFixtures.minimalValidPackage();
		realSha256Hex = VpkgFixtures.sha256Hex(vpkgBytes);
		realSignatureBase64 = Base64.getEncoder()
				.encodeToString(VpkgFixtures.signSha256Digest(vpkgBytes, testKeyPair.getPrivate()));
		Files.write(releaseDir.resolve(MANIFEST_ID + ".vpkg"), vpkgBytes);

		// Schema-gueltiger Registry-Eintrag (echte https-vpkgUrl, wie es das
		// Schema verlangt) fuer die Detailseite -- NICHT die tatsaechliche
		// Download-Quelle dieses Tests (siehe Klassendoku).
		JsonObject json = new JsonObject();
		json.addProperty("schemaVersion", 1);
		json.addProperty("id", MANIFEST_ID);
		json.addProperty("name", "Minimalpaket");
		json.addProperty("category", "texturen");
		JsonObject author = new JsonObject();
		author.addProperty("name", "Vitrine-Testfixture");
		json.add("author", author);
		json.addProperty("description", "Ein minimales Katalog-Testpaket.");
		json.addProperty("license", "CC0-1.0");
		json.addProperty("repository", "https://github.com/umgekocht/vitrine-packs-starter");
		com.google.gson.JsonArray versions = new com.google.gson.JsonArray();
		JsonObject version = new JsonObject();
		version.addProperty("version", "1.0.0");
		version.addProperty("vpkgUrl",
				"https://github.com/umgekocht/vitrine-packs-starter/releases/download/minimalpaket-v1.0.0/minimalpaket.vpkg");
		version.addProperty("sha256", "b".repeat(64));
		version.addProperty("signature", "B".repeat(44));
		version.addProperty("size", 1234);
		version.addProperty("publishedAt", "2026-08-25T09:00:00Z");
		versions.add(version);
		json.add("versions", versions);
		json.addProperty("latestVersion", "1.0.0");
		json.addProperty("updatedAt", "2026-08-25T09:00:00Z");
		Files.writeString(packagesDir.resolve(MANIFEST_ID + ".json"), json.toString(), StandardCharsets.UTF_8);

		packActivation = new RecordingPackActivation();
		reloadTrigger = new CountingReloadTrigger();
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		hotApply = new HotApplyCoordinator(packActivation, reloadTrigger, new SilentToastPresenter(),
				new ImmediateScheduler());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		deleteRecursively(vitrineConfigDir);
		deleteRecursively(packagesDir);
		deleteRecursively(releaseDir);
	}

	@Test
	public void installsCatalogOnlyFixtureEndToEndThroughTheStorePath() throws Exception {
		// --- 1. Detailseite oeffnen: das Fixture ist NUR ueber den Katalog
		// bekannt, es liegt nirgendwo in library/. ---------------------------
		RegistryPackageClient registryClient = CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir);
		Optional<PackageDetailModel> loaded =
				CatalogPackageDetailSource.forTesting(registryClient).load(MANIFEST_ID, TileStatus.NONE, List.of());
		PackageDetailModel model = loaded.orElseThrow(() -> new AssertionError("Detailseite haette laden muessen"));
		assertEquals(model.manifestId(), MANIFEST_ID);
		assertEquals(model.header().type(), TilePackageType.TEXTUREN);
		assertEquals(model.actionState(), PackageActionState.INSTALL,
				"Aktionsknopf muss 'Installieren' zeigen -- das Paket ist noch nicht installiert");

		// --- 2. Aktionsknopf-Aequivalent: laedt + installiert -----------------
		DownloadRequest request = DownloadRequest.of(releaseDir.resolve(MANIFEST_ID + ".vpkg").toUri(),
				realSha256Hex, realSignatureBase64, vpkgBytes.length);
		CatalogPackInstaller installer = installer(EmbeddedSigningKey.load());

		List<String> capabilityIds = installer.install(model.manifestId(), request, "example.invalid");
		assertTrue(capabilityIds.isEmpty());

		// --- 3. state.json: installiert + aktiv, Quelle beginnt 'catalog:' ---
		String profileId = VitrinePackProvider.profileId(MANIFEST_ID);
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.isInstalled(profileId));
		assertTrue(state.isActive(profileId));
		String source = sourceOf(state, profileId);
		assertTrue(source != null && source.startsWith(PackState.SOURCE_CATALOG_PREFIX),
				"source muss mit '" + PackState.SOURCE_CATALOG_PREFIX + "' beginnen, war: " + source);
		assertEquals(source, PackState.SOURCE_CATALOG_PREFIX + "example.invalid");

		// --- 4. die Datei liegt tatsaechlich in packs/, samt gueltigem Beleg -
		Path installedVpkg = provider.findPackFile(profileId).orElseThrow();
		assertEquals(Files.readAllBytes(installedVpkg), vpkgBytes);
		VpkgIntegritySidecar.Content sidecar = VpkgIntegritySidecar.read(installedVpkg);
		assertEquals(sidecar.sha256(), realSha256Hex);

		assertEquals(packActivation.activatedIds, List.of(profileId));
		assertTrue(reloadTrigger.callCount >= 1);
	}

	/**
	 * Beweist, dass die Pruefung tatsaechlich {@link EmbeddedSigningKey#load()}
	 * konsultiert: derselbe Ablauf, aber der Test-Override wird VOR dem
	 * Installationsversuch zurueckgesetzt -- die Signatur, die mit dem
	 * Test-Schluesselpaar erzeugt wurde, muss gegen den echten eingebetteten
	 * Schluessel scheitern.
	 */
	@Test
	public void installationFailsAgainstTheRealEmbeddedKeyWithoutTheTestOverride() throws Exception {
		SigningKeyTestSupport.reset();
		PublicKey realEmbeddedKey = EmbeddedSigningKey.load();

		DownloadRequest request = DownloadRequest.of(releaseDir.resolve(MANIFEST_ID + ".vpkg").toUri(),
				realSha256Hex, realSignatureBase64, vpkgBytes.length);
		CatalogPackInstaller installer = installer(realEmbeddedKey);

		assertThrows(PackInstallException.class, () -> installer.install(MANIFEST_ID, request, "example.invalid"));

		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.packs.isEmpty(), "eine gescheiterte Signaturpruefung darf nichts installieren");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private CatalogPackInstaller installer(PublicKey signingPublicKey) {
		PackageDownloader downloader =
				PackageDownloader.forTesting(new FileUriDownloadTransport(), uri -> true, signingPublicKey);
		return new CatalogPackInstaller(vitrineConfigDir, provider, hotApply, null, downloader, signingPublicKey);
	}

	private static String sourceOf(PackState state, String profileId) {
		for (PackState.PackEntry entry : state.packs) {
			if (entry.packId.equals(profileId)) {
				return entry.source;
			}
		}
		return null;
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// --- Test-Doubles (wie PackInstallerTest) -----------------------------

	private static final class RecordingPackActivation implements PackActivation {
		private final List<String> activatedIds = new ArrayList<>();

		@Override
		public boolean activate(String packId) {
			activatedIds.add(packId);
			return true;
		}

		@Override
		public boolean deactivate(String packId) {
			return true;
		}
	}

	private static final class CountingReloadTrigger implements ResourceReloadTrigger {
		private int callCount;

		@Override
		public CompletableFuture<Void> reloadResources() {
			callCount++;
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class SilentToastPresenter implements HotApplyToastPresenter {
		@Override
		public void showApplying(Set<String> packIds) {
		}

		@Override
		public void showApplied(Set<String> packIds) {
		}

		@Override
		public void showFailed(Set<String> packIds, String reason) {
		}

		@Override
		public void showRolledBack(Set<String> packIds, String reason) {
		}
	}

	private static final class ImmediateScheduler implements DelayedTaskScheduler {
		@Override
		public Handle scheduleOnce(Runnable task, long delayMillis) {
			task.run();
			return () -> {
			};
		}
	}
}
