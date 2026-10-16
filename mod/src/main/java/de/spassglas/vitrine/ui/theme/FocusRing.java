package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.gui.DrawContext;

/**
 * Zeichnet den sichtbaren Fokusrahmen aus docs/UI-SPEC.md "Barrierefreiheit"
 * ("sichtbarer Fokusrahmen aus einem eigenen Token") -- die eine Stelle dafuer,
 * damit jedes selbstgebaute Widget denselben Rahmen bekommt und keine Screen-
 * Klasse die Schleife noch einmal ausschreibt.
 *
 * <p>Breite und Farbe kommen aus den Tokens {@code focus.ring.width} /
 * {@code focus.ring.color} ({@link VitrineTheme#FOCUS_RING_WIDTH} /
 * {@link VitrineTheme#FOCUS_RING_COLOR}). Vanilla-{@code ButtonWidget} bringt
 * seinen eigenen Fokusrahmen mit -- fuer solche Widgets wird diese Klasse
 * nicht aufgerufen.</p>
 */
public final class FocusRing {

	private FocusRing() {
	}

	/** Rahmen mit der Token-Farbe (focus.ring.color). */
	public static void draw(DrawContext context, int x, int y, int width, int height) {
		draw(context, x, y, width, height, de.spassglas.vitrine.ui.theme.ActiveThemeColors.focusRing());
	}

	/**
	 * Abgerundeter, ein Pixel feiner Rahmen mit kleinem Abstand um das Element
	 * -- passend zu den runden Karten/Rahmen (umgekocht, 2026-10-06: der eckige
	 * Doppelrahmen sah bei jedem Teil falsch aus).
	 */
	public static void draw(DrawContext context, int x, int y, int width, int height, int color) {
		// umgekocht (2026-10-06): die blauen Rahmen "komplett entfernen". Fokus zeigen
		// Knoepfe/Felder ueber ihre eigene Optik; Kacheln ueber die Anhebung.
		if (!ENABLED) {
			return;
		}
		int gap = 2;
		int radius = height > 40 ? VitrineTheme.RADIUS_CARD + gap : VitrineTheme.RADIUS_CONTROL + gap;
		RoundedRect.border(context, x - gap, y - gap, width + 2 * gap, height + 2 * gap, radius, color);
	}

	/**
	 * Fokusrahmen fuer ein beliebiges Element -- aber nicht fuer Knoepfe,
	 * Suchfelder und Icon-Knoepfe: die zeigen ihren Fokus schon selbst
	 * (Nine-Slice-Fokusrahmen bzw. Hover-Flaeche). Sonst entsteht ein
	 * zweiter Rahmen um/in den ersten.
	 */
	/** Fokusrahmen sind ausgeschaltet (siehe {@link #draw}). */
	static final boolean ENABLED = false;

	public static void drawFor(DrawContext context, net.minecraft.client.gui.widget.ClickableWidget widget) {
		if (widget instanceof net.minecraft.client.gui.widget.ButtonWidget
				|| widget instanceof net.minecraft.client.gui.widget.TextFieldWidget
				|| widget instanceof de.spassglas.vitrine.ui.widget.IconButtonWidget) {
			return;
		}
		draw(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
	}
}
