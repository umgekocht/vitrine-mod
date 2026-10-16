package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Banner oben auf der Startseite wie im Microsoft Store: links ein grosses,
 * rotierendes Hauptbild mit leichtem Leuchten in seiner Grundfarbe, rechts
 * eine grosse und zwei kleine Kacheln.
 */
public final class BannerWidget extends HomeWidget {

	private static final long ROTATE_MS = 7000;

	private final List<HomeItem> slides;
	private final HomeItem sideTop;
	private final HomeItem sideLeft;
	private final HomeItem sideRight;
	private final boolean reduceMotion;
	private static int current;
	private static long shownSince = Util.getMeasuringTimeMs();

	public BannerWidget(int x, int y, int width, TextRenderer tr, List<HomeItem> slides, HomeItem sideTop,
			HomeItem sideLeft, HomeItem sideRight, boolean reduceMotion) {
		super(x, y, width, heightFor(width), tr, Text.translatable("vitrine.home.banner"));
		this.slides = slides;
		this.sideTop = sideTop;
		this.sideLeft = sideLeft;
		this.sideRight = sideRight;
		this.reduceMotion = reduceMotion;
		this.prominentPills = true;
		if (current >= slides.size()) {
			current = 0;
		}
	}

	public static int heightFor(int width) {
		return Math.min(280, width * 34 / 100) + 18;
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight() - 18;
		int gap = 10;
		int heroW = w * 62 / 100;
		int sideX = x + heroW + gap;
		int sideW = w - heroW - gap;
		int bg = ActiveThemeColors.bg();

		if (!slides.isEmpty()) {
			long now = Util.getMeasuringTimeMs();
			boolean heroHovered = hovered(x, y, heroW, h);
			if (!reduceMotion && de.spassglas.vitrine.config.VitrineConfig.get().bannerAutoplay && !heroHovered
					&& now - shownSince > ROTATE_MS) {
				current = (current + 1) % slides.size();
				shownSince = now;
			}
			HomeItem s = slides.get(current);
			if (s.imageUrl() != null && de.spassglas.vitrine.ui.image.CatalogImageCache.get(s.imageUrl()).isPresent()) {
				image(context, s.imageUrl(), s.fallback(), x, y, heroW, h, darken(s.color(), 0.5f), bg, RADIUS, true);
			} else {
				placeholder(context, x, y, heroW, h);
			}
			shadeLeft(context, x, y, heroW * 4 / 5, h, 235);
			RoundedRect.maskCorners(context, x, y, heroW, h, RADIUS, bg);
			if (de.spassglas.vitrine.config.VitrineConfig.get().bannerGlow) {
				glow(context, x, y, heroW, h, s.color(), 90);
			}
			region(x, y, heroW, h, s.onOpen());
			int tx = x + 24;
			int titleSize = VitrineTheme.TYPE_TITLE + 8;
			int ty = y + h / 2 - titleSize - 14;
			ScaledText.draw(context, textRenderer, ScaledText.bold(Text.literal(fit(s.title(), heroW / 2, titleSize))), tx, ty,
					0xFFFFFFFF, titleSize, false);
			int subH = 0;
			if (s.subtitle() != null) {
				subH = ScaledText.drawWrapped(context, textRenderer, Text.literal(fit(s.subtitle(), heroW, VitrineTheme.TYPE_BODY)),
						tx, ty + titleSize + 8, heroW / 2, 0xFFE8ECF4, VitrineTheme.TYPE_BODY);
			}
			String pillText = s.pill().get();
			int pw = ScaledText.width(textRenderer, pillText, VitrineTheme.TYPE_CAPTION) + 16;
			pill(context, pillText, tx + pw, ty + titleSize + 8 + subH + 14,
					s.pillIsAction().get(), s.onGet());
			// Punkte unter dem Hauptbild
			int dotsW = slides.size() * 10;
			int dx = x + (heroW - dotsW) / 2;
			int dy = y + h + 8;
			for (int i = 0; i < slides.size(); i++) {
				boolean on = i == current;
				RoundedRect.fill(context, dx + i * 10, dy, on ? 6 : 4, on ? 6 : 4, 2,
						on ? ActiveThemeColors.text() : ActiveThemeColors.textMuted());
				int idx = i;
				region(dx + i * 10 - 2, dy - 3, 10, 12, () -> {
					current = idx;
					shownSince = Util.getMeasuringTimeMs();
				});
			}
		}

		int topH = h * 62 / 100;
		if (sideTop != null) {
			tile(context, sideTop, sideX, y, sideW, topH, true);
		}
		int bottomY = y + topH + gap;
		int bottomH = h - topH - gap;
		int halfW = (sideW - gap) / 2;
		if (sideLeft != null) {
			tile(context, sideLeft, sideX, bottomY, halfW, bottomH, false);
		}
		if (sideRight != null) {
			tile(context, sideRight, sideX + halfW + gap, bottomY, sideW - halfW - gap, bottomH, false);
		}
	}

	private static final net.minecraft.util.Identifier PLACEHOLDER =
			net.minecraft.util.Identifier.of("vitrine", "textures/gui/banner_placeholder.png");

	/** Ruhiges Platzhalterbild (assets v4, 8:3), mittig zugeschnitten. */
	private static void placeholder(DrawContext context, int x, int y, int w, int h) {
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		int pw = Math.max(w, h * 8 / 3);
		int ph = pw * 3 / 8;
		context.enableScissor(x, y, x + w, y + h);
		context.drawTexture(PLACEHOLDER, x + (w - pw) / 2, y + (h - ph) / 2, 0, 0, pw, ph, pw, ph);
		context.disableScissor();
	}

	private void tile(DrawContext context, HomeItem item, int x, int y, int w, int h, boolean big) {
		int bg = ActiveThemeColors.bg();
		boolean over = hovered(x, y, w, h);
		if (item.imageUrl() != null) {
			image(context, item.imageUrl(), null, x, y, w, h, darken(item.color(), over ? 0.75f : 0.6f), bg, RADIUS,
					true);
		} else if (item.fallback() != null && item.fallback().getPath().contains("/collections/")) {
			// Sammlungs-Kachel mit Kartenbild (assets v4)
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			float b = over ? 1f : 0.88f;
			context.setShaderColor(b, b, b, 1f);
			int iw = Math.max(w, h * 1200 / 640);
			int ih = iw * 640 / 1200;
			context.enableScissor(x, y, x + w, y + h);
			context.drawTexture(item.fallback(), x + (w - iw) / 2, y + (h - ih) / 2, 0, 0, iw, ih, iw, ih);
			context.disableScissor();
			context.setShaderColor(1f, 1f, 1f, 1f);
		} else {
			image(context, null, item.fallback(), x, y, w, h, darken(item.color(), over ? 0.75f : 0.6f), bg, RADIUS,
					false);
		}
		shadeBottom(context, x, y, w, h, 170);
		RoundedRect.maskCorners(context, x, y, w, h, RADIUS, bg);
		int size = big ? VitrineTheme.TYPE_TITLE : VitrineTheme.TYPE_BODY;
		ScaledText.draw(context, textRenderer, ScaledText.bold(Text.literal(fit(item.title(), w - 28, size))), x + 14,
				y + h - size - 14, 0xFFFFFFFF, size, false);
		region(x, y, w, h, item.onOpen());
	}
}
