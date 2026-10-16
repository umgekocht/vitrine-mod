"use strict";

/*
 * Vitrine Designer -- D6 Export-Logik (.vpkg im Browser).
 *
 * Kein DOM-Zugriff hier (wie theme-editor.js/texture-studio.js) -- unter Node
 * direkt testbar (designer/test/vpkg-export.test.js) und im Browser per
 * <script> eingebunden (kein type="module", kein fetch(), siehe
 * app.js-Kopfkommentar aus D1). app.js reicht das aktuelle Projekt, das
 * geladene JSZip (aus designer/vendor/jszip.min.js, MIT-Lizenz, siehe
 * THIRD-PARTY.md) und das eingebettete Manifest-Schema hierher weiter --
 * alles Pruefen/Bauen/Hashen passiert in dieser Datei.
 *
 * Zustaendigkeiten:
 *  - checkEntryPathSafety(name): dieselbe P3-Pruefung wie
 *    VpkgSecurityValidator#checkPathTraversal/#checkAbsolutePath/
 *    #checkForbiddenFileType (Mod, mod/.../vpkg/VpkgSecurityValidator.java) --
 *    kein absoluter Pfad, kein '..'-Segment, keine verbotene Dateiendung
 *    (dieselbe FORBIDDEN_EXTENSIONS-Liste, dort wortgleich uebernommen). Es
 *    gibt dafuer KEIN gemeinsames Schema unter registry/schema/ (Java und JS
 *    teilen sich keine Datei fuer diese Herleitung, genau wie bei
 *    texture-studio.js#readPngHeader) -- aendert sich die Liste im Mod, muss
 *    sie hier von Hand nachgezogen werden.
 *  - validateProjectForExport(project, schema, validator): baut NICHT das
 *    ZIP, sondern prueft VORHER alles, was P1/P3 verlangen -- Manifest gegen
 *    dasselbe Schema wie D2 (schema-validate.js), jeder Textur-Zielpfad
 *    gegen checkEntryPathSafety (bewusst NEU geprueft, nicht nur der
 *    schwaechere Texture-Studio-Regex uebernommen -- ein tampering am
 *    localStorage-Projektstand, das '..' in einen targetPath schmuggelt,
 *    MUSS hier auffliegen, nicht erst beim Anwenden im Mod), jede Textur
 *    erneut gegen die 4096x4096-PNG-Grenze (Wiederverwendung von
 *    texture-studio.js#readPngHeader/#validateDimensions, nicht neu
 *    erfunden) und auf doppelt vergebene Zielpfade. Liefert
 *    { ok, errors: string[] } -- bei !ok bricht der Aufrufer ab, OHNE ein
 *    Byte zu bauen.
 *  - buildZipEntries(project): { name, bytes } je ZIP-Eintrag aus einem
 *    BEREITS erfolgreich validierten Projekt -- "vitrine.json" (das
 *    Manifest, huebsch eingerueckt), GATE52: "theme.json" (ebenso eingerueckt)
 *    aus project.theme, NUR wenn project.theme vorhanden ist (siehe
 *    THEME_ENTRY_NAME, dieselbe Konstante wie
 *    mod/.../pack/ThemePackCoordinator.THEME_ENTRY_NAME), plus einen Eintrag
 *    je project.textures[] unter seinem targetPath.
 *  - buildVpkgPackage(project, schema, options): buendelt Validieren + Zippen
 *    (options.JSZip, echte JSZip-Klasse, per Dependency Injection statt
 *    fixem require()/global -- Browser liefert window.JSZip, Tests liefern
 *    require("../vendor/jszip.min.js")) + SHA-256 ueber die fertigen
 *    ZIP-Bytes (Web-Crypto-API "crypto.subtle", in jedem modernen Browser und
 *    in Node ab Version 19 global vorhanden -- keine neue Abhaengigkeit).
 *    Liefert ein Promise auf { ok, errors, zipBytes, sha256Hex, sidecarJson,
 *    sidecarFileName }. "sidecarJson" ist Byte-fuer-Byte im selben
 *    Schluessel-/Einrueckungs-Format wie cli/lib/signing.js#signFile
 *    schreibt ({"sha256": "..."} , JSON.stringify(..., null, 2) + "\n") --
 *    ohne "signature"/"publicKey" (Signieren ist NICHT Aufgabe von D6, der
 *    Browser hat keinen Zugriff auf den lokalen Signierschluessel). `cli
 *    vitrine.js sign` liest die fertige .vpkg-Datei ohnehin selbst neu ein
 *    und schreibt seine eigene, vollstaendige Begleitdatei -- das Format
 *    bleibt dabei unveraendert, nur "signature"/"publicKey" kommen dazu.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory(require("./texture-studio.js"), require("./schema-validate.js"));
  } else {
    root.VitrineVpkgExport = factory(root.VitrineTextureStudio, root.VitrineSchemaValidator);
  }
})(typeof self !== "undefined" ? self : this, function (TextureStudio, DefaultSchemaValidator) {

  /** Name des Manifest-Eintrags in jedem .vpkg, siehe mod/.../vpkg/VpkgArchive.MANIFEST_ENTRY_NAME. */
  var MANIFEST_ENTRY_NAME = "vitrine.json";

  /**
   * Name des Theme-Eintrags im Archiv-Root, GENAU wie
   * mod/.../pack/ThemePackCoordinator.THEME_ENTRY_NAME. GATE52: project.theme
   * (von theme-editor.js/theme-wiring.js aufgebaut, Form { schemaVersion,
   * light, dark } -- identisch zum Wurzelobjekt aus
   * registry/schema/theme.schema.json) wurde bislang nirgends ins Archiv
   * geschrieben; der Mod erkannte das Fehlen der Datei und liess das
   * Theme-Paket wirkungslos installieren.
   */
  var THEME_ENTRY_NAME = "theme.json";

  /** Suffix der Pruefsummen-Begleitdatei, siehe cli/lib/signing.js#SIDECAR_SUFFIX / VpkgIntegritySidecar.FILE_SUFFIX. */
  var SIDECAR_SUFFIX = ".integrity.json";

  /**
   * Verbotene Dateiendungen fuer Klasse A -- wortgleiche Kopie von
   * VpkgSecurityValidator.FORBIDDEN_EXTENSIONS (mod/.../vpkg/VpkgSecurityValidator.java).
   * Aendert sich diese Liste im Mod, muss sie hier von Hand nachgezogen werden
   * (siehe Kopfkommentar).
   */
  var FORBIDDEN_EXTENSIONS = [
    "exe", "dll", "so", "dylib", "jar", "class", "bat", "cmd", "sh", "ps1", "vbs", "py",
    "js", "mjs", "lua", "vbe", "jse", "wsf"
  ];

  function extensionOf(name) {
    var baseName = name;
    var lastSlash = Math.max(baseName.lastIndexOf("/"), baseName.lastIndexOf("\\"));
    if (lastSlash >= 0) {
      baseName = baseName.substring(lastSlash + 1);
    }
    var lastDot = baseName.lastIndexOf(".");
    if (lastDot < 0 || lastDot === baseName.length - 1) {
      return null;
    }
    return baseName.substring(lastDot + 1).toLowerCase();
  }

  /** Wie VpkgSecurityValidator#isAbsolutePath: fuehrender Slash/Backslash oder Windows-Laufwerksbuchstabe. */
  function isAbsolutePath(name) {
    if (!name) {
      return false;
    }
    var first = name.charAt(0);
    if (first === "/" || first === "\\") {
      return true;
    }
    if (name.length >= 3 && /[A-Za-z]/.test(name.charAt(0)) && name.charAt(1) === ":" &&
      (name.charAt(2) === "\\" || name.charAt(2) === "/")) {
      return true;
    }
    return false;
  }

  /** Wie VpkgSecurityValidator#checkPathTraversal: irgendein '..'-Segment nach Slash/Backslash-Trennung. */
  function hasPathTraversal(name) {
    var segments = name.split(/[/\\]+/);
    for (var i = 0; i < segments.length; i++) {
      if (segments[i] === "..") {
        return true;
      }
    }
    return false;
  }

  /**
   * Prueft EINEN Ziel-Pfad gegen dieselben P3-Regeln wie VpkgSecurityValidator
   * (Mod): relativ, kein '..'-Segment, keine verbotene Dateiendung. Liefert
   * eine Liste von Klartext-Meldungen (leer = sauber) -- lehnt ab, statt den
   * Pfad still zu bereinigen (siehe Kopfkommentar).
   */
  function checkEntryPathSafety(name) {
    var errors = [];
    if (typeof name !== "string" || name.trim().length === 0) {
      errors.push("Zielpfad ist leer.");
      return errors;
    }
    if (isAbsolutePath(name)) {
      errors.push("Zielpfad '" + name + "' ist absolut -- erlaubt sind nur relative Pfade innerhalb des Pakets.");
    }
    if (hasPathTraversal(name)) {
      errors.push("Zielpfad '" + name + "' enthaelt ein '..'-Segment (Zip-Slip-Versuch) und wird abgelehnt.");
    }
    var ext = extensionOf(name);
    if (ext && FORBIDDEN_EXTENSIONS.indexOf(ext) !== -1) {
      errors.push("Zielpfad '" + name + "' hat die verbotene Dateiendung '." + ext + "' -- Klasse A darf keinen ausfuehrbaren Code enthalten.");
    }
    return errors;
  }

  // --- Base64 decode (Gegenstueck zu texture-studio.js#bytesToBase64) ---

  var BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  /** "data:image/png;base64,...."-URL (oder reines Base64) -> Uint8Array der rohen Bytes. */
  function bytesFromDataUrl(dataUrl) {
    var idx = typeof dataUrl === "string" ? dataUrl.indexOf(",") : -1;
    var b64 = idx >= 0 ? dataUrl.substring(idx + 1) : String(dataUrl || "");
    var clean = b64.replace(/[^A-Za-z0-9+/]/g, "");
    var bytes = [];
    var buffer = 0;
    var bits = 0;
    for (var i = 0; i < clean.length; i++) {
      var value = BASE64_CHARS.indexOf(clean.charAt(i));
      if (value === -1) {
        continue;
      }
      buffer = (buffer << 6) | value;
      bits += 6;
      if (bits >= 8) {
        bits -= 8;
        bytes.push((buffer >> bits) & 0xff);
      }
    }
    return new Uint8Array(bytes);
  }

  /** Reine JS-UTF-8-Kodierung eines Strings, ohne TextEncoder vorauszusetzen (fuer sehr alte Browser). */
  function utf8Bytes(str) {
    var bytes = [];
    for (var i = 0; i < str.length; i++) {
      var code = str.charCodeAt(i);
      if (code >= 0xd800 && code <= 0xdbff && i + 1 < str.length) {
        var next = str.charCodeAt(i + 1);
        if (next >= 0xdc00 && next <= 0xdfff) {
          code = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00);
          i++;
        }
      }
      if (code < 0x80) {
        bytes.push(code);
      } else if (code < 0x800) {
        bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
      } else if (code < 0x10000) {
        bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
      } else {
        bytes.push(
          0xf0 | (code >> 18),
          0x80 | ((code >> 12) & 0x3f),
          0x80 | ((code >> 6) & 0x3f),
          0x80 | (code & 0x3f)
        );
      }
    }
    return new Uint8Array(bytes);
  }

  function bytesToHex(bytes) {
    var view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
    var hex = "";
    for (var i = 0; i < view.length; i++) {
      hex += (view[i] < 16 ? "0" : "") + view[i].toString(16);
    }
    return hex;
  }

  function getSubtleCrypto() {
    if (typeof crypto !== "undefined" && crypto && crypto.subtle) {
      return crypto.subtle;
    }
    if (typeof self !== "undefined" && self.crypto && self.crypto.subtle) {
      return self.crypto.subtle;
    }
    if (typeof window !== "undefined" && window.crypto && window.crypto.subtle) {
      return window.crypto.subtle;
    }
    return null;
  }

  /** SHA-256 ueber `bytes`, Ergebnis als Hex-Kleinbuchstaben-String (wie cli/lib/signing.js). */
  function sha256Hex(bytes) {
    var subtle = getSubtleCrypto();
    if (!subtle) {
      return Promise.reject(new Error("Web-Crypto-API (crypto.subtle) ist in dieser Umgebung nicht verfuegbar -- SHA-256 kann nicht gebildet werden."));
    }
    return subtle.digest("SHA-256", bytes).then(function (digestBuffer) {
      return bytesToHex(new Uint8Array(digestBuffer));
    });
  }

  /**
   * Prueft `project` vollstaendig gegen die Export-Voraussetzungen (P1/P3),
   * OHNE irgendein Byte zu bauen. Liefert { ok: boolean, errors: string[] }.
   */
  function validateProjectForExport(project, schema, validator) {
    validator = validator || DefaultSchemaValidator;
    var errors = [];
    var manifest = (project && project.manifest && typeof project.manifest === "object" && !Array.isArray(project.manifest))
      ? project.manifest
      : {};

    validator.validate(schema, manifest).forEach(function (message) {
      errors.push("Manifest: " + message);
    });

    // GATE6: ein Klasse-B-Paket hat gar kein .vpkg. Die .jar wird als eigenes
    // Release-Asset veroeffentlicht und im Katalog ueber 'jarUrl' referenziert
    // (registry-package.schema.json, $defs/javamodVersionItem). Ein hier
    // gebautes .vpkg wuerde nie jemand herunterladen -- lieber ehrlich
    // abbrechen als eine Datei ausliefern, die ins Leere zeigt.
    if (manifest.contentClass === "javamod") {
      errors.push(
        "Manifest: contentClass 'javamod' (Klasse B) wird nicht als .vpkg ausgeliefert -- " +
        "veroeffentliche stattdessen die .jar als Release-Asset und melde sie im Katalog an " +
        "(siehe docs/klasse-b.md)."
      );
    }

    var textures = TextureStudio.ensureTextures(project && project.textures);
    var seenPaths = {};

    textures.forEach(function (texture) {
      var name = texture && texture.targetPath;
      var pathErrors = checkEntryPathSafety(name);
      pathErrors.forEach(function (message) {
        errors.push("Textur: " + message);
      });

      if (pathErrors.length === 0) {
        if (Object.prototype.hasOwnProperty.call(seenPaths, name)) {
          errors.push("Textur: Zielpfad '" + name + "' ist mehrfach vergeben.");
        }
        seenPaths[name] = true;
      }

      if (!texture || typeof texture.dataUrl !== "string" || texture.dataUrl.length === 0) {
        errors.push("Textur '" + name + "': keine Bilddaten vorhanden.");
        return;
      }

      var bytes = bytesFromDataUrl(texture.dataUrl);
      var header = TextureStudio.readPngHeader(bytes);
      if (!header.valid) {
        errors.push("Textur '" + name + "': " + header.error);
        return;
      }
      var dimCheck = TextureStudio.validateDimensions(header.width, header.height);
      if (!dimCheck.valid) {
        errors.push("Textur '" + name + "': " + dimCheck.message);
      }
    });

    // Keine Textur ist keine harte Ablehnung -- ein Manifest-only-Paket
    // (z. B. reines Theme) ist gueltig, siehe
    // registry/schema/examples/valid/minimal.json.

    return { ok: errors.length === 0, errors: errors };
  }

  /**
   * { name, bytes: Uint8Array }[] fuer ein BEREITS erfolgreich validiertes
   * Projekt (siehe validateProjectForExport) -- baut selbst keine Pruefung
   * mehr auf, damit Aufrufer nie versehentlich ungeprueften Inhalt packen.
   */
  function buildZipEntries(project) {
    var manifestJson = JSON.stringify(project.manifest, null, 2) + "\n";
    var entries = [{ name: MANIFEST_ENTRY_NAME, bytes: utf8Bytes(manifestJson) }];

    // GATE52: project.theme ({ schemaVersion, light, dark }, siehe
    // theme-wiring.js#ensureTheme/theme-tokens.js-Kopfkommentar) ist dasselbe
    // Wurzelobjekt wie registry/schema/theme.schema.json verlangt -- byte-fuer-
    // byte als "theme.json" ins Archiv-Root, keine Umformung, kein zweites
    // Format. Ein Projekt ohne Theme (z. B. ein reines Textur-Paket) bekommt
    // KEINEN theme.json-Eintrag.
    var theme = project.theme;
    if (theme && typeof theme === "object" && !Array.isArray(theme)) {
      var themeJson = JSON.stringify(theme, null, 2) + "\n";
      entries.push({ name: THEME_ENTRY_NAME, bytes: utf8Bytes(themeJson) });
    }

    TextureStudio.ensureTextures(project.textures).forEach(function (texture) {
      entries.push({ name: texture.targetPath, bytes: bytesFromDataUrl(texture.dataUrl) });
    });
    return entries;
  }

  /** Dateiname der Begleitdatei zu `vpkgFileName`, exakt wie cli/lib/signing.js#sidecarPathFor (nur der Dateiname, kein Verzeichnis). */
  function sidecarFileNameFor(vpkgFileName) {
    return vpkgFileName + SIDECAR_SUFFIX;
  }

  /**
   * Baut ein vollstaendiges .vpkg aus `project` (Validieren + Zippen +
   * SHA-256). `options.JSZip` ist die JSZip-Klasse (Dependency Injection,
   * siehe Kopfkommentar). Liefert ein Promise:
   *   - bei Validierungsfehlern: { ok: false, errors: string[] } (kein ZIP
   *     gebaut, kein JSZip noetig)
   *   - bei Erfolg: { ok: true, errors: [], zipBytes: Uint8Array,
   *     sha256Hex: string, sidecarJson: string, sidecarFileNameFor: fn }
   */
  function buildVpkgPackage(project, schema, options) {
    options = options || {};
    var validator = options.validator || DefaultSchemaValidator;
    var check = validateProjectForExport(project, schema, validator);
    if (!check.ok) {
      return Promise.resolve({ ok: false, errors: check.errors });
    }

    var JSZipCtor = options.JSZip;
    if (typeof JSZipCtor !== "function") {
      return Promise.resolve({ ok: false, errors: ["JSZip ist nicht geladen (designer/vendor/jszip.min.js fehlt oder wurde nicht eingebunden)."] });
    }

    var entries = buildZipEntries(project);
    var zip = new JSZipCtor();
    entries.forEach(function (entry) {
      // createFolders: false -- keine impliziten Verzeichniseintraege (JSZip
      // legt sonst pro Pfadsegment einen eigenen "Ordner"-Eintrag an). Das
      // .vpkg soll ausschliesslich echte Datei-Eintraege enthalten, genau wie
      // cli/lib/zip-writer.js es von Hand baut ("Kein Eintrag ist ein
      // Verzeichnis").
      zip.file(entry.name, entry.bytes, { createFolders: false });
    });

    return zip.generateAsync({ type: "uint8array", compression: "DEFLATE" }).then(function (zipBytes) {
      return sha256Hex(zipBytes).then(function (hex) {
        var sidecar = { sha256: hex };
        return {
          ok: true,
          errors: [],
          entries: entries,
          zipBytes: zipBytes,
          sha256Hex: hex,
          sidecarJson: JSON.stringify(sidecar, null, 2) + "\n"
        };
      });
    });
  }

  return {
    MANIFEST_ENTRY_NAME: MANIFEST_ENTRY_NAME,
    THEME_ENTRY_NAME: THEME_ENTRY_NAME,
    SIDECAR_SUFFIX: SIDECAR_SUFFIX,
    FORBIDDEN_EXTENSIONS: FORBIDDEN_EXTENSIONS,
    checkEntryPathSafety: checkEntryPathSafety,
    bytesFromDataUrl: bytesFromDataUrl,
    utf8Bytes: utf8Bytes,
    bytesToHex: bytesToHex,
    sha256Hex: sha256Hex,
    validateProjectForExport: validateProjectForExport,
    buildZipEntries: buildZipEntries,
    sidecarFileNameFor: sidecarFileNameFor,
    buildVpkgPackage: buildVpkgPackage
  };
});
