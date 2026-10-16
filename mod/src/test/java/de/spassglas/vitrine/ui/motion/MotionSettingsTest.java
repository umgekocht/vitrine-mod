package de.spassglas.vitrine.ui.motion;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * ROADMAP S9, Teil des Fertig-Kriteriums: "Animationen reduzieren" schaltet
 * <em>jede</em> Bewegung sofort ab. {@link MotionSettings} ist die eine Weiche
 * dafuer -- bei gesetztem Schalter liefert sie fuer alle vier Bewegungswerte
 * aus docs/UI-SPEC.md ("Bewegung": 150 ms Ueberfahren, 250 ms Seitenwechsel,
 * 8 px Aufwaertsbewegung, 400 ms Karussell) sofort {@code 0}.
 */
public final class MotionSettingsTest {

	@Test
	public void allFourSpecMotionValuesCollapseToZeroWhenReduced() {
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_HOVER_MS, true), 0, "Ueberfahren sofort");
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_PAGE_MS, true), 0, "Seitenwechsel sofort");
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_CAROUSEL_MS, true), 0, "Karussell sofort");
		assertEquals(MotionSettings.liftPx(true), 0, "keine 8-px-Aufwaertsbewegung");
	}

	@Test
	public void withoutReductionEachValueIsItsSpecToken() {
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_HOVER_MS, false), VitrineTheme.MOTION_HOVER_MS);
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_PAGE_MS, false), VitrineTheme.MOTION_PAGE_MS);
		assertEquals(MotionSettings.durationMs(VitrineTheme.MOTION_CAROUSEL_MS, false), VitrineTheme.MOTION_CAROUSEL_MS);
		assertEquals(MotionSettings.liftPx(false), VitrineTheme.MOTION_PAGE_LIFT_PX);
	}

	@Test
	public void namedAccessorsMatchTheirTokensAndZeroOutWhenReduced() {
		assertEquals(MotionSettings.hoverMs(false), VitrineTheme.MOTION_HOVER_MS);
		assertEquals(MotionSettings.pageMs(false), VitrineTheme.MOTION_PAGE_MS);
		assertEquals(MotionSettings.carouselMs(false), VitrineTheme.MOTION_CAROUSEL_MS);

		assertEquals(MotionSettings.hoverMs(true), 0);
		assertEquals(MotionSettings.pageMs(true), 0);
		assertEquals(MotionSettings.carouselMs(true), 0);
	}

	@Test
	public void negativeDurationIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> MotionSettings.durationMs(-1, false));
	}
}
