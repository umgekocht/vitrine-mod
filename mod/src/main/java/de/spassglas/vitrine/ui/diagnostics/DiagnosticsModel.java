package de.spassglas.vitrine.ui.diagnostics;

import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyService;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freies Modell des Diagnose-Bildschirms (ROADMAP X3: "was ist
 * installiert, was hat den Reload gekostet, welche Datei kam von wo") --
 * analog zu {@code ui.library.LibraryModel}: baut aus {@code state.json} (H4)
 * plus einem rein lesenden {@code .vpkg}-Scan eine Zeile je <em>aktivem</em>
 * Paket, ohne eine einzige Minecraft-Klasse.
 *
 * <p><b>Woertliches Fertig-Kriterium:</b> "Zeigt fuer jedes aktive Paket
 * Quelle, Version und Pruefsumme." {@link #activePackages()} liefert genau
 * das je {@link DiagnosticsEntry}:</p>
 * <ul>
 *   <li>{@link DiagnosticsEntry#version()} -- aus dem bereits sicherheits-
 *       gepruepften Manifest ({@code vitrine.json}), wie {@code LibraryModel}
 *       es liest.</li>
 *   <li>{@link DiagnosticsEntry#sha256()} -- die beim Installieren bereits
 *       gegen {@link VpkgIntegrityValidator} gepruepfte Pruefsumme aus dem
 *       Integritaets-Beleg ({@link VpkgIntegritySidecar}); fehlt der Beleg
 *       (z. B. ein aeltestes, vor P4 installiertes Testpaket), wird die
 *       tatsaechliche Pruefsumme der Datei auf der Platte neu berechnet --
 *       ein echter, nie ein erfundener Wert.</li>
 *   <li>{@link DiagnosticsEntry#source()} -- {@link PackState.PackEntry#source}
 *       (ROADMAP X3-Feld, von {@code PackInstaller}/{@code BundleInstaller}/
 *       {@code PackUpdater} beim tatsaechlichen Installations-/Update-Vorgang
 *       gesetzt): lokaler Bibliotheksordner, Katalog-Host oder -- fuer einen
 *       Eintrag aus einem vor X3 geschriebenen {@code state.json} -- unbekannt.
 *       Kein Rateschritt, kein erfundener "Katalog"-Eintrag ohne echten
 *       Beleg.</li>
 * </ul>
 *
 * <p>{@link #lastReload()} liefert die echt gemessene Dauer und
 * Paketanzahl des zuletzt abgeschlossenen gesammelten Reloads aus {@link
 * HotApplyCoordinator#lastReloadStats()} -- leer, wenn seit dem Start des
 * Clients noch keiner abgeschlossen wurde.</p>
 */
public final class DiagnosticsModel {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Diagnostics");
	private static final String PACK_ID_PREFIX = "vitrine/";

	/** Liefert die Kennzahlen des zuletzt abgeschlossenen Reloads (Seam fuer Tests ohne echten Client). */
	@FunctionalInterface
	public interface ReloadStatsSource {
		Optional<HotApplyCoordinator.ReloadStats> lastReloadStats();
	}

	private final Path vitrineConfigDir;
	private final VitrinePackProvider packProvider;
	private final ReloadStatsSource reloadStatsSource;

	public DiagnosticsModel(Path vitrineConfigDir, VitrinePackProvider packProvider, ReloadStatsSource reloadStatsSource) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.packProvider = Objects.requireNonNull(packProvider, "packProvider");
		this.reloadStatsSource = Objects.requireNonNull(reloadStatsSource, "reloadStatsSource");
	}

	/** Verdrahtet das Modell mit den echten Client-Diensten. */
	public static DiagnosticsModel forConfigDir(Path vitrineConfigDir) {
		return new DiagnosticsModel(vitrineConfigDir, VitrinePackProvider.forConfigDir(vitrineConfigDir),
				HotApplyService::lastReloadStats);
	}

	/**
	 * Eine Zeile je <em>aktivem</em> installierten Paket, in der Reihenfolge aus
	 * {@code state.json}. Ein aktiv gefuehrtes Paket, dessen Manifest sich
	 * nicht lesen laesst (z. B. Sicherheitspruefung scheitert), wird
	 * uebersprungen (mit Warnung) -- der Rest der Liste erscheint trotzdem.
	 *
	 * <p>ROADMAP GATE115: fehlt die {@code .vpkg}-Datei komplett (z. B. ein
	 * zwischen den {@code PackUpdater}-Schritten abgebrochener Update-Lauf,
	 * dessen Backup beim Start nicht wiederherstellbar war, siehe {@code
	 * OrphanedUpdateBackupRecovery}), wird der Eintrag -- anders als bis
	 * GATE115 -- NICHT mehr still uebersprungen, sondern als {@link
	 * DiagnosticsEntry#missing(String, String)} sichtbar aufgenommen.</p>
	 */
	public List<DiagnosticsEntry> activePackages() {
		PackState state = PackStateStore.load(vitrineConfigDir);
		List<DiagnosticsEntry> result = new ArrayList<>();
		for (PackState.PackEntry pack : state.packs) {
			if (!pack.active) {
				continue;
			}
			Optional<Path> file = packProvider.findPackFile(pack.packId);
			if (file.isEmpty()) {
				LOGGER.warn("Diagnose: fuer aktives Paket '{}' wurde keine .vpkg-Datei in packs/ gefunden -- wird "
						+ "als kaputt/fehlend angezeigt (ROADMAP GATE115), nicht mehr stillschweigend uebersprungen.",
						pack.packId);
				result.add(DiagnosticsEntry.missing(pack.packId, stripPrefix(pack.packId)));
				continue;
			}
			DiagnosticsEntry entry = readEntry(file.get(), pack);
			if (entry != null) {
				result.add(entry);
			}
		}
		return List.copyOf(result);
	}

	/** Kennzahlen des zuletzt abgeschlossenen gesammelten Reloads (ROADMAP X3, "was hat den Reload gekostet"). */
	public Optional<HotApplyCoordinator.ReloadStats> lastReload() {
		return reloadStatsSource.lastReloadStats();
	}

	// --- intern ------------------------------------------------------------

	private DiagnosticsEntry readEntry(Path vpkgFile, PackState.PackEntry pack) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			String sha256 = resolveChecksum(vpkgFile);
			DiagnosticsEntry.Source source = resolveSource(pack.source);
			return new DiagnosticsEntry(pack.packId, manifest.id(), manifest.name(), manifest.author(),
					manifest.version(), sha256, source, false);
		} catch (IOException e) {
			LOGGER.warn("Diagnose: '{}' liess sich nicht als .vpkg oeffnen: {}", vpkgFile, e.toString());
			return null;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Diagnose: '{}' hat die Sicherheitspruefung nicht bestanden: {}", vpkgFile, e.getMessage());
			return null;
		} catch (ManifestParseException e) {
			LOGGER.warn("Diagnose: '{}' hat ein ungueltiges Manifest: {}", vpkgFile, e.getMessage());
			return null;
		} catch (RuntimeException e) {
			LOGGER.warn("Diagnose: '{}' liess sich unerwartet nicht auswerten: {}", vpkgFile, e.toString(), e);
			return null;
		}
	}

	/**
	 * Die Pruefsumme, die beim Installieren bereits gegen {@link
	 * VpkgIntegrityValidator} geprueft wurde -- einfach angezeigt, nicht neu
	 * erfunden. Fehlt der Integritaets-Beleg (z. B. ein manuell ohne Beleg in
	 * {@code packs/} abgelegtes Testpaket), wird die tatsaechliche Pruefsumme
	 * der Datei auf der Platte berechnet: immer noch ein echter Wert, nur ohne
	 * die Garantie, dass er beim Anwenden bereits gegengeprueft wurde.
	 */
	private static String resolveChecksum(Path vpkgFile) {
		VpkgIntegritySidecar.Content integrity = VpkgIntegritySidecar.read(vpkgFile);
		if (integrity.sha256() != null && !integrity.sha256().isBlank()) {
			return integrity.sha256();
		}
		try {
			return VpkgIntegrityValidator.sha256Hex(vpkgFile);
		} catch (IOException e) {
			LOGGER.warn("Diagnose: Pruefsumme von '{}' liess sich nicht berechnen: {}", vpkgFile, e.toString());
			return null;
		}
	}

	/** ROADMAP GATE115: Roh-Manifest-ID fuer {@link DiagnosticsEntry#missing(String, String)}, wie {@code LibraryModel#stripPrefix}. */
	private static String stripPrefix(String packId) {
		return packId != null && packId.startsWith(PACK_ID_PREFIX) ? packId.substring(PACK_ID_PREFIX.length()) : packId;
	}

	private static DiagnosticsEntry.Source resolveSource(String rawSource) {
		if (rawSource == null) {
			return DiagnosticsEntry.Source.unknown();
		}
		if (PackState.SOURCE_LOCAL_LIBRARY.equals(rawSource)) {
			return DiagnosticsEntry.Source.localLibrary();
		}
		if (rawSource.startsWith(PackState.SOURCE_CATALOG_PREFIX)) {
			String host = rawSource.substring(PackState.SOURCE_CATALOG_PREFIX.length());
			return host.isBlank() ? DiagnosticsEntry.Source.unknown() : DiagnosticsEntry.Source.catalog(host);
		}
		return DiagnosticsEntry.Source.unknown();
	}
}
