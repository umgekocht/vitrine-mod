package de.spassglas.vitrine.ui.tile;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt den Scroll-Kern von ROADMAP S1 ab (docs/UI-SPEC.md "3. Start", Punkt
 * 3: "Horizontal scrollen mit Mausrad, Pfeiltasten und Ziehen"): Vorzeichen
 * und Schrittweite der drei Eingabearten, die Begrenzung auf {@code
 * [0, maxOffset]} sowie das Verhalten, wenn der Inhalt schmaler als das
 * Betrachtungsfenster ist. Feste {@code cardWidth}/{@code gutter} im
 * Konstruktor machen die erwarteten Pixelwerte unabhaengig von den
 * {@code VitrineTheme}-Tokens nachrechenbar.
 */
public final class TileRowScrollStateTest {

	/** 5 Kacheln a 100 px, 20 px Zwischenraum -> contentWidth 580, stepSize 120. */
	private static TileRowScrollState fiveWideTiles() {
		return new TileRowScrollState(5, 100, 20);
	}

	@Test
	public void contentWidthCountsTilesAndInnerGutters() {
		assertEquals(fiveWideTiles().contentWidth(), 5 * 100 + 4 * 20);
	}

	@Test
	public void maxOffsetIsContentMinusViewport() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);
		assertEquals(state.maxOffset(), 580 - 300);
	}

	@Test
	public void wheelUpScrollsTowardStartWheelDownTowardEnd() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);

		// GLFW: positiver Betrag = "nach oben" -> zurueck zum Anfang (Offset sinkt).
		state.scrollByWheel(1.0);
		assertEquals(state.offset(), 0, "am linken Rand kann es nicht weiter zurueck");

		state.scrollByWheel(-1.0);
		assertEquals(state.offset(), 120, "ein Notch nach unten = eine Schrittweite nach rechts");

		state.scrollByWheel(1.0);
		assertEquals(state.offset(), 0, "ein Notch nach oben hebt den Schritt wieder auf");
	}

	@Test
	public void arrowStepMovesExactlyOneStepSizePerDirection() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);

		state.scrollByStep(1);
		assertEquals(state.offset(), 120);
		state.scrollByStep(1);
		assertEquals(state.offset(), 240);
		state.scrollByStep(-1);
		assertEquals(state.offset(), 120);
	}

	@Test
	public void dragFollowsFingerPositiveDeltaRevealsEarlierTiles() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);
		state.scrollByStep(1);
		assertEquals(state.offset(), 120);

		// Streifen nach rechts ziehen (positive Delta) -> fruehere Kacheln, Offset sinkt.
		state.dragBy(50);
		assertEquals(state.offset(), 70);

		state.dragBy(-30);
		assertEquals(state.offset(), 100);
	}

	@Test
	public void pageJumpMovesAboutOneViewportAndClampsAtEdges() {
		TileRowScrollState state = fiveWideTiles(); // contentWidth 580, stepSize 120
		state.setViewportWidth(300);

		// Seitensprung = max(stepSize, viewport - stepSize) = max(120, 180) = 180,
		// bewusst um eine Kachel ueberlappend, damit die Randkachel danach ganz sichtbar ist.
		state.scrollByPage(1);
		assertEquals(state.offset(), 180);

		// Naechster Sprung wuerde auf 360 wollen -> an maxOffset (280) geklemmt.
		state.scrollByPage(1);
		assertEquals(state.offset(), state.maxOffset());

		state.scrollByPage(-1);
		assertEquals(state.offset(), 100, "ein Seitensprung zurueck: 280 - 180");

		state.scrollByPage(-1);
		assertEquals(state.offset(), 0, "nie unter 0");
	}

	@Test
	public void pageJumpNeverMovesLessThanOneStepEvenWithTinyViewport() {
		TileRowScrollState state = fiveWideTiles(); // stepSize 120
		state.setViewportWidth(50); // viewport - stepSize waere negativ

		state.scrollByPage(1);
		assertEquals(state.offset(), 120, "Untergrenze des Seitensprungs ist eine Schrittweite");
	}

	@Test
	public void pageJumpDoesNothingWhenContentFitsViewport() {
		TileRowScrollState state = new TileRowScrollState(2, 100, 20); // contentWidth 220
		state.setViewportWidth(500);

		state.scrollByPage(1);
		state.scrollByPage(-1);
		assertEquals(state.offset(), 0);
	}

	@Test
	public void homeAndEndJumpStraightToBothBounds() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);

		state.scrollToEnd();
		assertEquals(state.offset(), state.maxOffset());
		assertTrue(state.canScrollLeft());
		assertFalse(state.canScrollRight());

		state.scrollToStart();
		assertEquals(state.offset(), 0);
		assertFalse(state.canScrollLeft());
		assertTrue(state.canScrollRight());
	}

	@Test
	public void offsetIsClampedToLowerAndUpperBound() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);

		state.scrollByWheel(-100.0);
		assertEquals(state.offset(), state.maxOffset(), "nie ueber maxOffset hinaus");

		state.scrollByStep(1);
		assertEquals(state.offset(), state.maxOffset(), "am rechten Rand bleibt es stehen");

		state.dragBy(10_000);
		assertEquals(state.offset(), 0, "nie unter 0");
	}

	@Test
	public void canScrollFlagsReflectPositionAtEdges() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(300);

		assertFalse(state.canScrollLeft(), "start: nichts links");
		assertTrue(state.canScrollRight());

		state.scrollByStep(1);
		assertTrue(state.canScrollLeft());
		assertTrue(state.canScrollRight());

		state.scrollByWheel(-100.0);
		assertTrue(state.canScrollLeft());
		assertFalse(state.canScrollRight(), "ganz rechts: nichts mehr rechts");
	}

	@Test
	public void contentNarrowerThanViewportCannotScrollAtAll() {
		TileRowScrollState state = new TileRowScrollState(2, 100, 20); // contentWidth 220
		state.setViewportWidth(500);

		assertEquals(state.maxOffset(), 0);
		assertFalse(state.canScrollLeft());
		assertFalse(state.canScrollRight());

		state.scrollByStep(1);
		state.scrollByWheel(-5.0);
		state.dragBy(-200);
		assertEquals(state.offset(), 0, "ohne Ueberhang bewegt sich nichts");
	}

	@Test
	public void growingViewportReclampsExistingOffset() {
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(100);
		state.scrollByWheel(-100.0);
		assertEquals(state.offset(), state.maxOffset());

		int wideMax = state.maxOffset();
		state.setViewportWidth(560);
		assertTrue(state.maxOffset() < wideMax);
		assertEquals(state.offset(), state.maxOffset(), "der alte Offset wird auf das neue Maximum gekappt");
	}

	@Test
	public void revealIndexScrollsTheMinimumSoTheFocusedTileBecomesFullyVisible() {
		// ROADMAP S9: Pfeiltasten wandern kachelweise, die fokussierte Kachel
		// muss dabei sichtbar bleiben. 5 Kacheln a 100 px, 20 px Abstand,
		// stepSize 120, Betrachtungsfenster 250 (zeigt gut zwei Kacheln).
		TileRowScrollState state = fiveWideTiles();
		state.setViewportWidth(250);

		state.revealIndex(0);
		assertEquals(state.offset(), 0, "erste Kachel ist schon sichtbar -- kein Scrollen");

		state.revealIndex(2); // rechte Kante bei 2*120 + 100 = 340 > 250 -> Offset 90
		assertEquals(state.offset(), 90);

		state.revealIndex(4); // rechte Kante bei 4*120 + 100 = 580 -> Offset 330, geklemmt auf maxOffset
		assertEquals(state.offset(), state.maxOffset());

		state.revealIndex(0); // linke Kante bei 0 < offset -> zurueck an den Anfang
		assertEquals(state.offset(), 0);

		state.revealIndex(99); // ausserhalb -> nichts passiert
		assertEquals(state.offset(), 0);
	}

	@Test
	public void defaultConstructorUsesThemeTokensForGeometry() {
		TileRowScrollState state = new TileRowScrollState(4);
		assertEquals(state.stepSize(),
				de.spassglas.vitrine.ui.theme.VitrineTheme.CARD_WIDTH
						+ de.spassglas.vitrine.ui.theme.VitrineTheme.SPACE_GUTTER);
	}
}
