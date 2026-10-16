package de.spassglas.vitrine.pack;

/**
 * Ein Installationsschritt aus {@link PackInstaller} ist gescheitert (ROADMAP
 * S4). Die Meldung ist bewusst Klartext für die Oberfläche ("sauber abbrechen,
 * nichts Halbes hinterlassen, Klartextfehler") -- kein verschluckter Abbruch.
 *
 * <p>Nicht mehr {@code final} (ROADMAP G3): {@link CatalogConsentRequiredException}
 * ist eine spezialisierte Unterklasse fuer den Fall, dass ein
 * Katalog-Installationsversuch an einer fehlenden Faehigkeiten-Zustimmung
 * scheitert, die Aufrufer aber -- anders als bei jedem anderen Fehler --
 * gezielt behandeln muessen (Faehigkeiten-Dialog zeigen, dann erneut
 * versuchen), statt nur zu loggen.</p>
 *
 * <p>ROADMAP GATE48: {@link #getMessage()} ist weiterhin fuer Protokolle
 * gedacht (darf Pfade/rohe Ursachen enthalten, so wie bisher) -- fuer die
 * Oberflaeche gibt es jetzt getrennt {@link #displayKey()}/{@link
 * #displayArgs()}, einen Uebersetzungs-Schluessel samt sicheren Argumenten
 * (nie ein absoluter Pfad, nie ein rohes {@link Throwable#toString()}), den
 * {@code PackageDetailScreen#showInstallError} statt {@link #getMessage()}
 * anzeigt. Ohne {@link #displayKey()} (die beiden Alt-Konstruktoren, u. a.
 * von {@link BundleInstaller} weiterverwendet) faellt die Oberflaeche auf
 * einen generischen, sicheren Schluessel zurueck -- siehe dort.</p>
 */
public class PackInstallException extends Exception {

	private final String displayKey;
	private final Object[] displayArgs;

	public PackInstallException(String message) {
		super(message);
		this.displayKey = null;
		this.displayArgs = new Object[0];
	}

	public PackInstallException(String message, Throwable cause) {
		super(message, cause);
		this.displayKey = null;
		this.displayArgs = new Object[0];
	}

	/**
	 * ROADMAP GATE48: {@code message} bleibt wie gewohnt fuer Protokolle; {@code
	 * displayKey}/{@code displayArgs} sind die oberflaechen-sichere Fassung
	 * (Uebersetzungs-Schluessel + Argumente ohne Pfade/rohe Ursachen).
	 */
	public PackInstallException(String message, String displayKey, Object... displayArgs) {
		super(message);
		this.displayKey = displayKey;
		this.displayArgs = displayArgs == null ? new Object[0] : displayArgs;
	}

	/** Wie {@link #PackInstallException(String, String, Object...)}, zusaetzlich mit Ursache fuer das Protokoll. */
	public PackInstallException(String message, Throwable cause, String displayKey, Object... displayArgs) {
		super(message, cause);
		this.displayKey = displayKey;
		this.displayArgs = displayArgs == null ? new Object[0] : displayArgs;
	}

	/** Der Uebersetzungs-Schluessel fuer die Oberflaeche, oder {@code null} ohne oberflaechen-sichere Fassung. */
	public String displayKey() {
		return displayKey;
	}

	/** Die Argumente zu {@link #displayKey()} (nie ein Pfad, nie ein rohes {@link Throwable}). */
	public Object[] displayArgs() {
		return displayArgs;
	}
}
