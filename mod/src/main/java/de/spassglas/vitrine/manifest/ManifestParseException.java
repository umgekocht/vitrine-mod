package de.spassglas.vitrine.manifest;

import java.util.Collections;
import java.util.List;

/**
 * Wird geworfen, wenn eine {@code vitrine.json} entweder kein gueltiges JSON
 * ist oder nicht dem Schema {@code vitrine.schema.json} entspricht.
 *
 * <p>Geprueft (checked), weil ein spaeterer Aufrufer (P2/P3, beim Einlesen
 * eines {@code .vpkg}-Archivs) ein einzelnes kaputtes Paket abweisen und mit
 * anderen weitermachen soll, statt dass ein vergessener catch-Block den
 * ganzen Vorgang abbrechen laesst.</p>
 *
 * <p>{@link #getMessage()} liefert eine konkrete, auf das jeweilige Manifest
 * zugeschnittene Meldung (welches Feld fehlt/hat den falschen Typ/verletzt
 * welches Muster) statt eines pauschalen "ungueltig" -- siehe
 * {@link #violations()} fuer die einzelnen, unterscheidbaren Verstoesse.</p>
 */
public final class ManifestParseException extends Exception {

	private final List<String> violations;

	ManifestParseException(String message) {
		this(message, List.of());
	}

	ManifestParseException(String message, List<String> violations) {
		super(message);
		this.violations = List.copyOf(violations);
	}

	/**
	 * Die einzelnen Schema-Verstoesse, die zu dieser Ablehnung gefuehrt
	 * haben (leer, wenn das Dokument nicht einmal gueltiges JSON war).
	 */
	public List<String> violations() {
		return Collections.unmodifiableList(violations);
	}
}
