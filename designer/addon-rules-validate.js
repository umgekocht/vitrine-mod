"use strict";

/*
 * Vitrine Designer -- AD4 Validator fuer Addon-Verhaltensregeln
 * (`regeln/*.json` in einem .vpkg mit `category: addon` + `capabilities:
 * [verhalten]`, ROADMAP AD2/AD4).
 *
 * Reine JS-Portierung von
 * mod/src/main/java/de/spassglas/vitrine/addon/AddonRule.java#parse: gleiche
 * Ereignis-/Aktions-/Befehls-Whitelist, gleiche Grenzen (MAX_RULES,
 * MAX_ACTIONS), gleiche Pruefreihenfolge -- damit ein Paketautor beim
 * Validieren im Designer oder per `cli vitrine.js validate` dieselbe Art
 * Meldung bekommt wie der Mod beim tatsaechlichen Laden (Charter-Regel 8
 * sinngemaess: es gibt kein JSON-Schema fuer dieses Format -- AddonRule.java
 * ist stattdessen die eine Quelle der Wahrheit, dieser Auswerter mimt sie
 * nachgebildet statt ein zweites, abweichendes Regelwerk zu erfinden.
 * AENDERT SICH AddonRule.java, MUSS diese Datei von Hand nachgezogen werden.)
 *
 * Kein DOM-Zugriff -- unter Node testbar (designer/test/addon-rules-
 * validate.test.js) und im Browser per <script> eingebunden (kein
 * type="module", kein fetch(), siehe app.js-Kopfkommentar aus D1).
 *
 * API:
 *  - EVENTS / ALLOWED_COMMANDS / MAX_RULES / MAX_ACTIONS: dieselben
 *    Konstanten wie AddonRule.java (siehe dort fuer die genaue Begruendung
 *    jeder Whitelist).
 *  - validateRulesInstance(instance): rohes, bereits per JSON.parse
 *    geparstes Regelwerk (Array ODER { "regeln": [...] }) -> string[]
 *    (leer = gueltig, sonst genau EINE Meldung -- AddonRule.parse() wirft
 *    beim ersten Verstoss, dieser Auswerter bricht aus demselben Grund
 *    ebenfalls beim ersten Verstoss ab statt eine Sammel-Liste zu bauen).
 *  - validateRulesJson(rawText): wie oben, aber nimmt den rohen Dateitext
 *    von `regeln/*.json` entgegen und meldet kaputtes JSON separat.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.VitrineAddonRules = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {

  /** block_abgebaut, item_benutzt, beigetreten, mob_besiegt, timer -- siehe AddonRule.EVENTS. */
  var EVENTS = ["block_abgebaut", "item_benutzt", "beigetreten", "mob_besiegt", "timer"];

  /** Befehls-Whitelist -- siehe AddonRule.ALLOWED_COMMANDS. */
  var ALLOWED_COMMANDS = ["give", "effect", "summon", "particle", "playsound", "title", "tellraw", "xp",
    "experience", "weather", "time", "setblock", "say", "enchant"];

  var MAX_RULES = 64;
  var MAX_ACTIONS = 16;

  function isPlainObject(value) {
    return typeof value === "object" && value !== null && !Array.isArray(value);
  }

  /** Nachbildung von java.util.Set#toString(): "[a, b, c]". */
  function setToString(list) {
    return "[" + list.join(", ") + "]";
  }

  function clamp(v, lo, hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  /** Wie AddonRule#str(JsonObject, String): Pflichtfeld, muss ein primitiver Wert sein. */
  function requireString(obj, key) {
    if (!Object.prototype.hasOwnProperty.call(obj, key) || obj[key] === null || typeof obj[key] === "object") {
      return { ok: false, error: "'" + key + "' fehlt" };
    }
    return { ok: true, value: String(obj[key]) };
  }

  /** Eine einzelne Aktion aus "dann" -- Nachbildung von AddonRule#action(JsonObject). */
  function validateAction(a) {
    if (!isPlainObject(a)) {
      return "unbekannte Aktion " + setToString(Object.keys(a || {}));
    }
    if (Object.prototype.hasOwnProperty.call(a, "nachricht")) {
      var nachricht = requireString(a, "nachricht");
      return nachricht.ok ? null : nachricht.error;
    }
    if (Object.prototype.hasOwnProperty.call(a, "geben")) {
      var geben = requireString(a, "geben");
      return geben.ok ? null : geben.error;
    }
    if (Object.prototype.hasOwnProperty.call(a, "effekt")) {
      var effekt = requireString(a, "effekt");
      return effekt.ok ? null : effekt.error;
    }
    if (Object.prototype.hasOwnProperty.call(a, "xp")) {
      if (typeof a.xp !== "number" || !isFinite(a.xp)) {
        return "'xp' muss eine Zahl sein";
      }
      return null;
    }
    if (Object.prototype.hasOwnProperty.call(a, "befehl")) {
      var befehl = requireString(a, "befehl");
      if (!befehl.ok) {
        return befehl.error;
      }
      var cmd = befehl.value.trim();
      if (cmd.charAt(0) === "/") {
        cmd = cmd.substring(1);
      }
      var rootWord = cmd.split(/\s+/)[0].toLowerCase();
      if (ALLOWED_COMMANDS.indexOf(rootWord) === -1 || cmd.length > 300) {
        return "Befehl '" + rootWord + "' ist fuer Addons nicht erlaubt (erlaubt: " + setToString(ALLOWED_COMMANDS) + ")";
      }
      return null;
    }
    return "unbekannte Aktion " + setToString(Object.keys(a));
  }

  /**
   * Nachbildung von AddonRule#parse(byte[]): `instance` ist bereits per
   * JSON.parse geparst (Array ODER { "regeln": [...] }). Liefert string[] --
   * leer bei Erfolg, sonst genau EIN Eintrag (fail-fast, wie die Java-Seite,
   * die bei der ersten Verletzung wirft).
   */
  function validateRulesInstance(instance) {
    var rules;
    if (Array.isArray(instance)) {
      rules = instance;
    } else if (isPlainObject(instance) && Array.isArray(instance.regeln)) {
      rules = instance.regeln;
    } else {
      return ["'regeln' fehlt"];
    }

    if (rules.length > MAX_RULES) {
      return ["mehr als " + MAX_RULES + " Regeln"];
    }

    for (var i = 0; i < rules.length; i++) {
      var o = rules[i];
      if (!isPlainObject(o)) {
        return ["Regel " + i + " ist kein JSON-Objekt"];
      }

      var wenn = requireString(o, "wenn");
      if (!wenn.ok) {
        return [wenn.error];
      }
      var event = wenn.value.toLowerCase();
      if (EVENTS.indexOf(event) === -1) {
        return ["unbekanntes Ereignis '" + event + "' (erlaubt: " + setToString(EVENTS) + ")"];
      }

      if (Object.prototype.hasOwnProperty.call(o, "alle_sekunden") && typeof o.alle_sekunden !== "number") {
        return ["'alle_sekunden' muss eine Zahl sein"];
      }
      if (Object.prototype.hasOwnProperty.call(o, "chance") && typeof o.chance !== "number") {
        return ["'chance' muss eine Zahl sein"];
      }

      var dann = o.dann;
      if (!Array.isArray(dann) || dann.length === 0 || dann.length > MAX_ACTIONS) {
        return ["'dann' braucht 1 bis " + MAX_ACTIONS + " Aktionen"];
      }

      for (var j = 0; j < dann.length; j++) {
        var actionError = validateAction(dann[j]);
        if (actionError) {
          return [actionError];
        }
      }
    }

    return [];
  }

  /** Wie validateRulesInstance, nimmt aber den rohen Dateitext entgegen (meldet kaputtes JSON separat). */
  function validateRulesJson(rawText) {
    var instance;
    try {
      instance = JSON.parse(rawText);
    } catch (e) {
      return ["ist kein gueltiges JSON: " + e.message];
    }
    return validateRulesInstance(instance);
  }

  return {
    EVENTS: EVENTS,
    ALLOWED_COMMANDS: ALLOWED_COMMANDS,
    MAX_RULES: MAX_RULES,
    MAX_ACTIONS: MAX_ACTIONS,
    validateRulesInstance: validateRulesInstance,
    validateRulesJson: validateRulesJson
  };
});
