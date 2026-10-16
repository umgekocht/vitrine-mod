"use strict";

/*
 * Vitrine Designer -- D5 HUD-Editor-Logik.
 *
 * Kein DOM-Zugriff hier (wie manifest-form.js/theme-editor.js) -- unter Node
 * direkt testbar (designer/test/hud-editor.test.js) und im Browser per
 * <script> eingebunden (kein type="module", kein fetch(), siehe
 * app.js-Kopfkommentar aus D1).
 *
 * Datenmodell: project.hud = { schemaVersion: 1, widgets: [...] }, GENAU in
 * der Form, die registry/schema/hud.schema.json vorschreibt (Charter-Regel 8
 * "Ein Schema, zwei Seiten") -- kein Zwischenformat. Jeder Eintrag in
 * project.hud.widgets ist bereits ein fertiges Widget-Objekt (id, type,
 * anchor, offset?, scale?, visibility?, plus typspezifische Felder). Nur
 * WAEHREND des Bearbeitens EINES Widgets im Formular gibt es ein flaches
 * "Formularwerte"-Zwischenobjekt (alles Strings, siehe defaultWidgetFormValues)
 * -- dasselbe Muster wie theme-editor.js (Formular <-> Instanz).
 *
 * Enums (Anker, Datenquellen, Sichtbarkeits-/Widget-Typen) werden NICHT hier
 * dupliziert, sondern bei jedem Aufruf direkt aus dem uebergebenen
 * hud.schema.json (designer/hud-schema-data.js) gelesen -- aendert sich das
 * Schema (z. B. ein neuer Anker), muss diese Datei nicht angefasst werden.
 *
 * Zustaendigkeiten:
 *  - anchors/dataSources/visibilityTypes/widgetTypes(schema): Enums aus dem
 *    Schema lesen (fuer <select>-Optionen in app.js).
 *  - ensureHud(hudField): project.hud fehlt/kaputt -> frisches, valides
 *    Grundgeruest { schemaVersion: 1, widgets: [] }.
 *  - defaultWidgetFormValues(type)/widgetToFormValues(widget): frische bzw.
 *    aus einem vorhandenen Widget vorbefuellte Formularwerte.
 *  - buildWidgetInstance(formValues): Formularwerte -> fertiges
 *    Widget-Objekt in exakt der Schema-Form (leere optionale Felder werden
 *    ausgelassen, keine Platzhalter-Werte erfunden).
 *  - addEntry/removeEntry: die "entries[]" eines list-Widgets im Formular
 *    pflegen (variable Anzahl Zeilen).
 *  - addWidget/updateWidgetAt/removeWidgetAt/moveWidget: project.hud.widgets
 *    pflegen (hinzufuegen/aendern/entfernen/Zeichenreihenfolge verschieben).
 *  - validateDraftWidgetByField: EIN Widget-Entwurf (bevor er der Liste
 *    hinzugefuegt wird) live gegen hud.schema.json pruefen, ueber
 *    schema-validate.js (VitrineSchemaValidator, D5-Ergaenzung: $ref/oneOf) --
 *    kein zweiter Validator. Zusaetzlich eine Id-Eindeutigkeits-Pruefung
 *    (siehe hud.schema.json-Kopfkommentar zu "widgetId": JSON Schema kann das
 *    fuer ein Unterfeld eines Array-Elements nicht ausdruecken, das ist reine
 *    Editor-/Mod-Laufzeitpruefung).
 *  - validateHudDocument: das GANZE project.hud gegen hud.schema.json pruefen
 *    (fuer den Export/die Zusammenfassung).
 *  - importHud(rawHud): ein bereits vorhandenes HUD-Dokument (z. B. eine
 *    eingefuegte hud.json) einmal durch das Editor-Datenmodell schicken
 *    (jedes Widget widgetToFormValues -> buildWidgetInstance) -- beweist die
 *    Rundreise "Datei -> Editor -> Export" und normiert dabei nur das, was
 *    das Formular ohnehin erzeugen wuerde (fehlende visibility wird explizit,
 *    siehe buildVisibility). Semantisch identisch zum Original, fuer bereits
 *    explizit ausgeschriebene Dokumente (examples/hud/valid/
 *    fps-and-coordinates.json) sogar byte-identisch.
 *  - toJsonText(hud): das HUD-Dokument als exakt der Text, den der Mod als
 *    hud.json liest (2 Leerzeichen Einrueckung, abschliessender Zeilenumbruch).
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory(require("./schema-validate.js"));
  } else {
    root.VitrineHudEditor = factory(root.VitrineSchemaValidator);
  }
})(typeof self !== "undefined" ? self : this, function (DefaultValidator) {

  // --- Schema-Enums lesen (einzige Quelle: hud.schema.json selbst) ---

  function defsOf(schema) {
    return (schema && schema.$defs) || {};
  }

  function anchors(schema) {
    var def = defsOf(schema).anchor;
    return def && Array.isArray(def.enum) ? def.enum.slice() : [];
  }

  function dataSources(schema) {
    var def = defsOf(schema).dataSource;
    return def && Array.isArray(def.enum) ? def.enum.slice() : [];
  }

  function visibilityTypes(schema) {
    var def = defsOf(schema).visibility;
    if (!def || !Array.isArray(def.oneOf)) {
      return [];
    }
    return def.oneOf.map(function (branch) {
      return branch.properties.type.const;
    });
  }

  /** Erlaubte Zustaende der debugScreen-Sichtbarkeitsbedingung (F3 an/aus) -- aus dem visibility-oneOf-Zweig, der 'debugScreen' fuehrt. Kein zweites Enum im Editor. */
  function debugScreenStates(schema) {
    var def = defsOf(schema).visibility;
    if (!def || !Array.isArray(def.oneOf)) {
      return [];
    }
    for (var i = 0; i < def.oneOf.length; i++) {
      var props = def.oneOf[i].properties || {};
      if (props.debugScreen && Array.isArray(props.debugScreen.enum)) {
        return props.debugScreen.enum.slice();
      }
    }
    return [];
  }

  /** Erlaubte 'kind'-Werte eines textContent (static/dynamic) -- aus den 'const' der textContent-oneOf-Zweige. Kein zweites Enum im Editor. */
  function textContentKinds(schema) {
    var def = defsOf(schema).textContent;
    if (!def || !Array.isArray(def.oneOf)) {
      return [];
    }
    return def.oneOf.map(function (branch) {
      return branch.properties.kind.const;
    });
  }

  function widgetTypes(schema) {
    var def = defsOf(schema).widget;
    if (!def || !Array.isArray(def.oneOf)) {
      return [];
    }
    return def.oneOf.map(function (ref) {
      var target = DefaultValidator.resolveRef(ref.$ref, schema);
      return target.properties.type.const;
    });
  }

  function maxWidgets(schema) {
    var prop = schema && schema.properties && schema.properties.widgets;
    return (prop && prop.maxItems) || 64;
  }

  function maxEntries(schema) {
    var def = defsOf(schema).widgetList;
    var prop = def && def.properties && def.properties.entries;
    return (prop && prop.maxItems) || 16;
  }

  // --- project.hud pflegen ---

  /** project.hud (roh, evtl. fehlend/kaputt) -> vollstaendiges, neues Hud-Objekt. Gibt IMMER ein neues Objekt zurueck. */
  function ensureHud(hudField) {
    var isObj = hudField && typeof hudField === "object" && !Array.isArray(hudField);
    var widgets = isObj && Array.isArray(hudField.widgets) ? hudField.widgets : [];
    return { schemaVersion: 1, widgets: widgets.slice() };
  }

  function addWidget(hud, widget) {
    var widgets = ensureHud(hud).widgets;
    widgets.push(widget);
    return { schemaVersion: 1, widgets: widgets };
  }

  function updateWidgetAt(hud, index, widget) {
    var widgets = ensureHud(hud).widgets;
    if (index < 0 || index >= widgets.length) {
      return { schemaVersion: 1, widgets: widgets };
    }
    widgets[index] = widget;
    return { schemaVersion: 1, widgets: widgets };
  }

  function removeWidgetAt(hud, index) {
    var widgets = ensureHud(hud).widgets;
    if (index >= 0 && index < widgets.length) {
      widgets.splice(index, 1);
    }
    return { schemaVersion: 1, widgets: widgets };
  }

  /** Vertauscht Widget `index` mit seinem Nachbarn (delta -1/+1) -- Zeichenreihenfolge, siehe hud.schema.json "widgets"-Beschreibung. Ausserhalb des gueltigen Bereichs: keine Aenderung. */
  function moveWidget(hud, index, delta) {
    var widgets = ensureHud(hud).widgets;
    var target = index + delta;
    if (target < 0 || target >= widgets.length || index < 0 || index >= widgets.length) {
      return { schemaVersion: 1, widgets: widgets };
    }
    var tmp = widgets[index];
    widgets[index] = widgets[target];
    widgets[target] = tmp;
    return { schemaVersion: 1, widgets: widgets };
  }

  // --- Formularwerte <-> Widget-Instanz ---

  function defaultEntryFormValues() {
    return { kind: "dynamic", value: "", source: "fps", label: "", decimals: "" };
  }

  function defaultWidgetFormValues(type) {
    type = type || "text";
    return {
      id: "",
      type: type,
      anchor: "top-left",
      offsetX: "",
      offsetY: "",
      scale: "",
      visibilityType: "always",
      visibilityDimension: "",
      visibilityDebugScreen: "on",
      contentKind: "dynamic",
      contentValue: "",
      contentSource: "fps",
      contentLabel: "",
      contentDecimals: "",
      texture: "",
      width: "",
      height: "",
      source: "fps",
      min: "",
      max: "",
      color: "",
      entries: type === "list" ? [defaultEntryFormValues()] : []
    };
  }

  function isBlank(raw) {
    return raw === undefined || raw === null || String(raw).trim() === "";
  }

  /** "42"/"3.5" -> echte Zahl; alles andere bleibt der (getrimmte) Rohstring, damit der Schema-Validator einen Typ-Fehler meldet statt der Editor ihn stillschweigend verschluckt (wie manifest-form.js#toIntegerOrRaw). */
  function toNumberOrRaw(trimmed) {
    var n = Number(trimmed);
    return trimmed !== "" && isFinite(n) ? n : trimmed;
  }

  function assignOptionalNumber(target, key, raw) {
    if (isBlank(raw)) {
      return;
    }
    target[key] = toNumberOrRaw(String(raw).trim());
  }

  /** Ein 'textContent' (kind: static/dynamic) aus flachen {kind, value, source, label, decimals}-Rohwerten (siehe Kopfkommentar). Fehlendes/leeres 'label' wird ausgelassen -- NICHT getrimmt, ein bewusster nachgestellter Leerraum ("FPS: ") ist gueltiger Inhalt. */
  function buildTextContentFrom(raw) {
    raw = raw || {};
    if (raw.kind === "static") {
      return { kind: "static", value: raw.value === undefined || raw.value === null ? "" : String(raw.value) };
    }
    var content = { kind: "dynamic", source: raw.source || "" };
    if (typeof raw.label === "string" && raw.label.length > 0) {
      content.label = raw.label;
    }
    if (!isBlank(raw.decimals)) {
      content.decimals = toNumberOrRaw(String(raw.decimals).trim());
    }
    return content;
  }

  /** Umkehrung: ein 'textContent'-Objekt -> flache {kind, value, source, label, decimals}-Formularwerte. */
  function textContentToFormValues(content) {
    content = content || {};
    if (content.kind === "static") {
      return { kind: "static", value: content.value || "", source: "fps", label: "", decimals: "" };
    }
    return {
      kind: "dynamic",
      value: "",
      source: content.source || "fps",
      label: content.label || "",
      decimals: content.decimals === undefined || content.decimals === null ? "" : String(content.decimals)
    };
  }

  function buildOffset(formValues) {
    var xBlank = isBlank(formValues.offsetX);
    var yBlank = isBlank(formValues.offsetY);
    if (xBlank && yBlank) {
      return undefined;
    }
    return {
      x: xBlank ? 0 : toNumberOrRaw(String(formValues.offsetX).trim()),
      y: yBlank ? 0 : toNumberOrRaw(String(formValues.offsetY).trim())
    };
  }

  /** Immer ein Objekt (nie ausgelassen) -- der Editor bildet die aktuelle Formularauswahl 1:1 ab, siehe registry/schema/examples/hud/valid/fps-and-coordinates.json (dort traegt sogar 'always' ein explizites Objekt). */
  function buildVisibility(formValues) {
    var type = formValues.visibilityType || "always";
    if (type === "dimension") {
      return { type: "dimension", dimension: (formValues.visibilityDimension || "").trim() };
    }
    if (type === "debugScreen") {
      return { type: "debugScreen", debugScreen: formValues.visibilityDebugScreen || "on" };
    }
    if (type === "outsideMenu") {
      return { type: "outsideMenu" };
    }
    return { type: "always" };
  }

  function visibilityToFormValues(visibility) {
    visibility = visibility || { type: "always" };
    return {
      visibilityType: visibility.type || "always",
      visibilityDimension: visibility.dimension || "",
      visibilityDebugScreen: visibility.debugScreen || "on"
    };
  }

  /** Flache Formularwerte (siehe defaultWidgetFormValues) -> fertiges Widget-Objekt in der Form von hud.schema.json. Leere optionale Felder werden ausgelassen statt mit einem Platzhalter befuellt. */
  function buildWidgetInstance(formValues) {
    formValues = formValues || {};
    var type = formValues.type || "text";
    var widget = {
      id: (formValues.id || "").trim(),
      type: type,
      anchor: formValues.anchor || ""
    };

    var offset = buildOffset(formValues);
    if (offset) {
      widget.offset = offset;
    }

    if (!isBlank(formValues.scale)) {
      widget.scale = toNumberOrRaw(String(formValues.scale).trim());
    }

    widget.visibility = buildVisibility(formValues);

    if (type === "text") {
      widget.content = buildTextContentFrom({
        kind: formValues.contentKind,
        value: formValues.contentValue,
        source: formValues.contentSource,
        label: formValues.contentLabel,
        decimals: formValues.contentDecimals
      });
    } else if (type === "icon") {
      widget.texture = (formValues.texture || "").trim();
      assignOptionalNumber(widget, "width", formValues.width);
      assignOptionalNumber(widget, "height", formValues.height);
    } else if (type === "bar") {
      widget.source = formValues.source || "";
      assignOptionalNumber(widget, "min", formValues.min);
      assignOptionalNumber(widget, "max", formValues.max);
      assignOptionalNumber(widget, "width", formValues.width);
      assignOptionalNumber(widget, "height", formValues.height);
      if (!isBlank(formValues.color)) {
        widget.color = String(formValues.color).trim();
      }
    } else if (type === "list") {
      var entries = Array.isArray(formValues.entries) ? formValues.entries : [];
      widget.entries = entries.map(buildTextContentFrom);
    }

    return widget;
  }

  /** Umkehrung: ein bereits gespeichertes Widget-Objekt -> flache Formularwerte zum Vorbefuellen des Editors. */
  function widgetToFormValues(widget) {
    widget = widget || {};
    var type = widget.type || "text";
    var values = defaultWidgetFormValues(type);
    values.id = widget.id || "";
    values.anchor = widget.anchor || "top-left";

    var offset = widget.offset || {};
    values.offsetX = offset.x === undefined || offset.x === null ? "" : String(offset.x);
    values.offsetY = offset.y === undefined || offset.y === null ? "" : String(offset.y);
    values.scale = widget.scale === undefined || widget.scale === null ? "" : String(widget.scale);

    var vis = visibilityToFormValues(widget.visibility);
    values.visibilityType = vis.visibilityType;
    values.visibilityDimension = vis.visibilityDimension;
    values.visibilityDebugScreen = vis.visibilityDebugScreen;

    if (type === "text") {
      var c = textContentToFormValues(widget.content);
      values.contentKind = c.kind;
      values.contentValue = c.value;
      values.contentSource = c.source;
      values.contentLabel = c.label;
      values.contentDecimals = c.decimals;
    } else if (type === "icon") {
      values.texture = widget.texture || "";
      values.width = widget.width === undefined || widget.width === null ? "" : String(widget.width);
      values.height = widget.height === undefined || widget.height === null ? "" : String(widget.height);
    } else if (type === "bar") {
      values.source = widget.source || "fps";
      values.min = widget.min === undefined || widget.min === null ? "" : String(widget.min);
      values.max = widget.max === undefined || widget.max === null ? "" : String(widget.max);
      values.width = widget.width === undefined || widget.width === null ? "" : String(widget.width);
      values.height = widget.height === undefined || widget.height === null ? "" : String(widget.height);
      values.color = widget.color || "";
    } else if (type === "list") {
      var rawEntries = Array.isArray(widget.entries) && widget.entries.length > 0 ? widget.entries : [{}];
      values.entries = rawEntries.map(textContentToFormValues);
    }

    return values;
  }

  // --- entries[] eines list-Widget-Entwurfs pflegen ---

  function addEntry(entries) {
    var copy = Array.isArray(entries) ? entries.slice() : [];
    copy.push(defaultEntryFormValues());
    return copy;
  }

  function removeEntryAt(entries, index) {
    var copy = Array.isArray(entries) ? entries.slice() : [];
    if (index >= 0 && index < copy.length) {
      copy.splice(index, 1);
    }
    return copy;
  }

  // --- Validierung ---

  /** Ids, die mehr als einmal in `widgets` vorkommen -- reine Editor-/Mod-Laufzeitpruefung, JSON Schema kann Eindeutigkeit ueber Array-Elemente hinweg nicht ausdruecken (siehe hud.schema.json-Kopfkommentar zu "widgetId"). */
  function findDuplicateIds(widgets) {
    var seen = {};
    var duplicates = [];
    (widgets || []).forEach(function (widget) {
      var id = widget && widget.id;
      if (!id) {
        return;
      }
      if (seen[id] && duplicates.indexOf(id) === -1) {
        duplicates.push(id);
      }
      seen[id] = true;
    });
    return duplicates;
  }

  /** Das GANZE project.hud gegen hud.schema.json pruefen (Export/Zusammenfassung). Flache Meldungsliste (wie VitrineSchemaValidator#validate) plus Duplikat-Id-Meldungen. */
  function validateHudDocument(hud, schema, validator) {
    validator = validator || DefaultValidator;
    var doc = ensureHud(hud);
    var messages = validator.validate(schema, doc).slice();
    findDuplicateIds(doc.widgets).forEach(function (id) {
      messages.push("Widget-Id '" + id + "' ist mehrfach vergeben -- jede Id muss innerhalb eines HUD-Pakets eindeutig sein.");
    });
    return messages;
  }

  /**
   * EIN Widget-Entwurf (bevor er der Liste hinzugefuegt/darin ersetzt wird)
   * live pruefen -- baut ein synthetisches Ein-Widget-Dokument, validiert das
   * gegen hud.schema.json und ordnet jede Meldung ihrem Formularfeld zu
   * (gleiches Muster wie manifest-form.js#validateManifestByField).
   * `editingIndex` ist der Index des Widgets in `existingWidgets`, das gerade
   * bearbeitet wird (-1 bei einem neuen Widget) -- fuer die Id-Duplikatpruefung
   * gegen die UEBRIGEN Widgets.
   */
  function validateDraftWidgetByField(draftWidget, existingWidgets, editingIndex, schema, validator) {
    validator = validator || DefaultValidator;
    var entries = validator.validateDetailed(schema, { schemaVersion: 1, widgets: [draftWidget] });
    var byField = {};
    var prefix = "widgets[0].";

    entries.forEach(function (entry) {
      if (entry.field === null || entry.field === undefined) {
        byField.__root__ = (byField.__root__ || []).concat([entry.message]);
        return;
      }
      var field = entry.field.indexOf(prefix) === 0 ? entry.field.slice(prefix.length) : entry.field;
      var base = validator.baseFieldName(field);
      byField[base] = (byField[base] || []).concat([entry.message]);
    });

    var id = draftWidget && draftWidget.id;
    if (id) {
      var duplicate = (existingWidgets || []).some(function (widget, idx) {
        return idx !== editingIndex && widget && widget.id === id;
      });
      if (duplicate) {
        byField.id = (byField.id || []).concat(["Id '" + id + "' ist bereits vergeben -- jede Widget-Id muss innerhalb dieses HUD-Pakets eindeutig sein."]);
      }
    }

    return byField;
  }

  /** Ein vorhandenes HUD-Dokument einmal komplett durch das Editor-Datenmodell schicken (Rundreise Datei -> Editor -> Export). */
  function importHud(rawHud) {
    var base = ensureHud(rawHud);
    return {
      schemaVersion: 1,
      widgets: base.widgets.map(function (widget) {
        return buildWidgetInstance(widgetToFormValues(widget));
      })
    };
  }

  /** Das HUD-Dokument als der Text, den der Mod als hud.json liest. */
  function toJsonText(hud) {
    return JSON.stringify(ensureHud(hud), null, 2) + "\n";
  }

  return {
    anchors: anchors,
    dataSources: dataSources,
    visibilityTypes: visibilityTypes,
    debugScreenStates: debugScreenStates,
    textContentKinds: textContentKinds,
    widgetTypes: widgetTypes,
    maxWidgets: maxWidgets,
    maxEntries: maxEntries,
    ensureHud: ensureHud,
    addWidget: addWidget,
    updateWidgetAt: updateWidgetAt,
    removeWidgetAt: removeWidgetAt,
    moveWidget: moveWidget,
    defaultWidgetFormValues: defaultWidgetFormValues,
    defaultEntryFormValues: defaultEntryFormValues,
    buildWidgetInstance: buildWidgetInstance,
    widgetToFormValues: widgetToFormValues,
    addEntry: addEntry,
    removeEntryAt: removeEntryAt,
    findDuplicateIds: findDuplicateIds,
    validateHudDocument: validateHudDocument,
    validateDraftWidgetByField: validateDraftWidgetByField,
    importHud: importHud,
    toJsonText: toJsonText
  };
});
