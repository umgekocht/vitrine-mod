"use strict";

/*
 * Vitrine Designer -- D2 Formular-Anbindung ans Schema.
 *
 * Bindeglied zwischen den rohen Formularfeldern (Strings aus <input>/
 * <textarea>) und dem Validator aus schema-validate.js. Enthaelt keinen
 * DOM-Zugriff -- unter Node direkt testbar (designer/test/manifest-form.test.js)
 * und im Browser per <script> eingebunden (kein type="module", kein fetch(),
 * siehe app.js-Kopfkommentar aus D1 -- Laufwerk Z: kann kein file://-Modul
 * nachladen).
 *
 * Zustaendigkeiten:
 *  - buildManifestInstance(formValues, schema): rohe Formularwerte (alles
 *    Strings, Arrays als Text mit einem Eintrag pro Zeile) -> ein
 *    Manifest-Objekt in den Typen, die vitrine.schema.json vorschreibt
 *    (schemaVersion als integer, capabilities/screenshots als Array).
 *    Ein leeres Feld (nach trim()) wird komplett aus dem Objekt entfernt --
 *    ein frisches, noch nicht ausgefuelltes Pflichtfeld erscheint damit als
 *    "fehlt" (kind: "required"), genau wie ein in einer vitrine.json ganz
 *    fehlendes Feld. Diese bewusste Vereinfachung kann den Sonderfall aus
 *    registry/schema/examples/invalid/07-empty-name.json (Feld PRAESENT,
 *    aber Wert "") nicht per Tastatur nachbilden -- ein <input> kennt den
 *    Unterschied zwischen "nie befuellt" und "geleert" ohnehin nicht. Beim
 *    Anzeigen eines bereits geladenen Manifests (formValuesFromInstance)
 *    bleibt die Unterscheidung dagegen erhalten, solange das Feld nicht neu
 *    abgeschickt wird.
 *  - formValuesFromInstance(instance, schema): Umkehrung fuer das Vorbefuellen
 *    der Formularfelder aus einem bereits gespeicherten Projekt-Manifest.
 *  - manifestFieldPaths(schema): flache Liste aller Formularfelder als
 *    gepunktete Pfade ("id", "javaMod.loader", "javaMod.jar.sha256"). Aus dem
 *    Schema abgeleitet, nicht abgeschrieben -- ein neues Feld in
 *    registry/schema/vitrine.schema.json taucht hier von selbst auf. Nur
 *    Objekte werden aufgeklappt; Arrays bleiben ein Feld (ein Eintrag pro
 *    Zeile, siehe oben).
 *
 * GATE6 (Klasse B): 'javaMod' ist das erste verschachtelte Objekt im
 * Manifest. buildManifestInstance/formValuesFromInstance arbeiten deshalb auf
 * diesen Pfaden statt auf den Schluesseln der obersten Ebene. Ein
 * Teilobjekt, in dem kein einziges Feld ausgefuellt ist, wird gar nicht erst
 * angelegt -- ein Klasse-A-Paket bekommt also nie ein leeres 'javaMod'
 * untergeschoben (das waere nach dem Schema sofort ein Verstoss). 'hotApply'
 * ist das erste Boolean-Feld: "true"/"false" aus einem <select> werden zu
 * echten Booleans, alles andere bleibt String und faellt im Validator auf.
 *
 *  - groupMessagesByField(entries): {field, message}-Liste (aus
 *    validateDetailed) -> Map Feldname -> Meldungsliste, fuer die Anzeige
 *    direkt unter dem jeweiligen Formularfeld (Array-Indizes wie
 *    "capabilities[0]" werden dabei auf "capabilities" zusammengefasst).
 *  - validateManifestByField(instance, schema, validator): Kurzform der
 *    beiden Schritte validateDetailed() + groupMessagesByField().
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory(require("./schema-validate.js"));
  } else {
    root.VitrineManifestForm = factory(root.VitrineSchemaValidator);
  }
})(typeof self !== "undefined" ? self : this, function (defaultValidator) {

  function isIntegerType(propSchema) {
    return propSchema && (propSchema.type === "integer" || propSchema.type === "number");
  }

  function isArrayType(propSchema) {
    return propSchema && propSchema.type === "array";
  }

  function isObjectType(propSchema) {
    return propSchema && propSchema.type === "object" && propSchema.properties;
  }

  function isBooleanType(propSchema) {
    return propSchema && propSchema.type === "boolean";
  }

  /**
   * Alle Formularfelder als gepunktete Pfade, in Schema-Reihenfolge.
   * Verschachtelte Objekte werden aufgeklappt ("javaMod.jar.sha256"),
   * Arrays bleiben ein einzelnes Feld.
   */
  function manifestFieldPaths(schema, prefix) {
    var properties = (schema && schema.properties) || {};
    var paths = [];
    Object.keys(properties).forEach(function (key) {
      var full = prefix ? prefix + "." + key : key;
      if (isObjectType(properties[key])) {
        paths = paths.concat(manifestFieldPaths(properties[key], full));
      } else {
        paths.push(full);
      }
    });
    return paths;
  }

  /** Teilschema zu einem gepunkteten Pfad, oder undefined. */
  function schemaAtPath(schema, fieldPath) {
    var node = schema;
    var segments = fieldPath.split(".");
    for (var i = 0; i < segments.length; i++) {
      var properties = (node && node.properties) || {};
      node = properties[segments[i]];
      if (!node) {
        return undefined;
      }
    }
    return node;
  }

  /** Setzt `value` unter dem gepunkteten Pfad; Zwischenobjekte entstehen erst beim ersten echten Wert. */
  function setAtPath(target, fieldPath, value) {
    var segments = fieldPath.split(".");
    var node = target;
    for (var i = 0; i < segments.length - 1; i++) {
      if (!Object.prototype.hasOwnProperty.call(node, segments[i])) {
        node[segments[i]] = {};
      }
      node = node[segments[i]];
    }
    node[segments[segments.length - 1]] = value;
  }

  /** Liest den Wert unter dem gepunkteten Pfad, oder undefined. */
  function getAtPath(source, fieldPath) {
    var segments = fieldPath.split(".");
    var node = source;
    for (var i = 0; i < segments.length; i++) {
      if (node === null || node === undefined || typeof node !== "object") {
        return undefined;
      }
      node = node[segments[i]];
    }
    return node;
  }

  /** Ein Eintrag pro Zeile, leere Zeilen und Rand-Leerraum verworfen. */
  function splitLines(raw) {
    return String(raw)
      .split(/\r\n|\r|\n/)
      .map(function (line) { return line.trim(); })
      .filter(function (line) { return line.length > 0; });
  }

  /** "42" -> 42 (echte Ganzzahl); alles andere bleibt String (fuehrt zum Typ-Fehler im Validator). */
  function toIntegerOrRaw(trimmed) {
    if (/^-?\d+$/.test(trimmed)) {
      return parseInt(trimmed, 10);
    }
    return trimmed;
  }

  /** "true"/"false" -> echtes Boolean; alles andere bleibt String (fuehrt zum Typ-Fehler im Validator). */
  function toBooleanOrRaw(trimmed) {
    if (trimmed === "true") {
      return true;
    }
    if (trimmed === "false") {
      return false;
    }
    return trimmed;
  }

  function buildManifestInstance(formValues, schema) {
    var instance = {};
    formValues = formValues || {};

    manifestFieldPaths(schema).forEach(function (fieldPath) {
      if (!Object.prototype.hasOwnProperty.call(formValues, fieldPath)) {
        return;
      }
      var raw = formValues[fieldPath];
      if (raw === undefined || raw === null) {
        return;
      }
      var propSchema = schemaAtPath(schema, fieldPath);

      if (isArrayType(propSchema)) {
        var lines = splitLines(raw);
        if (lines.length > 0) {
          setAtPath(instance, fieldPath, lines);
        }
        return;
      }

      var trimmed = String(raw).trim();
      if (trimmed.length === 0) {
        return; // leeres Feld = noch nicht ausgefuellt, siehe Kopfkommentar
      }

      if (isIntegerType(propSchema)) {
        setAtPath(instance, fieldPath, toIntegerOrRaw(trimmed));
      } else if (isBooleanType(propSchema)) {
        setAtPath(instance, fieldPath, toBooleanOrRaw(trimmed));
      } else {
        setAtPath(instance, fieldPath, raw); // Strings unveraendert (kein Trimmen von Nutzinhalt)
      }
    });

    return instance;
  }

  function formValuesFromInstance(instance, schema) {
    var values = {};
    instance = instance || {};

    manifestFieldPaths(schema).forEach(function (fieldPath) {
      var value = getAtPath(instance, fieldPath);
      if (value === undefined || value === null) {
        values[fieldPath] = "";
        return;
      }
      if (Array.isArray(value)) {
        values[fieldPath] = value.join("\n");
      } else {
        values[fieldPath] = String(value);
      }
    });

    return values;
  }

  function groupMessagesByField(entries, validator) {
    validator = validator || defaultValidator;
    var map = {};
    (entries || []).forEach(function (entry) {
      var key = entry.field === null || entry.field === undefined ? "__root__" : validator.baseFieldName(entry.field);
      if (!map[key]) {
        map[key] = [];
      }
      map[key].push(entry.message);
    });
    return map;
  }

  function validateManifestByField(instance, schema, validator) {
    validator = validator || defaultValidator;
    var entries = validator.validateDetailed(schema, instance);
    return groupMessagesByField(entries, validator);
  }

  return {
    manifestFieldPaths: manifestFieldPaths,
    schemaAtPath: schemaAtPath,
    buildManifestInstance: buildManifestInstance,
    formValuesFromInstance: formValuesFromInstance,
    groupMessagesByField: groupMessagesByField,
    validateManifestByField: validateManifestByField
  };
});
