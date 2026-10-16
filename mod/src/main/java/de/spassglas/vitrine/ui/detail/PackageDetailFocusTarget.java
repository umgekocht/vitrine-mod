package de.spassglas.vitrine.ui.detail;

/**
 * Die Tab-stoppbaren Bereiche der Store-Detailseite, in Leserichtung
 * (docs/UI-SPEC.md "Barrierefreiheit": "Tab in Leserichtung", "ESC
 * schließt"). Aufbau analog zu {@code StoreFocusTarget}/{@code StoreFocusOrder}
 * fuer das Store-Geruest.
 *
 * <p>Reiner Text (Kopfbereich-Beschriftung, Faehigkeitszeilen, Details-Zeilen)
 * ist kein Fokusstopp -- nur bedienbare Elemente stehen hier.</p>
 */
public enum PackageDetailFocusTarget {

	/** Zurueck in die vorige Ansicht (ESC oder Zurueck-Pfeil) -- auf der Detailseite immer vorhanden. */
	BACK,

	/** Der eine gefuellte Aktionsknopf -- immer vorhanden. */
	ACTION_BUTTON,

	/** Die Screenshot-Galerie -- nur wenn es echte Screenshots gibt (der Platzhalter ist nicht bedienbar). */
	SCREENSHOT_GALLERY,

	/** "Mehr anzeigen" der Beschreibung -- nur wenn sie laenger als {@link PackageDetailModel#DESCRIPTION_COLLAPSE_LINES} Zeilen ist. */
	DESCRIPTION_TOGGLE,

	/** GATE69: Kopier-Knopf der Pruefsumme im "Details"-Abschnitt -- nur wenn die Pruefsumme bekannt ist. */
	COPY_CHECKSUM,

	/** Auf-/Zuklappen des "Aenderungen"-Abschnitts -- nur wenn ein Changelog vorliegt. */
	CHANGELOG_TOGGLE,

	/** Die Kachelreihe "Weitere Pakete dieses Autors" -- nur wenn es weitere gibt. */
	MORE_FROM_AUTHOR
}
