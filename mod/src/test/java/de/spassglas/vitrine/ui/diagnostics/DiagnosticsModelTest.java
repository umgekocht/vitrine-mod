package de.spassglas.vitrine.ui.diagnostics;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.pack.PackInstaller;
import de.spassglas.vitrine.pack.VitrinePackProvider;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das woertliche Fertig-Kriterium von ROADMAP X3 ab: "Zeigt fuer jedes
 * aktive Paket Quelle, Version und Pruefsumme." Zwei aktive Testpakete mit
 * bekannter Quelle/Version/Pruefsumme werden ueber den echten {@link
 * PackInstaller}-Weg installiert; {@link DiagnosticsModel#activePackages()}
 * muss pro Paket genau diese drei Werte liefern -- ein deaktiviertes drittes
 * Paket darf nicht auftauchen.
 */
public final class DiagnosticsModelTest {

	private Path vitrineConfigDir;
	private Path libraryDir;
	private Path packsDir;

	private VitrinePackProvider provider;
	private PackInstaller installer;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-diagnostics-test-");
		libraryDir = vitrineConfigDir.resolve("library");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(libraryDir);
		Files.createDirectories(packsDir);

		// GATE29: Fixtures muessen gegen den Test-Override von
		// SigningKeyTestSupport signieren, nicht mehr gegen einen frei
		// erfundenen Sidecar-Schluessel.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());

		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator coordinator = new HotApplyCoordinator(
				new NoOpPackActivation(), new ImmediateReloadTrigger(), new SilentToastPresenter(),
				new ImmediateScheduler());
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

	@Test
	public void activePackagesShowExactSourceVersionAndChecksumPerPackage() throws Exception {
		byte[] bytesA = VpkgFixtures.packageWithVersion("beispiel.diagnosea", "Diagnose-Paket A", "1.2.3",
				List.of("textures/a.png"));
		writeLibraryPackage("paket-a.vpkg", bytesA);
		String expectedShaA = VpkgFixtures.sha256Hex(bytesA);

		byte[] bytesB = VpkgFixtures.packageWithVersion("beispiel.diagnoseb", "Diagnose-Paket B", "4.5.6",
				List.of("textures/b.png"));
		writeLibraryPackage("paket-b.vpkg", bytesB);
		String expectedShaB = VpkgFixtures.sha256Hex(bytesB);

		// Ein drittes, installiertes aber DEAKTIVIERTES Paket darf nicht auftauchen.
		byte[] bytesC = VpkgFixtures.packageWithVersion("beispiel.diagnosec", "Diagnose-Paket C", "7.0.0",
				List.of("textures/c.png"));
		writeLibraryPackage("paket-c.vpkg", bytesC);

		installer.install("beispiel.diagnosea");
		installer.install("beispiel.diagnoseb");
		installer.install("beispiel.diagnosec");
		installer.setActive("vitrine/beispiel.diagnosec", false);

		DiagnosticsModel model = new DiagnosticsModel(vitrineConfigDir, provider,
				() -> Optional.of(new HotApplyCoordinator.ReloadStats(2, 42, true)));

		List<DiagnosticsEntry> entries = model.activePackages();

		assertEquals(entries.size(), 2, "nur die beiden aktiven Pakete duerfen auftauchen");

		DiagnosticsEntry entryA = findByManifestId(entries, "beispiel.diagnosea");
		assertEquals(entryA.version(), "1.2.3", "die Version muss aus dem Manifest der aktiven Zeile stammen");
		assertEquals(entryA.sha256(), expectedShaA,
				"die Pruefsumme muss die beim Installieren bereits geprueften Bytes widerspiegeln");
		assertEquals(entryA.source().kind(), DiagnosticsEntry.Source.Kind.LOCAL_LIBRARY,
				"ueber PackInstaller installierte Pakete kommen ehrlich aus dem lokalen Bibliotheksordner");

		DiagnosticsEntry entryB = findByManifestId(entries, "beispiel.diagnoseb");
		assertEquals(entryB.version(), "4.5.6");
		assertEquals(entryB.sha256(), expectedShaB);
		assertEquals(entryB.source().kind(), DiagnosticsEntry.Source.Kind.LOCAL_LIBRARY);

		assertTrue(entries.stream().noneMatch(e -> e.manifestId().equals("beispiel.diagnosec")),
				"ein deaktiviertes Paket darf im Diagnose-Bildschirm nicht als aktiv gefuehrt werden");
	}

	/**
	 * ROADMAP GATE115: vorher wurde ein solcher Eintrag nur geloggt und
	 * uebersprungen -- er verschwand lautlos aus der Diagnose, blieb aber fuer
	 * immer in {@code state.json}. Jetzt bleibt er sichtbar als kaputt/fehlend
	 * stehen (z. B. nach einem zwischen den {@code PackUpdater}-Schritten
	 * abgebrochenen Update-Lauf, dessen Backup beim Start nicht wiederherstellbar
	 * war).
	 */
	@Test
	public void activePackagesShowAnActivePackageWithoutAnyFileAsMissingInsteadOfSkippingIt() throws Exception {
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel.verwaist", true);
		PackStateStore.save(vitrineConfigDir, state);

		DiagnosticsModel model = new DiagnosticsModel(vitrineConfigDir, provider, Optional::empty);
		List<DiagnosticsEntry> entries = model.activePackages();

		assertEquals(entries.size(), 1, "das verwaiste aktive Paket darf nicht aus der Liste verschwinden");
		DiagnosticsEntry missing = entries.get(0);
		assertEquals(missing.packId(), "vitrine/beispiel.verwaist");
		assertEquals(missing.manifestId(), "beispiel.verwaist");
		assertTrue(missing.missing(), "ohne .vpkg-Datei muss der Eintrag als kaputt/fehlend markiert sein");
		assertEquals(missing.sha256(), null, "ohne Datei gibt es ehrlich keine Pruefsumme, keine erfundene");
	}

	@Test
	public void lastReloadReturnsExactlyWhatTheSeamReports() throws Exception {
		DiagnosticsModel emptyModel = new DiagnosticsModel(vitrineConfigDir, provider, Optional::empty);
		assertTrue(emptyModel.lastReload().isEmpty(), "vor jedem Reload gibt es ehrlich nichts anzuzeigen");

		HotApplyCoordinator.ReloadStats stats = new HotApplyCoordinator.ReloadStats(3, 17, false);
		DiagnosticsModel filledModel = new DiagnosticsModel(vitrineConfigDir, provider, () -> Optional.of(stats));
		assertEquals(filledModel.lastReload(), Optional.of(stats));
	}

	/**
	 * Ein Paket kann {@link PackInstaller} nie ohne gueltigen Integritaets-Beleg
	 * passieren (Default ist Verbot) -- dieser Fall bildet stattdessen einen vor
	 * X3 geschriebenen {@code state.json}-Eintrag nach: manuell (ohne Installer)
	 * in {@code packs/} abgelegte Datei, kein Integritaets-Beleg, {@code source}
	 * unbekannt ({@code null}). {@link DiagnosticsModel} darf dafuer weder eine
	 * Quelle erfinden noch die Pruefsumme verschweigen.
	 */
	@Test
	public void packageWithoutIntegritySidecarStillGetsAnActuallyComputedChecksum() throws Exception {
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Files.write(packsDir.resolve("kein-beleg.vpkg"), bytes);
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel.minimalpaket", true);
		PackStateStore.save(vitrineConfigDir, state);

		DiagnosticsModel model = new DiagnosticsModel(vitrineConfigDir, provider, Optional::empty);
		List<DiagnosticsEntry> entries = model.activePackages();

		assertEquals(entries.size(), 1);
		assertEquals(entries.get(0).sha256(), VpkgFixtures.sha256Hex(bytes),
				"ohne Integritaets-Beleg wird die tatsaechliche Pruefsumme der Datei berechnet, nicht erfunden");
		assertEquals(entries.get(0).source().kind(), DiagnosticsEntry.Source.Kind.UNKNOWN,
				"ein Eintrag ohne bekannte Quelle (z. B. vor X3 geschrieben) darf keine Quelle vortaeuschen");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private static DiagnosticsEntry findByManifestId(List<DiagnosticsEntry> entries, String manifestId) {
		return entries.stream().filter(e -> e.manifestId().equals(manifestId)).findFirst()
				.orElseThrow(() -> new AssertionError("keine Diagnose-Zeile fuer '" + manifestId + "'"));
	}

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

	// --- Test-Doubles ------------------------------------------------------

	private static final class NoOpPackActivation implements PackActivation {
		@Override
		public boolean activate(String packId) {
			return true;
		}

		@Override
		public boolean deactivate(String packId) {
			return true;
		}
	}

	private static final class ImmediateReloadTrigger implements ResourceReloadTrigger {
		@Override
		public CompletableFuture<Void> reloadResources() {
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
}
