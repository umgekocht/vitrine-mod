package de.spassglas.vitrine.ui.downloads;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Eine Zeile der Download-Anzeige (ROADMAP S8): eine laufende oder zuletzt
 * abgeschlossene Installation mit Anzeigename, Groesse, Phase und -- sofern
 * bekannt -- einem Fortschrittsanteil.
 *
 * <p><b>Ehrlich unbestimmt</b> (Charter-Regel 2 "Nichts vorgaukeln"): ein
 * echter, tickender Fortschritt entsteht nur, wenn der Downloader Bytes
 * meldet. Eine lokale Installation tut das nicht; bis zur ersten Meldung
 * liefert ein laufender Eintrag {@link #progress()} ==
 * {@link OptionalDouble#empty()} -- die Oberflaeche zeichnet dann den
 * unbestimmten Zustand ({@code vitrine.downloads.progress.indeterminate}) und
 * den Hinweis {@code vitrine.downloads.progress.unknown_note}, keinen
 * gefuellten Balken, der nichts bedeutet.</p>
 *
 * @param packId      Paket-ID (Profil-Schreibweise, wie in {@code state.json})
 * @param displayName Anzeigename aus dem Manifest -- Paket-Freitext, kein Lang-Key
 * @param sizeBytes   Gesamtgroesse in Byte, oder {@code -1} wenn unbekannt
 * @param phase       aktuelle {@link DownloadPhase}
 * @param progress    Anteil in {@code [0.0, 1.0]}, oder leer = unbestimmt
 */
public record DownloadEntry(String packId, String displayName, long sizeBytes, DownloadPhase phase,
		OptionalDouble progress) {

	public DownloadEntry {
		Objects.requireNonNull(packId, "packId");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(progress, "progress");
		if (progress.isPresent() && (progress.getAsDouble() < 0.0 || progress.getAsDouble() > 1.0)) {
			throw new IllegalArgumentException("progress muss in [0,1] liegen: " + progress.getAsDouble());
		}
	}

	/** Ein neuer, laufender Eintrag ohne bekannten Fortschritt. */
	public static DownloadEntry starting(String packId, String displayName, long sizeBytes) {
		return new DownloadEntry(packId, displayName, sizeBytes, DownloadPhase.RUNNING, OptionalDouble.empty());
	}

	/** {@code true}, wenn kein Fortschrittsanteil bekannt ist -- die Oberflaeche zeigt den unbestimmten Zustand. */
	public boolean indeterminate() {
		return progress.isEmpty();
	}

	public boolean hasKnownSize() {
		return sizeBytes >= 0L;
	}

	/** Kopie mit anderer Phase (Groesse/Name/Fortschritt bleiben). */
	public DownloadEntry withPhase(DownloadPhase newPhase) {
		return new DownloadEntry(packId, displayName, sizeBytes, newPhase, progress);
	}
}
