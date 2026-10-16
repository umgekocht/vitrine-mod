package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.CatalogTestSupportBridge;
import de.spassglas.vitrine.catalog.Featured;
import de.spassglas.vitrine.catalog.FeaturedItem;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.IndexPage;
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
import de.spassglas.vitrine.pack.CatalogPackInstaller;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.detail.CatalogPackageDetailSource;
import de.spassglas.vitrine.ui.detail.PackageActionState;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE46, woertliches Fertig-Kriterium: "Test installiert ein
 * Katalog-Fixture und beweist {@code TileStatus.INSTALLED} danach auf
 * Start/Featured/Detail." Vor dieser Zeile setzte {@link IndexPackageTiles}
 * jede Katalog-Kachel stur auf {@link TileStatus#NONE} -- unabhaengig davon,
 * ob {@code state.json} das Paket laengst installiert zeigte.
 *
 * <p>Installiert echt ueber {@link CatalogPackInstaller} (wie {@code
 * CatalogPackInstallerTest}, ein eigenes Test-Schluesselpaar statt des
 * eingebetteten Registry-Schluessels -- die Signaturpruefung selbst ist
 * nicht Gegenstand dieses Tests). Danach baut ein {@link StoreCatalogController}
 * mit einer auf denselben {@code vitrineConfigDir} zeigenden {@link
 * PackStateSource} die Haupt- und die Featured-Reihe; zuletzt durchlaeuft
 * genau der Pfad, den {@code StoreScreen#openCatalogDetail} nimmt
 * ({@code CatalogPackageDetailSource.load(tile.id(), tile.status(), ...)})
 * mit der von der Haupt-Reihe gelieferten Kachel.</p>
 */
public final class StoreCatalogControllerInstalledStatusTest {

	private static final String MANIFEST_ID = "beispiel.minimalpaket";

	private Path vitrineConfigDir;
	private Path packsDir;
	private Path packagesDir;
	private Path downloadRoot;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-gate46-config-");
		packsDir = vitrineConfigDir.resolve("packs");
		packagesDir = Files.createTempDirectory("vitrine-gate46-packages-");
		downloadRoot = Files.createTempDirectory("vitrine-gate46-download-");
		Files.createDirectories(packsDir);
		CatalogAvailability.reset();
	}

	@AfterMethod
	public void tearDown() throws IOException {
		CatalogAvailability.reset();
		deleteRecursively(vitrineConfigDir);
		deleteRecursively(packagesDir);
		deleteRecursively(downloadRoot);
	}

	@Test
	public void installedCatalogFixtureShowsInstalledOnStartFeaturedAndDetail() throws Exception {
		installFixture();

		IndexPage page = pageWith(MANIFEST_ID);
		Featured featured = new Featured(1, "2026-09-10T00:00:00Z",
				List.of(new FeaturedItem(MANIFEST_ID, 0, null)), List.of());

		StoreCatalogController controller = new StoreCatalogController(
				pageNumber -> CatalogResult.online(page),
				() -> Optional.empty(),
				JavaModOfferGate.hidden(),
				() -> CatalogResult.online(featured),
				() -> PackStateStore.load(vitrineConfigDir));

		controller.refresh();

		// --- "Start" (Haupt-Katalogreihe) ------------------------------------
		TileData mainTile = tileById(controller.catalogTiles(), MANIFEST_ID);
		assertEquals(mainTile.status(), TileStatus.INSTALLED,
				"ein installiertes Katalog-Paket muss in der Hauptreihe 'Installiert' zeigen");

		// --- "Featured" -------------------------------------------------------
		TileData featuredTile = tileById(controller.featuredTiles(), MANIFEST_ID);
		assertEquals(featuredTile.status(), TileStatus.INSTALLED,
				"dasselbe Paket muss in der Featured-Reihe ebenso 'Installiert' zeigen");

		// --- "Detail" -- genau der Pfad von StoreScreen#openCatalogDetail -----
		writeRegistryPackageJson();
		RegistryPackageClient registryClient = CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir);
		Optional<PackageDetailModel> loaded = CatalogPackageDetailSource.forTesting(registryClient)
				.load(mainTile.id(), mainTile.status(), controller.catalogTiles());
		PackageDetailModel model = loaded.orElseThrow(() -> new AssertionError("Detailseite haette laden muessen"));
		assertEquals(model.actionState(), PackageActionState.INSTALLED,
				"der Aktionsknopf der Detailseite darf nach der Installation nicht mehr 'Installieren' zeigen");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private void installFixture() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Path source = downloadRoot.resolve("mini.vpkg");
		Files.write(source, bytes);
		byte[] signature = VpkgFixtures.signSha256Digest(bytes, keyPair.getPrivate());
		DownloadRequest request = DownloadRequest.of(source.toUri(), VpkgFixtures.sha256Hex(bytes),
				Base64.getEncoder().encodeToString(signature), bytes.length);

		PackageDownloader downloader = PackageDownloader.forTesting(new FileUriDownloadTransport(), uri -> true,
				keyPair.getPublic());
		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator hotApply = new HotApplyCoordinator(new RecordingPackActivation(),
				new CountingReloadTrigger(), new SilentToastPresenter(), new ImmediateScheduler());
		CatalogPackInstaller installer = new CatalogPackInstaller(vitrineConfigDir, provider, hotApply, null,
				downloader, keyPair.getPublic());

		installer.install(MANIFEST_ID, request, "example.invalid");

		assertTrue(PackStateStore.load(vitrineConfigDir).isInstalled(VitrinePackProvider.profileId(MANIFEST_ID)),
				"Vorbedingung: das Fixture muss nach der Installation in state.json stehen");
	}

	private void writeRegistryPackageJson() throws IOException {
		String json = """
				{
				  "schemaVersion": 1,
				  "id": "%s",
				  "name": "Minimalpaket",
				  "category": "texturen",
				  "author": {
				    "name": "Vitrine-Testfixture"
				  },
				  "description": "Ein minimales Katalog-Testpaket.",
				  "homepage": "https://github.com/umgekocht/vitrine-packs-starter",
				  "license": "CC0-1.0",
				  "repository": "https://github.com/umgekocht/vitrine-packs-starter",
				  "versions": [
				    {
				      "version": "1.0.0",
				      "vpkgUrl": "https://github.com/umgekocht/vitrine-packs-starter/releases/download/minimalpaket-v1.0.0/minimalpaket.vpkg",
				      "sha256": "%s",
				      "signature": "%s",
				      "size": 1234,
				      "publishedAt": "2026-08-25T09:00:00Z"
				    }
				  ],
				  "latestVersion": "1.0.0",
				  "updatedAt": "2026-08-25T09:00:00Z"
				}
				""".formatted(MANIFEST_ID, "b".repeat(64), "B".repeat(44));
		Files.writeString(packagesDir.resolve(MANIFEST_ID + ".json"), json, StandardCharsets.UTF_8);
	}

	private static IndexPage pageWith(String id) {
		List<IndexPackage> packages = List.of(
				new IndexPackage(id, "Minimalpaket", "Vitrine-Testfixture", "texturen", "1.0.0", null, null,
						"2026-09-01T00:00:00Z", false));
		return new IndexPage(1, "2026-09-10T00:00:00Z", 1, 50, 1, packages.size(), null, null, packages);
	}

	private static TileData tileById(List<TileData> tiles, String id) {
		return tiles.stream().filter(t -> t.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("Kachel '" + id + "' nicht gefunden unter " + tiles));
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

	// --- Test-Doubles (wie CatalogPackInstallerTest) -----------------------

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
