package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.catalog.download.CancellationToken;
import de.spassglas.vitrine.catalog.download.DownloadProgress;
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
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import net.minecraft.SharedConstants;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP G3: {@link CatalogPackInstaller} laedt ein Angebot, das nur als
 * Katalog-Eintrag bekannt ist (nicht in {@code library/}), ueber eine lokale
 * {@code file://}-Quelle, prueft es genauso wie {@link PackInstaller} (Zip-
 * Sicherheit, Pruefsumme/Signatur, Faehigkeiten-Zustimmung) und installiert
 * es -- mit {@code source} beginnend {@link PackState#SOURCE_CATALOG_PREFIX}.
 */
public final class CatalogPackInstallerTest {

	private static final Predicate<java.net.URI> ALLOW_ANY = uri -> true;

	private Path vitrineConfigDir;
	private Path packsDir;
	private Path downloadRoot;

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
		vitrineConfigDir = Files.createTempDirectory("vitrine-catalog-installer-test-");
		packsDir = vitrineConfigDir.resolve("packs");
		downloadRoot = Files.createTempDirectory("vitrine-catalog-installer-source-");
		Files.createDirectories(packsDir);

		packActivation = new RecordingPackActivation();
		reloadTrigger = new CountingReloadTrigger();
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		hotApply = new HotApplyCoordinator(packActivation, reloadTrigger, new SilentToastPresenter(),
				new ImmediateScheduler());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		deleteRecursively(vitrineConfigDir);
		deleteRecursively(downloadRoot);
	}

	private CatalogPackInstaller installer(java.security.PublicKey signingPublicKey) {
		PackageDownloader downloader =
				PackageDownloader.forTesting(new FileUriDownloadTransport(), ALLOW_ANY, signingPublicKey);
		return new CatalogPackInstaller(vitrineConfigDir, provider, hotApply, null, downloader, signingPublicKey);
	}

	/**
	 * Wie {@link #installer(java.security.PublicKey)}, aber mit einem
	 * {@link CountingDownloadTransport}, der jeden {@code open()}-Aufruf
	 * mitzaehlt -- der Beweis fuer GATE90, dass vor dem Download gar nicht
	 * erst ans Netz gegangen wird.
	 */
	private CatalogPackInstaller installerWithCountingTransport(java.security.PublicKey signingPublicKey,
			CountingDownloadTransport transport) {
		PackageDownloader downloader = PackageDownloader.forTesting(transport, ALLOW_ANY, signingPublicKey);
		return new CatalogPackInstaller(vitrineConfigDir, provider, hotApply, null, downloader, signingPublicKey);
	}

	private DownloadRequest hostRelease(String fileName, byte[] packageBytes, KeyPair keyPair) throws IOException {
		Path source = downloadRoot.resolve(fileName);
		Files.write(source, packageBytes);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, keyPair.getPrivate());
		return DownloadRequest.of(source.toUri(), VpkgFixtures.sha256Hex(packageBytes),
				java.util.Base64.getEncoder().encodeToString(signature), packageBytes.length);
	}

	@Test
	public void installsAMinimalCatalogOnlyPackageWithCatalogSource() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		DownloadRequest request = hostRelease("mini.vpkg", bytes, keyPair);

		List<String> capabilityIds = installer(keyPair.getPublic())
				.install("beispiel.minimalpaket", request, "example.invalid");

		assertTrue(capabilityIds.isEmpty());
		PackState state = PackStateStore.load(vitrineConfigDir);
		String profileId = VitrinePackProvider.profileId("beispiel.minimalpaket");
		assertTrue(state.isInstalled(profileId));
		assertTrue(state.isActive(profileId));
		assertTrue(sourceOf(state, profileId).startsWith(PackState.SOURCE_CATALOG_PREFIX),
				"source muss mit '" + PackState.SOURCE_CATALOG_PREFIX + "' beginnen, war: " + sourceOf(state, profileId));
		assertEquals(sourceOf(state, profileId), PackState.SOURCE_CATALOG_PREFIX + "example.invalid");

		assertEquals(vpkgFileCount(packsDir), 1);
		assertEquals(packActivation.activatedIds, List.of(profileId));
		assertTrue(reloadTrigger.callCount >= 1);

		// die frisch geschriebene Sidecar muss denselben oeffentlichen Schluessel tragen.
		Path installedVpkg = provider.findPackFile(profileId).orElseThrow();
		VpkgIntegritySidecar.Content sidecar = VpkgIntegritySidecar.read(installedVpkg);
		assertEquals(sidecar.sha256(), VpkgFixtures.sha256Hex(bytes));
	}

	@Test
	public void tamperedSignatureIsRejectedAndLeavesPacksEmpty() throws Exception {
		KeyPair correctKeyPair = VpkgFixtures.generateEd25519KeyPair();
		KeyPair wrongKeyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		DownloadRequest request = hostRelease("mini.vpkg", bytes, wrongKeyPair);

		assertThrows(PackInstallException.class,
				() -> installer(correctKeyPair.getPublic()).install("beispiel.minimalpaket", request, "example.invalid"));

		assertEquals(regularFileCount(packsDir), 0);
		assertFalse(PackStateStore.load(vitrineConfigDir)
				.isInstalled(VitrinePackProvider.profileId("beispiel.minimalpaket")));
	}

	@Test
	public void capabilitiesDiscoveredOnlyAfterDownloadRequireConsentBeforeInstalling() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackageWithCapabilities("beispiel.faehigkeitspaket", List.of("texturen"));
		DownloadRequest request = hostRelease("faehig.vpkg", bytes, keyPair);
		CatalogPackInstaller installer = installer(keyPair.getPublic());

		try {
			installer.install("beispiel.faehigkeitspaket", request, "example.invalid");
			throw new AssertionError("CatalogConsentRequiredException erwartet, aber keine Ausnahme geworfen");
		} catch (CatalogConsentRequiredException e) {
			assertEquals(e.capabilityIds(), List.of("texturen"));
		}
		assertEquals(regularFileCount(packsDir), 0, "ohne Zustimmung darf nichts nach packs/ kopiert werden");

		installer.confirmCapabilitiesAndInstall("beispiel.faehigkeitspaket", List.of("texturen"), request,
				"example.invalid");

		String profileId = VitrinePackProvider.profileId("beispiel.faehigkeitspaket");
		PackState state = PackStateStore.load(vitrineConfigDir);
		assertTrue(state.isInstalled(profileId));
		assertTrue(state.hasCapabilityConsent("beispiel.faehigkeitspaket", List.of("texturen")));
	}

	/**
	 * ROADMAP GATE90-Beweis: kennt der Aufrufer die angemeldeten Faehigkeiten
	 * schon <em>vor</em> dem Download (im Gegensatz zum Normalfall oben, wo
	 * sie erst danach aus der echten {@code vitrine.json} bekannt werden) und
	 * fehlt dafuer die Zustimmung, darf {@link CatalogPackInstaller#install}
	 * den {@link de.spassglas.vitrine.catalog.download.DownloadTransport}
	 * ueberhaupt nicht anfassen -- weder fuer den Download selbst noch fuer
	 * irgendeine Vorab-Anfrage. {@link CountingDownloadTransport} zaehlt jeden
	 * {@code open()}-Aufruf mit; der Test treibt denselben Installer zweimal:
	 * zuerst ohne Zustimmung (erwartet {@code 0} Aufrufe), danach nach einer
	 * erteilten Zustimmung (erwartet {@code >0} Aufrufe) -- exakt die
	 * Reihenfolge "Dialog/Entscheidung vor dem Netz-Transfer", die
	 * docs/UI-SPEC.md "8. Faehigkeiten-Dialog" fuer Katalog-Pakete verlangt.
	 */
	@Test
	public void knownCapabilitiesWithoutConsentBlockTheDownloadBeforeAnyNetworkCall() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackageWithCapabilities("beispiel.vorabfaehigkeit", List.of("texturen"));
		DownloadRequest request = hostRelease("vorab.vpkg", bytes, keyPair);

		CountingDownloadTransport transport = new CountingDownloadTransport(new FileUriDownloadTransport());
		CatalogPackInstaller installer = installerWithCountingTransport(keyPair.getPublic(), transport);

		try {
			installer.install("beispiel.vorabfaehigkeit", request, "example.invalid", List.of("texturen"));
			throw new AssertionError("CatalogConsentRequiredException erwartet, aber keine Ausnahme geworfen");
		} catch (CatalogConsentRequiredException e) {
			assertEquals(e.capabilityIds(), List.of("texturen"));
		}
		assertEquals(transport.openCount(), 0,
				"ohne Zustimmung zu einer bereits bekannten Faehigkeit darf kein Download-Aufruf passieren");
		assertEquals(regularFileCount(packsDir), 0);

		PackStateStore.update(vitrineConfigDir, state -> {
			state.recordCapabilityConsent("beispiel.vorabfaehigkeit", List.of("texturen"));
			return null;
		});

		installer.install("beispiel.vorabfaehigkeit", request, "example.invalid", List.of("texturen"));

		assertTrue(transport.openCount() > 0,
				"nach erteilter Zustimmung muss der eigentliche Download tatsaechlich stattfinden");
		assertEquals(vpkgFileCount(packsDir), 1);
	}

	/** Zaehlt {@code open()}-Aufrufe und delegiert sonst unveraendert -- der Netz-Spion fuer GATE90. */
	private static final class CountingDownloadTransport
			implements de.spassglas.vitrine.catalog.download.DownloadTransport {
		private final de.spassglas.vitrine.catalog.download.DownloadTransport delegate;
		private int openCount;

		CountingDownloadTransport(de.spassglas.vitrine.catalog.download.DownloadTransport delegate) {
			this.delegate = delegate;
		}

		@Override
		public de.spassglas.vitrine.catalog.download.DownloadResponse open(java.net.URI uri, long resumeFromOffset)
				throws IOException {
			openCount++;
			return delegate.open(uri, resumeFromOffset);
		}

		int openCount() {
			return openCount;
		}
	}

	// --- ROADMAP GATE4/GATE9: echter Fortschritts-Rueckruf + Abbruch ------

	@Test
	public void installReportsRealByteCountsThroughTheProgressCallback() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		DownloadRequest request = hostRelease("mini-progress.vpkg", bytes, keyPair);

		List<long[]> calls = new ArrayList<>();
		DownloadProgress progress = (bytesRead, totalBytes) -> calls.add(new long[] { bytesRead, totalBytes });

		installer(keyPair.getPublic()).install("beispiel.minimalpaket", request, "example.invalid",
				new CancellationToken(), progress);

		assertFalse(calls.isEmpty(), "der Fortschritts-Rueckruf muss mindestens einmal aufgerufen werden");
		long[] last = calls.get(calls.size() - 1);
		assertEquals(last[0], bytes.length, "die zuletzt gemeldeten gelesenen Bytes muessen der echten Dateigroesse entsprechen");
		assertEquals(last[1], bytes.length, "totalBytes muss die echte, angemeldete Groesse sein -- kein erfundener Wert");
		assertEquals(vpkgFileCount(packsDir), 1, "der Download muss trotz Fortschritts-Rueckruf normal installiert werden");
	}

	@Test
	public void installWithAlreadyCancelledTokenThrowsCancelledExceptionAndLeavesPacksEmpty() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		DownloadRequest request = hostRelease("mini-cancel.vpkg", bytes, keyPair);

		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(PackInstallCancelledException.class, () -> installer(keyPair.getPublic())
				.install("beispiel.minimalpaket", request, "example.invalid", token, DownloadProgress.NOOP));

		assertEquals(regularFileCount(packsDir), 0, "ein abgebrochener Download darf nichts nach packs/ kopieren");
		assertFalse(PackStateStore.load(vitrineConfigDir)
				.isInstalled(VitrinePackProvider.profileId("beispiel.minimalpaket")),
				"ein abgebrochenes Paket darf nicht als installiert im state.json stehen");
	}

	@Test
	public void confirmCapabilitiesAndInstallWithProgressOverloadInstallsAfterConsent() throws Exception {
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] bytes = VpkgFixtures.minimalValidPackageWithCapabilities("beispiel.faehigkeitspaket2", List.of("texturen"));
		DownloadRequest request = hostRelease("faehig2.vpkg", bytes, keyPair);
		CatalogPackInstaller installer = installer(keyPair.getPublic());

		assertThrows(CatalogConsentRequiredException.class,
				() -> installer.install("beispiel.faehigkeitspaket2", request, "example.invalid"));

		List<long[]> calls = new ArrayList<>();
		DownloadProgress progress = (bytesRead, totalBytes) -> calls.add(new long[] { bytesRead, totalBytes });
		installer.confirmCapabilitiesAndInstall("beispiel.faehigkeitspaket2", List.of("texturen"), request,
				"example.invalid", new CancellationToken(), progress);

		assertFalse(calls.isEmpty(), "auch der erneute Download nach der Zustimmung muss echten Fortschritt melden");
		String profileId = VitrinePackProvider.profileId("beispiel.faehigkeitspaket2");
		assertTrue(PackStateStore.load(vitrineConfigDir).isInstalled(profileId));
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private static String sourceOf(PackState state, String profileId) {
		for (PackState.PackEntry entry : state.packs) {
			if (entry.packId.equals(profileId)) {
				return entry.source;
			}
		}
		return null;
	}

	private static long regularFileCount(Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile).count();
		}
	}

	private static long vpkgFileCount(Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> p.getFileName().toString().endsWith(".vpkg")).count();
		}
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

	private static final class ImmediateScheduler implements DelayedTaskScheduler {
		@Override
		public Handle scheduleOnce(Runnable task, long delayMillis) {
			task.run();
			return () -> {
			};
		}
	}
}
