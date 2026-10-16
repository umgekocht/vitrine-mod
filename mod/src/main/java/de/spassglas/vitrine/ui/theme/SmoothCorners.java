package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Glatte, kantengeglaettete Rundungen (umgekocht, 2026-10-07: "alle Ecken rund,
 * keine Pixel sehen"). Statt einer Treppe aus GUI-Pixeln (die bei GUI-Skala 2-4
 * deutlich sichtbar ist) wird je Radius und Skala einmal eine Kreis-Textur in
 * echter Bildschirmaufloesung erzeugt -- mit 4x4-Supersampling pro Pixel -- und
 * fuer jede Ecke der passende Viertel-Ausschnitt eingefaerbt gezeichnet.
 *
 * <p>Drei Arten: {@link Kind#FILL} (Viertelscheibe), {@link Kind#MASK} (alles
 * ausserhalb der Rundung, zum Abdecken eckiger Bilder) und {@link Kind#RING}
 * (ein GUI-Pixel breiter Bogen fuer Rahmen).</p>
 */
public final class SmoothCorners {

	public enum Kind { FILL, MASK, RING }

	private record Key(Kind kind, int radiusPx, int ringPx) {
	}

	private static final Map<Key, Identifier> CACHE = new HashMap<>();

	private SmoothCorners() {
	}

	/** Physische Pixel je GUI-Einheit (gerundet, mindestens 1). */
	static int scale() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return 1;
		}
		return Math.max(1, (int) Math.round(mc.getWindow().getScaleFactor()));
	}

	private static Identifier texture(Kind kind, int radius) {
		int s = scale();
		Key key = new Key(kind, radius * s, s);
		Identifier id = CACHE.get(key);
		if (id != null) {
			return id;
		}
		int rPx = radius * s;
		int size = rPx * 2;
		NativeImage img = new NativeImage(size, size, true);
		double ringInner = rPx - s;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int inOuter = 0;
				int inInner = 0;
				for (int sy = 0; sy < 4; sy++) {
					for (int sx = 0; sx < 4; sx++) {
						double dx = x + (sx + 0.5) / 4.0 - rPx;
						double dy = y + (sy + 0.5) / 4.0 - rPx;
						double d2 = dx * dx + dy * dy;
						if (d2 <= (double) rPx * rPx) {
							inOuter++;
						}
						if (d2 <= ringInner * ringInner) {
							inInner++;
						}
					}
				}
				double cover = switch (kind) {
					case FILL -> inOuter / 16.0;
					case MASK -> 1.0 - inOuter / 16.0;
					case RING -> (inOuter - inInner) / 16.0;
				};
				int a = (int) Math.round(Math.max(0, Math.min(1, cover)) * 255);
				img.setColor(x, y, a << 24 | 0x00FFFFFF);
			}
		}
		NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
		tex.setFilter(true, false);
		id = Identifier.of("vitrine", "smooth_corner/" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + rPx + "_" + s);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
		CACHE.put(key, id);
		return id;
	}

	/**
	 * Zeichnet die gewaehlten Ecken eines Rechtecks in {@code color}.
	 *
	 * @param corners Bitmaske: 1 = oben links, 2 = oben rechts, 4 = unten links, 8 = unten rechts
	 */
	static void corners(DrawContext context, Kind kind, int x, int y, int w, int h, int r, int color, int corners) {
		if (r <= 0 || ((color >>> 24) == 0 && kind != Kind.MASK)) {
			return;
		}
		Identifier id = texture(kind, r);
		int tex = r * scale() * 2;
		int half = tex / 2;
		float a = ((color >>> 24) & 0xff) / 255f;
		float red = ((color >> 16) & 0xff) / 255f;
		float green = ((color >> 8) & 0xff) / 255f;
		float blue = (color & 0xff) / 255f;
		GuiBlend.on();
		context.setShaderColor(red, green, blue, a);
		if ((corners & 1) != 0) {
			context.drawTexture(id, x, y, r, r, 0, 0, half, half, tex, tex);
		}
		if ((corners & 2) != 0) {
			context.drawTexture(id, x + w - r, y, r, r, half, 0, half, half, tex, tex);
		}
		if ((corners & 4) != 0) {
			context.drawTexture(id, x, y + h - r, r, r, 0, half, half, half, tex, tex);
		}
		if ((corners & 8) != 0) {
			context.drawTexture(id, x + w - r, y + h - r, r, r, half, half, half, half, tex, tex);
		}
		context.setShaderColor(1f, 1f, 1f, 1f);
	}
}
