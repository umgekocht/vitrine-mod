package de.spassglas.vitrine.vpkg;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wird von {@link VpkgIntegrityValidator#requireValid(java.nio.file.Path,
 * String, byte[], java.security.PublicKey)} geworfen, wenn ein {@code .vpkg}
 * mindestens einen Pruefsummen- oder Signaturverstoss aufweist (P4,
 * Charter-Regel 3: "Sicherheit vor Bequemlichkeit"). Geprueft (checked), aus
 * demselben Grund wie {@link VpkgSecurityException}: ein spaeterer Aufrufer
 * (Installer/Downloader, H1-H2/K3) soll ein einzelnes nicht vertrauenswuerdiges
 * Paket gezielt abweisen muessen, statt dass ein vergessener catch-Block das
 * Anwenden versehentlich zulaesst.
 */
public final class VpkgIntegrityException extends Exception {

	private final List<VpkgIntegrityViolation> violations;

	VpkgIntegrityException(List<VpkgIntegrityViolation> violations) {
		super(buildMessage(violations));
		this.violations = List.copyOf(violations);
	}

	/** Alle Verstoesse, die zur Ablehnung gefuehrt haben (nie leer). */
	public List<VpkgIntegrityViolation> violations() {
		return Collections.unmodifiableList(violations);
	}

	private static String buildMessage(List<VpkgIntegrityViolation> violations) {
		return ".vpkg-Archiv abgelehnt (" + violations.size()
				+ " Pruefsummen-/Signaturverstoss/-verstoesse): "
				+ violations.stream().map(VpkgIntegrityViolation::message).collect(Collectors.joining("; "));
	}
}
