package de.spassglas.vitrine.catalog.download;

import java.time.Duration;

/**
 * Einkompilierte Sicherheits-Obergrenzen und Timeouts fuer den Paket-Download
 * (ROADMAP K3). Analog {@code CatalogEndpoints} fuer den Katalog-Client: kein
 * Secret, kein Token, kein absoluter Dateipfad -- nur Zahlen. Charter-Regel 12:
 * ohne jede Konfiguration nutzbar.
 */
public final class DownloadEndpoints {

	/**
	 * Verbindungsaufbau-Timeout. Kurz gehalten -- ein nicht erreichbarer Host
	 * soll den Store nicht minutenlang blockieren. Gleicher Wert wie
	 * {@code CatalogEndpoints.CONNECT_TIMEOUT}.
	 */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);

	/**
	 * Zeitfenster, bis Statuszeile + Antwort-Header da sein muessen. Deckt
	 * <em>nicht</em> das Streamen des Bodys ab -- ein 512-MiB-Paket ueber eine
	 * langsame Leitung darf lange dauern, solange ueberhaupt Daten fliessen.
	 */
	public static final Duration RESPONSE_HEADER_TIMEOUT = Duration.ofSeconds(20);

	/**
	 * Leerlauf-Grenze <em>zwischen</em> zwei empfangenen Datenbloecken beim
	 * Streamen des Bodys (kein hartes Gesamt-Timeout). Reisst die Leitung
	 * mitten im Download ab, ohne dass die TCP-Verbindung sauber schliesst,
	 * greift diese Grenze und bricht den Lesevorgang ab -- die {@code .part}
	 * bleibt fuer eine spaetere Wiederaufnahme liegen.
	 */
	public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * Harte Obergrenze fuer ein einzelnes {@code .vpkg}. Deckungsgleich mit
	 * {@code registry/schema/registry-package.schema.json} ({@code versions[].size}
	 * &le; 536870912) -- 512 MiB. Wird beim Streamen erzwungen, auch wenn die
	 * Anfrage keine erwartete Groesse mitbringt und der Server kein
	 * {@code Content-Length} sendet: sobald mehr Bytes ankommen, bricht der
	 * Download ab und die {@code .part} wird geloescht.
	 */
	public static final long MAX_PACKAGE_BYTES = 536_870_912L;

	/**
	 * Hoechstzahl an Weiterleitungen, denen der Downloader folgt -- und nur,
	 * solange jedes Ziel wieder auf {@link DownloadHostWhitelist} steht. Eine
	 * Weiterleitung auf einen fremden Host wird hart abgelehnt, nie verfolgt.
	 * GitHub loest den kanonischen Release-Asset-Link in aller Regel mit genau
	 * einer Weiterleitung auf; fuenf ist grosszuegig.
	 */
	public static final int MAX_REDIRECTS = 5;

	/** Lesepuffer beim Streamen des Bodys in die {@code .part}-Datei. */
	public static final int TRANSFER_BUFFER_BYTES = 64 * 1024;

	/** Endung der Teil-Datei waehrend des Downloads: {@code <ziel>.vpkg.part}. */
	public static final String PART_SUFFIX = ".part";

	private DownloadEndpoints() {
	}
}
