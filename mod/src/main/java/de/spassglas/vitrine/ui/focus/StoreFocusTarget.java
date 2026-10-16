package de.spassglas.vitrine.ui.focus;

/**
 * Alle Tab-stoppbaren Bereiche des Store-Layout-Geruests, in der Reihenfolge,
 * in der {@link StoreFocusOrder} sie in Leserichtung anordnet. Ein Eintrag
 * hier ist nicht zwingend ein einzelnes Minecraft-Widget -- {@code
 * CONTENT_PLACEHOLDER} steht fuer den (in S0 noch leeren) Inhaltsbereich, der
 * erst ab S1 wirkliche, fokussierbare Kacheln bekommt.
 */
public enum StoreFocusTarget {
	NAV_TOGGLE,
	BACK,
	SEARCH_FIELD,
	SETTINGS_BUTTON,
	DOWNLOADS_BUTTON,
	NAV_START,
	NAV_TEXTURES,
	NAV_THEMES,
	NAV_HUD,
	NAV_SOUND_LANGUAGE,
	NAV_COSMETICS,
	NAV_SHADER,
	NAV_LIBRARY,
	NAV_SETTINGS,
	CONTENT_PLACEHOLDER
}
