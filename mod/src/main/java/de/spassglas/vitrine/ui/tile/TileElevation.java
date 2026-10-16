package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.motion.MotionSettings;
import de.spassglas.vitrine.ui.theme.VitrineTheme;

/**
 * Beim Ueberfahren "leicht anheben" (docs/UI-SPEC.md "4. Kachel": "Beim
 * Ueberfahren: leicht anheben ({@code elevation.hover}), Rahmen aufhellen.").
 * Minecrafts GUI kennt keine echten Schatten -- {@link VitrineTheme#ELEVATION_REST}
 * und {@link VitrineTheme#ELEVATION_HOVER} werden hier als vertikaler
 * Zeichen-Versatz in Pixeln interpretiert, siehe Klassenkommentar-Hinweis in
 * {@link VitrineTheme}. Reine Funktion, ohne Minecraft-Abhaengigkeit, damit
 * "Hover-Zustand triggert Anhebung" ohne laufenden Client beweisbar ist.
 */
public final class TileElevation {

	private TileElevation() {
	}

	/** Endgueltiger Versatz in Pixeln, um den eine Kachel nach oben gezeichnet wird -- mehr, wenn {@code hovered}. */
	public static int liftPx(boolean hovered) {
		return hovered ? VitrineTheme.ELEVATION_HOVER : VitrineTheme.ELEVATION_REST;
	}

	/**
	 * Der Versatz waehrend der laufenden Ueberfahren-Animation
	 * (docs/UI-SPEC.md "Bewegung": "150 ms fuer Ueberfahren ... Eine Kurve
	 * ({@code ease-out}). Kein Federn, kein Huepfen."). {@code elapsedMs} ist
	 * die seit dem letzten Wechsel des Hover-Zustands vergangene Zeit; bei
	 * {@code >= VitrineTheme.MOTION_HOVER_MS} ist der Endwert erreicht, bei
	 * {@code <= 0} der Startwert. Reine Funktion, damit der Verlauf ohne
	 * laufenden Client geprueft werden kann.
	 */
	public static float liftPx(boolean hovered, long elapsedMs) {
		return liftPx(hovered, elapsedMs, false);
	}

	/**
	 * Wie {@link #liftPx(boolean, long)}, aber mit dem Schalter "Animationen
	 * reduzieren" (ROADMAP S9): ist er gesetzt, liefert
	 * {@link MotionSettings#hoverMs(boolean)} {@code 0} -- die Anhebung springt
	 * dann sofort auf den Zielwert, ohne Zwischenschritte (docs/UI-SPEC.md
	 * "Bewegung": "alles sofort, ohne Uebergang").
	 */
	public static float liftPx(boolean hovered, long elapsedMs, boolean reduceMotion) {
		int target = liftPx(hovered);
		int start = liftPx(!hovered);
		int durationMs = MotionSettings.hoverMs(reduceMotion);
		if (durationMs <= 0 || elapsedMs >= durationMs) {
			return target;
		}
		if (elapsedMs <= 0L) {
			return start;
		}
		float t = elapsedMs / (float) durationMs;
		float eased = 1f - (1f - t) * (1f - t);
		return start + (target - start) * eased;
	}
}
