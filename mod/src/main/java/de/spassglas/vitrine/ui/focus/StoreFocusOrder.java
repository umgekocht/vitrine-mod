package de.spassglas.vitrine.ui.focus;

import de.spassglas.vitrine.ui.nav.NavRailLayout;

import java.util.ArrayList;
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

/**
 * Berechnet die Tab-Reihenfolge des Store-Layout-Geruests in Leserichtung
 * (docs/UI-SPEC.md, "Barrierefreiheit": "Tab in Leserichtung"), reine
 * Funktion ohne Minecraft-Abhaengigkeit -- {@code StoreScreen} baut seine
 * Widgets in genau dieser Reihenfolge per {@code addDrawableChild} auf, damit
 * Minecrafts eingebaute Tab-Zyklus-Reihenfolge (Einfuegereihenfolge) damit
 * uebereinstimmt.
 *
 * <p>Reihenfolge: zuerst die Kopfleiste (Ueberlagerungs-Umschalter, falls die
 * Nav-Rail als Ueberlagerung existiert; Zurueck-Pfeil, falls es eine
 * Rueckkehr gibt; Suche; Einstellungen; Downloads), dann die Nav-Rail-Eintraege
 * (nur wenn sie gerade sichtbar sind -- bei einer geschlossenen Ueberlagerung
 * sind sie es nicht), zuletzt der Inhaltsbereich.</p>
 */
public final class StoreFocusOrder {

	private StoreFocusOrder() {
	}

	public static List<StoreFocusTarget> compute(boolean backAvailable, NavRailLayout layout, boolean overlayOpen) {
		List<StoreFocusTarget> order = new ArrayList<>();

		if (layout == NavRailLayout.OVERLAY) {
			order.add(NAV_TOGGLE);
		}
		if (backAvailable) {
			order.add(BACK);
		}
		order.add(SEARCH_FIELD);
		order.add(SETTINGS_BUTTON);
		order.add(DOWNLOADS_BUTTON);

		boolean navVisible = layout != NavRailLayout.OVERLAY || overlayOpen;
		if (navVisible) {
			order.add(NAV_START);
			order.add(NAV_TEXTURES);
			order.add(NAV_THEMES);
			order.add(NAV_HUD);
			order.add(NAV_SOUND_LANGUAGE);
			order.add(NAV_COSMETICS);
			order.add(NAV_SHADER);
			order.add(NAV_LIBRARY);
			order.add(NAV_SETTINGS);
		}

		order.add(CONTENT_PLACEHOLDER);

		return List.copyOf(order);
	}
}
