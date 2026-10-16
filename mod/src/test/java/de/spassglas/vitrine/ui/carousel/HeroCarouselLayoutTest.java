package de.spassglas.vitrine.ui.carousel;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die client-freie Geometrie des Hero-Karussells (ROADMAP S8,
 * docs/UI-SPEC.md "3. Start"): 21:9-Flaeche aus der Breite, Punktreihe unten
 * rechts, Pfeil-Trefferflaechen an den Kanten, Punkt-Treffer unter dem Zeiger.
 */
public final class HeroCarouselLayoutTest {

	private static final int X = 40;
	private static final int Y = 80;
	private static final int WIDTH = 630; // durch 21 teilbar -> exakte Hoehe 270
	private static final int ASPECT_W = 21;
	private static final int ASPECT_H = 9;
	private static final int DOT = 8;
	private static final int GAP = 4;
	private static final int INSET = 8;
	private static final int ARROW = 32;

	private static HeroCarouselLayout layout(int slides) {
		return new HeroCarouselLayout(X, Y, WIDTH, ASPECT_W, ASPECT_H, slides, DOT, GAP, INSET, ARROW);
	}

	@Test
	public void heightFollowsThe21To9Ratio() {
		assertEquals(layout(3).height(), WIDTH * ASPECT_H / ASPECT_W);
		assertEquals(layout(3).height(), 270);
	}

	@Test
	public void oneDotPerSlideRightAlignedInsideTheAreaAndNonOverlapping() {
		List<HeroCarouselLayout.Rect> dots = layout(4).dots();
		assertEquals(dots.size(), 4);

		int rightEdge = X + WIDTH;
		int bottomEdge = Y + layout(4).height();
		for (HeroCarouselLayout.Rect d : dots) {
			assertEquals(d.width(), DOT);
			assertEquals(d.height(), DOT);
			assertTrue(d.left() >= X && d.right() <= rightEdge, "Punkt liegt innerhalb der Flaeche (x)");
			assertTrue(d.top() >= Y && d.bottom() <= bottomEdge, "Punkt liegt innerhalb der Flaeche (y)");
		}
		// aufsteigend nach rechts, Abstand GAP, keine Ueberlappung
		for (int i = 1; i < dots.size(); i++) {
			assertEquals(dots.get(i).left() - dots.get(i - 1).right(), GAP);
		}
		// rechtester Punkt sitzt INSET von der rechten Kante
		assertEquals(rightEdge - dots.get(3).right(), INSET);
		// Punktreihe INSET ueber der Unterkante
		assertEquals(bottomEdge - dots.get(0).bottom(), INSET);
	}

	@Test
	public void dotIndexAtHitsTheRightDotAndMissesElsewhere() {
		HeroCarouselLayout l = layout(3);
		List<HeroCarouselLayout.Rect> dots = l.dots();

		for (int i = 0; i < 3; i++) {
			HeroCarouselLayout.Rect d = dots.get(i);
			assertEquals(l.dotIndexAt(d.left() + DOT / 2.0, d.top() + DOT / 2.0), i, "Mitte von Punkt " + i);
		}
		assertEquals(l.dotIndexAt(X + 5, Y + 5), -1, "obere linke Ecke -> kein Punkt");
		assertEquals(l.dotIndexAt(X - 100, Y - 100), -1, "weit ausserhalb -> kein Punkt");
	}

	@Test
	public void arrowsAreFullHeightStripsAtTheLeftAndRightEdges() {
		HeroCarouselLayout l = layout(3);

		HeroCarouselLayout.Rect left = l.leftArrow();
		assertEquals(left.left(), X);
		assertEquals(left.width(), ARROW);
		assertEquals(left.height(), l.height());

		HeroCarouselLayout.Rect right = l.rightArrow();
		assertEquals(right.right(), X + WIDTH);
		assertEquals(right.width(), ARROW);

		assertTrue(l.inLeftArrow(X + 1, Y + 10));
		assertTrue(l.inRightArrow(X + WIDTH - 1, Y + 10));
		assertFalse(l.inLeftArrow(X + WIDTH / 2.0, Y + 10), "Mitte ist kein Pfeil");
		assertFalse(l.inRightArrow(X + WIDTH / 2.0, Y + 10), "Mitte ist kein Pfeil");
		assertFalse(l.inLeftArrow(X - 1, Y + 10), "links neben der Flaeche -> kein Treffer");
	}

	@Test
	public void containsCoversTheWholeAreaForThePointerOverPauseCheck() {
		HeroCarouselLayout l = layout(3);

		assertTrue(l.contains(X + WIDTH / 2.0, Y + l.height() / 2.0));
		assertTrue(l.contains(X, Y));
		assertFalse(l.contains(X + WIDTH, Y)); // rechte/untere Kante exklusiv
		assertFalse(l.contains(X + WIDTH / 2.0, Y + l.height() + 1));
		assertFalse(l.contains(X - 1, Y));
	}

	@Test
	public void invalidConstructorArgumentsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new HeroCarouselLayout(0, 0, 0, ASPECT_W, ASPECT_H, 3, DOT, GAP, INSET, ARROW));
		assertThrows(IllegalArgumentException.class,
				() -> new HeroCarouselLayout(0, 0, WIDTH, ASPECT_W, ASPECT_H, 0, DOT, GAP, INSET, ARROW));
		assertThrows(IllegalArgumentException.class,
				() -> new HeroCarouselLayout(0, 0, WIDTH, 0, ASPECT_H, 3, DOT, GAP, INSET, ARROW));
		assertThrows(IllegalArgumentException.class,
				() -> new HeroCarouselLayout(0, 0, WIDTH, ASPECT_W, ASPECT_H, 3, 0, GAP, INSET, ARROW));
	}
}
