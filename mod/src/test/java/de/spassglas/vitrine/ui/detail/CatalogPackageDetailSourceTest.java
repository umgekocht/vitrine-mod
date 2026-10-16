package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.catalog.CatalogTestSupportBridge;
import de.spassglas.vitrine.catalog.github.GitHubRepoStats;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
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
 * ROADMAP G3: {@link CatalogPackageDetailSource} baut die Detailseite fuer
 * ein Angebot, das nur als Katalog-Eintrag bekannt ist (kein lokales
 * {@code .vpkg}), aus dem vollen Registry-Paketeintrag (nicht aus dem
 * Kurzeintrag {@code IndexPackage}).
 */
public final class CatalogPackageDetailSourceTest {

	private Path workDir;
	private Path packagesDir;

	@BeforeMethod
	public void setUp() throws IOException {
		workDir = Files.createTempDirectory("vitrine-catalog-detail-source-test-");
		packagesDir = workDir.resolve("packages");
		Files.createDirectories(packagesDir);
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (workDir == null || !Files.exists(workDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(workDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	@Test
	public void loadsAModelFromTheFullRegistryPackageEntry() throws IOException {
		Files.writeString(packagesDir.resolve("umgekocht.klarglas.json"),
				CatalogTestSupportBridge.validRegistryPackageJson(), StandardCharsets.UTF_8);

		Optional<PackageDetailModel> loaded = CatalogPackageDetailSource
				.forTesting(CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir))
				.load("umgekocht.klarglas", TileStatus.NONE, List.of());

		PackageDetailModel model = loaded.orElseThrow();
		assertEquals(model.manifestId(), "umgekocht.klarglas");
		assertEquals(model.header().name(), "Klarglas");
		assertEquals(model.header().author(), "Umgekocht");
		assertEquals(model.header().type(), TilePackageType.THEME);
		assertEquals(model.actionState(), PackageActionState.INSTALL);
		// ROADMAP G3/GATE76: Faehigkeiten sind erst nach dem Download bekannt --
		// die Detailseite zeigt dafuer ehrlich "unbekannt", NICHT die falsche
		// Verneinung "fordert keine Faehigkeiten an" (Charter-Regel 2). Screenshots
		// bleiben regulaer "keine Angaben".
		assertFalse(model.capabilitiesKnown(),
				"registry-package.schema.json fuehrt kein capabilities-Feld -- unbekannt, nicht leer");
		assertFalse(model.declaresNoCapabilities(),
				"unbekannt darf nicht als 'hat wirklich keine Faehigkeiten' gezeigt werden");
		assertFalse(model.hasScreenshots());
		assertTrue(model.hasDescription());
		assertEquals(model.description().orElseThrow(), "Cleanes Graustufen-Theme.");

		// GATE69: publishedAt/sha256 kommen aus dem Registry-Versions-Eintrag --
		// "dependencies"/"conflicts" gibt es fuer Klasse-A-Versionen im Schema
		// nicht, bleiben ehrlich "keine Angaben".
		List<PackageDetailFact> details = model.details();
		assertEquals(details.get(2).labelKey(), "vitrine.detail.field.published_at");
		assertEquals(details.get(2).value().orElseThrow(), "2026-08-25T09:00:00Z");
		assertEquals(details.get(6).labelKey(), "vitrine.detail.field.checksum");
		assertEquals(details.get(6).value().orElseThrow(), "a".repeat(64));
		assertTrue(details.get(6).copyable());
		assertTrue(model.hasCopyableChecksum());
		assertFalse(details.get(4).isKnown(), "Klasse A kennt keine 'dependencies' im Registry-Schema");
		assertFalse(details.get(5).isKnown(), "kein Konfliktfeld im Registry-Schema");

		// GATE70: das Autoren-Repo kommt aus dem Pflichtfeld "repository" des
		// Registry-Eintrags -- die GitHub-Kennzahlen selbst bleiben synchron
		// beim Bauen des Modells dauerhaft UNKNOWN, der Abruf ist Sache der
		// Detailseite (PackageDetailScreen#maybeFetchRepoStats), nicht dieser
		// Quelle.
		assertEquals(model.repositoryUrl().orElseThrow(), "https://github.com/umgekocht/vitrine-packs-starter");
		assertEquals(model.header().repoStats(), GitHubRepoStats.UNKNOWN);

		// GATE70-FOLLOW2: Rueckwaertskompatibilitaet -- dieser Registry-Eintrag
		// traegt kein "minecraftVersions" (aelteres Format), der Kopfbereich
		// zeigt dafuer ehrlich "keine Angaben" statt zu raten oder abzustuerzen.
		assertTrue(model.header().minecraftVersions().isEmpty(),
				"Registry-Eintrag ohne 'minecraftVersions' darf keine Angabe erfinden");

		// GATE77: Klasse A -- der Satz "Fuehrt keinen Programmcode aus." darf
		// bei einem echten Ressourcen-Angebot weiterhin erscheinen.
		assertTrue(model.runsNoProgramCode(), "Klasse A (contentClass fehlt) fuehrt keinen Programmcode aus");
	}

	/**
	 * GATE77: ein Klasse-B-Registry-Eintrag ({@code contentClass: "javamod"})
	 * hat keine {@code versions[]} -- die Versionsdaten stecken in
	 * {@code javaModVersions[]}. Vorher scheiterte {@link CatalogPackageDetailSource#load}
	 * hier an der leeren Liste und lieferte {@link Optional#empty()}. Vor
	 * allem aber: so ein Angebot fuehrt tatsaechlich Programmcode aus und
	 * braucht einen Neustart -- {@code runsNoProgramCode()} muss {@code false}
	 * sein, sonst zeigt die Detailseite die falsche Verneinung neben
	 * "Neustart nötig" (Charter-Regel 2).
	 */
	@Test
	public void javaModEntryLoadsAModelThatDoesNotClaimToRunNoCode() throws IOException {
		Files.writeString(packagesDir.resolve("umgekocht.testmod.json"),
				CatalogTestSupportBridge.validRegistryPackageJsonJavaMod(), StandardCharsets.UTF_8);

		Optional<PackageDetailModel> loaded = CatalogPackageDetailSource
				.forTesting(CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir))
				.load("umgekocht.testmod", TileStatus.NONE, List.of());

		PackageDetailModel model = loaded.orElseThrow(
				() -> new AssertionError("Klasse-B-Eintrag muss ein Modell liefern, nicht leer bleiben"));
		assertEquals(model.manifestId(), "umgekocht.testmod");
		assertFalse(model.runsNoProgramCode(),
				"Klasse B (.jar) fuehrt Programmcode aus -- der Satz darf nicht erscheinen");

		List<PackageDetailFact> details = model.details();
		assertEquals(details.get(2).value().orElseThrow(), "2026-08-25T09:00:00Z");
		assertEquals(details.get(4).value().orElseThrow(), "fabric-api",
				"javaModVersions[].dependencies landet in der 'Abhaengigkeiten'-Zeile");
		assertEquals(details.get(6).value().orElseThrow(), "a".repeat(64));
	}

	@Test
	public void loadsMinecraftVersionsFromTheFullRegistryPackageEntryWhenPresent() throws IOException {
		Files.writeString(packagesDir.resolve("spassglas.steinbrocken-textur.json"),
				CatalogTestSupportBridge.validRegistryPackageJsonWithMinecraftVersions(), StandardCharsets.UTF_8);

		Optional<PackageDetailModel> loaded = CatalogPackageDetailSource
				.forTesting(CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir))
				.load("spassglas.steinbrocken-textur", TileStatus.NONE, List.of());

		PackageDetailModel model = loaded.orElseThrow();
		// GATE70-FOLLOW2: 'minecraftVersions' haengt am Paket (registry-package.schema.json),
		// der Kopfbereich zeigt eine echte Angabe statt "keine Angaben".
		assertEquals(model.header().minecraftVersions().orElseThrow(), "1.21.1, 1.21.4");
	}

	@Test
	public void unknownPackageIdYieldsEmpty() throws IOException {
		Optional<PackageDetailModel> loaded = CatalogPackageDetailSource
				.forTesting(CatalogTestSupportBridge.fileRegistryPackageClient(packagesDir))
				.load("nicht-vorhanden", TileStatus.NONE, List.of());

		assertTrue(loaded.isEmpty());
	}
}
