package de.spassglas.vitrine.ui.source;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das Fertig-Kriterium von ROADMAP S2 woertlich ab: "Datei hineinlegen,
 * Store zeigt sie ohne Neustart." Der Scanner ({@link LocalLibrarySource})
 * wird ueber ein leeres Temp-{@code library/} laufen gelassen (0 Angebote),
 * danach wird eine gueltige {@code .vpkg} hineingelegt und <em>dieselbe</em>
 * Scanner-Instanz erneut befragt (kein Neustart-Aequivalent, kein Cache) --
 * das Paket erscheint als Kachelmodell mit korrektem Name/Autor/Typ. Dazu:
 * eine kaputte Datei im Ordner reisst den Scan nicht ab, und ein fehlender
 * Ordner ergibt ein leeres Ergebnis ohne Fehler (Charter-Regel 12).
 */
public final class LocalLibrarySourceTest {

	private Path libraryDir;

	@BeforeMethod
	public void setUp() throws IOException {
		libraryDir = Files.createTempDirectory("vitrine-local-library-test-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (libraryDir == null || !Files.exists(libraryDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(libraryDir)) {
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
	public void emptyLibraryFolderYieldsNoOffers() {
		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir).scan(null);

		assertTrue(offers.isEmpty(), "ein leerer library/-Ordner darf keine Angebote liefern");
	}

	@Test
	public void missingLibraryFolderYieldsNoOffersWithoutError() {
		Path missing = libraryDir.resolve("existiert-nicht");

		List<TileData> offers = LocalLibrarySource.forLibraryDir(missing).scan(null);

		assertTrue(offers.isEmpty(),
				"ein fehlender library/-Ordner (Frischinstallation, Charter 12) ergibt leer, keinen Fehler");
	}

	@Test
	public void fileDroppedInAppearsOnNextScanWithoutRestart() throws IOException {
		// Eine Scanner-Instanz ueber den ganzen Test -- beweist, dass kein
		// Zustand zwischen den Aufrufen zwischengespeichert wird.
		LocalLibrarySource source = LocalLibrarySource.forLibraryDir(libraryDir);

		assertTrue(source.scan(null).isEmpty(), "vor dem Hineinlegen: 0 Angebote");

		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());

		List<TileData> offers = source.scan(null);

		assertEquals(offers.size(), 1, "nach dem Hineinlegen muss genau ein Angebot erscheinen -- ohne Neustart");
		TileData tile = offers.get(0);
		assertEquals(tile.id(), "beispiel.minimalpaket");
		assertEquals(tile.name(), "Minimalpaket", "Name muss aus dem Manifest stammen");
		assertEquals(tile.author(), "Vitrine-Testfixture", "Autor muss aus dem Manifest stammen");
		assertEquals(tile.type(), TilePackageType.TEXTUREN, "Typ-Badge muss aus 'category' abgeleitet sein");
		assertEquals(tile.status(), TileStatus.NONE, "solange nicht installiert: kein Statuszeichen");
	}

	@Test
	public void brokenFileInFolderIsSkippedAndRestOfListStillAppears() throws IOException {
		Files.write(libraryDir.resolve("gueltig.vpkg"), VpkgFixtures.minimalValidPackage());
		// keine gueltige ZIP-Datei -- muss uebersprungen werden, nicht crashen
		Files.write(libraryDir.resolve("kaputt.vpkg"),
				"das ist kein ZIP-Archiv".getBytes(StandardCharsets.UTF_8));
		// gueltiges ZIP, aber boesartiges Manifest/Zip-Slip -- ebenfalls raus
		Files.write(libraryDir.resolve("boesartig.vpkg"), VpkgFixtures.zipSlipPackage());

		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir).scan(null);

		assertEquals(offers.size(), 1, "nur das gueltige Paket bleibt uebrig, der Scan bricht nicht ab");
		assertEquals(offers.get(0).id(), "beispiel.minimalpaket");
	}

	@Test
	public void nonVpkgFilesAreIgnored() throws IOException {
		Files.write(libraryDir.resolve("liesmich.txt"), "hallo".getBytes(StandardCharsets.UTF_8));
		Files.createDirectory(libraryDir.resolve("irgendein-unterordner"));

		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir).scan(null);

		assertTrue(offers.isEmpty(), "nur *.vpkg-Dateien zaehlen als Angebot");
	}

	@Test
	public void installedPackageIsMarkedInstalledViaPackState() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());

		PackState rawId = PackState.createDefault();
		rawId.addOrUpdate("beispiel.minimalpaket", false);
		assertEquals(LocalLibrarySource.forLibraryDir(libraryDir).scan(rawId).get(0).status(),
				TileStatus.INSTALLED, "Abgleich gegen PackStateStore mit roher Manifest-ID");

		PackState profileId = PackState.createDefault();
		profileId.addOrUpdate("vitrine/beispiel.minimalpaket", true);
		assertEquals(LocalLibrarySource.forLibraryDir(libraryDir).scan(profileId).get(0).status(),
				TileStatus.INSTALLED, "Abgleich greift auch mit der Profil-ID-Form aus state.json");
	}

	// --- ROADMAP GATE80: echter Update-Status statt nur "Installiert" -------

	@Test
	public void installedPackageWithOlderLocalVersionThanTheLibraryFileShowsUpdateAvailable() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"),
				VpkgFixtures.packageWithVersion("beispiel.minimalpaket", "Minimalpaket", "1.2.0", List.of()));
		PackState state = PackState.createDefault();
		state.addOrUpdate("vitrine/beispiel.minimalpaket", true);

		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir)
				.scan(state, manifestId -> java.util.Optional.of("1.0.0"));

		assertEquals(offers.size(), 1);
		assertEquals(offers.get(0).status(), TileStatus.UPDATE_AVAILABLE,
				"die Datei in library/ ist neuer als die installierte Version -- UPDATE_AVAILABLE statt INSTALLED");
	}

	@Test
	public void installedPackageAlreadyMatchingTheLibraryFileVersionStaysInstalled() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"),
				VpkgFixtures.packageWithVersion("beispiel.minimalpaket", "Minimalpaket", "1.2.0", List.of()));
		PackState state = PackState.createDefault();
		state.addOrUpdate("vitrine/beispiel.minimalpaket", true);

		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir)
				.scan(state, manifestId -> java.util.Optional.of("1.2.0"));

		assertEquals(offers.size(), 1);
		assertEquals(offers.get(0).status(), TileStatus.INSTALLED,
				"gleiche Version installiert -- kein vorgegaukeltes Update");
	}

	@Test
	public void multiplePackagesAreReturnedSortedByFileName() throws IOException {
		Files.write(libraryDir.resolve("b.vpkg"), VpkgFixtures.minimalValidPackage());
		Files.write(libraryDir.resolve("a.vpkg"), VpkgFixtures.multiResourcePackage());

		List<TileData> offers = LocalLibrarySource.forLibraryDir(libraryDir).scan(null);

		assertEquals(offers.size(), 2);
		assertEquals(offers.get(0).id(), "beispiel.mehrfachpaket", "a.vpkg kommt vor b.vpkg");
		assertEquals(offers.get(0).type(), TilePackageType.BUNDLE);
		assertEquals(offers.get(1).id(), "beispiel.minimalpaket");
	}
}
