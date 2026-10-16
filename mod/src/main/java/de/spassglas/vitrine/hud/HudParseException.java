package de.spassglas.vitrine.hud;

import java.util.Collections;
import java.util.List;

/**
 * Wird geworfen, wenn eine {@code hud/*.json} entweder kein gueltiges JSON
 * ist, nicht dem Schema {@code hud.schema.json} entspricht oder die
 * Laufzeit-Zusatzregel (eindeutige Widget-Ids) verletzt.
 *
 * <p>Geprueft (checked) -- analog zu
 * {@code de.spassglas.vitrine.manifest.ManifestParseException} (P1): ein
 * einzelnes kaputtes HUD-JSON in einem sonst gueltigen Paket soll abgewiesen
 * werden koennen, ohne den ganzen Ladevorgang abzubrechen.</p>
 *
 * <p>{@link #getMessage()} liefert eine konkrete, auf die jeweilige Datei
 * zugeschnittene Meldung; {@link #violations()} die einzelnen,
 * unterscheidbaren Verstoesse (leer, wenn die Datei nicht einmal gueltiges
 * JSON war).</p>
 */
public final class HudParseException extends Exception {

	private final List<String> violations;

	HudParseException(String message) {
		this(message, List.of());
	}

	HudParseException(String message, List<String> violations) {
		super(message);
		this.violations = List.copyOf(violations);
	}

	public List<String> violations() {
		return Collections.unmodifiableList(violations);
	}
}
