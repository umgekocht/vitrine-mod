package de.spassglas.vitrine.vpkg;

import java.util.List;

/**
 * Ergebnis von {@link VpkgIntegrityValidator#validate(java.nio.file.Path,
 * String, byte[], java.security.PublicKey)}: die vollstaendige Liste aller
 * gefundenen Verstoesse (leer, wenn Pruefsumme und Signatur beide gueltig
 * sind). Bewusst kein Abbruch bei der ersten Verletzung -- ein Aufrufer, der
 * einem Nutzer erklaeren will, WARUM ein Paket abgelehnt wurde, soll alle
 * Gruende auf einmal bekommen.
 */
public record VpkgIntegrityReport(List<VpkgIntegrityViolation> violations) {

	public VpkgIntegrityReport {
		violations = List.copyOf(violations);
	}

	/** Ob Pruefsumme und Signatur beide gueltig sind -- kein einziger Verstoss. */
	public boolean isValid() {
		return violations.isEmpty();
	}
}
