package de.spassglas.vitrine.ui.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Das eingebaute Standard-Theme des {@link ThemeManager} (ROADMAP T2), gebildet
 * <em>aus</em> {@link VitrineTheme} statt aus einer zweiten, eingebetteten
 * JSON-Kopie.
 *
 * <p><b>Warum aus {@code VitrineTheme} ableiten und nicht
 * {@code registry/schema/examples/theme/valid/klarglas.json} ins Jar kopieren:</b>
 * <ul>
 *   <li>{@code VitrineTheme} ist heute schon die kanonische Konstantenquelle,
 *       aus der der gesamte Store-Code liest, und {@code registry/test/
 *       theme-schema.test.mjs} nagelt jede dieser Konstanten mechanisch an
 *       einen Schema-Token. Ableiten heiszt: das Standard-Theme ist
 *       <em>per Konstruktion</em> das, was der Store ohne geladenes Paket
 *       ohnehin zeichnet -- der Start aendert kein Pixel, das eigentliche
 *       Klarglas-Theme (kontrastgeprueft) ist eine eigene Zeile.</li>
 *   <li>Eine zweite Datenkopie ({@code klarglas.json}) im Jar koennte
 *       unbemerkt von {@code VitrineTheme} wegdriften; hier gibt es nur eine
 *       Wahrheit.</li>
 * </ul>
 * Die eingebettete Kopie von <em>theme.schema.json</em> gibt es trotzdem
 * (siehe {@link ThemeSchemaValidator}) -- die braucht der Manager, um
 * <em>geladene</em> Themes zu pruefen.</p>
 *
 * <p>{@link ThemeManagerTest} gleicht {@link #sizeTokens()} &cup;
 * {@link #colorTokens()} mechanisch gegen die {@code required}-Liste des
 * eingebetteten Schemas ab: kommt dort ein Token dazu, faellt der Test durch,
 * bis es hier eine Vorgabe hat.</p>
 */
public final class DefaultThemeTokens implements ThemeTokens {

	/** Die eine Instanz -- unveraenderlich, threadsicher, ohne Client-Bezug. */
	public static final DefaultThemeTokens INSTANCE = new DefaultThemeTokens();

	private final Map<String, Integer> sizes;
	private final Map<String, Integer> colors;

	private DefaultThemeTokens() {
		Map<String, Integer> s = new LinkedHashMap<>();
		s.put("space.unit", VitrineTheme.SPACE_UNIT);
		s.put("space.gutter", VitrineTheme.SPACE_GUTTER);
		s.put("space.page", VitrineTheme.SPACE_PAGE);
		s.put("space.edgePadding", VitrineTheme.EDGE_PADDING);
		s.put("space.controlGap", VitrineTheme.CONTROL_GAP);
		s.put("radius.card", VitrineTheme.RADIUS_CARD);
		s.put("radius.control", VitrineTheme.RADIUS_CONTROL);
		s.put("nav.width.expanded", VitrineTheme.NAV_WIDTH_EXPANDED);
		s.put("nav.width.compact", VitrineTheme.NAV_WIDTH_COMPACT);
		s.put("header.height", VitrineTheme.HEADER_HEIGHT);
		// footer.height fuehrt VitrineTheme nicht als eigenes Feld: es gibt
		// (noch) keine Fusszeile, die es zeichnen wuerde. Aus dem Raster
		// abgeleitet statt als nackte Zahl, deckungsgleich mit klarglas.json (28).
		s.put("footer.height", VitrineTheme.HEADER_HEIGHT / 2);
		s.put("card.width", VitrineTheme.CARD_WIDTH);
		s.put("card.aspect.width", VitrineTheme.CARD_ASPECT_WIDTH);
		s.put("card.aspect.height", VitrineTheme.CARD_ASPECT_HEIGHT);
		s.put("hero.aspect.width", VitrineTheme.HERO_ASPECT_WIDTH);
		s.put("hero.aspect.height", VitrineTheme.HERO_ASPECT_HEIGHT);
		s.put("icon.button.size", VitrineTheme.ICON_BUTTON_SIZE);
		s.put("search.field.height", VitrineTheme.SEARCH_FIELD_HEIGHT);
		s.put("nav.item.height", VitrineTheme.NAV_ITEM_HEIGHT);
		s.put("nav.separator.gap", VitrineTheme.NAV_SEPARATOR_GAP);
		s.put("nav.active.bar.width", VitrineTheme.NAV_ACTIVE_BAR_WIDTH);
		s.put("menu.button.width", VitrineTheme.MENU_BUTTON_WIDTH);
		s.put("menu.button.height", VitrineTheme.MENU_BUTTON_HEIGHT);
		s.put("breakpoint.nav.compact", VitrineTheme.BREAKPOINT_NAV_COMPACT);
		s.put("breakpoint.nav.overlay", VitrineTheme.BREAKPOINT_NAV_OVERLAY);
		s.put("card.textBlock.height", VitrineTheme.CARD_TEXT_BLOCK_HEIGHT);
		s.put("card.textLineGap", VitrineTheme.CARD_TEXT_LINE_GAP);
		s.put("card.badge.inset", VitrineTheme.CARD_BADGE_INSET);
		s.put("row.header.height", VitrineTheme.ROW_HEADER_HEIGHT);
		s.put("row.header.gap", VitrineTheme.ROW_HEADER_GAP);
		s.put("chevron.width", VitrineTheme.CHEVRON_WIDTH);
		s.put("statusIcon.drawSize", VitrineTheme.STATUS_ICON_DRAW_SIZE);
		s.put("typeIcon.drawSize", VitrineTheme.TYPE_ICON_DRAW_SIZE);
		s.put("typeIcon.placeholderDrawSize", VitrineTheme.TYPE_ICON_PLACEHOLDER_DRAW_SIZE);
		s.put("detail.headerImage.width", VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH);
		s.put("detail.headerImage.height", VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT);
		s.put("detail.section.gap", VitrineTheme.DETAIL_SECTION_GAP);
		s.put("detail.heading.gap", VitrineTheme.DETAIL_HEADING_GAP);
		s.put("detail.lineGap", VitrineTheme.DETAIL_LINE_GAP);
		s.put("detail.action.button.width", VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH);
		s.put("detail.action.button.height", VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT);
		s.put("detail.screenshot.width", VitrineTheme.DETAIL_SCREENSHOT_WIDTH);
		s.put("detail.screenshot.height", VitrineTheme.DETAIL_SCREENSHOT_HEIGHT);
		s.put("detail.capability.iconSize", VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE);
		s.put("detail.scrollStep", VitrineTheme.DETAIL_SCROLL_STEP);
		s.put("dialog.width", VitrineTheme.DIALOG_WIDTH);
		s.put("dialog.padding", VitrineTheme.DIALOG_PADDING);
		s.put("dialog.buttonGap", VitrineTheme.DIALOG_BUTTON_GAP);
		s.put("library.row.height", VitrineTheme.LIBRARY_ROW_HEIGHT);
		s.put("library.row.gap", VitrineTheme.LIBRARY_ROW_GAP);
		s.put("library.toggle.width", VitrineTheme.LIBRARY_TOGGLE_WIDTH);
		s.put("library.reorderButton.width", VitrineTheme.LIBRARY_REORDER_BUTTON_WIDTH);
		s.put("library.section.gap", VitrineTheme.LIBRARY_SECTION_GAP);
		s.put("library.scrollStep", VitrineTheme.LIBRARY_SCROLL_STEP);
		s.put("state.symbolSize", VitrineTheme.STATE_SYMBOL_SIZE);
		s.put("state.contentGap", VitrineTheme.STATE_CONTENT_GAP);
		s.put("carousel.dot.size", VitrineTheme.CAROUSEL_DOT_SIZE);
		s.put("carousel.arrow.width", VitrineTheme.CAROUSEL_ARROW_WIDTH);
		s.put("flyout.width", VitrineTheme.FLYOUT_WIDTH);
		s.put("flyout.row.height", VitrineTheme.FLYOUT_ROW_HEIGHT);
		s.put("flyout.progress.height", VitrineTheme.FLYOUT_PROGRESS_HEIGHT);
		s.put("hud.bar.defaultWidth", VitrineTheme.HUD_BAR_DEFAULT_WIDTH);
		s.put("hud.bar.defaultHeight", VitrineTheme.HUD_BAR_DEFAULT_HEIGHT);
		s.put("hud.icon.defaultSize", VitrineTheme.HUD_ICON_DEFAULT_SIZE);
		s.put("hud.list.lineGap", VitrineTheme.HUD_LIST_LINE_GAP);
		s.put("blur.surface", VitrineTheme.BLUR_SURFACE);
		s.put("type.titleLarge", VitrineTheme.TYPE_TITLE_LARGE);
		s.put("type.title", VitrineTheme.TYPE_TITLE);
		s.put("type.subtitle", VitrineTheme.TYPE_SUBTITLE);
		s.put("type.body", VitrineTheme.TYPE_BODY);
		s.put("type.caption", VitrineTheme.TYPE_CAPTION);
		s.put("elevation.rest", VitrineTheme.ELEVATION_REST);
		s.put("elevation.hover", VitrineTheme.ELEVATION_HOVER);
		s.put("motion.hover.duration", VitrineTheme.MOTION_HOVER_MS);
		s.put("motion.page.duration", VitrineTheme.MOTION_PAGE_MS);
		s.put("motion.page.liftDistance", VitrineTheme.MOTION_PAGE_LIFT_PX);
		s.put("motion.carousel.duration", VitrineTheme.MOTION_CAROUSEL_MS);
		s.put("focus.ring.width", VitrineTheme.FOCUS_RING_WIDTH);
		this.sizes = Collections.unmodifiableMap(s);

		Map<String, Integer> c = new LinkedHashMap<>();
		c.put("hud.text.color", VitrineTheme.HUD_TEXT_COLOR);
		c.put("hud.bar.track.color", VitrineTheme.HUD_BAR_TRACK_COLOR);
		c.put("hud.bar.fill.color", VitrineTheme.HUD_BAR_FILL_COLOR);
		c.put("focus.ring.color", VitrineTheme.FOCUS_RING_COLOR);
		c.put("color.bg", VitrineTheme.COLOR_BG);
		c.put("color.surfaceSolid", VitrineTheme.COLOR_SURFACE_SOLID);
		c.put("color.text", VitrineTheme.COLOR_TEXT);
		c.put("color.textMuted", VitrineTheme.COLOR_TEXT_MUTED);
		c.put("color.accent", VitrineTheme.COLOR_ACCENT);
		c.put("color.accentContrast", VitrineTheme.COLOR_ACCENT_CONTRAST);
		c.put("color.disabledText", VitrineTheme.COLOR_DISABLED_TEXT);
		c.put("color.danger", VitrineTheme.COLOR_DANGER);
		c.put("color.surfaceTranslucent", VitrineTheme.COLOR_SURFACE_TRANSLUCENT);
		c.put("color.borderTranslucent", VitrineTheme.COLOR_BORDER_TRANSLUCENT);
		c.put("color.overlayScrim", VitrineTheme.COLOR_OVERLAY_SCRIM);
		this.colors = Collections.unmodifiableMap(c);
	}

	@Override
	public int size(String token) {
		Integer value = sizes.get(token);
		if (value == null) {
			throw new IllegalArgumentException("Kein Masz-Token '" + token + "' im Standard-Theme "
					+ "(bekannt sind " + sizes.size() + " Masz- und " + colors.size() + " Farb-Tokens)");
		}
		return value;
	}

	@Override
	public int color(String token) {
		Integer value = colors.get(token);
		if (value == null) {
			throw new IllegalArgumentException("Kein Farb-Token '" + token + "' im Standard-Theme "
					+ "(bekannt sind " + colors.size() + " Farb- und " + sizes.size() + " Masz-Tokens)");
		}
		return value;
	}

	/** Alle Masz-Token-Namen (kanonische Schema-Schreibweise). */
	public Set<String> sizeTokens() {
		return sizes.keySet();
	}

	/** Alle Farb-Token-Namen (kanonische Schema-Schreibweise). */
	public Set<String> colorTokens() {
		return colors.keySet();
	}

	/** {@code true}, wenn {@code token} ein Farb-Token ist; {@code false}, wenn Masz-Token oder unbekannt. */
	public boolean isColorToken(String token) {
		return colors.containsKey(token);
	}
}
