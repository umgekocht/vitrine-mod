package de.spassglas.vitrine.ui.carousel;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das headless pruefbare S8-Fertig-Kriterium ("Karussell bleibt stehen,
 * solange die Maus darauf liegt") sowie die uebrigen Verhalten des
 * client-freien {@link HeroCarouselModel}: 7-Sekunden-Auto-Wechsel mit Wrap,
 * Timer-Reset und Kurz-Pause bei manuellem Blaettern, Uebergangsdauer je nach
 * "Animationen reduzieren", kein Wechsel bei weniger als zwei Eintraegen.
 */
public final class HeroCarouselModelTest {

	/** Steuerbare Millisekunden-Uhr fuer die injizierbare {@code LongSupplier}. */
	private static final class FakeClock {
		private final AtomicLong now = new AtomicLong(1_000L);

		long get() {
			return now.get();
		}

		void advance(long ms) {
			now.addAndGet(ms);
		}
	}

	private static HeroCarouselModel model(int slides, FakeClock clock) {
		HeroCarouselModel m = HeroCarouselModel.forSlides(slides, clock::get);
		m.tick(); // erste Synchronisation der Uhr
		return m;
	}

	// --- S8-Fertig-Kriterium: Maus drauf -> steht still ------------------

	@Test
	public void pointerOverFreezesTheIndexNoMatterHowMuchTimePasses() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(4, clock);

		m.setPointerOver(true);
		for (int i = 0; i < 50; i++) {
			clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS * 3);
			m.tick();
		}

		assertEquals(m.index(), 0, "solange die Maus auf dem Karussell liegt, wechselt nichts");
		assertTrue(m.isAutoAdvancePaused());
	}

	@Test
	public void afterPointerLeavesItContinuesFromWhereItStopped() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(4, clock);

		// 5 s vergehen ohne Maus -> noch kein Wechsel, aber 5 s "auf dem Konto".
		clock.advance(5_000L);
		m.tick();
		assertEquals(m.index(), 0);

		// Maus drauf, sehr viel Zeit -> weiterhin kein Wechsel, Konto eingefroren.
		m.setPointerOver(true);
		clock.advance(1_000_000L);
		m.tick();
		assertEquals(m.index(), 0);

		// Maus weg: es fehlen nur noch 2 s bis 7 s -> danach genau ein Wechsel.
		m.setPointerOver(false);
		clock.advance(1_999L);
		m.tick();
		assertEquals(m.index(), 0, "die eingefrorenen 5 s zaehlen weiter, 1,999 s reichen noch nicht");
		clock.advance(1L);
		m.tick();
		assertEquals(m.index(), 1, "5 s + 2 s = 7 s -> genau ein Wechsel, dort wo er stand");
	}

	// --- Auto-Wechsel + Wrap ------------------------------------------

	@Test
	public void withoutInteractionItAdvancesEveryAutoAdvanceMsAndWrapsAround() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(3, clock);

		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS);
		m.tick();
		assertEquals(m.index(), 1);

		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS);
		m.tick();
		assertEquals(m.index(), 2);

		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS);
		m.tick();
		assertEquals(m.index(), 0, "am Ende zurueck zum Anfang (Wrap)");
	}

	@Test
	public void aLongSingleTickCatchesUpAllElapsedSteps() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(5, clock);

		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS * 7 + 10L);
		m.tick();

		assertEquals(m.index(), 7 % 5, "7 faellige Schritte auf einmal, korrekt gewrappt");
	}

	// --- Manuelles Blaettern: Timer-Reset + Kurz-Pause -----------------

	@Test
	public void nextResetsTheTimerAndPausesBrieflyThenResumes() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(3, clock);

		// 5 s Richtung Auto-Wechsel, dann manuell weiter.
		clock.advance(5_000L);
		m.tick();
		m.next();
		assertEquals(m.index(), 1);
		assertTrue(m.isAutoAdvancePaused(), "direkt nach dem Blaettern ruht der Auto-Wechsel");
		assertEquals(m.manualPauseRemainingMs(), HeroCarouselModel.MANUAL_PAUSE_MS);

		// Kurz-Pause laeuft ab.
		clock.advance(HeroCarouselModel.MANUAL_PAUSE_MS - 1L);
		m.tick();
		assertTrue(m.isAutoAdvancePaused());
		clock.advance(1L);
		m.tick();
		assertFalse(m.isAutoAdvancePaused(), "nach MANUAL_PAUSE_MS laeuft der Auto-Wechsel wieder");

		// Der 7-Sekunden-Timer wurde zurueckgesetzt: die 5 s von vorhin zaehlen nicht mehr.
		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS - 1L);
		m.tick();
		assertEquals(m.index(), 1, "Timer stand nach next() auf 0 -> noch kein weiterer Wechsel");
		clock.advance(1L);
		m.tick();
		assertEquals(m.index(), 2);
	}

	@Test
	public void previousWrapsBackwardAndSelectJumpsDirectly() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(4, clock);

		m.previous();
		assertEquals(m.index(), 3, "von 0 rueckwaerts -> letzter Eintrag");

		m.select(2);
		assertEquals(m.index(), 2);
		assertTrue(m.isAutoAdvancePaused());
		assertEquals(m.manualPauseRemainingMs(), HeroCarouselModel.MANUAL_PAUSE_MS);
	}

	@Test
	public void selectRejectsAnOutOfRangeIndex() {
		HeroCarouselModel m = model(3, new FakeClock());
		assertThrows(IndexOutOfBoundsException.class, () -> m.select(3));
		assertThrows(IndexOutOfBoundsException.class, () -> m.select(-1));
	}

	// --- Uebergangsdauer -------------------------------------------

	@Test
	public void transitionMsIsZeroWhenMotionReducedOtherwiseTheCarouselToken() {
		HeroCarouselModel m = model(3, new FakeClock());

		assertEquals(m.transitionMs(true), 0L, "\"Animationen reduzieren\" -> sofort, ohne Uebergang");
		assertEquals(m.transitionMs(false), VitrineTheme.MOTION_CAROUSEL_MS);
	}

	// --- Weniger als zwei Eintraege --------------------------------

	@Test
	public void withFewerThanTwoSlidesNothingEverChanges() {
		FakeClock clock = new FakeClock();
		HeroCarouselModel m = model(1, clock);

		clock.advance(HeroCarouselModel.AUTO_ADVANCE_MS * 10);
		m.tick();
		assertEquals(m.index(), 0);

		m.next();
		assertEquals(m.index(), 0);
		m.previous();
		assertEquals(m.index(), 0);
	}
}
