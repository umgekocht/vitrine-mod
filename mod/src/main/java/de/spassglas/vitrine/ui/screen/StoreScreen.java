package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.config.SettingsModel;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.carousel.HeroCarouselModel;
import de.spassglas.vitrine.ui.carousel.HeroCarouselWidget;
import de.spassglas.vitrine.ui.detail.HumanReadableSize;
import de.spassglas.vitrine.ui.downloads.DownloadEntry;
import de.spassglas.vitrine.ui.downloads.DownloadFlyoutModel;
import de.spassglas.vitrine.ui.downloads.DownloadFlyoutService;
import de.spassglas.vitrine.ui.downloads.DownloadPhase;
import de.spassglas.vitrine.ui.nav.NavRailController;
import de.spassglas.vitrine.ui.widget.IconButtonWidget;
import de.spassglas.vitrine.ui.nav.NavRailEntry;
import de.spassglas.vitrine.ui.nav.NavRailLayout;
import de.spassglas.vitrine.ui.detail.CatalogPackageDetailSource;
import de.spassglas.vitrine.ui.detail.LocalPackageDetailSource;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.motion.PageEntrance;
import de.spassglas.vitrine.ui.search.CatalogSearchMerge;
import de.spassglas.vitrine.ui.search.NavRailCategories;
import de.spassglas.vitrine.ui.search.SearchSuggestions;
import de.spassglas.vitrine.ui.search.SearchSuggestionsLayout;
import de.spassglas.vitrine.ui.search.StoreSearchController;
import de.spassglas.vitrine.ui.catalog.StoreCatalogController;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.pack.InstalledPackVersions;
import de.spassglas.vitrine.ui.source.LocalLibrarySource;
import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.theme.FocusRing;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.ThemeTokens;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.DemoCatalog;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileLabel;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileRowLabels;
import de.spassglas.vitrine.ui.tile.TileRowWidget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Store-Layout-Geruest (ROADMAP S0): Kopfleiste mit Suche und
 * Download-Anzeige, Nav-Rail links (ausgeklappt/kompakt/ueberlagert je nach
 * Fensterbreite, siehe {@link NavRailController}), leerer Inhaltsbereich als
 * Platzhalter. Kacheln, Hero-Karussell und die Download-Flyout-Liste sind
 * spaetere Zeilen (S1/S8) -- hier geht es nur um Aufbau, Umbruchpunkte und
 * Tastatur-/Fokusfuehrung.
 *
 * <p>Farben liest dieser Bildschirm ueber den {@link ThemeService} (ROADMAP
 * T2): ein registrierter Umfaerb-Zuhoerer baut den Bildschirm bei einem
 * Theme-Wechsel neu auf, sodass er sofort umfaerbt. Fehlt im geladenen Theme
 * ein Token, liefert der {@code ThemeManager} den Wert des Standard-Themes
 * (aus {@link VitrineTheme}). Maszzahlen kommen weiterhin direkt aus
 * {@link VitrineTheme}. Die Reihenfolge, in
 * der Widgets per {@link #addDrawableChild(Element)} hinzugefuegt werden,
 * entspricht exakt {@code StoreFocusOrder.compute(...)} -- Minecrafts
 * eingebauter Tab-Zyklus folgt der Einfuegereihenfolge, siehe
 * {@code docs/UI-SPEC.md} ("Barrierefreiheit": "Tab in Leserichtung").</p>
 *
 * <p>ROADMAP S9: der Kopfleisten-Knopf "Einstellungen" und der Nav-Rail-Eintrag
 * "Einstellungen" oeffnen beide den {@link SettingsScreen} (Schalter
 * "Animationen reduzieren"). Der Schalter wird bei jedem Aufbau frisch von der
 * Platte gelesen ({@link #reduceMotion()}) und an Karussell und Kachelreihen
 * durchgereicht -- legt der {@link SettingsScreen} ihn um, uebernimmt der
 * offene Store das beim naechsten {@code render}/{@code rebuild}.</p>
 */
public final class StoreScreen extends Screen {

	/**
	 * Obergrenze der Zeichen im Suchfeld -- eine Eingabe-Schutzgrenze, kein
	 * Layout-Mass: lange Freitexte bringen keinen Mehrwert und ein Deckel
	 * haelt die Rangfolge-Schleife kurz.
	 */
	private static final int SEARCH_INPUT_MAX_LENGTH = 120;

	/** Lupensymbol vor jeder Zeile „letzte Suchen" im Vorschlagsfeld (ROADMAP S6). */
	private static final Identifier SUGGESTION_RECENT_ICON =
			Identifier.of("vitrine", "textures/gui/icons/ui_search.png");

	/** Zeilenhoehe im Vorschlagsfeld -- teilt sich Mass und Anmutung mit dem Menue-Knopf. */
	private static int suggestionRowHeight() {
		return VitrineTheme.MENU_BUTTON_HEIGHT;
	}

	private final Screen parentScreen;
	private static final Identifier ICON_MENU = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_overflow.png");
	private static final Identifier ICON_BACK = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_back.png");
	private static final Identifier ICON_DOWNLOAD = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_download.png");

	private final NavRailController navController = new NavRailController();
	private final Map<NavRailEntry, ButtonWidget> navButtons = new EnumMap<>(NavRailEntry.class);

	/** Bei einem Laufzeit-Theme-Wechsel neu aufbauen -> sofortige Umfaerbung (ROADMAP T2). */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	private NavRailEntry selectedEntry = NavRailEntry.START;
	private int currentNavWidth = VitrineTheme.NAV_WIDTH_EXPANDED;

	private TextFieldWidget searchField;
	/** Flaeche des Suchfelds (x, y, Breite, Hoehe); das Feld selbst ist rahmenlos darin. */
	private int[] searchBox;
	/**
	 * Wie weit der Inhaltsbereich nach oben gescrollt ist. Im Spiel gefunden
	 * (2026-10-06): die Startseite liess sich nicht scrollen, unter dem Hero
	 * war alles abgeschnitten und unerreichbar.
	 */
	/**
	 * ROADMAP GATE85: Zeitpunkt der Erzeugung dieses Bildschirms
	 * (docs/UI-SPEC.md "Bewegung": "250 ms fuer Seitenwechsel"). {@link
	 * PageEntrance} errechnet daraus in {@link #render} den Einblend-Versatz
	 * und die Abdeckungs-Deckkraft -- einmal je Screen-Erzeugung, nicht bei
	 * jedem {@link #rebuild()} (ein Nav-Klick innerhalb des Stores ist kein
	 * neuer Seitenwechsel im Sinn von "neu geoeffneter Bildschirm").
	 */
	private final long pageEntranceStartMs = Util.getMeasuringTimeMs();

	private int contentScrollY;
	/** Untere Kante des Inhalts ohne Scroll (nach jedem Aufbau gemessen). */
	private int contentBottom;
	private HeroCarouselWidget heroCarousel;
	/** ROADMAP X2: kuratierte Reihen aus dem echten Katalog -- {@code null}, wenn leer (Reihe entfaellt). */
	private TileRowWidget featuredRow;
	private TileRowWidget newRow;
	private TileRowWidget popularRow;
	private TileRowWidget startRow;
	private TileRowWidget libraryRow;
	private TileRowWidget searchRow;
	private ButtonWidget navToggleButton;
	private ButtonWidget backButton;
	private ButtonWidget settingsButton;
	private ButtonWidget downloadsButton;
	private ButtonWidget sortButton;
	private ButtonWidget restartFreeButton;

	/**
	 * Client-freier Such-Zustand (ROADMAP S6). Einmal je Store-Sitzung aus dem
	 * zusammengefuehrten Katalog gebaut und ueber {@link #rebuild()} hinweg
	 * behalten, damit „letzte Suchen" ein Abschicken ueberleben.
	 */
	private StoreSearchController searchController;
	private List<TileData> searchResults = List.of();
	private boolean suggestionsOpen;

	/**
	 * ROADMAP GATE5/GATE72: Ids der Kacheln, die in {@link #ensureSearchController()}
	 * aus {@code catalogController.searchTiles()} in die durchsuchte
	 * Kachelmenge gemischt wurden -- ein Treffer mit dieser Id oeffnet ueber
	 * {@link #openCatalogDetail(TileData, List)} statt {@link #openDetail(TileData, List)},
	 * sonst laedt die Detailseite aus der falschen (lokalen) Quelle.
	 */
	private Set<String> catalogSearchIds = Set.of();

	/**
	 * Client-freies Zeit-/Pause-/Index-Modell des Hero-Karussells (ROADMAP S8).
	 * Einmal je Store-Sitzung gebaut und ueber {@link #rebuild()} hinweg
	 * behalten, damit ein Theme-Wechsel oder Nav-Klick den 7-Sekunden-Takt
	 * nicht zuruecksetzt.
	 */
	private HeroCarouselModel heroCarouselModel;

	/**
	 * Client-freies Modell des Download-Flyouts (ROADMAP S8, docs/UI-SPEC.md
	 * "1. Kopfleiste"). ROADMAP GATE4/GATE9/GATE13: das EINE geteilte Modell
	 * ueber {@link DownloadFlyoutService} -- nicht mehr je Store-Sitzung neu
	 * gebaut, sonst waere ein von der Detailseite oder der Bibliothek
	 * angestossener Download hier nie sichtbar.
	 */
	private DownloadFlyoutModel downloadFlyoutModel;
	private boolean downloadsFlyoutOpen;

	/**
	 * ROADMAP K4: schliesst den echten Katalog-Client (K2) an den Store an und
	 * behandelt den Offline-Fall ehrlich. Einmal je Store-Sitzung gebaut; der
	 * blockierende {@link StoreCatalogController#refresh()} laeuft im Hintergrund
	 * ({@link #startCatalogRefresh()}), meldet danach den Zustand an
	 * {@link CatalogAvailability} und baut den Store auf dem Client-Thread neu
	 * auf. Der Katalog-Zustand betrifft nur den Katalog-Inhaltsbereich -- die
	 * lokale/installierte Reihe wird unabhaengig davon gebaut (Charter-Regel 12).
	 */
	private StoreCatalogController catalogController;
	private boolean catalogRefreshStarted;

	/**
	 * ROADMAP S7: Zustands-Panels des Inhaltsbereichs. {@code catalogStateView}
	 * (kein Netz / Katalog kaputt / Lade-Skeleton) kommt aus dem
	 * {@link CatalogAvailability}-Seam; {@code noResultsStateView} steht, wenn
	 * eine aktive Suche/Kategorie keine Treffer hat. Beide kommen aus der
	 * wiederverwendbaren Komponente -- eine Wahrheit.
	 */
	private StoreStateView catalogStateView;
	private StoreStateView noResultsStateView;

	/** @param parentScreen der Bildschirm, zu dem "Zurueck"/ESC zurueckkehrt, oder {@code null} ohne Rueckkehr. */
	public StoreScreen(Screen parentScreen) {
		super(Text.translatable("vitrine.store.title"));
		this.parentScreen = parentScreen;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	/** Farb-Tokens des aktiven Themes (mit Standard-Theme als Fallback je Token). */
	private static ThemeTokens theme() {
		return ThemeService.instance().active();
	}

	@Override
	public void removed() {
		ThemeService.instance().removeRepaintListener(themeRepaintListener);
		super.removed();
	}

	@Override
	protected void init() {
		navController.onWidthChanged(this.width);
		navButtons.clear();
		currentNavWidth = navController.layout() == NavRailLayout.COMPACT
				? VitrineTheme.NAV_WIDTH_COMPACT
				: VitrineTheme.NAV_WIDTH_EXPANDED;

		// ROADMAP GATE5/GATE72: ensureCatalogRefresh() muss vor ensureSearchController()
		// laufen -- der Suchaufbau merged catalogController.searchTiles() (search.json,
		// nicht nur die geladenen Index-Seiten) in die durchsuchte Kachelmenge, der
		// Controller muss dafuer bereits existieren (auch wenn der Hintergrund-Abruf
		// noch nichts geliefert hat).
		ensureCatalogRefresh();
		ensureSearchController();
		ensureDownloadFlyoutModel();
		buildHeader();
		buildNavRail();
		buildStartContent();
		buildSearchContent();
		measureContent();
	}

	/**
	 * Baut den {@link StoreSearchController} beim ersten Aufbau; bei jedem
	 * weiteren Aufbau wird nur die durchsuchte Kachelmenge aktualisiert (ueber
	 * {@link StoreSearchController#setCatalog(List)}) -- der Controller selbst
	 * bleibt bestehen, damit ein {@link #rebuild()} Suchverlauf, Sortierung
	 * und Kategorie-Auswahl nicht verliert.
	 *
	 * <p>ROADMAP GATE5/GATE72: die durchsuchte Menge ist die lokalen Bibliotheks-
	 * Angebote aus S2 <em>und</em> die Treffer aus dem flachen Suchindex
	 * ({@link StoreCatalogController#searchTiles()}, {@code search.json}) --
	 * nicht nur die geladenen Index-Seiten (docs/UI-SPEC.md "1. Kopfleiste":
	 * "Live-Treffer aus {@code search.json}"), nach Paket-ID entdoppelt.
	 * {@link #catalogSearchIds} merkt sich, welche Ids aus dem echten Katalog
	 * stammen, damit ein Klick auf einen Katalog-Treffer ueber
	 * {@link #openCatalogDetail(TileData, List)} laeuft.</p>
	 *
	 * <p>ROADMAP GATE42: die Demo-Statusvitrine aus {@link DemoCatalog} landet
	 * nur dann mit in der durchsuchten Menge, wenn der echte Katalog noch
	 * nichts geliefert hat (derselbe Grundsatz wie fuer die Start-Reihe,
	 * GATE14/GATE15) -- ihre Ids existieren in keiner Registry, ein
	 * Treffer-Klick darauf liefe ueber {@link LocalPackageDetailSource} auf
	 * "Paket kaputt" (Charter-Regel 2). Sobald der Katalog geladen ist,
	 * verschwinden sie aus der Trefferliste; {@link CatalogSearchMerge} ist
	 * die reine, eigenstaendig getestete Zusammenfuehrung dafuer.</p>
	 */
	private void ensureSearchController() {
		List<TileData> searchTiles = catalogController != null ? catalogController.searchTiles() : List.of();
		CatalogSearchMerge.Result merged = CatalogSearchMerge.merge(searchTiles, readLocalLibraryOffers());
		catalogSearchIds = merged.catalogIds();

		if (searchController == null) {
			// ROADMAP J1: ohne den Opt-in-Schalter "Java-Mods anzeigen" reicht die
			// Suche kein Klasse-B-Angebot heraus.
			Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
			searchController = new StoreSearchController(new ArrayList<>(merged.tiles()),
					JavaModOfferGate.forConfigDir(configDir));
		} else {
			searchController.setCatalog(new ArrayList<>(merged.tiles()));
		}
	}

	/** Holt das eine geteilte Download-Flyout-Modell (ROADMAP S8/GATE4/GATE9/GATE13). */
	private void ensureDownloadFlyoutModel() {
		if (downloadFlyoutModel == null) {
			downloadFlyoutModel = DownloadFlyoutService.instance();
		}
	}

	/**
	 * ROADMAP K4: baut den {@link StoreCatalogController} einmal je Sitzung
	 * (Produktions-Seam mit dem Vitrine-Konfigordner -- derselbe Fabric-Weg wie
	 * {@code readLocalLibraryOffers()}, kein absoluter Pfad) und stoesst genau
	 * einen Hintergrund-Abruf an.
	 */
	private void ensureCatalogRefresh() {
		if (catalogController == null) {
			Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
			catalogController = StoreCatalogController.production(configDir);
		}
		if (catalogRefreshStarted) {
			return;
		}
		catalogRefreshStarted = true;
		startCatalogRefresh();
	}

	/**
	 * Startet {@link StoreCatalogController#refresh()} auf einem Hintergrund-
	 * Thread -- der Abruf blockiert (Netz, Timeout) und darf den Render-Thread
	 * nicht anhalten. Bis er fertig ist, zeigt der Inhaltsbereich das
	 * Lade-Skeleton (S7). Danach hat der Controller den echten Zustand an
	 * {@link CatalogAvailability} gemeldet; der Store baut sich auf dem
	 * Client-Thread neu auf, sofern er noch offen ist.
	 */
	private void startCatalogRefresh() {
		CatalogAvailability.report(CatalogAvailability.State.LOADING, null);
		StoreCatalogController controller = this.catalogController;
		var mc = this.client;
		Thread worker = new Thread(() -> {
			controller.refresh();
			if (mc != null) {
				mc.execute(() -> {
					if (mc.currentScreen == this) {
						rebuild();
					}
				});
			}
		}, "Vitrine-Catalog-Refresh");
		worker.setDaemon(true);
		worker.start();
	}

	/**
	 * Ob der Nutzer "Animationen reduzieren" gewaehlt hat (ROADMAP S9). Bei
	 * jedem Aufbau frisch von {@code config/vitrine/settings.json} gelesen --
	 * legt der {@link SettingsScreen} den Schalter um, uebernimmt der offene
	 * Store das beim naechsten {@code render}/{@code rebuild} ohne Neustart.
	 */
	private boolean reduceMotion() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		return SettingsModel.reduceMotion(configDir);
	}

	/** ROADMAP S9: oeffnet die Vitrine-Einstellungen (Kopfleisten-Knopf und Nav-Rail-Eintrag "Einstellungen"). */
	private void openSettings() {
		if (this.client != null) {
			this.client.setScreen(new SettingsHubScreen(this));
		}
	}

	/** Ob der Inhaltsbereich Suchergebnisse zeigt: aktive Freitextsuche oder eine Kategorie-Auswahl der Nav-Rail. */
	private boolean searchViewActive() {
		return searchController.hasActiveSearch() || NavRailCategories.isCategory(selectedEntry);
	}

	/**
	 * Ergebnisbereich der Suche (ROADMAP S6): durchschaltender Sortier-Knopf,
	 * Umschalter „ohne Neustart" und darunter die Trefferliste ueber die
	 * vorhandene {@link TileRowWidget}-Infrastruktur (S1). Nur aktiv, wenn eine
	 * Suche laeuft oder eine Nav-Rail-Kategorie gewaehlt ist.
	 */
	private void buildSearchContent() {
		searchRow = null;
		sortButton = null;
		restartFreeButton = null;
		noResultsStateView = null;
		if (!searchViewActive() || navController.isOverlayOpen()) {
			searchResults = List.of();
			return;
		}

		searchResults = searchController.results();

		int x = contentX() + VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE - contentScrollY;
		int rowWidth = this.width - x - VitrineTheme.SPACE_PAGE;
		if (rowWidth < VitrineTheme.CARD_WIDTH) {
			return;
		}

		if (searchController.hasActiveSearch()) {
			buildSearchResultsPage(x, y, rowWidth);
			return;
		}
		if (selectedEntry == NavRailEntry.NEWS && !searchController.hasActiveSearch()) {
			buildModrinthRow(x, y, rowWidth, javaModsAllowed() ? "news_mods" : "news");
			return;
		}
		if (selectedEntry == NavRailEntry.MODS && !searchController.hasActiveSearch()) {
			// Bereich "Modrinth": Reiter als Pillen wie die Filter der Suche
			this.addDrawableChild(new de.spassglas.vitrine.ui.home.PillTabsWidget(x, y, rowWidth, this.textRenderer,
					List.of("resourcepack", "shader", "mod", "datapack"), t -> Text.translatable("vitrine.modrinth.tab." + t),
					modrinthType, t -> {
						modrinthType = t;
						contentScrollY = 0;
						rebuild();
					}));
			buildModrinthRow(x, y + VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE, rowWidth, modrinthType);
			return;
		}

		sortButton = ButtonWidget.builder(sortButtonLabel(), button -> {
					searchController.cycleSort();
					rebuild();
				})
				.dimensions(x, y, VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		restartFreeButton = ButtonWidget.builder(restartFreeLabel(), button -> {
					searchController.toggleHideRestartRequired();
					rebuild();
				})
				.dimensions(x + VitrineTheme.MENU_BUTTON_WIDTH + VitrineTheme.CONTROL_GAP, y,
						VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.tooltip(Tooltip.of(restartFreeLabel()))
				.build();
		this.addDrawableChild(sortButton);
		this.addDrawableChild(restartFreeButton);

		int rowY = y + VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.SPACE_GUTTER;
		if (searchResults.isEmpty()) {
			// ROADMAP S7: Suche ohne Treffer -- Zustands-Panel mit genau einem Knopf ("Suche loeschen").
			noResultsStateView = StoreStateView.searchNoResults(searchController.submittedText());
			int btnY = rowY + StoreStatePanel.contentAdvance();
			int btnX = contentX() + (this.width - contentX()) / 2 - VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH / 2;
			ButtonWidget clearSearch = ButtonWidget.builder(
							Text.translatable(noResultsStateView.action().orElseThrow().labelKey()), button -> {
								searchController.clearActiveSearch();
								if (searchField != null) {
									searchField.setText("");
								}
								suggestionsOpen = false;
								rebuild();
							})
					.dimensions(btnX, btnY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
							VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(clearSearch);
			return;
		}
		List<TileData> results = searchResults;
		searchRow = new TileRowWidget(x, rowY, rowWidth, results, this.textRenderer,
				"vitrine.search.results.title", "vitrine.search.results.subtitle");
		searchRow.setReduceMotion(reduceMotion());
		searchRow.setOnTileActivated(tile -> openSearchResultDetail(tile, results));
		searchRow.setOnTileDownload(tile -> installFromTile(() -> openSearchResultDetail(tile, results)));
		this.addDrawableChild(searchRow);
		if ((selectedEntry == NavRailEntry.TEXTURES || selectedEntry == NavRailEntry.THEMES)
				&& !searchController.hasActiveSearch()) {
			buildModrinthRow(x, rowY + TileRowWidget.preferredHeight() + VitrineTheme.SPACE_GUTTER, rowWidth,
					"resourcepack");
		}
	}

	/**
	 * Reihe mit Angeboten von Modrinth (umgekocht, 2026-10-06): Ressourcenpakete im
	 * Texturen-Bereich, Fabric-Mods im Mods-Bereich. Laedt im Hintergrund;
	 * Mods nur mit dem Opt-in "Java-Mods anzeigen" (Klasse B, Neustart noetig).
	 */
	private void buildModrinthRow(int x, int y, int rowWidth, String type) {
		boolean mod = "mod".equals(type);
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		if (mod && !SettingsModel.showJavaModOffers(configDir)) {
			ButtonWidget enable = ButtonWidget.builder(Text.translatable("vitrine.modrinth.enable_mods"),
							b -> openSettings())
					.dimensions(x, y, Math.min(rowWidth, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH * 4), VitrineTheme.MENU_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(enable);
			return;
		}
		var mc = this.client;
		List<de.spassglas.vitrine.modrinth.ModrinthClient.Hit> hits = de.spassglas.vitrine.modrinth.ModrinthCatalog.get(
				type, () -> {
					if (mc != null) {
						mc.execute(() -> {
							if (mc.currentScreen == this) {
								rebuild();
							}
						});
					}
				});
		if (hits.isEmpty()) {
			return;
		}
		// Senkrechte Liste mit echten Projekt-Icons (umgekocht, 2026-10-06).
		Text heading = Text.translatable("vitrine.modrinth.row.title");
		Text sub = Text.translatable(mod ? "vitrine.modrinth.row.subtitle_mods"
				: type.startsWith("news") ? "vitrine.modrinth.row.subtitle_news"
				: type.equals("shader") ? "vitrine.modrinth.row.subtitle_shader"
				: type.equals("datapack") ? "vitrine.modrinth.row.subtitle_datapack" : "vitrine.modrinth.row.subtitle");
		this.addDrawableChild(new net.minecraft.client.gui.widget.ClickableWidget(x, y, rowWidth,
				VitrineTheme.ROW_HEADER_HEIGHT, heading) {
			@Override
			protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mx, int my, float d) {
				de.spassglas.vitrine.ui.theme.ScaledText.draw(context, textRenderer, heading, getX(), getY(),
						de.spassglas.vitrine.ui.theme.ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
				de.spassglas.vitrine.ui.theme.ScaledText.draw(context, textRenderer, sub, getX(),
						getY() + VitrineTheme.TYPE_TITLE + VitrineTheme.CARD_TEXT_LINE_GAP,
						de.spassglas.vitrine.ui.theme.ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			}

			@Override
			public boolean isMouseOver(double mx, double my) {
				return false;
			}

			@Override
			protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder b) {
			}
		});
		int ly = y + VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP;
		for (var h : hits) {
			this.addDrawableChild(new de.spassglas.vitrine.ui.widget.ModrinthListEntryWidget(x, ly, rowWidth, h,
					this.textRenderer, hit -> {
						if (this.client != null) {
							this.client.setScreen(new ModrinthInstallScreen(this, hit));
						}
					}));
			ly += de.spassglas.vitrine.ui.widget.ModrinthListEntryWidget.HEIGHT + VitrineTheme.SPACE_UNIT * 2;
		}
	}

	private Text sortButtonLabel() {
		return Text.translatable("vitrine.search.sort.label",
				Text.translatable(searchController.sort().translationKey()));
	}

	private Text restartFreeLabel() {
		String stateKey = searchController.hideRestartRequired()
				? "vitrine.search.filter.on"
				: "vitrine.search.filter.off";
		return Text.translatable("vitrine.search.filter.restart_free", Text.translatable(stateKey));
	}

	/**
	 * Baut die Kachelreihe fuer die Nav-Rail-Auswahl START in den
	 * Inhaltsbereich (ROADMAP S1). Sie wird als letztes Kind hinzugefuegt --
	 * das entspricht {@code CONTENT_PLACEHOLDER} als letztem Eintrag in
	 * {@code StoreFocusOrder.compute(...)}. Bei offener Nav-Rail-Ueberlagerung
	 * bleibt der Inhaltsbereich leer, damit nichts unter der Ueberlagerung
	 * hervorzeichnet; {@code onNavToggleClicked}/ESC bauen danach neu auf.
	 */
	private void buildStartContent() {
		heroCarousel = null;
		featuredRow = null;
		newRow = null;
		popularRow = null;
		startRow = null;
		libraryRow = null;
		catalogStateView = null;
		if (selectedEntry != NavRailEntry.START || navController.isOverlayOpen()
				|| searchController.hasActiveSearch()) {
			return;
		}
		int x = contentX() + VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE - contentScrollY;
		int rowWidth = this.width - x - VitrineTheme.SPACE_PAGE;
		if (rowWidth < VitrineTheme.CARD_WIDTH) {
			return;
		}

		// ROADMAP S7/K4: Ist der Katalogbereich nicht READY (laedt / kein Netz /
		// Katalog kaputt), zeigt der Inhaltsbereich das ehrliche Zustands-Panel
		// statt der Startreihe -- ausgeloest vom CatalogAvailability-Halter, den
		// der StoreCatalogController fuellt.
		Optional<StoreStateView> catalogState = CatalogAvailability.currentStateView();
		if (catalogState.isPresent()) {
			catalogStateView = catalogState.get();
			List<TileData> cachedCatalog = catalogStateView.hasAction() && catalogController != null
					? catalogController.catalogTiles() : List.of();
			boolean hasCachedCatalogRow = catalogStateView.hasAction() && !cachedCatalog.isEmpty();
			if (catalogStateView.hasAction()) {
				int btnY = y + StoreStatePanel.contentAdvance();
				int btnX = contentX() + (this.width - contentX()) / 2 - VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH / 2;
				ButtonWidget retry = ButtonWidget.builder(
								Text.translatable(catalogStateView.action().orElseThrow().labelKey()), button -> {
									Vitrine.LOGGER.info("Store: Katalog erneut anfordern.");
									startCatalogRefresh();
									rebuild();
								})
						.dimensions(btnX, btnY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
								VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
						.build();
				this.addDrawableChild(retry);

				// ROADMAP K4: Kein Netz, aber ein Cache-Stand liegt vor -> die
				// gecachten Katalog-Pakete bleiben als Kacheln unter der
				// "Stand von <Datum>"-Zeile bedienbar (Store bleibt bedienbar).
				if (hasCachedCatalogRow) {
					int rowY = btnY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE;
					// ROADMAP GATE14: echter (wenn auch gecachter) Katalog-
					// Bestand -- also auch die Beschriftung des echten
					// Katalogs, nie die Demo-Beschriftung (Charter-Regel 2).
					TileRowLabels cachedLabels = TileRowLabels.cachedCatalogRow();
					startRow = new TileRowWidget(x, rowY, rowWidth, cachedCatalog, this.textRenderer,
							cachedLabels.titleKey(), cachedLabels.subtitleKey());
					startRow.setReduceMotion(reduceMotion());
					// ROADMAP G3: auch der gecachte Katalog-Bestand ist echte
					// Registry-Herkunft -- Katalog-Quelle fuer die Detailseite.
					startRow.setOnTileActivated(tile -> openCatalogDetail(tile, cachedCatalog));
					startRow.setOnTileDownload(tile -> installFromTile(() -> openCatalogDetail(tile, cachedCatalog)));
					this.addDrawableChild(startRow);
				}
			}

			// Charter-Regel 12/ROADMAP GATE84: ein toter, abwesender oder noch
			// ladender Katalog legt den Store nicht lahm -- die lokale/
			// installierte Reihe erscheint trotzdem, unter dem Zustands-Panel,
			// unconditioned auf den Katalog-Zustand (auch das Lade-Skeleton
			// ohne Knopf waehrend des 15s-Netz-Timeouts, CatalogEndpoints.java:49).
			int rowY = localLibraryRowTopY(catalogStateView, y, hasCachedCatalogRow);
			buildLocalLibraryRow(x, rowY, rowWidth);
			return;
		}

		// Microsoft-Store-Aufbau (umgekocht, 2026-10-07): Banner, zwei Trending-Panels,
		// Modrinth-Regal, Vitrine-Regal, Collections, darunter die eigene Bibliothek.
		int rowsY = buildHome(x, y, rowWidth);
		buildLocalLibraryRow(x, rowsY, rowWidth);
	}

	// --- Startseite im Microsoft-Store-Stil --------------------------------

	private Runnable reloadWhenLoaded() {
		var mc = this.client;
		return () -> {
			if (mc != null) {
				mc.execute(() -> {
					if (mc.currentScreen == this) {
						rebuild();
					}
				});
			}
		};
	}

	private List<de.spassglas.vitrine.modrinth.ModrinthClient.Hit> modrinth(String type) {
		if (!de.spassglas.vitrine.config.VitrineConfig.get().showModrinth) {
			return List.of();
		}
		return de.spassglas.vitrine.modrinth.ModrinthCatalog.get(type, reloadWhenLoaded());
	}

	private static Identifier categoryIcon(String modrinthType) {
		String id = switch (modrinthType) {
			case "mod" -> "mod";
			case "shader" -> "shader";
			case "datapack" -> "datapack";
			default -> "texturen";
		};
		return Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/category_" + id + ".png");
	}

	private de.spassglas.vitrine.ui.home.HomeItem itemFromHit(de.spassglas.vitrine.modrinth.ModrinthClient.Hit h,
			boolean banner) {
		String id = h.id();
		return new de.spassglas.vitrine.ui.home.HomeItem(h.title(), banner ? h.description() : h.author(),
				banner ? h.galleryUrl() : h.iconUrl(), categoryIcon(h.projectType()), h.color(),
				() -> Text.translatable(de.spassglas.vitrine.ui.home.ModrinthInstalls.pillKey(id)).getString(),
				() -> {
					var st = de.spassglas.vitrine.ui.home.ModrinthInstalls.state(id);
					return st == de.spassglas.vitrine.ui.home.ModrinthInstalls.State.IDLE
							|| st == de.spassglas.vitrine.ui.home.ModrinthInstalls.State.FAILED;
				},
				() -> {
					if (this.client != null) {
						this.client.setScreen(new ModrinthInstallScreen(this, h));
					}
				},
				() -> {
					if (de.spassglas.vitrine.config.VitrineConfig.get().confirmModrinthInstall && this.client != null) {
						this.client.setScreen(new ModrinthInstallScreen(this, h));
					} else {
						de.spassglas.vitrine.ui.home.ModrinthInstalls.start(h);
					}
				},
				de.spassglas.vitrine.ui.home.HomeItem.BADGE_MODRINTH);
	}

	private de.spassglas.vitrine.ui.home.HomeItem itemFromTile(TileData tile, List<TileData> list, boolean catalog) {
		boolean installed = tile.status() == de.spassglas.vitrine.ui.tile.TileStatus.INSTALLED;
		String image = tile.hasImage() && tile.imagePath().startsWith("https://") ? tile.imagePath() : null;
		Runnable open = catalog ? () -> openCatalogDetail(tile, list) : () -> openDetail(tile, list);
		return new de.spassglas.vitrine.ui.home.HomeItem(tile.name(), tile.author(), image,
				Identifier.of(Vitrine.MOD_ID, tile.type().iconPath()), 0x2E7D5B,
				() -> Text.translatable(installed ? "vitrine.modrinth.installed" : "vitrine.tile.get").getString(),
				() -> !installed, open, () -> installFromTile(open), de.spassglas.vitrine.ui.home.HomeItem.BADGE_VITRINE);
	}

	/** Letzte Kachel eines Regals: "Mehr anzeigen" fuehrt in den passenden Bereich. */
	private de.spassglas.vitrine.ui.home.HomeItem seeMore(Runnable open) {
		return new de.spassglas.vitrine.ui.home.HomeItem(Text.translatable("vitrine.home.see_more").getString(), null,
				null, Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_arrow_right_small.png"), 0x3B74E8,
				() -> Text.translatable("vitrine.home.see_more.pill").getString(), () -> true, open, open);
	}

	/** umgekocht: Bereich "Modrinth" mit Reiter oeffnen (aus Collections und "Mehr >"). */
	private void openModrinth(String type) {
		modrinthType = type;
		onNavEntrySelected(NavRailEntry.MODS);
	}

	private String modrinthType = "resourcepack";

	private boolean javaModsAllowed() {
		return SettingsModel.showJavaModOffers(FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID));
	}

	private int buildHome(int x, int y, int w) {
		// Banner: rotierende Modrinth-Highlights, rechts ein weiteres Highlight
		// und zwei Vitrine-Sammlungen.
		List<de.spassglas.vitrine.modrinth.ModrinthClient.Hit> featured = new ArrayList<>();
		for (var h : modrinth("featured")) {
			if (javaModsAllowed() || !"mod".equals(h.projectType())) {
				featured.add(h);
			}
		}
		List<de.spassglas.vitrine.ui.home.HomeItem> slides = new ArrayList<>();
		for (int i = 0; i < featured.size() && slides.size() < 5; i++) {
			if (i == 1) {
				continue;
			}
			slides.add(itemFromHit(featured.get(i), true));
		}
		var sideTop = featured.size() > 1 ? itemFromHit(featured.get(1), true) : null;
		var sideLeft = new de.spassglas.vitrine.ui.home.HomeItem(Text.translatable("vitrine.home.side.addons").getString(),
				null, null, Identifier.of(Vitrine.MOD_ID, "textures/gui/collections/collection_addons.png"), 0x1F8A5B,
				() -> "", () -> false, () -> onNavEntrySelected(NavRailEntry.ADDONS), null);
		var sideRight = new de.spassglas.vitrine.ui.home.HomeItem(Text.translatable("vitrine.home.side.shader").getString(),
				null, null, Identifier.of(Vitrine.MOD_ID, "textures/gui/collections/collection_shader.png"), 0x6A3FC8,
				() -> "", () -> false, () -> openModrinth("shader"), null);
		this.addDrawableChild(new de.spassglas.vitrine.ui.home.BannerWidget(x, y, w, this.textRenderer, slides,
				sideTop, sideLeft, sideRight, reduceMotion()));
		y += de.spassglas.vitrine.ui.home.BannerWidget.heightFor(w) + VitrineTheme.SPACE_PAGE;

		// Trending: links meistgeladene Modrinth-Downloads, rechts Vitrine-Addons
		List<de.spassglas.vitrine.ui.home.HomeItem> trending = new ArrayList<>();
		for (var h : modrinth(javaModsAllowed() ? "top_mods" : "top")) {
			if (trending.size() < 6) {
				trending.add(itemFromHit(h, false));
			}
		}
		List<TileData> catalogTiles = catalogController != null ? catalogController.catalogTiles() : List.of();
		List<TileData> addonTiles = new ArrayList<>();
		for (TileData t : catalogTiles) {
			if (t.type() == de.spassglas.vitrine.ui.tile.TilePackageType.ADDON
					|| t.type() == de.spassglas.vitrine.ui.tile.TilePackageType.HUD) {
				addonTiles.add(t);
			}
		}
		List<TileData> local = readLocalLibraryOffers();
		List<de.spassglas.vitrine.ui.home.HomeItem> addons = new ArrayList<>();
		for (TileData t : addonTiles) {
			addons.add(itemFromTile(t, catalogTiles, true));
		}
		for (TileData t : local) {
			boolean dup = addonTiles.stream().anyMatch(c -> c.id().equals(t.id()));
			if (!dup && (t.type() == de.spassglas.vitrine.ui.tile.TilePackageType.ADDON
					|| t.type() == de.spassglas.vitrine.ui.tile.TilePackageType.HUD)) {
				addons.add(itemFromTile(t, local, false));
			}
		}
		this.addDrawableChild(new de.spassglas.vitrine.ui.home.TrendingPanelsWidget(x, y, w, this.textRenderer,
				Text.translatable("vitrine.home.trending_downloads"), trending, () -> openModrinth("resourcepack"),
				Text.translatable("vitrine.home.trending_addons"), addons, () -> onNavEntrySelected(NavRailEntry.ADDONS)));
		y += de.spassglas.vitrine.ui.home.TrendingPanelsWidget.heightFor() + VitrineTheme.SPACE_PAGE;

		// Regal: beliebte Modrinth-Downloads (Ressourcenpakete + Shader)
		List<de.spassglas.vitrine.ui.home.HomeItem> shelf = new ArrayList<>();
		for (var h : modrinth("resourcepack_50")) {
			if (shelf.size() < 50) {
				shelf.add(itemFromHit(h, false));
			}
		}
		if (!shelf.isEmpty()) {
			shelf.add(seeMore(() -> openModrinth("resourcepack")));
		}
		this.addDrawableChild(new de.spassglas.vitrine.ui.home.ShelfWidget(x, y, w, this.textRenderer,
				Text.translatable("vitrine.home.shelf_modrinth"), shelf, () -> openModrinth("resourcepack")));
		y += de.spassglas.vitrine.ui.home.ShelfWidget.heightFor() + VitrineTheme.SPACE_PAGE;

		// Regal: Vitrine-Katalog
		if (!catalogTiles.isEmpty()) {
			List<de.spassglas.vitrine.ui.home.HomeItem> vitrine = new ArrayList<>();
			for (TileData t : catalogTiles) {
				vitrine.add(itemFromTile(t, catalogTiles, true));
			}
			this.addDrawableChild(new de.spassglas.vitrine.ui.home.ShelfWidget(x, y, w, this.textRenderer,
					Text.translatable("vitrine.home.shelf_vitrine"), vitrine, () -> onNavEntrySelected(NavRailEntry.THEMES)));
			y += de.spassglas.vitrine.ui.home.ShelfWidget.heightFor() + VitrineTheme.SPACE_PAGE;
		}

		// Collections
		List<de.spassglas.vitrine.ui.home.CollectionsWidget.Collection> cols = List.of(
				collection("texturen", 0xFF2F7D4F, 0xFF173D27, "texturen", () -> openModrinth("resourcepack")),
				collection("addons", 0xFF1F6FB0, 0xFF0F3558, "addons", () -> onNavEntrySelected(NavRailEntry.ADDONS)),
				collection("shader", 0xFF6A3FC8, 0xFF2F1C5C, "shader", () -> openModrinth("shader")),
				collection("mods", 0xFFB0602A, 0xFF552D12, "mods", () -> openModrinth("mod")),
				collection("datapacks", 0xFF2A9AA0, 0xFF124A4D, "datapacks", () -> openModrinth("datapack")),
				collection("themes", 0xFFB03A6A, 0xFF551A33, "themes", () -> onNavEntrySelected(NavRailEntry.THEMES)));
		var colWidget = new de.spassglas.vitrine.ui.home.CollectionsWidget(x, y, w, this.textRenderer, cols);
		this.addDrawableChild(colWidget);
		return y + colWidget.getHeight() + VitrineTheme.SPACE_PAGE;
	}

	// --- Suche im Microsoft-Store-Stil -----------------------------------------

	private String searchFilter = "all";
	private final de.spassglas.vitrine.ui.home.SearchDropdown dropdown = new de.spassglas.vitrine.ui.home.SearchDropdown();
	private boolean dropdownOpen;
	private String dropdownText = "";
	private long dropdownChangedMs;

	private static String modrinthTypeLabel(String type) {
		return Text.translatable("vitrine.modrinth.tab." + type).getString();
	}

	private void buildSearchResultsPage(int x, int y, int w) {
		String text = searchController.submittedText();
		boolean mods = javaModsAllowed();
		List<de.spassglas.vitrine.ui.home.SearchResultsWidget.Result> results = new ArrayList<>();
		if (searchFilter.equals("all") || searchFilter.equals("vitrine")) {
			for (TileData t : searchResults) {
				var item = itemFromTile(t, searchResults, catalogSearchIds.contains(t.id()));
				results.add(new de.spassglas.vitrine.ui.home.SearchResultsWidget.Result(item,
						Text.translatable(t.type().translationKey()).getString() + "  |  Vitrine", t.author(),
						item.imageUrl()));
			}
		}
		var hits = de.spassglas.vitrine.modrinth.ModrinthSearch.get(text, mods, reloadWhenLoaded());
		for (var h : hits) {
			if (searchFilter.equals("all") || searchFilter.equals(h.projectType())) {
				results.add(new de.spassglas.vitrine.ui.home.SearchResultsWidget.Result(itemFromHit(h, false),
						modrinthTypeLabel(h.projectType()) + "  |  " + h.author(), h.description(), h.galleryUrl()));
			}
		}
		List<de.spassglas.vitrine.ui.home.SearchResultsWidget.Filter> filters = new ArrayList<>();
		filters.add(new de.spassglas.vitrine.ui.home.SearchResultsWidget.Filter("all",
				Text.translatable("vitrine.search.filter.all")));
		filters.add(new de.spassglas.vitrine.ui.home.SearchResultsWidget.Filter("vitrine",
				Text.translatable("vitrine.search.filter.vitrine")));
		for (String type : mods ? List.of("resourcepack", "shader", "mod", "datapack")
				: List.of("resourcepack", "shader", "datapack")) {
			filters.add(new de.spassglas.vitrine.ui.home.SearchResultsWidget.Filter(type,
					Text.translatable("vitrine.modrinth.tab." + type)));
		}
		boolean loading = hits.isEmpty() && de.spassglas.vitrine.modrinth.ModrinthSearch.cached(text, mods).isEmpty();
		this.addDrawableChild(new de.spassglas.vitrine.ui.home.SearchResultsWidget(x, y, w, this.textRenderer,
				Text.translatable("vitrine.search.heading", text), filters, searchFilter, f -> {
					searchFilter = f;
					rebuild();
				}, results, loading));
	}

	/** Baut die Vorschlagszeilen fuer den aktuellen Text (jedes Bild neu, ohne Neuaufbau). */
	private void updateDropdown() {
		if (searchField == null || searchBox == null) {
			return;
		}
		String text = searchField.getText().trim();
		if (!text.equals(dropdownText)) {
			dropdownText = text;
			dropdownChangedMs = net.minecraft.util.Util.getMeasuringTimeMs();
		}
		boolean mods = javaModsAllowed();
		List<de.spassglas.vitrine.ui.home.SearchDropdown.Row> queries = new ArrayList<>();
		List<de.spassglas.vitrine.ui.home.SearchDropdown.Row> items = new ArrayList<>();
		java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		java.util.Set<String> history = new java.util.HashSet<>();
		if (de.spassglas.vitrine.config.VitrineConfig.get().saveSearchHistory) {
			for (String recent : de.spassglas.vitrine.config.VitrineConfig.get().searchHistory) {
				if (lower.isEmpty() || recent.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
					words.add(recent);
					history.add(recent);
				}
			}
		}
		List<de.spassglas.vitrine.modrinth.ModrinthClient.Hit> hits = List.of();
		if (!text.isEmpty() && de.spassglas.vitrine.config.VitrineConfig.get().liveSuggestions
				&& de.spassglas.vitrine.config.VitrineConfig.get().showModrinth) {
			// kurze Pause nach dem Tippen, dann live auf Modrinth suchen
			hits = net.minecraft.util.Util.getMeasuringTimeMs() - dropdownChangedMs > 250
					? de.spassglas.vitrine.modrinth.ModrinthSearch.get(text, mods, () -> {
					})
					: de.spassglas.vitrine.modrinth.ModrinthSearch.cached(text, mods);
			for (var h : hits) {
				String t = h.title().toLowerCase(java.util.Locale.ROOT);
				if (t.startsWith(lower)) {
					words.add(t);
				}
			}
			words.add(text);
		}
		int maxQueries = text.isEmpty() ? 6 : 3;
		for (String q : words) {
			if (queries.size() >= maxQueries) {
				break;
			}
			// Verlauf mit Uhr-Symbol, Vorschlaege mit Lupe (wie im Microsoft Store)
			Identifier rowIcon = history.contains(q) ? de.spassglas.vitrine.ui.home.SearchDropdown.HISTORY : null;
			queries.add(new de.spassglas.vitrine.ui.home.SearchDropdown.Row(q, null, null, rowIcon, () -> {
				searchField.setText(q);
				dropdownOpen = false;
				submitSearch(q);
			}));
		}
		if (!text.isEmpty()) {
			List<TileData> local = searchController.suggestions().liveMatches();
			for (TileData t : local) {
				if (items.size() >= 2) {
					break;
				}
				items.add(new de.spassglas.vitrine.ui.home.SearchDropdown.Row(t.name(),
						Text.translatable(t.type().translationKey()).getString() + " | Vitrine", null,
						Identifier.of(Vitrine.MOD_ID, t.type().iconPath()), () -> {
							dropdownOpen = false;
							openSearchResultDetail(t, local);
						}));
			}
			for (var h : hits) {
				if (items.size() >= 6) {
					break;
				}
				items.add(new de.spassglas.vitrine.ui.home.SearchDropdown.Row(h.title(), modrinthTypeLabel(h.projectType()),
						h.iconUrl(), categoryIcon(h.projectType()), () -> {
							dropdownOpen = false;
							if (this.client != null) {
								this.client.setScreen(new ModrinthInstallScreen(this, h));
							}
						}));
			}
		}
		dropdown.set(searchBox[0], searchBox[1] + searchBox[3] + 2, searchBox[2], queries, items);
	}

	private boolean dropdownVisible() {
		return dropdownOpen && searchField != null && searchField.isFocused() && !navController.isOverlayOpen();
	}

	private de.spassglas.vitrine.ui.home.CollectionsWidget.Collection collection(String key, int top, int bottom,
			String icon, Runnable open) {
		return new de.spassglas.vitrine.ui.home.CollectionsWidget.Collection(
				Text.translatable("vitrine.home.collection." + key),
				Text.translatable("vitrine.home.collection." + key + ".sub"), top, bottom,
				Identifier.of(Vitrine.MOD_ID, "textures/gui/collections/collection_" + icon + ".png"), open);
	}

	/**
	 * ROADMAP X2: baut eine kuratierte Kachelreihe bei {@code (x, y)}, sofern
	 * {@code tiles} nicht leer ist, und liefert die y-Koordinate fuer die
	 * naechste Reihe zurueck (unveraendert, wenn ausgelassen).
	 */
	private int buildOptionalRow(List<TileData> tiles, String titleKey, String subtitleKey, int x, int y,
			int rowWidth, Consumer<TileRowWidget> assign) {
		if (tiles.isEmpty()) {
			return y;
		}
		TileRowWidget row = new TileRowWidget(x, y, rowWidth, tiles, this.textRenderer, titleKey, subtitleKey);
		row.setReduceMotion(reduceMotion());
		// ROADMAP G3: Featured/Neu/Beliebt kommen ausschliesslich aus dem
		// echten Katalog (K2/X2, CatalogResult.Availability.ONLINE) -- nie aus
		// der lokalen Bibliothek. Katalog-Quelle fuer die Detailseite.
		row.setOnTileActivated(tile -> openCatalogDetail(tile, tiles));
		row.setOnTileDownload(tile -> installFromTile(() -> openCatalogDetail(tile, tiles)));
		this.addDrawableChild(row);
		assign.accept(row);
		return y + TileRowWidget.preferredHeight() + VitrineTheme.SPACE_GUTTER;
	}

	/**
	 * ROADMAP GATE84/Charter-Regel 12: y-Koordinate, an der
	 * {@link #buildStartContent()} die lokale Bibliotheksreihe unter dem
	 * Katalog-Zustands-Panel ansetzt -- fuer <em>jeden</em> Zustand, auch das
	 * Lade-Skeleton ohne Knopf ({@code view.hasAction() == false}, z.B.
	 * waehrend des 15s-Netz-Timeouts in {@code CatalogEndpoints}). Reine,
	 * seiteneffektfreie Berechnung (package-privat fuer den Test): vorher gab
	 * ein {@code if (hasAction())}-Zweig ohne {@code else} den Aufruf von
	 * {@link #buildLocalLibraryRow} fuer das Lade-Skeleton nie frei --
	 * installierte Pakete blieben hinter dem Netz-Timeout verdeckt.
	 */
	static int localLibraryRowTopY(StoreStateView view, int panelTopY, boolean hasCachedCatalogRow) {
		if (!view.hasAction()) {
			return panelTopY + StoreStatePanel.skeletonContentAdvance();
		}
		int btnY = panelTopY + StoreStatePanel.contentAdvance();
		int rowY = btnY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE;
		if (hasCachedCatalogRow) {
			rowY += TileRowWidget.preferredHeight() + VitrineTheme.SPACE_GUTTER;
		}
		return rowY;
	}

	/**
	 * Baut die lokale Bibliotheks-Kachelreihe (ROADMAP S2) an {@code (x, y)},
	 * frisch aus {@code config/vitrine/library/}. Leeres Ergebnis -> keine Reihe.
	 * Wird im Normalfall <em>und</em> unter einem Katalog-Zustands-Panel
	 * aufgerufen, damit lokale/installierte Pakete unabhaengig vom Katalog-
	 * Zustand bedienbar bleiben (ROADMAP K4/GATE84, Charter-Regel 12).
	 */
	private void buildLocalLibraryRow(int x, int y, int rowWidth) {
		List<TileData> localOffers = readLocalLibraryOffers();
		if (localOffers.isEmpty()) {
			return;
		}
		libraryRow = new TileRowWidget(x, y, rowWidth, localOffers, this.textRenderer,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		libraryRow.setReduceMotion(reduceMotion());
		libraryRow.setOnTileActivated(tile -> openDetail(tile, localOffers));
		libraryRow.setOnTileDownload(tile -> installFromTile(() -> openDetail(tile, localOffers)));
		this.addDrawableChild(libraryRow);
	}

	/**
	 * Oeffnet die Detailseite (ROADMAP S3) fuer eine angeklickte/aktivierte
	 * Kachel aus der Bibliotheks-Reihe. Das {@code .vpkg} wird dafuer nur
	 * gelesen (Manifest, Groesse) -- nichts angewendet. Findet sich keine
	 * gueltige Datei, passiert nichts (die Quelle loggt den Grund).
	 */
	private void openDetail(TileData tile, List<TileData> catalog) {
		openDetail(tile, catalog, false);
	}

	/**
	 * ROADMAP G3: wie {@link #openDetail(TileData, List)}, aber fuer eine
	 * Kachel aus einer Katalog-Reihe (Featured/Neu/Beliebt/Katalog-
	 * Hauptreihe) -- laedt ueber {@link CatalogPackageDetailSource} (Registry-
	 * Paketeintrag, {@code packages/&lt;id&gt;.json}) statt aus dem lokalen
	 * Bibliotheksordner. Der Aktionsknopf der Detailseite installiert dann
	 * ueber {@link de.spassglas.vitrine.pack.CatalogPackInstallService}
	 * (Download + Pruefung gegen den eingebetteten Registry-Schluessel).
	 */
	private void openCatalogDetail(TileData tile, List<TileData> catalog) {
		openDetail(tile, catalog, true);
	}

	/**
	 * ROADMAP GATE5: oeffnet einen Suchtreffer ueber die richtige Quelle --
	 * {@link #openCatalogDetail(TileData, List)}, wenn die Kachel-Id aus dem
	 * echten Katalog stammt ({@link #catalogSearchIds}), sonst
	 * {@link #openDetail(TileData, List)} (Demo-/lokale Bibliotheks-Kachel).
	 * Ohne diese Unterscheidung wuerde ein Katalog-Treffer aus der lokalen
	 * Quelle geladen (dort nicht vorhanden -> "Paket kaputt") statt ueber den
	 * Katalog-Installationsweg zu oeffnen.
	 */
	private void openSearchResultDetail(TileData tile, List<TileData> catalog) {
		if (catalogSearchIds.contains(tile.id())) {
			openCatalogDetail(tile, catalog);
		} else {
			openDetail(tile, catalog);
		}
	}

	/** Gesetzt vom "Holen"-Knopf einer Kachel: die naechste Detailseite installiert sofort. */
	private boolean nextDetailInstalls;

	private void installFromTile(Runnable open) {
		nextDetailInstalls = true;
		try {
			open.run();
		} finally {
			nextDetailInstalls = false;
		}
	}

	private void openDetail(TileData tile, List<TileData> catalog, boolean fromCatalog) {
		if (this.client == null) {
			return;
		}
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		Optional<PackageDetailModel> loaded = fromCatalog
				? CatalogPackageDetailSource.forConfigDir(configDir).load(tile.id(), tile.status(), catalog)
				: LocalPackageDetailSource.forConfigDir(configDir).load(tile.id(), tile.status(), catalog);
		if (loaded.isPresent()) {
			PackageDetailScreen detail = fromCatalog
					? PackageDetailScreen.forCatalog(this, loaded.get())
					: new PackageDetailScreen(this, loaded.get());
			this.client.setScreen(nextDetailInstalls ? detail.withAutoInstall() : detail);
		} else {
			// ROADMAP S7: Paket kaputt -- die Quelle hat den genauen Grund geloggt,
			// die Seite zeigt "<Name> konnte nicht geladen werden" mit einem Knopf.
			Vitrine.LOGGER.warn("Store: '{}' liess sich nicht als Detailseite laden -- Paket-kaputt-Zustand.",
					tile.id());
			this.client.setScreen(PackageDetailScreen.broken(this, StoreStateView.packageBroken(tile.name())));
		}
	}

	/**
	 * Liest die Angebote aus {@code config/vitrine/library/} bei jedem Aufbau
	 * des Inhalts neu (ROADMAP S2, "ohne Neustart"). Der Konfigurationsordner
	 * wird ueber denselben Fabric-Weg ermittelt wie {@code SettingsManager} und
	 * {@code VitrinePackProvider} -- kein eigener Pfad.
	 */
	private List<TileData> readLocalLibraryOffers() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		// GATE17: Kacheln zeigen das Paketbild aus der .vpkg.
		// GATE80: der echte Versionsabgleich -- eine bereits installierte Datei in
		// library/ mit einer neueren Version zeigt UPDATE_AVAILABLE statt INSTALLED.
		return LocalLibrarySource.forConfigDir(configDir)
				.withImageResolver((file, entry) -> de.spassglas.vitrine.ui.image.PackageImageTextures
						.get(file, entry).map(texture -> texture.id().toString()))
				.scan(PackStateStore.load(configDir), InstalledPackVersions.forConfigDir(configDir));
	}

	private static final int SEARCH_MAX_WIDTH = 520;
	private static final int SEARCH_ICON = 12;
	private static final int BRAND_ICON = 18;
	private static final Identifier ICON_SEARCH = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_search.png");
	private static final Identifier ICON_CLOSE = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/ui_close.png");

	/** x des X-Knopfs im Suchfeld (links neben der Lupe). */
	private int clearX() {
		return searchBox[0] + searchBox[2] - 2 * SEARCH_ICON - VitrineTheme.SPACE_UNIT * 6;
	}
	private static final Identifier ICON_BRAND = Identifier.of(Vitrine.MOD_ID, "textures/gui/icons/vitrine_button.png");
	private int brandX;
	private boolean brandVisible;
	private final de.spassglas.vitrine.ui.home.AccountMenu account = new de.spassglas.vitrine.ui.home.AccountMenu();

	private void buildHeader() {
		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		int leftX = VitrineTheme.EDGE_PADDING;
		// Vitrine-Logo ganz links (umgekocht, 2026-10-07), danach Menue/Zurueck.
		brandX = leftX + 6;
		brandVisible = this.width > 700;
		if (brandVisible) {
			leftX = brandX + BRAND_ICON + VitrineTheme.CONTROL_GAP
					+ de.spassglas.vitrine.ui.theme.ScaledText.width(this.textRenderer, "Vitrine", VitrineTheme.TYPE_BODY)
					+ VitrineTheme.SPACE_GUTTER;
		}

		navToggleButton = null;
		if (navController.layout() == NavRailLayout.OVERLAY) {
			navToggleButton = IconButtonWidget.iconOnly(leftX, buttonY, VitrineTheme.ICON_BUTTON_SIZE,
					VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.nav_toggle"),
					button -> onNavToggleClicked(), ICON_MENU, null, VitrineTheme.UI_ICON_SOURCE_SIZE);
			leftX += VitrineTheme.ICON_BUTTON_SIZE + VitrineTheme.CONTROL_GAP;
		}

		backButton = null;
		if (parentScreen != null) {
			backButton = IconButtonWidget.iconOnly(leftX, buttonY, VitrineTheme.ICON_BUTTON_SIZE,
					VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), button -> this.close(),
					ICON_BACK, null, VitrineTheme.UI_ICON_SOURCE_SIZE);
			leftX += VitrineTheme.ICON_BUTTON_SIZE + VitrineTheme.CONTROL_GAP;
		}

		int rightX = this.width - VitrineTheme.EDGE_PADDING - VitrineTheme.ICON_BUTTON_SIZE;
		// ROADMAP S8: Klick oeffnet/schliesst das Download-Flyout unter der
		// Kopfleiste. Der Zaehler-Text erscheint als Tooltip, wenn etwas laeuft
		// (docs/UI-SPEC.md "1. Kopfleiste": "Pfeil nach unten, mit Zaehler, wenn
		// etwas laeuft"), sonst der schlichte Knopf-Text.
		int running = downloadFlyoutModel.runningCount();
		Text downloadsLabel = running > 0
				? Text.translatable("vitrine.header.downloads.count", running)
				: Text.translatable("vitrine.header.downloads");
		IconButtonWidget downloads = IconButtonWidget.iconOnly(rightX, buttonY, VitrineTheme.ICON_BUTTON_SIZE,
				VitrineTheme.ICON_BUTTON_SIZE, downloadsLabel, button -> toggleDownloadsFlyout(),
				ICON_DOWNLOAD, null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		downloads.setBadge(running > 0 ? Integer.toString(running) : null);
		downloadsButton = downloads;
		rightX -= VitrineTheme.ICON_BUTTON_SIZE + VitrineTheme.CONTROL_GAP;

		settingsButton = IconButtonWidget.iconOnly(rightX, buttonY, VitrineTheme.ICON_BUTTON_SIZE,
				VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.nav.einstellungen"), button -> openSettings(),
				Identifier.of(Vitrine.MOD_ID, NavRailEntry.SETTINGS.outlineIconPath()), null,
				VitrineTheme.NAV_ICON_SOURCE_SIZE);
		rightX -= VitrineTheme.CONTROL_GAP;

		// Microsoft-Store-Aufbau (umgekocht, 2026-10-07): links Marke (V + "Vitrine"),
		// Suche mittig mit fester Hoechstbreite und Lupe rechts im Feld, rechts die Knoepfe.
		int available = Math.max(VitrineTheme.NAV_WIDTH_COMPACT, rightX - leftX);
		int searchWidth = Math.min(available, SEARCH_MAX_WIDTH);
		leftX = Math.max(leftX, Math.min(rightX - searchWidth, (this.width - searchWidth) / 2));
		int searchY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.SEARCH_FIELD_HEIGHT) / 2;
		// Flaches, abgerundetes Suchfeld im Store-Stil: das Feld selbst zeichnet
		// keinen Minecraft-Rahmen, die Flaeche dahinter kommt aus render()
		// (searchBoxBounds). Ohne Hintergrund setzt Vanilla den Text an die
		// Oberkante des Feldes -- deshalb ein textzeilenhohes Feld, mittig.
		searchBox = new int[] {leftX, searchY, searchWidth, VitrineTheme.SEARCH_FIELD_HEIGHT};
		int fieldPad = VitrineTheme.SPACE_UNIT * 2;
		int fieldHeight = this.textRenderer.fontHeight + 1;
		searchField = new TextFieldWidget(this.textRenderer, leftX + fieldPad + 2,
				searchY + (VitrineTheme.SEARCH_FIELD_HEIGHT - fieldHeight) / 2 + 1,
				searchWidth - 4 * fieldPad - 2 * SEARCH_ICON,
				fieldHeight, Text.translatable("vitrine.search.placeholder"));
		searchField.setDrawsBackground(false);
		// eingegebener Text in derselben Schrift wie die restliche Oberflaeche
		searchField.setRenderTextProvider((string, firstCharacterIndex) ->
				de.spassglas.vitrine.ui.theme.ScaledText.styled(string).asOrderedText());
		searchField.setPlaceholder(de.spassglas.vitrine.ui.theme.ScaledText.styled(
				Text.translatable("vitrine.search.placeholder")));
		searchField.setMaxLength(SEARCH_INPUT_MAX_LENGTH);
		searchField.setText(searchController.liveText());
		searchField.setChangedListener(text -> {
			// Live-Text speist nur das Vorschlagsfeld -- kein Verlaufseintrag,
			// kein Neuaufbau je Tastendruck (ROADMAP S6).
			searchController.setLiveText(text);
			suggestionsOpen = false;
			dropdownOpen = true;
		});

		// Reihenfolge in Leserichtung -- siehe StoreFocusOrder.compute.
		if (navToggleButton != null) {
			this.addDrawableChild(navToggleButton);
		}
		if (backButton != null) {
			this.addDrawableChild(backButton);
		}
		this.addDrawableChild(searchField);
		settingsButton.visible = false;
		this.addDrawableChild(settingsButton);
		this.addDrawableChild(downloadsButton);
	}

	/** Seitenleiste wie im Microsoft Store (umgekocht, 2026-10-07). */
	private static final List<NavRailEntry> RAIL_TOP = List.of(NavRailEntry.START, NavRailEntry.ADDONS,
			NavRailEntry.MODS, NavRailEntry.THEMES);
	private static final List<NavRailEntry> RAIL_BOTTOM = List.of(NavRailEntry.NEWS, NavRailEntry.LIBRARY,
			NavRailEntry.SETTINGS);

	private void buildNavRail() {
		if (!navController.isRailVisible()) {
			return;
		}
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.EDGE_PADDING;
		for (NavRailEntry entry : RAIL_TOP) {
			addNavButton(entry, y);
			y += NAV_STACK_HEIGHT + 4;
		}
		int bottomY = this.height - VitrineTheme.EDGE_PADDING - RAIL_BOTTOM.size() * (NAV_STACK_HEIGHT + 4);
		for (NavRailEntry entry : RAIL_BOTTOM) {
			addNavButton(entry, bottomY);
			bottomY += NAV_STACK_HEIGHT + 4;
		}
	}

	private static final int NAV_STACK_HEIGHT = 46;

	private void addNavButton(NavRailEntry entry, int y) {
		// GATE18: die mitgelieferten Icons -- Umriss inaktiv, gefuellt auf der
		// aktiven Seite (docs/UI-SPEC.md "2. Nav-Rail"). Eingeklappt nur das
		// Icon, der Name als Tooltip.
		Text label = Text.translatable(entry.translationKey());
		Identifier outline = Identifier.of(Vitrine.MOD_ID, entry.outlineIconPath());
		Identifier filled = Identifier.of(Vitrine.MOD_ID, entry.filledIconPath());
		IconButtonWidget button = navController.layout() == NavRailLayout.COMPACT
				? IconButtonWidget.stacked(4, y, currentNavWidth - 8, NAV_STACK_HEIGHT, label,
						b -> onNavEntrySelected(entry), outline, filled, VitrineTheme.NAV_ICON_SOURCE_SIZE)
				: IconButtonWidget.withLabel(0, y, currentNavWidth, VitrineTheme.NAV_ITEM_HEIGHT, label,
						b -> onNavEntrySelected(entry), outline, filled, VitrineTheme.NAV_ICON_SOURCE_SIZE);
		button.setSelected(entry == selectedEntry);
		navButtons.put(entry, button);
		this.addDrawableChild(button);
	}

	/** Nur AutoShot: Suchtext tippen (Vorschlaege offen) oder abschicken. */
	public void autoShotSearch(String text, boolean submit) {
		if (searchField == null) {
			return;
		}
		setFocused(searchField);
		searchField.setFocused(true);
		searchField.setText(text);
		dropdownOpen = true;
		if (submit) {
			submitSearch(text);
		}
	}

	/** Nur AutoShot: Produktseite des ersten beliebten Texturepacks oeffnen. */
	public void autoShotProduct() {
		var list = modrinth("resourcepack");
		if (!list.isEmpty() && this.client != null) {
			this.client.setScreen(new ModrinthInstallScreen(this, list.get(0)));
		}
	}

	/** Nur AutoShot: Einstellungen oeffnen. */
	public void autoShotSettings() {
		openSettings();
	}

	/** Nur AutoShot: Konto-Menue aufklappen. */
	public void autoShotAccount() {
		if (!account.isOpen() && settingsButton != null) {
			account.click(this, settingsButton.getX() + settingsButton.getWidth() / 2.0, VitrineTheme.HEADER_HEIGHT / 2.0);
		}
	}

	/** Oeffnet einen Bereich der Nav-Leiste (auch fuer AutoShot/Tests). */
	public void openSection(NavRailEntry entry) {
		onNavEntrySelected(entry);
	}

	private void onNavEntrySelected(NavRailEntry entry) {
		contentScrollY = 0;
		if (entry == NavRailEntry.LIBRARY && this.client != null) {
			// ROADMAP S5: die Bibliothek ist ein eigener Bereich (docs/UI-SPEC.md
			// "6. Bibliothek"), ueber die Nav-Rail erreichbar.
			this.client.setScreen(new LibraryScreen(this));
			return;
		}
		if (entry == NavRailEntry.SETTINGS) {
			// ROADMAP S9: "Einstellungen" ist ueber die Nav-Rail erreichbar
			// (docs/UI-SPEC.md "2. Nav-Rail links": "unten abgesetzt: Einstellungen").
			openSettings();
			return;
		}
		this.selectedEntry = entry;
		// ROADMAP S6: die Nav-Rail waehlt die Kategorie. „Start" hebt die
		// Einschraenkung auf und raeumt eine laufende Suche weg (Heimweg).
		searchController.setCategories(NavRailCategories.typesFor(entry));
		if (entry == NavRailEntry.START) {
			searchController.clearActiveSearch();
			if (searchField != null) {
				searchField.setText("");
			}
		}
		suggestionsOpen = false;
		// Der Inhaltsbereich haengt von der Auswahl ab (S1: START zeigt eine
		// Kachelreihe, S6: Kategorien zeigen die gefilterte Trefferliste) --
		// neu aufbauen.
		rebuild();
	}

	private void onNavToggleClicked() {
		navController.toggleOverlay();
		rebuild();
	}

	/** ROADMAP S8: Kopfleisten-Knopf "Downloads" -- Flyout auf/zu. */
	private void toggleDownloadsFlyout() {
		downloadsFlyoutOpen = !downloadsFlyoutOpen;
	}

	private void rebuild() {
		this.clearChildren();
		this.init();
	}

	/** Header- und Nav-Elemente scrollen nicht mit; alles andere ist Inhalt. */
	private boolean isContentElement(Object element) {
		return element != navToggleButton && element != backButton && element != searchField
				&& element != settingsButton && element != downloadsButton
				&& !navButtons.containsValue(element);
	}

	/** Misst nach dem Aufbau, wie weit der Inhalt reicht; klemmt einen zu grossen Scroll und baut dann neu. */
	private void measureContent() {
		int bottom = 0;
		for (var element : this.children()) {
			if (isContentElement(element) && element instanceof net.minecraft.client.gui.widget.ClickableWidget w
					&& w.visible) {
				bottom = Math.max(bottom, w.getY() + w.getHeight() + contentScrollY);
			}
		}
		contentBottom = bottom + VitrineTheme.SPACE_PAGE;
		int max = Math.max(0, contentBottom - this.height);
		if (contentScrollY > max) {
			contentScrollY = max;
			rebuild();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}
		if (verticalAmount == 0 || mouseX < contentX() || mouseY < VitrineTheme.HEADER_HEIGHT || suggestionsOpen) {
			return false;
		}
		int max = Math.max(0, contentBottom - this.height);
		int next = Math.max(0, Math.min(max,
				contentScrollY - (int) Math.signum(verticalAmount) * VitrineTheme.DETAIL_SCROLL_STEP * 2));
		if (next != contentScrollY) {
			contentScrollY = next;
			rebuild();
		}
		return true;
	}

	/**
	 * Zuhoerer-Rueckruf des {@link ThemeService} nach einem Laufzeit-
	 * Theme-Wechsel: nur den tatsaechlich sichtbaren Store neu aufbauen (der
	 * neue Aufbau liest die Farben ueber {@link #theme()} frisch -> sofortige
	 * Umfaerbung). Ein Rueckruf an einen bereits geschlossenen Bildschirm
	 * (z. B. wenn {@code removed()} ausblieb) wird ignoriert.
	 *
	 * <p><b>GATE113 (Thread-Modell):</b> {@link ThemeService} ruft diesen
	 * Zuhoerer auf dem Thread auf, der den Theme-Wechsel ausgeloest hat --
	 * bei Theme-<em>Paketen</em> ist das der Installations-Thread
	 * (Vitrine-Catalog-Install), nicht der Render-Thread (T4 hat bewusst
	 * keinen {@code reloadResources()}-Umweg fuer Themes, dieser Rueckruf ist
	 * der einzige Pfad). {@code rebuild()} ruft {@code clearChildren()}/
	 * {@code init()}, waehrend der Render-Thread ueber {@code
	 * Screen.children()} iterieren kann -- {@code currentScreen == this}
	 * schuetzt davor nicht, das ist nur eine Bedingung, keine Thread-Grenze.
	 * Deshalb nur ueber {@code client.execute(...)} einplanen, nie direkt
	 * ausfuehren; die {@code currentScreen}-Prüfung laeuft dabei absichtlich
	 * erst bei der tatsaechlichen Ausfuehrung erneut, der sichtbare Bildschirm
	 * kann zwischen Einplanung und Ausfuehrung gewechselt haben.</p>
	 */
	private void onThemeChanged() {
		if (this.client == null) {
			return;
		}
		this.client.execute(() -> {
			if (this.client != null && this.client.currentScreen == this) {
				rebuild();
			}
		});
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ESC schliesst Overlays in umgekehrter Oeffnungsreihenfolge, zuletzt den
		// Store selbst (docs/UI-SPEC.md "Barrierefreiheit"; Reihenfolge wie
		// FocusTraversal.dismissOrder: Vorschlagsfeld -> Download-Flyout ->
		// Nav-Rail-Ueberlagerung -> Bildschirm).
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && suggestionsOpen) {
			suggestionsOpen = false;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && downloadsFlyoutOpen) {
			downloadsFlyoutOpen = false;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && navController.isOverlayOpen()) {
			navController.closeOverlay();
			rebuild();
			return true;
		}
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
				&& searchField != null && searchField.isFocused()) {
			submitSearch(searchField.getText());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// ROADMAP S8: Klick ins Download-Flyout -> Zeile abbrechen bzw. eine
		// fertige Zeile schliessen; Klick daneben (nicht auf den Knopf) schliesst
		// das Flyout. Vor super.mouseClicked, damit der Klick nicht eine
		// darunterliegende Kachel trifft.
		if (downloadsFlyoutOpen && !navController.isOverlayOpen()) {
			int row = flyoutRowAt(mouseX, mouseY);
			if (row >= 0) {
				DownloadEntry entry = downloadFlyoutModel.allRows().get(row);
				downloadFlyoutModel.cancel(entry.packId());
				Vitrine.LOGGER.info("Store: Download-Flyout -- Abbrechen/Schliessen fuer '{}'.", entry.packId());
				rebuild();
				return true;
			}
			boolean onButton = downloadsButton != null && downloadsButton.isMouseOver(mouseX, mouseY);
			if (!pointInFlyout(mouseX, mouseY) && !onButton) {
				downloadsFlyoutOpen = false;
				return true;
			}
		}
		if (button == 0 && account.click(this, mouseX, mouseY)) {
			return true;
		}
		if (button == 0 && searchBox != null && searchField != null && !searchField.getText().isEmpty()
				&& mouseX >= clearX() - 3 && mouseX < clearX() + SEARCH_ICON + 3 && mouseY >= searchBox[1]
				&& mouseY < searchBox[1] + searchBox[3]) {
			searchField.setText("");
			if (searchController.hasActiveSearch()) {
				searchController.clearActiveSearch();
				rebuild();
			}
			return true;
		}
		if (dropdownVisible() && button == 0) {
			if (dropdown.click(mouseX, mouseY)) {
				return true;
			}
			if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
				dropdownOpen = false;
			}
		} else if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) {
			dropdownOpen = true;
			setFocused(searchField);
			searchField.setFocused(true);
		}
		// ROADMAP S6: Klick ins Vorschlagsfeld -> Zeile uebernehmen; Klick
		// daneben schliesst es; Klick in die Suchleiste oeffnet es (wenn es
		// etwas zu zeigen gibt). Vor super.mouseClicked, damit der Klick nicht
		// erst eine darunterliegende Kachel trifft.
		if (suggestionsOpen && searchField != null && !navController.isOverlayOpen()) {
			SearchSuggestions suggestions = searchController.suggestions();
			if (!suggestions.isEmpty()) {
				int left = searchField.getX();
				int right = left + searchField.getWidth();
				SearchSuggestionsLayout layout =
						new SearchSuggestionsLayout(suggestions, VitrineTheme.HEADER_HEIGHT, suggestionRowHeight());
				var hit = layout.rowAt(mouseX, mouseY, left, right);
				if (hit.isPresent()) {
					SearchSuggestionsLayout.Row row = hit.get();
					if (row.kind() == SearchSuggestionsLayout.RowKind.RECENT) {
						String recentText = suggestions.recentSearches().get(row.index());
						searchField.setText(recentText);
						submitSearch(recentText);
					} else {
						TileData tile = suggestions.liveMatches().get(row.index());
						suggestionsOpen = false;
						openSearchResultDetail(tile, suggestions.liveMatches());
					}
					return true;
				}
				if (!searchField.isMouseOver(mouseX, mouseY)) {
					suggestionsOpen = false;
				}
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (searchField != null && searchField.isMouseOver(mouseX, mouseY) && !navController.isOverlayOpen()) {
			suggestionsOpen = !searchController.suggestions().isEmpty();
		}
		return handled;
	}

	/** Schickt den Suchbegriff ab (ROADMAP S6): in den Verlauf, als Ergebnis-Filter, Vorschlagsfeld zu. */
	private void submitSearch(String text) {
		searchController.submit(text);
		String t = text.trim();
		if (!t.isEmpty() && de.spassglas.vitrine.config.VitrineConfig.get().saveSearchHistory) {
			de.spassglas.vitrine.config.VitrineConfig.update(s -> {
				s.searchHistory.removeIf(e -> e.equalsIgnoreCase(t));
				s.searchHistory.add(0, t);
				while (s.searchHistory.size() > 8) {
					s.searchHistory.remove(s.searchHistory.size() - 1);
				}
			});
		}
		suggestionsOpen = false;
		dropdownOpen = false;
		searchFilter = "all";
		contentScrollY = 0;
		rebuild();
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	/**
	 * Vanilla legt in {@code super.render(...)} die Menue-Unschaerfe ueber alles
	 * bis dahin Gezeichnete -- bei Vitrine also ueber den eigenen Inhalt, der vor
	 * {@code super.render} gezeichnet wird (im Spiel: verschwommener Text). Den
	 * Hintergrund zeichnet dieser Screen selbst, darum hier bewusst nichts.
	 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, theme().color("color.bg"));

		int contentX = contentX();
		boolean overlayOpen = navController.isOverlayOpen();
		if (overlayOpen) {
			context.fill(contentX, VitrineTheme.HEADER_HEIGHT, this.width, this.height, theme().color("color.overlayScrim"));
		}

		// ROADMAP GATE85: docs/UI-SPEC.md "Bewegung" -- 250 ms Einblenden
		// (8 px Aufwaertsbewegung) nach dem Erzeugen dieses Bildschirms. Nur
		// der Seiteninhalt bewegt sich; Kopfleiste und Nav-Leiste (Chrome)
		// bleiben fest.
		boolean reduceMotion = reduceMotion();
		long pageEntranceElapsedMs = Util.getMeasuringTimeMs() - pageEntranceStartMs;
		float pageOffsetY = PageEntrance.offsetPx(pageEntranceElapsedMs, reduceMotion);
		int pageOverlayAlpha = PageEntrance.overlayAlpha(pageEntranceElapsedMs, reduceMotion);

		context.getMatrices().push();
		context.getMatrices().translate(0, pageOffsetY, 0);
		renderContentPlaceholder(context, contentX);
		renderStatePanels(context, contentX);
		context.getMatrices().pop();

		if (navController.isRailVisible()) {
			context.fill(0, VitrineTheme.HEADER_HEIGHT, currentNavWidth, this.height, theme().color("color.surfaceSolid"));
			context.fill(currentNavWidth - 1, VitrineTheme.HEADER_HEIGHT, currentNavWidth, this.height,
					theme().color("color.borderTranslucent"));
		}

		context.fill(0, 0, this.width, VitrineTheme.HEADER_HEIGHT, theme().color("color.surfaceSolid"));
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, this.width, VitrineTheme.HEADER_HEIGHT,
				theme().color("color.borderTranslucent"));
		if (searchBox != null) {
			boolean focused = searchField != null && searchField.isFocused();
			de.spassglas.vitrine.ui.theme.NineSlice.draw(context,
					focused ? de.spassglas.vitrine.ui.theme.NineSlice.SEARCH_FOCUS : de.spassglas.vitrine.ui.theme.NineSlice.SEARCH,
					searchBox[0], searchBox[1], searchBox[2], searchBox[3]);
			if (searchField != null && !searchField.getText().isEmpty()) {
				int cx = clearX();
				int cy = searchBox[1] + (searchBox[3] - SEARCH_ICON) / 2;
				boolean overClear = mouseX >= cx - 3 && mouseX < cx + SEARCH_ICON + 3 && mouseY >= searchBox[1]
						&& mouseY < searchBox[1] + searchBox[3];
				if (overClear) {
					de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, cx - 3, cy - 3, SEARCH_ICON + 6, SEARCH_ICON + 6,
							4, 0x22FFFFFF);
				}
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(ICON_CLOSE, cx, cy, 0, 0, SEARCH_ICON, SEARCH_ICON, SEARCH_ICON, SEARCH_ICON);
			}
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(ICON_SEARCH, searchBox[0] + searchBox[2] - SEARCH_ICON - VitrineTheme.SPACE_UNIT * 3,
					searchBox[1] + (searchBox[3] - SEARCH_ICON) / 2, SEARCH_ICON, SEARCH_ICON, 0, 0,
					VitrineTheme.UI_ICON_SOURCE_SIZE, VitrineTheme.UI_ICON_SOURCE_SIZE,
					VitrineTheme.UI_ICON_SOURCE_SIZE, VitrineTheme.UI_ICON_SOURCE_SIZE);
		}
		if (settingsButton != null) {
			int s = 20;
			account.renderAvatar(context, settingsButton.getX() + (settingsButton.getWidth() - s) / 2,
					(VitrineTheme.HEADER_HEIGHT - s) / 2, s, theme().color("color.surfaceSolid"), mouseX, mouseY);
		}
		if (brandVisible) {
			int by = (VitrineTheme.HEADER_HEIGHT - BRAND_ICON) / 2;
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(ICON_BRAND, brandX, by, BRAND_ICON, BRAND_ICON, 0, 0, 64, 64, 64, 64);
			de.spassglas.vitrine.ui.theme.ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.brand"),
					brandX + BRAND_ICON + VitrineTheme.CONTROL_GAP,
					(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_BODY) / 2, de.spassglas.vitrine.ui.theme.ActiveThemeColors.text(),
					VitrineTheme.TYPE_BODY);
		}

		// Inhalt (scrollt) unter der Kopfleiste abgeschnitten; Kopfleiste und
		// Nav-Leiste danach unbeschnitten obenauf.
		context.enableScissor(contentX, VitrineTheme.HEADER_HEIGHT, this.width, this.height);
		context.getMatrices().push();
		context.getMatrices().translate(0, pageOffsetY, 0);
		for (var element : this.children()) {
			if (isContentElement(element) && element instanceof net.minecraft.client.gui.Drawable d) {
				d.render(context, mouseX, mouseY, delta);
			}
		}
		context.getMatrices().pop();
		if (pageOverlayAlpha > 0) {
			// Deckt den Seiteninhalt waehrend des Einblendens ab (GATE85):
			// dieselbe Hintergrundfarbe, abnehmende Deckkraft.
			context.fill(contentX, VitrineTheme.HEADER_HEIGHT, this.width, this.height,
					PageEntrance.overlayColor(pageOverlayAlpha, theme().color("color.bg")));
		}
		context.disableScissor();
		for (var element : this.children()) {
			if (!isContentElement(element) && element instanceof net.minecraft.client.gui.Drawable d) {
				d.render(context, mouseX, mouseY, delta);
			}
		}

		renderActiveNavIndicator(context);
		renderFocusRing(context);
		// Ueberlagerungen ganz nach vorne (Text darunter wird spaeter gebuendelt
		// gezeichnet und schien sonst durch die Flaechen durch).
		context.draw();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 400);
		if (dropdownVisible()) {
			updateDropdown();
			dropdown.render(context, this.textRenderer, mouseX, mouseY);
		}
		account.renderMenu(context, this.textRenderer, mouseX, mouseY);
		renderDownloadsFlyout(context, mouseX, mouseY);
		context.getMatrices().pop();
	}

	// --- ROADMAP S8: Download-Flyout ------------------------------------

	/** Linke Kante des Flyouts -- unter der Kopfleisten-Download-Anzeige, rechtsbuendig. */
	private int flyoutLeft() {
		int preferred = downloadsButton.getX() + VitrineTheme.ICON_BUTTON_SIZE - VitrineTheme.FLYOUT_WIDTH;
		return Math.max(VitrineTheme.EDGE_PADDING, preferred);
	}

	/** Innenabstand des Flyouts -- wie im Vorschlagsfeld aus {@link VitrineTheme#SPACE_UNIT} abgeleitet. */
	private static int flyoutPad() {
		return VitrineTheme.SPACE_UNIT * 2;
	}

	/** y-Kante, ab der die erste Download-Zeile (bzw. die Leer-Zeile) beginnt. */
	private int flyoutRowsTop() {
		int y = VitrineTheme.HEADER_HEIGHT + flyoutPad() + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.SPACE_UNIT;
		if (!downloadFlyoutModel.isEmpty() && !downloadFlyoutModel.realProgressAvailable()) {
			// Zeile mit dem ehrlichen K3-Seam-Hinweis -- entfaellt, sobald echter
			// Fortschritt (ROADMAP GATE4) mindestens einmal gemeldet wurde.
			y += VitrineTheme.TYPE_CAPTION + VitrineTheme.SPACE_UNIT;
		}
		return y;
	}

	private int flyoutRowCount() {
		return Math.max(1, downloadFlyoutModel.allRows().size());
	}

	private int flyoutPanelHeight() {
		return flyoutRowsTop() - VitrineTheme.HEADER_HEIGHT
				+ flyoutRowCount() * VitrineTheme.FLYOUT_ROW_HEIGHT + flyoutPad();
	}

	private boolean pointInFlyout(double mouseX, double mouseY) {
		int left = flyoutLeft();
		int top = VitrineTheme.HEADER_HEIGHT;
		return mouseX >= left && mouseX <= left + VitrineTheme.FLYOUT_WIDTH
				&& mouseY >= top && mouseY <= top + flyoutPanelHeight();
	}

	/** Index der Download-Zeile unter {@code mouseY}, oder {@code -1}. */
	private int flyoutRowAt(double mouseX, double mouseY) {
		if (!pointInFlyout(mouseX, mouseY)) {
			return -1;
		}
		int rel = (int) (mouseY - flyoutRowsTop());
		if (rel < 0) {
			return -1;
		}
		int row = rel / VitrineTheme.FLYOUT_ROW_HEIGHT;
		return row < downloadFlyoutModel.allRows().size() ? row : -1;
	}

	/**
	 * Zeichnet das Download-Flyout (docs/UI-SPEC.md "1. Kopfleiste": "laufende
	 * und zuletzt abgeschlossene Installationen, je mit Fortschrittsbalken,
	 * Groesse und Abbrechen"). Direkt in {@link #render} gezeichnet -- wie das
	 * Vorschlagsfeld -- damit der Aufbau die Tab-Reihenfolge nicht veraendert.
	 * Bis ROADMAP K3 gibt es keinen echten, tickenden Fortschritt: laufende
	 * Zeilen zeigen den unbestimmten Zustand plus den Seam-Hinweis, keinen
	 * gefuellten Balken (Charter-Regel 2).
	 */
	private void renderDownloadsFlyout(DrawContext context, int mouseX, int mouseY) {
		if (!downloadsFlyoutOpen || navController.isOverlayOpen()) {
			return;
		}
		int left = flyoutLeft();
		int right = left + VitrineTheme.FLYOUT_WIDTH;
		int top = VitrineTheme.HEADER_HEIGHT;
		int height = flyoutPanelHeight();
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left - 2, top + 4, VitrineTheme.FLYOUT_WIDTH + 4, height + 4,
				12, 0x40000000);
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top + 4, VitrineTheme.FLYOUT_WIDTH, height, 10,
				0xFF272A31);

		int textLeft = left + flyoutPad();
		int textRight = right - flyoutPad();
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.downloads.flyout.title"),
				textLeft, top + flyoutPad(), theme().color("color.text"), VitrineTheme.TYPE_SUBTITLE);

		List<DownloadEntry> rows = downloadFlyoutModel.allRows();
		if (rows.isEmpty()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.downloads.flyout.empty"),
					textLeft, flyoutRowsTop(), theme().color("color.textMuted"), VitrineTheme.TYPE_BODY);
			return;
		}

		if (!downloadFlyoutModel.realProgressAvailable()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable(downloadFlyoutModel.progressNoteKey()),
					textLeft, top + flyoutPad() + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.SPACE_UNIT,
					theme().color("color.textMuted"), VitrineTheme.TYPE_CAPTION);
		}

		int rowTop = flyoutRowsTop();
		for (DownloadEntry entry : rows) {
			renderDownloadRow(context, entry, textLeft, textRight, rowTop, mouseX, mouseY);
			rowTop += VitrineTheme.FLYOUT_ROW_HEIGHT;
		}
	}

	private void renderDownloadRow(DrawContext context, DownloadEntry entry, int left, int right, int rowTop,
			int mouseX, int mouseY) {
		boolean hovered = mouseX >= left - flyoutPad() && mouseX <= right + flyoutPad()
				&& mouseY >= rowTop && mouseY < rowTop + VitrineTheme.FLYOUT_ROW_HEIGHT;
		if (hovered) {
			context.fill(left - flyoutPad(), rowTop, right + flyoutPad(), rowTop + VitrineTheme.FLYOUT_ROW_HEIGHT,
					theme().color("color.surfaceTranslucent"));
		}

		String name = TileLabel.truncateToWidth(entry.displayName(), right - left,
				t -> ScaledText.width(this.textRenderer, t, VitrineTheme.TYPE_BODY));
		ScaledText.draw(context, this.textRenderer, Text.literal(name), left, rowTop + VitrineTheme.SPACE_UNIT,
				theme().color("color.text"), VitrineTheme.TYPE_BODY);

		// Zweite Zeile: Groesse + Phasentext bzw. "Laufend", rechts "Abbrechen".
		int line2Y = rowTop + VitrineTheme.SPACE_UNIT + VitrineTheme.TYPE_BODY + VitrineTheme.SPACE_UNIT / 2;
		String meta = HumanReadableSize.format(entry.sizeBytes())
				.map(size -> size + "  ")
				.orElse("");
		Text phase = Text.translatable(entry.phase().translationKey());
		ScaledText.draw(context, this.textRenderer,
				Text.literal(meta).append(phase), left, line2Y,
				theme().color("color.textMuted"), VitrineTheme.TYPE_CAPTION);

		Text cancel = Text.translatable("vitrine.downloads.cancel");
		int cancelWidth = ScaledText.width(this.textRenderer, cancel.getString(), VitrineTheme.TYPE_CAPTION);
		ScaledText.draw(context, this.textRenderer, cancel, right - cancelWidth, line2Y,
				theme().color("color.accent"), VitrineTheme.TYPE_CAPTION);

		// Dritte Zeile: Fortschritt. Ohne echten Anteil (Seam bis K3) nur die
		// Spur plus "Laeuft ..." -- kein gefuellter Balken, der nichts bedeutet.
		int barY = rowTop + VitrineTheme.FLYOUT_ROW_HEIGHT - flyoutPad() - VitrineTheme.FLYOUT_PROGRESS_HEIGHT;
		context.fill(left, barY, right, barY + VitrineTheme.FLYOUT_PROGRESS_HEIGHT,
				theme().color("color.surfaceTranslucent"));
		if (entry.progress().isPresent()) {
			int fillRight = left + (int) Math.round((right - left) * entry.progress().getAsDouble());
			context.fill(left, barY, fillRight, barY + VitrineTheme.FLYOUT_PROGRESS_HEIGHT,
					theme().color("color.accent"));
		} else if (entry.phase() == DownloadPhase.RUNNING) {
			ScaledText.draw(context, this.textRenderer,
					Text.translatable("vitrine.downloads.progress.indeterminate"),
					left, barY - VitrineTheme.TYPE_CAPTION - VitrineTheme.SPACE_UNIT / 2,
					theme().color("color.textMuted"), VitrineTheme.TYPE_CAPTION);
		}
	}

	/**
	 * Zeichnet das Vorschlagsfeld unter der Suchleiste (ROADMAP S6,
	 * docs/UI-SPEC.md „1. Kopfleiste"): erst die letzten Suchen (mit
	 * Lupensymbol), dann die Live-Treffer zum aktuellen Eingabetext (mit
	 * Typ-Symbol und Typ-Badge), getrennt durch eine Linie. Direkt in
	 * {@link #render} gezeichnet -- so folgt das Feld dem Live-Text ohne
	 * Neuaufbau und ohne den Eingabefokus zu verlieren. Die Geometrie und die
	 * Klick-Treffer liefert das client-freie {@link SearchSuggestionsLayout}.
	 */
	private void renderSuggestions(DrawContext context, int mouseX, int mouseY) {
		if (!suggestionsOpen || searchField == null || navController.isOverlayOpen()) {
			return;
		}
		SearchSuggestions suggestions = searchController.suggestions();
		if (suggestions.isEmpty()) {
			return;
		}
		int left = searchField.getX();
		int width = searchField.getWidth();
		int right = left + width;
		SearchSuggestionsLayout layout =
				new SearchSuggestionsLayout(suggestions, VitrineTheme.HEADER_HEIGHT, suggestionRowHeight());

		context.fill(left, layout.panelTop(), right, layout.panelBottom(), theme().color("color.surfaceSolid"));

		int iconSize = VitrineTheme.TYPE_ICON_DRAW_SIZE;
		int iconX = left + VitrineTheme.SPACE_UNIT * 2;
		int textX = iconX + iconSize + VitrineTheme.SPACE_UNIT * 2;
		for (SearchSuggestionsLayout.Row row : layout.rows()) {
			if (mouseX >= left && mouseX <= right && row.containsY(mouseY)) {
				context.fill(left, row.top(), right, row.bottom(), theme().color("color.surfaceTranslucent"));
			}
			int iconY = row.top() + (suggestionRowHeight() - iconSize) / 2;
			int textY = row.top() + (suggestionRowHeight() - VitrineTheme.TYPE_BODY) / 2;

			if (row.kind() == SearchSuggestionsLayout.RowKind.RECENT) {
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(SUGGESTION_RECENT_ICON, iconX, iconY, iconSize, iconSize, 0.0F, 0.0F,
						VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE,
						VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE);
				int maxWidth = right - textX - VitrineTheme.SPACE_UNIT * 2;
				String label = TileLabel.truncateToWidth(suggestions.recentSearches().get(row.index()), maxWidth,
						t -> ScaledText.width(this.textRenderer, t, VitrineTheme.TYPE_BODY));
				ScaledText.draw(context, this.textRenderer, Text.literal(label), textX, textY,
						theme().color("color.text"), VitrineTheme.TYPE_BODY);
			} else {
				TileData tile = suggestions.liveMatches().get(row.index());
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(Identifier.of("vitrine", tile.type().iconPath()), iconX, iconY, iconSize, iconSize,
						0.0F, 0.0F, VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
						VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);

				Text badge = Text.translatable(tile.type().translationKey());
				int badgeWidth = ScaledText.width(this.textRenderer, badge.getString(), VitrineTheme.TYPE_CAPTION);
				int badgeX = right - VitrineTheme.SPACE_UNIT * 2 - badgeWidth;
				ScaledText.draw(context, this.textRenderer, badge, badgeX,
						row.top() + (suggestionRowHeight() - VitrineTheme.TYPE_CAPTION) / 2,
						theme().color("color.textMuted"), VitrineTheme.TYPE_CAPTION);

				int maxWidth = badgeX - textX - VitrineTheme.SPACE_UNIT * 2;
				String name = TileLabel.truncateToWidth(tile.name(), maxWidth,
						t -> ScaledText.width(this.textRenderer, t, VitrineTheme.TYPE_BODY));
				ScaledText.draw(context, this.textRenderer, Text.literal(name), textX, textY,
						theme().color("color.text"), VitrineTheme.TYPE_BODY);
			}
		}

		int dividerY = layout.dividerY();
		if (dividerY >= 0) {
			context.fill(left, dividerY, right, dividerY + 1, theme().color("color.borderTranslucent"));
		}
		context.drawBorder(left, layout.panelTop(), width, layout.panelHeight(), theme().color("color.borderTranslucent"));
	}

	/** Linke Kante des Inhaltsbereichs: neben einer angedockten Rail, sonst 0 (die Ueberlagerung schwebt darueber). */
	private int contentX() {
		return navController.isDocked() ? currentNavWidth : 0;
	}

	/**
	 * ROADMAP S7: zeichnet das aktive Zustands-Panel (kein Netz / Katalog
	 * kaputt / Lade-Skeleton oder Suche ohne Treffer) mittig in den
	 * Inhaltsbereich. Der zugehoerige eine Knopf ist ein regulaeres Kind aus
	 * dem Aufbau; das Panel setzt nur Symbol, Ueberschrift und Erklaersatz.
	 */
	private void renderStatePanels(DrawContext context, int contentX) {
		StoreStateView view = catalogStateView != null ? catalogStateView : noResultsStateView;
		if (view == null || navController.isOverlayOpen()) {
			return;
		}
		int centerX = contentX + (this.width - contentX) / 2;
		int topY = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE - contentScrollY;
		if (noResultsStateView != null) {
			// unter der Sortier-/Filterzeile der Suche
			topY += VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.SPACE_GUTTER;
		}
		StoreStatePanel.render(context, this.textRenderer, view, centerX, topY,
				this.width - contentX - VitrineTheme.SPACE_PAGE * 2);
	}

	private void renderContentPlaceholder(DrawContext context, int contentX) {
		if (selectedEntry == NavRailEntry.START || heroCarousel != null || startRow != null || catalogStateView != null || noResultsStateView != null
				|| searchRow != null || sortButton != null) {
			// START/Suche zeigen echte Kacheln oder ein Zustands-Panel -- kein "kommt spaeter"-Text.
			return;
		}
		if (navController.isOverlayOpen() || searchViewActive()) {
			// Offenes Menue: der Inhalt ist absichtlich leer (wird nach dem
			// Schliessen aufgebaut). Kategorien sind gebaut (gefilterte Kacheln) --
			// "kommt in einem spaeteren Update" waere hier schlicht falsch. Im
			// Spiel gefunden (2026-10-06): der Satz lag quer ueber dem Menue.
			return;
		}
		int centerX = contentX + (this.width - contentX) / 2;
		int centerY = VitrineTheme.HEADER_HEIGHT + (this.height - VitrineTheme.HEADER_HEIGHT) / 2;
		Text label = de.spassglas.vitrine.ui.theme.ScaledText.styled(
				Text.translatable("vitrine.content.placeholder", Text.translatable(selectedEntry.translationKey())));
		context.drawCenteredTextWithShadow(this.textRenderer, label, centerX, centerY, theme().color("color.textMuted"));
	}

	private void renderActiveNavIndicator(DrawContext context) {
		// umgekocht (2026-10-07): kein zweiter, grosser Balken am Rand -- der gewaehlte
		// Eintrag zeigt seine Markierung selbst (ganze Flaeche + kleiner Akzentstrich).
		if (true || !navController.isRailVisible()) {
			return;
		}
		ButtonWidget selected = navButtons.get(selectedEntry);
		if (selected == null) {
			return;
		}
		context.fill(0, selected.getY(), VitrineTheme.NAV_ACTIVE_BAR_WIDTH, selected.getY() + selected.getHeight(),
				theme().color("color.accent"));
	}

	/**
	 * ROADMAP S9: sichtbarer Fokusrahmen (Token {@code focus.ring.*}) um das
	 * fokussierte selbstgebaute Widget (Kachelreihe, Hero-Karussell). Vanilla-
	 * {@link ButtonWidget} -- Nav-Eintraege, Kopfleisten-Knoepfe -- bringt seinen
	 * eigenen Rahmen mit und wird hier uebersprungen (nicht doppelt).
	 */
	private void renderFocusRing(DrawContext context) {
		if (!(this.getFocused() instanceof ClickableWidget widget) || widget instanceof ButtonWidget) {
			return;
		}
		FocusRing.drawFor(context, widget);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
