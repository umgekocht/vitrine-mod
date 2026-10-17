package de.spassglas.vitrine.ui.tile;

import java.util.List;
import java.util.Objects;

/**
 * Das kleine Rechtsklick-Menue einer Store-Kachel (docs/UI-SPEC.md:112-114:
 * "Beim Ueberfahren: ... Klick oeffnet die Detailseite; Rechtsklick oeffnet
 * ein kleines Menue (Installieren, Details, Autor, Melden)." -- ROADMAP
 * GATE74).
 *
 * <p>Reines Datenmodell, kein Minecraft-Typ: {@link #entriesFor(TileData)}
 * liefert immer genau die vier Eintraege in genau dieser Reihenfolge, damit
 * sich das ohne laufenden Client beweisen laesst ({@code TileContextMenuTest}).
 * {@link TileRowWidget} zeichnet nur, was hier herauskommt, und bindet den
 * Klick auf einen Eintrag an die passende Wirkung.</p>
 */
public final class TileContextMenu {

	private TileContextMenu() {
	}

	/**
	 * Die vier Eintraege fuer {@code tile}. Der erste Eintrag
	 * ({@link TileContextMenuAction#INSTALL}) traegt je nach
	 * {@link TileStatus} dieselbe Beschriftung wie der Aktionsknopf der
	 * Detailseite in diesem Zustand ({@link de.spassglas.vitrine.ui.detail.PackageActionState}):
	 * noch nicht installiert -&gt; "Installieren", eine neuere Version
	 * verfuegbar -&gt; "Aktualisieren", sonst (installiert bzw. installiert-aber-
	 * Neustart-noetig) -&gt; "Deinstallieren" -- derselbe Installationspfad
	 * laeuft in jedem Fall ueber {@link TileRowWidget}s bestehende
	 * "Holen"-/Detailseiten-Anbindung, nicht ueber eine eigene Logik hier.
	 */
	public static List<TileContextMenuEntry> entriesFor(TileData tile) {
		Objects.requireNonNull(tile, "tile");
		return List.of(
				TileContextMenuEntry.of(TileContextMenuAction.INSTALL, installLabelKey(tile.status())),
				TileContextMenuEntry.of(TileContextMenuAction.DETAILS, "vitrine.tile.menu.details"),
				TileContextMenuEntry.withArgument(TileContextMenuAction.AUTHOR, "vitrine.detail.author_label",
						tile.author()),
				TileContextMenuEntry.of(TileContextMenuAction.REPORT, "vitrine.tile.menu.report"));
	}

	private static String installLabelKey(TileStatus status) {
		return switch (status) {
			case NONE -> "vitrine.action.install";
			case UPDATE_AVAILABLE -> "vitrine.action.update";
			case INSTALLED, RESTART_REQUIRED -> "vitrine.action.uninstall";
		};
	}
}
