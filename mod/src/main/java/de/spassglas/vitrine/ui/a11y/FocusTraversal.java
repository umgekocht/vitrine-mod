package de.spassglas.vitrine.ui.a11y;

import de.spassglas.vitrine.ui.nav.NavRailLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Die vollstaendige, client-freie Fokus-Reihenfolge des Stores in Leserichtung
 * (docs/UI-SPEC.md "Barrierefreiheit": "Alles per Tastatur erreichbar (Tab in
 * Leserichtung, Pfeiltasten innerhalb einer Reihe, ESC schliesst)").
 *
 * <p>Waehrend {@code StoreFocusOrder} nur das grobe Layout-Geruest ordnet,
 * modelliert diese Klasse <em>jedes</em> aktionierbare Element eines konkreten
 * Store-Zustands: Kopfleiste, Vorschlagsfeld-Zeilen, Nav-Rail-Eintraege,
 * Hero-Karussell (Pfeile + Punkte), jede Kachel jeder Kachelreihe und die
 * Zeilen eines offenen Download-Flyouts. Damit ist das Fertig-Kriterium von
 * ROADMAP S9 ("Store einmal vollstaendig ohne Maus bedienbar") als reiner,
 * headless-Test pruefbar.</p>
 *
 * <ul>
 *   <li>{@link #order(StoreState)} -- die flache Tab-Reihenfolge; jeder
 *       {@link FocusStop} kommt genau einmal vor.</li>
 *   <li>{@link #tabMove(StoreState, String, int)} -- {@code Tab}/{@code Shift+Tab}
 *       (zyklisch), springt am Reihenende zur naechsten Reihe.</li>
 *   <li>{@link #arrowMove(StoreState, String, int)} -- Pfeiltasten, nur
 *       innerhalb derselben Reihe/Gruppe, an den Enden geklemmt.</li>
 *   <li>{@link #dismissOrder(StoreState)} -- die Reihenfolge, in der wiederholtes
 *       {@code ESC} Overlays und zuletzt den Bildschirm schliesst (umgekehrt zur
 *       Oeffnungsreihenfolge).</li>
 * </ul>
 */
public final class FocusTraversal {

	/** Gruppenschluessel der Kopfleiste (Zurueck / Suche / Einstellungen / Downloads / Menue-Umschalter). */
	public static final String GROUP_HEADER = "header";
	/** Gruppenschluessel der Vorschlagsfeld-Zeilen (letzte Suchen + Live-Treffer). */
	public static final String GROUP_SUGGESTIONS = "suggestions";
	/** Gruppenschluessel der Nav-Rail. */
	public static final String GROUP_NAV_RAIL = "navrail";
	/** Gruppenschluessel des Hero-Karussells (Pfeile + Punkte). */
	public static final String GROUP_HERO = "hero";
	/** Gruppenschluessel einer Kachelreihe {@code i}: {@code "tilerow." + i}. */
	public static String tileRowGroup(int rowIndex) {
		return "tilerow." + rowIndex;
	}
	/** Gruppenschluessel der Download-Flyout-Zeilen. */
	public static final String GROUP_FLYOUT = "flyout";

	private FocusTraversal() {
	}

	/** Was {@code ESC} in welcher Reihenfolge schliesst (umgekehrt zur Oeffnungsreihenfolge). */
	public enum DismissLayer {
		/** Zuletzt geoeffnet, zuerst geschlossen: das Vorschlagsfeld unter der Suche. */
		SUGGESTIONS,
		/** Das Download-Flyout unter der Kopfleiste. */
		DOWNLOADS_FLYOUT,
		/** Die Nav-Rail-Ueberlagerung (nur im schmalen Fenster). */
		NAV_OVERLAY,
		/** Nichts mehr offen: {@code ESC} verlaesst den Store (Zurueck/Schliessen). */
		SCREEN
	}

	/** Ein einzelner Tab-Stopp mit seiner Reihen-/Gruppenzugehoerigkeit (fuer die Pfeiltasten). */
	public record FocusStop(String id, String group) {
		public FocusStop {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(group, "group");
		}
	}

	/**
	 * Beschreibung eines konkreten Store-Zustands, aus dem sich die
	 * Fokus-Reihenfolge ergibt. Reine Daten -- {@code StoreScreen} fuellt sie
	 * aus seinem aktuellen Aufbau, der Test aus einem repraesentativen Beispiel.
	 *
	 * @param backAvailable          ob es eine Rueckkehr (Zurueck-Pfeil) gibt
	 * @param navLayout              Breakpoint-Zustand der Nav-Rail
	 * @param navOverlayOpen         ob die Nav-Rail-Ueberlagerung offen ist
	 * @param heroSlideCount         Anzahl Karussell-Eintraege (0 = kein Hero sichtbar)
	 * @param tileRowSizes           Kachelanzahl je Inhalts-Kachelreihe, in Reihenfolge
	 * @param downloadsFlyoutOpen    ob das Download-Flyout offen ist
	 * @param downloadsFlyoutRowCount Zeilen im Flyout (je Zeile = ein Abbrechen/Schliessen-Ziel)
	 * @param suggestionsOpen        ob das Vorschlagsfeld offen ist
	 * @param suggestionRowCount     Zeilen im Vorschlagsfeld
	 */
	public record StoreState(
			boolean backAvailable,
			NavRailLayout navLayout,
			boolean navOverlayOpen,
			int heroSlideCount,
			List<Integer> tileRowSizes,
			boolean downloadsFlyoutOpen,
			int downloadsFlyoutRowCount,
			boolean suggestionsOpen,
			int suggestionRowCount) {

		public StoreState {
			Objects.requireNonNull(navLayout, "navLayout");
			tileRowSizes = tileRowSizes == null ? List.of() : List.copyOf(tileRowSizes);
			if (heroSlideCount < 0) {
				throw new IllegalArgumentException("heroSlideCount darf nicht negativ sein: " + heroSlideCount);
			}
			if (downloadsFlyoutRowCount < 0 || suggestionRowCount < 0) {
				throw new IllegalArgumentException("Zeilenanzahl darf nicht negativ sein");
			}
			for (int size : tileRowSizes) {
				if (size < 0) {
					throw new IllegalArgumentException("Kachelanzahl darf nicht negativ sein: " + size);
				}
			}
		}

		private boolean navVisible() {
			return navLayout != NavRailLayout.OVERLAY || navOverlayOpen;
		}

		private boolean contentVisible() {
			// Bei offener Nav-Rail-Ueberlagerung liegt der Inhaltsbereich verdeckt
			// darunter -- StoreScreen baut ihn dann gar nicht auf.
			return !navOverlayOpen;
		}
	}

	/**
	 * Die flache Tab-Reihenfolge in Leserichtung. Jeder Eintrag kommt genau
	 * einmal vor.
	 */
	public static List<FocusStop> order(StoreState state) {
		Objects.requireNonNull(state, "state");
		List<FocusStop> stops = new ArrayList<>();

		// --- Kopfleiste ---
		if (state.navLayout() == NavRailLayout.OVERLAY) {
			stops.add(new FocusStop("header.navToggle", GROUP_HEADER));
		}
		if (state.backAvailable()) {
			stops.add(new FocusStop("header.back", GROUP_HEADER));
		}
		stops.add(new FocusStop("header.search", GROUP_HEADER));
		// Das Vorschlagsfeld haengt an der Suche -- direkt danach, eigene Reihe.
		if (state.suggestionsOpen()) {
			for (int i = 0; i < state.suggestionRowCount(); i++) {
				stops.add(new FocusStop("suggestion." + i, GROUP_SUGGESTIONS));
			}
		}
		stops.add(new FocusStop("header.settings", GROUP_HEADER));
		stops.add(new FocusStop("header.downloads", GROUP_HEADER));

		// --- Nav-Rail ---
		if (state.navVisible()) {
			for (String id : NAV_RAIL_IDS) {
				stops.add(new FocusStop(id, GROUP_NAV_RAIL));
			}
		}

		// --- Inhaltsbereich ---
		if (state.contentVisible()) {
			if (state.heroSlideCount() > 0) {
				stops.add(new FocusStop("hero.prev", GROUP_HERO));
				for (int i = 0; i < state.heroSlideCount(); i++) {
					stops.add(new FocusStop("hero.dot." + i, GROUP_HERO));
				}
				stops.add(new FocusStop("hero.next", GROUP_HERO));
			}
			List<Integer> rows = state.tileRowSizes();
			for (int r = 0; r < rows.size(); r++) {
				String group = tileRowGroup(r);
				for (int t = 0; t < rows.get(r); t++) {
					stops.add(new FocusStop(group + ".tile." + t, group));
				}
			}
		}

		// --- Download-Flyout ---
		if (state.downloadsFlyoutOpen() && state.contentVisible()) {
			for (int i = 0; i < state.downloadsFlyoutRowCount(); i++) {
				stops.add(new FocusStop("flyout.row." + i, GROUP_FLYOUT));
			}
		}

		return List.copyOf(stops);
	}

	private static final List<String> NAV_RAIL_IDS = List.of(
			"nav.start", "nav.textures", "nav.themes", "nav.hud", "nav.soundLanguage",
			"nav.cosmetics", "nav.shader", "nav.library", "nav.settings");

	/**
	 * {@code Tab} ({@code direction > 0}) bzw. {@code Shift+Tab}
	 * ({@code direction < 0}) ausgehend von {@code currentId}. Zyklisch: hinter
	 * dem letzten Stopp kommt wieder der erste. Ist {@code currentId} unbekannt,
	 * beginnt die Reihe vorne bzw. hinten.
	 */
	public static Optional<FocusStop> tabMove(StoreState state, String currentId, int direction) {
		List<FocusStop> stops = order(state);
		if (stops.isEmpty() || direction == 0) {
			return Optional.empty();
		}
		int step = direction > 0 ? 1 : -1;
		int index = indexOf(stops, currentId);
		if (index < 0) {
			return Optional.of(step > 0 ? stops.get(0) : stops.get(stops.size() - 1));
		}
		int next = Math.floorMod(index + step, stops.size());
		return Optional.of(stops.get(next));
	}

	/**
	 * Pfeiltaste innerhalb derselben Reihe/Gruppe ({@code direction > 0} = weiter,
	 * {@code direction < 0} = zurueck). An den Enden geklemmt: steht der Fokus
	 * bereits am Rand seiner Reihe oder gehoert {@code currentId} zu keiner
	 * mehrelementigen Reihe, liefert die Methode {@link Optional#empty()} (nichts
	 * bewegt sich, {@code Tab} muss die Reihe wechseln).
	 */
	public static Optional<FocusStop> arrowMove(StoreState state, String currentId, int direction) {
		if (direction == 0) {
			return Optional.empty();
		}
		List<FocusStop> stops = order(state);
		int index = indexOf(stops, currentId);
		if (index < 0) {
			return Optional.empty();
		}
		String group = stops.get(index).group();
		int step = direction > 0 ? 1 : -1;
		int target = index + step;
		if (target < 0 || target >= stops.size()) {
			return Optional.empty();
		}
		FocusStop candidate = stops.get(target);
		if (!candidate.group().equals(group)) {
			return Optional.empty();
		}
		return Optional.of(candidate);
	}

	/**
	 * Die Reihenfolge, in der wiederholtes {@code ESC} schliesst -- umgekehrt zur
	 * Oeffnungsreihenfolge (docs/UI-SPEC.md "Barrierefreiheit": "ESC schliesst").
	 * Der letzte Eintrag ist immer {@link DismissLayer#SCREEN}: ist nichts (mehr)
	 * offen, verlaesst {@code ESC} den Store.
	 */
	public static List<DismissLayer> dismissOrder(StoreState state) {
		Objects.requireNonNull(state, "state");
		List<DismissLayer> layers = new ArrayList<>();
		if (state.suggestionsOpen()) {
			layers.add(DismissLayer.SUGGESTIONS);
		}
		if (state.downloadsFlyoutOpen()) {
			layers.add(DismissLayer.DOWNLOADS_FLYOUT);
		}
		if (state.navOverlayOpen()) {
			layers.add(DismissLayer.NAV_OVERLAY);
		}
		layers.add(DismissLayer.SCREEN);
		return List.copyOf(layers);
	}

	private static int indexOf(List<FocusStop> stops, String id) {
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}
