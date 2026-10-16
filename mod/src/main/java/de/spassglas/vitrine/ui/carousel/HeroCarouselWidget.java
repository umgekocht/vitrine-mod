package de.spassglas.vitrine.ui.carousel;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileLabel;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Das Hero-Karussell der Start-Seite (ROADMAP S8; docs/UI-SPEC.md "3. Start":
 * "21:9 ... Punkte-Anzeige unten rechts, Pfeile links/rechts beim Ueberfahren,
 * automatischer Wechsel alle 7 Sekunden -- pausiert, sobald die Maus darauf
 * liegt oder der Nutzer selbst blaettert").
 *
 * <p>Die gesamte Zeit-, Pause- und Index-Logik liegt im client-freien
 * {@link HeroCarouselModel} (dort getestet, u. a. das S8-Fertig-Kriterium
 * "bleibt stehen, solange die Maus darauf liegt"); die Geometrie der Punkte und
 * Pfeil-Trefferflaechen im ebenfalls client-freien {@link HeroCarouselLayout}.
 * Dieses Widget bindet nur Minecrafts Zeichnen und Eingabe daran.</p>
 *
 * <p>ROADMAP GATE5/GATE15: sobald der echte Katalog etwas fuer "Featured"
 * liefert ({@code catalogController.featuredTiles()}), zeigt das Karussell
 * diese echten Kacheln -- {@code isPlaceholder} ist dann {@code false} und der
 * Hinweistext {@code vitrine.carousel.sample_note} entfaellt (er waere sonst
 * eine falsche Behauptung ueber echte Katalogdaten, Charter-Regel 2). Ist der
 * Katalog offline/kaputt/noch nicht geladen, faellt {@code StoreScreen} auf
 * die ehrlich als Beispielauswahl beschriftete Demo-Reihe zurueck --
 * {@code isPlaceholder} ist dann {@code true} und der Hinweistext erscheint.
 * Jede Zahl und jede Farbe stammt aus {@link VitrineTheme}; Titel/Autor sind
 * Paket-Freitexte (siehe {@link TileData}).</p>
 *
 * <p>Ein Klick auf die Kachel selbst (nicht Punkt/Pfeil) ruft
 * {@link #setOnSlideActivated(Consumer)} mit der aktuell gezeigten Kachel auf
 * -- {@code StoreScreen} verdrahtet das nur fuer echte Katalog-Kacheln auf die
 * Katalog-Detailseite (Muster wie {@code TileRowWidget#setOnTileActivated}).</p>
 */
public final class HeroCarouselWidget extends ClickableWidget {

	private static final Identifier BACKGROUND =
			Identifier.of("vitrine", "textures/gui/placeholder_card.png");
	private static final Identifier ARROW_LEFT =
			Identifier.of("vitrine", "textures/gui/icons/ui_chevron_left.png");
	private static final Identifier ARROW_RIGHT =
			Identifier.of("vitrine", "textures/gui/icons/ui_chevron_right.png");

	private final List<TileData> slides;
	private final TextRenderer textRenderer;
	private final HeroCarouselModel model;
	private final boolean reduceMotion;
	private final boolean isPlaceholder;

	private int shownIndex;
	private int previousIndex;
	private long transitionStartMs;
	private boolean pointerOver;
	private Consumer<TileData> onSlideActivated;

	/**
	 * @param isPlaceholder ROADMAP GATE15: {@code true}, wenn {@code slides}
	 *                      die ehrlich beschriftete Demo-Auswahl ist (dann
	 *                      erscheint {@code vitrine.carousel.sample_note});
	 *                      {@code false} fuer echte Katalog-Kacheln.
	 */
	public HeroCarouselWidget(int x, int y, int width, List<TileData> slides, TextRenderer textRenderer,
			HeroCarouselModel model, boolean reduceMotion, boolean isPlaceholder) {
		super(x, y, width, heightFor(width), Text.translatable("vitrine.nav.start"));
		if (slides.isEmpty()) {
			throw new IllegalArgumentException("Hero-Karussell braucht mindestens einen Eintrag");
		}
		this.slides = List.copyOf(slides);
		this.textRenderer = textRenderer;
		this.model = model;
		this.reduceMotion = reduceMotion;
		this.isPlaceholder = isPlaceholder;
		this.shownIndex = model.index();
		this.previousIndex = model.index();
	}

	/**
	 * ROADMAP GATE15: Rueckruf fuer einen Klick/Enter/Leertaste auf die
	 * aktuell gezeigte Kachel (nicht Punkt/Pfeil) -- liefert die dazugehoerige
	 * {@link TileData}. Ohne Rueckruf (Demo-Platzhalter, deren Ids in keiner
	 * Registry existieren) tut ein solcher Klick nichts (Charter-Regel 2).
	 */
	public void setOnSlideActivated(Consumer<TileData> onSlideActivated) {
		this.onSlideActivated = onSlideActivated;
	}

	/** Hoehe der 21:9-Flaeche bei gegebener Breite (docs/UI-SPEC.md {@code hero.aspect}). */
	public static int heightFor(int width) {
		return width * VitrineTheme.HERO_ASPECT_HEIGHT / VitrineTheme.HERO_ASPECT_WIDTH;
	}

	private HeroCarouselLayout layout() {
		return new HeroCarouselLayout(getX(), getY(), getWidth(),
				VitrineTheme.HERO_ASPECT_WIDTH, VitrineTheme.HERO_ASPECT_HEIGHT, slides.size(),
				VitrineTheme.CAROUSEL_DOT_SIZE, VitrineTheme.SPACE_UNIT, VitrineTheme.SPACE_UNIT * 2,
				VitrineTheme.CAROUSEL_ARROW_WIDTH);
	}

	/** Nur fuer die Anbindung/Tests: das zugrundeliegende Zeit-/Index-Modell. */
	public HeroCarouselModel model() {
		return model;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		HeroCarouselLayout layout = layout();

		// docs/UI-SPEC.md "3. Start": "pausiert, sobald die Maus darauf liegt".
		pointerOver = layout.contains(mouseX, mouseY);
		model.setPointerOver(pointerOver);
		model.tick();

		int index = model.index();
		if (index != shownIndex) {
			previousIndex = shownIndex;
			shownIndex = index;
			transitionStartMs = Util.getMeasuringTimeMs();
		}

		int width = getWidth();
		int height = layout.height();
		context.enableScissor(getX(), getY(), getX() + width, getY() + height);

		long transitionMs = model.transitionMs(reduceMotion);
		float progress = transitionMs <= 0L ? 1f
				: Math.min(1f, (Util.getMeasuringTimeMs() - transitionStartMs) / (float) transitionMs);
		if (progress < 1f && previousIndex != shownIndex) {
			renderSlide(context, previousIndex, Math.round(-progress * width), width, height);
			renderSlide(context, shownIndex, Math.round((1f - progress) * width), width, height);
		} else {
			renderSlide(context, shownIndex, 0, width, height);
		}

		context.disableScissor();

		renderDots(context, layout);
		if (pointerOver) {
			renderArrows(context, layout);
		}
		context.drawBorder(getX(), getY(), width, height, ActiveThemeColors.borderTranslucent());
	}

	private void renderSlide(DrawContext context, int slideIndex, int dx, int width, int height) {
		int left = getX() + dx;
		int top = getY();
		TileData slide = slides.get(slideIndex);
		// Kategorie-Vorschaubild des Pakets (16:9), auf die breitere Hero-Flaeche
		// mittig zugeschnitten statt verzerrt; vorher fuer jedes Paket dasselbe
		// pixelig hochgezogene Vitrine-"V".
		var remote = slide.hasImage() && slide.imagePath().startsWith("https://")
				? de.spassglas.vitrine.ui.image.CatalogImageCache.get(slide.imagePath())
				: java.util.Optional.<de.spassglas.vitrine.ui.image.PackageImageTextures.Texture>empty();
		if (remote.isPresent()) {
			de.spassglas.vitrine.ui.image.PackageImageTextures.drawCover(context, remote.get(), left, top, width, height);
		} else {
			int texW = 1536;
			int texH = 864;
			int regionH = Math.min(texH, Math.max(1, texW * height / Math.max(1, width)));
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(Identifier.of("vitrine", "textures/gui/placeholder_" + slide.type().id() + ".png"),
					left, top, width, height, 0.0F, (texH - regionH) / 2.0F, texW, regionH, texW, texH);
		}
		// dunkler Verlauf unten, damit der Titel auf jedem Bild lesbar bleibt
		context.fillGradient(left, top + height * 3 / 5, left + width, top + height, 0x00000000, 0xB0000000);
		int textX = left + VitrineTheme.SPACE_PAGE;
		int maxTextWidth = width - VitrineTheme.SPACE_PAGE * 2;

		String title = TileLabel.truncateToWidth(slide.name(), maxTextWidth,
				t -> ScaledText.width(textRenderer, t, VitrineTheme.TYPE_TITLE_LARGE));
		int titleY = top + height - VitrineTheme.SPACE_PAGE
				- VitrineTheme.TYPE_TITLE_LARGE - VitrineTheme.SPACE_UNIT - VitrineTheme.TYPE_CAPTION;
		ScaledText.draw(context, textRenderer, Text.literal(title), textX, titleY,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE_LARGE, true);

		if (isPlaceholder) {
			// Ehrlicher Seam (Charter-Regel 2): nur die Demo-Auswahl ist ein
			// Platzhalter -- echte Katalog-Kacheln (GATE5/GATE15) tragen diesen
			// Hinweis nicht, das waere eine falsche Behauptung.
			ScaledText.draw(context, textRenderer, Text.translatable("vitrine.carousel.sample_note"),
					textX, titleY + VitrineTheme.TYPE_TITLE_LARGE + VitrineTheme.SPACE_UNIT,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION, true);
		}
	}

	private void renderDots(DrawContext context, HeroCarouselLayout layout) {
		List<HeroCarouselLayout.Rect> dots = layout.dots();
		for (int i = 0; i < dots.size(); i++) {
			HeroCarouselLayout.Rect d = dots.get(i);
			// docs/UI-SPEC.md "Barrierefreiheit": "Kein Zustand nur ueber Farbe".
			// Der aktuelle Eintrag ist zusaetzlich zur Akzentfarbe durch die Form
			// unterscheidbar -- gefuellter Punkt statt hohlem Ring.
			if (i == model.index()) {
				context.fill(d.left(), d.top(), d.right(), d.bottom(), ActiveThemeColors.accent());
			} else {
				context.drawBorder(d.left(), d.top(), d.width(), d.height(), ActiveThemeColors.textMuted());
			}
		}
	}

	private void renderArrows(DrawContext context, HeroCarouselLayout layout) {
		drawArrow(context, layout.leftArrow(), ARROW_LEFT);
		drawArrow(context, layout.rightArrow(), ARROW_RIGHT);
	}

	private void drawArrow(DrawContext context, HeroCarouselLayout.Rect zone, Identifier icon) {
		context.fill(zone.left(), zone.top(), zone.right(), zone.bottom(), ActiveThemeColors.surfaceTranslucent());
		int size = VitrineTheme.CHEVRON_ICON_SOURCE_SIZE;
		int ix = zone.left() + (zone.width() - size) / 2;
		int iy = zone.top() + (zone.height() - size) / 2;
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(icon, ix, iy, size, size, 0.0F, 0.0F, size, size, size, size);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		HeroCarouselLayout layout = layout();
		int dot = layout.dotIndexAt(mouseX, mouseY);
		if (dot >= 0) {
			model.select(dot);
			return;
		}
		if (layout.inLeftArrow(mouseX, mouseY)) {
			model.previous();
			return;
		}
		if (layout.inRightArrow(mouseX, mouseY)) {
			model.next();
			return;
		}
		// ROADMAP GATE15: ein Klick auf die Kachel selbst (nicht Punkt/Pfeil)
		// aktiviert die aktuell gezeigte Kachel.
		if (onSlideActivated != null) {
			onSlideActivated.accept(slides.get(model.index()));
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			model.previous();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			model.next();
			return true;
		}
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| keyCode == GLFW.GLFW_KEY_SPACE) && onSlideActivated != null) {
			onSlideActivated.accept(slides.get(model.index()));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.translatable("vitrine.carousel.page", model.index() + 1));
	}
}
