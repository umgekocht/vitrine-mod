package de.spassglas.vitrine.ui.detail;

import java.util.Locale;
import java.util.Optional;

/**
 * Formatiert eine Dateigroesse in Bytes fuer die "Details"-Zeile der
 * Store-Detailseite (docs/UI-SPEC.md "5. Detailseite": "Details: Version,
 * Größe, …").
 *
 * <p>Reine Funktion, kein Minecraft, kein Theme -- die Masszahl "Groesse"
 * ist ein Fakt ueber die {@code .vpkg}-Datei, kein Layoutwert. Einheiten
 * ({@code B}/{@code KB}/{@code MB}) sind dezimal (1000er-Schritte, wie im
 * Datei-Explorer ueblich) und werden bewusst nicht uebersetzt -- es sind
 * Einheitenzeichen, kein Fliesstext.</p>
 */
public final class HumanReadableSize {

	private static final long KILO = 1000L;
	private static final long MEGA = KILO * 1000L;

	private HumanReadableSize() {
	}

	/**
	 * @param bytes Dateigroesse; ein negativer Wert bedeutet "unbekannt".
	 * @return z. B. {@code "742 B"}, {@code "12.3 KB"}, {@code "4.0 MB"}, oder
	 *         {@link Optional#empty()}, wenn {@code bytes < 0}.
	 */
	public static Optional<String> format(long bytes) {
		if (bytes < 0) {
			return Optional.empty();
		}
		if (bytes < KILO) {
			return Optional.of(bytes + " B");
		}
		if (bytes < MEGA) {
			return Optional.of(String.format(Locale.ROOT, "%.1f KB", bytes / (double) KILO));
		}
		return Optional.of(String.format(Locale.ROOT, "%.1f MB", bytes / (double) MEGA));
	}
}
