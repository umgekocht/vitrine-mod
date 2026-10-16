package de.spassglas.vitrine.vpkg;

import java.util.List;

/**
 * Ergebnis von {@link VpkgSecurityValidator#validate(VpkgArchive)}: die
 * vollstaendige Liste aller gefundenen Verstoesse (leer, wenn das Archiv
 * sauber ist). Bewusst kein Abbruch bei der ersten Verletzung -- ein
 * Aufrufer, der einem Nutzer erklaeren will, WARUM ein Paket abgelehnt wurde,
 * soll alle Gruende auf einmal bekommen, nicht einen nach dem anderen per
 * Trial-and-Error.
 */
public record VpkgSecurityReport(List<VpkgSecurityViolation> violations) {

	public VpkgSecurityReport {
		violations = List.copyOf(violations);
	}

	/** Ob das Archiv keinen einzigen Verstoss aufweist. */
	public boolean isSafe() {
		return violations.isEmpty();
	}
}
