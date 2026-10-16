package de.spassglas.vitrine.ui.theme;

/**
 * Benannter Zugriff auf die <b>Farb</b>-Tokens des aktuell aktiven Themes
 * ({@link ThemeService#tokens()}) -- damit ein Laufzeit-Theme-Wechsel (ROADMAP
 * T4) jede Store-Flaeche sofort umfaerbt und in den Bildschirmen/Widgets keine
 * rohen Token-String-Literale ({@code "color.bg"} &hellip;) verstreut werden.
 *
 * <p>Jede Methode liest bei jedem Aufruf frisch aus {@link ThemeService#tokens()}
 * -- der Wert wird <em>nie</em> in ein Feld gecacht. Ein {@code Screen}, dessen
 * Aufbau Farben festhaelt, meldet sich zusaetzlich per
 * {@link ThemeManager#addRepaintListener} an und baut sich beim Wechsel neu auf
 * (Muster wie {@code StoreScreen}); Widgets lesen pro {@code render(...)}.</p>
 *
 * <p><b>Nur Farben.</b> Masze, Radien, Blur und Schriftgroeszen bleiben vorerst
 * statisch bei {@link VitrineTheme}; die bildet weiterhin die Default-Quelle,
 * aus der {@link DefaultThemeTokens} jeden Token ableitet.</p>
 */
public final class ActiveThemeColors {

	private ActiveThemeColors() {
	}

	/** Seiten-/Fensterhintergrund ({@code color.bg}). */
	public static int bg() {
		return color("color.bg");
	}

	/** Deckende Flaeche (Kopfleiste, Karten, Panels) ({@code color.surfaceSolid}). */
	public static int surfaceSolid() {
		return color("color.surfaceSolid");
	}

	/** Durchscheinende Flaeche (Hover, Zonen) ({@code color.surfaceTranslucent}). */
	public static int surfaceTranslucent() {
		return color("color.surfaceTranslucent");
	}

	/** Durchscheinende Trennlinie/Rahmen ({@code color.borderTranslucent}). */
	public static int borderTranslucent() {
		return color("color.borderTranslucent");
	}

	/** Abdunkelnder Schleier hinter Overlays/Dialogen ({@code color.overlayScrim}). */
	public static int overlayScrim() {
		return color("color.overlayScrim");
	}

	/** Vordergrund-/Textfarbe ({@code color.text}). */
	public static int text() {
		return color("color.text");
	}

	/** Gedaempfte Text-/Sekundaerfarbe ({@code color.textMuted}). */
	public static int textMuted() {
		return color("color.textMuted");
	}

	/** Die eine Akzentfarbe ({@code color.accent}). */
	public static int accent() {
		return color("color.accent");
	}

	/** Kontrastfarbe auf {@link #accent()} ({@code color.accentContrast}). */
	public static int accentContrast() {
		return color("color.accentContrast");
	}

	/** Text eines deaktivierten Bedienelements ({@code color.disabledText}). */
	public static int disabledText() {
		return color("color.disabledText");
	}

	/** Warn-/Gefahrfarbe ({@code color.danger}). */
	public static int danger() {
		return color("color.danger");
	}

	/** Farbe des Tastatur-Fokusrings ({@code focus.ring.color}). */
	public static int focusRing() {
		return color("focus.ring.color");
	}

	/** HUD-Textfarbe, Rueckfall ohne Paketvorgabe ({@code hud.text.color}). */
	public static int hudText() {
		return color("hud.text.color");
	}

	/** Ruecklauffarbe (leerer Teil) eines HUD-Balkens ({@code hud.bar.track.color}). */
	public static int hudBarTrack() {
		return color("hud.bar.track.color");
	}

	/** Fuellfarbe eines HUD-Balkens, Rueckfall ohne Paketvorgabe ({@code hud.bar.fill.color}). */
	public static int hudBarFill() {
		return color("hud.bar.fill.color");
	}

	private static int color(String token) {
		return ThemeService.tokens().color(token);
	}
}
