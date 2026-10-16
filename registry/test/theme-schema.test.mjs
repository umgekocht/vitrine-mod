// Test fuer registry/schema/theme.schema.json (M5/T1).
//
// Drei Dinge werden geprueft:
//  1. Das gueltige Beispiel-Theme (examples/theme/valid/klarglas.json) besteht
//     die Validierung.
//  2. Jedes ungueltige Beispiel (examples/theme/invalid/*.json) faellt durch.
//  3. Der eigentliche Beweis fuer T1s "Fertig"-Kriterium ("Schema deckt jedes
//     im Store benutzte UI-Element ab"): jeder Token-Name wird MECHANISCH aus
//     den zwei bestehenden, bewusst noch nicht kanonischen Token-Quellen
//     ausgelesen (Regex/Parsing, keine von Hand gepflegte Kopie, die
//     auseinanderlaufen koennte):
//       - mod/src/main/java/de/spassglas/vitrine/ui/theme/VitrineTheme.java
//         (Java-Konstanten, S0-Platzhalter)
//       - designer/theme-tokens.js (TOKENS-Array, D3-Platzhalter)
//     und gegen eine explizite Zuordnungstabelle auf die Schema-Eigenschaften
//     von theme.schema.json abgeglichen. Kein Joker/Wildcard -- jede in den
//     Quellen gefundene Kennung muss in der Zuordnungstabelle stehen, sonst
//     schlaegt der Test fehl (schuetzt davor, dass ein neuer Token in einer
//     der beiden Quellen dazukommt, ohne dass das Schema nachzieht).
//
// Wichtig: dieser Test migriert NICHT VitrineTheme.java oder
// theme-tokens.js auf das neue Schema -- das bleibt T2 (ThemeManager) und der
// entsprechenden Designer-Zeile vorbehalten. Er beweist nur, dass das Schema
// bereits jeden Token benennen kann, den beide Seiten heute schon benutzen.
//
// Aufruf: node --test registry/test/theme-schema.test.mjs
// (vorher einmalig: npm install --prefix registry)

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { Validator } from "@cfworker/json-schema";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const repoRoot = path.join(registryRoot, "..");

const schemaPath = path.join(registryRoot, "schema", "theme.schema.json");
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));

const modThemePath = path.join(
	repoRoot,
	"mod", "src", "main", "java", "de", "spassglas", "vitrine", "ui", "theme", "VitrineTheme.java",
);
const designerTokensPath = path.join(repoRoot, "designer", "theme-tokens.js");

function jsonFilesIn(dir) {
	return readdirSync(dir)
		.filter((f) => f.endsWith(".json"))
		.map((f) => path.join(dir, f));
}

// --- 1 + 2: gueltige/ungueltige Beispiele -------------------------------

test("theme.schema.json akzeptiert das gueltige Klarglas-Beispiel", () => {
	const validator = new Validator(schema, "2020-12");
	const validDir = path.join(registryRoot, "schema", "examples", "theme", "valid");
	const files = jsonFilesIn(validDir);
	assert.ok(files.length >= 1, "erwarte mindestens ein gueltiges Beispiel-Theme");
	for (const file of files) {
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(
			result.valid,
			true,
			`${path.basename(file)} sollte gueltig sein: ${JSON.stringify(result.errors, null, 2)}`,
		);
	}
});

test("theme.schema.json lehnt jedes ungueltige Beispiel ab", () => {
	const invalidDir = path.join(registryRoot, "schema", "examples", "theme", "invalid");
	const files = jsonFilesIn(invalidDir);
	assert.ok(files.length >= 3, "erwarte mindestens drei ungueltige Beispiele (Pflichtfeld, Typ, Zusatzfeld)");
	for (const file of files) {
		const validator = new Validator(schema, "2020-12");
		const data = JSON.parse(readFileSync(file, "utf8"));
		const result = validator.validate(data);
		assert.equal(result.valid, false, `${path.basename(file)} sollte ungueltig sein, wurde aber akzeptiert.`);
	}
});

// --- 3: mechanischer Vollstaendigkeitsbeweis ----------------------------

/** Liest alle `public static final <typ> <NAME> = ...;`-Konstanten aus VitrineTheme.java. */
function extractModConstants(javaSource) {
	const names = [];
	const re = /public\s+static\s+final\s+\w+\s+(\w+)\s*=/g;
	let m;
	while ((m = re.exec(javaSource)) !== null) {
		names.push(m[1]);
	}
	return names;
}

/** Liest alle Token-`id`-Werte aus dem TOKENS-Array in designer/theme-tokens.js (nicht aus CONTRAST_PAIRS). */
function extractDesignerTokenIds(jsSource) {
	const startMarker = "var TOKENS = [";
	const endMarker = "var TOKEN_GROUPS";
	const start = jsSource.indexOf(startMarker);
	const end = jsSource.indexOf(endMarker);
	assert.ok(start !== -1 && end !== -1 && end > start, "designer/theme-tokens.js: TOKENS-Array-Grenzen nicht gefunden -- Datei-Layout geaendert?");
	const tokensBlock = jsSource.slice(start, end);
	const ids = [];
	const re = /\{\s*id:\s*"([^"]+)"/g;
	let m;
	while ((m = re.exec(tokensBlock)) !== null) {
		ids.push(m[1]);
	}
	return ids;
}

function schemaVariantPropertyNames(themeSchema) {
	return Object.keys(themeSchema.$defs.variant.properties);
}

// Explizite Zuordnung: Java-Konstantenname (VitrineTheme.java) -> kanonischer
// Schema-Token-Name (theme.schema.json, $defs.variant.properties). Muss jede
// von extractModConstants() gefundene Konstante abdecken -- der Test prueft
// das unten, keine Wildcard-Ausnahme.
const MOD_CONSTANT_TO_SCHEMA_TOKEN = {
	SPACE_UNIT: "space.unit",
	SPACE_GUTTER: "space.gutter",
	SPACE_PAGE: "space.page",
	RADIUS_CARD: "radius.card",
	RADIUS_CONTROL: "radius.control",
	NAV_WIDTH_EXPANDED: "nav.width.expanded",
	NAV_WIDTH_COMPACT: "nav.width.compact",
	HEADER_HEIGHT: "header.height",
	CARD_WIDTH: "card.width",
	CARD_ASPECT_WIDTH: "card.aspect.width",
	CARD_ASPECT_HEIGHT: "card.aspect.height",
	HERO_ASPECT_WIDTH: "hero.aspect.width",
	HERO_ASPECT_HEIGHT: "hero.aspect.height",
	ELEVATION_REST: "elevation.rest",
	ELEVATION_HOVER: "elevation.hover",
	BREAKPOINT_NAV_COMPACT: "breakpoint.nav.compact",
	BREAKPOINT_NAV_OVERLAY: "breakpoint.nav.overlay",
	TYPE_TITLE_LARGE: "type.titleLarge",
	TYPE_TITLE: "type.title",
	TYPE_SUBTITLE: "type.subtitle",
	TYPE_BODY: "type.body",
	TYPE_CAPTION: "type.caption",
	MOTION_HOVER_MS: "motion.hover.duration",
	MOTION_PAGE_MS: "motion.page.duration",
	MOTION_PAGE_LIFT_PX: "motion.page.liftDistance",
	MOTION_CAROUSEL_MS: "motion.carousel.duration",
	EDGE_PADDING: "space.edgePadding",
	CONTROL_GAP: "space.controlGap",
	ICON_BUTTON_SIZE: "icon.button.size",
	SEARCH_FIELD_HEIGHT: "search.field.height",
	NAV_ITEM_HEIGHT: "nav.item.height",
	NAV_SEPARATOR_GAP: "nav.separator.gap",
	NAV_ACTIVE_BAR_WIDTH: "nav.active.bar.width",
	MENU_BUTTON_WIDTH: "menu.button.width",
	MENU_BUTTON_HEIGHT: "menu.button.height",
	FOCUS_RING_WIDTH: "focus.ring.width",
	COLOR_BG: "color.bg",
	COLOR_SURFACE_SOLID: "color.surfaceSolid",
	COLOR_TEXT: "color.text",
	COLOR_TEXT_MUTED: "color.textMuted",
	COLOR_ACCENT: "color.accent",
	COLOR_ACCENT_CONTRAST: "color.accentContrast",
	COLOR_DISABLED_TEXT: "color.disabledText",
	COLOR_DANGER: "color.danger",
	COLOR_SURFACE_TRANSLUCENT: "color.surfaceTranslucent",
	COLOR_BORDER_TRANSLUCENT: "color.borderTranslucent",
	COLOR_OVERLAY_SCRIM: "color.overlayScrim",
	FOCUS_RING_COLOR: "focus.ring.color",

	// ROADMAP S1 -- Kachel/Kachelreihe. Themebare Masse: gehoeren ins Schema.
	CARD_TEXT_BLOCK_HEIGHT: "card.textBlock.height",
	CARD_TEXT_LINE_GAP: "card.textLineGap",
	CARD_BADGE_INSET: "card.badge.inset",
	ROW_HEADER_HEIGHT: "row.header.height",
	ROW_HEADER_GAP: "row.header.gap",
	CHEVRON_WIDTH: "chevron.width",
	STATUS_ICON_DRAW_SIZE: "statusIcon.drawSize",
	TYPE_ICON_DRAW_SIZE: "typeIcon.drawSize",
	TYPE_ICON_PLACEHOLDER_DRAW_SIZE: "typeIcon.placeholderDrawSize",

	// ROADMAP S3 -- Detailseite. Themebare Masse: gehoeren ins Schema.
	// DETAIL_HEADER_IMAGE_* und DETAIL_SCREENSHOT_* sind Anzeige-Zielmasse
	// (gestalterisch), nicht die native Bildaufloesung -> Schema, nicht
	// ASSET_SOURCE_DIMENSIONS.
	DETAIL_HEADER_IMAGE_WIDTH: "detail.headerImage.width",
	DETAIL_HEADER_IMAGE_HEIGHT: "detail.headerImage.height",
	DETAIL_SECTION_GAP: "detail.section.gap",
	DETAIL_HEADING_GAP: "detail.heading.gap",
	DETAIL_LINE_GAP: "detail.lineGap",
	DETAIL_ACTION_BUTTON_WIDTH: "detail.action.button.width",
	DETAIL_ACTION_BUTTON_HEIGHT: "detail.action.button.height",
	DETAIL_SCREENSHOT_WIDTH: "detail.screenshot.width",
	DETAIL_SCREENSHOT_HEIGHT: "detail.screenshot.height",
	DETAIL_CAPABILITY_ICON_SIZE: "detail.capability.iconSize",
	DETAIL_SCROLL_STEP: "detail.scrollStep",

	// ROADMAP S4 -- Faehigkeiten-Dialog. Themebare Masse: gehoeren ins Schema.
	DIALOG_WIDTH: "dialog.width",
	DIALOG_PADDING: "dialog.padding",
	DIALOG_BUTTON_GAP: "dialog.buttonGap",

	// ROADMAP S5 -- Bibliothek. Reine Masse (aus SPACE_UNIT/NAV_ITEM_HEIGHT
	// abgeleitet), kein Asset-Pixelmass -> Schema, nicht ASSET_SOURCE_DIMENSIONS.
	LIBRARY_ROW_HEIGHT: "library.row.height",
	LIBRARY_ROW_GAP: "library.row.gap",
	LIBRARY_TOGGLE_WIDTH: "library.toggle.width",
	LIBRARY_REORDER_BUTTON_WIDTH: "library.reorderButton.width",
	LIBRARY_SECTION_GAP: "library.section.gap",
	LIBRARY_SCROLL_STEP: "library.scrollStep",

	// ROADMAP S7 -- Zustands-Panels (leer / keine Treffer / Warnung). Themebare
	// Masse (aus SPACE_UNIT abgeleitet), kein Asset-Pixelmass -> Schema.
	// STATE_SYMBOL_SIZE ist eine gestalterische Zeichengroesse, nicht die native
	// PNG-Aufloesung -> Schema, nicht ASSET_SOURCE_DIMENSIONS.
	STATE_SYMBOL_SIZE: "state.symbolSize",
	STATE_CONTENT_GAP: "state.contentGap",

	// ROADMAP S8 -- Hero-Karussell + Download-Flyout. Themebare Masse (aus
	// SPACE_UNIT abgeleitet), kein Asset-Pixelmass -> Schema, nicht
	// ASSET_SOURCE_DIMENSIONS. Die 7-s-Auto-Wechsel-/Kurz-Pause-Dauer sind
	// Verhaltens-Zeiten und stehen als Konstanten in HeroCarouselModel, nicht
	// in VitrineTheme.
	CAROUSEL_DOT_SIZE: "carousel.dot.size",
	CAROUSEL_ARROW_WIDTH: "carousel.arrow.width",
	FLYOUT_WIDTH: "flyout.width",
	FLYOUT_ROW_HEIGHT: "flyout.row.height",
	FLYOUT_PROGRESS_HEIGHT: "flyout.progress.height",

	// ROADMAP T5 -- HUD-Laufzeit-Rueckfalltokens (Render-Defaults, wenn ein
	// HUD-Paket nichts vorgibt). Themebare Farben + Masse: gehoeren ins Schema.
	// HUD_ICON_DEFAULT_SIZE ist eine gestalterische Default-Zeichengroesse
	// (kein rohes Asset-Pixelmass) -> Schema, nicht ASSET_SOURCE_DIMENSIONS.
	HUD_TEXT_COLOR: "hud.text.color",
	HUD_BAR_TRACK_COLOR: "hud.bar.track.color",
	HUD_BAR_FILL_COLOR: "hud.bar.fill.color",
	HUD_BAR_DEFAULT_WIDTH: "hud.bar.defaultWidth",
	HUD_BAR_DEFAULT_HEIGHT: "hud.bar.defaultHeight",
	HUD_ICON_DEFAULT_SIZE: "hud.icon.defaultSize",
	HUD_LIST_LINE_GAP: "hud.list.lineGap",
};

// Ausdrueckliche Ausnahmemenge: VitrineTheme.java-Konstanten, die KEIN
// themebares Mass sind, sondern die native Pixelaufloesung einer mitgelieferten
// PNG-Grafik festhalten (noetig fuers UV-Mapping beim Zeichnen, kein
// gestalterischer Spielraum). Diese gehoeren bewusst NICHT ins Theme-Schema.
// Kein Wildcard/Regex -- jeder Eintrag ist einzeln aufgezaehlt und begruendet.
// Wenn eine neue *_SOURCE_*-Konstante dazukommt, die hier fehlt, faellt der
// Test durch (Zwang zur bewussten Entscheidung pro Konstante).
const ASSET_SOURCE_DIMENSIONS = {
	STATUS_ICON_SOURCE_SIZE: "native 16x16-Aufloesung von ui_check/ui_download/ui_warning.png",
	TYPE_ICON_SOURCE_SIZE: "native 32x32-Aufloesung der category_*.png",
	PLACEHOLDER_CARD_SOURCE_WIDTH: "native Breite (512) von gui/placeholder_card.png -- ASSETS-NEEDED.md",
	PLACEHOLDER_CARD_SOURCE_HEIGHT: "native Hoehe (288) von gui/placeholder_card.png -- ASSETS-NEEDED.md",
	CHEVRON_ICON_SOURCE_SIZE: "native 16x16-Aufloesung von ui_chevron_left/right.png",
	STATE_EMPTY_SOURCE_SIZE: "native 192x192-Aufloesung von gui/empty_state.png -- ROADMAP S7, UV-Mapping",
	NAV_ICON_SOURCE_SIZE: "native 20x20-Aufloesung der nav_*_outline/filled.png (GATE18)",
	UI_ICON_SOURCE_SIZE: "native 16x16-Aufloesung der ui_*.png-Bediensymbole (GATE18)",
};

// Explizite Zuordnung: Designer-Token-Id (designer/theme-tokens.js,
// TOKENS[].id) -> kanonischer Schema-Token-Name. Muss jede von
// extractDesignerTokenIds() gefundene Id abdecken.
//
// GATE22: theme-tokens.js heisst jedes Token-Feld inzwischen EXAKT wie die
// gleichnamige Schema-Eigenschaft (keine Umbenennung mehr) -- diese Tabelle
// ist deshalb fuer alle 93 Ids reine Identitaet (Key == Value), ohne
// Ausnahme.
const DESIGNER_TOKEN_ID_TO_SCHEMA_TOKEN = {
	"space.unit": "space.unit",
	"space.gutter": "space.gutter",
	"space.page": "space.page",
	"space.edgePadding": "space.edgePadding",
	"space.controlGap": "space.controlGap",
	"radius.card": "radius.card",
	"radius.control": "radius.control",
	"nav.width.expanded": "nav.width.expanded",
	"nav.width.compact": "nav.width.compact",
	"header.height": "header.height",
	"footer.height": "footer.height",
	"card.width": "card.width",
	"card.aspect.width": "card.aspect.width",
	"card.aspect.height": "card.aspect.height",
	"hero.aspect.width": "hero.aspect.width",
	"hero.aspect.height": "hero.aspect.height",
	"icon.button.size": "icon.button.size",
	"search.field.height": "search.field.height",
	"nav.item.height": "nav.item.height",
	"nav.separator.gap": "nav.separator.gap",
	"nav.active.bar.width": "nav.active.bar.width",
	"menu.button.width": "menu.button.width",
	"menu.button.height": "menu.button.height",
	"breakpoint.nav.compact": "breakpoint.nav.compact",
	"breakpoint.nav.overlay": "breakpoint.nav.overlay",
	"card.textBlock.height": "card.textBlock.height",
	"card.textLineGap": "card.textLineGap",
	"card.badge.inset": "card.badge.inset",
	"row.header.height": "row.header.height",
	"row.header.gap": "row.header.gap",
	"chevron.width": "chevron.width",
	"statusIcon.drawSize": "statusIcon.drawSize",
	"typeIcon.drawSize": "typeIcon.drawSize",
	"typeIcon.placeholderDrawSize": "typeIcon.placeholderDrawSize",
	"detail.headerImage.width": "detail.headerImage.width",
	"detail.headerImage.height": "detail.headerImage.height",
	"detail.section.gap": "detail.section.gap",
	"detail.heading.gap": "detail.heading.gap",
	"detail.lineGap": "detail.lineGap",
	"detail.action.button.width": "detail.action.button.width",
	"detail.action.button.height": "detail.action.button.height",
	"detail.screenshot.width": "detail.screenshot.width",
	"detail.screenshot.height": "detail.screenshot.height",
	"detail.capability.iconSize": "detail.capability.iconSize",
	"detail.scrollStep": "detail.scrollStep",
	"dialog.width": "dialog.width",
	"dialog.padding": "dialog.padding",
	"dialog.buttonGap": "dialog.buttonGap",
	"library.row.height": "library.row.height",
	"library.row.gap": "library.row.gap",
	"library.toggle.width": "library.toggle.width",
	"library.reorderButton.width": "library.reorderButton.width",
	"library.section.gap": "library.section.gap",
	"library.scrollStep": "library.scrollStep",
	"state.symbolSize": "state.symbolSize",
	"state.contentGap": "state.contentGap",
	"carousel.dot.size": "carousel.dot.size",
	"carousel.arrow.width": "carousel.arrow.width",
	"flyout.width": "flyout.width",
	"flyout.row.height": "flyout.row.height",
	"flyout.progress.height": "flyout.progress.height",
	"hud.text.color": "hud.text.color",
	"hud.bar.track.color": "hud.bar.track.color",
	"hud.bar.fill.color": "hud.bar.fill.color",
	"hud.bar.defaultWidth": "hud.bar.defaultWidth",
	"hud.bar.defaultHeight": "hud.bar.defaultHeight",
	"hud.icon.defaultSize": "hud.icon.defaultSize",
	"hud.list.lineGap": "hud.list.lineGap",
	"blur.surface": "blur.surface",
	"type.titleLarge": "type.titleLarge",
	"type.title": "type.title",
	"type.subtitle": "type.subtitle",
	"type.body": "type.body",
	"type.caption": "type.caption",
	"elevation.rest": "elevation.rest",
	"elevation.hover": "elevation.hover",
	"motion.hover.duration": "motion.hover.duration",
	"motion.page.duration": "motion.page.duration",
	"motion.page.liftDistance": "motion.page.liftDistance",
	"motion.carousel.duration": "motion.carousel.duration",
	"focus.ring.width": "focus.ring.width",
	"focus.ring.color": "focus.ring.color",
	"color.bg": "color.bg",
	"color.surfaceSolid": "color.surfaceSolid",
	"color.text": "color.text",
	"color.textMuted": "color.textMuted",
	"color.accent": "color.accent",
	"color.accentContrast": "color.accentContrast",
	"color.disabledText": "color.disabledText",
	"color.danger": "color.danger",
	"color.surfaceTranslucent": "color.surfaceTranslucent",
	"color.borderTranslucent": "color.borderTranslucent",
	"color.overlayScrim": "color.overlayScrim",
};

test("jede Java-Theme-Konstante (VitrineTheme.java) hat eine Entsprechung im Schema", () => {
	const javaSource = readFileSync(modThemePath, "utf8");
	const constants = extractModConstants(javaSource);
	assert.ok(constants.length > 0, "erwarte mindestens eine gefundene Java-Konstante -- Regex/Datei geaendert?");

	const schemaTokens = new Set(schemaVariantPropertyNames(schema));
	const unmapped = [];
	const missingInSchema = [];

	for (const name of constants) {
		if (Object.prototype.hasOwnProperty.call(ASSET_SOURCE_DIMENSIONS, name)) {
			continue; // reines Asset-Faktum, siehe ASSET_SOURCE_DIMENSIONS
		}
		const target = MOD_CONSTANT_TO_SCHEMA_TOKEN[name];
		if (!target) {
			unmapped.push(name);
			continue;
		}
		if (!schemaTokens.has(target)) {
			missingInSchema.push(`${name} -> ${target}`);
		}
	}

	assert.deepEqual(unmapped, [], `Java-Konstanten ohne Eintrag in MOD_CONSTANT_TO_SCHEMA_TOKEN: ${unmapped.join(", ")}`);
	assert.deepEqual(missingInSchema, [], `Zugeordnete Schema-Tokens fehlen in theme.schema.json: ${missingInSchema.join(", ")}`);

	console.log(`OK: ${constants.length} Java-Konstanten aus VitrineTheme.java abgedeckt.`);
});

test("jeder Designer-Token (designer/theme-tokens.js) hat eine Entsprechung im Schema", () => {
	const jsSource = readFileSync(designerTokensPath, "utf8");
	const ids = extractDesignerTokenIds(jsSource);
	assert.ok(ids.length > 0, "erwarte mindestens eine gefundene Designer-Token-Id -- Regex/Datei geaendert?");

	const schemaTokens = new Set(schemaVariantPropertyNames(schema));
	const unmapped = [];
	const missingInSchema = [];

	for (const id of ids) {
		const target = DESIGNER_TOKEN_ID_TO_SCHEMA_TOKEN[id];
		if (!target) {
			unmapped.push(id);
			continue;
		}
		if (!schemaTokens.has(target)) {
			missingInSchema.push(`${id} -> ${target}`);
		}
	}

	assert.deepEqual(unmapped, [], `Designer-Token-Ids ohne Eintrag in DESIGNER_TOKEN_ID_TO_SCHEMA_TOKEN: ${unmapped.join(", ")}`);
	assert.deepEqual(missingInSchema, [], `Zugeordnete Schema-Tokens fehlen in theme.schema.json: ${missingInSchema.join(", ")}`);

	console.log(`OK: ${ids.length} Designer-Tokens aus theme-tokens.js abgedeckt.`);
});

test("die Vereinigung beider Quellen deckt jede Schema-Eigenschaft ab (kein toter Token im Schema)", () => {
	const javaSource = readFileSync(modThemePath, "utf8");
	const jsSource = readFileSync(designerTokensPath, "utf8");
	const modConstants = extractModConstants(javaSource);
	const designerIds = extractDesignerTokenIds(jsSource);

	const coveredSchemaTokens = new Set([
		...modConstants.map((n) => MOD_CONSTANT_TO_SCHEMA_TOKEN[n]).filter(Boolean),
		...designerIds.map((id) => DESIGNER_TOKEN_ID_TO_SCHEMA_TOKEN[id]).filter(Boolean),
	]);

	const schemaTokens = schemaVariantPropertyNames(schema);
	const uncovered = schemaTokens.filter((t) => !coveredSchemaTokens.has(t));

	assert.deepEqual(
		uncovered,
		[],
		`Schema-Tokens ohne jede Entsprechung in Mod ODER Designer (moeglicherweise unnoetig): ${uncovered.join(", ")}`,
	);

	console.log(`OK: alle ${schemaTokens.length} Schema-Tokens sind durch Mod und/oder Designer belegt.`);
});
