package de.spassglas.vitrine.ui.detail;

/**
 * Die Abschnitte der Store-Detailseite in genau der Reihenfolge, die
 * {@code docs/UI-SPEC.md} ("5. Detailseite") und die ROADMAP-Zeile S3
 * vorgeben:
 *
 * <pre>
 * Kopfbereich -> Aktionsknopf -> Screenshot-Galerie -> Beschreibung
 * -> "Das darf dieses Paket" -> Details -> Aenderungen
 * -> weitere Pakete des Autors
 * </pre>
 *
 * <p>{@link PackageDetailModel#sections()} liefert diese Werte immer
 * vollstaendig und in dieser Aufzaehlungsreihenfolge -- ein Abschnitt
 * verschwindet nicht, wenn er leer ist, sondern zeigt einen ruhigen
 * Leerzustand ({@code docs/UI-SPEC.md} "7. Zustaende": "einen Satz
 * Erklaerung", kein Blindtext). Die Reihenfolge ist damit testbar, ohne
 * einen laufenden Client.</p>
 */
public enum PackageDetailSection {

	/** Grosses Bild links, Name, Autor, Typ-Badge, Icon (docs/UI-SPEC.md "5. Detailseite", Kopfbereich). */
	HEADER,

	/** Der einzige gefuellte Knopf der Seite, Zustand aus {@link PackageActionState}. */
	ACTION_BUTTON,

	/** Horizontale Screenshot-Galerie aus {@code manifest.screenshots()} (Platzhalter, wenn keine da sind). */
	SCREENSHOTS,

	/** Freitext aus {@code manifest.description()}, aufklappbar ab {@link PackageDetailModel#DESCRIPTION_COLLAPSE_LINES} Zeilen. */
	DESCRIPTION,

	/** "Das darf dieses Paket" -- die angemeldeten Faehigkeiten im Klartext (docs/UI-SPEC.md). */
	CAPABILITIES,

	/** Version, Groesse, Lizenz, Homepage (docs/UI-SPEC.md "5. Detailseite", Details). */
	DETAILS,

	/** "Aenderungen" (Changelog), aufklappbar -- ehrlich "keine Angaben", wenn nichts vorliegt. */
	CHANGELOG,

	/** "Weitere Pakete dieses Autors" als Kachelreihe, gefiltert nach {@code manifest.author()}. */
	MORE_FROM_AUTHOR
}
