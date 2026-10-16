"use strict";

/*
 * Vitrine Designer -- D3 Verdrahtungslogik zwischen Theme-Formular und DOM.
 *
 * Wie manifest-form.js: kein DOM-Zugriff hier, unter Node direkt testbar
 * (designer/test/theme-editor-wiring.test.js) und im Browser per <script>
 * eingebunden (kein type="module", kein fetch()). app.js ruft diese
 * Funktionen auf, um die tatsaechlichen <input>-Elemente zu erzeugen/lesen.
 *
 * Zustaendigkeiten:
 *  - ensureTheme(themeField): project.theme fehlt -> ein frisches, vom
 *    Default unabhaengiges Theme-Objekt; project.theme vorhanden aber
 *    kaputt/unvollstaendig -> pro Variante mit fillVariantDefaults()
 *    aufgefuellt. Gibt IMMER ein neues Objekt zurueck (keine geteilte
 *    Referenz mit VitrineThemeTokens.DEFAULT_THEME).
 *  - fieldId/errorId: einzige Quelle fuer die HTML-id-Konvention der
 *    generierten Formularfelder. app.js benutzt dieselben Funktionen sowohl
 *    beim Erzeugen der <input>-Elemente als auch beim spaeteren Auslesen --
 *    dadurch koennen HTML-Markup und JS-Zugriff nicht auseinanderlaufen (es
 *    gibt kein statisches Formular-Markup, das von Hand synchron gehalten
 *    werden muesste).
 *  - groupedTokens(): VitrineThemeTokens.TOKENS gruppiert nach
 *    VitrineThemeTokens.TOKEN_GROUPS, in Gruppenreihenfolge, fuer den Aufbau
 *    des Formulars.
 *  - collectFormValues(variant, getValue): liest ueber eine vom Aufrufer
 *    bereitgestellte getValue(fieldId)-Funktion (typischerweise
 *    document.getElementById(id).value) die Formularwerte EINER Variante ein
 *    und liefert sie in der Form, die
 *    VitrineThemeEditor.buildVariantInstance() erwartet (Schluessel =
 *    Token-Id bzw. "tokenId.hex"/"tokenId.alpha").
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory(require("./theme-tokens.js"), require("./theme-editor.js"));
  } else {
    root.VitrineThemeWiring = factory(root.VitrineThemeTokens, root.VitrineThemeEditor);
  }
})(typeof self !== "undefined" ? self : this, function (Tokens, Editor) {

  var VARIANTS = ["light", "dark"];

  /** project.theme (roh, evtl. fehlend/kaputt) -> vollstaendiges, neues Theme-Objekt. */
  function ensureTheme(themeField) {
    var isObj = themeField && typeof themeField === "object" && !Array.isArray(themeField);
    var lightSrc = isObj ? themeField.light : undefined;
    var darkSrc = isObj ? themeField.dark : undefined;
    return {
      schemaVersion: 1,
      light: Editor.fillVariantDefaults(lightSrc, "light"),
      dark: Editor.fillVariantDefaults(darkSrc, "dark")
    };
  }

  /** "space.unit" -> "space-unit" (fuer HTML-ids, Punkte sind dort unueblich). */
  function tokenIdToSlug(tokenId) {
    return tokenId.replace(/\./g, "-");
  }

  /** HTML-id eines Formularfelds. "part" ist bei color-alpha-Tokens "hex" oder "alpha", sonst weglassen. */
  function fieldId(variant, token, part) {
    var tokenId = typeof token === "string" ? token : token.id;
    var base = "theme-field-" + variant + "-" + tokenIdToSlug(tokenId);
    return part ? base + "-" + part : base;
  }

  /** HTML-id des Fehlerbereichs eines Formularfelds (immer ohne "part" -- ein Fehlerbereich pro Token, auch bei color-alpha). */
  function errorId(variant, tokenId) {
    return "theme-error-" + variant + "-" + tokenIdToSlug(tokenId);
  }

  /** VitrineThemeTokens.TOKENS, gruppiert nach TOKEN_GROUPS, in Gruppenreihenfolge. */
  function groupedTokens() {
    return Tokens.TOKEN_GROUPS.map(function (group) {
      return {
        id: group.id,
        label: group.label,
        tokens: Tokens.TOKENS.filter(function (t) {
          return t.group === group.id;
        })
      };
    });
  }

  /**
   * Liest ueber getValue(id) die rohen Formularwerte einer Variante ein und
   * liefert sie in der von VitrineThemeEditor.buildVariantInstance()
   * erwarteten Form (Schluessel = Token-Id bzw. "tokenId.hex"/"tokenId.alpha").
   */
  function collectFormValues(variant, getValue) {
    var values = {};
    Tokens.TOKENS.forEach(function (t) {
      if (t.type === "color-alpha") {
        var names = Editor.alphaFieldNames(t.id);
        values[names.hex] = getValue(fieldId(variant, t, "hex"));
        values[names.alpha] = getValue(fieldId(variant, t, "alpha"));
      } else {
        values[t.id] = getValue(fieldId(variant, t));
      }
    });
    return values;
  }

  function otherVariant(variant) {
    return variant === "light" ? "dark" : "light";
  }

  return {
    VARIANTS: VARIANTS,
    ensureTheme: ensureTheme,
    tokenIdToSlug: tokenIdToSlug,
    fieldId: fieldId,
    errorId: errorId,
    groupedTokens: groupedTokens,
    collectFormValues: collectFormValues,
    otherVariant: otherVariant
  };
});
