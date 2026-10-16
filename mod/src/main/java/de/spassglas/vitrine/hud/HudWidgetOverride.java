package de.spassglas.vitrine.hud;

/**
 * Nutzer-Anpassung fuer genau ein HUD-Widget (ROADMAP T6, In-Game-HUD-Editor).
 * Rein additiv <em>ueber</em> dem schreibgeschuetzten Paket-Layout -- der
 * Editor mutiert nie das {@link HudLayout} eines Pakets, sondern haelt seine
 * Aenderungen getrennt in {@link HudLayoutOverrides} und persistiert sie nach
 * {@code config/vitrine/hud/overrides.json}.
 *
 * <p>Jedes Feld ist optional ({@code null} = "keine Anpassung, Paket-Wert
 * gilt"):</p>
 * <ul>
 *   <li>{@link #anchor()} -- geaenderte Bezugsecke/-kante (ausrichten).</li>
 *   <li>{@link #offset()} -- geaenderter Pixel-Versatz (verschieben).</li>
 *   <li>{@link #visible()} -- {@code TRUE} = immer zeigen, {@code FALSE} = nie
 *       zeigen (an-/abschalten); {@code null} = Paket-Sichtbarkeit gilt.</li>
 * </ul>
 */
public record HudWidgetOverride(HudAnchor anchor, HudOffset offset, Boolean visible) {

	/** Anpassung ohne Inhalt -- Ausgangspunkt fuer {@code with*}-Aenderungen. */
	public static final HudWidgetOverride EMPTY = new HudWidgetOverride(null, null, null);

	/** Ob diese Anpassung nichts ueberschreibt (dann muss sie nicht gespeichert werden). */
	public boolean isEmpty() {
		return anchor == null && offset == null && visible == null;
	}

	public HudWidgetOverride withAnchor(HudAnchor newAnchor) {
		return new HudWidgetOverride(newAnchor, offset, visible);
	}

	public HudWidgetOverride withOffset(HudOffset newOffset) {
		return new HudWidgetOverride(anchor, newOffset, visible);
	}

	public HudWidgetOverride withVisible(Boolean newVisible) {
		return new HudWidgetOverride(anchor, offset, newVisible);
	}
}
