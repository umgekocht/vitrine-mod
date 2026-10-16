package de.spassglas.vitrine.ui.screen;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.classb.JavaModStagingException;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.PackInstaller;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.tile.TileStatus;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * ROADMAP GATE34-Beweis: vorher fingen {@code runInstall}/{@code
 * runCatalogInstall}/{@code confirmCatalogInstall}/{@code toggleActive} jeden
 * {@link PackInstallException} nur mit einem Log-Eintrag ab und riefen danach
 * still {@code returnToParent()} -- eine abgelehnte Installation (Zip-Slip,
 * kaputte Signatur) sah optisch genauso aus wie eine erfolgreiche. Dieser Test
 * treibt einen echten {@link PackInstaller} gegen zwei boesartige Fixtures aus
 * {@link VpkgFixtures} (ohne FabricLoader/MinecraftClient, wie {@code
 * PackInstallerTest}), faengt die dabei geworfene, echte {@link
 * PackInstallException} ab und fuettert sie in {@link
 * PackageDetailScreen#showInstallError(PackInstallException)} -- denselben
 * Weg, den alle vier Fangstellen jetzt nutzen. Der Bildschirm selbst laesst
 * sich ohne laufenden Client nicht rendern (kein Screen-Test im gesamten
 * Projekt tut das, siehe {@code FabricInstalledModConflictCheckTest}), aber
 * der sichtbare Zustand -- {@link PackageDetailScreen#installError()} -- ist
 * ein waschechtes, client-freies Feld und beweist damit den Unterschied.
 */
public final class PackageDetailScreenInstallErrorTest {

	private Path vitrineConfigDir;
	private Path libraryDir;
	private Path packsDir;
	private PackInstaller installer;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-detailscreen-installerror-test-");
		libraryDir = vitrineConfigDir.resolve("library");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(libraryDir);
		Files.createDirectories(packsDir);

		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator coordinator = new HotApplyCoordinator(
				new NoopPackActivation(), new NoopReloadTrigger(), new SilentToastPresenter(), new ImmediateScheduler());
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
	public void zipSlipFixtureProducesVisibleErrorInsteadOfSilentReturn() throws Exception {
		writeLibraryPackage("boese.vpkg", VpkgFixtures.zipSlipPackage());

		PackInstallException failure = expectThrows(PackInstallException.class,
				() -> installer.install("beispiel.zipslip"));
		assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank(),
				"PackInstallException muss einen Klartext-Grund tragen (Vorbedingung fuer GATE34)");

		PackageDetailScreen screen = new PackageDetailScreen(null, minimalModel());
		assertTrue(screen.installError().isEmpty(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");

		screen.showInstallError(failure);

		assertTrue(screen.installError().isPresent(),
				"eine abgelehnte Zip-Slip-Installation muss einen sichtbaren Fehlerzustand setzen, statt "
						+ "still zu bleiben (vorher: nur geloggt + returnToParent())");

		// ROADMAP GATE48/GATE66: der sichtbare Text ist nicht mehr e.getMessage() (rohe
		// Protokoll-Fassung -- kann absolute Pfade/rohe Throwable-Fragmente enthalten),
		// sondern der uebersetzte, sichere displayKey()/displayArgs()-Text aus den
		// Sprachdateien.
		assertTrue(failure.displayKey() != null && !failure.displayKey().isBlank(),
				"PackInstallException einer Zip-Sicherheitsverletzung muss einen Uebersetzungs-Key tragen "
						+ "(GATE48), statt nur eine rohe Protokoll-Meldung");
		String expectedText = Translations.format(Translations.FALLBACK_LOCALE, failure.displayKey(),
				(Object[]) failure.displayArgs());
		assertEquals(screen.installError().get(), expectedText,
				"der sichtbare Text muss der echte, uebersetzte Klartext-Grund sein, keine generische/leere Meldung");
		assertFalse(screen.installError().get().contains(libraryDir.toString()),
				"der sichtbare Text darf keinen absoluten Dateipfad enthalten (GATE48)");
		assertFalse(screen.installError().get().contains("../../../evil.txt"),
				"der sichtbare Text darf die rohe Zip-Slip-Pfadangabe aus der Ausnahme nicht enthalten (GATE48)");
	}

	@Test
	public void tamperedSignatureFixtureProducesVisibleErrorInsteadOfSilentReturn() throws Exception {
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Path vpkg = libraryDir.resolve("mani.vpkg");
		Files.write(vpkg, bytes);
		// gueltige Pruefsumme, aber eine Signatur ueber voellig andere Bytes.
		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] wrongSignature = VpkgFixtures.signSha256Digest(new byte[] { 1, 2, 3 }, keyPair.getPrivate());
		writeSidecar(vpkg, VpkgFixtures.sha256Hex(bytes), wrongSignature, keyPair.getPublic());

		PackInstallException failure = expectThrows(PackInstallException.class,
				() -> installer.install("beispiel.minimalpaket"));

		PackageDetailScreen screen = new PackageDetailScreen(null, minimalModel());
		screen.showInstallError(failure);

		assertTrue(screen.installError().isPresent(),
				"eine abgelehnte Signaturpruefung muss einen sichtbaren Fehlerzustand setzen, statt still "
						+ "zurueckzukehren");
		assertFalse(screen.installError().get().isBlank());
	}

	/**
	 * ROADMAP GATE78-Beweis: {@link PackageDetailScreen#friendlyFailureReason}
	 * darf fuer keinen bekannten Ursachentyp einen rohen Java-Klassennamen
	 * liefern -- weder "Exception" als Teilstring noch die rohe {@link
	 * Throwable#getMessage()} (kann Pfade enthalten). IOException und
	 * SecurityException muessen auf ihre eigenen, uebersetzten Gruende
	 * abbilden; ein sonst unbekannter Fehler faellt auf den generischen,
	 * ebenso uebersetzten "unbekannter Grund"-Text zurueck.
	 */
	@Test
	public void friendlyFailureReasonMapsIoExceptionWithoutClassNameOrRawMessage() {
		String reason = PackageDetailScreen.friendlyFailureReason(
				new IOException("rohe interne Meldung mit Pfad " + libraryDir));
		assertFalse(reason.contains("Exception"), "kein Java-Klassenname im Grundtext: " + reason);
		assertFalse(reason.contains(libraryDir.toString()), "kein roher Pfad im Grundtext: " + reason);
		assertEquals(reason, Translations.format(Translations.FALLBACK_LOCALE, "vitrine.detail.error.reason.io"));
	}

	@Test
	public void friendlyFailureReasonMapsSecurityExceptionWithoutClassName() {
		String reason = PackageDetailScreen.friendlyFailureReason(new SecurityException("signatur kaputt"));
		assertFalse(reason.contains("Exception"), "kein Java-Klassenname im Grundtext: " + reason);
		assertEquals(reason,
				Translations.format(Translations.FALLBACK_LOCALE, "vitrine.detail.error.reason.security"));
	}

	@Test
	public void friendlyFailureReasonMapsUnknownCauseToGenericFallbackWithoutClassName() {
		String reason = PackageDetailScreen.friendlyFailureReason(new IllegalStateException("mysteriös"));
		assertFalse(reason.contains("Exception"), "kein Java-Klassenname im Grundtext: " + reason);
		assertFalse(reason.contains("IllegalStateException"));
		assertEquals(reason,
				Translations.format(Translations.FALLBACK_LOCALE, "vitrine.detail.error.reason.unknown"));
	}

	@Test
	public void friendlyFailureReasonPrefersJavaModStagingExceptionDisplayKeyOverClassName() {
		JavaModStagingException je = new JavaModStagingException("interner Grund mit Pfad " + libraryDir,
				"vitrine.classb.error.already_staged", "demo.pack");
		String reason = PackageDetailScreen.friendlyFailureReason(je);
		assertFalse(reason.contains("Exception"), "kein Java-Klassenname im Grundtext: " + reason);
		assertEquals(reason, Translations.format(Translations.FALLBACK_LOCALE,
				"vitrine.classb.error.already_staged", "demo.pack"));
	}

	/**
	 * ROADMAP GATE78: die drei betroffenen Fangstellen ({@code
	 * beginJavaModStaging}, {@code beginCatalogInstall} und der generische
	 * {@link PackageDetailScreen#showInstallError} Rueckfall) setzen alle
	 * auf {@link PackageDetailScreen#friendlyFailureReason} statt {@code
	 * e.getClass().getSimpleName()}. Dieser Test treibt den generischen
	 * Rueckfall end-to-end (eine {@link PackInstallException} ohne
	 * displayKey, verursacht durch eine {@link IOException}) und beweist,
	 * dass der tatsaechlich sichtbare Text weder "Exception" noch die rohe
	 * interne Meldung enthaelt.
	 */
	@Test
	public void showInstallErrorGenericFallbackNeverExposesJavaClassName() {
		PackInstallException failure = new PackInstallException("interne Protokoll-Meldung",
				new IOException("rohe interne Ursache mit Pfad " + libraryDir));
		assertTrue(failure.displayKey() == null, "Testfall muss den generischen, displayKey-losen Zweig treffen");

		PackageDetailScreen screen = new PackageDetailScreen(null, minimalModel());
		screen.showInstallError(failure);

		String shown = screen.installError().orElseThrow();
		assertFalse(shown.contains("Exception"), "sichtbarer Fehlertext enthaelt einen Java-Klassennamen: " + shown);
		assertFalse(shown.contains("interne Protokoll-Meldung"), shown);
		assertFalse(shown.contains(libraryDir.toString()), shown);
	}

	private static PackageDetailModel minimalModel() {
		Manifest manifest = new Manifest(1, "solo.pack", "Solo", "1.0.0", "texturen", "solo",
				null, List.of(), null, List.of(), null, null);
		return PackageDetailModel.of(manifest, TileStatus.NONE);
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

	// --- Test-Doubles (wie PackInstallerTest, nur ohne Aufzeichnung -- hier interessiert nur der Fehlerpfad) ---

	private static final class NoopPackActivation implements PackActivation {
		@Override
		public boolean activate(String packId) {
			return true;
		}

		@Override
		public boolean deactivate(String packId) {
			return true;
		}
	}

	private static final class NoopReloadTrigger implements ResourceReloadTrigger {
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
