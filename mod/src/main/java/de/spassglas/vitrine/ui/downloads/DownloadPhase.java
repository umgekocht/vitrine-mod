package de.spassglas.vitrine.ui.downloads;

/**
 * Der Zustand einer Installation in der Download-Anzeige (ROADMAP S8,
 * docs/UI-SPEC.md "1. Kopfleiste": Flyout "mit laufenden und zuletzt
 * abgeschlossenen Installationen"). Die vier Werte haben je einen eigenen
 * Lang-Key ({@code vitrine.downloads.flyout.running} bzw.
 * {@code vitrine.downloads.phase.done/cancelled/failed}).
 */
public enum DownloadPhase {

	/** Laeuft gerade -- zaehlt in den Knopf-Zaehler, Fortschritt bis K3 unbestimmt. */
	RUNNING("vitrine.downloads.flyout.running"),

	/** Erfolgreich abgeschlossen. */
	DONE("vitrine.downloads.phase.done"),

	/** Vom Nutzer abgebrochen. */
	CANCELLED("vitrine.downloads.phase.cancelled"),

	/** Fehlgeschlagen. */
	FAILED("vitrine.downloads.phase.failed");

	private final String translationKey;

	DownloadPhase(String translationKey) {
		this.translationKey = translationKey;
	}

	public String translationKey() {
		return translationKey;
	}

	/** {@code true} fuer {@link #DONE}/{@link #CANCELLED}/{@link #FAILED} -- Eintrag steht im Abschnitt "Zuletzt abgeschlossen". */
	public boolean isTerminal() {
		return this != RUNNING;
	}
}
