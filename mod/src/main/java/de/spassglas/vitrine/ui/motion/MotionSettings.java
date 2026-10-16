package de.spassglas.vitrine.ui.motion;

import de.spassglas.vitrine.ui.theme.VitrineTheme;

/**
 * Die eine, client-freie Stelle fuer alle Uebergangszeiten des Stores
 * (docs/UI-SPEC.md "Bewegung": "150 ms fuer Ueberfahren, 250 ms fuer
 * Seitenwechsel (Einblenden + 8 px Aufwaertsbewegung), 400 ms fuers Karussell.
 * Eine Kurve (ease-out) ... Bei ,Animationen reduzieren' ... alles sofort,
 * ohne Uebergang.").
 *
 * <p>Jeder Animationspfad (Hover-Anhebung, Seitenwechsel, Karussell) fragt hier
 * seine Dauer ab, statt eine der Spec-Zahlen selbst zu tragen. Ist
 * {@code reduceMotion} gesetzt, liefert {@link #durationMs(int, boolean)}
 * {@code 0} -- der aufrufende Code springt dann sofort auf den Zielwert, ohne
 * Zwischenschritte. Damit schaltet der Einstellungs-Schalter <em>jede</em>
 * Bewegung ab, nicht nur eine einzelne.</p>
 *
 * <p>Reine Funktionen, kein Minecraft-Typ -- headless pruefbar (siehe
 * {@code MotionSettingsTest}). Die Spec-Werte selbst stehen als Tokens in
 * {@link VitrineTheme} ({@code MOTION_*}); diese Klasse ist nur die Weiche.</p>
 */
public final class MotionSettings {

	private MotionSettings() {
	}

	/**
	 * Die wirksame Uebergangsdauer in Millisekunden: {@code 0} bei reduzierter
	 * Bewegung ("alles sofort, ohne Uebergang"), sonst {@code specDurationMs}.
	 *
	 * @param specDurationMs die Spec-Dauer (eines der {@code VitrineTheme.MOTION_*_MS}), nicht negativ
	 * @param reduceMotion   ob der Nutzer "Animationen reduzieren" gewaehlt hat
	 */
	public static int durationMs(int specDurationMs, boolean reduceMotion) {
		if (specDurationMs < 0) {
			throw new IllegalArgumentException("specDurationMs darf nicht negativ sein: " + specDurationMs);
		}
		return reduceMotion ? 0 : specDurationMs;
	}

	/** Ueberfahren-Uebergang (docs/UI-SPEC.md: 150 ms), {@code 0} bei reduzierter Bewegung. */
	public static int hoverMs(boolean reduceMotion) {
		return durationMs(VitrineTheme.MOTION_HOVER_MS, reduceMotion);
	}

	/** Seitenwechsel-Uebergang (docs/UI-SPEC.md: 250 ms), {@code 0} bei reduzierter Bewegung. */
	public static int pageMs(boolean reduceMotion) {
		return durationMs(VitrineTheme.MOTION_PAGE_MS, reduceMotion);
	}

	/** Karussell-Uebergang (docs/UI-SPEC.md: 400 ms), {@code 0} bei reduzierter Bewegung. */
	public static int carouselMs(boolean reduceMotion) {
		return durationMs(VitrineTheme.MOTION_CAROUSEL_MS, reduceMotion);
	}

	/**
	 * Die Aufwaertsbewegung des Seitenwechsels (docs/UI-SPEC.md: "8 px
	 * Aufwaertsbewegung"): {@code 0} bei reduzierter Bewegung (kein Versatz,
	 * die Seite steht sofort), sonst {@link VitrineTheme#MOTION_PAGE_LIFT_PX}.
	 */
	public static int liftPx(boolean reduceMotion) {
		return reduceMotion ? 0 : VitrineTheme.MOTION_PAGE_LIFT_PX;
	}
}
