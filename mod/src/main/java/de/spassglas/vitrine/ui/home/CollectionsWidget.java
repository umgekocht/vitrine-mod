package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** "Collections" wie im Microsoft Store: grosse, farbige Karten je Inhaltsart. */
public final class CollectionsWidget extends HomeWidget {

	/** Eine Sammlung: Name, Unterzeile, zwei Verlaufsfarben, Symbol, Klick. */
	public record Collection(Text title, Text subtitle, int top, int bottom, Identifier icon, Runnable onOpen) {
	}

	private static final int PER_ROW = 3;
	private final List<Collection> collections;

	public CollectionsWidget(int x, int y, int width, TextRenderer tr, List<Collection> collections) {
		super(x, y, width, heightFor(width, collections.size()), tr, Text.translatable("vitrine.home.collections"));
		this.collections = collections;
	}

	private static int cardH(int width) {
		int cardW = (width - (PER_ROW - 1) * 12) / PER_ROW;
		return cardW * 52 / 100;
	}

	public static int heightFor(int width, int count) {
		int rows = (count + PER_ROW - 1) / PER_ROW;
		return VitrineTheme.TYPE_TITLE + 14 + rows * (cardH(width) + 12);
	}

	@Override
	protected void draw(DrawContext context, float delta) {
		heading(context, Text.translatable("vitrine.home.collections"), getX(), getY(), null);
		int gap = 12;
		int cardW = (getWidth() - (PER_ROW - 1) * gap) / PER_ROW;
		int cardH = cardH(getWidth());
		int top = getY() + VitrineTheme.TYPE_TITLE + 14;
		int bg = ActiveThemeColors.bg();
		for (int i = 0; i < collections.size(); i++) {
			Collection c = collections.get(i);
			int x = getX() + (i % PER_ROW) * (cardW + gap);
			int y = top + (i / PER_ROW) * (cardH + gap);
			boolean over = hovered(x, y, cardW, cardH);
			// Kartenbild (assets v4, 15:8) als Hintergrund, beim Hovern leicht aufgehellt
			context.fillGradient(x, y, x + cardW, y + cardH, c.top(), c.bottom());
			GuiBlend.on();
			float b = over ? 1f : 0.88f;
			context.setShaderColor(b, b, b, 1f);
			int imgH = cardW * 640 / 1200;
			int iy = y + (cardH - imgH) / 2;
			context.enableScissor(x, y, x + cardW, y + cardH);
			context.drawTexture(c.icon(), x, iy, 0, 0, cardW, imgH, cardW, imgH);
			context.disableScissor();
			context.setShaderColor(1f, 1f, 1f, 1f);
			shadeLeft(context, x, y, cardW * 2 / 3, cardH, 150);
			RoundedRect.maskCorners(context, x, y, cardW, cardH, RADIUS, bg);
			int size = VitrineTheme.TYPE_TITLE + 2;
			ScaledText.draw(context, textRenderer, ScaledText.bold(c.title()), x + 18,
					y + cardH - size - VitrineTheme.TYPE_CAPTION - 22, 0xFFFFFFFF, size, false);
			ScaledText.draw(context, textRenderer, c.subtitle(), x + 18, y + cardH - VitrineTheme.TYPE_CAPTION - 14,
					0xFFE0E4EC, VitrineTheme.TYPE_CAPTION);
			region(x, y, cardW, cardH, c.onOpen());
		}
	}

	private static int brighten(int argb) {
		int r = Math.min(255, ((argb >> 16) & 0xff) + 18);
		int g = Math.min(255, ((argb >> 8) & 0xff) + 18);
		int b = Math.min(255, (argb & 0xff) + 18);
		return (argb & 0xff000000) | r << 16 | g << 8 | b;
	}
}
