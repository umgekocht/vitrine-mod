package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Zeichnet umgekocht's Nine-Slice-Rahmen (assets v2, {@code textures/gui/ui/},
 * siehe {@code ui_textures.md}): Ecken fest, Kanten und Mitte gestreckt.
 *
 * <p>Die Dateien liegen in doppelter Nenngroesse vor (aus den @4x-Vorlagen).
 * Die Nenngroesse ist in Bildschirmpixeln gedacht -- ein 40er-Knopf ist 40
 * echte Pixel hoch. Deshalb wird die Eckengroesse in GUI-Einheiten aus der
 * aktuellen GUI-Skalierung berechnet ({@code inset / scale}), sonst waeren die
 * Ecken bei Skalierung 2 doppelt so dick wie gezeichnet.</p>
 */
public final class NineSlice {

	public static final Identifier BUTTON = ui("button_enabled");
	public static final Identifier BUTTON_DISABLED = ui("button_disabled");
	public static final Identifier BUTTON_ACCENT = ui("button_accent");
	public static final Identifier BUTTON_ACCENT_HOVER = ui("button_accent_hover");
	public static final Identifier BUTTON_ACCENT_PRESSED = ui("button_accent_pressed");
	public static final Identifier SEARCH = ui("search_field");
	public static final Identifier SEARCH_FOCUS = ui("search_field_focus");
	public static final Identifier PANEL = ui("panel_frame");
	public static final Identifier PANEL_FLAT = ui("panel_frame_flat");
	public static final Identifier LABEL = ui("label_frame");

	private NineSlice() {
	}

	private static Identifier ui(String name) {
		return Identifier.of("vitrine", "textures/gui/ui/" + name + ".png");
	}

	/** Nenngroesse und Rand (aus ui_textures.md) je Rahmen. */
	private static int[] spec(Identifier id) {
		String p = id.getPath();
		if (p.contains("panel_frame")) {
			return new int[] {48, 14};
		}
		if (p.contains("label_frame")) {
			return new int[] {32, 10};
		}
		return new int[] {40, 13};
	}

	public static void draw(DrawContext context, Identifier id, int x, int y, int w, int h) {
		int[] spec = spec(id);
		int nominal = spec[0];
		int tex = nominal * 2;
		int insetTex = spec[1] * 2;
		double scale = Math.max(1.0, MinecraftClient.getInstance().getWindow().getScaleFactor());
		int inset = (int) Math.max(1, Math.round(spec[1] / scale));
		inset = Math.min(inset, Math.min(w / 2, h / 2));
		int mid = tex - 2 * insetTex;
		int innerW = Math.max(0, w - 2 * inset);
		int innerH = Math.max(0, h - 2 * inset);
		GuiBlend.on();
		// Zeilen: oben, Mitte, unten
		int[][] rows = {{y, inset, 0, insetTex}, {y + inset, innerH, insetTex, mid}, {y + h - inset, inset, tex - insetTex, insetTex}};
		int[][] cols = {{x, inset, 0, insetTex}, {x + inset, innerW, insetTex, mid}, {x + w - inset, inset, tex - insetTex, insetTex}};
		for (int[] r : rows) {
			if (r[1] <= 0) {
				continue;
			}
			for (int[] c : cols) {
				if (c[1] <= 0) {
					continue;
				}
				context.drawTexture(id, c[0], r[0], c[1], r[1], c[2], r[2], c[3], r[3], tex, tex);
			}
		}
	}
}
