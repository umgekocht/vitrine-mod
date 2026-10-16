package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.FocusRing;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * ROADMAP GATE82: die Screenshot-Galerie der Detailseite (docs/UI-SPEC.md
 * "5. Detailseite": "Screenshots als horizontale Galerie, Klick öffnet
 * groß."). Zeichnet Überschrift und Vorschaubilder selbst (Muster wie
 * {@code TileRowWidget}/{@code HeroCarouselWidget}) und meldet eine
 * Aktivierung -- Klick auf ein Bild oder Enter/Leertaste bei fokussierter
 * Galerie -- über den im Konstruktor übergebenen Rückruf. Was "groß öffnen"
 * bedeutet, entscheidet allein {@code PackageDetailScreen} (eine
 * Großansicht); dieses Widget kennt nur Position und Klick.
 *
 * <p>Vorher gab es hier gar kein {@link ClickableWidget}, nur gezeichnete
 * Kacheln ohne jeden Eingabe-Handler -- ein Klick tat nichts (GATE82).</p>
 */
public final class ScreenshotGalleryWidget extends ClickableWidget {

	private static final Identifier PLACEHOLDER_CARD =
			Identifier.of("vitrine", "textures/gui/placeholder_card.png");

	private final List<String> screenshotPaths;
	private final Path sourceFile;
	private final TextRenderer textRenderer;
	private final IntConsumer onActivated;

	/** Per Pfeiltaste fokussierte Kachel, {@code -1} ohne Tastaturfokus auf der Galerie. */
	private int focusedIndex = -1;

	public ScreenshotGalleryWidget(int x, int y, List<String> screenshotPaths, Path sourceFile,
			TextRenderer textRenderer, IntConsumer onActivated) {
		super(x, y, width(screenshotPaths.size()), preferredHeight(),
				Text.translatable("vitrine.detail.section.screenshots"));
		this.screenshotPaths = List.copyOf(screenshotPaths);
		this.sourceFile = sourceFile;
		this.textRenderer = textRenderer;
		this.onActivated = Objects.requireNonNull(onActivated, "onActivated");
	}

	private static int width(int count) {
		if (count <= 0) {
			return VitrineTheme.DETAIL_SCREENSHOT_WIDTH;
		}
		return count * VitrineTheme.DETAIL_SCREENSHOT_WIDTH + (count - 1) * VitrineTheme.SPACE_GUTTER;
	}

	/** Gesamthöhe: Überschrift + eine Reihe Kacheln, wie {@code PackageDetailScreen#advanceHeading}. */
	public static int preferredHeight() {
		return VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP + VitrineTheme.DETAIL_SCREENSHOT_HEIGHT;
	}

	private int galleryTop() {
		return getY() + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		ScaledText.draw(context, textRenderer, Text.translatable("vitrine.detail.section.screenshots"),
				getX(), getY(), ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);

		int top = galleryTop();
		int shotX = getX();
		for (int i = 0; i < screenshotPaths.size(); i++) {
			// GATE17: echte Screenshots aus dem Paket, Platzhalter nur, wenn keiner ladbar ist.
			String shotPath = screenshotPaths.get(i);
			Optional<PackageImageTextures.Texture> shot = sourceFile == null
					? Optional.empty()
					: PackageImageTextures.get(sourceFile, shotPath);
			if (shot.isPresent()) {
				PackageImageTextures.drawFitted(context, shot.get(), shotX, top,
						VitrineTheme.DETAIL_SCREENSHOT_WIDTH, VitrineTheme.DETAIL_SCREENSHOT_HEIGHT);
			} else {
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(PLACEHOLDER_CARD, shotX, top, VitrineTheme.DETAIL_SCREENSHOT_WIDTH,
						VitrineTheme.DETAIL_SCREENSHOT_HEIGHT, 0.0F, 0.0F,
						VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT,
						VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT);
			}
			RoundedRect.maskCorners(context, shotX, top, VitrineTheme.DETAIL_SCREENSHOT_WIDTH,
					VitrineTheme.DETAIL_SCREENSHOT_HEIGHT, VitrineTheme.RADIUS_CARD, ActiveThemeColors.bg());
			RoundedRect.border(context, shotX, top, VitrineTheme.DETAIL_SCREENSHOT_WIDTH,
					VitrineTheme.DETAIL_SCREENSHOT_HEIGHT, VitrineTheme.RADIUS_CARD, ActiveThemeColors.borderTranslucent());
			// ROADMAP S9: sichtbarer Fokusrahmen um die per Pfeiltaste gewaehlte Kachel.
			if (isFocused() && i == focusedIndex) {
				FocusRing.draw(context, shotX, top, VitrineTheme.DETAIL_SCREENSHOT_WIDTH,
						VitrineTheme.DETAIL_SCREENSHOT_HEIGHT);
			}
			shotX += VitrineTheme.DETAIL_SCREENSHOT_WIDTH + VitrineTheme.SPACE_GUTTER;
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		int index = indexAt(mouseX, mouseY);
		if (index >= 0) {
			onActivated.accept(index);
		}
	}

	/** Der Vorschaubild-Index unter {@code (mouseX, mouseY)}, oder {@code -1} ausserhalb jedes Bildes. */
	private int indexAt(double mouseX, double mouseY) {
		int top = galleryTop();
		if (mouseY < top || mouseY > top + VitrineTheme.DETAIL_SCREENSHOT_HEIGHT) {
			return -1;
		}
		int step = VitrineTheme.DETAIL_SCREENSHOT_WIDTH + VitrineTheme.SPACE_GUTTER;
		int firstX = getX();
		for (int i = 0; i < screenshotPaths.size(); i++) {
			int shotX = firstX + i * step;
			if (mouseX >= shotX && mouseX <= shotX + VitrineTheme.DETAIL_SCREENSHOT_WIDTH) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * ROADMAP S9: bekommt die Galerie den Tastaturfokus, wandert der Fokus auf
	 * das erste Vorschaubild; verliert sie ihn, wird die Auswahl zurueckgesetzt
	 * (Muster wie {@code TileRowWidget#setFocused}).
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (focused) {
			if (focusedIndex < 0 && !screenshotPaths.isEmpty()) {
				focusedIndex = 0;
			}
		} else {
			focusedIndex = -1;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			return moveFocus(-1);
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			return moveFocus(1);
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
			int target = focusedIndex >= 0 ? focusedIndex : (screenshotPaths.isEmpty() ? -1 : 0);
			if (target >= 0) {
				onActivated.accept(target);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private boolean moveFocus(int direction) {
		if (screenshotPaths.isEmpty()) {
			return true;
		}
		int from = focusedIndex < 0 ? 0 : focusedIndex;
		focusedIndex = Math.max(0, Math.min(screenshotPaths.size() - 1, from + direction));
		return true;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}

	/** Nur fuer Tests: die per Pfeiltaste fokussierte Kachel, oder {@code -1} ohne Fokus (ROADMAP GATE82). */
	int focusedIndexForTest() {
		return focusedIndex;
	}
}
