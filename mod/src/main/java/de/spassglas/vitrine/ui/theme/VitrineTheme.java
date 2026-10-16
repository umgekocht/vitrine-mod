package de.spassglas.vitrine.ui.theme;

/**
 * Interne Theme-Tokens fuer das Store-Layout-Geruest (ROADMAP S0).
 *
 * <p>Charter-Regel 7 verlangt, dass keine Farbe, kein Radius, kein Blur und
 * keine Schriftgroesse im Store-Code hartkodiert ist -- alles kommt aus
 * Theme-Tokens. Die vollstaendige Theme-Paket-Infrastruktur (T1
 * Theme-Token-Schema in {@code registry/schema/theme.schema.json}, T2
 * ThemeManager) ist laut ROADMAP.md erst in M5 dran. Bis dahin ist diese
 * Klasse -- analog zu {@code designer/theme-tokens.js} (D1/D3, siehe dortiger
 * Kommentarkopf) -- ein eigenes, internes Token-Set, das <em>nicht</em> die
 * zweite Seite von "ein Schema, zwei Seiten" (Charter-Regel 8) ist: es gibt
 * noch keine gemeinsame Schema-Datei, die Mod und Designer beide lesen. Wenn
 * T1/T2 gebaut werden, ist dieses Set (zusammen mit dem Designer-Pendant) die
 * Grundlage dafuer, keine zweite, abweichende Werte-Erfindung.</p>
 *
 * <p>Masse und Umbruchpunkte stammen wortgleich aus
 * {@code docs/UI-SPEC.md} ("Raster und Masse", "Typo-Stufen", "Bewegung").
 * Die Farben sind die dunkle Variante aus {@code designer/theme-tokens.js}
 * (Theme "Klarglas"), weil der Store als durchscheinende Flaeche ueber dem
 * (meist dunklen) Spielhintergrund liegt -- eine echte hell/dunkel-Umschaltung
 * kommt erst mit T2. Alle weiteren Konstanten (ab "Zusaetzliche Masse")
 * benennt diese Klasse selbst, abgeleitet aus {@link #SPACE_UNIT}, weil die
 * Spec-Tabelle sie nicht einzeln auffuehrt, aber der Store-Code trotzdem
 * keine nackte Zahl verwenden darf.</p>
 */
public final class VitrineTheme {

	private VitrineTheme() {
	}

	// --- Raster und Masse (docs/UI-SPEC.md, "Raster und Masse") ---
	public static int SPACE_UNIT = 4;
	public static int SPACE_GUTTER = 20;
	public static int SPACE_PAGE = 32;
	public static int RADIUS_CARD = 8;
	public static int RADIUS_CONTROL = 4;
	public static int NAV_WIDTH_EXPANDED = 280;
	public static int NAV_WIDTH_COMPACT = 72;
	public static int HEADER_HEIGHT = 56;
	/**
	 * Weichzeichnung hinter Vitrine-Screens ({@code blur.surface}), ersetzt dort
	 * Vanillas Menue-Unschaerfe. Wer die Unschaerfe in den Minecraft-Optionen
	 * auf 0 gestellt hat, bekommt weiterhin keine (siehe GameRendererBlurMixin).
	 */
	public static int BLUR_SURFACE = SPACE_UNIT * 3;
	public static int CARD_WIDTH = 200;
	public static int CARD_ASPECT_WIDTH = 16;
	public static int CARD_ASPECT_HEIGHT = 9;
	public static int HERO_ASPECT_WIDTH = 21;
	public static int HERO_ASPECT_HEIGHT = 9;
	public static int ELEVATION_REST = 2;
	public static int ELEVATION_HOVER = 8;

	// --- Umbruchpunkte (docs/UI-SPEC.md, "Raster und Masse") ---
	/** Unter dieser Fensterbreite (px) wird die Nav-Rail kompakt (nur Icons). */
	public static int BREAKPOINT_NAV_COMPACT = 1000;
	/** Unter dieser Fensterbreite (px) wird die Nav-Rail zur Ueberlagerung. */
	public static int BREAKPOINT_NAV_OVERLAY = 700;

	// --- Typo-Stufen (docs/UI-SPEC.md, "Typo-Stufen") ---
	public static int TYPE_TITLE_LARGE = 28;
	public static int TYPE_TITLE = 20;
	public static int TYPE_SUBTITLE = 16;
	public static int TYPE_BODY = 14;
	public static int TYPE_CAPTION = 12;

	// --- Bewegung (docs/UI-SPEC.md, "Bewegung") ---
	public static int MOTION_HOVER_MS = 150;
	public static int MOTION_PAGE_MS = 250;
	public static int MOTION_PAGE_LIFT_PX = 8;
	public static int MOTION_CAROUSEL_MS = 400;

	// --- Zusaetzliche Masse, aus SPACE_UNIT abgeleitet ---
	// (Spec-Tabelle nennt sie nicht einzeln, das Layout-Geruest braucht sie
	// trotzdem -- lieber ein benannter, aus dem Raster abgeleiteter Token als
	// eine nackte Zahl im Screen-Code.)
	public static int EDGE_PADDING = SPACE_UNIT * 2;
	public static int CONTROL_GAP = SPACE_UNIT * 2;
	public static int ICON_BUTTON_SIZE = HEADER_HEIGHT - EDGE_PADDING * 2;
	public static int SEARCH_FIELD_HEIGHT = SPACE_UNIT * 5;
	public static int NAV_ITEM_HEIGHT = SPACE_UNIT * 10;
	public static int NAV_SEPARATOR_GAP = SPACE_UNIT * 3;
	public static int NAV_ACTIVE_BAR_WIDTH = SPACE_UNIT;
	public static int MENU_BUTTON_WIDTH = SPACE_UNIT * 30;
	public static int MENU_BUTTON_HEIGHT = SPACE_UNIT * 5;

	/** Barrierefreiheit: "sichtbarer Fokusrahmen aus einem eigenen Token" (docs/UI-SPEC.md). */
	public static int FOCUS_RING_WIDTH = 2;

	// --- Kachel/Kachelreihe (ROADMAP S1), aus SPACE_UNIT/CARD_* abgeleitet ---
	// (docs/UI-SPEC.md "4. Kachel"/"3. Start" nennen Aufbau und Inhalt, aber
	// keine zusaetzlichen Pixelwerte -- diese Ableitungen sind der Store-
	// Code-eigene, benannte Ersatz fuer sonst noetige nackte Zahlen.)
	/** Platz unterhalb des 16:9-Bildbereichs fuer Name, Autor und Typ-Badge einer Kachel. */
	public static int CARD_TEXT_BLOCK_HEIGHT = SPACE_UNIT * 11;
	/** Innenabstand zwischen Bildkante und Statuszeichen/Typ-Badge auf der Kachel. */
	public static int CARD_BADGE_INSET = SPACE_UNIT;
	/** Abstand zwischen den Textzeilen (Name/Autor) innerhalb einer Kachel. */
	public static int CARD_TEXT_LINE_GAP = SPACE_UNIT / 2;
	/** Abstand zwischen Kachelreihen-Ueberschrift/Unterzeile und der ersten Kachelreihe. */
	public static int ROW_HEADER_GAP = SPACE_UNIT * 3;
	/** Hoehe des Reihen-Kopfbereichs (Ueberschrift + Unterzeile), aus den Typo-Stufen abgeleitet. */
	public static int ROW_HEADER_HEIGHT = TYPE_TITLE + CARD_TEXT_LINE_GAP + TYPE_BODY + SPACE_UNIT;
	/** Breite der Chevron-Schaltflaechen am Rand einer Kachelreihe. */
	public static int CHEVRON_WIDTH = SPACE_UNIT * 6;

	// --- Zustaende (ROADMAP S7), aus SPACE_UNIT / Asset-Fakten abgeleitet ---
	// (docs/UI-SPEC.md "7. Zustaende" nennt je Zustand Bild, Ueberschrift,
	// Erklaersatz und Knopf, aber keine Pixelwerte -- diese Werte sind der
	// Zustands-Panel-eigene, benannte Ersatz fuer sonst noetige nackte Zahlen.)
	/** Kantenlaenge, in der das Zustands-Symbol (leer / keine Treffer / Warnung) mittig gezeichnet wird. */
	public static int STATE_SYMBOL_SIZE = SPACE_UNIT * 16;
	/**
	 * Natuerliche Aufloesung von {@code gui/empty_state.png} (ASSETS-NEEDED.md:
	 * 192x192) -- Asset-Fakt fuers UV-Mapping, keine Design-Zahl.
	 */
	public static final int STATE_EMPTY_SOURCE_SIZE = 192;
	/** Vertikaler Abstand zwischen Symbol, Ueberschrift, Erklaersatz und Knopf im Zustands-Panel. */
	public static int STATE_CONTENT_GAP = SPACE_UNIT * 4;

	// --- Hero-Karussell + Download-Flyout (ROADMAP S8), aus SPACE_UNIT abgeleitet ---
	// (docs/UI-SPEC.md "3. Start" / "1. Kopfleiste" nennen Aufbau und Verhalten
	// -- 21:9-Karussell mit Punkte-Anzeige und Pfeilen, Download-Flyout mit
	// Fortschrittsbalken, Groesse und Abbrechen -- aber keine Pixelwerte. Diese
	// Ableitungen sind der Widget-Code-eigene, benannte Ersatz fuer sonst noetige
	// nackte Zahlen. Die 7-Sekunden-Auto-Wechsel- und die Kurz-Pause-Dauer sind
	// kein gestalterisches Mass, sondern Verhaltens-Zeiten -- sie stehen als
	// Konstanten in HeroCarouselModel (wie DEBOUNCE_DELAY_MILLIS in
	// HotApplyCoordinator), nicht hier.)
	/** Kantenlaenge eines Punkts der Karussell-Seitenanzeige (unten rechts). */
	public static int CAROUSEL_DOT_SIZE = SPACE_UNIT * 2;
	/** Breite der Pfeil-Trefferflaeche links/rechts am Karussell (erscheint beim Ueberfahren). */
	public static int CAROUSEL_ARROW_WIDTH = SPACE_UNIT * 8;
	/** Breite des Download-Flyouts unter der Kopfleisten-Download-Anzeige. */
	public static int FLYOUT_WIDTH = SPACE_UNIT * 80;
	/** Hoehe einer Flyout-Zeile (Name, Groesse, Fortschrittsbalken, Abbrechen). */
	public static int FLYOUT_ROW_HEIGHT = SPACE_UNIT * 16;
	/** Hoehe des Fortschrittsbalkens einer Flyout-Zeile. */
	public static int FLYOUT_PROGRESS_HEIGHT = SPACE_UNIT;

	// --- Detailseite (ROADMAP S3), aus SPACE_UNIT/CARD_*/MENU_BUTTON_* abgeleitet ---
	// (docs/UI-SPEC.md "5. Detailseite" nennt Aufbau und Reihenfolge, aber
	// keine zusaetzlichen Pixelwerte -- diese Ableitungen sind der Screen-
	// Code-eigene, benannte Ersatz fuer sonst noetige nackte Zahlen.)
	/** Breite des grossen Kopfbild-/Icon-Bereichs (16:9) auf der Detailseite -- "großes Bild links". */
	public static int DETAIL_HEADER_IMAGE_WIDTH = CARD_WIDTH * 2;
	/** Hoehe des Kopfbild-Bereichs, aus {@link #DETAIL_HEADER_IMAGE_WIDTH} und {@link #CARD_ASPECT_HEIGHT}/{@link #CARD_ASPECT_WIDTH}. */
	public static int DETAIL_HEADER_IMAGE_HEIGHT =
			DETAIL_HEADER_IMAGE_WIDTH * CARD_ASPECT_HEIGHT / CARD_ASPECT_WIDTH;
	/** Vertikaler Abstand zwischen zwei Detailseiten-Abschnitten. */
	public static int DETAIL_SECTION_GAP = SPACE_UNIT * 6;
	/** Abstand zwischen einer Abschnitts-Ueberschrift und ihrem Inhalt. */
	public static int DETAIL_HEADING_GAP = SPACE_UNIT * 2;
	/** Zeilenabstand innerhalb eines Detailseiten-Abschnitts (Faehigkeits-/Detailzeilen). */
	public static int DETAIL_LINE_GAP = SPACE_UNIT + SPACE_UNIT / 2;
	/** Breite des einen gefuellten Aktionsknopfs -- wie der Menue-Knopf. */
	public static int DETAIL_ACTION_BUTTON_WIDTH = MENU_BUTTON_WIDTH;
	/** Hoehe des Aktionsknopfs. */
	public static int DETAIL_ACTION_BUTTON_HEIGHT = MENU_BUTTON_HEIGHT;
	/** Breite einer Kachel in der Screenshot-Galerie (16:9), wie eine Standardkachel. */
	public static int DETAIL_SCREENSHOT_WIDTH = CARD_WIDTH;
	/** Hoehe einer Screenshot-Kachel, aus {@link #DETAIL_SCREENSHOT_WIDTH} und dem 16:9-Verhaeltnis. */
	public static int DETAIL_SCREENSHOT_HEIGHT =
			DETAIL_SCREENSHOT_WIDTH * CARD_ASPECT_HEIGHT / CARD_ASPECT_WIDTH;
	/** Kantenlaenge des Icons je Faehigkeitszeile im Abschnitt "Das darf dieses Paket" (16 px, wie die Statuszeichen). */
	public static int DETAIL_CAPABILITY_ICON_SIZE = SPACE_UNIT * 4;
	/** Scrollschritt der Detailseite je Mausrad-Raste. */
	public static int DETAIL_SCROLL_STEP = NAV_ITEM_HEIGHT;

	/**
	 * Natuerliche Pixelgroesse der mitgelieferten Status-Symbole
	 * ({@code ui_check.png}/{@code ui_download.png}/{@code ui_warning.png}, je
	 * 16x16) -- keine Design-Entscheidung, sondern die tatsaechliche
	 * Asset-Aufloesung, noetig fuer korrektes UV-Mapping beim Zeichnen.
	 */
	public static final int STATUS_ICON_SOURCE_SIZE = 16;
	/** Zielgroesse, in der ein Statuszeichen auf der Kachel gezeichnet wird. */
	public static int STATUS_ICON_DRAW_SIZE = STATUS_ICON_SOURCE_SIZE;
	/**
	 * Natuerliche Pixelgroesse der mitgelieferten Kategorie-Symbole
	 * ({@code category_*.png}, je 32x32) -- ebenfalls Asset-Aufloesung, nicht
	 * Design.
	 */
	public static final int TYPE_ICON_SOURCE_SIZE = 32;
	/** Zielgroesse, in der das Typ-Symbol als Ecken-Badge auf der Kachel gezeichnet wird. */
	public static int TYPE_ICON_DRAW_SIZE = STATUS_ICON_SOURCE_SIZE;
	/** Zielgroesse des grossen, mittigen Typ-Symbols im Bild-Platzhalter einer Kachel ohne eigenes Vorschaubild. */
	public static int TYPE_ICON_PLACEHOLDER_DRAW_SIZE = TYPE_ICON_SOURCE_SIZE;
	/**
	 * Natuerliche Aufloesung von {@code gui/placeholder_card.png} (ASSETS-NEEDED.md:
	 * 512x288) -- Asset-Fakt fuers UV-Mapping, keine Design-Zahl.
	 */
	public static final int PLACEHOLDER_CARD_SOURCE_WIDTH = 512;
	public static final int PLACEHOLDER_CARD_SOURCE_HEIGHT = 288;
	/** Natuerliche Pixelgroesse der Chevron-Symbole ({@code ui_chevron_left/right.png}, je 16x16). */
	public static final int CHEVRON_ICON_SOURCE_SIZE = 16;
	/** Natuerliche Pixelgroesse der Nav-Rail-Symbole ({@code nav_*_outline/filled.png}, je 20x20). */
	public static final int NAV_ICON_SOURCE_SIZE = 20;
	/** Natuerliche Pixelgroesse der Bedien-Symbole ({@code ui_*.png}, je 16x16). */
	public static final int UI_ICON_SOURCE_SIZE = 16;

	// --- Farben (Theme "Klarglas", dunkle Variante -- siehe designer/theme-tokens.js) ---
	public static final int COLOR_BG = 0xFF1E2026; // assets v2: window background
	public static final int COLOR_SURFACE_SOLID = 0xFF23252B; // assets v2: panel
	public static final int COLOR_TEXT = 0xFFE8ECF4; // assets v2: text
	public static final int COLOR_TEXT_MUTED = 0xFFB3B3BA;
	public static final int COLOR_ACCENT = 0xFF7EA1FF;
	public static final int COLOR_ACCENT_CONTRAST = 0xFF14141A;
	public static final int COLOR_DISABLED_TEXT = 0xFF6C6C74;
	/**
	 * Warn-/Fehlerfarbe. Gegenueber dem urspruenglichen {@code #b3261e} leicht
	 * aufgehellt, damit der Fehlertext auf {@link #COLOR_BG} die 3:1-Schwelle
	 * fuer Bedienelemente haelt (docs/UI-SPEC.md "Barrierefreiheit"; 4.5:1 ist
	 * fuer ein gesaettigtes Rot auf fast schwarzem Grund nicht erreichbar, ohne
	 * die gedaempfte Anmutung zu sprengen -- der Fehlerzustand traegt laut
	 * ROADMAP S9 ohnehin zusaetzlich Symbol und Text). Auf dem hellen
	 * {@code color.bg} der Klarglas-Hell-Reihe bleibt der Wert &ge; 4.5:1.
	 * Nachgerechnet in {@code KlarglasContrastTest}.
	 */
	public static final int COLOR_DANGER = 0xFFCC351E;
	/** Durchscheinende Flaeche, alpha ~0.72 (designer/theme-tokens.js: color.surface, dark). */
	public static final int COLOR_SURFACE_TRANSLUCENT = 0xB728282E;
	/** Rand, alpha ~0.14 (designer/theme-tokens.js: color.border, dark). */
	public static final int COLOR_BORDER_TRANSLUCENT = 0x23FFFFFF;
	/** Dimmt den Inhaltsbereich hinter der Nav-Rail-Ueberlagerung. */
	public static final int COLOR_OVERLAY_SCRIM = 0x99000000;
	/** Fokusrahmenfarbe -- eigener Token, kein Vanilla-Standardwert. */
	public static final int FOCUS_RING_COLOR = COLOR_ACCENT;

	// --- Fähigkeiten-Dialog (ROADMAP S4), aus SPACE_UNIT/CARD_WIDTH abgeleitet ---
	// (docs/UI-SPEC.md "8. Fähigkeiten-Dialog" nennt Aufbau und "zwei
	// gleichwertige Knöpfe", aber keine Pixelwerte -- diese Ableitungen sind
	// der Dialog-Code-eigene, benannte Ersatz für sonst nötige nackte Zahlen.
	// Die beiden Knöpfe teilen sich Größe und Stil mit dem Menü-Knopf
	// (MENU_BUTTON_WIDTH/HEIGHT), damit keiner hervorgehoben wirkt.)
	/** Breite der Dialog-Fläche -- wie das große Detail-Kopfbild, eine Standardkachel doppelt. */
	public static int DIALOG_WIDTH = CARD_WIDTH * 2;
	/** Innenabstand der Dialog-Fläche. */
	public static int DIALOG_PADDING = SPACE_UNIT * 5;
	/** Abstand zwischen den beiden gleichwertigen Dialog-Knöpfen. */
	public static int DIALOG_BUTTON_GAP = SPACE_UNIT * 3;

	// --- Bibliothek (ROADMAP S5), aus SPACE_UNIT/CARD_*/MENU_BUTTON_* abgeleitet ---
	// (docs/UI-SPEC.md "6. Bibliothek" nennt Aufbau -- Liste statt Kacheln,
	// Aktiv-Schalter, Ueberlaufmenue, Abschnitt "Reihenfolge", Konflikthinweis,
	// "Alle aktualisieren" -- aber keine Pixelwerte. Diese Ableitungen sind der
	// Screen-Code-eigene, benannte Ersatz fuer sonst noetige nackte Zahlen.)
	/** Hoehe einer Bibliotheks-Listenzeile (Name, Version, Typ, Schalter, Ueberlaufmenue). */
	public static int LIBRARY_ROW_HEIGHT = SPACE_UNIT * 12;
	/** Vertikaler Abstand zwischen zwei Bibliotheks-Listenzeilen. */
	public static int LIBRARY_ROW_GAP = SPACE_UNIT * 4;
	/** Breite des Aktiv/Inaktiv-Schalters am rechten Rand einer Zeile. */
	public static int LIBRARY_TOGGLE_WIDTH = SPACE_UNIT * 22;
	/** Breite der Nach-oben/Nach-unten-Schaltflaechen im Abschnitt "Reihenfolge" (Tastatur-Alternative zum Ziehen). */
	public static int LIBRARY_REORDER_BUTTON_WIDTH = SPACE_UNIT * 7;
	/** Vertikaler Abstand zwischen den Bibliotheks-Abschnitten (Liste / "Reihenfolge" / Konflikthinweis). */
	public static int LIBRARY_SECTION_GAP = SPACE_UNIT * 6;
	/** Scrollschritt der Bibliothek je Mausrad-Raste. */
	public static int LIBRARY_SCROLL_STEP = NAV_ITEM_HEIGHT;

	// --- HUD-Laufzeit (ROADMAP T5) -----------------------------------------
	// Der HUD-Renderer darf keine nackte Farbe/Maszahl tragen (Charter-Regel 7).
	// Gibt ein HUD-Paket eigene Werte an (z. B. 'color' am Balken, 'width' am
	// Icon), gewinnen die -- das ist Paketdatum. Diese Tokens greifen nur als
	// Rueckfall, wenn das Paket nichts vorgibt.
	/** HUD-Textfarbe (Text- und Listen-Widgets). */
	public static final int HUD_TEXT_COLOR = COLOR_TEXT;
	/** Ruecklauffarbe (leerer Teil) eines HUD-Balkens. */
	public static final int HUD_BAR_TRACK_COLOR = COLOR_SURFACE_SOLID;
	/** Fuellfarbe eines HUD-Balkens, wenn das Paket keine eigene #rrggbb-Farbe angibt. */
	public static final int HUD_BAR_FILL_COLOR = COLOR_ACCENT;
	/** Standard-Balkenlaenge, wenn das HUD-Paket kein {@code width} angibt. */
	public static int HUD_BAR_DEFAULT_WIDTH = SPACE_UNIT * 20;
	/** Standard-Balkenhoehe, wenn das HUD-Paket kein {@code height} angibt. */
	public static int HUD_BAR_DEFAULT_HEIGHT = SPACE_UNIT;
	/** Kantenlaenge, mit der ein HUD-Icon ohne eigene {@code width}/{@code height} gezeichnet wird. */
	public static int HUD_ICON_DEFAULT_SIZE = STATUS_ICON_SOURCE_SIZE;
	/** Zeilenabstand zwischen den Eintraegen eines HUD-Listen-Widgets. */
	public static int HUD_LIST_LINE_GAP = SPACE_UNIT / 2;

	/**
	 * GATE21 (Charter-Regel 7): uebernimmt die Masze des aktiven Themes.
	 *
	 * <p>Vorher waren alle Masze {@code static final} -- der Compiler setzt
	 * solche Zahlen fest in jede benutzende Klasse ein, kein Theme kam je an.
	 * Nur die Farben liefen ueber das Theme. Jetzt sind die themebaren Felder
	 * veraenderlich; diese Methode setzt sie aus {@code size} und rechnet die
	 * abgeleiteten Felder in derselben Reihenfolge wie bei der Initialisierung
	 * neu. Reine Asset-Fakten (natuerliche Bildgroessen) bleiben final.</p>
	 *
	 * <p>{@link DefaultThemeTokens} haelt die Ausgangswerte vorher fest
	 * (einmalig bei seiner Erzeugung) -- deshalb wird es hier zuerst
	 * angefasst, damit das Standard-Theme nie die Werte eines geladenen
	 * Themes uebernimmt.</p>
	 */
	public static synchronized void applyFrom(java.util.function.ToIntFunction<String> size) {
		java.util.Objects.requireNonNull(size, "size");
		DefaultThemeTokens.INSTANCE.sizeTokens();
		SPACE_UNIT = size.applyAsInt("space.unit");
		SPACE_GUTTER = size.applyAsInt("space.gutter");
		SPACE_PAGE = size.applyAsInt("space.page");
		RADIUS_CARD = size.applyAsInt("radius.card");
		RADIUS_CONTROL = size.applyAsInt("radius.control");
		NAV_WIDTH_EXPANDED = size.applyAsInt("nav.width.expanded");
		NAV_WIDTH_COMPACT = size.applyAsInt("nav.width.compact");
		HEADER_HEIGHT = size.applyAsInt("header.height");
		BLUR_SURFACE = size.applyAsInt("blur.surface");
		CARD_WIDTH = size.applyAsInt("card.width");
		CARD_ASPECT_WIDTH = size.applyAsInt("card.aspect.width");
		CARD_ASPECT_HEIGHT = size.applyAsInt("card.aspect.height");
		HERO_ASPECT_WIDTH = size.applyAsInt("hero.aspect.width");
		HERO_ASPECT_HEIGHT = size.applyAsInt("hero.aspect.height");
		ELEVATION_REST = size.applyAsInt("elevation.rest");
		ELEVATION_HOVER = size.applyAsInt("elevation.hover");
		BREAKPOINT_NAV_COMPACT = size.applyAsInt("breakpoint.nav.compact");
		BREAKPOINT_NAV_OVERLAY = size.applyAsInt("breakpoint.nav.overlay");
		TYPE_TITLE_LARGE = size.applyAsInt("type.titleLarge");
		TYPE_TITLE = size.applyAsInt("type.title");
		TYPE_SUBTITLE = size.applyAsInt("type.subtitle");
		TYPE_BODY = size.applyAsInt("type.body");
		TYPE_CAPTION = size.applyAsInt("type.caption");
		MOTION_HOVER_MS = size.applyAsInt("motion.hover.duration");
		MOTION_PAGE_MS = size.applyAsInt("motion.page.duration");
		MOTION_PAGE_LIFT_PX = size.applyAsInt("motion.page.liftDistance");
		MOTION_CAROUSEL_MS = size.applyAsInt("motion.carousel.duration");
		EDGE_PADDING = size.applyAsInt("space.edgePadding");
		CONTROL_GAP = size.applyAsInt("space.controlGap");
		ICON_BUTTON_SIZE = size.applyAsInt("icon.button.size");
		SEARCH_FIELD_HEIGHT = size.applyAsInt("search.field.height");
		NAV_ITEM_HEIGHT = size.applyAsInt("nav.item.height");
		NAV_SEPARATOR_GAP = size.applyAsInt("nav.separator.gap");
		NAV_ACTIVE_BAR_WIDTH = size.applyAsInt("nav.active.bar.width");
		MENU_BUTTON_WIDTH = size.applyAsInt("menu.button.width");
		MENU_BUTTON_HEIGHT = size.applyAsInt("menu.button.height");
		FOCUS_RING_WIDTH = size.applyAsInt("focus.ring.width");
		CARD_TEXT_BLOCK_HEIGHT = size.applyAsInt("card.textBlock.height");
		CARD_BADGE_INSET = size.applyAsInt("card.badge.inset");
		CARD_TEXT_LINE_GAP = size.applyAsInt("card.textLineGap");
		ROW_HEADER_GAP = size.applyAsInt("row.header.gap");
		ROW_HEADER_HEIGHT = size.applyAsInt("row.header.height");
		CHEVRON_WIDTH = size.applyAsInt("chevron.width");
		STATE_SYMBOL_SIZE = size.applyAsInt("state.symbolSize");
		STATE_CONTENT_GAP = size.applyAsInt("state.contentGap");
		CAROUSEL_DOT_SIZE = size.applyAsInt("carousel.dot.size");
		CAROUSEL_ARROW_WIDTH = size.applyAsInt("carousel.arrow.width");
		FLYOUT_WIDTH = size.applyAsInt("flyout.width");
		FLYOUT_ROW_HEIGHT = size.applyAsInt("flyout.row.height");
		FLYOUT_PROGRESS_HEIGHT = size.applyAsInt("flyout.progress.height");
		DETAIL_HEADER_IMAGE_WIDTH = size.applyAsInt("detail.headerImage.width");
		DETAIL_HEADER_IMAGE_HEIGHT = size.applyAsInt("detail.headerImage.height");
		DETAIL_SECTION_GAP = size.applyAsInt("detail.section.gap");
		DETAIL_HEADING_GAP = size.applyAsInt("detail.heading.gap");
		DETAIL_LINE_GAP = size.applyAsInt("detail.lineGap");
		DETAIL_ACTION_BUTTON_WIDTH = size.applyAsInt("detail.action.button.width");
		DETAIL_ACTION_BUTTON_HEIGHT = size.applyAsInt("detail.action.button.height");
		DETAIL_SCREENSHOT_WIDTH = size.applyAsInt("detail.screenshot.width");
		DETAIL_SCREENSHOT_HEIGHT = size.applyAsInt("detail.screenshot.height");
		DETAIL_CAPABILITY_ICON_SIZE = size.applyAsInt("detail.capability.iconSize");
		DETAIL_SCROLL_STEP = size.applyAsInt("detail.scrollStep");
		STATUS_ICON_DRAW_SIZE = size.applyAsInt("statusIcon.drawSize");
		TYPE_ICON_DRAW_SIZE = size.applyAsInt("typeIcon.drawSize");
		TYPE_ICON_PLACEHOLDER_DRAW_SIZE = size.applyAsInt("typeIcon.placeholderDrawSize");
		DIALOG_WIDTH = size.applyAsInt("dialog.width");
		DIALOG_PADDING = size.applyAsInt("dialog.padding");
		DIALOG_BUTTON_GAP = size.applyAsInt("dialog.buttonGap");
		LIBRARY_ROW_HEIGHT = size.applyAsInt("library.row.height");
		LIBRARY_ROW_GAP = size.applyAsInt("library.row.gap");
		LIBRARY_TOGGLE_WIDTH = size.applyAsInt("library.toggle.width");
		LIBRARY_REORDER_BUTTON_WIDTH = size.applyAsInt("library.reorderButton.width");
		LIBRARY_SECTION_GAP = size.applyAsInt("library.section.gap");
		LIBRARY_SCROLL_STEP = size.applyAsInt("library.scrollStep");
		HUD_BAR_DEFAULT_WIDTH = size.applyAsInt("hud.bar.defaultWidth");
		HUD_BAR_DEFAULT_HEIGHT = size.applyAsInt("hud.bar.defaultHeight");
		HUD_ICON_DEFAULT_SIZE = size.applyAsInt("hud.icon.defaultSize");
		HUD_LIST_LINE_GAP = size.applyAsInt("hud.list.lineGap");
	}
}
