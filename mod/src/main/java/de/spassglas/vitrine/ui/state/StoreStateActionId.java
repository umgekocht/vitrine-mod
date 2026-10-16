package de.spassglas.vitrine.ui.state;

import de.spassglas.vitrine.i18n.Translations;

/**
 * Die eine Aktion (Knopf) eines Store-Zustands aus {@code docs/UI-SPEC.md}
 * ("7. Zustaende"): jede Kennung traegt ihren Beschriftungs-Key. Ein
 * {@link StoreStateView} hat <em>0 oder genau 1</em> davon -- der Lade-Skeleton
 * hat keine, alle uebrigen fuenf Zustaende genau eine.
 */
public enum StoreStateActionId {

	/** Bibliothek leer -> "Zum Store". */
	OPEN_STORE("vitrine.state.empty.button"),

	/** Suche ohne Treffer -> "Suche loeschen". */
	CLEAR_SEARCH("vitrine.state.no_results.button"),

	/** Kein Netz / Katalog kaputt -> "Erneut versuchen". */
	RETRY("vitrine.state.retry"),

	/** Paket kaputt -> "Details". */
	SHOW_PACKAGE_DETAILS("vitrine.state.package_broken.button");

	private final String labelKey;

	StoreStateActionId(String labelKey) {
		this.labelKey = labelKey;
	}

	/** Uebersetzungs-Key der Knopfbeschriftung (fuer {@code Text.translatable} im Screen). */
	public String labelKey() {
		return labelKey;
	}

	/** Klartext der Knopfbeschriftung in {@code locale} (fuer Tests/Diagnose, siehe {@link Translations}). */
	public String describe(String locale) {
		return Translations.get(locale, labelKey);
	}
}
