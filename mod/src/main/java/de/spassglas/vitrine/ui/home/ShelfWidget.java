package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Regal wie "Your video streaming shelf" im Microsoft Store: Karten mit
 * getoenter Bildflaeche (Grundfarbe des Logos), Logo mittig, Name und Pille.
 * Pfeile rechts oben blaettern seitenweise.
 */
public final class ShelfWidget extends HomeWidget {

	private static final int CARD_W = 104;
	private static final int GAP = 10;

	private final Text title;
	private final List<HomeItem> items;
	private final Runnable onMore;
	private int page;

	public ShelfWidget(int x, int y, int width, TextRenderer tr, Text title, List<HomeItem> items, Runnable onMore) {
		super(x, y, width, heightFor(), tr, title);
		this.title = title;
		this.items = items;
		this.onMore = onMore;
	}

	public static int heightFor() {
		return VitrineTheme.TYPE_TITLE + 14 + 150;
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		int x = getX();
		int y = getY();
		// "Alle anzeigen ›" endet vor den Blaetter-Pfeilen (x+width-48..x+width),
		// sonst ueberlappen sich beide Trefferflaechen.
		heading(context, title, x, y, x + getWidth() - 56, onMore);
		int perPage = Math.max(1, (getWidth() + GAP) / (CARD_W + GAP));
		int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);
		arrow(context, x + getWidth() - 48, y, LEFT, page > 0, () -> page--);
		arrow(context, x + getWidth() - 22, y, RIGHT, page < pages - 1, () -> page++);
		int cardW = (getWidth() - (perPage - 1) * GAP) / perPage;
		int top = y + VitrineTheme.TYPE_TITLE + 14;
		for (int i = 0; i < perPage; i++) {
			int idx = page * perPage + i;
			if (idx >= items.size()) {
				break;
			}
			card(context, items.get(idx), x + i * (cardW + GAP), top, cardW, 150);
		}
	}

	private static final net.minecraft.util.Identifier LEFT =
			net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_chevron_left.png");
	private static final net.minecraft.util.Identifier RIGHT =
			net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_chevron_right.png");

	private void arrow(DrawContext context, int x, int y, net.minecraft.util.Identifier icon, boolean enabled,
			Runnable action) {
		boolean over = enabled && hovered(x, y, 20, 20);
		RoundedRect.fill(context, x, y, 20, 20, 10, over ? 0x40FFFFFF : enabled ? 0x22FFFFFF : 0x0FFFFFFF);
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.setShaderColor(1f, 1f, 1f, enabled ? 1f : 0.3f);
		context.drawTexture(icon, x + 5, y + 5, 0, 0, 10, 10, 10, 10);
		context.setShaderColor(1f, 1f, 1f, 1f);
		if (enabled) {
			region(x, y, 20, 20, action);
		}
	}

	private void card(DrawContext context, HomeItem it, int x, int y, int w, int h) {
		boolean over = hovered(x, y, w, h);
		int bg = de.spassglas.vitrine.ui.theme.ActiveThemeColors.bg();
		int imgH = h * 58 / 100;
		RoundedRect.fill(context, x, y, w, h, RADIUS, over ? PANEL_HOVER : PANEL_COLOR);
		context.fill(x, y, x + w, y + imgH, darken(it.color(), over ? 0.6f : 0.45f));
		int s = imgH * 62 / 100;
		image(context, it.imageUrl(), it.fallback(), x + (w - s) / 2, y + (imgH - s) / 2, s, s,
				darken(it.color(), 0.45f), darken(it.color(), over ? 0.6f : 0.45f), 8, false);
		badge(context, it.badge(), x + (w - s) / 2, y + (imgH - s) / 2, s);
		RoundedRect.maskCorners(context, x, y, w, h, RADIUS, bg);
		region(x, y, w, h, it.onOpen());
		ScaledText.draw(context, textRenderer, Text.literal(fit(it.title(), w - 16, VitrineTheme.TYPE_BODY)), x + 8,
				y + imgH + 8, 0xFFFFFFFF, VitrineTheme.TYPE_BODY);
		widePill(context, it.pill().get(), x + 8, y + h - VitrineTheme.TYPE_CAPTION - 18, w - 16,
				it.pillIsAction().get(), it.onGet());
	}
}
