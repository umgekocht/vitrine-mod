package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.theme.DefaultThemeTokens;
import de.spassglas.vitrine.ui.theme.ThemeManager;
import de.spassglas.vitrine.ui.theme.ThemeMessagePresenter;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das woertliche Fertig-Kriterium von ROADMAP T4 ("Theme aus dem Store
 * installieren &rArr; sofort sichtbar, kein Reload"), headless und client-frei so
 * weit moeglich: die Aktivierung laeuft ueber den echten Koordinator-Pfad
 * ({@link PackInstaller} &rarr; {@link ThemePackCoordinator} &rarr; {@link
 * ThemeManager}).
 *
 * <ul>
 *   <li>Ein Theme-{@code .vpkg} mit einer {@code theme.json}, deren
 *       {@code color.accent} sich klar vom Standard unterscheidet, installiert
 *       &rArr; {@code ThemeManager.active().color("color.accent")} liefert den
 *       neuen Wert, der Repaint-Zuhoerer wurde <b>genau einmal</b> gerufen, und
 *       das {@link ResourceReloadTrigger}-Double zaehlt <b>0</b> Aufrufe.</li>
 *   <li>Deaktivieren ({@code setActive(..., false)}) &rArr; zurueck auf den
 *       Standard, Zuhoerer erneut gerufen, weiterhin kein Reload.</li>
 *   <li>Kaputte {@code theme.json} im Paket &rArr; die Installation gelingt
 *       trotzdem, das alte Theme bleibt aktiv, eine Klartext-Meldung ging raus.</li>
 * </ul>
 */
public final class ThemePackCoordinatorTest {

	private static final String THEME_MANIFEST_ID = "beispiel.themepaket";
	private static final String THEME_PROFILE_ID = "vitrine/beispiel.themepaket";
	private static final int RED_ARGB = 0xFFFF0000;

	private Path vitrineConfigDir;
	private Path libraryDir;
	private Path packsDir;

	private CountingReloadTrigger reloadTrigger;
	private RecordingPresenter presenter;
	private ThemeManager themeManager;
	private ThemePackCoordinator coordinator;
	private PackInstaller installer;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-themepack-test-");
		libraryDir = vitrineConfigDir.resolve("library");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(libraryDir);
		Files.createDirectories(packsDir);

		// GATE29: Fixtures muessen gegen den Test-Override von
		// SigningKeyTestSupport signieren, nicht mehr gegen einen frei
		// erfundenen Sidecar-Schluessel.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());

		reloadTrigger = new CountingReloadTrigger();
		presenter = new RecordingPresenter();
		themeManager = new ThemeManager(DefaultThemeTokens.INSTANCE, presenter);

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator hotApply = new HotApplyCoordinator(
				new NoopPackActivation(), reloadTrigger, new SilentToastPresenter(), new ImmediateScheduler());
		coordinator = new ThemePackCoordinator(themeManager, vitrineConfigDir, provider::findPackFile);
		installer = new PackInstaller(provider, hotApply, coordinator);
	}

	@AfterMethod
	public void tearDown() throws Exception {
		SigningKeyTestSupport.reset();
		if (vitrineConfigDir == null || !Files.exists(vitrineConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(vitrineConfigDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// --- 1. Installieren -> neuer Token sofort aktiv, ein Repaint, KEIN Reload ---

	@Test
	public void installingThemePackAppliesAccentHotWithoutResourceReload() throws Exception {
		writeLibraryPackage("thema.vpkg",
				VpkgFixtures.themePackage(THEME_MANIFEST_ID, "Rotes Theme", themeJsonWithDarkAccent("#ff0000")));

		int defaultAccent = themeManager.active().color("color.accent");
		assertNotEquals(defaultAccent, RED_ARGB, "Testvoraussetzung: der Standard-Akzent ist nicht schon rot");

		AtomicInteger repaints = new AtomicInteger();
		themeManager.addRepaintListener(repaints::incrementAndGet);

		// GATE32: themePackage() meldet die Faehigkeit "theme" jetzt an --
		// install() erwartet, wie jede andere Faehigkeit, dass ihr schon
		// zugestimmt wurde (Charter-Regel 4).
		installer.confirmCapabilitiesAndInstall(THEME_MANIFEST_ID, List.of("theme"));

		assertEquals(themeManager.active().color("color.accent"), RED_ARGB,
				"das frisch installierte Theme muss sofort aktiv sein (kein Reload, kein Neustart)");
		assertEquals(repaints.get(), 1, "der Repaint-Zuhoerer wurde genau einmal benachrichtigt");
		assertEquals(reloadTrigger.callCount, 0,
				"ein Klasse-A-Theme darf keinen Ressourcen-Reload ausloesen (ROADMAP T4)");
		assertTrue(PackStateStore.load(vitrineConfigDir).isActive(THEME_PROFILE_ID),
				"das Theme-Paket muss als installiert und aktiv gefuehrt werden");
		assertEquals(coordinator.activeThemePackId().orElse(null), THEME_PROFILE_ID);
	}

	// --- 1b. GATE52: ein ECHTER Designer-Export (nicht VpkgFixtures) wirkt im Mod ---

	@Test
	public void realDesignerExportInstallsAndSwitchesThemeHot() throws Exception {
		// Erzeugt von designer/tools/make-mod-theme-fixture.js ueber den echten
		// Exportpfad des Web-Designers (ensureTheme + buildVpkgPackage).
		byte[] exported;
		try (java.io.InputStream in = getClass().getResourceAsStream("designer-export-theme.vpkg")) {
			org.testng.Assert.assertNotNull(in, "Test-Ressource designer-export-theme.vpkg fehlt");
			exported = in.readAllBytes();
		}
		writeLibraryPackage("designer-export-theme.vpkg", exported);

		Runnable applySizes = () -> de.spassglas.vitrine.ui.theme.VitrineTheme.applyFrom(themeManager::size);
		themeManager.addRepaintListener(applySizes);
		try {
			installer.confirmCapabilitiesAndInstall("designer.exporttheme", List.of("theme"));

			assertEquals(themeManager.active().color("color.accent"), 0xFF12AB34,
					"Akzent aus der Designer-theme.json muss sofort aktiv sein");
			assertEquals(themeManager.active().size("header.height"), 77);
			assertEquals(de.spassglas.vitrine.ui.theme.VitrineTheme.HEADER_HEIGHT, 77,
					"auch die Masze des Designer-Themes kommen im Layout an (GATE21)");
			assertEquals(reloadTrigger.callCount, 0, "Theme wechselt hot, ohne Ressourcen-Reload");
		} finally {
			themeManager.removeRepaintListener(applySizes);
			de.spassglas.vitrine.ui.theme.VitrineTheme.applyFrom(DefaultThemeTokens.INSTANCE::size);
		}
	}

	// --- 2. Deaktivieren -> zurueck auf Standard, erneuter Repaint, kein Reload ---

	@Test
	public void deactivatingThemePackResetsToDefaultAndNotifiesAgain() throws Exception {
		writeLibraryPackage("thema.vpkg",
				VpkgFixtures.themePackage(THEME_MANIFEST_ID, "Rotes Theme", themeJsonWithDarkAccent("#ff0000")));

		int defaultAccent = themeManager.active().color("color.accent");

		AtomicInteger repaints = new AtomicInteger();
		themeManager.addRepaintListener(repaints::incrementAndGet);

		// GATE32: themePackage() meldet die Faehigkeit "theme" jetzt an --
		// install() erwartet, wie jede andere Faehigkeit, dass ihr schon
		// zugestimmt wurde (Charter-Regel 4).
		installer.confirmCapabilitiesAndInstall(THEME_MANIFEST_ID, List.of("theme"));
		assertEquals(themeManager.active().color("color.accent"), RED_ARGB);

		installer.setActive(THEME_PROFILE_ID, false);

		assertEquals(themeManager.active().color("color.accent"), defaultAccent,
				"nach dem Deaktivieren muss der Standard-Akzent wieder gelten");
		assertEquals(repaints.get(), 2, "Aktivieren und Deaktivieren benachrichtigen den Zuhoerer je einmal");
		assertEquals(reloadTrigger.callCount, 0, "auch das Deaktivieren eines Themes loest keinen Reload aus");
		assertTrue(coordinator.activeThemePackId().isEmpty(), "kein aktives Theme-Paket mehr");
	}

	// --- 3. Kaputte theme.json -> Installation ok, altes Theme bleibt, Meldung ---

	@Test
	public void brokenThemeJsonStillInstallsKeepsCurrentThemeAndReportsMessage() throws Exception {
		writeLibraryPackage("kaputt.vpkg", VpkgFixtures.themePackage(
				THEME_MANIFEST_ID, "Kaputtes Theme", "{ das ist kein gueltiges JSON ".getBytes(StandardCharsets.UTF_8)));

		int defaultAccent = themeManager.active().color("color.accent");
		AtomicInteger repaints = new AtomicInteger();
		themeManager.addRepaintListener(repaints::incrementAndGet);

		// GATE32: themePackage() meldet die Faehigkeit "theme" jetzt an --
		// install() erwartet, wie jede andere Faehigkeit, dass ihr schon
		// zugestimmt wurde (Charter-Regel 4).
		installer.confirmCapabilitiesAndInstall(THEME_MANIFEST_ID, List.of("theme"));

		assertEquals(themeManager.active().color("color.accent"), defaultAccent,
				"eine kaputte theme.json darf das aktive Theme nicht veraendern");
		assertEquals(repaints.get(), 0, "ein abgelehntes Theme loest keinen Umfaerb-Rueckruf aus");
		assertEquals(presenter.messages.size(), 1, "genau eine Klartext-Meldung ueber das kaputte Theme");
		assertEquals(reloadTrigger.callCount, 0);
		assertTrue(PackStateStore.load(vitrineConfigDir).isInstalled(THEME_PROFILE_ID),
				"das Paket ist installiert -- nur das Theme griff nicht");
		assertTrue(coordinator.activeThemePackId().isEmpty(),
				"ein Paket mit kaputter theme.json wird nicht als aktives Theme gemerkt");
	}

	// --- 4. GATE103: unsigniertes Theme-Paket direkt in packs/ wird nicht angewandt ---

	@Test
	public void activatingUnsignedThemePackageInPacksDirIsRejected() throws Exception {
		// Simuliert ein .vpkg, das direkt in packs/ liegt, ohne ueber
		// PackInstaller (und damit ohne Pruefsumme/Signatur) installiert worden
		// zu sein -- genau der vom Kritiker gefundene Weg (GATE103).
		byte[] packageBytes = VpkgFixtures.themePackage(THEME_MANIFEST_ID, "Unsigniert",
				themeJsonWithDarkAccent("#ff0000"));
		Files.write(packsDir.resolve("unsigniert.vpkg"), packageBytes);
		// Bewusst KEINE .vpkg.integrity.json-Begleitdatei.

		int defaultAccent = themeManager.active().color("color.accent");

		coordinator.onThemePackActivated(THEME_PROFILE_ID);

		assertEquals(themeManager.active().color("color.accent"), defaultAccent,
				"ein unsigniertes Theme-Paket darf das Theme nicht umfaerben (GATE103)");
		assertTrue(coordinator.activeThemePackId().isEmpty(),
				"ein unsigniertes Theme-Paket darf nicht als aktives Theme gemerkt werden");
	}

	// --- 5. GATE105: signiertes Theme-Paket ohne erteilte Zustimmung wird nicht angewandt ---

	@Test
	public void activatingThemePackageWithoutCapabilityConsentIsRejected() throws Exception {
		byte[] packageBytes = VpkgFixtures.themePackage(THEME_MANIFEST_ID, "Ohne Zustimmung",
				themeJsonWithDarkAccent("#ff0000"));
		Path vpkgFile = packsDir.resolve("ohne-zustimmung.vpkg");
		Files.write(vpkgFile, packageBytes);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());
		// Das Paket ist "installiert" (state.json kennt es), aber der
		// Faehigkeiten-Dialog wurde nie bestaetigt (GATE105) -- im Unterschied
		// zu confirmCapabilitiesAndInstall() in den uebrigen Tests dieser Klasse.
		PackStateStore.update(vitrineConfigDir, state -> {
			state.addOrUpdate(THEME_PROFILE_ID, true);
			return null;
		});

		int defaultAccent = themeManager.active().color("color.accent");

		coordinator.onThemePackActivated(THEME_PROFILE_ID);

		assertEquals(themeManager.active().color("color.accent"), defaultAccent,
				"ohne erteilte Faehigkeiten-Zustimmung darf das Theme nicht greifen (GATE105)");
		assertTrue(coordinator.activeThemePackId().isEmpty(),
				"ein Theme-Paket ohne Zustimmung darf nicht als aktives Theme gemerkt werden");
	}

	// --- Hilfsfunktionen ------------------------------------------------

	private static byte[] themeJsonWithDarkAccent(String hex) {
		JsonObject theme = readKlarglas();
		theme.getAsJsonObject("dark").addProperty("color.accent", hex);
		return theme.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static JsonObject readKlarglas() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "examples", "theme", "valid", "klarglas.json");
		Path viaRepoRoot = Path.of("registry", "schema", "examples", "theme", "valid", "klarglas.json");
		Path source = Files.exists(viaModuleWorkingDir) ? viaModuleWorkingDir
				: Files.exists(viaRepoRoot) ? viaRepoRoot : null;
		if (source == null) {
			throw new IllegalStateException("klarglas.json nicht gefunden, Arbeitsverzeichnis: "
					+ Path.of("").toAbsolutePath());
		}
		try {
			return JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Konnte klarglas.json nicht lesen", e);
		}
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

	// --- Test-Doubles -------------------------------------------------

	private static final class CountingReloadTrigger implements ResourceReloadTrigger {
		private int callCount;

		@Override
		public CompletableFuture<Void> reloadResources() {
			callCount++;
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class RecordingPresenter implements ThemeMessagePresenter {
		private final List<String> messages = new ArrayList<>();

		@Override
		public void showBrokenThemeKeptCurrent(String themeName, String reason) {
			messages.add(themeName + ": " + reason);
		}
	}

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
