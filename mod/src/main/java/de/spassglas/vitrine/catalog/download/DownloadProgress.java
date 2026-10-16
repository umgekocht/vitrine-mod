package de.spassglas.vitrine.catalog.download;

/**
 * Fortschritts-Seam fuer den Paket-Download (ROADMAP K3). Meldet
 * <strong>echte gezaehlte Bytes</strong> -- nie einen erfundenen Balken
 * (Charter-Regel 2 "Nichts vorgaukeln"). S8s {@code DownloadEntry} kann daraus
 * spaeter (K4) einen bestimmten Fortschritt zeichnen; solange {@code totalBytes}
 * unbekannt ist, bleibt es der unbestimmte Zustand.
 */
@FunctionalInterface
public interface DownloadProgress {

	/** Tut nichts -- fuer Aufrufer ohne Fortschrittsanzeige. */
	DownloadProgress NOOP = (bytesRead, totalBytes) -> {
	};

	/**
	 * @param bytesRead  bislang tatsaechlich in die {@code .part}-Datei
	 *                   geschriebene Bytes (inklusive eines wiederaufgenommenen
	 *                   Anfangsstuecks).
	 * @param totalBytes erwartete Gesamtgroesse, oder {@code -1}, wenn weder die
	 *                   Anfrage noch der {@code Content-Length}-Header sie
	 *                   nennt.
	 */
	void onProgress(long bytesRead, long totalBytes);
}
