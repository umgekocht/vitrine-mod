package de.spassglas.vitrine.ui.tile;

/**
 * Die vier moeglichen Wirkungen eines Rechtsklick-Menuepunkts auf einer
 * Store-Kachel (docs/UI-SPEC.md:112-114; ROADMAP GATE74). Reine Aufzaehlung
 * ohne Minecraft-Typ, damit {@link TileContextMenu} ohne laufenden Client
 * getestet werden kann -- {@link TileRowWidget} bindet die eigentliche
 * Wirkung (Installationspfad ausloesen, Detailseite oeffnen, Melde-Hinweis
 * zeigen) erst beim Klick auf einen gezeichneten Menuepunkt.
 */
public enum TileContextMenuAction {

	/**
	 * "Installieren" (bzw. je Installationszustand "Aktualisieren" /
	 * "Deinstallieren", siehe {@link TileContextMenu#entriesFor(TileData)}) --
	 * derselbe Installationspfad wie der "Holen"-Knopf auf der Kachel bzw.
	 * der Aktionsknopf der Detailseite.
	 */
	INSTALL,

	/** Oeffnet die Detailseite -- wie ein normaler Klick auf die Kachel. */
	DETAILS,

	/**
	 * Zeigt den Autorennamen; oeffnet dafuer die Detailseite, weil dort (Kopf
	 * plus Abschnitt "Weitere Pakete dieses Autors") die einzige bestehende
	 * Autorenansicht im Store liegt -- es gibt keine eigene Autoren-Seite.
	 */
	AUTHOR,

	/**
	 * Meldet ein Paket. Es gibt noch keinen Melde-Mechanismus (kein Dienst,
	 * keine Formular-URL) -- der Klick zeigt ehrlich einen Hinweis, dass das
	 * noch nicht moeglich ist, statt eine nicht vorhandene Funktion
	 * vorzugaukeln (Charter-Regel 2).
	 */
	REPORT
}
