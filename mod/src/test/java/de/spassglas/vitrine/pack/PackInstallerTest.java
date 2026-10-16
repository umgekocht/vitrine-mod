package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourcePackProfile;
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
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das woertliche Fertig-Kriterium von ROADMAP S4 ab ("Ablauf einmal
 * komplett ohne Neustart durchspielbar"), soweit ohne laufenden Client
 * pruefbar: {@link PackInstaller} installiert aus der Bibliothek erst NACH der
 * Faehigkeiten-Zustimmung (Charter-Regel 4), schaltet ein bereits
 * installiertes Paket an/aus ohne die Datei anzufassen, ist bei einem zweiten
 * Installationsaufruf idempotent, laesst bei jedem Sicherheits-/Integritaets-
 * Verstoss nichts Halbes in {@code packs/} liegen -- und ein einmal
 * installiertes, dann deaktiviertes und deinstalliertes Paket verschwindet
 * ohne Neustart wieder aus einem erneuten {@link VitrinePackProvider}-Scan.
 */
public final class PackInstallerTest {

	private static final String CAPS_MANIFEST_ID = "beispiel.faehigkeitspaket";
	private static final String CAPS_PROFILE_ID = "vitrine/beispiel.faehigkeitspaket";
	private static final String MINI_MANIFEST_ID = "beispiel.minimalpaket";
	private static final String MINI_PROFILE_ID = "vitrine/beispiel.minimalpaket";

	private Path vitrineConfigDir;
	private Path libraryDir;
	private Path packsDir;

	private RecordingPackActivation packActivation;
	private CountingReloadTrigger reloadTrigger;
	private VitrinePackProvider provider;
	private PackInstaller installer;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-installer-test-");
		libraryDir = vitrineConfigDir.resolve("library");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(libraryDir);
		Files.createDirectories(packsDir);

		// Charter-Regel 3 (GATE29): die Pruefung laeuft jetzt gegen
		// EmbeddedSigningKey.load(), nicht mehr gegen den im Sidecar
		// mitgelieferten Schluessel -- Fixtures muessen deshalb mit dem
		// Test-Override signieren, siehe SigningKeyTestSupport#install.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());

		packActivation = new RecordingPackActivation();
		reloadTrigger = new CountingReloadTrigger();
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator coordinator = new HotApplyCoordinator(
				packActivation, reloadTrigger, new SilentToastPresenter(), new ImmediateScheduler());
		installer = new PackInstaller(provider, coordinator);
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

	// --- 1. install VOR jeder Zustimmung -> Abbruch, packs/ bleibt leer -----

	@Test
	public void installBeforeAnyConsentThrowsAndCopiesNothing() throws Exception {
		writeLibraryPackage("faehig.vpkg",
				VpkgFixtures.minimalValidPackageWithCapabilities(CAPS_MANIFEST_ID, List.of("texturen")));

		assertThrows(PackInstallException.class, () -> installer.install(CAPS_MANIFEST_ID));

		assertEquals(regularFileCount(packsDir), 0,
				"ohne Faehigkeiten-Zustimmung darf nichts nach packs/ kopiert werden (Charter-Regel 4)");
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertFalse(state.isInstalled(CAPS_PROFILE_ID));
		assertFalse(state.hasCapabilityConsent(CAPS_MANIFEST_ID, List.of("texturen")));
	}

	// --- 2. confirmCapabilitiesAndInstall: Zustimmung + Datei + aktiv ------

	@Test
	public void confirmCapabilitiesAndInstallRecordsConsentInstallsAndActivates() throws Exception {
		writeLibraryPackage("faehig.vpkg",
				VpkgFixtures.minimalValidPackageWithCapabilities(CAPS_MANIFEST_ID, List.of("texturen")));

		installer.confirmCapabilitiesAndInstall(CAPS_MANIFEST_ID, List.of("texturen"));

		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.hasCapabilityConsent(CAPS_MANIFEST_ID, List.of("texturen")),
				"die Zustimmung muss in state.json stehen");
		assertTrue(state.isInstalled(CAPS_PROFILE_ID), "das Paket muss als installiert gefuehrt werden");
		assertTrue(state.isActive(CAPS_PROFILE_ID), "frisch installierte Pakete sind aktiv");

		assertTrue(Files.exists(packsDir.resolve("faehig.vpkg")), ".vpkg muss in packs/ liegen");
		assertTrue(Files.exists(packsDir.resolve("faehig.vpkg" + VpkgIntegritySidecar.FILE_SUFFIX)),
				"der Integritaets-Beleg muss mitkopiert sein");

		assertEquals(packActivation.activatedIds, List.of(CAPS_PROFILE_ID),
				"die Aktivierung muss beim Koordinator angefordert worden sein");
		assertTrue(reloadTrigger.callCount >= 1, "ein Ressourcen-Reload muss angestossen worden sein");
	}

	// --- 3. setActive schaltet an/aus, ohne die Datei anzufassen ----------

	@Test
	public void setActiveTogglesActivationWithoutTouchingTheFile() throws Exception {
		Path vpkg = writeLibraryPackage("mini.vpkg", VpkgFixtures.minimalValidPackage());
		installer.install(MINI_MANIFEST_ID);
		Path installed = packsDir.resolve("mini.vpkg");
		byte[] contentAfterInstall = Files.readAllBytes(installed);

		installer.setActive(MINI_PROFILE_ID, false);
		assertEquals(packActivation.deactivatedIds, List.of(MINI_PROFILE_ID),
				"Deaktivierung muss beim Koordinator angefordert worden sein");
		assertFalse(PackStateStore.load(vitrineConfigDir).isActive(MINI_PROFILE_ID));
		assertTrue(Files.exists(installed), "An/Aus darf die Datei in packs/ nicht entfernen");
		assertEquals(Files.readAllBytes(installed), contentAfterInstall, "An/Aus darf die Datei nicht veraendern");

		installer.setActive(MINI_PROFILE_ID, true);
		assertEquals(packActivation.activatedIds, List.of(MINI_PROFILE_ID, MINI_PROFILE_ID),
				"Aktivierung muss erneut beim Koordinator angefordert worden sein");
		assertTrue(PackStateStore.load(vitrineConfigDir).isActive(MINI_PROFILE_ID));
		assertTrue(Files.exists(vpkg), "die Bibliotheks-Datei bleibt ebenfalls unangetastet");
	}

	// --- 4. Idempotenz: zweiter install kopiert nicht erneut -------------

	@Test
	public void secondInstallIsIdempotentAndOnlyRequestsActivationAgain() throws Exception {
		writeLibraryPackage("mini.vpkg", VpkgFixtures.minimalValidPackage());

		installer.install(MINI_MANIFEST_ID);
		long filesAfterFirst = regularFileCount(packsDir);

		installer.install(MINI_MANIFEST_ID);
		long filesAfterSecond = regularFileCount(packsDir);

		assertEquals(filesAfterSecond, filesAfterFirst,
				"ein zweiter install-Aufruf darf keine weitere Kopie in packs/ anlegen");
		assertEquals(vpkgFileCount(packsDir), 1, "es darf genau eine .vpkg in packs/ liegen");
		assertEquals(packActivation.activatedIds, List.of(MINI_PROFILE_ID, MINI_PROFILE_ID),
				"der zweite Aufruf fordert nur erneut die Aktivierung an");
	}

	// --- 4b. GATE120: zwei gleichzeitige install()-Aufrufe derselben --------
	// ---     Manifest-ID duerfen nicht zwei Dateien in packs/ anlegen ------

	@Test
	public void concurrentInstallsOfSameManifestIdProduceExactlyOneFile() throws Exception {
		writeLibraryPackage("mini.vpkg", VpkgFixtures.minimalValidPackage());

		int threadCount = 8;
		CyclicBarrier barrier = new CyclicBarrier(threadCount);
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		try {
			List<Callable<Exception>> tasks = new ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				tasks.add(() -> {
					try {
						barrier.await(10, TimeUnit.SECONDS);
						installer.install(MINI_MANIFEST_ID);
						return null;
					} catch (PackInstallException e) {
						// GATE120-Anforderung: ein abgewiesener/kontrollierter
						// zweiter Versuch ist erlaubt -- nur eine zweite Datei
						// waere der Bug. Siehe Assertion unten.
						return e;
					} catch (BrokenBarrierException e) {
						return e;
					}
				});
			}
			List<Future<Exception>> futures = new ArrayList<>();
			for (Callable<Exception> task : tasks) {
				futures.add(pool.submit(task));
			}
			List<Exception> failures = new ArrayList<>();
			for (Future<Exception> future : futures) {
				Exception failure = future.get(30, TimeUnit.SECONDS);
				if (failure != null) {
					failures.add(failure);
				}
			}
			assertTrue(failures.isEmpty(),
					"bei einer identischen, gueltigen Manifest-ID darf kein gleichzeitiger install()-Aufruf scheitern "
							+ "(einer kopiert, die uebrigen sehen unter der Sperre 'schon installiert' und sind no-op): "
							+ failures);
		} finally {
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		}

		assertEquals(vpkgFileCount(packsDir), 1,
				"GATE120: " + threadCount + " gleichzeitige install()-Aufrufe derselben Manifest-ID duerfen am Ende "
						+ "nur eine Datei in packs/ hinterlassen, nicht eine je Thread, der die Idempotenz-Pruefung "
						+ "noch vor dem Kopieren eines anderen Threads bestanden hat.");
		assertEquals(regularFileCount(packsDir), 2,
				"genau die eine .vpkg plus ihr Integritaets-Beleg, keine weiteren Halbreste");
		PackState finalState = PackStateStore.load(vitrineConfigDir);
		assertTrue(finalState.isInstalled(MINI_PROFILE_ID));
	}

	// --- 5a. Fehlerfall Zip-Slip -> Abbruch, packs/ bleibt leer ----------

	@Test
	public void zipSlipFixtureIsRejectedAndLeavesPacksEmpty() throws Exception {
		writeLibraryPackage("boese.vpkg", VpkgFixtures.zipSlipPackage());

		assertThrows(PackInstallException.class, () -> installer.install("beispiel.zipslip"));

		assertEquals(regularFileCount(packsDir), 0, "ein Zip-Slip-Paket darf kein halbes Paket in packs/ hinterlassen");
		assertFalse(PackStateStore.load(vitrineConfigDir).isInstalled("vitrine/beispiel.zipslip"));
	}

	// --- 5b. Fehlerfall manipulierte Signatur -> Abbruch, packs/ leer ----

	@Test
	public void tamperedSignatureIsRejectedAndLeavesPacksEmpty() throws Exception {
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Path vpkg = libraryDir.resolve("mani.vpkg");
		Files.write(vpkg, bytes);
		// gueltige Pruefsumme, aber eine Signatur ueber voellig andere Bytes.
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] wrongSignature = VpkgFixtures.signSha256Digest(new byte[] { 1, 2, 3 }, keyPair.getPrivate());
		writeSidecar(vpkg, VpkgFixtures.sha256Hex(bytes), wrongSignature, keyPair.getPublic());

		assertThrows(PackInstallException.class, () -> installer.install(MINI_MANIFEST_ID));

		assertEquals(regularFileCount(packsDir), 0,
				"eine fehlgeschlagene Signaturpruefung darf kein halbes Paket in packs/ hinterlassen");
	}

	// --- 6. End-zu-End ohne Neustart: installieren -> deaktivieren -> ------
	// ---    deinstallieren -> erneuter Scan zeigt das Paket nicht mehr ----

	@Test
	public void installDeactivateUninstallRemovesPackFromRescanWithoutRestart() throws Exception {
		writeLibraryPackage("faehig.vpkg",
				VpkgFixtures.minimalValidPackageWithCapabilities(CAPS_MANIFEST_ID, List.of("texturen")));

		installer.confirmCapabilitiesAndInstall(CAPS_MANIFEST_ID, List.of("texturen"));

		CollectingProfileAdder afterInstall = new CollectingProfileAdder();
		provider.register(afterInstall);
		assertEquals(afterInstall.profiles.size(), 1,
				"nach dem Installieren muss das Paket im Provider-Scan auftauchen");

		installer.setActive(CAPS_PROFILE_ID, false);
		assertFalse(PackStateStore.load(vitrineConfigDir).isActive(CAPS_PROFILE_ID));

		PackUninstaller uninstaller = new PackUninstaller(provider, packActivation, reloadTrigger);
		uninstaller.uninstall(CAPS_PROFILE_ID).join();

		CollectingProfileAdder afterUninstall = new CollectingProfileAdder();
		provider.register(afterUninstall);
		assertTrue(afterUninstall.profiles.isEmpty(),
				"nach dem Deinstallieren darf ein erneuter Scan (kein Neustart) das Paket nicht mehr zeigen");
		assertFalse(PackStateStore.load(vitrineConfigDir).isInstalled(CAPS_PROFILE_ID),
				"state.json darf das deinstallierte Paket nicht mehr fuehren");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private Path writeLibraryPackage(String fileName, byte[] packageBytes) throws Exception {
		Path vpkgFile = libraryDir.resolve(fileName);
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

	/** Fuehrt den geplanten Lauf sofort aus -- Debounce-Zeit ist fuer diese Tests ohne Belang. */
	private static final class ImmediateScheduler implements DelayedTaskScheduler {
		@Override
		public Handle scheduleOnce(Runnable task, long delayMillis) {
			task.run();
			return () -> {
			};
		}
	}

	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}
	}
}
