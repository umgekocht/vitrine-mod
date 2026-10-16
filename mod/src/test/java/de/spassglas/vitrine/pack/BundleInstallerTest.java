package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das woertliche Fertig-Kriterium von ROADMAP X1 ab ("Test mit einem
 * Bundle, dessen drittes Teil kaputt ist"): {@link BundleInstaller}
 * installiert mehrere {@code .vpkg}-Dateien als eine Einheit -- entweder alle
 * oder keines, mit genau einem gesammelten Reload im Erfolgsfall und einem
 * vollstaendigen Rueckfall, sobald irgendetwas schiefgeht (egal ob bei der
 * Pruefung oder erst danach beim Reload).
 */
public final class BundleInstallerTest {

	private Path vitrineConfigDir;
	private Path stagingDir;
	private Path packsDir;

	private RecordingPackActivation packActivation;
	private CountingReloadTrigger reloadTrigger;
	private VitrinePackProvider provider;
	private BundleInstaller bundleInstaller;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-bundle-test-");
		stagingDir = vitrineConfigDir.resolve("staging");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(stagingDir);
		Files.createDirectories(packsDir);

		// GATE29: Fixtures muessen gegen den Test-Override von
		// SigningKeyTestSupport signieren, nicht mehr gegen einen frei
		// erfundenen Sidecar-Schluessel.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());

		packActivation = new RecordingPackActivation();
		reloadTrigger = new CountingReloadTrigger();
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		bundleInstaller = new BundleInstaller(vitrineConfigDir, provider, packActivation, reloadTrigger);
	}

	@AfterMethod
	public void tearDown() throws Exception {
		SigningKeyTestSupport.reset();
		if (vitrineConfigDir == null || !Files.exists(vitrineConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(vitrineConfigDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
		}
	}

	// --- 1. Erfolgsfall: 3 gueltige Pakete -> alle installiert, EIN Reload ---

	@Test
	public void threeValidPackagesAreAllInstalledWithExactlyOneCollectedReload() throws Exception {
		Path a = writeStagedPackage("teil-a.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teila", "Bundle-Teil A", "1.0.0",
						List.of("textures/a.png")));
		Path b = writeStagedPackage("teil-b.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teilb", "Bundle-Teil B", "1.0.0",
						List.of("textures/b.png")));
		Path c = writeStagedPackage("teil-c.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teilc", "Bundle-Teil C", "1.0.0",
						List.of("textures/c.png")));

		// GATE38: install() liefert jetzt eine Future -- hier im Test wird sie
		// bewusst abgewartet (Test-Thread, kein Client-Thread).
		BundleInstaller.BundleResult result = bundleInstaller.install(List.of(a, b, c)).join();

		assertEquals(result.installedProfileIds(), List.of("vitrine/bundle.teila", "vitrine/bundle.teilb",
				"vitrine/bundle.teilc"));
		assertEquals(vpkgFileCount(packsDir), 3, "alle drei Bundle-Teile muessen in packs/ liegen");

		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.isInstalled("vitrine/bundle.teila"));
		assertTrue(state.isInstalled("vitrine/bundle.teilb"));
		assertTrue(state.isInstalled("vitrine/bundle.teilc"));
		assertTrue(state.isActive("vitrine/bundle.teila"));
		assertTrue(state.isActive("vitrine/bundle.teilb"));
		assertTrue(state.isActive("vitrine/bundle.teilc"));

		assertEquals(packActivation.activatedIds, List.of("vitrine/bundle.teila", "vitrine/bundle.teilb",
				"vitrine/bundle.teilc"), "alle drei Teile muessen aktiviert worden sein");
		assertEquals(reloadTrigger.callCount, 1, "ein Bundle mit drei Teilen darf nur EINEN Reload ausloesen");
	}

	// --- 2. Fertig-Kriterium: drittes Teil kaputt (Zip-Slip) -> ganzes ------
	// ---    Bundle abgelehnt, auch die ersten beiden gueltigen Teile -------

	@Test
	public void thirdPartBrokenRejectsTheWholeBundleAndKeepsFirstTwoPartsOut() throws Exception {
		Path a = writeStagedPackage("teil-a.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teila", "Bundle-Teil A", "1.0.0",
						List.of("textures/a.png")));
		Path b = writeStagedPackage("teil-b.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teilb", "Bundle-Teil B", "1.0.0",
						List.of("textures/b.png")));
		// Drittes Teil: Zip-Slip-Angriff (VpkgFixtures.zipSlipPackage(), Manifest-ID "beispiel.zipslip").
		Path c = writeStagedPackage("teil-c-boese.vpkg", VpkgFixtures.zipSlipPackage());

		PackInstallException thrown = org.testng.Assert.expectThrows(PackInstallException.class,
				() -> bundleInstaller.install(List.of(a, b, c)));
		assertTrue(thrown.getMessage().contains("Bundle-Teil 3"), "die Meldung muss das dritte Teil benennen");

		assertEquals(regularFileCount(packsDir), 0,
				"kein einziges Bundle-Teil darf in packs/ landen -- auch nicht die ersten beiden gueltigen (a, b)");

		PackState state = PackStateStore.load(vitrineConfigDir);
		assertFalse(state.isInstalled("vitrine/bundle.teila"), "Teil A darf trotz Gueltigkeit nicht installiert sein");
		assertFalse(state.isInstalled("vitrine/bundle.teilb"), "Teil B darf trotz Gueltigkeit nicht installiert sein");
		assertFalse(state.isInstalled("vitrine/beispiel.zipslip"));
		assertEquals(state.packs.size(), 0, "state.json darf durch das abgelehnte Bundle keinen Eintrag bekommen");

		assertEquals(packActivation.activatedIds, List.of(), "kein Teil darf aktiviert worden sein");
		assertEquals(reloadTrigger.callCount, 0, "ein abgelehntes Bundle darf keinen Reload ausloesen");
	}

	// --- 3. Fertig-Kriterium-Variante: kollidierende Faehigkeit ohne --------
	// ---    Zustimmung im dritten Teil -> ganzes Bundle abgelehnt ----------

	@Test
	public void thirdPartWithUnconsentedCapabilityRejectsTheWholeBundle() throws Exception {
		Path a = writeStagedPackage("teil-a.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teila", "Bundle-Teil A", "1.0.0",
						List.of("textures/a.png")));
		Path b = writeStagedPackage("teil-b.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teilb", "Bundle-Teil B", "1.0.0",
						List.of("textures/b.png")));
		Path c = writeStagedPackage("teil-c-faehig.vpkg",
				VpkgFixtures.minimalValidPackageWithCapabilities("bundle.teilc.faehig", List.of("texturen")));

		assertThrows(PackInstallException.class, () -> bundleInstaller.install(List.of(a, b, c)));

		assertEquals(regularFileCount(packsDir), 0, "ohne Faehigkeiten-Zustimmung fuer Teil C darf nichts kopiert werden");
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertFalse(state.isInstalled("vitrine/bundle.teila"));
		assertFalse(state.isInstalled("vitrine/bundle.teilb"));
		assertFalse(state.isInstalled("vitrine/bundle.teilc.faehig"));
	}

	// --- 4. confirmCapabilitiesAndInstall erteilt Zustimmung fuer das ganze -
	// ---    Bundle auf einmal und installiert danach erfolgreich -----------

	@Test
	public void confirmCapabilitiesAndInstallGrantsConsentForWholeBundleThenInstalls() throws Exception {
		Path a = writeStagedPackage("teil-a.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teila", "Bundle-Teil A", "1.0.0",
						List.of("textures/a.png")));
		Path c = writeStagedPackage("teil-c-faehig.vpkg",
				VpkgFixtures.minimalValidPackageWithCapabilities("bundle.teilc.faehig", List.of("texturen")));

		BundleInstaller.BundleResult result = bundleInstaller.confirmCapabilitiesAndInstall(List.of(a, c),
				Map.of("bundle.teilc.faehig", List.of("texturen"))).join();

		assertEquals(result.installedProfileIds(), List.of("vitrine/bundle.teila", "vitrine/bundle.teilc.faehig"));
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.isInstalled("vitrine/bundle.teila"));
		assertTrue(state.isInstalled("vitrine/bundle.teilc.faehig"));
		assertTrue(state.hasCapabilityConsent("bundle.teilc.faehig", List.of("texturen")));
	}

	// --- 5. Fehler NACH dem Kopieren (Reload schlaegt fehl) -> Rueckfall ---
	// ---    raeumt bereits kopierte Teile weg -------------------------------

	@Test
	public void failureAfterCopyingRollsBackAlreadyCopiedParts() throws Exception {
		Path a = writeStagedPackage("teil-a.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teila", "Bundle-Teil A", "1.0.0",
						List.of("textures/a.png")));
		Path b = writeStagedPackage("teil-b.vpkg",
				VpkgFixtures.packageWithVersion("bundle.teilb", "Bundle-Teil B", "1.0.0",
						List.of("textures/b.png")));

		FailingReloadTrigger failingTrigger = new FailingReloadTrigger();
		BundleInstaller installer = new BundleInstaller(vitrineConfigDir, provider, packActivation, failingTrigger);

		// GATE38: der fehlgeschlagene Reload kommt jetzt ueber die Future --
		// die Rueckname selbst ist unveraendert vollstaendig.
		java.util.concurrent.CompletionException thrown = org.testng.Assert.expectThrows(
				java.util.concurrent.CompletionException.class, () -> installer.install(List.of(a, b)).join());
		assertTrue(thrown.getCause() instanceof PackInstallException,
				"die Future muss mit einer PackInstallException scheitern, war: " + thrown.getCause());

		assertEquals(regularFileCount(packsDir), 0,
				"ein fehlgeschlagener Reload nach dem Kopieren muss alle bereits kopierten Teile wieder entfernen");
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertFalse(state.isInstalled("vitrine/bundle.teila"));
		assertFalse(state.isInstalled("vitrine/bundle.teilb"));
		assertEquals(packActivation.deactivatedIds, List.of("vitrine/bundle.teila", "vitrine/bundle.teilb"),
				"nach dem Rueckfall muessen beide Teile wieder deaktiviert worden sein");
		assertEquals(failingTrigger.callCount, 2, "der fehlgeschlagene Reload plus der bereinigende Wiederholungs-Reload");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private Path writeStagedPackage(String fileName, byte[] packageBytes) throws Exception {
		Path vpkgFile = stagingDir.resolve(fileName);
		Files.write(vpkgFile, packageBytes);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());
		return vpkgFile;
	}

	private static void writeSidecar(Path vpkgFile, String sha256, byte[] signature, PublicKey publicKey)
			throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("sha256", sha256);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
		Files.writeString(VpkgIntegritySidecar.sidecarPathFor(vpkgFile), json.toString(), StandardCharsets.UTF_8);
	}

	private static long regularFileCount(Path dir) throws Exception {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile).count();
		}
	}

	private static long vpkgFileCount(Path dir) throws Exception {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> p.getFileName().toString().endsWith(".vpkg")).count();
		}
	}

	// --- Test-Doubles --------------------------------------------------

	private static final class RecordingPackActivation implements PackActivation {
		private final List<String> activatedIds = new ArrayList<>();
		private final List<String> deactivatedIds = new ArrayList<>();

		@Override
		public boolean activate(String packId) {
			activatedIds.add(packId);
			return true;
		}

		@Override
		public boolean deactivate(String packId) {
			deactivatedIds.add(packId);
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

	/** Jeder Reload (auch der bereinigende Wiederholungsversuch) scheitert -- fuer den "Fehler nach dem Kopieren"-Test. */
	private static final class FailingReloadTrigger implements ResourceReloadTrigger {
		private int callCount;

		@Override
		public CompletableFuture<Void> reloadResources() {
			callCount++;
			CompletableFuture<Void> future = new CompletableFuture<>();
			future.completeExceptionally(new RuntimeException("absichtlich kaputter Test-Reload"));
			return future;
		}
	}
}
