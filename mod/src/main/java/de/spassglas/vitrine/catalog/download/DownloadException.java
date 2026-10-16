package de.spassglas.vitrine.catalog.download;

/**
 * Fehlzustand eines Paket-Downloads (ROADMAP K3). Geprueft (checked), aus
 * demselben Grund wie {@code CatalogException} / {@code VpkgIntegrityException}:
 * ein vergessener catch-Block darf nicht dazu fuehren, dass ein nicht
 * verifiziertes oder unvollstaendiges Paket weiterverarbeitet wird.
 *
 * <p>Die {@link Kind} entscheidet, was auf der Platte zurueckbleibt:</p>
 * <ul>
 *   <li>{@link Kind#NETWORK} -- die Uebertragung riss ab. Die {@code .part}
 *       <strong>bleibt liegen</strong>, ein erneuter Aufruf setzt per
 *       {@code Range} fort.</li>
 *   <li>{@link Kind#BROKEN} -- Protokoll-/Groessen-/Whitelist-Verstoss. Die
 *       {@code .part} wird geloescht (kein halbes, moeglicherweise vergiftetes
 *       Fragment bleibt liegen).</li>
 *   <li>{@link Kind#VERIFICATION} -- Pruefsumme oder Signatur der fertigen Datei
 *       ungueltig. Die bereits umbenannte Datei wird wieder geloescht.</li>
 * </ul>
 */
public final class DownloadException extends Exception {

	/** Grobklasse des Fehlers -- steuert das Aufraeumen. */
	public enum Kind {
		NETWORK,
		BROKEN,
		VERIFICATION
	}

	private final Kind kind;

	public DownloadException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public DownloadException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}
}
