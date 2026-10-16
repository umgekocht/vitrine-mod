package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ergebnisseite der Suche wie im Microsoft Store: Ueberschrift mit dem
 * Suchbegriff, Filter-Pillen (Abteilungen) und Karten in vier Spalten mit
 * Logo, Name, Art, Status-Pille, Beschreibung und grossem Vorschaubild.
 */
public final class SearchResultsWidget extends HomeWidget {

	/** Ein Treffer der Ergebnisseite. */
	public record Result(HomeItem item, String category, String description, String previewUrl) {
	}

	/** Eine Filter-Pille. */
	public record Filter(String id, Text label) {
	}

	private static final int COLS = 4;
	private static final net.minecraft.util.Identifier EMPTY =
			net.minecraft.util.Identifier.of("vitrine", "textures/gui/empty_state.png");
	private static final int GAP = 12;

	private final Text heading;
	private final List<Filter> filters;
	private final String activeFilter;
	private final Consumer<String> onFilter;
	private final List<Result> results;
	private final boolean loading;

	public SearchResultsWidget(int x, int y, int width, TextRenderer tr, Text heading, List<Filter> filters,
			String activeFilter, Consumer<String> onFilter, List<Result> results, boolean loading) {
		super(x, y, width, heightFor(width, results.size()), tr, heading);
		this.heading = heading;
		this.filters = filters;
		this.activeFilter = activeFilter;
		this.onFilter = onFilter;
		this.results = results;
		this.loading = loading;
	}

	private static int cardW(int width) {
		return (width - (COLS - 1) * GAP) / COLS;
	}

	private static int cardH(int width) {
		return 92 + cardW(width) * 56 / 100;
	}

	public static int heightFor(int width, int count) {
		int rows = Math.max(1, (count + COLS - 1) / COLS);
		return VitrineTheme.TYPE_TITLE + 12 + 30 + 20 + rows * (cardH(width) + GAP);
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		int x = getX();
		int y = getY();
		ScaledText.draw(context, textRenderer, ScaledText.bold(heading), x, y, ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE + 6);
		int py = y + VitrineTheme.TYPE_TITLE + 22;
		int px = x;
		for (Filter f : filters) {
			int tw = ScaledText.width(textRenderer, f.label().getString(), VitrineTheme.TYPE_BODY);
			int pw = tw + 32;
			int ph = 22;
			boolean on = f.id().equals(activeFilter);
			boolean over = hovered(px, py, pw, ph);
			RoundedRect.fill(context, px, py, pw, ph, 11, on ? 0xFF2B3A55 : over ? 0xFF33363E : 0xFF2A2C33);
			RoundedRect.border(context, px, py, pw, ph, 11, on ? ActiveThemeColors.accent() : 0x33FFFFFF);
			ScaledText.draw(context, textRenderer, f.label(), px + 16, py + (ph - VitrineTheme.TYPE_BODY) / 2 + 1,
					on ? ActiveThemeColors.accent() : ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			String id = f.id();
			region(px, py, pw, ph, () -> onFilter.accept(id));
			px += pw + 8;
		}
		int top = py + 22 + 20;
		if (results.isEmpty()) {
			if (!loading) {
				// Leer-Zustand mit Bild (assets v4), mittig
				int s = 96;
				int cx = x + getWidth() / 2;
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(EMPTY, cx - s / 2, top + 10, 0, 0, s, s, s, s);
				Text none = Text.translatable("vitrine.search.none");
				int tw = ScaledText.width(textRenderer, none.getString(), VitrineTheme.TYPE_BODY);
				ScaledText.draw(context, textRenderer, none, cx - tw / 2, top + s + 22, ActiveThemeColors.textMuted(),
						VitrineTheme.TYPE_BODY);
				return;
			}
			ScaledText.draw(context, textRenderer, Text.translatable("vitrine.home.loading"),
					x, top, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			return;
		}
		int cw = cardW(getWidth());
		int ch = cardH(getWidth());
		for (int i = 0; i < results.size(); i++) {
			card(context, results.get(i), x + (i % COLS) * (cw + GAP), top + (i / COLS) * (ch + GAP), cw, ch);
		}
	}

	private void card(DrawContext context, Result r, int x, int y, int w, int h) {
		HomeItem it = r.item();
		boolean over = hovered(x, y, w, h);
		int surface = over ? PANEL_HOVER : PANEL_COLOR;
		RoundedRect.fill(context, x, y, w, h, RADIUS, surface);
		region(x, y, w, h, it.onOpen());
		int pad = 12;
		int icon = 30;
		image(context, it.imageUrl(), it.fallback(), x + pad, y + pad, icon, icon, darken(it.color(), 0.5f), surface, 6,
				false);
		badge(context, it.badge(), x + pad, y + pad, icon);
		String pill = it.pill().get();
		int pillW = pill(context, pill, x + w - pad, y + pad, it.pillIsAction().get(), it.onGet());
		int tx = x + pad + icon + 10;
		int tw = w - pad * 2 - icon - 10 - pillW - 6;
		ScaledText.draw(context, textRenderer, Text.literal(fit(it.title(), tw, VitrineTheme.TYPE_BODY)), tx, y + pad,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
		ScaledText.draw(context, textRenderer, Text.literal(fit(r.category(), tw, VitrineTheme.TYPE_CAPTION)), tx,
				y + pad + VitrineTheme.TYPE_BODY + 4, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		int dy = y + pad + icon + 8;
		ScaledText.drawWrapped(context, textRenderer, Text.literal(fit(r.description(), (w - 2 * pad) * 3 / 2,
				VitrineTheme.TYPE_CAPTION)), x + pad, dy, w - 2 * pad, ActiveThemeColors.text(), VitrineTheme.TYPE_CAPTION);
		int imgY = y + 92;
		int imgH = h - 92 - pad;
		String preview = r.previewUrl() != null ? r.previewUrl() : it.imageUrl();
		image(context, preview, it.fallback(), x + pad, imgY, w - 2 * pad, imgH, darken(it.color(), 0.45f), surface, 8,
				r.previewUrl() != null);
	}
}
