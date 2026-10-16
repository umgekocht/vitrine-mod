"use strict";

/*
 * Vitrine CLI -- ZIP-Bau ausschliesslich mit Node-Bordmitteln (node:zlib fuer
 * DEFLATE, eine selbst geschriebene CRC-32-Tabelle -- reine Mathematik, keine
 * fremde Bibliothek noetig). Bewusst gegen eine neue npm-Abhaengigkeit
 * entschieden: eine Zip-Bibliothek muesste Charter-Regel 5 (Lizenz an der
 * Quelle geprueft) neu durchlaufen, obwohl das PKZIP-APPNOTE-Format klein
 * genug ist, um es direkt zu schreiben -- und `mod/.../VpkgFixtures.java`
 * zeigt bereits, dass ein von Hand gebautes ZIP fuer .vpkg reicht.
 *
 * Format-Entscheidungen, damit das Ergebnis VpkgSecurityValidator besteht
 * (siehe mod/.../VpkgSecurityValidator.java, VpkgZipCentralDirectoryReader.java):
 *   - "version made by" traegt Host-System 0 (MS-DOS/FAT), niemals 3 (Unix) --
 *     VpkgZipCentralDirectoryReader interpretiert "external file attributes"
 *     nur bei Host-System Unix als Symlink-Kennung. Mit Host 0 ist das Feld
 *     fuer diese Pruefung irrelevant, deshalb bleibt es zusaetzlich auf 0.
 *   - Kein Eintrag ist ein Verzeichnis, kein Eintragsname beginnt mit '/'
 *     oder enthaelt ein '..'-Segment (das erledigt bereits der Aufrufer,
 *     collectEntries() in cli/lib/pack.js, durch simples Rekursiv-Einlesen
 *     eines echten Ordners -- ein Name, der nicht im Dateisystem existiert,
 *     kann hier gar nicht erst ankommen).
 *   - DEFLATE nur, wenn es tatsaechlich kleiner wird, sonst STORE -- damit
 *     das Kompressionsverhaeltnis nie unplausibel wird (Java-Grenze 100:1).
 */

const zlib = require("node:zlib");

const CRC_TABLE = buildCrcTable();

function buildCrcTable() {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[n] = c >>> 0;
  }
  return table;
}

/** CRC-32 (ISO 3309 / PKZIP) einer Buffer-Instanz. */
function crc32(buffer) {
  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buffer.length; i++) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xFF] ^ (crc >>> 8);
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

const DOS_TIME = 0; // 00:00:00
const DOS_DATE = 0x21; // 1.1.1980 -- kleinstes gueltiges DOS-Datum, wie in VpkgFixtures.java

/**
 * Baut ein vollstaendiges ZIP-Archiv (als Buffer) aus einer Liste von
 * Eintraegen {name, data}. `name` muss bereits ein sicherer, relativer,
 * Slash-getrennter Pfad sein -- diese Funktion prueft das selbst nicht (siehe
 * Klassendoku), sie ist reines Bauwerkzeug.
 *
 * @param {{name: string, data: Buffer}[]} entries
 * @returns {Buffer}
 */
function buildZip(entries) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBytes = Buffer.from(entry.name, "utf8");
    const data = entry.data;
    const crc = crc32(data);
    const deflated = zlib.deflateRawSync(data, { level: 9 });

    let method;
    let storedData;
    if (deflated.length < data.length) {
      method = 8; // DEFLATE
      storedData = deflated;
    } else {
      method = 0; // STORE
      storedData = data;
    }

    const lfh = Buffer.alloc(30);
    lfh.writeUInt32LE(0x04034b50, 0);
    lfh.writeUInt16LE(20, 4); // version needed to extract
    lfh.writeUInt16LE(0, 6); // general purpose bit flag
    lfh.writeUInt16LE(method, 8);
    lfh.writeUInt16LE(DOS_TIME, 10);
    lfh.writeUInt16LE(DOS_DATE, 12);
    lfh.writeUInt32LE(crc, 14);
    lfh.writeUInt32LE(storedData.length, 18);
    lfh.writeUInt32LE(data.length, 22);
    lfh.writeUInt16LE(nameBytes.length, 26);
    lfh.writeUInt16LE(0, 28); // extra field length

    localParts.push(lfh, nameBytes, storedData);

    const cdh = Buffer.alloc(46);
    cdh.writeUInt32LE(0x02014b50, 0);
    cdh.writeUInt16LE(20, 4); // version made by (host system 0 = MS-DOS/FAT, siehe Klassendoku)
    cdh.writeUInt16LE(20, 6); // version needed to extract
    cdh.writeUInt16LE(0, 8); // general purpose bit flag
    cdh.writeUInt16LE(method, 10);
    cdh.writeUInt16LE(DOS_TIME, 12);
    cdh.writeUInt16LE(DOS_DATE, 14);
    cdh.writeUInt32LE(crc, 16);
    cdh.writeUInt32LE(storedData.length, 20);
    cdh.writeUInt32LE(data.length, 24);
    cdh.writeUInt16LE(nameBytes.length, 28);
    cdh.writeUInt16LE(0, 30); // extra field length
    cdh.writeUInt16LE(0, 32); // file comment length
    cdh.writeUInt16LE(0, 34); // disk number start
    cdh.writeUInt16LE(0, 36); // internal file attributes
    cdh.writeUInt32LE(0, 38); // external file attributes -- bewusst 0, kein Unix-Modus
    cdh.writeUInt32LE(offset, 42); // relative offset of local header

    centralParts.push(cdh, nameBytes);

    offset += lfh.length + nameBytes.length + storedData.length;
  }

  const centralDirectoryOffset = offset;
  const centralDirectorySize = centralParts.reduce((sum, part) => sum + part.length, 0);

  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4); // number of this disk
  eocd.writeUInt16LE(0, 6); // disk where central directory starts
  eocd.writeUInt16LE(entries.length, 8); // central directory records on this disk
  eocd.writeUInt16LE(entries.length, 10); // total central directory records
  eocd.writeUInt32LE(centralDirectorySize, 12);
  eocd.writeUInt32LE(centralDirectoryOffset, 16);
  eocd.writeUInt16LE(0, 20); // comment length

  return Buffer.concat([...localParts, ...centralParts, eocd]);
}

module.exports = { buildZip, crc32 };
