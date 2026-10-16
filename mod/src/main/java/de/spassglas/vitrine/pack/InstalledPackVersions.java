package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Produktions-Verdrahtung von {@link InstalledVersionLookup} (ROADMAP GATE80):
 * liest die installierte Version eines Pakets direkt aus seiner {@code .vpkg}-
 * Datei in {@code config/vitrine/packs/} -- rein lesend, dieselbe Zip-Sicherheits-
 * und Manifest-Pruefung wie {@code LibraryModel#readEntry}. Wirft nie: eine
 * fehlende Datei, eine kaputte Zip oder ein ungueltiges Manifest ergibt
 * {@link Optional#empty()} (geloggt) statt eines Absturzes -- der Aufrufer
 * behandelt das wie "keine installierte Version bekannt" (kein Update).
 */
public final class InstalledPackVersions {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/InstalledVersions");

	private InstalledPackVersions() {
	}

	/** Seam gebunden an {@code vitrineConfigDir} (im echten Spiel {@code config/vitrine}). */
	public static InstalledVersionLookup forConfigDir(Path vitrineConfigDir) {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		return manifestId -> readVersion(provider, manifestId);
	}

	private static Optional<String> readVersion(VitrinePackProvider provider, String manifestId) {
		Optional<Path> file = provider.findPackFile(VitrinePackProvider.profileId(manifestId));
		if (file.isEmpty()) {
			return Optional.empty();
		}
		try (VpkgArchive archive = VpkgArchive.open(file.get())) {
			VpkgSecurityValidator.requireSafe(archive);
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			return Optional.of(manifest.version());
		} catch (IOException e) {
			LOGGER.warn("Installierte Version von '{}' liess sich nicht lesen (Datei '{}'): {}",
					manifestId, file.get(), e.toString());
			return Optional.empty();
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Installierte Version von '{}' liess sich nicht lesen -- Sicherheitspruefung fehlgeschlagen: {}",
					manifestId, e.getMessage());
			return Optional.empty();
		} catch (ManifestParseException e) {
			LOGGER.warn("Installierte Version von '{}' liess sich nicht lesen -- Manifest ungueltig: {}",
					manifestId, e.getMessage());
			return Optional.empty();
		} catch (RuntimeException e) {
			LOGGER.warn("Installierte Version von '{}' liess sich unerwartet nicht lesen: {}",
					manifestId, e.toString(), e);
			return Optional.empty();
		}
	}
}
