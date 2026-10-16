package de.spassglas.vitrine.ui.state;

import de.spassglas.vitrine.i18n.Translations;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freie, wiederverwendbare Zustands-Komponente fuer den Store (ROADMAP
 * S7) -- eine Auspraegung je Zeile aus {@code docs/UI-SPEC.md} ("7. Zustaende").
 * Muster wie {@code ui.library.LibraryModel} / {@code ui.detail.PackageDetailModel}:
 * keine einzige Minecraft-Klasse, damit die Invariante "Bild, Ueberschrift, ein
 * Satz Erklaerung und <em>genau ein</em> Knopf" (Skeleton: <em>kein</em> Knopf,
 * kein Spinner) ohne laufenden Client beweisbar ist.
 *
 * <ul>
 *   <li>{@link #symbol()} -- immer gesetzt (Barrierefreiheit: kein Zustand nur
 *       ueber Farbe).</li>
 *   <li>{@link #headingKey()} + {@link #headingArgs()} -- genau eine
 *       Ueberschrift; Platzhalter (z. B. Suchbegriff, Standdatum, Paketname)
 *       werden geordnet als Argumente mitgefuehrt.</li>
 *   <li>{@link #explanationKey()} -- genau ein Erklaersatz.</li>
 *   <li>{@link #action()} -- {@link Optional#empty()} nur beim Lade-Skeleton,
 *       sonst genau eine {@link StoreStateActionId}.</li>
 *   <li>{@link #showsSpinner()} -- immer {@code false} (Charter-Regel 2 /
 *       Spec: "kein Spinner").</li>
 * </ul>
 */
public final class StoreStateView {

	/**
	 * Standardanzahl Platzhalter-Kacheln im Lade-Skeleton. Ein Inhalts-Default
	 * (wie {@code SearchModel.LIVE_MATCH_LIMIT}), keine Theme-Maszahl -- die
	 * tatsaechliche Kachelgroesse kommt aus {@code VitrineTheme}.
	 */
	public static final int DEFAULT_SKELETON_TILE_COUNT = 6;

	private final StoreState state;
	private final StateSymbol symbol;
	private final String headingKey;
	private final List<String> headingArgs;
	private final String explanationKey;
	private final Optional<StoreStateActionId> action;
	private final Optional<LocalDate> asOfDate;
	private final int skeletonTileCount;

	private StoreStateView(StoreState state, StateSymbol symbol, String headingKey, List<String> headingArgs,
			String explanationKey, Optional<StoreStateActionId> action, Optional<LocalDate> asOfDate,
			int skeletonTileCount) {
		this.state = state;
		this.symbol = symbol;
		this.headingKey = headingKey;
		this.headingArgs = List.copyOf(headingArgs);
		this.explanationKey = explanationKey;
		this.action = action;
		this.asOfDate = asOfDate;
		this.skeletonTileCount = skeletonTileCount;
	}

	// --- Fabriken, eine je Spec-Tabellenzeile ---------------------------

	/** "Noch nichts installiert" + Knopf "Zum Store" (docs/UI-SPEC.md "6. Bibliothek"). */
	public static StoreStateView libraryEmpty() {
		return new StoreStateView(StoreState.LIBRARY_EMPTY, StateSymbol.EMPTY,
				"vitrine.state.empty.title", List.of(),
				"vitrine.state.empty.description",
				Optional.of(StoreStateActionId.OPEN_STORE), Optional.empty(), 0);
	}

	/**
	 * "Nichts gefunden fuer &lt;Begriff&gt;" + Knopf "Suche loeschen". Der
	 * (getrimmte) Suchbegriff wird als Ueberschrift-Argument mitgefuehrt.
	 */
	public static StoreStateView searchNoResults(String searchTerm) {
		String term = searchTerm == null ? "" : searchTerm.trim();
		return new StoreStateView(StoreState.SEARCH_NO_RESULTS, StateSymbol.NO_RESULTS,
				"vitrine.state.no_results.title", List.of(term),
				"vitrine.state.no_results.description",
				Optional.of(StoreStateActionId.CLEAR_SEARCH), Optional.empty(), 0);
	}

	/**
	 * "Keine Verbindung -- du siehst den Stand von &lt;Datum&gt;" + Knopf
	 * "Erneut versuchen". Das Standdatum (Stand des zuletzt geladenen Cache)
	 * wird geordnet mitgefuehrt: als {@link #asOfDate()} und als
	 * ISO-Ueberschrift-Argument.
	 */
	public static StoreStateView offline(LocalDate cacheAsOf) {
		Objects.requireNonNull(cacheAsOf, "cacheAsOf");
		return new StoreStateView(StoreState.OFFLINE, StateSymbol.WARNING,
				"vitrine.state.offline.title", List.of(cacheAsOf.toString()),
				"vitrine.state.offline.description",
				Optional.of(StoreStateActionId.RETRY), Optional.of(cacheAsOf), 0);
	}

	/**
	 * "Keine Verbindung" <em>ohne</em> gecachten Katalog (ROADMAP K4): es gibt
	 * noch keinen Stand, also kein "Stand von &lt;Datum&gt;" -- kein erfundenes
	 * Datum (Charter-Regel 2). Reihe {@link StoreState#OFFLINE} wie der
	 * datierte Fall, aber {@link #asOfDate()} ist leer; der Store bleibt fuer
	 * lokale/installierte Pakete bedienbar. Knopf: "Erneut versuchen".
	 */
	public static StoreStateView offlineNoCache() {
		return new StoreStateView(StoreState.OFFLINE, StateSymbol.WARNING,
				"vitrine.state.offline_no_cache.title", List.of(),
				"vitrine.state.offline.description",
				Optional.of(StoreStateActionId.RETRY), Optional.empty(), 0);
	}

	/** "Katalog konnte nicht gelesen werden" + Knopf "Erneut versuchen". */
	public static StoreStateView catalogBroken() {
		return new StoreStateView(StoreState.CATALOG_BROKEN, StateSymbol.WARNING,
				"vitrine.state.catalog_broken.title", List.of(),
				"vitrine.state.catalog_broken.description",
				Optional.of(StoreStateActionId.RETRY), Optional.empty(), 0);
	}

	/**
	 * GATE40: Katalogdatei kaputt, aber ein frueherer Stand liegt im Cache.
	 * "Katalog gerade nicht lesbar -- du siehst den Stand von &lt;Datum&gt;" +
	 * Knopf "Erneut versuchen"; die gecachten Kacheln bleiben darunter
	 * bedienbar (wie bei {@link #offline(LocalDate)}), nur der Grund ist ein
	 * anderer und wird auch so benannt.
	 */
	public static StoreStateView catalogBrokenCached(LocalDate cacheAsOf) {
		Objects.requireNonNull(cacheAsOf, "cacheAsOf");
		return new StoreStateView(StoreState.CATALOG_BROKEN, StateSymbol.WARNING,
				"vitrine.state.catalog_broken_cached.title", List.of(cacheAsOf.toString()),
				"vitrine.state.catalog_broken_cached.description",
				Optional.of(StoreStateActionId.RETRY), Optional.of(cacheAsOf), 0);
	}

	/** "&lt;Name&gt; konnte nicht geladen werden" + Knopf "Details". */
	public static StoreStateView packageBroken(String packageName) {
		String name = packageName == null ? "" : packageName.trim();
		return new StoreStateView(StoreState.PACKAGE_BROKEN, StateSymbol.WARNING,
				"vitrine.state.package_broken.title", List.of(name),
				"vitrine.state.package_broken.description",
				Optional.of(StoreStateActionId.SHOW_PACKAGE_DETAILS), Optional.empty(), 0);
	}

	/** Lade-Skeleton mit {@link #DEFAULT_SKELETON_TILE_COUNT} Platzhalter-Kacheln, kein Knopf, kein Spinner. */
	public static StoreStateView loading() {
		return loading(DEFAULT_SKELETON_TILE_COUNT);
	}

	/** Lade-Skeleton mit {@code skeletonTileCount} Platzhalter-Kacheln (&ge; 1), kein Knopf, kein Spinner. */
	public static StoreStateView loading(int skeletonTileCount) {
		if (skeletonTileCount < 1) {
			throw new IllegalArgumentException("skeletonTileCount muss >= 1 sein, war " + skeletonTileCount);
		}
		return new StoreStateView(StoreState.LOADING, StateSymbol.SKELETON,
				"vitrine.state.loading.title", List.of(),
				"vitrine.state.loading.description",
				Optional.empty(), Optional.empty(), skeletonTileCount);
	}

	// --- Zugriff -------------------------------------------------------

	public StoreState state() {
		return state;
	}

	/** Symbol-Kennung, nie {@code null} (Barrierefreiheit). */
	public StateSymbol symbol() {
		return symbol;
	}

	/** Uebersetzungs-Key der einen Ueberschrift. */
	public String headingKey() {
		return headingKey;
	}

	/** Geordnete Platzhalter-Argumente der Ueberschrift (Suchbegriff / Standdatum / Paketname), evtl. leer. */
	public List<String> headingArgs() {
		return headingArgs;
	}

	/** Uebersetzungs-Key des einen Erklaersatzes. */
	public String explanationKey() {
		return explanationKey;
	}

	/** Die eine Aktion, oder {@link Optional#empty()} beim Lade-Skeleton. */
	public Optional<StoreStateActionId> action() {
		return action;
	}

	public boolean hasAction() {
		return action.isPresent();
	}

	/** Nur beim {@link StoreState#OFFLINE}: Stand des zuletzt geladenen Katalog-Cache. */
	public Optional<LocalDate> asOfDate() {
		return asOfDate;
	}

	/** Ob dieser Zustand der Lade-Skeleton ist (Platzhalter-Kacheln statt Inhalt). */
	public boolean isSkeleton() {
		return state == StoreState.LOADING;
	}

	/** Immer {@code false}: die Spec verbietet einen Spinner ausdruecklich ("kein Spinner"). */
	public boolean showsSpinner() {
		return false;
	}

	/** Anzahl Platzhalter-Kacheln (nur beim Lade-Skeleton {@code > 0}, sonst {@code 0}). */
	public int skeletonTileCount() {
		return skeletonTileCount;
	}

	/** Aufgeloeste Ueberschrift in {@code locale}, mit eingesetzten {@link #headingArgs()} (fuer Tests/Diagnose). */
	public String heading(String locale) {
		String pattern = Translations.get(locale, headingKey);
		return headingArgs.isEmpty() ? pattern : String.format(pattern, headingArgs.toArray());
	}

	/** Aufgeloester Erklaersatz in {@code locale} (fuer Tests/Diagnose). */
	public String explanation(String locale) {
		return Translations.get(locale, explanationKey);
	}
}
