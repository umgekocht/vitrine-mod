package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.gui.DrawContext;

/**
 * Gerundete Rechtecke fuer die Tokens {@code radius.card} und
 * {@code radius.control} (GATE21, Teil 2).
 *
 * <p>{@link DrawContext} kennt nur achsenparallele Rechtecke. Eine Rundung
 * entsteht hier zeilenweise: in den obersten und untersten {@code r} Zeilen
 * wird jede Zeile um {@link #inset(int, int)} Pixel eingerueckt -- der Abstand
 * des Viertelkreises vom Rand in dieser Zeile. Radius 0 zeichnet exakt ein
 * normales Rechteck.</p>
 */
public final class RoundedRect {

	private RoundedRect() {
	}

	/**
	 * Einrueckung der Zeile {@code row} (0 = aeusserste Zeile) in einer Ecke
	 * mit Radius {@code radius}. Gemessen in der Zeilenmitte, abgerundet auf
	 * ganze Pixel. Ab Zeile {@code radius} immer 0.
	 */
	public static int inset(int radius, int row) {
		if (radius <= 0 || row >= radius) {
			return 0;
		}
		double dy = radius - row - 0.5;
		double dx = Math.sqrt((double) radius * radius - dy * dy);
		return (int) Math.round(radius - dx);
	}

	/** Radius, begrenzt auf die halbe kuerzere Kante (sonst ueberlappen die Ecken). */
	public static int clampRadius(int radius, int width, int height) {
		return Math.max(0, Math.min(radius, Math.min(width, height) / 2));
	}

	/** Gefuelltes Rechteck mit gerundeten Ecken. */
	public static void fill(DrawContext context, int x, int y, int width, int height, int radius, int color) {
		int r = clampRadius(radius, width, height);
		if (r == 0) {
			context.fill(x, y, x + width, y + height, color);
			return;
		}
		// glatte Ecken (SmoothCorners), dazwischen gerade Flaechen
		context.fill(x + r, y, x + width - r, y + r, color);
		context.fill(x, y + r, x + width, y + height - r, color);
		context.fill(x + r, y + height - r, x + width - r, y + height, color);
		if (smooth()) {
			SmoothCorners.corners(context, SmoothCorners.Kind.FILL, x, y, width, height, r, color, 15);
			return;
		}
		for (int row = 0; row < r; row++) {
			int in = inset(r, row);
			context.fill(x + in, y + row, x + r, y + row + 1, color);
			context.fill(x + width - r, y + row, x + width - in, y + row + 1, color);
			context.fill(x + in, y + height - row - 1, x + r, y + height - row, color);
			context.fill(x + width - r, y + height - row - 1, x + width - in, y + height - row, color);
		}
	}

	/** Glatte Rundung nur mit laufendem Client (Tests zeichnen ohne Fenster). */
	private static boolean smooth() {
		try {
			var mc = net.minecraft.client.MinecraftClient.getInstance();
			return mc != null && mc.getWindow() != null && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
		} catch (Throwable t) {
			return false;
		}
	}

	/** Ein Pixel breiter Rahmen mit gerundeten Ecken. */
	public static void border(DrawContext context, int x, int y, int width, int height, int radius, int color) {
		int r = clampRadius(radius, width, height);
		if (r == 0) {
			context.drawBorder(x, y, width, height, color);
			return;
		}
		// gerade Kanten
		context.fill(x + r, y, x + width - r, y + 1, color);
		context.fill(x + r, y + height - 1, x + width - r, y + height, color);
		context.fill(x, y + r, x + 1, y + height - r, color);
		context.fill(x + width - 1, y + r, x + width, y + height - r, color);
		if (smooth()) {
			SmoothCorners.corners(context, SmoothCorners.Kind.RING, x, y, width, height, r, color, 15);
			return;
		}
		// Boegen: je Zeile vom eigenen Einzug bis zum Einzug der Nachbarzeile
		for (int row = 0; row < r; row++) {
			int in = inset(r, row);
			int next = row + 1 < r ? inset(r, row + 1) : 0;
			int span = Math.max(1, in - next);
			// oberste Zeile schliesst an die gerade Kante an (die erst bei x + r beginnt)
			int end = row == 0 ? r : Math.min(in + span, r);
			int top = y + row;
			int bottom = y + height - row - 1;
			context.fill(x + in, top, x + end, top + 1, color);
			context.fill(x + width - end, top, x + width - in, top + 1, color);
			context.fill(x + in, bottom, x + end, bottom + 1, color);
			context.fill(x + width - end, bottom, x + width - in, bottom + 1, color);
		}
	}

	/**
	 * Uebermalt die Pixel ausserhalb der oberen Rundung mit {@code outsideColor}.
	 * Fuer Inhalte, die als Rechteck in eine gerundete Flaeche gezeichnet
	 * werden (z. B. das Kachelbild oben in der Karte).
	 */
	public static void maskTopCorners(DrawContext context, int x, int y, int width, int height, int radius,
			int outsideColor) {
		int r = clampRadius(radius, width, height);
		if (smooth()) {
			SmoothCorners.corners(context, SmoothCorners.Kind.MASK, x, y, width, height, r, outsideColor, 3);
			return;
		}
		for (int row = 0; row < r; row++) {
			int in = inset(r, row);
			if (in > 0) {
				context.fill(x, y + row, x + in, y + row + 1, outsideColor);
				context.fill(x + width - in, y + row, x + width, y + row + 1, outsideColor);
			}
		}
	}

	/**
	 * Wie {@link #maskTopCorners}, deckt aber alle vier Ecken ab -- fuer ein
	 * freistehendes, rechteckig gezeichnetes Bild ohne eigene Karten-Flaeche
	 * darunter (z. B. Kopfbild/Screenshot-Galerie, ROADMAP GATE54), bei dem
	 * anders als bei einer {@code TileRowWidget}-Karte (Textzeile unten, nur
	 * oben gerundet) auch die unteren Ecken sichtbar blieben.
	 */
	public static void maskCorners(DrawContext context, int x, int y, int width, int height, int radius,
			int outsideColor) {
		int r = clampRadius(radius, width, height);
		if (smooth()) {
			SmoothCorners.corners(context, SmoothCorners.Kind.MASK, x, y, width, height, r, outsideColor, 15);
			return;
		}
		for (int row = 0; row < r; row++) {
			int in = inset(r, row);
			if (in <= 0) {
				continue;
			}
			int top = y + row;
			int bottom = y + height - row - 1;
			context.fill(x, top, x + in, top + 1, outsideColor);
			context.fill(x + width - in, top, x + width, top + 1, outsideColor);
			context.fill(x, bottom, x + in, bottom + 1, outsideColor);
			context.fill(x + width - in, bottom, x + width, bottom + 1, outsideColor);
		}
	}
}
