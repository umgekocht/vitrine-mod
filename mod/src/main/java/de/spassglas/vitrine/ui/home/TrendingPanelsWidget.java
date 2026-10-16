package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Zwei Panels nebeneinander wie "Trending games / Trending apps" im Microsoft
 * Store: links die meistgeladenen Modrinth-Downloads, rechts die
 * Vitrine-Addons. Je Panel zwei Spalten mit Symbol, Name und Pille.
 */
public final class TrendingPanelsWidget extends HomeWidget {

	private static final int ROWS = 3;
	private static final int ITEM_H = 58;

	private final Text leftTitle;
	private final List<HomeItem> left;
	private final Runnable leftMore;
	private final Text rightTitle;
	private final List<HomeItem> right;
	private final Runnable rightMore;

	public TrendingPanelsWidget(int x, int y, int width, TextRenderer tr, Text leftTitle, List<HomeItem> left,
			Runnable leftMore, Text rightTitle, List<HomeItem> right, Runnable rightMore) {
		super(x, y, width, heightFor(), tr, leftTitle);
		this.leftTitle = leftTitle;
		this.left = left;
		this.leftMore = leftMore;
		this.rightTitle = rightTitle;
		this.right = right;
		this.rightMore = rightMore;
	}

	public static int heightFor() {
		return 22 + VitrineTheme.TYPE_TITLE + 14 + ROWS * (ITEM_H + 10) + 6;
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		int gap = 12;
		int pw = (getWidth() - gap) / 2;
		panel(context, getX(), pw, leftTitle, left, leftMore);
		panel(context, getX() + pw + gap, getWidth() - pw - gap, rightTitle, right, rightMore);
	}

	private void panel(DrawContext context, int x, int w, Text title, List<HomeItem> items, Runnable more) {
		int y = getY();
		int h = getHeight();
		RoundedRect.fill(context, x, y, w, h, HomeWidget.RADIUS, PANEL_COLOR);
		int pad = 18;
		heading(context, title, x + pad, y + 18, more);
		int lineY = y + 18 + VitrineTheme.TYPE_TITLE + 10;
		context.fill(x + pad, lineY, x + w - pad, lineY + 1, 0x22FFFFFF);
		if (items.isEmpty()) {
			ScaledText.draw(context, textRenderer, Text.translatable("vitrine.home.loading"), x + pad, lineY + 16,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			return;
		}
		int colW = (w - 2 * pad - 12) / 2;
		for (int i = 0; i < Math.min(items.size(), ROWS * 2); i++) {
			int col = i % 2;
			int row = i / 2;
			int ix = x + pad + col * (colW + 12);
			int iy = lineY + 12 + row * (ITEM_H + 10);
			item(context, items.get(i), ix, iy, colW);
		}
	}

	private void item(DrawContext context, HomeItem it, int x, int y, int w) {
		boolean over = hovered(x - 4, y - 4, w + 8, ITEM_H + 8);
		if (over) {
			RoundedRect.fill(context, x - 4, y - 4, w + 8, ITEM_H + 8, 6, PANEL_HOVER);
		}
		int under = over ? PANEL_HOVER : PANEL_COLOR;
		int s = ITEM_H;
		region(x - 4, y - 4, w + 8, ITEM_H + 8, it.onOpen());
		image(context, it.imageUrl(), it.fallback(), x, y, s, s, darken(it.color(), 0.55f), under, 6, false);
		badge(context, it.badge(), x, y, s);
		int tx = x + s + 12;
		int tw = w - s - 12;
		ScaledText.draw(context, textRenderer, Text.literal(fit(it.title(), tw, VitrineTheme.TYPE_BODY)), tx, y + 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
		if (it.subtitle() != null) {
			ScaledText.draw(context, textRenderer, Text.literal(fit(it.subtitle(), tw, VitrineTheme.TYPE_CAPTION)), tx,
					y + VitrineTheme.TYPE_BODY + 6, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		}
		pill(context, it.pill().get(), x + w, y + ITEM_H - VitrineTheme.TYPE_CAPTION - 10, it.pillIsAction().get(),
				it.onGet());
	}
}
