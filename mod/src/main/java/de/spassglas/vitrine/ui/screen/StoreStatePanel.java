package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Zeichnet einen {@link StoreStateView} (ROADMAP S7): Symbol, Ueberschrift,
 * Erklaersatz -- mittig im Inhaltsbereich. Der <em>eine</em> Knopf wird vom
 * aufrufenden Screen als {@code ButtonWidget} an {@link #contentAdvance()}
 * unter {@code topY} gesetzt (die Zustands-Komponente liefert das Label ueber
 * ihre {@code action().labelKey()}); der Lade-Skeleton hat keinen Knopf und
 * keinen Spinner, hier erscheinen stattdessen {@link StoreStateView#skeletonTileCount()}
 * Platzhalter-Kacheln.
 *
 * <p>Rein zeichnend, ohne Zustand -- die eine Wahrheit ueber Symbol,
 * Ueberschrift, Satz und Knopf ist {@link StoreStateView}.</p>
 */
final class StoreStatePanel {

	private static final Identifier EMPTY_SYMBOL = Identifier.of("vitrine", "textures/gui/empty_state.png");
	private static final Identifier WARNING_SYMBOL = Identifier.of("vitrine", "textures/gui/icons/ui_warning.png");
	private static final Identifier PLACEHOLDER_CARD = Identifier.of("vitrine", "textures/gui/placeholder_card.png");

	private StoreStatePanel() {
	}

	/**
	 * Vertikaler Abstand von {@code topY} bis zur Oberkante des einen Knopfs --
	 * Symbol + Ueberschrift + Erklaersatz, je mit {@link VitrineTheme#STATE_CONTENT_GAP}.
	 * Build- und Zeichenpfad nutzen denselben Wert, damit der Knopf sitzt.
	 */
	static int contentAdvance() {
		return VitrineTheme.STATE_SYMBOL_SIZE + VitrineTheme.STATE_CONTENT_GAP
				+ VitrineTheme.TYPE_TITLE + VitrineTheme.STATE_CONTENT_GAP
				+ VitrineTheme.TYPE_BODY + VitrineTheme.STATE_CONTENT_GAP;
	}

	/**
	 * Vertikaler Abstand von {@code topY} bis unter das Lade-Skeleton (Platzhalter-
	 * Kacheln + Ueberschrift + Erklaersatz, siehe {@link #renderSkeleton}) -- fuer
	 * Aufrufer, die unter dem Skeleton weiterbauen muessen (ROADMAP GATE84:
	 * die lokale Bibliothek bleibt waehrend des Katalog-Ladens sichtbar, Charter-
	 * Regel 12).
	 */
	static int skeletonContentAdvance() {
		int cardH = VitrineTheme.CARD_WIDTH * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH;
		return cardH + VitrineTheme.STATE_CONTENT_GAP
				+ VitrineTheme.TYPE_SUBTITLE + VitrineTheme.STATE_CONTENT_GAP
				+ VitrineTheme.TYPE_CAPTION + VitrineTheme.STATE_CONTENT_GAP;
	}

	/** Zeichnet Symbol/Ueberschrift/Erklaersatz (bzw. beim Skeleton die Platzhalter-Kacheln). */
	static void render(DrawContext context, TextRenderer textRenderer, StoreStateView view,
			int centerX, int topY, int areaWidth) {
		if (view.isSkeleton()) {
			renderSkeleton(context, textRenderer, view, centerX, topY, areaWidth);
			return;
		}

		int y = topY;
		int size = VitrineTheme.STATE_SYMBOL_SIZE;
		int symbolX = centerX - size / 2;
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		switch (view.symbol()) {
			case EMPTY, NO_RESULTS -> context.drawTexture(EMPTY_SYMBOL, symbolX, y, size, size, 0.0F, 0.0F,
					VitrineTheme.STATE_EMPTY_SOURCE_SIZE, VitrineTheme.STATE_EMPTY_SOURCE_SIZE,
					VitrineTheme.STATE_EMPTY_SOURCE_SIZE, VitrineTheme.STATE_EMPTY_SOURCE_SIZE);
			case WARNING -> context.drawTexture(WARNING_SYMBOL, symbolX, y, size, size, 0.0F, 0.0F,
					VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE,
					VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE);
			case SKELETON -> {
				// unerreichbar: isSkeleton() oben behandelt
			}
		}
		y += size + VitrineTheme.STATE_CONTENT_GAP;

		Text heading = heading(view);
		drawCentered(context, textRenderer, heading, centerX, y, ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
		y += VitrineTheme.TYPE_TITLE + VitrineTheme.STATE_CONTENT_GAP;

		Text explanation = Text.translatable(view.explanationKey());
		drawCentered(context, textRenderer, explanation, centerX, y, ActiveThemeColors.textMuted(),
				VitrineTheme.TYPE_BODY);
	}

	private static void renderSkeleton(DrawContext context, TextRenderer textRenderer, StoreStateView view,
			int centerX, int topY, int areaWidth) {
		int cardW = VitrineTheme.CARD_WIDTH;
		int cardH = cardW * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH;
		int perRow = Math.max(1, (areaWidth + VitrineTheme.SPACE_GUTTER) / (cardW + VitrineTheme.SPACE_GUTTER));
		int count = Math.max(1, Math.min(view.skeletonTileCount(), perRow));
		int totalW = count * cardW + (count - 1) * VitrineTheme.SPACE_GUTTER;
		int x = centerX - totalW / 2;
		for (int i = 0; i < count; i++) {
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(PLACEHOLDER_CARD, x, topY, cardW, cardH, 0.0F, 0.0F,
					VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT,
					VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT);
			context.drawBorder(x, topY, cardW, cardH, ActiveThemeColors.borderTranslucent());
			x += cardW + VitrineTheme.SPACE_GUTTER;
		}
		int y = topY + cardH + VitrineTheme.STATE_CONTENT_GAP;
		drawCentered(context, textRenderer, heading(view), centerX, y, ActiveThemeColors.textMuted(),
				VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.STATE_CONTENT_GAP;
		drawCentered(context, textRenderer, Text.translatable(view.explanationKey()), centerX, y,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
	}

	/** Ueberschrift als {@code Text}, mit den geordneten {@link StoreStateView#headingArgs()} als Argumente. */
	static Text heading(StoreStateView view) {
		List<String> args = view.headingArgs();
		if (args.isEmpty()) {
			return Text.translatable(view.headingKey());
		}
		Object[] resolved = args.stream().map(Text::literal).toArray();
		return Text.translatable(view.headingKey(), resolved);
	}

	private static void drawCentered(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y,
			int color, int pixelHeight) {
		int width = ScaledText.width(textRenderer, text.getString(), pixelHeight);
		ScaledText.draw(context, textRenderer, text, centerX - width / 2, y, color, pixelHeight);
	}
}
