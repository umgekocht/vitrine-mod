package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das woertliche Fertig-Kriterium von H5 ab: "Nach dem Deinstallieren
 * ist die Textur ohne Neustart wieder vanilla." Ohne echten Client heisst das
 * hier konkret (analog zu {@code VitrinePackProviderTest}s Begruendung, warum
 * ein sammelnder {@code Consumer<ResourcePackProfile>} "taucht in der Liste
 * auf" beweist):
 * <ul>
 *   <li>{@link PackActivation#deactivate} wird VOR dem Verschieben aufgerufen
 *       und der dadurch angestossene Reload abgewartet -- kein Zustand, in dem
 *       ein laufendes Spiel auf eine bereits verschobene Datei zeigen
 *       koennte.</li>
 *   <li>nach dem Deinstallieren erscheint das Paket bei einem erneuten
 *       {@link VitrinePackProvider#register} nicht mehr (die Datei liegt nicht
 *       mehr in {@code packs/}) -- kein Neustart noetig, ein einfacher
 *       erneuter Scan (das, was ein Reload tatsaechlich ausloest) reicht.</li>
 *   <li>{@code state.json} fuehrt das Paket danach nicht mehr als
 *       installiert.</li>
 *   <li>die {@code .vpkg}-Datei liegt unveraendert (Papierkorb-Prinzip, kein
 *       Loeschen) in {@code trash/}.</li>
 * </ul>
 */
public final class PackUninstallerTest {

	private Path vitrineConfigDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-uninstaller-test-");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(packsDir);

		// GATE29: die Sicherheitspruefung beim erneuten Scan (register())
		// laeuft gegen EmbeddedSigningKey.load() -- Fixtures muessen deshalb
		// mit dem Test-Override signieren.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
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

	// --- Grundfall: deaktivieren -> Reload abwarten -> verschieben -> Zustand ---

	@Test
	public void uninstallDeactivatesWaitsForReloadThenMovesFileAndUpdatesState() throws IOException {
		Path vpkgFile = writeInstalledPackage("beispiel.vpkg", "vitrine/beispiel.minimalpaket");
		installIntoState("vitrine/beispiel.minimalpaket", true);

		RecordingPackActivation packActivation = new RecordingPackActivation();
		ControllableReloadTrigger reloadTrigger = new ControllableReloadTrigger();
		PackUninstaller uninstaller = new PackUninstaller(
				VitrinePackProvider.forConfigDir(vitrineConfigDir), packActivation, reloadTrigger);

		CompletableFuture<Void> result = uninstaller.uninstall("vitrine/beispiel.minimalpaket");

		// Vor Abschluss des Reloads: deaktiviert, aber noch nichts verschoben --
		// das ist der Kern des H5-Fertig-Kriteriums (erst Reload, dann Datei).
		assertEquals(packActivation.deactivatedIds, List.of("vitrine/beispiel.minimalpaket"));
		assertTrue(Files.exists(vpkgFile), "vor dem abgeschlossenen Reload darf die Datei noch nicht verschoben sein");
		assertFalse(result.isDone());

		reloadTrigger.completeLastSuccessfully();

		assertTrue(result.isDone() && !result.isCompletedExceptionally());
		assertFalse(Files.exists(vpkgFile), "nach dem Reload muss die Datei aus packs/ verschwunden sein");

		Path trashFile = vitrineConfigDir.resolve("trash").resolve("beispiel.vpkg");
		assertTrue(Files.exists(trashFile), "die Datei muss unveraendert im Papierkorb liegen");

		PackState reloaded = PackStateStore.load(vitrineConfigDir);
		assertFalse(reloaded.isInstalled("vitrine/beispiel.minimalpaket"),
				"state.json darf das deinstallierte Paket nicht mehr fuehren");
	}

	// --- Fertig-Kriterium woertlich: Paket taucht nach dem Deinstallieren ---
	// --- ohne Neustart nicht mehr in der Ressourcen-Pack-Liste auf ---------

	@Test
	public void packNoLongerAppearsInResourcePackListAfterUninstallWithoutRestart() throws IOException {
		writeInstalledPackage("beispiel.vpkg", "vitrine/beispiel.minimalpaket");
		installIntoState("vitrine/beispiel.minimalpaket", true);

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		CollectingProfileAdder before = new CollectingProfileAdder();
		provider.register(before);
		assertEquals(before.profiles.size(), 1, "Testvoraussetzung: Paket muss vor dem Deinstallieren gelistet sein");

		RecordingPackActivation packActivation = new RecordingPackActivation();
		ControllableReloadTrigger reloadTrigger = new ControllableReloadTrigger();
		PackUninstaller uninstaller = new PackUninstaller(provider, packActivation, reloadTrigger);
		uninstaller.uninstall("vitrine/beispiel.minimalpaket");
		reloadTrigger.completeLastSuccessfully();

		// Kein Neustart -- derselbe Provider, ein erneuter Scan (das, was ein
		// echter reloadResources() im Spiel tatsaechlich ausloest).
		CollectingProfileAdder after = new CollectingProfileAdder();
		provider.register(after);
		assertTrue(after.profiles.isEmpty(),
				"nach dem Deinstallieren darf das Paket nicht mehr in der Ressourcen-Pack-Liste auftauchen");
	}

	// --- Reload schlaegt fehl: Datei wird trotzdem verschoben ---------------

	@Test
	public void uninstallStillMovesFileEvenIfReloadFails() throws IOException {
		Path vpkgFile = writeInstalledPackage("kaputt.vpkg", "vitrine/beispiel.minimalpaket");
		installIntoState("vitrine/beispiel.minimalpaket", true);

		RecordingPackActivation packActivation = new RecordingPackActivation();
		ControllableReloadTrigger reloadTrigger = new ControllableReloadTrigger();
		PackUninstaller uninstaller = new PackUninstaller(
				VitrinePackProvider.forConfigDir(vitrineConfigDir), packActivation, reloadTrigger);

		CompletableFuture<Void> result = uninstaller.uninstall("vitrine/beispiel.minimalpaket");
		reloadTrigger.completeLastExceptionally(new RuntimeException("Reload kaputt"));

		assertTrue(result.isDone() && !result.isCompletedExceptionally(),
				"ein fehlgeschlagener Reload darf das Deinstallieren selbst nicht scheitern lassen");
		assertFalse(Files.exists(vpkgFile), "die Datei muss trotz fehlgeschlagenem Reload verschoben werden");
		assertTrue(Files.exists(vitrineConfigDir.resolve("trash").resolve("kaputt.vpkg")));
	}

	// --- Namenskollision im Papierkorb: nichts wird ueberschrieben ----------

	@Test
	public void nameCollisionInTrashDoesNotOverwriteThePreviousFile() throws IOException {
		Path trashDir = vitrineConfigDir.resolve("trash");
		Files.createDirectories(trashDir);
		Path preExisting = trashDir.resolve("beispiel.vpkg");
		byte[] previousTrashContent = "ein frueher deinstalliertes, anderes Paket".getBytes(StandardCharsets.UTF_8);
		Files.write(preExisting, previousTrashContent);

		writeInstalledPackage("beispiel.vpkg", "vitrine/beispiel.minimalpaket");
		installIntoState("vitrine/beispiel.minimalpaket", false);

		RecordingPackActivation packActivation = new RecordingPackActivation();
		ControllableReloadTrigger reloadTrigger = new ControllableReloadTrigger();
		PackUninstaller uninstaller = new PackUninstaller(
				VitrinePackProvider.forConfigDir(vitrineConfigDir), packActivation, reloadTrigger);
		uninstaller.uninstall("vitrine/beispiel.minimalpaket");
		reloadTrigger.completeLastSuccessfully();

		assertEquals(Files.readAllBytes(preExisting), previousTrashContent,
				"eine bereits im Papierkorb liegende gleichnamige Datei darf nie ueberschrieben werden");

		try (Stream<Path> entries = Files.list(trashDir)) {
			long vpkgFilesInTrash = entries
					.filter(p -> p.getFileName().toString().endsWith(".vpkg"))
					.count();
			assertEquals(vpkgFilesInTrash, 2, "die neu deinstallierte Datei muss unter einem eindeutigen Namen dazukommen");
		}
	}

	// --- Hilfsfunktionen -----------------------------------------------------

	/** Legt {@code fileName} in {@link #packsDir} an -- die Manifest-ID ist immer {@code vitrine/beispiel.minimalpaket}, siehe {@link VpkgFixtures#minimalValidPackage()}. */
	private Path writeInstalledPackage(String fileName, String expectedPackId) throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = packsDir.resolve(fileName);
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);
		return vpkgFile;
	}

	private void installIntoState(String packId, boolean active) throws IOException {
		PackState state = PackStateStore.load(vitrineConfigDir);
		state.addOrUpdate(packId, active);
		PackStateStore.save(vitrineConfigDir, state);
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());
	}

	private static void writeSidecar(Path vpkgFile, String sha256, byte[] signature, PublicKey publicKey)
			throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("sha256", sha256);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
		Path sidecar = vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + ".integrity.json");
		Files.writeString(sidecar, json.toString(), StandardCharsets.UTF_8);
	}

	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}
	}

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

	private static final class ControllableReloadTrigger implements ResourceReloadTrigger {
		private final List<CompletableFuture<Void>> issuedFutures = new ArrayList<>();

		@Override
		public CompletableFuture<Void> reloadResources() {
			CompletableFuture<Void> future = new CompletableFuture<>();
			issuedFutures.add(future);
			return future;
		}

		void completeLastSuccessfully() {
			issuedFutures.get(issuedFutures.size() - 1).complete(null);
		}

		void completeLastExceptionally(Throwable throwable) {
			issuedFutures.get(issuedFutures.size() - 1).completeExceptionally(throwable);
		}
	}
}
