package de.spassglas.vitrine.ui.nav;

import de.spassglas.vitrine.ui.theme.VitrineTheme;

/**
 * Die drei Nav-Rail-Zustaende aus {@code docs/UI-SPEC.md} ("Raster und
 * Masse", Umbruchpunkte), abhaengig allein von der Fensterbreite. Reine,
 * ohne Minecraft-Klassen testbare Logik -- {@link #forWidth(int)} ist die
 * Breakpoint-Entscheidung, die {@code NavRailController} und die Tests
 * benutzen.
 */
public enum NavRailLayout {
	/** Voll ausgeklappt, Icon + Text, siehe {@link VitrineTheme#NAV_WIDTH_EXPANDED}. */
	EXPANDED,
	/** Nur Icons, Text als Tooltip, siehe {@link VitrineTheme#NAV_WIDTH_COMPACT}. */
	COMPACT,
	/** Ausgeblendet, klappt bei Bedarf als Ueberlagerung ueber den Inhalt. */
	OVERLAY;

	/**
	 * Umbruchpunkte laut {@code docs/UI-SPEC.md}: unter
	 * {@link VitrineTheme#BREAKPOINT_NAV_COMPACT} px wird die Nav-Rail
	 * kompakt, unter {@link VitrineTheme#BREAKPOINT_NAV_OVERLAY} px zur
	 * Ueberlagerung. "Unter" ist strikt kleiner -- bei exakt 700 bzw. 1000 px
	 * gilt noch der jeweils breitere Zustand.
	 */
	public static NavRailLayout forWidth(int windowWidth) {
		if (windowWidth < VitrineTheme.BREAKPOINT_NAV_OVERLAY) {
			return OVERLAY;
		}
		if (windowWidth < VitrineTheme.BREAKPOINT_NAV_COMPACT) {
			return COMPACT;
		}
		return EXPANDED;
	}
}
