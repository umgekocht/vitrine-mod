package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
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
 * ROADMAP GATE115: Start-Selbstheilung fuer einen Update-Lauf, der genau
 * zwischen {@link PackUpdater}s Schritt "alte Version zur Seite legen"
 * ({@code moveInto(oldVpkg, backupVpkg)}) und "neue Version nach {@code
 * packs/} setzen" ({@code moveInto(stagedVpkg, newInPacks)}) abgebrochen ist
 * -- z. B. durch einen Spielabbruch. Zustand danach: {@code state.json}
 * fuehrt das Paket unveraendert als installiert, {@code packs/} enthaelt
 * dafuer aber keine {@code .vpkg} mehr, waehrend die alte, zuletzt
 * vollstaendig geprueft gelaufene Version unberuehrt in {@link
 * PackUpdater#backupDirFor} liegt.
 *
 * <p>{@link #recover(Path)} laeuft einmal beim Start (siehe {@code
 * Vitrine#onInitializeClient}), bevor Bibliothek oder Diagnose irgendetwas
 * anzeigen: fuer jedes in {@code state.json} installierte Paket, zu dem
 * {@link VitrinePackProvider#findPackFile} keine Datei mehr findet, wird im
 * Backup-Ordner nach einer {@code .vpkg} gesucht, deren Manifest-ID zur
 * Paket-ID passt. Erst nach derselben Pruef-Kette wie beim regulaeren
 * Anmelden ({@link VpkgSecurityValidator#requireSafe}, {@link
 * ManifestParser#parse}, {@link VitrinePackProvider#hasValidIntegrity} --
 * Charter-Regel 3: Sicherheit vor Bequemlichkeit, auch bei einer eigenen
 * frueheren Datei) wird sie zurueck nach {@code packs/} verschoben.</p>
 *
 * <p>Findet sich keine passende, gueltige Backup-Datei, bleibt das Paket wie
 * es ist -- {@code LibraryModel#entries()} und {@code
 * DiagnosticsModel#activePackages()} zeigen es dann als sichtbar
 * kaputt/fehlend an (eigener Zustand, Klartext-Hinweis), statt es weiterhin
 * stillschweigend aus der Liste zu lassen.</p>
 */
public final class OrphanedUpdateBackupRecovery {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/UpdateRecovery");
	private static final String VPKG_EXTENSION = ".vpkg";

	private OrphanedUpdateBackupRecovery() {
	}

	/**
	 * Prueft jedes in {@code state.json} installierte Paket und stellt ein
	 * verwaistes zurueck, wenn eine passende, gueltige Backup-Datei existiert.
	 *
	 * @return wie viele Pakete tatsaechlich aus dem Backup-Ordner wiederhergestellt wurden
	 */
	public static int recover(Path vitrineConfigDir) {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		Path backupDir = PackUpdater.backupDirFor(vitrineConfigDir);
		if (!Files.isDirectory(backupDir)) {
			return 0; // der weit ueberwiegende Normalfall: kein abgebrochener Update-Lauf
		}

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		PackState state = PackStateStore.load(vitrineConfigDir);
		int recovered = 0;
		for (String profileId : state.installedPackIdsInOrder()) {
			if (provider.findPackFile(profileId).isPresent()) {
				continue; // Datei ist da -- nichts zu heilen
			}
			Optional<Path> backupFile = findBackupFile(backupDir, profileId);
			if (backupFile.isEmpty()) {
				LOGGER.warn("Verwaistes Paket '{}': kein passendes Backup in '{}' gefunden -- bleibt als "
						+ "kaputt/fehlend sichtbar, bis jemand sich darum kuemmert.", profileId, backupDir);
				continue;
			}
			if (restore(provider.packsDir(), backupFile.get())) {
				recovered++;
				LOGGER.info("Verwaistes Paket '{}' aus dem Update-Backup '{}' nach 'packs/' zurueckgestellt -- "
						+ "ein abgebrochener Update-Lauf wurde geheilt.", profileId, backupFile.get());
			}
		}
		return recovered;
	}

	/**
	 * Sucht in {@code backupDir} nach der einzigen {@code .vpkg}-Datei, deren
	 * Manifest-ID (mit {@code vitrine/} versehen) zu {@code profileId} passt
	 * -- Dateinamen im Backup-Ordner sind nicht garantiert eindeutig/stabil
	 * (siehe {@code PackUpdater#uniqueTarget}), deshalb wie bei {@link
	 * VitrinePackProvider#findPackFile} ueber das Manifest statt den
	 * Dateinamen gesucht.
	 */
	private static Optional<Path> findBackupFile(Path backupDir, String profileId) {
		List<Path> candidates;
		try (Stream<Path> stream = Files.list(backupDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			LOGGER.error("Backup-Ordner '{}' liess sich nicht auflisten: {}", backupDir, e.toString(), e);
			return Optional.empty();
		}

		for (Path candidate : candidates) {
			try (VpkgArchive archive = VpkgArchive.open(candidate)) {
				VpkgSecurityValidator.requireSafe(archive);
				Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
				if (!VitrinePackProvider.profileId(manifest.id()).equals(profileId)) {
					continue;
				}
				if (!VitrinePackProvider.hasValidIntegrity(candidate)) {
					LOGGER.warn("Backup-Datei '{}' fuer '{}' hat die Pruefsummen-/Signaturpruefung nicht "
							+ "bestanden, wird NICHT wiederhergestellt.", candidate, profileId);
					return Optional.empty();
				}
				return Optional.of(candidate);
			} catch (IOException e) {
				LOGGER.warn("Backup-Datei '{}' liess sich nicht als ZIP-Archiv oeffnen: {}", candidate, e.toString());
			} catch (VpkgSecurityException e) {
				LOGGER.warn("Backup-Datei '{}' hat die Sicherheitspruefung nicht bestanden: {}", candidate,
						e.getMessage());
			} catch (ManifestParseException e) {
				LOGGER.warn("Backup-Datei '{}' hat ein ungueltiges Manifest: {}", candidate, e.getMessage());
			}
		}
		return Optional.empty();
	}

	private static boolean restore(Path packsDir, Path backupFile) {
		try {
			Files.createDirectories(packsDir);
		} catch (IOException e) {
			LOGGER.error("Paketordner '{}' liess sich nicht anlegen, Wiederherstellung abgebrochen: {}", packsDir,
					e.toString(), e);
			return false;
		}
		Path target = PackUpdater.uniqueTarget(packsDir, backupFile.getFileName().toString());
		Path backupSidecar = VpkgIntegritySidecar.sidecarPathFor(backupFile);
		Path targetSidecar = VpkgIntegritySidecar.sidecarPathFor(target);
		try {
			PackUpdater.moveInto(backupFile, target);
			if (Files.exists(backupSidecar)) {
				PackUpdater.moveInto(backupSidecar, targetSidecar);
			}
			return true;
		} catch (IOException e) {
			LOGGER.error("Backup-Datei '{}' liess sich nicht nach '{}' zurueckstellen: {}", backupFile, target,
					e.toString(), e);
			return false;
		}
	}
}
