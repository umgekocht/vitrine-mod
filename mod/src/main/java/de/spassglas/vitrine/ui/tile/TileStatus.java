package de.spassglas.vitrine.ui.tile;

import java.util.Optional;

/**
 * Die vier Statuszeichen einer Kachel aus {@code docs/UI-SPEC.md} ("4.
 * Kachel"): "rechts unten Statuszeichen: Installiert (Haken), Update
 * (Pfeil), Neustart noetig (Warnzeichen, nur Klasse B)" -- und implizit ein
 * vierter Zustand, {@link #NONE}: das normale Angebot ohne jedes Zeichen.
 *
 * <p>Barrierefreiheit ("Kein Zustand nur ueber Farbe -- immer zusaetzlich
 * Symbol oder Text"): jeder Zustand ausser {@link #NONE} traegt sowohl ein
 * Icon-Asset als auch einen Uebersetzungs-Key, nie nur eine Farbe.</p>
 *
 * <p>{@link #RESTART_REQUIRED} ist laut Spec "nur Klasse B" (ROADMAP M7) --
 * dieses reine Datenmodell erzwingt das nicht selbst, das ist Sache der
 * spaeteren Datenquelle (S2/M7), die einer Klasse-A-Kachel diesen Status gar
 * nicht erst zuweisen darf.</p>
 */
public enum TileStatus {

	/** Kein Statuszeichen -- das normale, noch nicht installierte Angebot. */
	NONE(null, null),

	/** Haken: bereits installiert. */
	INSTALLED("textures/gui/icons/ui_check.png", "vitrine.tile.status.installed"),

	/** Pfeil: eine neuere Version ist verfuegbar. */
	UPDATE_AVAILABLE("textures/gui/icons/ui_download.png", "vitrine.tile.status.update_available"),

	/** Warnzeichen: installiert, aber erst nach einem Neustart aktiv (Klasse B). */
	RESTART_REQUIRED("textures/gui/icons/ui_warning.png", "vitrine.restart.required");

	private final String iconPath;
	private final String translationKey;

	TileStatus(String iconPath, String translationKey) {
		this.iconPath = iconPath;
		this.translationKey = translationKey;
	}

	/** Ob dieser Zustand ueberhaupt ein sichtbares Zeichen zeichnet. */
	public boolean hasIndicator() {
		return iconPath != null;
	}

	/** Pfad relativ zu {@code assets/vitrine/}, oder leer bei {@link #NONE}. */
	public Optional<String> iconPath() {
		return Optional.ofNullable(iconPath);
	}

	/** Uebersetzungs-Key des Klartexts (Tooltip/Screenreader), oder leer bei {@link #NONE}. */
	public Optional<String> translationKey() {
		return Optional.ofNullable(translationKey);
	}
}
