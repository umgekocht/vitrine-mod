package de.spassglas.vitrine.ui.state;

/**
 * Die sechs Zustaende des Store-Inhaltsbereichs aus {@code docs/UI-SPEC.md}
 * ("7. Zustaende") -- eine Zeile der dortigen Tabelle je Konstante (ROADMAP
 * S7). Jeder Zustand hat ein Symbol, eine Ueberschrift, einen Erklaersatz und
 * <em>0 oder genau 1</em> Aktion; ausgepraegt wird das in {@link StoreStateView}.
 */
public enum StoreState {

	/** "Noch nichts installiert" -- die Bibliothek ist leer (docs/UI-SPEC.md "6. Bibliothek"). */
	LIBRARY_EMPTY,

	/** "Nichts gefunden fuer &lt;Begriff&gt;" -- die Suche liefert keine Treffer. */
	SEARCH_NO_RESULTS,

	/** "Keine Verbindung -- du siehst den Stand von &lt;Datum&gt;" -- kein Netz, Cache-Ansicht. */
	OFFLINE,

	/** "Katalog konnte nicht gelesen werden" -- die Katalogdatei ist beschaedigt/unvollstaendig. */
	CATALOG_BROKEN,

	/** "&lt;Name&gt; konnte nicht geladen werden" -- ein einzelnes Paket ist kaputt. */
	PACKAGE_BROKEN,

	/** Laedt: Platzhalter-Kacheln (Skeleton), kein Spinner, kein Knopf. */
	LOADING
}
