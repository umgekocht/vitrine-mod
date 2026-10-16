package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Zeichnet Text in einer der fuenf Typo-Stufen aus {@code docs/UI-SPEC.md}
 * ("Typo-Stufen": {@link VitrineTheme#TYPE_TITLE_LARGE},
 * {@link VitrineTheme#TYPE_TITLE}, {@link VitrineTheme#TYPE_SUBTITLE},
 * {@link VitrineTheme#TYPE_BODY}, {@link VitrineTheme#TYPE_CAPTION}), obwohl
 * Minecrafts eingebauter {@link TextRenderer} nur eine feste Schriftgroesse
 * ({@link TextRenderer#fontHeight}, ueblicherweise 9 px) kennt.
 *
 * <p>Der Massstab ergibt sich zur Laufzeit aus dem Verhaeltnis der
 * gewuenschten Zielhoehe zur tatsaechlichen {@code fontHeight} des
 * aktuellen Font-Renderers -- keine hartkodierte Schriftgroesse, sondern
 * eine reine Ableitung aus einem Theme-Token und einer Laufzeit-Eigenschaft
 * der Schriftart.</p>
 */
public final class ScaledText {

	private ScaledText() {
	}

	/**
	 * Vitrines glatte UI-Schrift (Inter, assets/vitrine/font/ui.json). Die
	 * Minecraft-Pixelschrift wirkt vergroessert grob; umgekocht wollte "nicht alles
	 * so verpixelt" (2026-10-06). Fehlende Zeichen fallen ueber den
	 * reference-Provider auf die Minecraft-Schrift zurueck.
	 */
	public static final Identifier UI_FONT = Identifier.of("vitrine", "ui");
	/**
	 * Inter SemiBold (aus der variablen Inter erzeugt, wght 600). HIG
	 * typography.md: Hierarchie ueber Schriftstaerke statt nur ueber Groesse.
	 */
	public static final Identifier UI_BOLD = Identifier.of("vitrine", "ui_bold");

	/** Zeilenabstand mehrzeiliger Texte (HIG: bei drei und mehr Zeilen keine enge Laufweite). */
	public static final float LEADING = 1.3f;

	/** {@code text} in Inter SemiBold -- fuer Ueberschriften und Titel. */
	public static Text bold(Text text) {
		return text.copy().setStyle(text.getStyle().withFont(UI_BOLD));
	}

	/** {@code text} in der Vitrine-UI-Schrift (Teile mit eigener Schrift bleiben unberuehrt). */
	public static Text styled(Text text) {
		// eine bewusst gesetzte Schrift (z. B. UI_BOLD) bleibt erhalten
		if (!Style.DEFAULT_FONT_ID.equals(text.getStyle().getFont())) {
			return text.copy();
		}
		return text.copy().fillStyle(Style.EMPTY.withFont(UI_FONT));
	}

	/** Wie {@link #styled(Text)} fuer einfachen Text. */
	public static Text styled(String text) {
		return styled(Text.literal(text));
	}

	/** Wie {@link #draw(DrawContext, TextRenderer, Text, int, int, int, int, boolean)}, ohne Schlagschatten. */
	public static void draw(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color,
			int targetPixelHeight) {
		draw(context, textRenderer, text, x, y, color, targetPixelHeight, false);
	}

	public static void draw(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color,
			int targetPixelHeight, boolean shadow) {
		float scale = scaleFactor(textRenderer, targetPixelHeight);
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x, y, 0);
		matrices.scale(scale, scale, 1f);
		Text ui = styled(text);
		if (shadow) {
			context.drawTextWithShadow(textRenderer, ui, 0, 0, color);
		} else {
			context.drawText(textRenderer, ui, 0, 0, color, false);
		}
		matrices.pop();
	}

	/**
	 * Wie {@link #draw(DrawContext, TextRenderer, Text, int, int, int, int)}, bricht aber an
	 * {@code maxWidth} (in Bildschirm-Einheiten, nach dem Skalieren) um, statt ueber den
	 * Rand hinauszulaufen.
	 *
	 * @return die verbrauchte Hoehe (Zeilenzahl mal {@code targetPixelHeight})
	 */
	public static int drawWrapped(DrawContext context, TextRenderer textRenderer, Text text, int x, int y,
			int maxWidth, int color, int targetPixelHeight) {
		float scale = scaleFactor(textRenderer, targetPixelHeight);
		int unscaledWidth = Math.max(1, (int) (maxWidth / scale));
		java.util.List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(styled(text), unscaledWidth);
		MatrixStack matrices = context.getMatrices();
		for (int i = 0; i < lines.size(); i++) {
			matrices.push();
			matrices.translate(x, y + Math.round(i * targetPixelHeight * LEADING), 0);
			matrices.scale(scale, scale, 1f);
			context.drawText(textRenderer, lines.get(i), 0, 0, color, false);
			matrices.pop();
		}
		return lines.isEmpty() ? 0 : Math.round((lines.size() - 1) * targetPixelHeight * LEADING) + targetPixelHeight;
	}

	/** Hoehe, die {@link #drawWrapped} fuer {@code text} verbrauchen wuerde. */
	public static int wrappedHeight(TextRenderer textRenderer, Text text, int maxWidth, int targetPixelHeight) {
		float scale = scaleFactor(textRenderer, targetPixelHeight);
		int unscaledWidth = Math.max(1, (int) (maxWidth / scale));
		int n = textRenderer.wrapLines(styled(text), unscaledWidth).size();
		return n == 0 ? 0 : Math.round((n - 1) * targetPixelHeight * LEADING) + targetPixelHeight;
	}

	/** Faktor, um den {@code targetPixelHeight} gegenueber der echten Zeilenhoehe der Schriftart skaliert. */
	public static float scaleFactor(TextRenderer textRenderer, int targetPixelHeight) {
		int fontHeight = textRenderer.fontHeight;
		if (fontHeight <= 0) {
			return 1f;
		}
		return targetPixelHeight / (float) fontHeight;
	}

	/** Breite von {@code text} bei {@code targetPixelHeight}, umgerechnet aus der unskalierten Breitenmessung. */
	public static int width(TextRenderer textRenderer, String text, int targetPixelHeight) {
		return Math.round(textRenderer.getWidth(styled(text)) * scaleFactor(textRenderer, targetPixelHeight));
	}
}
