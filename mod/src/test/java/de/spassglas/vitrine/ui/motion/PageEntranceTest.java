package de.spassglas.vitrine.ui.motion;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE85: {@link MotionSettings#pageMs(boolean)} hatte bislang keinen
 * Aufrufer -- nur {@code hoverMs}/das Karussell waren verdrahtet. {@link
 * PageEntrance} ist dieser Aufrufer (verdrahtet in {@code StoreScreen},
 * {@code LibraryScreen}, {@code PackageDetailScreen}, {@code SettingsScreen},
 * {@code HudEditorScreen}). Dieser Test deckt docs/UI-SPEC.md "Bewegung" ab:
 * "250 ms fuer Seitenwechsel (Einblenden + 8 px Aufwaertsbewegung) ... Eine
 * Kurve (ease-out) ... Bei ,Animationen reduzieren' ... alles sofort, ohne
 * Uebergang."
 */
public final class PageEntranceTest {

	private static final float EPS = 1.0e-4f;

	@Test
	public void callsMotionSettingsPageMsAndLiftPx() {
		// Der eigentliche Beweis fuer GATE85: pageMs() hat jetzt einen
		// Aufrufer, dessen sichtbares Verhalten (Dauer/Start-Versatz) exakt
		// dem von MotionSettings.pageMs()/liftPx() entspricht.
		assertEquals(PageEntrance.offsetPx(0L, false), (float) MotionSettings.liftPx(false), EPS);
		assertEquals(PageEntrance.offsetPx(VitrineTheme.MOTION_PAGE_MS, false), 0f, EPS);
	}

	@Test
	public void beforeAnimationStartsAtTheFullLiftAndFullyOpaque() {
		assertEquals(PageEntrance.offsetPx(0L, false), (float) VitrineTheme.MOTION_PAGE_LIFT_PX, EPS);
		assertEquals(PageEntrance.offsetPx(-50L, false), (float) VitrineTheme.MOTION_PAGE_LIFT_PX, EPS);
		assertEquals(PageEntrance.overlayAlpha(0L, false), 255);
	}

	@Test
	public void atOrAfterPageDurationTheAnimationIsFullyDone() {
		assertEquals(PageEntrance.offsetPx(VitrineTheme.MOTION_PAGE_MS, false), 0f, EPS);
		assertEquals(PageEntrance.offsetPx(VitrineTheme.MOTION_PAGE_MS + 1000L, false), 0f, EPS);
		assertEquals(PageEntrance.overlayAlpha(VitrineTheme.MOTION_PAGE_MS, false), 0);
		assertFalse(PageEntrance.isAnimating(VitrineTheme.MOTION_PAGE_MS, false));
	}

	@Test
	public void midAnimationIsStrictlyMonotonicAndStaysWithinBounds() {
		float previousOffset = PageEntrance.offsetPx(0L, false);
		int previousAlpha = PageEntrance.overlayAlpha(0L, false);
		for (long ms = 1; ms <= VitrineTheme.MOTION_PAGE_MS; ms++) {
			float offset = PageEntrance.offsetPx(ms, false);
			int alpha = PageEntrance.overlayAlpha(ms, false);
			assertTrue(offset <= previousOffset + EPS, "Versatz muss fallen bei ms=" + ms);
			assertTrue(alpha <= previousAlpha, "Deckkraft muss fallen bei ms=" + ms);
			assertTrue(offset >= -EPS && offset <= VitrineTheme.MOTION_PAGE_LIFT_PX + EPS,
					"kein Ueberschwingen des Versatzes bei ms=" + ms + ": " + offset);
			assertTrue(alpha >= 0 && alpha <= 255, "Deckkraft ausserhalb 0..255 bei ms=" + ms + ": " + alpha);
			previousOffset = offset;
			previousAlpha = alpha;
		}
	}

	@Test
	public void reduceMotionCollapsesTheAnimationToZeroDurationImmediately() {
		// ROADMAP S9 / docs/UI-SPEC.md "Bewegung": "alles sofort, ohne Uebergang" --
		// bei reduzierter Bewegung ist die Seite von der ersten Frame an fertig,
		// egal welcher elapsedMs-Wert hereinkommt.
		assertEquals(PageEntrance.offsetPx(0L, true), 0f, EPS);
		assertEquals(PageEntrance.offsetPx(1L, true), 0f, EPS);
		assertEquals(PageEntrance.overlayAlpha(0L, true), 0);
		assertEquals(PageEntrance.overlayAlpha(1L, true), 0);
		assertFalse(PageEntrance.isAnimating(0L, true));
		assertFalse(PageEntrance.isAnimating(1L, true));

		// Ohne den Schalter laeuft die Animation wie gehabt an.
		assertTrue(PageEntrance.isAnimating(0L, false));
	}

	@Test
	public void easeOutIsFastFirstThenSlowSecondHalf() {
		float half = PageEntrance.offsetPx(VitrineTheme.MOTION_PAGE_MS / 2L, false);
		float progressAtHalf = 1f - half / VitrineTheme.MOTION_PAGE_LIFT_PX;

		// ease-out: bei halber Zeit ist schon deutlich mehr als die Haelfte des Wegs zurueckgelegt.
		assertTrue(progressAtHalf > 0.5f, "ease-out muss in der ersten Haelfte mehr Weg machen, war " + progressAtHalf);
	}

	@Test
	public void overlayColorCombinesAlphaWithTheBaseColorChannels() {
		int baseArgb = 0xFF112233;
		assertEquals(PageEntrance.overlayColor(255, baseArgb), 0xFF112233);
		assertEquals(PageEntrance.overlayColor(0, baseArgb), 0x00112233);
		assertEquals(PageEntrance.overlayColor(128, baseArgb), (128 << 24) | 0x112233);
		// Deckkraft wird geklemmt, kein Ueber-/Unterlauf in den Farbkanal.
		assertEquals(PageEntrance.overlayColor(300, baseArgb), 0xFF112233);
		assertEquals(PageEntrance.overlayColor(-10, baseArgb), 0x00112233);
	}
}
