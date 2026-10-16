package de.spassglas.vitrine.hud;

/**
 * Versatz in unskalierten Pixeln vom gewaehlten {@link HudAnchor} aus
 * (Schema: {@code $defs/offset}). Fehlt {@code offset} an einem Widget, gilt
 * {@link #ZERO} -- kein Versatz.
 */
public record HudOffset(int x, int y) {

	/** Dokumentierter Default, wenn ein Widget kein {@code offset} angibt. */
	public static final HudOffset ZERO = new HudOffset(0, 0);
}
