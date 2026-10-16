package de.spassglas.vitrine.ui.motion;

/**
 * Seitenwechsel-Einblendung (docs/UI-SPEC.md "Bewegung": "250 ms fuer
 * Seitenwechsel (Einblenden + 8 px Aufwaertsbewegung) ... Eine Kurve
 * (ease-out). Kein Federn, kein Huepfen. Bei ,Animationen reduzieren' ...
 * alles sofort, ohne Uebergang."). Analog zu {@link
 * de.spassglas.vitrine.ui.tile.TileElevation}: reine Funktionen eines
 * vergangenen-Zeit-Werts, kein Minecraft-Typ -- headless pruefbar (siehe
 * {@code PageEntranceTest}).
 *
 * <p>Ein neu geoeffneter Bildschirm (ROADMAP GATE85: {@code StoreScreen},
 * {@code LibraryScreen}, {@code PackageDetailScreen}, {@code SettingsScreen},
 * {@code HudEditorScreen}) merkt sich den Zeitpunkt seiner Erzeugung
 * ({@code Util.getMeasuringTimeMs()}, wie {@code hoverStartMs} in {@code
 * TileRowWidget} oder {@code transitionStartMs} in {@code
 * HeroCarouselWidget}) und fragt waehrend der ersten {@link
 * MotionSettings#pageMs(boolean)} Millisekunden hier zwei Dinge ab:</p>
 * <ul>
 *   <li>{@link #offsetPx(long, boolean)}: den noch verbleibenden
 *       Aufwaerts-Versatz in Pixeln, mit dem der gesamte Seiteninhalt
 *       (ueber {@code DrawContext.getMatrices().translate(0, offset, 0)})
 *       gezeichnet wird -- startet bei {@link MotionSettings#liftPx(boolean)}
 *       (8 px), endet bei 0.</li>
 *   <li>{@link #overlayAlpha(long, boolean)}: die Deckkraft (0..255) einer
 *       ueber die ganze Seite gelegten Flaeche in der Hintergrundfarbe der
 *       Seite ({@code color.bg}), die das "Einblenden" nachbildet -- startet
 *       voll deckend, endet durchsichtig.</li>
 * </ul>
 *
 * <p>Bei "Animationen reduzieren" liefert {@link MotionSettings#pageMs(boolean)}
 * {@code 0} -- beide Methoden liefern dann sofort ihren Endwert (kein Versatz,
 * keine Abdeckung), die Seite steht von der ersten gezeichneten Frame an fest
 * (S9-Vertrag: der Schalter schaltet <em>jede</em> Bewegung ab).</p>
 */
public final class PageEntrance {

	private PageEntrance() {
	}

	/**
	 * Der verbleibende Aufwaerts-Versatz in Pixeln waehrend der Einblend-Animation:
	 * startet bei {@link MotionSettings#liftPx(boolean)}, faellt mit einer
	 * ease-out-Kurve auf {@code 0}. Vor Ablauf der Dauer (oder bei {@code
	 * elapsedMs <= 0}) der Startwert, danach immer {@code 0}.
	 */
	public static float offsetPx(long elapsedMs, boolean reduceMotion) {
		int durationMs = MotionSettings.pageMs(reduceMotion);
		int startPx = MotionSettings.liftPx(reduceMotion);
		if (durationMs <= 0 || startPx <= 0 || elapsedMs >= durationMs) {
			return 0f;
		}
		if (elapsedMs <= 0L) {
			return startPx;
		}
		float t = elapsedMs / (float) durationMs;
		float eased = 1f - (1f - t) * (1f - t);
		return startPx * (1f - eased);
	}

	/**
	 * Die Deckkraft (0..255) der Einblend-Abdeckung: startet voll deckend
	 * ({@code 255}), faellt mit derselben ease-out-Kurve wie {@link
	 * #offsetPx(long, boolean)} auf {@code 0} (durchsichtig, die Seite ist
	 * fertig eingeblendet).
	 */
	public static int overlayAlpha(long elapsedMs, boolean reduceMotion) {
		int durationMs = MotionSettings.pageMs(reduceMotion);
		if (durationMs <= 0 || elapsedMs >= durationMs) {
			return 0;
		}
		if (elapsedMs <= 0L) {
			return 255;
		}
		float t = elapsedMs / (float) durationMs;
		float eased = 1f - (1f - t) * (1f - t);
		return Math.round(255 * (1f - eased));
	}

	/** {@code true}, solange die Einblend-Animation noch sichtbar etwas beitraegt (Versatz oder Abdeckung). */
	public static boolean isAnimating(long elapsedMs, boolean reduceMotion) {
		return overlayAlpha(elapsedMs, reduceMotion) > 0 || offsetPx(elapsedMs, reduceMotion) != 0f;
	}

	/**
	 * Verknuepft eine Deckkraft (0..255) mit den Farbkanaelen von {@code baseArgb}
	 * (ueblicherweise {@code ActiveThemeColors.bg()}) zu einer fertigen ARGB-Farbe
	 * fuer {@code DrawContext.fill(...)}. Reine Bit-Arithmetik, kein Minecraft-Typ.
	 */
	public static int overlayColor(int alpha, int baseArgb) {
		int clamped = Math.max(0, Math.min(255, alpha));
		return (clamped << 24) | (baseArgb & 0x00FFFFFF);
	}
}
