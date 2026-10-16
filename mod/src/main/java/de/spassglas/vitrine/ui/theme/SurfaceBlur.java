package de.spassglas.vitrine.ui.theme;

/**
 * Welche Unschaerfe hinter einem Screen liegt (Token {@code blur.surface}).
 *
 * <p>Vanilla zeichnet hinter Menues eine Unschaerfe in der Staerke der Option
 * "Menue-Hintergrundunschaerfe". Hinter Vitrine-Screens gilt stattdessen der
 * Wert des aktiven Themes -- ausser der Spieler hat die Unschaerfe in den
 * Optionen ganz abgeschaltet (0). Diese Entscheidung wird nie ueberstimmt,
 * sie kann eine Barrierefreiheits-Einstellung sein.</p>
 */
public final class SurfaceBlur {

	private SurfaceBlur() {
	}

	/**
	 * @param vanillaValue  Wert der Vanilla-Option
	 * @param vitrineScreen ob gerade ein Vitrine-Screen offen ist
	 * @param themeValue    {@code blur.surface} des aktiven Themes
	 */
	public static int radius(int vanillaValue, boolean vitrineScreen, int themeValue) {
		if (!vitrineScreen || vanillaValue <= 0) {
			return vanillaValue;
		}
		return Math.max(0, themeValue);
	}

	/** Ob {@code screen} zur Vitrine-Oberflaeche gehoert (nach Paket). */
	public static boolean isVitrineScreen(Object screen) {
		return screen != null && screen.getClass().getName().startsWith("de.spassglas.vitrine.");
	}
}
