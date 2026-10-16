package de.spassglas.vitrine.ui.focus;

import de.spassglas.vitrine.ui.nav.NavRailLayout;
import org.testng.annotations.Test;

import java.util.List;

import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.BACK;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.CONTENT_PLACEHOLDER;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.DOWNLOADS_BUTTON;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_COSMETICS;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_HUD;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_LIBRARY;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_SETTINGS;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_SHADER;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_SOUND_LANGUAGE;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_START;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_TEXTURES;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_THEMES;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.NAV_TOGGLE;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.SEARCH_FIELD;
import static de.spassglas.vitrine.ui.focus.StoreFocusTarget.SETTINGS_BUTTON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist "Tab in Leserichtung" (docs/UI-SPEC.md, "Barrierefreiheit") als
 * reine, von Minecraft unabhaengige Reihenfolge fuer alle in ROADMAP S0
 * relevanten Kombinationen aus Layout, Rueckkehr-Verfuegbarkeit und
 * Ueberlagerungs-Zustand.
 */
public final class StoreFocusOrderTest {

	@Test
	public void expandedWithoutBackHasNoToggleOrBackAndFullNavList() {
		List<StoreFocusTarget> order = StoreFocusOrder.compute(false, NavRailLayout.EXPANDED, false);

		assertEquals(order, List.of(
				SEARCH_FIELD, SETTINGS_BUTTON, DOWNLOADS_BUTTON,
				NAV_START, NAV_TEXTURES, NAV_THEMES, NAV_HUD, NAV_SOUND_LANGUAGE,
				NAV_COSMETICS, NAV_SHADER, NAV_LIBRARY, NAV_SETTINGS,
				CONTENT_PLACEHOLDER));
	}

	@Test
	public void expandedWithBackInsertsBackBeforeSearch() {
		List<StoreFocusTarget> order = StoreFocusOrder.compute(true, NavRailLayout.EXPANDED, false);

		assertEquals(order.get(0), BACK);
		assertEquals(order.get(1), SEARCH_FIELD);
		assertFalse(order.contains(NAV_TOGGLE), "ausgeklappte Rail braucht keinen Umschalter");
	}

	@Test
	public void compactLayoutStillShowsFullNavListNoToggle() {
		List<StoreFocusTarget> order = StoreFocusOrder.compute(false, NavRailLayout.COMPACT, false);

		assertFalse(order.contains(NAV_TOGGLE), "kompakte Rail ist dauerhaft sichtbar, kein Umschalter noetig");
		assertTrue(order.contains(NAV_START));
		assertTrue(order.contains(NAV_SETTINGS));
	}

	@Test
	public void overlayClosedHidesNavItemsButKeepsToggleAndHeader() {
		List<StoreFocusTarget> order = StoreFocusOrder.compute(false, NavRailLayout.OVERLAY, false);

		assertEquals(order, List.of(NAV_TOGGLE, SEARCH_FIELD, SETTINGS_BUTTON, DOWNLOADS_BUTTON, CONTENT_PLACEHOLDER));
	}

	@Test
	public void overlayOpenInsertsNavItemsAfterHeaderControls() {
		List<StoreFocusTarget> order = StoreFocusOrder.compute(true, NavRailLayout.OVERLAY, true);

		assertEquals(order, List.of(
				NAV_TOGGLE, BACK, SEARCH_FIELD, SETTINGS_BUTTON, DOWNLOADS_BUTTON,
				NAV_START, NAV_TEXTURES, NAV_THEMES, NAV_HUD, NAV_SOUND_LANGUAGE,
				NAV_COSMETICS, NAV_SHADER, NAV_LIBRARY, NAV_SETTINGS,
				CONTENT_PLACEHOLDER));
	}

	@Test
	public void contentPlaceholderIsAlwaysLast() {
		for (NavRailLayout layout : NavRailLayout.values()) {
			for (boolean overlayOpen : new boolean[] {true, false}) {
				for (boolean back : new boolean[] {true, false}) {
					List<StoreFocusTarget> order = StoreFocusOrder.compute(back, layout, overlayOpen);
					assertEquals(order.get(order.size() - 1), CONTENT_PLACEHOLDER,
							"Kombination layout=" + layout + " overlayOpen=" + overlayOpen + " back=" + back);
				}
			}
		}
	}
}
