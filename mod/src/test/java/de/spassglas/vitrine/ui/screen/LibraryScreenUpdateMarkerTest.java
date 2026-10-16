package de.spassglas.vitrine.ui.screen;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.catalog.UpdateStatus;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.library.LibraryEntry;
import de.spassglas.vitrine.ui.library.LibraryModel;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE80, woertliches Fertig-Kriterium: "echter Update-Status je
 * Paket aus dem Katalogvergleich, Bibliothekszeile zeigt welches Paket
 * betroffen ist." Vorher kannte {@code TileStatus.UPDATE_AVAILABLE} nur
 * {@link de.spassglas.vitrine.ui.tile.DemoCatalog}, {@link
 * LibraryModel#hasUpdate} hatte keinen Aufrufer, und die Bibliothekskopfzeile
 * zeigte nur eine nackte Zahl ohne zu sagen, welches Paket betroffen ist.
 *
 * <p>Installiert zwei echte Pakete ({@code A} mit veralteter, {@code B} mit
 * aktueller lokaler Version), haengt {@link LibraryModel} an einen
 * Fake-Katalog ({@link LibraryModel.CatalogVersions}) mit einer neueren
 * Version nur fuer {@code A}, baut einen echten {@link LibraryScreen} und
 * beweist: nur die Zeile von {@code A} traegt einen Update-Status, {@code B}
 * nicht -- und der daraus gebaute Zeilentext nennt A's Versionen, nicht nur
 * eine Gesamtzahl.</p>
 */
public final class LibraryScreenUpdateMarkerTest {

	private static final String A_ID = "beispiel.paketa";
	private static final String B_ID = "beispiel.paketb";
	private static final String A_PROFILE = "vitrine/" + A_ID;
	private static final String B_PROFILE = "vitrine/" + B_ID;

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-library-update-marker-test-");
		packsDir = Files.createDirectories(configDir.resolve("packs"));
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

	@Test
	public void onlyThePackageWithANewerCatalogVersionGetsAnUpdateMarkerOnItsOwnRow() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", "1.0.0");
		installPack("b.vpkg", B_ID, "Paket B", "2.0.0");
		writeState(A_PROFILE, B_PROFILE);

		// Fake-Katalog (K5-Seam): nur A hat eine neuere Version im Katalog.
		LibraryModel.CatalogVersions catalogVersions = manifestId -> switch (manifestId) {
			case A_ID -> Optional.of("1.1.0");
			case B_ID -> Optional.of("2.0.0"); // B ist schon aktuell
			default -> Optional.empty();
		};
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				affectedPackIds -> {
				}, (packId, active) -> {
				}, packId -> {
				}, catalogVersions);

		LibraryScreen screen = new LibraryScreen(null, model);
		screen.init();

		Map<String, UpdateStatus> statuses = screen.updateStatusByPackId();

		// --- A: traegt den Update-Status, GENAU auf seiner eigenen Zeile -------
		UpdateStatus aStatus = statuses.get(A_PROFILE);
		assertTrue(aStatus != null && aStatus.updateAvailable(),
				"Paket A hat eine echte neuere Katalogversion -- seine Zeile muss das zeigen");

		// --- B: keine Update-Zeile, obwohl auch installiert --------------------
		UpdateStatus bStatus = statuses.get(B_PROFILE);
		assertTrue(bStatus == null || !bStatus.updateAvailable(),
				"Paket B ist schon aktuell -- seine Zeile darf kein Update vorgaukeln");

		// --- Der Zeilentext nennt die konkreten Versionen des betroffenen Pakets, ---
		// --- nicht nur eine nackte Gesamtzahl (woertliches GATE80-Kriterium). ---
		LibraryEntry aEntry = findEntry(model, A_PROFILE);
		String aRowText = LibraryScreen.buildMetaText(aEntry, aStatus);
		assertTrue(aRowText.contains("1.0.0"), "die Zeile von A muss die installierte Version nennen: " + aRowText);
		assertTrue(aRowText.contains("1.1.0"), "die Zeile von A muss die neue Katalogversion nennen: " + aRowText);

		LibraryEntry bEntry = findEntry(model, B_PROFILE);
		String bRowText = LibraryScreen.buildMetaText(bEntry, bStatus);
		assertFalse(bRowText.contains("1.1.0"),
				"die Zeile von B darf nicht A's Update-Text uebernehmen: " + bRowText);

		assertEquals(statuses.values().stream().filter(UpdateStatus::updateAvailable).count(), 1,
				"genau ein Paket hat ein Update -- die Kopfzeile und die Zeilen-Marker muessen uebereinstimmen");
	}

	// --- Hilfsmittel ----------------------------------------------

	private static LibraryEntry findEntry(LibraryModel model, String packId) {
		return model.entries().stream().filter(e -> e.packId().equals(packId)).findFirst()
				.orElseThrow(() -> new AssertionError("Zeile '" + packId + "' nicht gefunden"));
	}

	private void installPack(String fileName, String manifestId, String name, String version) throws IOException {
		byte[] bytes = VpkgFixtures.packageWithVersion(manifestId, name, version, List.of());
		Path vpkgFile = packsDir.resolve(fileName);
		Files.write(vpkgFile, bytes);
		writeValidSidecar(vpkgFile, bytes);
	}

	private void writeState(String... profileIds) {
		PackState state = PackState.createDefault();
		for (String profileId : profileIds) {
			state.addOrUpdate(profileId, true);
		}
		try {
			PackStateStore.save(configDir, state);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(packageBytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded()));
		Path sidecar = vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + ".integrity.json");
		Files.writeString(sidecar, json.toString(), StandardCharsets.UTF_8);
	}
}
