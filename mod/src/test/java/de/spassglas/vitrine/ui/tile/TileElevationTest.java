package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt die Ueberfahren-Anhebung aus docs/UI-SPEC.md "4. Kachel" ("Beim
 * Ueberfahren: leicht anheben") und "Bewegung" ("150 ms ... eine Kurve
 * (ease-out). Kein Federn, kein Huepfen.") ab: der animierte Versatz startet
 * beim Ruhewert, endet beim Hover-Wert, ist dazwischen streng monoton und
 * verlaesst das Intervall nie (kein Ueberschwingen).
 */
public final class TileElevationTest {

	private static final float EPS = 1.0e-4f;

	@Test
	public void discreteLiftPicksRestOrHoverToken() {
		assertEquals(TileElevation.liftPx(false), VitrineTheme.ELEVATION_REST);
		assertEquals(TileElevation.liftPx(true), VitrineTheme.ELEVATION_HOVER);
	}

	@Test
	public void beforeAnimationStartsValueIsTheStartValue() {
		assertEquals(TileElevation.liftPx(true, 0L), (float) VitrineTheme.ELEVATION_REST, EPS);
		assertEquals(TileElevation.liftPx(true, -50L), (float) VitrineTheme.ELEVATION_REST, EPS);
		// Umgekehrte Richtung: verlassen des Hovers startet beim Hover-Wert.
		assertEquals(TileElevation.liftPx(false, 0L), (float) VitrineTheme.ELEVATION_HOVER, EPS);
	}

	@Test
	public void atOrAfterMotionDurationValueIsTheTargetValue() {
		assertEquals(TileElevation.liftPx(true, VitrineTheme.MOTION_HOVER_MS), (float) VitrineTheme.ELEVATION_HOVER, EPS);
		assertEquals(TileElevation.liftPx(true, VitrineTheme.MOTION_HOVER_MS + 500L),
				(float) VitrineTheme.ELEVATION_HOVER, EPS);
		assertEquals(TileElevation.liftPx(false, VitrineTheme.MOTION_HOVER_MS), (float) VitrineTheme.ELEVATION_REST, EPS);
	}

	@Test
	public void hoverInProgressIsStrictlyMonotonicAndStaysWithinBounds() {
		float previous = TileElevation.liftPx(true, 0L);
		for (long ms = 1; ms <= VitrineTheme.MOTION_HOVER_MS; ms++) {
			float current = TileElevation.liftPx(true, ms);
			assertTrue(current >= previous - EPS, "monoton steigend bei ms=" + ms + " (" + previous + " -> " + current + ")");
			assertTrue(current >= VitrineTheme.ELEVATION_REST - EPS && current <= VitrineTheme.ELEVATION_HOVER + EPS,
					"kein Ueberschwingen bei ms=" + ms + ": " + current);
			previous = current;
		}
	}

	@Test
	public void reduceMotionSnapsStraightToTheTargetWithoutAnyInBetweenStep() {
		// ROADMAP S9 / docs/UI-SPEC.md "Bewegung": "alles sofort, ohne Uebergang".
		assertEquals(TileElevation.liftPx(true, 0L, true), (float) VitrineTheme.ELEVATION_HOVER, EPS);
		assertEquals(TileElevation.liftPx(true, 1L, true), (float) VitrineTheme.ELEVATION_HOVER, EPS);
		assertEquals(TileElevation.liftPx(false, 0L, true), (float) VitrineTheme.ELEVATION_REST, EPS);
		// Ohne den Schalter laeuft der Uebergang wie gehabt.
		assertEquals(TileElevation.liftPx(true, 0L, false), (float) VitrineTheme.ELEVATION_REST, EPS);
	}

	@Test
	public void easeOutIsFastFirstThenSlowSecondHalf() {
		int span = VitrineTheme.ELEVATION_HOVER - VitrineTheme.ELEVATION_REST;
		float half = TileElevation.liftPx(true, VitrineTheme.MOTION_HOVER_MS / 2L);
		float progressAtHalf = (half - VitrineTheme.ELEVATION_REST) / span;

		// ease-out: bei halber Zeit ist schon deutlich mehr als die Haelfte des Wegs zurueckgelegt.
		assertTrue(progressAtHalf > 0.5f, "ease-out muss in der ersten Haelfte mehr Weg machen, war " + progressAtHalf);
	}
}
