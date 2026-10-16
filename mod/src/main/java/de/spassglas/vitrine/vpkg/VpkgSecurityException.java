package de.spassglas.vitrine.vpkg;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wird von {@link VpkgSecurityValidator#requireSafe(VpkgArchive)} geworfen,
 * wenn ein Archiv mindestens einen Sicherheitsverstoss aufweist (P3, Charter-
 * Regel 3: "Sicherheit vor Bequemlichkeit"). Geprueft (checked), aus demselben
 * Grund wie {@link de.spassglas.vitrine.manifest.ManifestParseException}: ein
 * spaeterer Aufrufer (Installer/H1-H2) soll ein einzelnes boesartiges Paket
 * gezielt abweisen muessen, statt dass ein vergessener catch-Block das
 * Anwenden versehentlich zulaesst.
 */
public final class VpkgSecurityException extends Exception {

	private final List<VpkgSecurityViolation> violations;

	VpkgSecurityException(List<VpkgSecurityViolation> violations) {
		super(buildMessage(violations));
		this.violations = List.copyOf(violations);
	}

	/** Alle Verstoesse, die zur Ablehnung gefuehrt haben (nie leer). */
	public List<VpkgSecurityViolation> violations() {
		return Collections.unmodifiableList(violations);
	}

	private static String buildMessage(List<VpkgSecurityViolation> violations) {
		return ".vpkg-Archiv abgelehnt (" + violations.size() + " Sicherheitsverstoss/-verstoesse): "
				+ violations.stream().map(VpkgSecurityViolation::message).collect(Collectors.joining("; "));
	}
}
