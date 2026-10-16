package de.spassglas.vitrine.vpkg;

/**
 * Ein einzelner, konkreter Pruefsummen-/Signaturverstoss, den
 * {@link VpkgIntegrityValidator} (P4) gefunden hat.
 *
 * @param type    welche Art von Verstoss
 * @param message fertig formulierte, auf diesen Fall zugeschnittene Meldung
 */
public record VpkgIntegrityViolation(VpkgIntegrityViolationType type, String message) {

	@Override
	public String toString() {
		return message;
	}
}
