package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.image.CatalogImageCache;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Grundlage der Startseiten-Bausteine im Microsoft-Store-Stil (umgekocht,
 * 2026-10-07): weiche, runde Ecken, Flaechen leicht heller als der Grund,
 * Pillen-Knoepfe. Klickflaechen werden beim Zeichnen gesammelt und beim
 * Klick abgefragt -- so bleibt jeder Baustein ein einziges Widget.
 */
public abstract class HomeWidget extends ClickableWidget {

	/** Radius fuer Karten und Bilder: deutlich runder als die Kacheln vorher. */
	public static final int RADIUS = 10;
	public static final int PANEL_COLOR = 0xFF2A2C33;
	public static final int PANEL_HOVER = 0xFF2E323B;
	protected static final Identifier ICON_ARROW = Identifier.of("vitrine", "textures/gui/icons/ui_arrow_right_small.png");
	protected static final Identifier ICON_SPINNER = Identifier.of("vitrine", "textures/gui/icons/ui_spinner.png");
	protected static final Identifier ICON_RESTART = Identifier.of("vitrine", "textures/gui/icons/ui_restart.png");
	protected static final Identifier ICON_CHECK = Identifier.of("vitrine", "textures/gui/icons/ui_check.png");

	protected final TextRenderer textRenderer;
	/**
	 * Blaue Akzent-Pillen nur, wo es die eine Hauptaktion ist (Banner). Sonst
	 * neutral wie "Kostenlos" im Microsoft Store -- HIG buttons.md: "Keep the
	 * number of prominent buttons to one or two per view."
	 */
	protected boolean prominentPills = false;
	protected static final int PILL_NEUTRAL = 0xFF3A3D45;
	protected static final int PILL_NEUTRAL_HOVER = 0xFF474B55;
	private final List<Region> regions = new ArrayList<>();
	protected int mouseX;
	protected int mouseY;

	private record Region(int x, int y, int w, int h, Runnable action) {
	}

	protected HomeWidget(int x, int y, int width, int height, TextRenderer textRenderer, Text label) {
		super(x, y, width, height, label);
		this.textRenderer = textRenderer;
	}

	@Override
	protected final void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		regions.clear();
		draw(context, delta);
	}

	protected abstract void draw(DrawContext context, float delta);

	protected void region(int x, int y, int w, int h, Runnable action) {
		if (action != null) {
			regions.add(new Region(x, y, w, h, action));
		}
	}

	protected boolean hovered(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0 || !isMouseOver(mx, my)) {
			return false;
		}
		// zuletzt gezeichnete (oberste) Flaeche gewinnt, z. B. Pille ueber Karte
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
				playDownSound(net.minecraft.client.MinecraftClient.getInstance().getSoundManager());
				r.action.run();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}

	// --- Zeichenhilfen -------------------------------------------------------

	/** Bild (https-Bild oder Ersatzsymbol) mit runden Ecken; {@code under} = Farbe dahinter. */
	protected void image(DrawContext context, String url, Identifier fallback, int x, int y, int w, int h, int bg,
			int under, int radius, boolean cover) {
		var tex = url == null || url.isEmpty() ? java.util.Optional.<PackageImageTextures.Texture>empty()
				: CatalogImageCache.get(url);
		if (tex.isPresent()) {
			GuiBlend.on();
			if (cover) {
				PackageImageTextures.drawCover(context, tex.get(), x, y, w, h);
			} else {
				context.fill(x, y, x + w, y + h, bg);
				PackageImageTextures.drawFitted(context, tex.get(), x, y, w, h);
			}
		} else {
			context.fill(x, y, x + w, y + h, bg);
			if (fallback != null) {
				int s = Math.min(w, h) * 3 / 5;
				GuiBlend.on();
				context.drawTexture(fallback, x + (w - s) / 2, y + (h - s) / 2, 0, 0, s, s, s, s);
			}
		}
		RoundedRect.maskCorners(context, x, y, w, h, radius, under);
	}

	/** Pillen-Knopf rechtsbuendig an {@code right}; liefert seine Breite. */
	protected int pill(DrawContext context, String text, int right, int y, boolean action, Runnable onClick) {
		int size = VitrineTheme.TYPE_CAPTION;
		int tw = ScaledText.width(textRenderer, text, size);
		// Symbol je Zustand: Laden (drehend), Neustart noetig, installiert
		Identifier icon = null;
		if (text.equals(Text.translatable("vitrine.modrinth.installing").getString())) {
			icon = ICON_SPINNER;
		} else if (text.equals(Text.translatable("vitrine.home.pill.restart").getString())) {
			icon = ICON_RESTART;
		} else if (text.equals(Text.translatable("vitrine.modrinth.installed").getString())) {
			icon = ICON_CHECK;
		}
		int is = 8;
		int w = tw + 16 + (icon != null ? is + 4 : 0);
		int h = size + 10;
		int x = right - w;
		boolean over = hovered(x, y, w, h);
		int fill = action ? (prominentPills ? (over ? 0xFF4A86F0 : ActiveThemeColors.accent())
				: (over ? PILL_NEUTRAL_HOVER : PILL_NEUTRAL)) : (over ? 0x40FFFFFF : 0x26FFFFFF);
		RoundedRect.fill(context, x, y, w, h, h / 2, fill);
		int tx = x + 8;
		if (icon != null) {
			GuiBlend.on();
			int ix = x + 7;
			int iy = y + (h - is) / 2;
			if (icon == ICON_SPINNER) {
				float angle = (net.minecraft.util.Util.getMeasuringTimeMs() % 1000L) / 1000f * 360f;
				context.getMatrices().push();
				context.getMatrices().translate(ix + is / 2f, iy + is / 2f, 0);
				context.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(angle));
				context.drawTexture(icon, -is / 2, -is / 2, 0, 0, is, is, is, is);
				context.getMatrices().pop();
			} else {
				context.drawTexture(icon, ix, iy, 0, 0, is, is, is, is);
			}
			tx += is + 4;
		}
		ScaledText.draw(context, textRenderer, Text.literal(text), tx, y + 5,
				action && prominentPills ? ActiveThemeColors.accentContrast() : ActiveThemeColors.text(), size);
		if (action) {
			region(x, y, w, h, onClick);
		}
		return w;
	}

	/** Pille ueber eine feste Breite (z. B. ganze Kartenbreite im Regal), Text mittig. */
	protected void widePill(DrawContext context, String text, int x, int y, int w, boolean action, Runnable onClick) {
		int size = VitrineTheme.TYPE_CAPTION;
		int h = size + 10;
		boolean over = hovered(x, y, w, h);
		int fill = action ? (prominentPills ? (over ? 0xFF4A86F0 : ActiveThemeColors.accent())
				: (over ? PILL_NEUTRAL_HOVER : PILL_NEUTRAL)) : (over ? 0x40FFFFFF : 0x26FFFFFF);
		RoundedRect.fill(context, x, y, w, h, h / 2, fill);
		int tw = ScaledText.width(textRenderer, text, size);
		ScaledText.draw(context, textRenderer, Text.literal(text), x + (w - tw) / 2, y + 5,
				ActiveThemeColors.text(), size);
		if (action) {
			region(x, y, w, h, onClick);
		}
	}

	/** Ueberschrift "Titel >" wie im Microsoft Store; Klick auf den Titel = {@code onMore}. */
	protected void heading(DrawContext context, Text title, int x, int y, Runnable onMore) {
		int size = VitrineTheme.TYPE_TITLE;
		int w = ScaledText.width(textRenderer, title.getString(), size);
		boolean over = onMore != null && hovered(x, y, w + 16, size + 4);
		ScaledText.draw(context, textRenderer, ScaledText.bold(title), x, y, ActiveThemeColors.text(), size);
		if (onMore != null) {
			int as = size * 2 / 3;
			GuiBlend.on();
			if (over) {
				context.setShaderColor(0.45f, 0.62f, 0.98f, 1f);
			}
			context.drawTexture(ICON_ARROW, x + w + 6, y + (size - as) / 2 + 1, 0, 0, as, as, as, as);
			context.setShaderColor(1f, 1f, 1f, 1f);
			region(x, y, w + 16, size + 4, onMore);
		}
	}

	/** Quell-Abzeichen (Vitrine/Modrinth) unten rechts an einem Logo. */
	protected static void badge(DrawContext context, Identifier badge, int x, int y, int s) {
		if (badge == null || !de.spassglas.vitrine.config.VitrineConfig.get().showSourceBadges) {
			return;
		}
		int b = Math.max(8, s * 3 / 10);
		GuiBlend.on();
		context.drawTexture(badge, x + s - b + 2, y + s - b + 2, 0, 0, b, b, b, b);
	}

	/** Text auf eine Breite kuerzen. */
	protected String fit(String s, int maxW, int size) {
		if (s == null) {
			return "";
		}
		if (ScaledText.width(textRenderer, s, size) <= maxW) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && ScaledText.width(textRenderer, t + "...", size) > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t.stripTrailing() + "...";
	}

	/** Waagrechter Verlauf links dunkel -> rechts durchsichtig (fuer Text auf Bildern). */
	protected static void shadeLeft(DrawContext context, int x, int y, int w, int h, int alpha) {
		int steps = 32;
		for (int i = 0; i < steps; i++) {
			int a = alpha * (steps - i) / steps;
			int x0 = x + w * i / steps;
			int x1 = x + w * (i + 1) / steps;
			context.fill(x0, y, x1, y + h, a << 24);
		}
	}

	/** Senkrechter Verlauf unten dunkel. */
	protected static void shadeBottom(DrawContext context, int x, int y, int w, int h, int alpha) {
		context.fillGradient(x, y + h / 3, x + w, y + h, 0, alpha << 24);
	}

	/**
	 * Weiches Leuchten in {@code rgb} um eine Flaeche ("leichter Glow" des Banners).
	 * Wird NACH dem Bild gezeichnet: eine Folge ein GUI-Pixel breiter, glatt
	 * gerundeter Ringe, deren Deckkraft quadratisch ausklingt -- so gibt es keine
	 * sichtbaren Stufen, und die Ringe fuellen auch die Ecken ausserhalb der
	 * Rundung (dort lag vorher ein eckiger Fleck in Hintergrundfarbe).
	 */
	protected static void glow(DrawContext context, int x, int y, int w, int h, int rgb, int strength) {
		int spread = 22;
		for (int k = 0; k < spread; k++) {
			double t = 1.0 - (double) k / spread;
			int a = (int) Math.round(strength * t * t);
			if (a <= 0) {
				continue;
			}
			RoundedRect.border(context, x - k, y - k, w + 2 * k, h + 2 * k, RADIUS + k, (a << 24) | (rgb & 0xFFFFFF));
		}
	}

	protected static int darken(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 0xff) * f);
		int g = (int) (((rgb >> 8) & 0xff) * f);
		int b = (int) ((rgb & 0xff) * f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
