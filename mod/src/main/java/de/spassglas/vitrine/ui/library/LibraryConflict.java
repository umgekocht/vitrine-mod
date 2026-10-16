package de.spassglas.vitrine.ui.library;

import java.util.List;
import java.util.Objects;

/**
 * Ein erkannter Ressourcen-Konflikt in der Bibliothek (ROADMAP S5,
 * "Konflikthinweis"): mindestens zwei <em>aktive</em> Pakete ueberschreiben
 * denselben {@code assets/...}-Pfad. {@link #contenders()} steht in
 * Prioritaetsreihenfolge (weiter oben in der Bibliothek zuerst), daher ist
 * {@link #winner()} das Paket, dessen Datei bei der aktuellen Reihenfolge
 * tatsaechlich im Spiel erscheint. Aendert der Nutzer die Reihenfolge,
 * wechselt der Gewinner.
 */
public final class LibraryConflict {

	private final String resourcePath;
	private final List<LibraryEntry> contenders;

	public LibraryConflict(String resourcePath, List<LibraryEntry> contenders) {
		this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
		this.contenders = List.copyOf(contenders);
		if (this.contenders.size() < 2) {
			throw new IllegalArgumentException("Ein Konflikt braucht mindestens zwei beteiligte Pakete");
		}
	}

	/** Der gemeinsam ueberschriebene Ressourcenpfad, z. B. {@code assets/minecraft/textures/block/stone.png}. */
	public String resourcePath() {
		return resourcePath;
	}

	/** Alle am Konflikt beteiligten aktiven Pakete, in Prioritaetsreihenfolge (Gewinner zuerst). */
	public List<LibraryEntry> contenders() {
		return contenders;
	}

	/** Das Paket, das bei der aktuellen Reihenfolge gewinnt (weiter oben in der Bibliothek). */
	public LibraryEntry winner() {
		return contenders.get(0);
	}
}
