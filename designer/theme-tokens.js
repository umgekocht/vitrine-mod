"use strict";

/*
 * Vitrine Designer -- Theme-Token-Set, abgeleitet aus
 * registry/schema/theme.schema.json (Charter-Regel 8: "ein Schema, zwei
 * Seiten" -- Designer und Mod lesen/erzeugen dasselbe Format, nie ein
 * zweites, abweichendes).
 *
 * GATE22: dieses Modul trug bis hierher noch ein D3-Vorlaeufer-Set mit
 * eigenen, vom Schema abweichenden Feldnamen (z. B. "color.surface" statt
 * "color.surfaceTranslucent", "surface.blur" statt "blur.surface") und nur
 * einem Bruchteil der Pflichtfelder. Jedes Token-Feld hier heisst jetzt
 * EXAKT wie die gleichnamige Eigenschaft in
 * registry/schema/theme.schema.json ($defs.variant.properties) -- keine
 * Erfindung, keine Umbenennung. Die TOKENS-Liste deckt ALLE Pflichtfelder
 * aus $defs.variant.required ab (93 Stueck, Stand T1); ein Test
 * (registry/test/theme-schema.test.mjs) prueft das mechanisch von der
 * Schema-Seite her, ein zweiter (designer/test/theme-tokens-schema.test.js)
 * von hier aus: das per DEFAULT_THEME gebaute Beispiel-Theme wird gegen den
 * echten Schema-Validator (@cfworker/json-schema, wie in registry/test/)
 * validiert und MUSS bestehen.
 *
 * Die Default-Werte (DEFAULT_THEME) sind exakt die Werte aus
 * registry/schema/examples/theme/valid/klarglas.json -- dem kanonischen,
 * bereits gegen das Schema UND (registry/test/theme-contrast.test.mjs) auf
 * WCAG-AA-Kontrast geprueften Standard-Theme. Keine zweite, hier neu
 * erfundene Werte-Quelle.
 *
 * Ein Theme-Wert-Objekt (ein "Variant", hell ODER dunkel) sieht so aus:
 *   { "space.unit": 4, "color.bg": "#f3f3f4", "color.surfaceTranslucent": {hex, alpha}, ... }
 * Ein komplettes Theme-Projekt-Feld (siehe app.js) hat die Form:
 *   { schemaVersion: 1, light: {...Variant...}, dark: {...Variant...} }
 * -- identisch zum Schema-Wurzelobjekt (schemaVersion/light/dark).
 *
 * Zwei Werttypen ueber "number" hinaus, wie im Schema:
 *  - "color": einfacher Hex-String "#rrggbb" (Solidfarbe, per <input type=color>
 *    editierbar) -- entspricht $defs.color.
 *  - "color-alpha": {hex, alpha} fuer die durchscheinenden Flaechen/Raender
 *    ($defs.colorAlpha) -- <input type=color> kennt keinen Alpha-Kanal,
 *    deshalb getrennt als Hex + Deckkraft-Regler.
 * "number"-Tokens mit unit "px" entsprechen $defs.px/$defs.pxPositive
 * (Ganzzahl, min 0 bzw. 1), unit "ms" entspricht $defs.ms (Ganzzahl, min 0).
 * min/max/step hier sind reine Editor-Komfortgrenzen (Regler-Rahmen), nicht
 * Teil des Schemas -- das Schema selbst kennt fuer die meisten Zahlen kein
 * Maximum, nur "min. 0" bzw. "min. 1" und "ganzzahlig".
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.VitrineThemeTokens = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {

  /** "space.unit" -> "--vt-space-unit"; "type.titleLarge" -> "--vt-type-title-large". */
  function tokenIdToCssVar(id) {
    var kebab = id.split(".").map(function (segment) {
      return segment.replace(/([a-z0-9])([A-Z])/g, "$1-$2").toLowerCase();
    }).join("-");
    return "--vt-" + kebab;
  }

  // Reihenfolge der Gruppen = Reihenfolge im Editor-Formular. Jede Gruppen-Id
  // hier hat eine Entsprechung in TOKEN_GROUPS weiter unten.
  var TOKENS = [
    // --- Abstaende ---
    { id: "space.unit", label: "Basiseinheit, alle Abstaende sind Vielfache (docs/UI-SPEC.md)", group: "space", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 4, dark: 4 } },
    { id: "space.gutter", label: "Abstand zwischen Karten", group: "space", type: "number", unit: "px", min: 0, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "space.page", label: "Seitenrand des Inhaltsbereichs", group: "space", type: "number", unit: "px", min: 0, max: 200, step: 1, default: { light: 32, dark: 32 } },
    { id: "space.edgePadding", label: "Abgeleiteter Innenabstand (z. B. Kopfleiste); Vielfaches von space.unit", group: "space", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 8, dark: 8 } },
    { id: "space.controlGap", label: "Abstand zwischen benachbarten Bedienelementen; Vielfaches von space.unit", group: "space", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 8, dark: 8 } },

    // --- Radien ---
    { id: "radius.card", label: "Kartenradius (Karten, Bilder)", group: "radius", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 8, dark: 8 } },
    { id: "radius.control", label: "Radius fuer Knoepfe/Felder", group: "radius", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 4, dark: 4 } },

    // --- Masse (Nav, Kopf-/Fusszeile, Kachel, Karussellbild, Icons, Suche, Menue, Breakpoints) ---
    { id: "nav.width.expanded", label: "Breite Nav-Rail ausgeklappt", group: "layout", type: "number", unit: "px", min: 1, max: 1680, step: 1, default: { light: 280, dark: 280 } },
    { id: "nav.width.compact", label: "Breite Nav-Rail eingeklappt (schmales Fenster)", group: "layout", type: "number", unit: "px", min: 1, max: 340, step: 1, default: { light: 56, dark: 56 } },
    { id: "header.height", label: "Hoehe der Kopfleiste mit Suche", group: "layout", type: "number", unit: "px", min: 1, max: 340, step: 1, default: { light: 56, dark: 56 } },
    { id: "footer.height", label: "Hoehe einer optionalen Fusszeile", group: "layout", type: "number", unit: "px", min: 0, max: 170, step: 1, default: { light: 28, dark: 28 } },
    { id: "card.width", label: "Breite der Standardkachel", group: "layout", type: "number", unit: "px", min: 1, max: 1200, step: 1, default: { light: 200, dark: 200 } },
    { id: "card.aspect.width", label: "Seitenverhaeltnis-Zaehler des Kachel-Bildbereichs (z. B. 16 fuer 16:9)", group: "layout", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "card.aspect.height", label: "Seitenverhaeltnis-Nenner des Kachel-Bildbereichs (z. B. 9 fuer 16:9)", group: "layout", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 9, dark: 9 } },
    { id: "hero.aspect.width", label: "Seitenverhaeltnis-Zaehler des Karussellbilds (z. B. 21 fuer 21:9)", group: "layout", type: "number", unit: "px", min: 1, max: 130, step: 1, default: { light: 21, dark: 21 } },
    { id: "hero.aspect.height", label: "Seitenverhaeltnis-Nenner des Karussellbilds (z. B. 9 fuer 21:9)", group: "layout", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 9, dark: 9 } },
    { id: "icon.button.size", label: "Kantenlaenge quadratischer Icon-Knoepfe (z. B. in der Kopfleiste)", group: "layout", type: "number", unit: "px", min: 1, max: 240, step: 1, default: { light: 40, dark: 40 } },
    { id: "search.field.height", label: "Hoehe des Suchfelds", group: "layout", type: "number", unit: "px", min: 1, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "nav.item.height", label: "Hoehe eines Eintrags in der Nav-Rail", group: "layout", type: "number", unit: "px", min: 1, max: 240, step: 1, default: { light: 40, dark: 40 } },
    { id: "nav.separator.gap", label: "Abstand um die Trennlinie in der Nav-Rail", group: "layout", type: "number", unit: "px", min: 0, max: 80, step: 1, default: { light: 12, dark: 12 } },
    { id: "nav.active.bar.width", label: "Breite des farbigen Balkens am aktiven Nav-Eintrag", group: "layout", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 4, dark: 4 } },
    { id: "menu.button.width", label: "Breite eines Menu-/Ueberlauf-Knopfeintrags", group: "layout", type: "number", unit: "px", min: 1, max: 720, step: 1, default: { light: 120, dark: 120 } },
    { id: "menu.button.height", label: "Hoehe eines Menu-/Ueberlauf-Knopfeintrags", group: "layout", type: "number", unit: "px", min: 1, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "breakpoint.nav.compact", label: "Unter dieser Fensterbreite (px) wird die Nav-Rail kompakt (nur Icons)", group: "layout", type: "number", unit: "px", min: 1, max: 6000, step: 1, default: { light: 1000, dark: 1000 } },
    { id: "breakpoint.nav.overlay", label: "Unter dieser Fensterbreite (px) wird die Nav-Rail zur Ueberlagerung", group: "layout", type: "number", unit: "px", min: 1, max: 4200, step: 1, default: { light: 700, dark: 700 } },

    // --- Kachel und Kachelreihe ---
    { id: "card.textBlock.height", label: "Hoehe des Textblocks unter dem 16:9-Kachelbild (Name, Autor, Typ-Badge)", group: "cards", type: "number", unit: "px", min: 1, max: 270, step: 1, default: { light: 44, dark: 44 } },
    { id: "card.textLineGap", label: "Abstand zwischen den Textzeilen (Name/Autor) innerhalb einer Kachel", group: "cards", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 2, dark: 2 } },
    { id: "card.badge.inset", label: "Innenabstand zwischen Bildkante und Statuszeichen bzw. Typ-Badge auf der Kachel", group: "cards", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 4, dark: 4 } },
    { id: "row.header.height", label: "Hoehe des Kopfbereichs einer Kachelreihe (Ueberschrift + Unterzeile)", group: "cards", type: "number", unit: "px", min: 1, max: 240, step: 1, default: { light: 40, dark: 40 } },
    { id: "row.header.gap", label: "Abstand zwischen dem Kopf einer Kachelreihe und der ersten Kachelreihe", group: "cards", type: "number", unit: "px", min: 0, max: 80, step: 1, default: { light: 12, dark: 12 } },
    { id: "chevron.width", label: "Breite der Chevron-Schaltflaechen (Blaettern) am linken/rechten Rand einer Kachelreihe", group: "cards", type: "number", unit: "px", min: 1, max: 150, step: 1, default: { light: 24, dark: 24 } },
    { id: "statusIcon.drawSize", label: "Zielgroesse, in der ein Statuszeichen (Haken/Download/Warnung) auf der Kachel gezeichnet wird", group: "cards", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "typeIcon.drawSize", label: "Zielgroesse des Typ-Symbols als Ecken-Badge auf der Kachel", group: "cards", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "typeIcon.placeholderDrawSize", label: "Zielgroesse des grossen, mittigen Typ-Symbols im Bild-Platzhalter einer Kachel ohne eigenes Vorschaubild", group: "cards", type: "number", unit: "px", min: 1, max: 200, step: 1, default: { light: 32, dark: 32 } },

    // --- Detailseite ---
    { id: "detail.headerImage.width", label: "Breite des grossen Kopfbild-/Icon-Bereichs (16:9) auf der Detailseite", group: "detail", type: "number", unit: "px", min: 1, max: 2400, step: 1, default: { light: 400, dark: 400 } },
    { id: "detail.headerImage.height", label: "Hoehe des Kopfbild-Bereichs auf der Detailseite", group: "detail", type: "number", unit: "px", min: 1, max: 1350, step: 1, default: { light: 225, dark: 225 } },
    { id: "detail.section.gap", label: "Vertikaler Abstand zwischen zwei Detailseiten-Abschnitten", group: "detail", type: "number", unit: "px", min: 0, max: 150, step: 1, default: { light: 24, dark: 24 } },
    { id: "detail.heading.gap", label: "Abstand zwischen einer Abschnitts-Ueberschrift und ihrem Inhalt auf der Detailseite", group: "detail", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 8, dark: 8 } },
    { id: "detail.lineGap", label: "Zeilenabstand innerhalb eines Detailseiten-Abschnitts (Faehigkeits-/Detailzeilen)", group: "detail", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 6, dark: 6 } },
    { id: "detail.action.button.width", label: "Breite des gefuellten Aktionsknopfs auf der Detailseite", group: "detail", type: "number", unit: "px", min: 1, max: 720, step: 1, default: { light: 120, dark: 120 } },
    { id: "detail.action.button.height", label: "Hoehe des Aktionsknopfs auf der Detailseite", group: "detail", type: "number", unit: "px", min: 1, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "detail.screenshot.width", label: "Breite einer Kachel in der Screenshot-Galerie der Detailseite (16:9). Anzeige-Zielmass, unabhaengig von der nativen Bildaufloesung", group: "detail", type: "number", unit: "px", min: 1, max: 1200, step: 1, default: { light: 200, dark: 200 } },
    { id: "detail.screenshot.height", label: "Hoehe einer Screenshot-Kachel der Detailseite", group: "detail", type: "number", unit: "px", min: 1, max: 680, step: 1, default: { light: 112, dark: 112 } },
    { id: "detail.capability.iconSize", label: "Kantenlaenge des Icons je Faehigkeitszeile im Abschnitt 'Das darf dieses Paket'", group: "detail", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "detail.scrollStep", label: "Scrollschritt der Detailseite je Mausrad-Raste", group: "detail", type: "number", unit: "px", min: 1, max: 240, step: 1, default: { light: 40, dark: 40 } },

    // --- Faehigkeiten-Dialog ---
    { id: "dialog.width", label: "Breite der Flaeche des Faehigkeiten-Dialogs", group: "dialog", type: "number", unit: "px", min: 1, max: 2400, step: 1, default: { light: 400, dark: 400 } },
    { id: "dialog.padding", label: "Innenabstand der Dialog-Flaeche des Faehigkeiten-Dialogs", group: "dialog", type: "number", unit: "px", min: 0, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "dialog.buttonGap", label: "Abstand zwischen den beiden gleichwertigen Knoepfen im Faehigkeiten-Dialog", group: "dialog", type: "number", unit: "px", min: 0, max: 80, step: 1, default: { light: 12, dark: 12 } },

    // --- Bibliothek ---
    { id: "library.row.height", label: "Hoehe einer Bibliotheks-Listenzeile (Name, Version, Typ, Aktiv-Schalter, Ueberlaufmenue)", group: "library", type: "number", unit: "px", min: 1, max: 290, step: 1, default: { light: 48, dark: 48 } },
    { id: "library.row.gap", label: "Vertikaler Abstand zwischen zwei Bibliotheks-Listenzeilen", group: "library", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 4, dark: 4 } },
    { id: "library.toggle.width", label: "Breite des Aktiv/Inaktiv-Schalters am rechten Rand einer Bibliothekszeile", group: "library", type: "number", unit: "px", min: 1, max: 530, step: 1, default: { light: 88, dark: 88 } },
    { id: "library.reorderButton.width", label: "Breite der Nach-oben/Nach-unten-Schaltflaechen im Bibliotheks-Abschnitt 'Reihenfolge' (Tastatur-Alternative", group: "library", type: "number", unit: "px", min: 1, max: 170, step: 1, default: { light: 28, dark: 28 } },
    { id: "library.section.gap", label: "Vertikaler Abstand zwischen den Bibliotheks-Abschnitten (Liste / 'Reihenfolge' / Konflikthinweis)", group: "library", type: "number", unit: "px", min: 0, max: 150, step: 1, default: { light: 24, dark: 24 } },
    { id: "library.scrollStep", label: "Scrollschritt der Bibliothek je Mausrad-Raste", group: "library", type: "number", unit: "px", min: 1, max: 240, step: 1, default: { light: 40, dark: 40 } },

    // --- Zustands-Panels ---
    { id: "state.symbolSize", label: "Kantenlaenge des mittigen Zustands-Symbols in einem Zustands-Panel (leer / keine Treffer / Warnung)", group: "state", type: "number", unit: "px", min: 1, max: 390, step: 1, default: { light: 64, dark: 64 } },
    { id: "state.contentGap", label: "Vertikaler Abstand zwischen Symbol, Ueberschrift, Erklaersatz und Knopf innerhalb eines Zustands-Panels", group: "state", type: "number", unit: "px", min: 0, max: 100, step: 1, default: { light: 16, dark: 16 } },

    // --- Karussell und Download-Flyout ---
    { id: "carousel.dot.size", label: "Kantenlaenge eines Punkts der Karussell-Seitenanzeige (unten rechts)", group: "carousel", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 8, dark: 8 } },
    { id: "carousel.arrow.width", label: "Breite der Pfeil-Trefferflaeche links/rechts am Hero-Karussell (erscheint beim Ueberfahren)", group: "carousel", type: "number", unit: "px", min: 1, max: 200, step: 1, default: { light: 32, dark: 32 } },
    { id: "flyout.width", label: "Breite des Download-Flyouts unter der Kopfleisten-Download-Anzeige", group: "carousel", type: "number", unit: "px", min: 1, max: 1920, step: 1, default: { light: 320, dark: 320 } },
    { id: "flyout.row.height", label: "Hoehe einer Download-Flyout-Zeile (Name, Groesse, Fortschrittsbalken, Abbrechen)", group: "carousel", type: "number", unit: "px", min: 1, max: 390, step: 1, default: { light: 64, dark: 64 } },
    { id: "flyout.progress.height", label: "Hoehe des Fortschrittsbalkens einer Download-Flyout-Zeile", group: "carousel", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 4, dark: 4 } },

    // --- HUD-Rueckfalltokens ---
    { id: "hud.text.color", label: "HUD-Rueckfall-Textfarbe fuer Text- und Listen-Widgets, wenn ein HUD-Paket keine eigene Farbe vorgibt", group: "hud", type: "color", default: { light: "#1b1b1f", dark: "#eeeef0" } },
    { id: "hud.bar.track.color", label: "HUD-Rueckfall-Farbe des leeren Teils (Ruecklauf) eines Fortschrittsbalkens", group: "hud", type: "color", default: { light: "#ffffff", dark: "#232328" } },
    { id: "hud.bar.fill.color", label: "HUD-Rueckfall-Fuellfarbe eines Fortschrittsbalkens, wenn das HUD-Paket keine eigene #rrggbb-Farbe am Balken angibt", group: "hud", type: "color", default: { light: "#3a6ff0", dark: "#7ea1ff" } },
    { id: "hud.bar.defaultWidth", label: "HUD-Rueckfall-Balkenlaenge, wenn das HUD-Paket kein width angibt", group: "hud", type: "number", unit: "px", min: 1, max: 480, step: 1, default: { light: 80, dark: 80 } },
    { id: "hud.bar.defaultHeight", label: "HUD-Rueckfall-Balkenhoehe, wenn das HUD-Paket kein height angibt", group: "hud", type: "number", unit: "px", min: 1, max: 70, step: 1, default: { light: 4, dark: 4 } },
    { id: "hud.icon.defaultSize", label: "HUD-Rueckfall-Kantenlaenge, mit der ein HUD-Icon ohne eigene width/height gezeichnet wird", group: "hud", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "hud.list.lineGap", label: "HUD-Zeilenabstand zwischen den Eintraegen eines Listen-Widgets", group: "hud", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 2, dark: 2 } },

    // --- Blur ---
    { id: "blur.surface", label: "Weichzeichnungsradius der durchscheinenden Flaechen (Acrylic/Mica-Anmutung)", group: "blur", type: "number", unit: "px", min: 0, max: 80, step: 1, default: { light: 12, dark: 12 } },

    // --- Schriftgroessen ---
    { id: "type.titleLarge", label: "Schriftgroesse Typo-Stufe 'title.large'", group: "type", type: "number", unit: "px", min: 1, max: 170, step: 1, default: { light: 28, dark: 28 } },
    { id: "type.title", label: "Schriftgroesse Typo-Stufe 'title'", group: "type", type: "number", unit: "px", min: 1, max: 120, step: 1, default: { light: 20, dark: 20 } },
    { id: "type.subtitle", label: "Schriftgroesse Typo-Stufe 'subtitle'", group: "type", type: "number", unit: "px", min: 1, max: 100, step: 1, default: { light: 16, dark: 16 } },
    { id: "type.body", label: "Schriftgroesse Typo-Stufe 'body'", group: "type", type: "number", unit: "px", min: 1, max: 90, step: 1, default: { light: 14, dark: 14 } },
    { id: "type.caption", label: "Schriftgroesse Typo-Stufe 'caption'", group: "type", type: "number", unit: "px", min: 1, max: 80, step: 1, default: { light: 12, dark: 12 } },

    // --- Erhebung ---
    { id: "elevation.rest", label: "Schattentiefe/Weichzeichnung einer Karte in Ruhe", group: "elevation", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 2, dark: 2 } },
    { id: "elevation.hover", label: "Schattentiefe/Weichzeichnung einer Karte beim Ueberfahren", group: "elevation", type: "number", unit: "px", min: 0, max: 90, step: 1, default: { light: 12, dark: 14 } },

    // --- Bewegung ---
    { id: "motion.hover.duration", label: "Dauer fuer Ueberfahren-Uebergaenge (docs/UI-SPEC.md: 150 ms)", group: "motion", type: "number", unit: "ms", min: 0, max: 900, step: 1, default: { light: 150, dark: 150 } },
    { id: "motion.page.duration", label: "Dauer fuer Seitenwechsel (docs/UI-SPEC.md: 250 ms)", group: "motion", type: "number", unit: "ms", min: 0, max: 1500, step: 1, default: { light: 250, dark: 250 } },
    { id: "motion.page.liftDistance", label: "Aufwaertsbewegung beim Seitenwechsel in px (docs/UI-SPEC.md: 8 px)", group: "motion", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 8, dark: 8 } },
    { id: "motion.carousel.duration", label: "Dauer fuer Karussell-Uebergaenge (docs/UI-SPEC.md: 400 ms)", group: "motion", type: "number", unit: "ms", min: 0, max: 2400, step: 1, default: { light: 400, dark: 400 } },

    // --- Fokus ---
    { id: "focus.ring.width", label: "Breite des sichtbaren Fokusrahmens (Barrierefreiheit, docs/UI-SPEC.md)", group: "focus", type: "number", unit: "px", min: 0, max: 70, step: 1, default: { light: 2, dark: 2 } },
    { id: "focus.ring.color", label: "Farbe des sichtbaren Fokusrahmens", group: "focus", type: "color", default: { light: "#3a6ff0", dark: "#7ea1ff" } },

    // --- Farben ---
    { id: "color.bg", label: "Hintergrundfarbe des Spielhintergrunds/Store-Fensters", group: "color", type: "color", default: { light: "#f3f3f4", dark: "#1b1b1f" } },
    { id: "color.surfaceSolid", label: "Deckende Flaeche (z. B. Karten ohne Transparenz)", group: "color", type: "color", default: { light: "#ffffff", dark: "#232328" } },
    { id: "color.text", label: "Standard-Textfarbe", group: "color", type: "color", default: { light: "#1b1b1f", dark: "#eeeef0" } },
    { id: "color.textMuted", label: "Gedaempfte Textfarbe (z. B. Autorenzeile, caption)", group: "color", type: "color", default: { light: "#5b5b63", dark: "#b3b3ba" } },
    { id: "color.accent", label: "Einzige Akzentfarbe des Themes", group: "color", type: "color", default: { light: "#3a6dec", dark: "#7ea1ff" } },
    { id: "color.accentContrast", label: "Textfarbe auf Akzentflaeche (z. B. gefuellter Aktionsknopf)", group: "color", type: "color", default: { light: "#ffffff", dark: "#14141a" } },
    { id: "color.disabledText", label: "Textfarbe fuer deaktivierte Bedienelemente", group: "color", type: "color", default: { light: "#8b8b8b", dark: "#6c6c74" } },
    { id: "color.danger", label: "Farbe fuer Fehler-/Gefahrenzustaende", group: "color", type: "color", default: { light: "#cc351e", dark: "#cc351e" } },
    { id: "color.surfaceTranslucent", label: "Durchscheinende Flaeche (Acrylic/Mica) ueber dem Spielhintergrund", group: "color", type: "color-alpha", default: { light: { hex: "#ffffff", alpha: 0.72 }, dark: { hex: "#28282e", alpha: 0.72 } } },
    { id: "color.borderTranslucent", label: "Durchscheinender Rand von Karten/Feldern", group: "color", type: "color-alpha", default: { light: { hex: "#000000", alpha: 0.12 }, dark: { hex: "#ffffff", alpha: 0.14 } } },
    { id: "color.overlayScrim", label: "Abdunklung des Inhaltsbereichs hinter einer Ueberlagerung (z. B. Nav-Rail im schmalen Fenster)", group: "color", type: "color-alpha", default: { light: { hex: "#000000", alpha: 0.6 }, dark: { hex: "#000000", alpha: 0.6 } } }
  ];

  var TOKEN_GROUPS = [
    { id: "space", label: "Abstaende" },
    { id: "radius", label: "Radien" },
    { id: "layout", label: "Masse (Nav, Kopf-/Fusszeile, Kachel, Karussellbild, Icons, Suche, Menue, Breakpoints)" },
    { id: "cards", label: "Kachel und Kachelreihe" },
    { id: "detail", label: "Detailseite" },
    { id: "dialog", label: "Faehigkeiten-Dialog" },
    { id: "library", label: "Bibliothek" },
    { id: "state", label: "Zustands-Panels" },
    { id: "carousel", label: "Karussell und Download-Flyout" },
    { id: "hud", label: "HUD-Rueckfalltokens" },
    { id: "blur", label: "Blur" },
    { id: "type", label: "Schriftgroessen" },
    { id: "elevation", label: "Erhebung" },
    { id: "motion", label: "Bewegung" },
    { id: "focus", label: "Fokus" },
    { id: "color", label: "Farben" }
  ];

  var TOKENS_BY_ID = {};
  TOKENS.forEach(function (t) {
    t.cssVar = tokenIdToCssVar(t.id);
    TOKENS_BY_ID[t.id] = t;
  });

  /**
   * Text-auf-Hintergrund-Kombinationen, die auf WCAG-AA-Kontrast (>= 4.5:1)
   * geprueft werden. Beide Seiten muessen type "color" sein (Solidfarbe,
   * sonst nicht sinnvoll berechenbar) -- alle fuenf Token-Namen hier sind
   * bereits die kanonischen Schema-Namen, keine Aenderung noetig.
   */
  var CONTRAST_PAIRS = [
    { id: "text-on-bg", label: "Text auf Hintergrund", textToken: "color.text", bgToken: "color.bg" },
    { id: "textmuted-on-bg", label: "Gedaempfter Text auf Hintergrund", textToken: "color.textMuted", bgToken: "color.bg" },
    { id: "text-on-surface", label: "Text auf Flaeche", textToken: "color.text", bgToken: "color.surfaceSolid" },
    { id: "accentcontrast-on-accent", label: "Knopftext auf Akzentflaeche", textToken: "color.accentContrast", bgToken: "color.accent" },
    { id: "danger-on-surface", label: "Fehlertext auf Flaeche", textToken: "color.danger", bgToken: "color.surfaceSolid" }
  ];

  function buildDefaultVariant(variant) {
    var out = {};
    TOKENS.forEach(function (t) {
      out[t.id] = t.default[variant];
    });
    return out;
  }

  var DEFAULT_THEME = {
    schemaVersion: 1,
    light: buildDefaultVariant("light"),
    dark: buildDefaultVariant("dark")
  };

  return {
    TOKENS: TOKENS,
    TOKENS_BY_ID: TOKENS_BY_ID,
    TOKEN_GROUPS: TOKEN_GROUPS,
    CONTRAST_PAIRS: CONTRAST_PAIRS,
    DEFAULT_THEME: DEFAULT_THEME,
    tokenIdToCssVar: tokenIdToCssVar
  };
});
