"use strict";

/*
 * Vitrine Designer -- D4 Texture-Studio-Logik.
 *
 * Kein DOM-Zugriff hier (wie theme-editor.js/theme-wiring.js) -- unter Node
 * direkt testbar (designer/test/texture-studio.test.js) und im Browser per
 * <script> eingebunden (kein type="module", kein fetch(), siehe
 * app.js-Kopfkommentar aus D1). app.js liest eine ausgewaehlte Datei ueber
 * FileReader.readAsArrayBuffer() und reicht die rohen Bytes hierher weiter --
 * alles Parsen/Pruefen/Kodieren passiert in reinen Funktionen, die genau die
 * gleichen Bytes bekommen koennten, die der Mod beim Entpacken sieht.
 *
 * Zustaendigkeiten:
 *  - readPngHeader(bytes): liest PNG-Signatur + IHDR-Breite/-Hoehe direkt aus
 *    den ersten Bytes, OHNE das Bild zu dekodieren -- bewusst dieselbe
 *    Herleitung wie mod/.../vpkg/VpkgSecurityValidator#checkPngDimensions
 *    (Signatur pruefen, IHDR-Chunk-Typ pruefen, Breite/Hoehe als
 *    Big-Endian-int32 an Offset 16/20 lesen). Es gibt dafuer KEIN gemeinsames
 *    Schema unter registry/schema/ (Java und JS teilen sich keine Datei) --
 *    aendert sich diese Herleitung im Mod, muss sie hier von Hand
 *    nachgezogen werden, deshalb der Verweis oben.
 *  - MAX_PNG_DIMENSION: dieselbe harte P3-Sicherheitsgrenze (4096x4096) wie
 *    VpkgSecurityValidator.MAX_PNG_DIMENSION, hier als einzige benannte
 *    Konstante (keine verstreute "4096" im Code) und bereits im Designer
 *    erzwungen -- ein zu grosses Bild wird abgelehnt, BEVOR es ueberhaupt im
 *    Projekt landet, nicht erst beim spaeteren Packen (D6).
 *  - validateDimensions/powerOfTwoWarning: harte Ablehnung vs. optionaler
 *    Hinweis (Zweierpotenz ist ueblich fuer Vanilla-Texturen, aber keine
 *    Pflicht).
 *  - validateTargetPath: der Vanilla-Zielpfad, dem eine Textur zugeordnet
 *    wird (z. B. "textures/block/stone.png") -- freie Eingabe, aber ohne
 *    absoluten Pfad/'..'  (dieselbe Vorsicht wie beim "icon"/"screenshots"-
 *    Pfadmuster in vitrine.schema.json, hier aber ein eigenes, kleineres
 *    Muster, weil Textur-Zielpfade kein Feld dieses Schemas sind).
 *  - ensureTextures/upsertTexture/removeTexture: project.textures[] pflegen,
 *    ein Eintrag pro targetPath (ein zweiter Upload auf denselben Zielpfad
 *    ersetzt den ersten statt zu duplizieren).
 *  - bytesToBase64/dataUrlFromPngBytes: eigene Base64-Kodierung (kein
 *    Browser-btoa noetig) fuer die "Nachher"-Vorschau (<img src="data:...">)
 *    und die Speicherung im Projekt (localStorage) -- keine neue
 *    Abhaengigkeit, rein arithmetisch.
 *  - buildTextureEntry: buendelt alle Pruefungen zu EINEM Aufruf, den app.js
 *    beim Absenden des Zuordnen-Formulars macht.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.VitrineTextureStudio = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {

  // --- PNG-Kopf lesen (siehe Kopfkommentar: gleiche Herleitung wie im Mod) ---

  var PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

  /** Signatur(8) + Laenge(4) + Chunk-Typ(4) + Breite(4) + Hoehe(4). */
  var PNG_HEADER_PREFIX_LENGTH = 24;

  /**
   * Maximale PNG-Breite/-Hoehe in Pixeln -- dieselbe P3-Sicherheitsgrenze wie
   * VpkgSecurityValidator.MAX_PNG_DIMENSION (siehe Kopfkommentar). Einzige
   * Stelle im Designer, an der dieser Wert steht.
   */
  var MAX_PNG_DIMENSION = 4096;

  function readInt32BE(bytes, offset) {
    return (((bytes[offset] << 24) |
      (bytes[offset + 1] << 16) |
      (bytes[offset + 2] << 8) |
      bytes[offset + 3]) >>> 0);
  }

  /** Rohe Datei-Bytes -> { valid: true, width, height } oder { valid: false, error }. Liest nie mehr als die ersten 24 Bytes aus. */
  function readPngHeader(bytes) {
    if (!bytes || typeof bytes.length !== "number" || bytes.length < PNG_HEADER_PREFIX_LENGTH) {
      return { valid: false, error: "Datei ist zu kurz fuer einen gueltigen PNG-Kopf." };
    }
    for (var i = 0; i < PNG_SIGNATURE.length; i++) {
      if (bytes[i] !== PNG_SIGNATURE[i]) {
        return { valid: false, error: "Datei hat keine gueltige PNG-Signatur -- nur echte PNG-Dateien werden akzeptiert." };
      }
    }
    // Chunk-Typ an Offset 12..15 muss "IHDR" sein (0x49 0x48 0x44 0x52).
    if (bytes[12] !== 0x49 || bytes[13] !== 0x48 || bytes[14] !== 0x44 || bytes[15] !== 0x52) {
      return { valid: false, error: "PNG hat kein IHDR als ersten Chunk -- Masse nicht pruefbar." };
    }
    var width = readInt32BE(bytes, 16);
    var height = readInt32BE(bytes, 20);
    if (width <= 0 || height <= 0) {
      return { valid: false, error: "PNG nennt unplausible Masse " + width + "x" + height + "." };
    }
    return { valid: true, width: width, height: height };
  }

  /** Harte P3-Grenze: Bilder ueber MAX_PNG_DIMENSION werden abgelehnt, nicht stillschweigend geladen. */
  function validateDimensions(width, height) {
    if (width > MAX_PNG_DIMENSION || height > MAX_PNG_DIMENSION) {
      return {
        valid: false,
        message: "Textur ist " + width + "x" + height + " Pixel, erlaubt sind hoechstens " +
          MAX_PNG_DIMENSION + "x" + MAX_PNG_DIMENSION + " Pixel (Sicherheitsgrenze)."
      };
    }
    return { valid: true, message: "" };
  }

  function isPowerOfTwo(n) {
    return typeof n === "number" && isFinite(n) && n > 0 && (n & (n - 1)) === 0;
  }

  /** Rein informativer Hinweis (keine Ablehnung) -- null, wenn beide Masse Zweierpotenzen sind. */
  function powerOfTwoWarning(width, height) {
    if (isPowerOfTwo(width) && isPowerOfTwo(height)) {
      return null;
    }
    return "Hinweis: " + width + "x" + height + " ist keine Zweierpotenz -- funktioniert meist trotzdem, ist aber unueblich fuer Vanilla-Texturen.";
  }

  // --- Zielpfad ---

  // Kein absoluter Pfad, kein "..", muss auf ".png" enden -- angelehnt an das
  // icon/screenshots-Pfadmuster in vitrine.schema.json, aber ein eigenes,
  // kleineres Muster (Textur-Zielpfade sind kein Feld dieses Schemas).
  var TARGET_PATH_RE = /^[A-Za-z0-9_./-]+\.png$/;

  function validateTargetPath(path) {
    var trimmed = typeof path === "string" ? path.trim() : "";
    if (trimmed.length === 0) {
      return { valid: false, message: "Bitte einen Ziel-Pfad angeben, z. B. \"textures/block/stone.png\"." };
    }
    if (trimmed.charAt(0) === "/" || trimmed.charAt(0) === "\\") {
      return { valid: false, message: "Ziel-Pfad darf nicht mit '/' beginnen (relativ zum Ressourcen-Stamm)." };
    }
    if (trimmed.indexOf("..") !== -1) {
      return { valid: false, message: "Ziel-Pfad darf kein '..' enthalten." };
    }
    if (trimmed.length > 256) {
      return { valid: false, message: "Ziel-Pfad ist zu lang (hoechstens 256 Zeichen)." };
    }
    if (!TARGET_PATH_RE.test(trimmed)) {
      return { valid: false, message: "Ziel-Pfad muss auf '.png' enden und darf nur Buchstaben, Ziffern, '_', '-', '.', '/' enthalten." };
    }
    return { valid: true, message: "" };
  }

  // --- project.textures[] pflegen ---

  /** project.textures (roh, evtl. fehlend/kaputt) -> Array (Kopie, nie die Originalreferenz). */
  function ensureTextures(texturesField) {
    return Array.isArray(texturesField) ? texturesField.slice() : [];
  }

  /** Fuegt "entry" ein oder ersetzt den bestehenden Eintrag mit demselben targetPath. Gibt ein NEUES Array zurueck. */
  function upsertTexture(texturesField, entry) {
    var list = ensureTextures(texturesField);
    var next = list.slice();
    var idx = -1;
    for (var i = 0; i < next.length; i++) {
      if (next[i] && next[i].targetPath === entry.targetPath) {
        idx = i;
        break;
      }
    }
    if (idx === -1) {
      next.push(entry);
    } else {
      next[idx] = entry;
    }
    return next;
  }

  /** Entfernt den Eintrag mit targetPath. Gibt ein NEUES Array zurueck. */
  function removeTexture(texturesField, targetPath) {
    return ensureTextures(texturesField).filter(function (t) {
      return !t || t.targetPath !== targetPath;
    });
  }

  // --- Base64 / data:-URL (keine Browser-API noetig, deshalb unter Node testbar) ---

  var BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  function bytesToBase64(bytes) {
    var out = "";
    var i = 0;
    for (; i + 2 < bytes.length; i += 3) {
      var chunk = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
      out += BASE64_CHARS.charAt((chunk >> 18) & 63);
      out += BASE64_CHARS.charAt((chunk >> 12) & 63);
      out += BASE64_CHARS.charAt((chunk >> 6) & 63);
      out += BASE64_CHARS.charAt(chunk & 63);
    }
    var remaining = bytes.length - i;
    if (remaining === 1) {
      var chunk1 = bytes[i] << 16;
      out += BASE64_CHARS.charAt((chunk1 >> 18) & 63);
      out += BASE64_CHARS.charAt((chunk1 >> 12) & 63);
      out += "==";
    } else if (remaining === 2) {
      var chunk2 = (bytes[i] << 16) | (bytes[i + 1] << 8);
      out += BASE64_CHARS.charAt((chunk2 >> 18) & 63);
      out += BASE64_CHARS.charAt((chunk2 >> 12) & 63);
      out += BASE64_CHARS.charAt((chunk2 >> 6) & 63);
      out += "=";
    }
    return out;
  }

  function dataUrlFromPngBytes(bytes) {
    return "data:image/png;base64," + bytesToBase64(bytes);
  }

  // --- Alles zusammen: ein Aufruf pro "Zuordnen"-Absenden ---

  /**
   * params: { targetPath: string, fileName: string, bytes: Uint8Array|number[] }.
   * Liefert { ok: true, entry, warning } oder { ok: false, errors: string[] }.
   * "entry" hat exakt die Form, die im Projekt gespeichert wird:
   *   { targetPath, fileName, dataUrl, width, height }.
   */
  function buildTextureEntry(params) {
    params = params || {};
    var errors = [];
    var header = readPngHeader(params.bytes);
    if (!header.valid) {
      errors.push(header.error);
    } else {
      var dimCheck = validateDimensions(header.width, header.height);
      if (!dimCheck.valid) {
        errors.push(dimCheck.message);
      }
    }
    var pathCheck = validateTargetPath(params.targetPath);
    if (!pathCheck.valid) {
      errors.push(pathCheck.message);
    }
    if (errors.length > 0) {
      return { ok: false, errors: errors };
    }
    return {
      ok: true,
      errors: [],
      warning: powerOfTwoWarning(header.width, header.height),
      entry: {
        targetPath: params.targetPath.trim(),
        fileName: typeof params.fileName === "string" ? params.fileName : "",
        dataUrl: dataUrlFromPngBytes(params.bytes),
        width: header.width,
        height: header.height
      }
    };
  }

  return {
    MAX_PNG_DIMENSION: MAX_PNG_DIMENSION,
    readPngHeader: readPngHeader,
    validateDimensions: validateDimensions,
    isPowerOfTwo: isPowerOfTwo,
    powerOfTwoWarning: powerOfTwoWarning,
    validateTargetPath: validateTargetPath,
    ensureTextures: ensureTextures,
    upsertTexture: upsertTexture,
    removeTexture: removeTexture,
    bytesToBase64: bytesToBase64,
    dataUrlFromPngBytes: dataUrlFromPngBytes,
    buildTextureEntry: buildTextureEntry
  };
});
