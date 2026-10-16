package de.spassglas.vitrine.ui.carousel;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import net.minecraft.SharedConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * ROADMAP GATE15-Fertig-Kriterium ("Klick auf eine Hero-Kachel oeffnet die
 * Detailseite"): beweist, dass ein Klick auf die Kachel selbst (nicht
 * Punkt/Pfeil) {@link HeroCarouselWidget#setOnSlideActivated(java.util.function.Consumer)}
 * mit der aktuell gezeigten {@link TileData} aufruft, dass ein Klick auf
 * Punkt/Pfeil stattdessen nur blaettert (kein Aktivieren), und dass ohne
 * Rueckruf (Demo-Platzhalter) ein Klick auf die Kachel folgenlos bleibt --
 * ohne laufenden Client, wie {@code HeroCarouselLayoutTest}/{@code
 * HeroCarouselModelTest} fuer die uebrige Karussell-Logik.
 */
public final class HeroCarouselWidgetClickTest {

	private static final int WIDTH = 900;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	private static TileData tile(String id) {
		return TileData.withoutImage(id, "Kachel " + id, "vitrine", TilePackageType.THEME, TileStatus.NONE);
	}

	private static HeroCarouselModel model(int slides) {
		AtomicLong now = new AtomicLong(1_000L);
		return HeroCarouselModel.forSlides(slides, now::get);
	}

	@Test
	public void clickOnTheCardActivatesTheCurrentlyShownSlide() {
		List<TileData> slides = List.of(tile("a"), tile("b"), tile("c"));
		HeroCarouselWidget widget = new HeroCarouselWidget(0, 0, WIDTH, slides, null, model(3), false, false);

		TileData[] activated = new TileData[1];
		widget.setOnSlideActivated(t -> activated[0] = t);

		// Mitte der Karussell-Flaeche -- weder Punkt (unten rechts) noch Pfeil
		// (linker/rechter Randstreifen, siehe HeroCarouselLayoutTest).
		widget.onClick(WIDTH / 2.0, HeroCarouselWidget.heightFor(WIDTH) / 2.0);

		assertEquals(activated[0], slides.get(0), "der Klick muss die aktuell gezeigte Kachel liefern");
	}

	@Test
	public void clickOnTheRightArrowAdvancesButDoesNotActivate() {
		List<TileData> slides = List.of(tile("a"), tile("b"));
		HeroCarouselWidget widget = new HeroCarouselWidget(0, 0, WIDTH, slides, null, model(2), false, false);

		TileData[] activated = new TileData[1];
		widget.setOnSlideActivated(t -> activated[0] = t);

		double arrowX = WIDTH - 1; // rechter Randstreifen = Pfeil-Trefferflaeche
		double y = HeroCarouselWidget.heightFor(WIDTH) / 2.0;
		widget.onClick(arrowX, y);

		assertNull(activated[0], "ein Pfeil-Klick blaettert nur, aktiviert keine Kachel");
		assertEquals(widget.model().index(), 1, "der Pfeil-Klick muss trotzdem weiterblaettern");
	}

	@Test
	public void clickOnADotSelectsButDoesNotActivate() {
		List<TileData> slides = List.of(tile("a"), tile("b"), tile("c"));
		HeroCarouselWidget widget = new HeroCarouselWidget(0, 0, WIDTH, slides, null, model(3), false, false);

		TileData[] activated = new TileData[1];
		widget.setOnSlideActivated(t -> activated[0] = t);

		HeroCarouselLayout layout = new HeroCarouselLayout(0, 0, WIDTH, VitrineTheme.HERO_ASPECT_WIDTH,
				VitrineTheme.HERO_ASPECT_HEIGHT, 3, VitrineTheme.CAROUSEL_DOT_SIZE, VitrineTheme.SPACE_UNIT,
				VitrineTheme.SPACE_UNIT * 2, VitrineTheme.CAROUSEL_ARROW_WIDTH);
		HeroCarouselLayout.Rect lastDot = layout.dots().get(2);

		widget.onClick(lastDot.left() + 1, lastDot.top() + 1);

		assertNull(activated[0], "ein Punkt-Klick springt nur, aktiviert keine Kachel");
		assertEquals(widget.model().index(), 2);
	}

	@Test
	public void withoutACallbackAClickOnTheCardDoesNothing() {
		List<TileData> slides = List.of(tile("a"));
		HeroCarouselWidget widget = new HeroCarouselWidget(0, 0, WIDTH, slides, null, model(1), false, true);

		// Kein setOnSlideActivated (Demo-Platzhalter) -- ein Klick darf weder
		// werfen noch den gezeigten Index veraendern (Charter-Regel 2: kein
		// Vorgaukeln einer Aktion, die es fuer diese Ids nicht gibt).
		widget.onClick(WIDTH / 2.0, HeroCarouselWidget.heightFor(WIDTH) / 2.0);

		assertEquals(widget.model().index(), 0, "ohne Rueckruf bleibt der gezeigte Index unveraendert");
	}
}
