package de.spassglas.vitrine.ui.state;

/**
 * Symbol-Kennung eines Store-Zustands. {@code docs/UI-SPEC.md}
 * ("Barrierefreiheit") verlangt: "Kein Zustand nur ueber Farbe -- immer
 * zusaetzlich Symbol oder Text." Darum traegt <em>jeder</em>
 * {@link StoreStateView} genau eine dieser Kennungen (nie {@code null}); der
 * Screen bildet sie auf ein Icon-Asset ab, das client-freie Modell nennt nur
 * die Kennung.
 */
public enum StateSymbol {

	/** Leerer Bereich (Bibliothek leer) -- Bild {@code gui/empty_state.png}. */
	EMPTY,

	/** Suche ohne Treffer -- Bild {@code gui/empty_state.png} (ASSETS-NEEDED.md: geteilt mit "leer"). */
	NO_RESULTS,

	/** Kein Netz / Katalog kaputt / Paket kaputt -- Warnzeichen {@code gui/icons/ui_warning.png}. */
	WARNING,

	/** Lade-Skeleton -- Platzhalter-Kacheln statt eines einzelnen Symbols, kein Spinner. */
	SKELETON
}
