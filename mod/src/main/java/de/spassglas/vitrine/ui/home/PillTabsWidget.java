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
import java.util.function.Function;

/** Reiter als runde Pillen (wie die Filter der Microsoft-Store-Suche). */
public final class PillTabsWidget extends HomeWidget {

	public static final int HEIGHT = 24;

	private final List<String> ids;
	private final Function<String, Text> label;
	private final String active;
	private final Consumer<String> onSelect;

	public PillTabsWidget(int x, int y, int width, TextRenderer tr, List<String> ids, Function<String, Text> label,
			String active, Consumer<String> onSelect) {
		super(x, y, width, HEIGHT, tr, Text.translatable("vitrine.nav.mods"));
		this.ids = ids;
		this.label = label;
		this.active = active;
		this.onSelect = onSelect;
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		int px = getX();
		int py = getY();
		for (String id : ids) {
			Text t = label.apply(id);
			int pw = ScaledText.width(textRenderer, t.getString(), VitrineTheme.TYPE_BODY) + 32;
			boolean on = id.equals(active);
			boolean over = hovered(px, py, pw, HEIGHT);
			RoundedRect.fill(context, px, py, pw, HEIGHT, HEIGHT / 2, on ? 0xFF2B3A55 : over ? 0xFF33363E : 0xFF2A2C33);
			RoundedRect.border(context, px, py, pw, HEIGHT, HEIGHT / 2, on ? ActiveThemeColors.accent() : 0x33FFFFFF);
			ScaledText.draw(context, textRenderer, t, px + 16, py + (HEIGHT - VitrineTheme.TYPE_BODY) / 2 + 1,
					on ? ActiveThemeColors.accent() : ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			region(px, py, pw, HEIGHT, () -> onSelect.accept(id));
			px += pw + 8;
		}
	}
}
