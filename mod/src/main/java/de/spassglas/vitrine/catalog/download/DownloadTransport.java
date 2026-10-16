package de.spassglas.vitrine.catalog.download;

import java.io.IOException;
import java.net.URI;

/**
 * Streaming-Seam fuer den Paket-Download (ROADMAP K3). Das Gegenstueck zu
 * {@code de.spassglas.vitrine.catalog.Transport}, aber ohne Vollpufferung: ein
 * {@code .vpkg} bis 512 MiB darf nie komplett in den Speicher.
 *
 * <p>Produktion: {@link JdkDownloadTransport} ({@code java.net.http},
 * {@code BodyHandlers.ofInputStream()}, nur {@code https},
 * {@code followRedirects(NEVER)}). Tests: {@link FileUriDownloadTransport} oder
 * ein Fake mit steuerbarem Abbruch.</p>
 *
 * <p>Wie {@code Transport} folgt eine Implementierung <strong>nie</strong> selbst
 * einer Weiterleitung: eine 30x-Antwort kommt roh mit ihrem {@code Location}-
 * Header zurueck, damit {@link PackageDownloader} das Ziel gegen
 * {@link DownloadHostWhitelist} pruefen kann.</p>
 */
public interface DownloadTransport {

	/**
	 * Oeffnet einen GET auf {@code uri} und gibt die Antwort mit noch offenem
	 * Body-Stream zurueck.
	 *
	 * @param uri              die Ziel-URI.
	 * @param resumeFromOffset {@code 0} fuer einen normalen Download; ein Wert
	 *                         {@code > 0} bittet den Server per
	 *                         {@code Range: bytes=<offset>-} um Fortsetzung ab
	 *                         diesem Byte. Der Server darf mit {@code 206}
	 *                         (Bereich geliefert) oder {@code 200} (Range
	 *                         ignoriert, ganze Datei) antworten -- beides muss
	 *                         der Aufrufer behandeln.
	 * @return die Antwort; der Aufrufer schliesst sie.
	 * @throws IOException bei Verbindungs-, Timeout- oder Lesefehlern beim
	 *                     Aufbau (nicht beim spaeteren Streamen des Bodys).
	 */
	DownloadResponse open(URI uri, long resumeFromOffset) throws IOException;
}
