package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Laedt das {@link PackageDetailModel} fuer ein einzelnes Angebot aus
 * {@code config/vitrine/library/} (ROADMAP S3, "Öffnen: aus einer S1-Kachel
 * → Detailseite"). Spiegelbild zu {@code LocalLibrarySource} (S2), das die
 * Kacheln liefert -- hier wird dasselbe {@code .vpkg} noch einmal
 * <em>rein lesend</em> geoeffnet, um Beschreibung, Faehigkeiten, Details und
 * Screenshots-Pfade zu bekommen.
 *
 * <p><b>Sicherheit:</b> es wird nur gelesen -- Zip-Sicherheitspruefung (P3)
 * vor jedem Zugriff, danach ausschliesslich das Manifest geparst (P1).
 * Nichts wird entpackt, nichts angewendet (das ist S4).</p>
 *
 * <p>Wirft nie: eine fehlende Datei, ein kaputtes Archiv oder ein
 * schema-widriges Manifest ergeben {@link Optional#empty()} plus eine
 * SLF4J-Warnung.</p>
 */
public final class LocalPackageDetailSource {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/LocalPackageDetail");

	private static final String LIBRARY_SUBDIR = "library";
	private static final String VPKG_EXTENSION = ".vpkg";

	private final Path libraryDir;

	private LocalPackageDetailSource(Path libraryDir) {
		this.libraryDir = Objects.requireNonNull(libraryDir, "libraryDir");
	}

	/** Quelle fuer {@code vitrineConfigDir/library} (im echten Spiel {@code config/vitrine/library}). */
	public static LocalPackageDetailSource forConfigDir(Path vitrineConfigDir) {
		return new LocalPackageDetailSource(vitrineConfigDir.resolve(LIBRARY_SUBDIR));
	}

	/** Quelle fuer einen beliebigen Ordner -- gedacht fuer Tests ohne echten Konfigurationsordner. */
	public static LocalPackageDetailSource forLibraryDir(Path libraryDir) {
		return new LocalPackageDetailSource(libraryDir);
	}

	/**
	 * Sucht im Bibliotheksordner die {@code .vpkg}-Datei mit
	 * {@code manifest.id() == packageId} und baut daraus ein
	 * {@link PackageDetailModel}.
	 *
	 * @param packageId Manifest-Id der gesuchten Kachel
	 * @param status    Installationszustand der Kachel (fuer Kopf-Statuszeichen und Aktionsknopf)
	 * @param catalog   alle bekannten Kachelangebote fuer "weitere Pakete des Autors" ({@code null} erlaubt)
	 * @return das Modell, oder leer, wenn keine passende, gueltige Datei gefunden wurde
	 */
	public Optional<PackageDetailModel> load(String packageId, TileStatus status, List<TileData> catalog) {
		Objects.requireNonNull(packageId, "packageId");
		Objects.requireNonNull(status, "status");

		if (!Files.isDirectory(libraryDir)) {
			LOGGER.debug("Bibliotheksordner '{}' existiert nicht -- keine Detailseite fuer '{}'.", libraryDir, packageId);
			return Optional.empty();
		}

		List<Path> candidates;
		try (Stream<Path> stream = Files.list(libraryDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			LOGGER.warn("Bibliotheksordner '{}' liess sich nicht auflisten: {}", libraryDir, e.toString());
			return Optional.empty();
		}

		for (Path vpkgFile : candidates) {
			Manifest manifest = openAndReadManifest(vpkgFile);
			if (manifest == null || !packageId.equals(manifest.id())) {
				continue;
			}
			return Optional.of(buildModel(vpkgFile, manifest, status, catalog));
		}
		LOGGER.warn("Kein Angebot mit Id '{}' im Bibliotheksordner '{}' gefunden.", packageId, libraryDir);
		return Optional.empty();
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Überlaufmenü-Eintrag
	 * "Details") -- baut das {@link PackageDetailModel} direkt aus einer
	 * bereits bekannten, installierten {@code .vpkg}-Datei (z. B. {@code
	 * LibraryEntry#file()}), ohne den Bibliotheksordner zu durchsuchen: die
	 * Bibliothek liest ihre Pakete aus {@code packs/}, nicht aus {@code
	 * library/} (siehe {@code de.spassglas.vitrine.ui.source.LocalLibrarySource}
	 * für diesen Unterschied).
	 *
	 * @return das Modell, oder leer, wenn sich die Datei nicht als gültiges
	 *         Paket öffnen lässt (Grund steht im Protokoll)
	 */
	public static Optional<PackageDetailModel> loadInstalledFile(Path vpkgFile, TileStatus status,
			List<TileData> catalog) {
		Objects.requireNonNull(vpkgFile, "vpkgFile");
		Objects.requireNonNull(status, "status");
		Manifest manifest = openAndReadManifest(vpkgFile);
		if (manifest == null) {
			return Optional.empty();
		}
		return Optional.of(buildModel(vpkgFile, manifest, status, catalog));
	}

	/**
	 * GATE69: eine lokale Installation ohne Registry-Metadaten kennt weder
	 * Veroeffentlichungszeitpunkt noch Abhaengigkeiten/Konflikte -- ehrlich
	 * null/leer (Seite zeigt "keine Angaben"). Die Pruefsumme laesst sich
	 * dagegen aus der vorliegenden Datei berechnen, derselbe Algorithmus wie
	 * vor jeder Installation (VpkgIntegrityValidator, P4).
	 *
	 * <p>GATE70: eine lokale Installation hat kein Registry-Feld "repository" --
	 * als einzig verbliebener Kandidat fuer die GitHub-Kennzahlen im Kopf
	 * bleibt das optionale Manifest-Feld "homepage" (oft dasselbe Repo, aber
	 * nicht zwingend GitHub; GitHubRepositoryReference.parse prueft das).</p>
	 */
	private static PackageDetailModel buildModel(Path vpkgFile, Manifest manifest, TileStatus status,
			List<TileData> catalog) {
		return PackageDetailModel.of(manifest, status, PackageInstallPhase.IDLE,
				fileSize(vpkgFile), null, catalog == null ? List.of() : catalog, null, sha256Hex(vpkgFile),
				List.of(), List.of(), manifest.homepage(), List.of()).withSourceFile(vpkgFile);
	}

	private static long fileSize(Path vpkgFile) {
		try {
			return Files.size(vpkgFile);
		} catch (IOException e) {
			LOGGER.warn("Groesse von '{}' nicht lesbar: {}", vpkgFile, e.toString());
			return -1L;
		}
	}

	/** GATE69: Pruefsumme fuer den Kopier-Knopf der Detailseite, oder {@code null} wenn nicht berechenbar. */
	private static String sha256Hex(Path vpkgFile) {
		try {
			return VpkgIntegrityValidator.sha256Hex(vpkgFile);
		} catch (IOException e) {
			LOGGER.warn("Pruefsumme von '{}' nicht berechenbar: {}", vpkgFile, e.toString());
			return null;
		}
	}

	private static Manifest openAndReadManifest(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			return ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			LOGGER.warn("Angebot '{}' liess sich nicht als .vpkg oeffnen: {}", vpkgFile, e.toString());
			return null;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Angebot '{}' hat die Sicherheitspruefung nicht bestanden: {}", vpkgFile, e.getMessage());
			return null;
		} catch (ManifestParseException e) {
			LOGGER.warn("Angebot '{}' hat ein ungueltiges Manifest: {}", vpkgFile, e.getMessage());
			return null;
		} catch (RuntimeException e) {
			LOGGER.warn("Angebot '{}' liess sich unerwartet nicht auswerten: {}", vpkgFile, e.toString(), e);
			return null;
		}
	}
}
