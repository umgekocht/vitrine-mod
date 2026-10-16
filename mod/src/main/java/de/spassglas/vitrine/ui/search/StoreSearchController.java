package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client-freier Zustand der Store-Suche (ROADMAP S6): haelt Katalog,
 * Verlauf, Kategorie-Auswahl, den Filter „ohne Neustart", die Sortierung
 * sowie den laufenden Live-Text und den abgeschickten Suchtext zusammen.
 * {@code StoreScreen} bindet nur Zeichnen und Eingabe daran -- die gesamte
 * Entscheidungslogik ist hier ohne laufenden Client testbar (Muster wie
 * {@code ui.library.LibraryModel}).
 *
 * <ul>
 *   <li><b>Live-Text</b> ({@link #setLiveText}) speist das Vorschlagsfeld,
 *       wird <em>nicht</em> in den Verlauf aufgenommen.</li>
 *   <li><b>Abschicken</b> ({@link #submit}) nimmt den getrimmten Begriff in
 *       den Verlauf auf und macht ihn zum Ergebnis-Filter.</li>
 *   <li>{@link #results()} liefert die Kachelmenge fuer den Inhaltsbereich:
 *       aktive Suche (falls vorhanden) plus Kategorie- und „ohne
 *       Neustart"-Filter, in der gewaehlten Sortierung.</li>
 * </ul>
 *
 * <p>ROADMAP J1: jede an die Oberflaeche gereichte Kachelmenge ({@link #results()}
 * und die Live-Treffer in {@link #suggestions()}) laeuft zuvor durch die
 * {@link JavaModOfferGate}. Ohne den Opt-in-Schalter „Java-Mods anzeigen"
 * erscheint in der Suche also kein Jar-Angebot -- ohne eigenes Konstruktor-
 * Argument gilt die immer ausblendende {@link JavaModOfferGate#hidden()}.</p>
 */
public final class StoreSearchController {

	private SearchModel model;
	private final JavaModOfferGate javaModOfferGate;
	private final RecentSearches recent = new RecentSearches();

	private Set<TilePackageType> categories = Set.of();
	private boolean hideRestartRequired = false;
	private SearchSort sort = SearchSort.NAME_ASC;
	private String liveText = "";
	private String submittedText = "";

	public StoreSearchController(List<TileData> catalog) {
		this(catalog, JavaModOfferGate.hidden());
	}

	public StoreSearchController(List<TileData> catalog, JavaModOfferGate javaModOfferGate) {
		this.model = new SearchModel(catalog);
		this.javaModOfferGate = Objects.requireNonNull(javaModOfferGate, "javaModOfferGate");
	}

	public int catalogSize() {
		return model.catalogSize();
	}

	/**
	 * ROADMAP GATE5: ersetzt die durchsuchte Kachelmenge (z. B. weil der
	 * Hintergrund-Katalogabruf inzwischen echte Kacheln geliefert hat), ohne
	 * Suchverlauf, Kategorie-Auswahl, Sortierung oder den laufenden/abge-
	 * schickten Suchtext zu verlieren -- {@code StoreScreen} ruft das bei
	 * jedem Aufbau erneut mit der frisch zusammengefuehrten Menge auf.
	 */
	public void setCatalog(List<TileData> catalog) {
		this.model = new SearchModel(catalog);
	}

	// --- Kategorie (Nav-Rail) ------------------------------------------

	/** Setzt die Kategorie-Einschraenkung; leere Menge = „Start" = keine Einschraenkung. */
	public void setCategories(Set<TilePackageType> newCategories) {
		this.categories = newCategories == null ? Set.of() : Set.copyOf(newCategories);
	}

	public Set<TilePackageType> categories() {
		return categories;
	}

	// --- Filter „ohne Neustart" --------------------------------------

	public boolean hideRestartRequired() {
		return hideRestartRequired;
	}

	public void setHideRestartRequired(boolean value) {
		this.hideRestartRequired = value;
	}

	/** Schaltet den Filter „ohne Neustart" um und liefert den neuen Zustand. */
	public boolean toggleHideRestartRequired() {
		hideRestartRequired = !hideRestartRequired;
		return hideRestartRequired;
	}

	// --- Sortierung --------------------------------------------------

	public SearchSort sort() {
		return sort;
	}

	public void setSort(SearchSort newSort) {
		this.sort = Objects.requireNonNull(newSort, "sort");
	}

	/** Schaltet zur naechsten Sortierung durch und liefert sie. */
	public SearchSort cycleSort() {
		sort = sort.next();
		return sort;
	}

	// --- Vorschlagsfeld -------------------------------------------------

	/** Aktualisiert den Live-Text des Vorschlagsfelds (kein Verlaufseintrag). */
	public void setLiveText(String text) {
		this.liveText = text == null ? "" : text;
	}

	public String liveText() {
		return liveText;
	}

	public List<String> recentSearches() {
		return recent.entries();
	}

	/** Inhalt des Vorschlagsfelds: letzte Suchen, dann Live-Treffer zum aktuellen Live-Text. */
	public SearchSuggestions suggestions() {
		List<TileData> live = javaModOfferGate.apply(model.liveMatches(liveText, SearchModel.LIVE_MATCH_LIMIT));
		return new SearchSuggestions(recent.entries(), live);
	}

	// --- Abschicken / Ergebnisse --------------------------------------

	/**
	 * Schickt {@code text} ab: getrimmt in den Verlauf aufnehmen und als
	 * Ergebnis-Filter setzen. Der Live-Text wird angeglichen.
	 */
	public void submit(String text) {
		String value = text == null ? "" : text.trim();
		submittedText = value;
		liveText = value;
		recent.record(value);
	}

	/** Uebernimmt einen Begriff aus den letzten Suchen als neue aktive Suche. */
	public void applyRecent(String recentText) {
		submit(recentText);
	}

	public String submittedText() {
		return submittedText;
	}

	/** Ob gerade ein nicht-leerer Suchbegriff aktiv ist (Inhaltsbereich zeigt dann Ergebnisse). */
	public boolean hasActiveSearch() {
		return !submittedText.isBlank();
	}

	/** Loescht die aktive Suche (Feld geleert / „Suche loeschen"). */
	public void clearActiveSearch() {
		submittedText = "";
		liveText = "";
	}

	/**
	 * Kachelmenge fuer den Inhaltsbereich: aktive Suche (falls vorhanden)
	 * plus Kategorie- und „ohne Neustart"-Filter, in der gewaehlten Sortierung.
	 */
	public List<TileData> results() {
		// ROADMAP J1: ohne den Opt-in-Schalter faellt hier jedes Klasse-B-Angebot heraus.
		return javaModOfferGate.apply(
				model.search(new SearchQuery(submittedText, categories, hideRestartRequired, sort)));
	}
}
