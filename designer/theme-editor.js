"use strict";

/*
 * Vitrine Designer -- D3 Theme-Editor-Logik.
 *
 * Kein DOM-Zugriff hier (wie manifest-form.js/schema-validate.js) -- unter
 * Node direkt testbar (designer/test/theme-editor.test.js) und im Browser
 * per <script> eingebunden (kein type="module", kein fetch(), siehe
 * app.js-Kopfkommentar aus D1).
 *
 * Zustaendigkeiten:
 *  - Formularwerte (rohe Strings aus <input>) <-> ein Variant-Objekt
 *    (buildVariantInstance / variantToFormValues).
 *  - Fehlende/kaputte Werte gegen den Default auffuellen
 *    (fillVariantDefaults) -- Vorstufe zu T2s "kaputtes Theme -> Standard +
 *    Meldung", hier schon fuer den Editor selbst gebraucht, damit ein
 *    unvollstaendiges gespeichertes Theme nicht abstuerzt.
 *  - Variant-Objekt -> CSS-Custom-Properties fuer die Live-Vorschau
 *    (cssVarsFromVariant).
 *  - Kontrastberechnung nach WCAG (relative Luminanz, Kontrastverhaeltnis)
 *    und computeContrastWarnings() gegen VitrineThemeTokens.CONTRAST_PAIRS,
 *    Schwelle 4.5:1 (WCAG AA fuer Text, siehe docs/UI-SPEC.md
 *    "Barrierefreiheit"). Eigene Implementierung, keine neue Abhaengigkeit.
 *  - Leichte Feldpruefung (validateVariantByField): Zahl im erlaubten
 *    Bereich, Hex-Farbe im richtigen Format.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory(require("./theme-tokens.js"));
  } else {
    root.VitrineThemeEditor = factory(root.VitrineThemeTokens);
  }
})(typeof self !== "undefined" ? self : this, function (Tokens) {

  var HEX_RE = /^#([0-9a-fA-F]{6})$/;

  function isValidHex(value) {
    return typeof value === "string" && HEX_RE.test(value);
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  // --- Farbe / Kontrast ---

  function hexToRgb(hex) {
    if (!isValidHex(hex)) {
      return null;
    }
    var n = hex.slice(1);
    return {
      r: parseInt(n.slice(0, 2), 16),
      g: parseInt(n.slice(2, 4), 16),
      b: parseInt(n.slice(4, 6), 16)
    };
  }

  function srgbChannelToLinear(c) {
    var cs = c / 255;
    return cs <= 0.04045 ? cs / 12.92 : Math.pow((cs + 0.055) / 1.055, 2.4);
  }

  /** WCAG relative Luminanz (0..1) eines "#rrggbb"-Hexwerts, oder null bei ungueltiger Farbe. */
  function relativeLuminance(hex) {
    var rgb = hexToRgb(hex);
    if (!rgb) {
      return null;
    }
    var r = srgbChannelToLinear(rgb.r);
    var g = srgbChannelToLinear(rgb.g);
    var b = srgbChannelToLinear(rgb.b);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  /** WCAG-Kontrastverhaeltnis (1..21) zwischen zwei "#rrggbb"-Hexwerten, oder null bei ungueltiger Eingabe. */
  function contrastRatio(hexA, hexB) {
    var lA = relativeLuminance(hexA);
    var lB = relativeLuminance(hexB);
    if (lA === null || lB === null) {
      return null;
    }
    var lighter = Math.max(lA, lB);
    var darker = Math.min(lA, lB);
    return (lighter + 0.05) / (darker + 0.05);
  }

  var AA_THRESHOLD = 4.5;

  /**
   * Prueft jedes Paar aus VitrineThemeTokens.CONTRAST_PAIRS gegen die
   * uebergebene Variant-Instanz. Gibt fuer JEDES Paar einen Eintrag zurueck
   * (auch bestandene), damit der Editor "alles ok" genauso anzeigen kann wie
   * eine Warnung -- kein Zustand nur ueber Farbe (docs/UI-SPEC.md).
   */
  function computeContrastWarnings(variant) {
    variant = variant || {};
    return Tokens.CONTRAST_PAIRS.map(function (pair) {
      var textValue = variant[pair.textToken];
      var bgValue = variant[pair.bgToken];
      var ratio = (typeof textValue === "string" && typeof bgValue === "string")
        ? contrastRatio(textValue, bgValue)
        : null;
      var passes = ratio !== null && ratio >= AA_THRESHOLD;
      return {
        id: pair.id,
        label: pair.label,
        textToken: pair.textToken,
        bgToken: pair.bgToken,
        ratio: ratio,
        passes: passes,
        message: ratio === null
          ? "Kontrast konnte nicht berechnet werden (ungueltige Farbe)."
          : "Kontrastverhaeltnis " + ratio.toFixed(2) + ":1" +
            (passes ? " -- erfuellt WCAG AA (4.5:1)." : " -- UNTER WCAG AA (4.5:1 gefordert).")
      };
    });
  }

  // --- Defaults auffuellen ---

  /** Fehlende oder kaputte (falscher Typ) Werte in "variant" durch den Default der jeweiligen Variante ersetzen. Gibt immer ein NEUES, vollstaendiges Objekt zurueck. */
  function fillVariantDefaults(variant, variantName) {
    variant = variant && typeof variant === "object" ? variant : {};
    var defaults = Tokens.DEFAULT_THEME[variantName] || Tokens.DEFAULT_THEME.light;
    var out = {};
    Tokens.TOKENS.forEach(function (t) {
      var value = variant[t.id];
      out[t.id] = isValueValidForToken(value, t) ? value : defaults[t.id];
    });
    return out;
  }

  function isValueValidForToken(value, token) {
    if (value === undefined || value === null) {
      return false;
    }
    if (token.type === "number") {
      return typeof value === "number" && isFinite(value);
    }
    if (token.type === "color") {
      return isValidHex(value);
    }
    if (token.type === "color-alpha") {
      return value && typeof value === "object" &&
        isValidHex(value.hex) &&
        typeof value.alpha === "number" && isFinite(value.alpha);
    }
    return true;
  }

  // --- Formular <-> Variant-Instanz ---

  /** Feldname im Formular fuer ein color-alpha-Token: "token.id.hex" und "token.id.alpha". */
  function alphaFieldNames(tokenId) {
    return { hex: tokenId + ".hex", alpha: tokenId + ".alpha" };
  }

  /** Rohe Formularwerte (Strings, siehe alphaFieldNames fuer color-alpha) -> typisierte Variant-Instanz. Ungueltige/leere Eingaben werden ausgelassen (spaeter per fillVariantDefaults aufgefuellt). */
  function buildVariantInstance(formValues) {
    formValues = formValues || {};
    var instance = {};
    Tokens.TOKENS.forEach(function (t) {
      if (t.type === "number") {
        var raw = formValues[t.id];
        if (raw === undefined || raw === null || String(raw).trim() === "") {
          return;
        }
        var num = Number(raw);
        if (isFinite(num)) {
          instance[t.id] = num;
        }
      } else if (t.type === "color") {
        var hex = formValues[t.id];
        if (typeof hex === "string" && hex.trim() !== "") {
          instance[t.id] = hex.trim();
        }
      } else if (t.type === "color-alpha") {
        var names = alphaFieldNames(t.id);
        var hexVal = formValues[names.hex];
        var alphaRaw = formValues[names.alpha];
        if (typeof hexVal === "string" && hexVal.trim() !== "") {
          var alphaNum = alphaRaw === undefined || alphaRaw === null || String(alphaRaw).trim() === ""
            ? 1
            : Number(alphaRaw);
          if (!isFinite(alphaNum)) {
            alphaNum = 1;
          }
          instance[t.id] = { hex: hexVal.trim(), alpha: clamp(alphaNum, 0, 1) };
        }
      }
    });
    return instance;
  }

  /** Umkehrung: Variant-Instanz -> rohe Formularwerte (fuer das Vorbefuellen der Felder). */
  function variantToFormValues(variant) {
    variant = variant || {};
    var values = {};
    Tokens.TOKENS.forEach(function (t) {
      var value = variant[t.id];
      if (t.type === "color-alpha") {
        var names = alphaFieldNames(t.id);
        if (value && typeof value === "object") {
          values[names.hex] = value.hex || "";
          values[names.alpha] = typeof value.alpha === "number" ? String(value.alpha) : "";
        } else {
          values[names.hex] = "";
          values[names.alpha] = "";
        }
      } else {
        values[t.id] = value === undefined || value === null ? "" : String(value);
      }
    });
    return values;
  }

  // --- Feldpruefung (leichtgewichtig, kein Schema -- siehe theme-tokens.js-Kopfkommentar) ---

  function validateVariantByField(variant) {
    variant = variant || {};
    var byField = {};
    Tokens.TOKENS.forEach(function (t) {
      var value = variant[t.id];
      var messages = [];
      if (t.type === "number") {
        if (typeof value === "number" && isFinite(value)) {
          if (value < t.min || value > t.max) {
            messages.push("Wert " + value + t.unit + " liegt ausserhalb von " + t.min + "-" + t.max + t.unit + ".");
          }
        } else if (value !== undefined) {
          messages.push("Kein gueltiger Zahlenwert.");
        }
      } else if (t.type === "color") {
        if (value !== undefined && !isValidHex(value)) {
          messages.push("Keine gueltige Farbe (erwartet #rrggbb).");
        }
      } else if (t.type === "color-alpha") {
        if (value !== undefined) {
          if (!value || typeof value !== "object" || !isValidHex(value.hex)) {
            messages.push("Keine gueltige Farbe (erwartet #rrggbb).");
          } else if (typeof value.alpha !== "number" || value.alpha < 0 || value.alpha > 1) {
            messages.push("Deckkraft muss zwischen 0 und 1 liegen.");
          }
        }
      }
      if (messages.length > 0) {
        byField[t.id] = messages;
      }
    });
    return byField;
  }

  // --- Vorschau: Variant-Instanz -> CSS-Custom-Properties ---

  function rgbaString(colorAlpha) {
    var rgb = hexToRgb(colorAlpha.hex);
    if (!rgb) {
      return null;
    }
    return "rgba(" + rgb.r + ", " + rgb.g + ", " + rgb.b + ", " + colorAlpha.alpha + ")";
  }

  /** Vollstaendig aufgefuellte Variant-Instanz -> { "--vt-...": "wert" }-Map, fuer element.style.setProperty() in der Live-Vorschau. */
  function cssVarsFromVariant(variant) {
    variant = variant || {};
    var out = {};
    Tokens.TOKENS.forEach(function (t) {
      var value = variant[t.id];
      if (value === undefined || value === null) {
        return;
      }
      if (t.type === "number") {
        out[t.cssVar] = value + (t.unit || "");
      } else if (t.type === "color") {
        out[t.cssVar] = value;
      } else if (t.type === "color-alpha") {
        var rgba = rgbaString(value);
        if (rgba) {
          out[t.cssVar] = rgba;
        }
      }
    });
    return out;
  }

  return {
    hexToRgb: hexToRgb,
    relativeLuminance: relativeLuminance,
    contrastRatio: contrastRatio,
    AA_THRESHOLD: AA_THRESHOLD,
    computeContrastWarnings: computeContrastWarnings,
    fillVariantDefaults: fillVariantDefaults,
    buildVariantInstance: buildVariantInstance,
    variantToFormValues: variantToFormValues,
    alphaFieldNames: alphaFieldNames,
    validateVariantByField: validateVariantByField,
    rgbaString: rgbaString,
    cssVarsFromVariant: cssVarsFromVariant,
    isValidHex: isValidHex
  };
});
