"use strict";

/*
 * Vitrine Designer -- D1 Grundgeruest.
 *
 * Klassisches Script (kein type="module"): ueber file:// blockieren manche
 * Browser das Nachladen von ES-Modulen per CORS-Fehler ("Cross origin
 * requests are only supported for protocol schemes: http, https, ..."),
 * ein normales <script src="app.js"> hat dieses Problem nicht.
 *
 * Kein fetch() -- ueber file:// scheitert das ebenfalls an CORS/Same-Origin.
 * Alles, was diese Seite braucht, ist bereits im Dokument oder im
 * localStorage des Browsers.
 *
 * Projekt-Schema (localStorage, Schluessel STORAGE_KEY):
 *   {
 *     "schemaVersion": 1,
 *     "projects": [ { "id": "...", "name": "...", "createdAt": "ISO-8601" } ],
 *     "currentProjectId": "..." | null
 *   }
 * Bewusst schmal gehalten fuer D1 -- Manifest-Felder etc. kommen mit D2+ und
 * werden dann pro Projekt ergaenzt (eigener Registry-Eintrag noetig, falls
 * das Format sich mit dem vitrine.json-Schema ueberschneidet).
 *
 * D2 ergaenzt pro Projekt ein Feld "manifest": ein Objekt in exakt der Form,
 * die vitrine.schema.json vorschreibt (schemaVersion, id, name, version,
 * category, author, description?, capabilities?, icon?, screenshots?,
 * homepage?, license?) -- kein zweites Format, dieselbe Datei wie der Mod
 * (registry/schema/vitrine.schema.json, ueber schema-data.js eingebettet,
 * siehe generate-designer-schema.mjs). Live-Validierung ueber
 * schema-validate.js (VitrineSchemaValidator) + manifest-form.js
 * (VitrineManifestForm, Bindeglied Formularfeld <-> Schema-Instanz).
 *
 * D3 ergaenzt pro Projekt ein Feld "theme": { schemaVersion, light, dark },
 * siehe theme-tokens.js-Kopfkommentar dafuer, warum es (noch) KEIN
 * registry/schema/theme.schema.json dazu gibt (T1 kommt spaeter). Das
 * Formular selbst wird nicht von Hand ins HTML geschrieben (23 Tokens x 2
 * Varianten waeren viel Wiederholung), sondern hier aus
 * VitrineThemeTokens.TOKENS erzeugt -- theme-wiring.js ist dabei die einzige
 * Quelle fuer die id-Konvention der erzeugten Felder, damit Erzeugen und
 * spaeteres Auslesen nicht auseinanderlaufen koennen.
 *
 * D4 ergaenzt pro Projekt ein Feld "textures": ein Array aus
 * { targetPath, fileName, dataUrl, width, height }, siehe
 * texture-studio.js-Kopfkommentar. Datei-Zugriff laeuft ausschliesslich ueber
 * FileReader.readAsArrayBuffer() (kein <input type="file">-Upload an einen
 * Server, kein fetch()) -- die rohen Bytes werden hier nur an die reinen
 * Pruef-/Kodierfunktionen aus texture-studio.js weitergereicht. Es gibt dafuer
 * bewusst KEIN Feld in registry/schema/vitrine.schema.json (das Manifest
 * beschreibt nur icon/screenshots) -- das eigentliche .vpkg-Packen inklusive
 * Ablage der Textur-Dateien im Archiv ist D6, hier landet die Zuordnung nur
 * im Designer-Projektzustand.
 */

(function () {
  var STORAGE_KEY = "vitrine-designer.v1";
  var storageAvailable = true;

  function loadState() {
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return { schemaVersion: 1, projects: [], currentProjectId: null };
      }
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.projects)) {
        return { schemaVersion: 1, projects: [], currentProjectId: null };
      }
      return {
        schemaVersion: 1,
        projects: parsed.projects,
        currentProjectId: parsed.currentProjectId || null
      };
    } catch (err) {
      storageAvailable = false;
      return { schemaVersion: 1, projects: [], currentProjectId: null };
    }
  }

  function saveState(state) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      storageAvailable = true;
    } catch (err) {
      storageAvailable = false;
    }
  }

  function createId() {
    return "proj-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 8);
  }

  function formatDate(iso) {
    try {
      var d = new Date(iso);
      if (isNaN(d.getTime())) {
        return iso;
      }
      return d.toLocaleDateString("de-DE", { year: "numeric", month: "2-digit", day: "2-digit" });
    } catch (err) {
      return iso;
    }
  }

  var state = loadState();

  // --- DOM-Referenzen ---
  var elProjectEmpty = document.getElementById("project-empty");
  var elProjectOpen = document.getElementById("project-open");
  var elProjectOpenName = document.getElementById("project-open-name");
  var elProjectOpenMeta = document.getElementById("project-open-meta");

  var btnNewProject = document.getElementById("btn-new-project");
  var btnLoadProject = document.getElementById("btn-load-project");
  var btnCloseProject = document.getElementById("btn-close-project");

  var formNewProject = document.getElementById("new-project-form");
  var inputProjectName = document.getElementById("project-name-input");
  var btnCancelNew = document.getElementById("btn-cancel-new");
  var elNewProjectError = document.getElementById("new-project-error");

  var wrapProjectList = document.getElementById("project-list-wrap");
  var elProjectList = document.getElementById("project-list");
  var elProjectListEmpty = document.getElementById("project-list-empty");
  var btnCancelLoad = document.getElementById("btn-cancel-load");

  var elStorageWarning = document.getElementById("storage-warning");

  // --- D2: Manifest-Formular ---
  var schemaAvailable = typeof VITRINE_MANIFEST_SCHEMA !== "undefined" &&
    typeof VitrineSchemaValidator !== "undefined" &&
    typeof VitrineManifestForm !== "undefined";

  // GATE6: gepunktete Pfade statt nur der obersten Ebene -- 'javaMod' ist das
  // erste verschachtelte Objekt im Manifest ("javaMod.jar.sha256"). Die Liste
  // kommt aus dem Schema, nicht aus einer Aufzaehlung hier.
  var manifestFieldKeys = schemaAvailable ? VitrineManifestForm.manifestFieldPaths(VITRINE_MANIFEST_SCHEMA) : [];

  var navManifest = document.getElementById("nav-manifest");
  var elEditorPlaceholder = document.getElementById("editor-placeholder");
  var elManifestPanel = document.getElementById("manifest-panel");
  var elManifestSummary = document.getElementById("manifest-summary");

  var currentSection = "placeholder";

  function getManifestFieldEl(key, kind) {
    return document.getElementById("manifest-" + kind + "-" + key);
  }

  function showSection(section) {
    currentSection = section;
    if (elEditorPlaceholder) {
      elEditorPlaceholder.hidden = section !== "placeholder";
    }
    if (elManifestPanel) {
      elManifestPanel.hidden = section !== "manifest";
    }
    if (elThemePanel) {
      elThemePanel.hidden = section !== "theme";
    }
    if (elTexturePanel) {
      elTexturePanel.hidden = section !== "texture";
    }
    if (elHudPanel) {
      elHudPanel.hidden = section !== "hud";
    }
    if (elExportPanel) {
      elExportPanel.hidden = section !== "export";
    }
    if (navManifest) {
      navManifest.setAttribute("aria-current", section === "manifest" ? "true" : "false");
    }
    if (navTheme) {
      navTheme.setAttribute("aria-current", section === "theme" ? "true" : "false");
    }
    if (navTexture) {
      navTexture.setAttribute("aria-current", section === "texture" ? "true" : "false");
    }
    if (navHud) {
      navHud.setAttribute("aria-current", section === "hud" ? "true" : "false");
    }
    if (navExport) {
      navExport.setAttribute("aria-current", section === "export" ? "true" : "false");
    }
  }

  function updateManifestNavAvailability() {
    if (!navManifest) {
      return;
    }
    var hasProject = !!getCurrentProject();
    navManifest.disabled = !hasProject || !schemaAvailable;
    if ((!hasProject || !schemaAvailable) && currentSection === "manifest") {
      showSection("placeholder");
    }
  }

  function populateManifestForm(manifestObj) {
    if (!schemaAvailable) {
      return;
    }
    var values = VitrineManifestForm.formValuesFromInstance(manifestObj || {}, VITRINE_MANIFEST_SCHEMA);
    manifestFieldKeys.forEach(function (key) {
      var input = getManifestFieldEl(key, "field");
      if (input) {
        input.value = values[key] || "";
      }
    });
    syncClassBSection({ adjustHotApply: false, clearClassBFields: false });
  }

  function collectManifestFormValues() {
    var values = {};
    manifestFieldKeys.forEach(function (key) {
      var input = getManifestFieldEl(key, "field");
      values[key] = input ? input.value : "";
    });
    return values;
  }

  function clearManifestDisplay() {
    manifestFieldKeys.forEach(function (key) {
      var errorEl = getManifestFieldEl(key, "error");
      if (errorEl) {
        errorEl.textContent = "";
      }
      var wrapper = document.querySelector('.field[data-field="' + key + '"]');
      if (wrapper) {
        wrapper.classList.remove("has-error");
      }
    });
    if (elManifestSummary) {
      elManifestSummary.textContent = "";
      elManifestSummary.classList.remove("has-errors", "is-valid");
    }
  }

  function renderManifestValidation(byField) {
    var totalMessages = 0;
    manifestFieldKeys.forEach(function (key) {
      var errorEl = getManifestFieldEl(key, "error");
      var wrapper = document.querySelector('.field[data-field="' + key + '"]');
      var messages = byField[key] || [];
      totalMessages += messages.length;
      if (errorEl) {
        errorEl.textContent = "";
        messages.forEach(function (message) {
          var p = document.createElement("p");
          p.textContent = message;
          errorEl.appendChild(p);
        });
      }
      if (wrapper) {
        wrapper.classList.toggle("has-error", messages.length > 0);
      }
    });

    if (!elManifestSummary) {
      return;
    }
    var rootMessages = byField.__root__ || [];
    elManifestSummary.classList.remove("has-errors", "is-valid");
    if (rootMessages.length > 0) {
      elManifestSummary.textContent = rootMessages.join(" ");
      elManifestSummary.classList.add("has-errors");
    } else if (totalMessages > 0) {
      elManifestSummary.textContent = totalMessages === 1 ? "1 Problem gefunden." : totalMessages + " Probleme gefunden.";
      elManifestSummary.classList.add("has-errors");
    } else {
      elManifestSummary.textContent = "Manifest ist gueltig.";
      elManifestSummary.classList.add("is-valid");
    }
  }

  /** Liest die Formularfelder, baut die Manifest-Instanz, validiert live und speichert im Projekt. */
  function runManifestValidation() {
    if (!schemaAvailable) {
      return;
    }
    var current = getCurrentProject();
    if (!current) {
      return;
    }
    var formValues = collectManifestFormValues();
    var instance = VitrineManifestForm.buildManifestInstance(formValues, VITRINE_MANIFEST_SCHEMA);
    current.manifest = instance;
    saveState(state);
    var byField = VitrineManifestForm.validateManifestByField(instance, VITRINE_MANIFEST_SCHEMA, VitrineSchemaValidator);
    renderManifestValidation(byField);
  }

  /** Nach Oeffnen/Anlegen/Schliessen eines Projekts: Formular neu befuellen, Nav-Verfuegbarkeit setzen. */
  function onProjectChanged() {
    updateManifestNavAvailability();
    updateThemeNavAvailability();
    updateTextureNavAvailability();
    updateHudNavAvailability();
    updateExportNavAvailability();
    resetExportDisplay();
    var current = getCurrentProject();
    if (current) {
      if (!current.manifest || typeof current.manifest !== "object" || Array.isArray(current.manifest)) {
        current.manifest = {};
      }
      populateManifestForm(current.manifest);
      runManifestValidation();

      if (themeAvailable) {
        current.theme = VitrineThemeWiring.ensureTheme(current.theme);
        saveState(state);
        populateThemeForm(current.theme);
        clearThemeFieldErrors();
        setActiveThemeVariant(activeThemeVariant);
      }

      if (textureAvailable) {
        current.textures = VitrineTextureStudio.ensureTextures(current.textures);
        saveState(state);
        resetTexturePendingSelection();
        selectedTextureTargetPath = null;
        renderTextureList();
        renderTexturePreviewSelection();
      }

      if (hudAvailable) {
        current.hud = VitrineHudEditor.ensureHud(current.hud);
        saveState(state);
        closeHudForm();
        renderHudList();
        renderHudExport();
      }
    } else {
      populateManifestForm({});
      clearManifestDisplay();
      if (hudAvailable) {
        closeHudForm();
        renderHudList();
        renderHudExport();
      }
      if (themeAvailable) {
        clearThemeFieldErrors();
        if (elThemeContrastList) {
          elThemeContrastList.textContent = "";
        }
      }
      if (textureAvailable) {
        resetTexturePendingSelection();
        selectedTextureTargetPath = null;
        renderTextureList();
        renderTexturePreviewSelection();
      }
    }
  }

  if (schemaAvailable) {
    var categoryProp = VITRINE_MANIFEST_SCHEMA.properties.category;
    var elCategoryOptions = document.getElementById("manifest-category-options");
    if (elCategoryOptions && categoryProp && categoryProp.enum) {
      categoryProp.enum.forEach(function (value) {
        var option = document.createElement("option");
        option.value = value;
        elCategoryOptions.appendChild(option);
      });
    }
    (VITRINE_MANIFEST_SCHEMA.required || []).forEach(function (key) {
      var label = document.getElementById("manifest-label-" + key);
      if (label) {
        label.classList.add("is-required");
      }
    });
    manifestFieldKeys.forEach(function (key) {
      var input = getManifestFieldEl(key, "field");
      if (input) {
        input.addEventListener("input", runManifestValidation);
        // <select> feuert kein "input" in jedem Browser zuverlaessig fuer
        // Tastaturbedienung -- "change" zusaetzlich, doppelt schadet nicht
        // (runManifestValidation ist idempotent).
        if (input.tagName === "SELECT") {
          input.addEventListener("change", runManifestValidation);
        }
      }
    });
  }

  // --- GATE6: Klasse B (Java-Mod, .jar, Neustart noetig) ---
  //
  // Der Abschnitt 'javaMod' im Formular wird nur gezeigt, wenn contentClass
  // auf 'javamod' steht -- und dann zusammen mit einem auffaelligen Hinweis,
  // der genau das sagt, was der Nutzerin spaeter auch im Spiel begegnet:
  // "Neustart noetig". Umgekehrt wird beim Zurueckschalten auf Klasse A der
  // ganze javaMod-Block geleert, sonst bliebe ein verbotenes Objekt im
  // Manifest stehen (Schema: ein Klasse-A-Paket darf gar kein 'javaMod'
  // tragen).
  var elClassBSection = document.getElementById("manifest-classb-section");
  var elClassBNotice = document.getElementById("manifest-classb-notice");
  var elHotApplyField = document.getElementById("manifest-field-hotApply");

  function isClassBSelected() {
    var select = getManifestFieldEl("contentClass", "field");
    return !!select && select.value === "javamod";
  }

  /** Blendet den Klasse-B-Abschnitt ein/aus und setzt hotApply auf den einzig zulaessigen Wert. */
  function syncClassBSection(options) {
    var classB = isClassBSelected();
    if (elClassBSection) {
      elClassBSection.hidden = !classB;
    }
    if (elClassBNotice) {
      elClassBNotice.hidden = !classB;
    }
    if (elHotApplyField && options && options.adjustHotApply) {
      elHotApplyField.value = classB ? "false" : "";
    }
    if (!classB && options && options.clearClassBFields) {
      manifestFieldKeys.forEach(function (key) {
        if (key.indexOf("javaMod.") === 0) {
          var input = getManifestFieldEl(key, "field");
          if (input) {
            input.value = "";
          }
        }
      });
    }
  }

  if (schemaAvailable) {
    var elContentClass = getManifestFieldEl("contentClass", "field");
    if (elContentClass) {
      elContentClass.addEventListener("change", function () {
        syncClassBSection({ adjustHotApply: true, clearClassBFields: true });
        runManifestValidation();
      });
    }
  }

  if (navManifest) {
    navManifest.addEventListener("click", function () {
      if (navManifest.disabled) {
        return;
      }
      showSection("manifest");
    });
  }

  // --- D3: Theme-Editor ---
  var themeAvailable = typeof VitrineThemeTokens !== "undefined" &&
    typeof VitrineThemeEditor !== "undefined" &&
    typeof VitrineThemeWiring !== "undefined";

  var navTheme = document.getElementById("nav-theme");
  var elThemePanel = document.getElementById("theme-panel");
  var elThemeFieldsLight = document.getElementById("theme-fields-light");
  var elThemeFieldsDark = document.getElementById("theme-fields-dark");
  var themeTabLight = document.getElementById("theme-tab-light");
  var themeTabDark = document.getElementById("theme-tab-dark");
  var elThemePreview = document.getElementById("theme-preview");
  var elThemeContrastList = document.getElementById("theme-contrast-list");

  var activeThemeVariant = "light";

  function buildThemeFieldElement(token, variant) {
    var wrapper = document.createElement("div");
    wrapper.className = "field theme-field";
    wrapper.setAttribute("data-field", token.id);
    wrapper.setAttribute("data-variant", variant);

    var label = document.createElement("label");
    var mainInputId = VitrineThemeWiring.fieldId(variant, token, token.type === "color-alpha" ? "hex" : undefined);
    label.setAttribute("for", mainInputId);
    label.textContent = token.label + (token.unit ? " (" + token.unit + ")" : "");
    wrapper.appendChild(label);

    if (token.type === "number") {
      var numberInput = document.createElement("input");
      numberInput.type = "number";
      numberInput.id = mainInputId;
      numberInput.name = mainInputId;
      numberInput.min = String(token.min);
      numberInput.max = String(token.max);
      numberInput.step = String(token.step);
      numberInput.addEventListener("input", function () {
        runThemeValidation(variant);
      });
      wrapper.appendChild(numberInput);
    } else if (token.type === "color") {
      var colorInput = document.createElement("input");
      colorInput.type = "color";
      colorInput.id = mainInputId;
      colorInput.name = mainInputId;
      colorInput.addEventListener("input", function () {
        runThemeValidation(variant);
      });
      wrapper.appendChild(colorInput);
    } else if (token.type === "color-alpha") {
      var hexInput = document.createElement("input");
      hexInput.type = "color";
      hexInput.id = mainInputId;
      hexInput.name = mainInputId;
      hexInput.addEventListener("input", function () {
        runThemeValidation(variant);
      });
      wrapper.appendChild(hexInput);

      var alphaId = VitrineThemeWiring.fieldId(variant, token, "alpha");
      var alphaLabel = document.createElement("label");
      alphaLabel.setAttribute("for", alphaId);
      alphaLabel.className = "theme-field-sublabel";
      alphaLabel.textContent = "Deckkraft";
      wrapper.appendChild(alphaLabel);

      var alphaInput = document.createElement("input");
      alphaInput.type = "range";
      alphaInput.id = alphaId;
      alphaInput.name = alphaId;
      alphaInput.min = "0";
      alphaInput.max = "1";
      alphaInput.step = "0.01";
      alphaInput.addEventListener("input", function () {
        runThemeValidation(variant);
      });
      wrapper.appendChild(alphaInput);
    }

    var errorEl = document.createElement("div");
    errorEl.id = VitrineThemeWiring.errorId(variant, token.id);
    errorEl.className = "field-errors";
    errorEl.setAttribute("role", "alert");
    errorEl.setAttribute("aria-live", "polite");
    wrapper.appendChild(errorEl);

    return wrapper;
  }

  function buildThemeForm() {
    if (!themeAvailable) {
      return;
    }
    ["light", "dark"].forEach(function (variant) {
      var container = variant === "light" ? elThemeFieldsLight : elThemeFieldsDark;
      if (!container) {
        return;
      }
      container.textContent = "";
      VitrineThemeWiring.groupedTokens().forEach(function (group) {
        var fieldset = document.createElement("fieldset");
        fieldset.className = "theme-group";
        var legend = document.createElement("legend");
        legend.textContent = group.label;
        fieldset.appendChild(legend);
        group.tokens.forEach(function (token) {
          fieldset.appendChild(buildThemeFieldElement(token, variant));
        });
        container.appendChild(fieldset);
      });
    });
  }

  function populateThemeForm(themeObj) {
    if (!themeAvailable) {
      return;
    }
    ["light", "dark"].forEach(function (variant) {
      var values = VitrineThemeEditor.variantToFormValues(themeObj[variant]);
      VitrineThemeTokens.TOKENS.forEach(function (t) {
        if (t.type === "color-alpha") {
          var names = VitrineThemeEditor.alphaFieldNames(t.id);
          var hexEl = document.getElementById(VitrineThemeWiring.fieldId(variant, t, "hex"));
          var alphaEl = document.getElementById(VitrineThemeWiring.fieldId(variant, t, "alpha"));
          if (hexEl) {
            hexEl.value = values[names.hex] || "#000000";
          }
          if (alphaEl) {
            alphaEl.value = values[names.alpha] || "1";
          }
        } else {
          var el = document.getElementById(VitrineThemeWiring.fieldId(variant, t));
          if (el) {
            el.value = values[t.id] || "";
          }
        }
      });
    });
  }

  function renderThemeFieldErrors(variant, byField) {
    VitrineThemeTokens.TOKENS.forEach(function (t) {
      var errorEl = document.getElementById(VitrineThemeWiring.errorId(variant, t.id));
      var wrapper = document.querySelector('.theme-field[data-field="' + t.id + '"][data-variant="' + variant + '"]');
      var messages = byField[t.id] || [];
      if (errorEl) {
        errorEl.textContent = "";
        messages.forEach(function (message) {
          var p = document.createElement("p");
          p.textContent = message;
          errorEl.appendChild(p);
        });
      }
      if (wrapper) {
        wrapper.classList.toggle("has-error", messages.length > 0);
      }
    });
  }

  function clearThemeFieldErrors() {
    renderThemeFieldErrors("light", {});
    renderThemeFieldErrors("dark", {});
  }

  function applyThemePreview(filledVariant) {
    if (!elThemePreview || !themeAvailable) {
      return;
    }
    var vars = VitrineThemeEditor.cssVarsFromVariant(filledVariant);
    Object.keys(vars).forEach(function (cssVar) {
      elThemePreview.style.setProperty(cssVar, vars[cssVar]);
    });
  }

  function renderContrastWarnings(filledVariant) {
    if (!elThemeContrastList || !themeAvailable) {
      return;
    }
    elThemeContrastList.textContent = "";
    var warnings = VitrineThemeEditor.computeContrastWarnings(filledVariant);
    warnings.forEach(function (w) {
      var li = document.createElement("li");
      li.className = "theme-contrast-item " + (w.passes ? "is-pass" : "is-fail");
      var icon = document.createElement("span");
      icon.className = "theme-contrast-icon";
      icon.setAttribute("aria-hidden", "true");
      icon.textContent = w.passes ? "✓" : "✗";
      var text = document.createElement("span");
      text.className = "theme-contrast-text";
      text.textContent = w.label + ": " + w.message;
      li.appendChild(icon);
      li.appendChild(text);
      elThemeContrastList.appendChild(li);
    });
  }

  /** Liest die Formularfelder EINER Variante, baut die Variant-Instanz, validiert live und speichert im Projekt. Aktualisiert Vorschau/Kontrast nur, wenn diese Variante gerade aktiv (sichtbar) ist. */
  function runThemeValidation(variant) {
    if (!themeAvailable) {
      return;
    }
    var current = getCurrentProject();
    if (!current || !current.theme) {
      return;
    }
    var getValue = function (id) {
      var el = document.getElementById(id);
      return el ? el.value : "";
    };
    var formValues = VitrineThemeWiring.collectFormValues(variant, getValue);
    var instance = VitrineThemeEditor.buildVariantInstance(formValues);
    var filled = VitrineThemeEditor.fillVariantDefaults(instance, variant);
    current.theme[variant] = filled;
    saveState(state);

    renderThemeFieldErrors(variant, VitrineThemeEditor.validateVariantByField(filled));

    if (variant === activeThemeVariant) {
      applyThemePreview(filled);
      renderContrastWarnings(filled);
    }
  }

  function setActiveThemeVariant(variant) {
    activeThemeVariant = variant;
    if (elThemeFieldsLight) {
      elThemeFieldsLight.hidden = variant !== "light";
    }
    if (elThemeFieldsDark) {
      elThemeFieldsDark.hidden = variant !== "dark";
    }
    if (themeTabLight) {
      themeTabLight.setAttribute("aria-selected", variant === "light" ? "true" : "false");
    }
    if (themeTabDark) {
      themeTabDark.setAttribute("aria-selected", variant === "dark" ? "true" : "false");
    }
    var current = getCurrentProject();
    if (current && current.theme) {
      applyThemePreview(current.theme[variant]);
      renderContrastWarnings(current.theme[variant]);
    }
  }

  function updateThemeNavAvailability() {
    if (!navTheme) {
      return;
    }
    var hasProject = !!getCurrentProject();
    navTheme.disabled = !hasProject || !themeAvailable;
    if ((!hasProject || !themeAvailable) && currentSection === "theme") {
      showSection("placeholder");
    }
  }

  if (themeAvailable) {
    buildThemeForm();
  }

  if (themeTabLight) {
    themeTabLight.addEventListener("click", function () {
      setActiveThemeVariant("light");
    });
  }

  if (themeTabDark) {
    themeTabDark.addEventListener("click", function () {
      setActiveThemeVariant("dark");
    });
  }

  if (navTheme) {
    navTheme.addEventListener("click", function () {
      if (navTheme.disabled) {
        return;
      }
      showSection("theme");
    });
  }

  // --- D4: Texture-Studio ---
  var textureAvailable = typeof VitrineTextureStudio !== "undefined";

  var navTexture = document.getElementById("nav-texture");
  var elTexturePanel = document.getElementById("texture-panel");
  var elTextureForm = document.getElementById("texture-form");
  var elTextureFileInput = document.getElementById("texture-file-input");
  var elTexturePendingInfo = document.getElementById("texture-pending-info");
  var elTexturePendingMeta = document.getElementById("texture-pending-meta");
  var elTexturePendingWarning = document.getElementById("texture-pending-warning");
  var elTextureTargetInput = document.getElementById("texture-target-input");
  var elTextureFormError = document.getElementById("texture-form-error");
  var elTextureList = document.getElementById("texture-list");
  var elTextureListEmpty = document.getElementById("texture-list-empty");
  var elTexturePreviewEmpty = document.getElementById("texture-preview-empty");
  var elTexturePreviewContent = document.getElementById("texture-preview-content");
  var elTexturePreviewPath = document.getElementById("texture-preview-path");
  var elTexturePreviewDims = document.getElementById("texture-preview-dims");
  var elTexturePreviewAfterImg = document.getElementById("texture-preview-after-img");

  // Bytes der zuletzt geladenen, noch nicht zugeordneten Datei (Uint8Array)
  // und ihr Anzeigename -- erst beim Absenden des Formulars wird daraus ein
  // project.textures-Eintrag. null, solange keine gueltige Datei geladen ist.
  var pendingTextureBytes = null;
  var pendingTextureFileName = "";
  var selectedTextureTargetPath = null;

  function updateTextureNavAvailability() {
    if (!navTexture) {
      return;
    }
    var hasProject = !!getCurrentProject();
    navTexture.disabled = !hasProject || !textureAvailable;
    if ((!hasProject || !textureAvailable) && currentSection === "texture") {
      showSection("placeholder");
    }
  }

  function showTextureFormError(message) {
    if (!elTextureFormError) {
      return;
    }
    elTextureFormError.textContent = message;
    elTextureFormError.hidden = !message;
  }

  function resetTexturePendingSelection() {
    pendingTextureBytes = null;
    pendingTextureFileName = "";
    if (elTextureFileInput) {
      elTextureFileInput.value = "";
    }
    if (elTextureTargetInput) {
      elTextureTargetInput.value = "";
    }
    showTextureFormError("");
    if (elTexturePendingInfo) {
      elTexturePendingInfo.hidden = true;
    }
    if (elTexturePendingWarning) {
      elTexturePendingWarning.hidden = true;
      elTexturePendingWarning.textContent = "";
    }
  }

  function renderTexturePending(header, warning) {
    if (!elTexturePendingInfo || !elTexturePendingMeta) {
      return;
    }
    elTexturePendingInfo.hidden = false;
    elTexturePendingMeta.textContent = pendingTextureFileName + ": " + header.width + " x " + header.height + " px geladen.";
    if (elTexturePendingWarning) {
      elTexturePendingWarning.hidden = !warning;
      elTexturePendingWarning.textContent = warning || "";
    }
  }

  /** FileReader-Ergebnis (ArrayBuffer) als callback(err, Uint8Array) -- reines DOM, kein fetch(), siehe app.js-Kopfkommentar. */
  function readFileAsBytes(file, callback) {
    var reader = new FileReader();
    reader.onload = function () {
      callback(null, new Uint8Array(reader.result));
    };
    reader.onerror = function () {
      callback(reader.error || new Error("Datei konnte nicht gelesen werden."));
    };
    reader.readAsArrayBuffer(file);
  }

  function handleTextureFileChosen() {
    resetTexturePendingSelection();
    var file = elTextureFileInput.files && elTextureFileInput.files[0];
    if (!file) {
      return;
    }
    readFileAsBytes(file, function (err, bytes) {
      if (err) {
        showTextureFormError("Datei konnte nicht gelesen werden.");
        return;
      }
      var header = VitrineTextureStudio.readPngHeader(bytes);
      if (!header.valid) {
        showTextureFormError(header.error);
        return;
      }
      var dimCheck = VitrineTextureStudio.validateDimensions(header.width, header.height);
      if (!dimCheck.valid) {
        showTextureFormError(dimCheck.message);
        return;
      }
      pendingTextureBytes = bytes;
      pendingTextureFileName = file.name;
      renderTexturePending(header, VitrineTextureStudio.powerOfTwoWarning(header.width, header.height));
    });
  }

  function renderTextureList() {
    if (!elTextureList) {
      return;
    }
    elTextureList.textContent = "";
    var current = getCurrentProject();
    var textures = current ? VitrineTextureStudio.ensureTextures(current.textures) : [];

    if (elTextureListEmpty) {
      elTextureListEmpty.hidden = textures.length > 0;
    }

    textures.forEach(function (texture) {
      var li = document.createElement("li");

      var openBtn = document.createElement("button");
      openBtn.type = "button";
      openBtn.className = "texture-list-item" + (texture.targetPath === selectedTextureTargetPath ? " is-selected" : "");

      var info = document.createElement("span");
      var pathEl = document.createElement("span");
      pathEl.className = "texture-list-item-path";
      pathEl.textContent = texture.targetPath;
      var metaEl = document.createElement("span");
      metaEl.className = "texture-list-item-meta";
      metaEl.textContent = texture.width + " x " + texture.height + " px -- " + texture.fileName;
      info.appendChild(pathEl);
      info.appendChild(document.createElement("br"));
      info.appendChild(metaEl);
      openBtn.appendChild(info);

      openBtn.addEventListener("click", function () {
        selectedTextureTargetPath = texture.targetPath;
        renderTextureList();
        renderTexturePreviewSelection();
      });

      var actions = document.createElement("div");
      actions.className = "texture-list-item-actions";
      var removeBtn = document.createElement("button");
      removeBtn.type = "button";
      removeBtn.className = "btn";
      removeBtn.textContent = "Entfernen";
      removeBtn.addEventListener("click", function (event) {
        event.stopPropagation();
        var proj = getCurrentProject();
        if (!proj) {
          return;
        }
        proj.textures = VitrineTextureStudio.removeTexture(proj.textures, texture.targetPath);
        saveState(state);
        if (selectedTextureTargetPath === texture.targetPath) {
          selectedTextureTargetPath = null;
        }
        renderTextureList();
        renderTexturePreviewSelection();
      });
      actions.appendChild(removeBtn);

      li.appendChild(openBtn);
      li.appendChild(actions);
      li.style.display = "flex";
      li.style.alignItems = "center";
      li.style.gap = "var(--space-unit)";
      elTextureList.appendChild(li);
    });
  }

  function renderTexturePreviewSelection() {
    if (!elTexturePreviewEmpty || !elTexturePreviewContent) {
      return;
    }
    var current = getCurrentProject();
    var textures = current ? VitrineTextureStudio.ensureTextures(current.textures) : [];
    var entry = null;
    for (var i = 0; i < textures.length; i++) {
      if (textures[i].targetPath === selectedTextureTargetPath) {
        entry = textures[i];
        break;
      }
    }
    if (!entry) {
      elTexturePreviewEmpty.hidden = false;
      elTexturePreviewContent.hidden = true;
      return;
    }
    elTexturePreviewEmpty.hidden = true;
    elTexturePreviewContent.hidden = false;
    if (elTexturePreviewPath) {
      elTexturePreviewPath.textContent = entry.targetPath;
    }
    if (elTexturePreviewDims) {
      elTexturePreviewDims.textContent = entry.width + " x " + entry.height + " px";
    }
    if (elTexturePreviewAfterImg) {
      elTexturePreviewAfterImg.src = entry.dataUrl;
      elTexturePreviewAfterImg.alt = "Custom-Textur fuer " + entry.targetPath;
    }
  }

  if (textureAvailable) {
    if (elTextureFileInput) {
      elTextureFileInput.addEventListener("change", handleTextureFileChosen);
    }

    if (elTextureForm) {
      elTextureForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var current = getCurrentProject();
        if (!current) {
          return;
        }
        if (!pendingTextureBytes) {
          showTextureFormError("Bitte zuerst eine gueltige PNG-Datei laden.");
          return;
        }
        var result = VitrineTextureStudio.buildTextureEntry({
          targetPath: elTextureTargetInput ? elTextureTargetInput.value : "",
          fileName: pendingTextureFileName,
          bytes: pendingTextureBytes
        });
        if (!result.ok) {
          showTextureFormError(result.errors.join(" "));
          return;
        }
        current.textures = VitrineTextureStudio.upsertTexture(current.textures, result.entry);
        saveState(state);
        selectedTextureTargetPath = result.entry.targetPath;
        resetTexturePendingSelection();
        renderTextureList();
        renderTexturePreviewSelection();
      });
    }
  }

  if (navTexture) {
    navTexture.addEventListener("click", function () {
      if (navTexture.disabled) {
        return;
      }
      showSection("texture");
    });
  }

  // --- D5: HUD-Editor ---
  // Widgets werden hier in project.hud gepflegt (schemaVersion 1 + widgets[]),
  // exakt in der Form von registry/schema/hud.schema.json -- kein zweites
  // Format (Charter-Regel 8). Live-Validierung eines Widget-Entwurfs und des
  // ganzen Dokuments laeuft ueber VitrineHudEditor + VitrineSchemaValidator,
  // also denselben Auswerter wie Manifest/CLI. Datenquelle ("source") und
  // Sichtbarkeitstyp sind Auswahllisten aus dem Schema-Enum, keine
  // Freitexteingabe.
  var hudAvailable = typeof VITRINE_HUD_SCHEMA !== "undefined" &&
    typeof VitrineSchemaValidator !== "undefined" &&
    typeof VitrineHudEditor !== "undefined";

  var navHud = document.getElementById("nav-hud");
  var elHudPanel = document.getElementById("hud-panel");
  var elHudSummary = document.getElementById("hud-summary");
  var elHudAddType = document.getElementById("hud-add-type");
  var btnHudAdd = document.getElementById("btn-hud-add");
  var elHudWidgetList = document.getElementById("hud-widget-list");
  var elHudWidgetListEmpty = document.getElementById("hud-widget-list-empty");
  var elHudForm = document.getElementById("hud-form");
  var elHudFormTitle = document.getElementById("hud-form-title");
  var elHudFormFields = document.getElementById("hud-form-fields");
  var elHudEntriesGroup = document.getElementById("hud-entries-group");
  var elHudEntries = document.getElementById("hud-entries");
  var btnHudEntryAdd = document.getElementById("btn-hud-entry-add");
  var elHudFormError = document.getElementById("hud-form-error");
  var btnHudSave = document.getElementById("btn-hud-save");
  var btnHudCancel = document.getElementById("btn-hud-cancel");
  var elHudEditHint = document.getElementById("hud-edit-hint");
  var elHudExportJson = document.getElementById("hud-export-json");
  var elHudDownload = document.getElementById("hud-download");
  var elHudImportJson = document.getElementById("hud-import-json");
  var elHudImportError = document.getElementById("hud-import-error");
  var btnHudImport = document.getElementById("btn-hud-import");

  var hudEditingIndex = -1;
  var hudFormOpen = false;
  var hudDraftEntries = [];
  var hudDownloadUrl = null;

  // Welche Felder zu welchem Widget-Typ gehoeren -- "types" steuert das
  // Ein-/Ausblenden (applyHudFormConditionals). "visField"/"contentField"
  // sind zusaetzliche Bedingungen (Sichtbarkeitstyp bzw. Inhaltsart).
  var HUD_ALL_TYPES = ["text", "icon", "bar", "list"];
  var HUD_FIELDS = [
    { key: "id", label: "id", types: HUD_ALL_TYPES, control: "text", help: "Kleinbuchstaben, Ziffern, Bindestrich; beginnt mit einem Buchstaben." },
    { key: "type", label: "Typ", types: HUD_ALL_TYPES, control: "select", options: "widgetTypes" },
    { key: "anchor", label: "Anker", types: HUD_ALL_TYPES, control: "select", options: "anchors" },
    { key: "offsetX", label: "Versatz X (px)", types: HUD_ALL_TYPES, control: "number" },
    { key: "offsetY", label: "Versatz Y (px)", types: HUD_ALL_TYPES, control: "number" },
    { key: "scale", label: "Skalierung (0.5 - 3.0)", types: HUD_ALL_TYPES, control: "number", step: "0.1" },
    { key: "visibilityType", label: "Sichtbarkeit", types: HUD_ALL_TYPES, control: "select", options: "visibilityTypes" },
    { key: "visibilityDimension", label: "Dimension (namespace:pfad)", types: HUD_ALL_TYPES, visField: "dimension", control: "text", help: "z. B. minecraft:overworld" },
    { key: "visibilityDebugScreen", label: "Debug-Bildschirm (F3)", types: HUD_ALL_TYPES, visField: "debugScreen", control: "select", options: "onOff" },
    { key: "contentKind", label: "Inhalt", types: ["text"], control: "select", options: "kind" },
    { key: "contentValue", label: "Fester Text", types: ["text"], contentField: "static", control: "text" },
    { key: "contentSource", label: "Datenquelle", types: ["text"], contentField: "dynamic", control: "select", options: "dataSources" },
    { key: "contentLabel", label: "Label (fester Praefix, z. B. \"FPS: \")", types: ["text"], contentField: "dynamic", control: "text" },
    { key: "contentDecimals", label: "Nachkommastellen (0 - 3)", types: ["text"], contentField: "dynamic", control: "number" },
    { key: "texture", label: "Textur-Pfad (.png)", types: ["icon"], control: "text", help: "Relativer Pfad im Paket, kein \"..\"." },
    { key: "width", label: "Breite (px)", types: ["icon", "bar"], control: "number" },
    { key: "height", label: "Hoehe (px)", types: ["icon", "bar"], control: "number" },
    { key: "source", label: "Datenquelle", types: ["bar"], control: "select", options: "dataSources" },
    { key: "min", label: "Minimum (leerer Balken)", types: ["bar"], control: "number" },
    { key: "max", label: "Maximum (voller Balken)", types: ["bar"], control: "number" },
    { key: "color", label: "Fuellfarbe (#rrggbb)", types: ["bar"], control: "text" }
  ];
  var HUD_FIELD_KEYS = HUD_FIELDS.map(function (d) { return d.key; });

  // Schema-Feldpfad (nach Abschneiden von "widgets[0].") -> Formularfeld-Id-Suffix.
  var HUD_ERROR_TARGET = {
    "offset.x": "offsetX", "offset.y": "offsetY", "offset": "offsetX",
    "visibility.type": "visibilityType", "visibility.dimension": "visibilityDimension",
    "visibility.debugScreen": "visibilityDebugScreen",
    "content.kind": "contentKind", "content.value": "contentValue", "content.source": "contentSource",
    "content.label": "contentLabel", "content.decimals": "contentDecimals"
  };

  function hudOptionValues(name) {
    switch (name) {
      case "widgetTypes": return VitrineHudEditor.widgetTypes(VITRINE_HUD_SCHEMA);
      case "anchors": return VitrineHudEditor.anchors(VITRINE_HUD_SCHEMA);
      case "dataSources": return VitrineHudEditor.dataSources(VITRINE_HUD_SCHEMA);
      case "visibilityTypes": return VitrineHudEditor.visibilityTypes(VITRINE_HUD_SCHEMA);
      case "onOff": return VitrineHudEditor.debugScreenStates(VITRINE_HUD_SCHEMA);
      case "kind": return VitrineHudEditor.textContentKinds(VITRINE_HUD_SCHEMA);
      default: return [];
    }
  }

  function hudGetVal(id) {
    var el = document.getElementById(id);
    return el ? el.value : "";
  }

  function hudMakeSelect(values, selected) {
    var sel = document.createElement("select");
    sel.className = "hud-input";
    values.forEach(function (v) {
      var o = document.createElement("option");
      o.value = v;
      o.textContent = v;
      sel.appendChild(o);
    });
    sel.value = selected;
    return sel;
  }

  function hudMakeInput(kind, value) {
    var input = document.createElement("input");
    input.type = kind === "number" ? "number" : "text";
    input.className = "hud-input";
    input.autocomplete = "off";
    input.value = value == null ? "" : String(value);
    return input;
  }

  function buildHudField(desc) {
    var wrap = document.createElement("div");
    wrap.className = "field hud-field";
    wrap.setAttribute("data-hud-field", desc.key);
    wrap.setAttribute("data-hud-types", desc.types.join(","));
    if (desc.visField) {
      wrap.setAttribute("data-hud-visfield", desc.visField);
    }
    if (desc.contentField) {
      wrap.setAttribute("data-hud-contentfield", desc.contentField);
    }

    var label = document.createElement("label");
    label.setAttribute("for", "hud-field-" + desc.key);
    label.textContent = desc.label;
    wrap.appendChild(label);

    if (desc.help) {
      var help = document.createElement("p");
      help.className = "field-help";
      help.textContent = desc.help;
      wrap.appendChild(help);
    }

    var control;
    if (desc.control === "select") {
      control = document.createElement("select");
      hudOptionValues(desc.options).forEach(function (v) {
        var o = document.createElement("option");
        o.value = v;
        o.textContent = v;
        control.appendChild(o);
      });
    } else {
      control = document.createElement("input");
      control.type = desc.control === "number" ? "number" : "text";
      control.autocomplete = "off";
      if (desc.step) {
        control.step = desc.step;
      }
    }
    control.id = "hud-field-" + desc.key;
    control.name = "hud-field-" + desc.key;
    control.className = "hud-input";
    control.addEventListener("input", onHudFieldInput);
    control.addEventListener("change", onHudFieldInput);
    wrap.appendChild(control);

    var err = document.createElement("div");
    err.id = "hud-error-" + desc.key;
    err.className = "field-errors";
    err.setAttribute("role", "alert");
    err.setAttribute("aria-live", "polite");
    wrap.appendChild(err);

    return wrap;
  }

  function buildHudForm() {
    if (!hudAvailable || !elHudFormFields) {
      return;
    }
    elHudFormFields.textContent = "";
    HUD_FIELDS.forEach(function (desc) {
      elHudFormFields.appendChild(buildHudField(desc));
    });
    if (elHudAddType) {
      elHudAddType.textContent = "";
      VitrineHudEditor.widgetTypes(VITRINE_HUD_SCHEMA).forEach(function (t) {
        var o = document.createElement("option");
        o.value = t;
        o.textContent = t;
        elHudAddType.appendChild(o);
      });
    }
  }

  function onHudFieldInput() {
    applyHudFormConditionals();
    runHudDraftValidation();
  }

  function applyHudFormConditionals() {
    if (!elHudFormFields) {
      return;
    }
    var type = hudGetVal("hud-field-type") || "text";
    var visType = hudGetVal("hud-field-visibilityType") || "always";
    var contentKind = hudGetVal("hud-field-contentKind") || "dynamic";
    var wrappers = elHudFormFields.querySelectorAll("[data-hud-field]");
    Array.prototype.forEach.call(wrappers, function (w) {
      var types = (w.getAttribute("data-hud-types") || "").split(",");
      var show = types.indexOf(type) !== -1;
      if (show && w.getAttribute("data-hud-visfield")) {
        show = w.getAttribute("data-hud-visfield") === visType;
      }
      if (show && w.getAttribute("data-hud-contentfield")) {
        show = w.getAttribute("data-hud-contentfield") === contentKind;
      }
      w.hidden = !show;
    });
    if (elHudEntriesGroup) {
      elHudEntriesGroup.hidden = type !== "list";
    }
  }

  function collectHudFormValues() {
    var type = hudGetVal("hud-field-type") || "text";
    var values = VitrineHudEditor.defaultWidgetFormValues(type);
    HUD_FIELD_KEYS.forEach(function (key) {
      var el = document.getElementById("hud-field-" + key);
      if (el) {
        values[key] = el.value;
      }
    });
    values.entries = hudDraftEntries.map(function (e) {
      return { kind: e.kind, value: e.value, source: e.source, label: e.label, decimals: e.decimals };
    });
    return values;
  }

  function populateHudForm(formValues) {
    HUD_FIELD_KEYS.forEach(function (key) {
      var el = document.getElementById("hud-field-" + key);
      if (el) {
        el.value = formValues[key] == null ? "" : String(formValues[key]);
      }
    });
  }

  function hudEntryControl(labelText, control) {
    var wrap = document.createElement("label");
    wrap.className = "hud-entry-control";
    var span = document.createElement("span");
    span.className = "hud-entry-control-label";
    span.textContent = labelText;
    wrap.appendChild(span);
    wrap.appendChild(control);
    return wrap;
  }

  function renderHudEntries() {
    if (!elHudEntries) {
      return;
    }
    elHudEntries.textContent = "";
    hudDraftEntries.forEach(function (entry, idx) {
      var li = document.createElement("li");
      li.className = "hud-entry";

      var kindSel = hudMakeSelect(VitrineHudEditor.textContentKinds(VITRINE_HUD_SCHEMA), entry.kind || "dynamic");
      kindSel.addEventListener("change", function () {
        hudDraftEntries[idx].kind = kindSel.value;
        renderHudEntries();
        runHudDraftValidation();
      });
      li.appendChild(hudEntryControl("Art", kindSel));

      if ((entry.kind || "dynamic") === "static") {
        var valInput = hudMakeInput("text", entry.value);
        valInput.addEventListener("input", function () {
          hudDraftEntries[idx].value = valInput.value;
          runHudDraftValidation();
        });
        li.appendChild(hudEntryControl("Text", valInput));
      } else {
        var srcSel = hudMakeSelect(VitrineHudEditor.dataSources(VITRINE_HUD_SCHEMA), entry.source || "fps");
        srcSel.addEventListener("change", function () {
          hudDraftEntries[idx].source = srcSel.value;
          runHudDraftValidation();
        });
        li.appendChild(hudEntryControl("Datenquelle", srcSel));

        var labelInput = hudMakeInput("text", entry.label);
        labelInput.addEventListener("input", function () {
          hudDraftEntries[idx].label = labelInput.value;
          runHudDraftValidation();
        });
        li.appendChild(hudEntryControl("Label", labelInput));

        var decInput = hudMakeInput("number", entry.decimals);
        decInput.addEventListener("input", function () {
          hudDraftEntries[idx].decimals = decInput.value;
          runHudDraftValidation();
        });
        li.appendChild(hudEntryControl("Nachkommastellen", decInput));
      }

      var rm = document.createElement("button");
      rm.type = "button";
      rm.className = "btn";
      rm.textContent = "Eintrag entfernen";
      rm.addEventListener("click", function () {
        hudDraftEntries = VitrineHudEditor.removeEntryAt(hudDraftEntries, idx);
        if (hudDraftEntries.length === 0) {
          hudDraftEntries = [VitrineHudEditor.defaultEntryFormValues()];
        }
        renderHudEntries();
        runHudDraftValidation();
      });
      li.appendChild(rm);

      elHudEntries.appendChild(li);
    });
  }

  function hudErrorElFor(field) {
    var key = HUD_ERROR_TARGET[field] || field;
    return document.getElementById("hud-error-" + key);
  }

  function renderHudFieldErrors(byField) {
    if (!elHudForm) {
      return false;
    }
    var allErr = elHudForm.querySelectorAll(".field-errors");
    Array.prototype.forEach.call(allErr, function (e) {
      e.textContent = "";
    });
    var rootMsgs = [];
    var count = 0;
    Object.keys(byField).forEach(function (field) {
      var msgs = byField[field] || [];
      var target = field === "__root__" ? null : hudErrorElFor(field);
      if (!target) {
        rootMsgs = rootMsgs.concat(msgs);
        count += msgs.length;
        return;
      }
      msgs.forEach(function (m) {
        var p = document.createElement("p");
        p.textContent = m;
        target.appendChild(p);
        count++;
      });
    });
    if (elHudFormFields) {
      Array.prototype.forEach.call(elHudFormFields.querySelectorAll(".hud-field"), function (w) {
        var e = w.querySelector(".field-errors");
        w.classList.toggle("has-error", !!e && e.textContent !== "");
      });
    }
    if (elHudFormError) {
      if (rootMsgs.length > 0) {
        elHudFormError.textContent = rootMsgs.join(" ");
        elHudFormError.hidden = false;
      } else {
        elHudFormError.textContent = "";
        elHudFormError.hidden = true;
      }
    }
    var hasErrors = count > 0;
    if (btnHudSave) {
      btnHudSave.disabled = hasErrors;
    }
    return hasErrors;
  }

  function runHudDraftValidation() {
    if (!hudAvailable || !hudFormOpen) {
      return null;
    }
    var current = getCurrentProject();
    if (!current) {
      return null;
    }
    var draft = VitrineHudEditor.buildWidgetInstance(collectHudFormValues());
    var widgets = VitrineHudEditor.ensureHud(current.hud).widgets;
    var byField = VitrineHudEditor.validateDraftWidgetByField(
      draft, widgets, hudEditingIndex, VITRINE_HUD_SCHEMA, VitrineSchemaValidator
    );
    renderHudFieldErrors(byField);
    return byField;
  }

  function openHudForm(index) {
    var current = getCurrentProject();
    if (!current || !elHudForm) {
      return;
    }
    hudEditingIndex = index;
    hudFormOpen = true;
    var widgets = VitrineHudEditor.ensureHud(current.hud).widgets;
    var formValues;
    if (index < 0) {
      formValues = VitrineHudEditor.defaultWidgetFormValues(
        elHudAddType && elHudAddType.value ? elHudAddType.value : "text"
      );
    } else {
      formValues = VitrineHudEditor.widgetToFormValues(widgets[index]);
    }
    var rawEntries = Array.isArray(formValues.entries) && formValues.entries.length > 0
      ? formValues.entries
      : [VitrineHudEditor.defaultEntryFormValues()];
    hudDraftEntries = rawEntries.map(function (e) {
      return { kind: e.kind, value: e.value, source: e.source, label: e.label, decimals: e.decimals };
    });
    populateHudForm(formValues);
    applyHudFormConditionals();
    renderHudEntries();
    if (elHudFormTitle) {
      elHudFormTitle.textContent = index < 0 ? "Neues Widget" : "Widget bearbeiten (Nr. " + (index + 1) + ")";
    }
    elHudForm.hidden = false;
    if (elHudEditHint) {
      elHudEditHint.hidden = true;
    }
    runHudDraftValidation();
  }

  function closeHudForm() {
    hudFormOpen = false;
    hudEditingIndex = -1;
    hudDraftEntries = [];
    if (elHudForm) {
      elHudForm.hidden = true;
    }
    if (elHudEditHint) {
      elHudEditHint.hidden = false;
    }
    if (elHudFormError) {
      elHudFormError.hidden = true;
      elHudFormError.textContent = "";
    }
  }

  function submitHudForm(event) {
    if (event) {
      event.preventDefault();
    }
    if (!hudAvailable) {
      return;
    }
    var current = getCurrentProject();
    if (!current) {
      return;
    }
    var draft = VitrineHudEditor.buildWidgetInstance(collectHudFormValues());
    var byField = runHudDraftValidation();
    if (byField && Object.keys(byField).length > 0) {
      if (elHudFormError) {
        elHudFormError.hidden = false;
        elHudFormError.textContent = (elHudFormError.textContent ? elHudFormError.textContent + " " : "") +
          "Bitte die markierten Felder korrigieren.";
      }
      return;
    }
    if (hudEditingIndex < 0) {
      current.hud = VitrineHudEditor.addWidget(current.hud, draft);
    } else {
      current.hud = VitrineHudEditor.updateWidgetAt(current.hud, hudEditingIndex, draft);
    }
    saveState(state);
    closeHudForm();
    renderHudList();
    renderHudExport();
  }

  function hudMoveButton(idx, delta, label, length) {
    var b = document.createElement("button");
    b.type = "button";
    b.className = "btn";
    b.textContent = label;
    b.disabled = idx + delta < 0 || idx + delta >= length;
    b.addEventListener("click", function () {
      var proj = getCurrentProject();
      if (!proj) {
        return;
      }
      proj.hud = VitrineHudEditor.moveWidget(proj.hud, idx, delta);
      saveState(state);
      if (hudEditingIndex === idx) {
        hudEditingIndex = idx + delta;
      } else if (hudEditingIndex === idx + delta) {
        hudEditingIndex = idx;
      }
      renderHudList();
      renderHudExport();
    });
    return b;
  }

  function renderHudSummary(widgets, current) {
    if (!elHudSummary) {
      return;
    }
    elHudSummary.classList.remove("has-errors", "is-valid");
    if (!current) {
      elHudSummary.textContent = "";
      return;
    }
    if (widgets.length === 0) {
      elHudSummary.textContent = "Noch kein Widget -- ein leeres HUD ist kein gueltiges Paket (mindestens 1 Widget noetig).";
      elHudSummary.classList.add("has-errors");
      return;
    }
    var msgs = VitrineHudEditor.validateHudDocument(current.hud, VITRINE_HUD_SCHEMA, VitrineSchemaValidator);
    if (msgs.length === 0) {
      elHudSummary.textContent = widgets.length === 1
        ? "1 Widget -- hud.json ist gueltig."
        : widgets.length + " Widgets -- hud.json ist gueltig.";
      elHudSummary.classList.add("is-valid");
    } else {
      elHudSummary.textContent = (msgs.length === 1 ? "1 Problem: " : msgs.length + " Probleme: ") + msgs.join(" | ");
      elHudSummary.classList.add("has-errors");
    }
  }

  function renderHudList() {
    if (!hudAvailable || !elHudWidgetList) {
      return;
    }
    elHudWidgetList.textContent = "";
    var current = getCurrentProject();
    var widgets = current ? VitrineHudEditor.ensureHud(current.hud).widgets : [];
    if (elHudWidgetListEmpty) {
      elHudWidgetListEmpty.hidden = widgets.length > 0;
    }

    widgets.forEach(function (widget, idx) {
      var li = document.createElement("li");
      li.className = "hud-widget-item";

      var openBtn = document.createElement("button");
      openBtn.type = "button";
      openBtn.className = "hud-widget-open" + (hudFormOpen && idx === hudEditingIndex ? " is-selected" : "");
      var title = document.createElement("span");
      title.className = "hud-widget-open-title";
      title.textContent = (widget.id || "(ohne id)") + " -- " + (widget.type || "?");
      var meta = document.createElement("span");
      meta.className = "hud-widget-open-meta";
      meta.textContent = "Anker: " + (widget.anchor || "?");
      openBtn.appendChild(title);
      openBtn.appendChild(document.createElement("br"));
      openBtn.appendChild(meta);
      openBtn.addEventListener("click", function () {
        openHudForm(idx);
        renderHudList();
      });

      var actions = document.createElement("div");
      actions.className = "hud-widget-actions";
      actions.appendChild(hudMoveButton(idx, -1, "Hoch", widgets.length));
      actions.appendChild(hudMoveButton(idx, 1, "Runter", widgets.length));
      var del = document.createElement("button");
      del.type = "button";
      del.className = "btn";
      del.textContent = "Entfernen";
      del.addEventListener("click", function () {
        var proj = getCurrentProject();
        if (!proj) {
          return;
        }
        proj.hud = VitrineHudEditor.removeWidgetAt(proj.hud, idx);
        saveState(state);
        if (hudEditingIndex === idx) {
          closeHudForm();
        }
        renderHudList();
        renderHudExport();
      });
      actions.appendChild(del);

      li.appendChild(openBtn);
      li.appendChild(actions);
      elHudWidgetList.appendChild(li);
    });

    renderHudSummary(widgets, current);
  }

  function renderHudExport() {
    if (!hudAvailable) {
      return;
    }
    var current = getCurrentProject();
    var text = current ? VitrineHudEditor.toJsonText(current.hud) : "";
    if (elHudExportJson) {
      elHudExportJson.value = text;
    }
    if (elHudDownload) {
      if (hudDownloadUrl) {
        try {
          URL.revokeObjectURL(hudDownloadUrl);
        } catch (err) {
          // Aufraeumen ist best effort
        }
        hudDownloadUrl = null;
      }
      if (current) {
        hudDownloadUrl = URL.createObjectURL(new Blob([text], { type: "application/json" }));
        elHudDownload.href = hudDownloadUrl;
        elHudDownload.removeAttribute("aria-disabled");
      } else {
        elHudDownload.removeAttribute("href");
        elHudDownload.setAttribute("aria-disabled", "true");
      }
    }
  }

  function updateHudNavAvailability() {
    if (!navHud) {
      return;
    }
    var hasProject = !!getCurrentProject();
    navHud.disabled = !hasProject || !hudAvailable;
    if ((!hasProject || !hudAvailable) && currentSection === "hud") {
      showSection("placeholder");
    }
  }

  if (hudAvailable) {
    buildHudForm();
  }

  if (navHud) {
    navHud.addEventListener("click", function () {
      if (navHud.disabled) {
        return;
      }
      showSection("hud");
    });
  }

  if (btnHudAdd) {
    btnHudAdd.addEventListener("click", function () {
      openHudForm(-1);
      renderHudList();
    });
  }

  if (btnHudCancel) {
    btnHudCancel.addEventListener("click", function () {
      closeHudForm();
      renderHudList();
    });
  }

  if (elHudForm) {
    elHudForm.addEventListener("submit", submitHudForm);
  }

  if (btnHudEntryAdd) {
    btnHudEntryAdd.addEventListener("click", function () {
      hudDraftEntries = VitrineHudEditor.addEntry(hudDraftEntries);
      renderHudEntries();
      runHudDraftValidation();
    });
  }

  if (btnHudImport) {
    btnHudImport.addEventListener("click", function () {
      if (!hudAvailable) {
        return;
      }
      var current = getCurrentProject();
      if (!current || !elHudImportJson) {
        return;
      }
      if (elHudImportError) {
        elHudImportError.hidden = true;
        elHudImportError.textContent = "";
      }
      var raw = elHudImportJson.value.trim();
      if (!raw) {
        return;
      }
      var parsed;
      try {
        parsed = JSON.parse(raw);
      } catch (err) {
        if (elHudImportError) {
          elHudImportError.hidden = false;
          elHudImportError.textContent = "Kein gueltiges JSON: " + (err && err.message ? err.message : String(err));
        }
        return;
      }
      current.hud = VitrineHudEditor.ensureHud(parsed);
      saveState(state);
      elHudImportJson.value = "";
      closeHudForm();
      renderHudList();
      renderHudExport();
    });
  }

  // --- D6: Export .vpkg ---
  var exportAvailable = typeof VitrineVpkgExport !== "undefined" && typeof JSZip === "function";

  var navExport = document.getElementById("nav-export");
  var elExportPanel = document.getElementById("export-panel");
  var btnExportBuild = document.getElementById("btn-export-build");
  var elExportErrors = document.getElementById("export-errors");
  var elExportResult = document.getElementById("export-result");
  var elExportSha256 = document.getElementById("export-sha256");
  var elExportDownloadVpkg = document.getElementById("export-download-vpkg");
  var elExportDownloadSidecar = document.getElementById("export-download-sidecar");

  // Objekt-URLs vorheriger Exporte -- werden vor jedem neuen Bau freigegeben
  // (URL.revokeObjectURL), damit ein Doppelklick auf "Paket exportieren"
  // keine Blobs im Speicher anhaeuft.
  var activeExportObjectUrls = [];

  function revokeActiveExportObjectUrls() {
    activeExportObjectUrls.forEach(function (url) {
      try {
        URL.revokeObjectURL(url);
      } catch (err) {
        // ignorieren -- Aufraeumen ist best effort
      }
    });
    activeExportObjectUrls = [];
  }

  function updateExportNavAvailability() {
    if (!navExport) {
      return;
    }
    var hasProject = !!getCurrentProject();
    navExport.disabled = !hasProject || !exportAvailable;
    if ((!hasProject || !exportAvailable) && currentSection === "export") {
      showSection("placeholder");
    }
  }

  function resetExportDisplay() {
    if (elExportErrors) {
      elExportErrors.hidden = true;
      elExportErrors.textContent = "";
    }
    if (elExportResult) {
      elExportResult.hidden = true;
    }
    revokeActiveExportObjectUrls();
  }

  function showExportErrors(errors) {
    if (!elExportErrors) {
      return;
    }
    elExportErrors.textContent = "";
    var heading = document.createElement("p");
    heading.textContent = errors.length === 1
      ? "Export abgebrochen -- 1 Problem gefunden:"
      : "Export abgebrochen -- " + errors.length + " Probleme gefunden:";
    elExportErrors.appendChild(heading);
    var list = document.createElement("ul");
    errors.forEach(function (message) {
      var li = document.createElement("li");
      li.textContent = message;
      list.appendChild(li);
    });
    elExportErrors.appendChild(list);
    elExportErrors.hidden = false;
  }

  /** Baut aus id/version einen sicheren Dateinamen -- fehlen beide (sollte nach erfolgreicher Validierung nie passieren), Rueckfall "paket". */
  function exportFileBaseName(manifest) {
    var raw = ((manifest && manifest.id) || "paket") + (manifest && manifest.version ? "-" + manifest.version : "");
    return raw.replace(/[^A-Za-z0-9_.-]+/g, "-");
  }

  function triggerDownload(anchorEl, blob, fileName) {
    var url = URL.createObjectURL(blob);
    activeExportObjectUrls.push(url);
    anchorEl.href = url;
    anchorEl.download = fileName;
  }

  if (btnExportBuild) {
    btnExportBuild.addEventListener("click", function () {
      if (!exportAvailable) {
        return;
      }
      var current = getCurrentProject();
      if (!current) {
        return;
      }
      resetExportDisplay();
      VitrineVpkgExport.buildVpkgPackage(current, VITRINE_MANIFEST_SCHEMA, { JSZip: JSZip, validator: VitrineSchemaValidator })
        .then(function (result) {
          if (!result.ok) {
            showExportErrors(result.errors);
            return;
          }
          var baseName = exportFileBaseName(current.manifest);
          var vpkgFileName = baseName + ".vpkg";
          var sidecarFileName = VitrineVpkgExport.sidecarFileNameFor(vpkgFileName);

          var vpkgBlob = new Blob([result.zipBytes], { type: "application/zip" });
          var sidecarBlob = new Blob([result.sidecarJson], { type: "application/json" });

          if (elExportSha256) {
            elExportSha256.textContent = result.sha256Hex;
          }
          if (elExportDownloadVpkg) {
            triggerDownload(elExportDownloadVpkg, vpkgBlob, vpkgFileName);
          }
          if (elExportDownloadSidecar) {
            triggerDownload(elExportDownloadSidecar, sidecarBlob, sidecarFileName);
          }
          if (elExportResult) {
            elExportResult.hidden = false;
          }
        })
        .catch(function (err) {
          showExportErrors(["Unerwarteter Fehler beim Export: " + (err && err.message ? err.message : String(err))]);
        });
    });
  }

  if (navExport) {
    navExport.addEventListener("click", function () {
      if (navExport.disabled) {
        return;
      }
      showSection("export");
    });
  }

  function getCurrentProject() {
    if (!state.currentProjectId) {
      return null;
    }
    for (var i = 0; i < state.projects.length; i++) {
      if (state.projects[i].id === state.currentProjectId) {
        return state.projects[i];
      }
    }
    return null;
  }

  function hideAllForms() {
    formNewProject.hidden = true;
    wrapProjectList.hidden = true;
    elNewProjectError.hidden = true;
    elNewProjectError.textContent = "";
  }

  function renderCurrentProject() {
    var current = getCurrentProject();
    if (current) {
      elProjectEmpty.hidden = true;
      elProjectOpen.hidden = false;
      elProjectOpenName.textContent = current.name;
      elProjectOpenMeta.textContent = "Angelegt am " + formatDate(current.createdAt);
      btnCloseProject.hidden = false;
    } else {
      elProjectEmpty.hidden = false;
      elProjectOpen.hidden = true;
      btnCloseProject.hidden = true;
    }
  }

  function renderProjectList() {
    elProjectList.textContent = "";
    if (state.projects.length === 0) {
      elProjectListEmpty.hidden = false;
      return;
    }
    elProjectListEmpty.hidden = true;

    var sorted = state.projects.slice().sort(function (a, b) {
      return (b.createdAt || "").localeCompare(a.createdAt || "");
    });

    sorted.forEach(function (project) {
      var li = document.createElement("li");
      li.className = "project-list-item";

      var info = document.createElement("div");
      var nameEl = document.createElement("div");
      nameEl.className = "project-list-item-name";
      nameEl.textContent = project.name;
      var metaEl = document.createElement("div");
      metaEl.className = "project-list-item-meta";
      metaEl.textContent = "Angelegt am " + formatDate(project.createdAt);
      info.appendChild(nameEl);
      info.appendChild(metaEl);

      var actions = document.createElement("div");
      actions.className = "project-list-item-actions";

      var openBtn = document.createElement("button");
      openBtn.type = "button";
      openBtn.className = "btn btn-primary";
      openBtn.textContent = "Oeffnen";
      openBtn.addEventListener("click", function () {
        state.currentProjectId = project.id;
        saveState(state);
        renderCurrentProject();
        hideAllForms();
        renderStorageWarning();
        onProjectChanged();
      });

      var deleteBtn = document.createElement("button");
      deleteBtn.type = "button";
      deleteBtn.className = "btn";
      deleteBtn.textContent = "Loeschen";
      deleteBtn.addEventListener("click", function () {
        state.projects = state.projects.filter(function (p) {
          return p.id !== project.id;
        });
        if (state.currentProjectId === project.id) {
          state.currentProjectId = null;
        }
        saveState(state);
        renderCurrentProject();
        renderProjectList();
        renderStorageWarning();
        onProjectChanged();
      });

      actions.appendChild(openBtn);
      actions.appendChild(deleteBtn);

      li.appendChild(info);
      li.appendChild(actions);
      elProjectList.appendChild(li);
    });
  }

  function renderStorageWarning() {
    elStorageWarning.hidden = storageAvailable;
  }

  btnNewProject.addEventListener("click", function () {
    hideAllForms();
    formNewProject.hidden = false;
    inputProjectName.value = "";
    inputProjectName.focus();
  });

  btnCancelNew.addEventListener("click", function () {
    hideAllForms();
  });

  formNewProject.addEventListener("submit", function (event) {
    event.preventDefault();
    var name = inputProjectName.value.trim();
    if (name.length === 0) {
      elNewProjectError.textContent = "Bitte einen Namen eingeben.";
      elNewProjectError.hidden = false;
      return;
    }
    if (name.length > 80) {
      elNewProjectError.textContent = "Name darf hoechstens 80 Zeichen lang sein.";
      elNewProjectError.hidden = false;
      return;
    }
    var project = {
      id: createId(),
      name: name,
      createdAt: new Date().toISOString(),
      manifest: {}
    };
    state.projects.push(project);
    state.currentProjectId = project.id;
    saveState(state);
    hideAllForms();
    renderCurrentProject();
    renderStorageWarning();
    onProjectChanged();
  });

  btnLoadProject.addEventListener("click", function () {
    hideAllForms();
    wrapProjectList.hidden = false;
    renderProjectList();
  });

  btnCancelLoad.addEventListener("click", function () {
    hideAllForms();
  });

  btnCloseProject.addEventListener("click", function () {
    state.currentProjectId = null;
    saveState(state);
    renderCurrentProject();
    onProjectChanged();
  });

  renderCurrentProject();
  renderStorageWarning();
  onProjectChanged();
})();
