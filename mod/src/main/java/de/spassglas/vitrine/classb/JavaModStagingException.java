package de.spassglas.vitrine.classb;

/**
 * ROADMAP J2: Fehler beim Stagen bzw. Zuruecknehmen eines Klasse-B-Jars. Wird
 * geworfen, sobald ein Schritt scheitert -- insbesondere bei einer
 * <b>Pruefsummen-Abweichung</b> (dann liegt nichts in {@code mods/}, die
 * temporaere Datei ist weg), bei einer bereits vorhandenen gleichnamigen Jar
 * (nie ueberschreiben) oder bei einer Ruecknahme nach dem Neustart.
 *
 * <p>ROADMAP GATE71: {@link #getMessage()} bleibt deutscher Klartext fuer die
 * Protokolle (darf Dateipfade und rohe {@link java.io.IOException#getMessage()}
 * enthalten, so wie bisher) -- fuer die Oberflaeche gibt es getrennt {@link
 * #displayKey()}/{@link #displayArgs()}, einen Uebersetzungs-Schluessel samt
 * sicheren Argumenten (nie ein absoluter Pfad, nie eine rohe
 * Ausnahme-Meldung), den {@link
 * de.spassglas.vitrine.ui.classb.RestartRequiredBannerWidget#takeBack} statt
 * {@link #getMessage()} anzeigt. Gleiches Muster wie {@code
 * PackInstallException} (GATE48/66). Ohne {@link #displayKey()} (die beiden
 * Alt-Konstruktoren) faellt die Oberflaeche auf einen generischen, sicheren
 * Schluessel zurueck.</p>
 */
public class JavaModStagingException extends Exception {

	private final String displayKey;
	private final Object[] displayArgs;

	public JavaModStagingException(String message) {
		super(message);
		this.displayKey = null;
		this.displayArgs = new Object[0];
	}

	public JavaModStagingException(String message, Throwable cause) {
		super(message, cause);
		this.displayKey = null;
		this.displayArgs = new Object[0];
	}

	/**
	 * ROADMAP GATE71: {@code message} bleibt wie gewohnt fuer Protokolle; {@code
	 * displayKey}/{@code displayArgs} sind die oberflaechen-sichere Fassung
	 * (Uebersetzungs-Schluessel + Argumente ohne Pfade/rohe Ursachen).
	 */
	public JavaModStagingException(String message, String displayKey, Object... displayArgs) {
		super(message);
		this.displayKey = displayKey;
		this.displayArgs = displayArgs == null ? new Object[0] : displayArgs;
	}

	/** Wie {@link #JavaModStagingException(String, String, Object...)}, zusaetzlich mit Ursache fuer das Protokoll. */
	public JavaModStagingException(String message, Throwable cause, String displayKey, Object... displayArgs) {
		super(message, cause);
		this.displayKey = displayKey;
		this.displayArgs = displayArgs == null ? new Object[0] : displayArgs;
	}

	/** Der Uebersetzungs-Schluessel fuer die Oberflaeche, oder {@code null} ohne oberflaechen-sichere Fassung. */
	public String displayKey() {
		return displayKey;
	}

	/** Die Argumente zu {@link #displayKey()} (nie ein Pfad, nie eine rohe Ausnahme-Meldung). */
	public Object[] displayArgs() {
		return displayArgs;
	}
}
