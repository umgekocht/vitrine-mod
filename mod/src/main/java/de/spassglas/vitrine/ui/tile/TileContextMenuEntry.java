package de.spassglas.vitrine.ui.tile;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein einzelner Eintrag im Rechtsklick-Menue einer Kachel (ROADMAP GATE74).
 *
 * @param action         welche Wirkung ein Klick auf diesen Eintrag ausloest
 * @param translationKey Uebersetzungs-Key der Beschriftung
 * @param argument       Paket-eigener Freitext fuer einen {@code %s}-Platzhalter im
 *                        Uebersetzungstext (z. B. der Autorenname fuer
 *                        {@link TileContextMenuAction#AUTHOR}), oder leer, wenn die
 *                        Beschriftung keinen Platzhalter hat
 */
public record TileContextMenuEntry(TileContextMenuAction action, String translationKey, Optional<String> argument) {

	public TileContextMenuEntry {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(translationKey, "translationKey");
		Objects.requireNonNull(argument, "argument");
	}

	/** Bequemlichkeits-Konstruktor fuer einen Eintrag ohne Platzhalter-Argument. */
	public static TileContextMenuEntry of(TileContextMenuAction action, String translationKey) {
		return new TileContextMenuEntry(action, translationKey, Optional.empty());
	}

	/** Bequemlichkeits-Konstruktor fuer einen Eintrag mit genau einem Platzhalter-Argument. */
	public static TileContextMenuEntry withArgument(TileContextMenuAction action, String translationKey,
			String argument) {
		return new TileContextMenuEntry(action, translationKey, Optional.of(argument));
	}
}
