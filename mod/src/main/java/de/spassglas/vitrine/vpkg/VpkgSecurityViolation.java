package de.spassglas.vitrine.vpkg;

/**
 * Ein einzelner, konkreter Sicherheitsverstoss, den {@link VpkgSecurityValidator}
 * in einem {@code .vpkg}-Archiv gefunden hat.
 *
 * @param type      welche Angriffsart
 * @param entryName der betroffene Eintragsname, oder {@code null} bei einem
 *                    Verstoss, der das ganze Archiv betrifft (Gesamtgroesse,
 *                    Eintragsanzahl)
 * @param message   fertig formulierte, auf diesen Fall zugeschnittene Meldung
 */
public record VpkgSecurityViolation(VpkgSecurityViolationType type, String entryName, String message) {

	@Override
	public String toString() {
		return message;
	}
}
