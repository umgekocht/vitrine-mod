package de.spassglas.vitrine.ui.screen;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE112, zweiter Teil des woertlichen Fertig-Kriteriums: "Sperre
 * waehrend 'Alle aktualisieren' auch auf den einzelnen Zeilen-Schaltern."
 *
 * <p>Vorher sperrte {@code updatingAll} nur den eigenen Knopf (ROADMAP GATE9,
 * {@code updateAllButton.active = !updatingAll}) und je Zeile nur den
 * Ueberlaufmenue-Eintrag "Aktualisieren" ({@code updatingPackIds}, GATE83) --
 * der An/Aus-Schalter einer Zeile ({@link LibraryScreen#onToggleActive}) blieb
 * waehrend eines laufenden Sammel-Updates uneingeschraenkt bedienbar und
 * konnte {@link LibraryModel#setActive} parallel zum selben, gerade
 * aktualisierten Paket auf dem Client-Thread anstossen. Dieser Test treibt
 * {@link LibraryScreen} gegen ein echtes installiertes Paket (kein Fake-
 * Entry, damit {@link LibraryScreen#init()} wirklich die Zeile baut) und
 * beweist beide Haelften der Sperre: den gebauten Knopf
 * ({@link LibraryScreen#isToggleActiveFor(String)}) und den direkten Aufruf
 * ({@link LibraryScreen#onToggleActive}, zweite Verteidigungslinie).</p>
 */
public final class LibraryScreenRowLockTest {

	private static final String PACK_ID = "beispiel.paket";
	private static final String PROFILE_ID = "vitrine/" + PACK_ID;

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-library-rowlock-test-");
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
	public void rowToggleIsDisabledWhileUpdateAllIsRunningAndEnabledAfterwards() throws IOException {
		installPack("a.vpkg", PACK_ID, "Beispielpaket", "1.0.0");
		writeState(PROFILE_ID);

		List<String> setActiveCalls = new ArrayList<>();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				affectedPackIds -> {
				},
				(packId, active) -> setActiveCalls.add(packId),
				packId -> {
				});

		LibraryScreen screen = new LibraryScreen(null, model);
		screen.init();

		assertTrue(screen.isToggleActiveFor(PROFILE_ID),
				"im Ruhezustand (kein Sammel-Update) muss der Zeilen-Schalter bedienbar sein");

		// --- "Alle aktualisieren" laeuft -----------------------------------
		screen.setUpdatingAllForTest(true);
		screen.init();

		assertFalse(screen.isToggleActiveFor(PROFILE_ID),
				"waehrend 'Alle aktualisieren' laeuft, muss der Knopf der einzelnen Zeile gesperrt sein -- "
						+ "genau wie der Sammel-Knopf selbst (GATE9) und die Ueberlaufmenue-Eintraege (GATE83)");

		// Zweite Verteidigungslinie: selbst ein direkter Aufruf (z.B. durch
		// Tastatur-Fokus statt eines Maus-Klicks auf den deaktivierten Knopf)
		// darf waehrend des Sammel-Laufs nicht durchgehen.
		screen.onToggleActive(findEntry(model), false);
		assertTrue(setActiveCalls.isEmpty(),
				"waehrend 'Alle aktualisieren' darf kein einzelner Aktiv-Schalter-Aufruf den Manager erreichen");

		// --- "Alle aktualisieren" ist fertig --------------------------------
		screen.setUpdatingAllForTest(false);
		screen.init();

		assertTrue(screen.isToggleActiveFor(PROFILE_ID),
				"nach dem Ende von 'Alle aktualisieren' muss der Zeilen-Schalter wieder bedienbar sein");

		screen.onToggleActive(findEntry(model), false);
		assertEquals(setActiveCalls, List.of(PROFILE_ID),
				"im Ruhezustand muss der Zeilen-Schalter ganz normal durchgehen");
	}

	// --- Hilfsmittel ----------------------------------------------

	private static de.spassglas.vitrine.ui.library.LibraryEntry findEntry(LibraryModel model) {
		return model.entries().stream().filter(e -> e.packId().equals(PROFILE_ID)).findFirst()
				.orElseThrow(() -> new AssertionError("Zeile '" + PROFILE_ID + "' nicht gefunden"));
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
