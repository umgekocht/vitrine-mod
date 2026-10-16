package de.spassglas.vitrine.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Der Update-Status eines einzelnen installierten Pakets (ROADMAP K5,
 * "Versionsvergleich (SemVer), Marker"): stellt die installierte Version (aus
 * dem {@code .vpkg}-Manifest, siehe {@code LibraryModel}) der neuesten im
 * Katalog gefuehrten Version ({@code IndexPackage.latestVersion()}) gegenueber.
 *
 * <p>{@link #updateAvailable()} ist genau dann {@code true}, wenn <em>beide</em>
 * Versionen als {@link SemanticVersion} parsebar sind und die Katalog-Version
 * echt neuer ist. Eine nicht parsebare Version (auf einer der beiden Seiten)
 * oder ein fehlender Katalog-Eintrag bedeutet "kein Update" -- geloggt, nie ein
 * Absturz (Charter-Regel 2: nichts vorgaukeln).</p>
 */
public record UpdateStatus(String packId, String installedVersion, String latestVersion, boolean updateAvailable) {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Updates");

	public UpdateStatus {
		Objects.requireNonNull(packId, "packId");
	}

	/**
	 * Bildet den Status aus roher installierter Version und roher
	 * Katalog-Version. {@code latestVersion} darf {@code null} sein (Paket ist
	 * nicht im Katalog) -- dann ist kein Update verfuegbar.
	 */
	public static UpdateStatus evaluate(String packId, String installedVersion, String latestVersion) {
		if (latestVersion == null || latestVersion.isBlank()) {
			return new UpdateStatus(packId, installedVersion, null, false);
		}
		Optional<SemanticVersion> installed = SemanticVersion.tryParse(installedVersion);
		Optional<SemanticVersion> latest = SemanticVersion.tryParse(latestVersion);
		if (installed.isEmpty() || latest.isEmpty()) {
			LOGGER.info("Update-Abgleich fuer '{}' uebersprungen: Version nicht als SemVer parsebar "
					+ "(installiert='{}', Katalog='{}') -- gilt als 'kein Update'.",
					packId, installedVersion, latestVersion);
			return new UpdateStatus(packId, installedVersion, latestVersion, false);
		}
		boolean newer = latest.get().isNewerThan(installed.get());
		return new UpdateStatus(packId, installedVersion, latestVersion, newer);
	}
}
