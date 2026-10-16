package de.spassglas.vitrine.ui.carousel;

import de.spassglas.vitrine.ui.motion.MotionSettings;
import de.spassglas.vitrine.ui.theme.VitrineTheme;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Client-freies Zeitschalt-, Pause- und Index-Modell des Hero-Karussells
 * (ROADMAP S8; docs/UI-SPEC.md "3. Start": "automatischer Wechsel alle 7
 * Sekunden -- pausiert, sobald die Maus darauf liegt oder der Nutzer selbst
 * blaettert"). Analog zu {@code SearchModel}, {@code LibraryModel} und
 * {@code TileRowScrollState}: kein einziger Minecraft-Typ, damit die Logik
 * headless getestet werden kann. {@code HeroCarouselWidget} verdrahtet nur.
 *
 * <p><b>Zeitquelle:</b> injizierbar als {@link LongSupplier} (Millisekunden),
 * wie {@code Debouncer}/{@code DelayedTaskScheduler} in H2 -- kein
 * hartverdrahtetes {@code System.currentTimeMillis()}. {@link #tick()} liest
 * die Uhr, bildet den Zeitunterschied zum letzten Aufruf und schaltet den
 * Zustand fort.</p>
 *
 * <p><b>Verhalten:</b></p>
 * <ul>
 *   <li>Ohne Eingriff rueckt {@link #index()} alle {@link #AUTO_ADVANCE_MS}
 *       simulierter Millisekunden um 1 vor und laeuft am Ende zum Anfang zurueck
 *       (Wrap).</li>
 *   <li>Liegt die Maus auf dem Karussell ({@link #setPointerOver(boolean)}),
 *       ruht der Timer: der Index bleibt stehen, egal wie viel Zeit vergeht;
 *       verlaesst die Maus das Karussell, laeuft er dort weiter, wo er
 *       stehengeblieben ist.</li>
 *   <li>Manuelles Blaettern ({@link #next()}/{@link #previous()}/
 *       {@link #select(int)}) setzt den 7-Sekunden-Timer zurueck und pausiert
 *       den Auto-Wechsel kurz ({@link #MANUAL_PAUSE_MS}).</li>
 *   <li>{@link #transitionMs(boolean)} liefert die Uebergangsdauer fuer den
 *       Slide-Wechsel: {@code 0} bei "Animationen reduzieren", sonst
 *       {@link VitrineTheme#MOTION_CAROUSEL_MS}.</li>
 * </ul>
 */
public final class HeroCarouselModel {

	/** docs/UI-SPEC.md "3. Start": "automatischer Wechsel alle 7 Sekunden". */
	public static final long AUTO_ADVANCE_MS = 7_000L;

	/**
	 * Kurze Pause nach manuellem Blaettern (Pfeil/Punkt), bevor der
	 * {@link #AUTO_ADVANCE_MS}-Auto-Wechsel wieder anlaeuft -- docs/UI-SPEC.md:
	 * pausiert, "wenn ... der Nutzer selbst blaettert". Bewusst deutlich kuerzer
	 * als der 7-Sekunden-Takt. Verhaltens-Zeit, kein gestalterisches Mass --
	 * darum eine Konstante hier (wie {@code DEBOUNCE_DELAY_MILLIS} in
	 * {@code HotApplyCoordinator}), kein Theme-Token.
	 */
	public static final long MANUAL_PAUSE_MS = 2_000L;

	private final int slideCount;
	private final long autoAdvanceMs;
	private final long manualPauseMs;
	private final long transitionMs;
	private final LongSupplier clockMs;

	private boolean clockSynced;
	private long lastObservedMs;
	private long elapsedTowardAdvanceMs;
	private long manualPauseRemainingMs;
	private boolean pointerOver;
	private int index;

	/**
	 * @param slideCount    Anzahl kuratierter Eintraege (docs/UI-SPEC.md: 3-5);
	 *                      mindestens 1.
	 * @param autoAdvanceMs Takt des automatischen Wechsels in Millisekunden.
	 * @param manualPauseMs Kurz-Pause nach manuellem Blaettern in Millisekunden.
	 * @param transitionMs  Uebergangsdauer eines Slide-Wechsels (0 = sofort).
	 * @param clockMs       monotone Millisekunden-Uhr (injizierbar fuer Tests).
	 */
	public HeroCarouselModel(int slideCount, long autoAdvanceMs, long manualPauseMs, long transitionMs,
			LongSupplier clockMs) {
		if (slideCount < 1) {
			throw new IllegalArgumentException("slideCount muss mindestens 1 sein: " + slideCount);
		}
		if (autoAdvanceMs <= 0L) {
			throw new IllegalArgumentException("autoAdvanceMs muss positiv sein: " + autoAdvanceMs);
		}
		if (manualPauseMs < 0L) {
			throw new IllegalArgumentException("manualPauseMs darf nicht negativ sein: " + manualPauseMs);
		}
		if (transitionMs < 0L) {
			throw new IllegalArgumentException("transitionMs darf nicht negativ sein: " + transitionMs);
		}
		this.slideCount = slideCount;
		this.autoAdvanceMs = autoAdvanceMs;
		this.manualPauseMs = manualPauseMs;
		this.transitionMs = transitionMs;
		this.clockMs = Objects.requireNonNull(clockMs, "clockMs");
	}

	/** Verdrahtet die Spec-Werte ({@link #AUTO_ADVANCE_MS}, {@link #MANUAL_PAUSE_MS}, {@link VitrineTheme#MOTION_CAROUSEL_MS}). */
	public static HeroCarouselModel forSlides(int slideCount, LongSupplier clockMs) {
		return new HeroCarouselModel(slideCount, AUTO_ADVANCE_MS, MANUAL_PAUSE_MS,
				VitrineTheme.MOTION_CAROUSEL_MS, clockMs);
	}

	public int index() {
		return index;
	}

	public int slideCount() {
		return slideCount;
	}

	/** Ob die Maus gerade auf dem Karussell liegt -- pausiert den Auto-Wechsel. */
	public void setPointerOver(boolean pointerOver) {
		this.pointerOver = pointerOver;
	}

	public boolean isPointerOver() {
		return pointerOver;
	}

	/** {@code true}, wenn der Auto-Wechsel gerade ruht: Maus auf dem Karussell ODER kurz nach manuellem Blaettern. */
	public boolean isAutoAdvancePaused() {
		return pointerOver || manualPauseRemainingMs > 0L;
	}

	/** Restliche Kurz-Pause nach manuellem Blaettern in Millisekunden (0 = keine). */
	public long manualPauseRemainingMs() {
		return manualPauseRemainingMs;
	}

	/**
	 * Uebergangsdauer eines Slide-Wechsels: {@code 0}, wenn "Animationen
	 * reduzieren" (docs/UI-SPEC.md "Bewegung": "alles sofort, ohne Uebergang"),
	 * sonst der konfigurierte Karussell-Wert ({@link VitrineTheme#MOTION_CAROUSEL_MS}).
	 */
	public long transitionMs(boolean reduceMotion) {
		return MotionSettings.durationMs((int) transitionMs, reduceMotion);
	}

	/** Liest die Uhr und schaltet den Zustand um den seit dem letzten Aufruf vergangenen Zeitunterschied fort. */
	public void tick() {
		long now = clockMs.getAsLong();
		if (!clockSynced) {
			clockSynced = true;
			lastObservedMs = now;
			return;
		}
		long delta = now - lastObservedMs;
		lastObservedMs = now;
		if (delta > 0L) {
			advanceTime(delta);
		}
	}

	private void advanceTime(long delta) {
		if (manualPauseRemainingMs > 0L) {
			if (delta <= manualPauseRemainingMs) {
				manualPauseRemainingMs -= delta;
				return;
			}
			delta -= manualPauseRemainingMs;
			manualPauseRemainingMs = 0L;
		}
		if (pointerOver || slideCount < 2) {
			return;
		}
		elapsedTowardAdvanceMs += delta;
		while (elapsedTowardAdvanceMs >= autoAdvanceMs) {
			elapsedTowardAdvanceMs -= autoAdvanceMs;
			index = (index + 1) % slideCount;
		}
	}

	/** Manuell einen Eintrag weiter (Pfeil rechts / Punkt). Setzt den Timer zurueck und pausiert kurz. */
	public void next() {
		pageTo((index + 1) % slideCount);
	}

	/** Manuell einen Eintrag zurueck (Pfeil links). Setzt den Timer zurueck und pausiert kurz. */
	public void previous() {
		pageTo((index - 1 + slideCount) % slideCount);
	}

	/** Manuell zu einem bestimmten Eintrag springen (Punkt-Klick). Setzt den Timer zurueck und pausiert kurz. */
	public void select(int target) {
		if (target < 0 || target >= slideCount) {
			throw new IndexOutOfBoundsException("Karussell-Index " + target + " ausserhalb 0.." + (slideCount - 1));
		}
		pageTo(target);
	}

	private void pageTo(int target) {
		this.index = target;
		this.elapsedTowardAdvanceMs = 0L;
		this.manualPauseRemainingMs = manualPauseMs;
		this.lastObservedMs = clockMs.getAsLong();
		this.clockSynced = true;
	}
}
