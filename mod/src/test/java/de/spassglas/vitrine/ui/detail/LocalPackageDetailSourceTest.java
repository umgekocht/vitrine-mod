package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP S3, Punkt 3: "Öffnen: aus einer S1-Kachel → Detailseite." Beweist,
 * dass {@link LocalPackageDetailSource} aus einer echten {@code .vpkg} im
 * Bibliotheksordner das {@link PackageDetailModel} baut -- nur lesend -- und
 * bei fehlender/kaputter/unbekannter Datei ruhig {@link Optional#empty()}
 * liefert statt zu werfen.
 */
public final class LocalPackageDetailSourceTest {

	private Path libraryDir;

	@BeforeMethod
	public void setUp() throws IOException {
		libraryDir = Files.createTempDirectory("vitrine-package-detail-test-");
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
	public void loadsModelForKnownPackageIdFromRealVpkg() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());

		Optional<PackageDetailModel> loaded = LocalPackageDetailSource.forLibraryDir(libraryDir)
				.load("beispiel.minimalpaket", TileStatus.NONE, List.of());

		assertTrue(loaded.isPresent());
		PackageDetailModel model = loaded.get();
		assertEquals(model.header().name(), "Minimalpaket");
		assertEquals(model.header().author(), "Vitrine-Testfixture");
		assertEquals(model.header().type(), TilePackageType.TEXTUREN);
		assertEquals(model.actionState(), PackageActionState.INSTALL);
		assertEquals(model.details().get(0).value().orElseThrow(), "1.0.0");
		assertTrue(model.details().get(1).isKnown(), "Groesse der .vpkg-Datei ist bekannt");

		// GATE69: Pruefsumme laesst sich aus der lokal vorliegenden Datei
		// berechnen (derselbe Algorithmus wie vor jeder Installation),
		// publishedAt/Abhaengigkeiten/Konflikte kennt eine lokale Installation
		// ohne Registry-Metadaten dagegen ehrlich nicht.
		String expectedSha256 = de.spassglas.vitrine.vpkg.VpkgIntegrityValidator
				.sha256Hex(libraryDir.resolve("minimalpaket.vpkg"));
		assertEquals(model.details().get(6).labelKey(), "vitrine.detail.field.checksum");
		assertEquals(model.details().get(6).value().orElseThrow(), expectedSha256);
		assertTrue(model.details().get(6).copyable());
		assertTrue(model.hasCopyableChecksum());
		assertFalse(model.details().get(2).isKnown(), "lokale Installation kennt kein publishedAt");
		assertFalse(model.details().get(4).isKnown(), "lokale Installation kennt keine Abhaengigkeiten");

		// GATE70: dieses Fixture-Manifest hat keine "homepage" -- ohne erkennbares
		// Autoren-Repo bleibt repositoryUrl() ehrlich leer, kein GitHub-Abruf wird
		// je angestossen, der Kopf zeigt dauerhaft "keine Angaben".
		assertTrue(model.repositoryUrl().isEmpty());
		assertTrue(model.header().minecraftVersions().isEmpty(), "Klasse A kennt keine Minecraft-Versionen");
		assertEquals(model.header().license().orElseThrow(), "CC0-1.0");
		assertEquals(model.header().version(), "1.0.0");
		assertTrue(model.header().size().isPresent(), "Groesse der .vpkg-Datei ist bekannt");
		assertEquals(model.header().repoStats(), de.spassglas.vitrine.catalog.github.GitHubRepoStats.UNKNOWN);
	}

	@Test
	public void installedStatusFlowsIntoModel() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());

		PackageDetailModel model = LocalPackageDetailSource.forLibraryDir(libraryDir)
				.load("beispiel.minimalpaket", TileStatus.INSTALLED, List.of()).orElseThrow();

		assertEquals(model.header().status(), TileStatus.INSTALLED);
		assertEquals(model.actionState(), PackageActionState.INSTALLED);
	}

	@Test
	public void moreFromAuthorIsFilteredFromCatalog() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());
		List<TileData> catalog = List.of(
				TileData.withoutImage("beispiel.minimalpaket", "Minimalpaket", "Vitrine-Testfixture",
						TilePackageType.TEXTUREN, TileStatus.NONE),
				TileData.withoutImage("beispiel.zweitpaket", "Zweitpaket", "Vitrine-Testfixture",
						TilePackageType.THEME, TileStatus.NONE),
				TileData.withoutImage("fremd.paket", "Fremd", "jemand-anderes",
						TilePackageType.HUD, TileStatus.NONE));

		PackageDetailModel model = LocalPackageDetailSource.forLibraryDir(libraryDir)
				.load("beispiel.minimalpaket", TileStatus.NONE, catalog).orElseThrow();

		assertEquals(model.moreFromAuthor().stream().map(TileData::id).toList(),
				List.of("beispiel.zweitpaket"));
	}

	@Test
	public void unknownPackageIdYieldsEmpty() throws IOException {
		Files.write(libraryDir.resolve("minimalpaket.vpkg"), VpkgFixtures.minimalValidPackage());

		assertFalse(LocalPackageDetailSource.forLibraryDir(libraryDir)
				.load("gibt.es.nicht", TileStatus.NONE, List.of()).isPresent());
	}

	@Test
	public void missingLibraryFolderYieldsEmptyWithoutError() {
		Path missing = libraryDir.resolve("existiert-nicht");

		assertFalse(LocalPackageDetailSource.forLibraryDir(missing)
				.load("beispiel.minimalpaket", TileStatus.NONE, List.of()).isPresent());
	}

	@Test
	public void brokenFileIsSkippedWhileTargetIsStillFound() throws IOException {
		Files.write(libraryDir.resolve("kaputt.vpkg"),
				"kein ZIP".getBytes(StandardCharsets.UTF_8));
		Files.write(libraryDir.resolve("boesartig.vpkg"), VpkgFixtures.zipSlipPackage());
		Files.write(libraryDir.resolve("gueltig.vpkg"), VpkgFixtures.minimalValidPackage());

		assertTrue(LocalPackageDetailSource.forLibraryDir(libraryDir)
				.load("beispiel.minimalpaket", TileStatus.NONE, List.of()).isPresent());
	}
}
