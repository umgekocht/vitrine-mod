#!/usr/bin/env node
"use strict";

/*
 * Erzeugt die eine Platzhalter-Textur, die im Vorlagen-Repo
 * (package-repo/assets/minecraft/textures/item/example_placeholder.png)
 * mitgeliefert wird (ROADMAP D8).
 *
 * Charter-Regel 6 ("Keine fremden Assets"): dieses PNG ist keine kopierte
 * Grafik, sondern eine 16x16-Flaeche aus einer einzigen, hier im Code fest
 * eingetragenen Graustufe -- mit Node-Bordmitteln (node:zlib fuer die
 * IDAT-Kompression, eine kleine handgeschriebene CRC-32-Tabelle fuer die
 * PNG-Chunk-Pruefsummen) erzeugt, keine neue Abhaengigkeit. Zweck: zeigen,
 * WO im .vpkg eine Ressource liegt (Vanilla-Resource-Pack-Layout
 * 'assets/<namespace>/textures/...', siehe mod/VpkgResourcePack.java), nicht
 * WIE eine echte Textur aussieht. Paket-Autoren ersetzen die Datei durch ihre
 * eigene Grafik, bevor sie etwas Ernstes veroeffentlichen (siehe
 * package-repo/README.md).
 *
 * Aufruf: node tools/generate-placeholder-texture.mjs
 * (idempotent -- erzeugt bei jedem Lauf byte-identische Ausgabe.)
 */

import { deflateSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT_PATH = path.join(
	here,
	"..",
	"package-repo",
	"assets",
	"minecraft",
	"textures",
	"item",
	"example_placeholder.png",
);

const WIDTH = 16;
const HEIGHT = 16;
// Eine einzige mittelgraue Flaeche -- neutral, aus keinem existierenden Werk
// abgeleitet, absichtlich langweilig genug, dass niemand sie mit einer
// echten Textur verwechselt.
const GRAY = 0x88;

function crcTable() {
	const table = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) {
			c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		}
		table[n] = c >>> 0;
	}
	return table;
}
const CRC_TABLE = crcTable();

function crc32(buf) {
	let crc = 0xffffffff;
	for (let i = 0; i < buf.length; i++) {
		crc = CRC_TABLE[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
	}
	return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
	const typeBuf = Buffer.from(type, "ascii");
	const lengthBuf = Buffer.alloc(4);
	lengthBuf.writeUInt32BE(data.length, 0);
	const crcBuf = Buffer.alloc(4);
	crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
	return Buffer.concat([lengthBuf, typeBuf, data, crcBuf]);
}

function buildPng() {
	const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

	const ihdrData = Buffer.alloc(13);
	ihdrData.writeUInt32BE(WIDTH, 0);
	ihdrData.writeUInt32BE(HEIGHT, 4);
	ihdrData[8] = 8; // Bittiefe
	ihdrData[9] = 2; // Farbtyp 2 = RGB (Truecolor, kein Alpha)
	ihdrData[10] = 0; // Kompressionsmethode
	ihdrData[11] = 0; // Filtermethode
	ihdrData[12] = 0; // kein Interlace

	// Rohdaten: pro Zeile ein Filterbyte (0 = None) + WIDTH*3 Pixelbytes.
	const raw = Buffer.alloc(HEIGHT * (1 + WIDTH * 3));
	let offset = 0;
	for (let y = 0; y < HEIGHT; y++) {
		raw[offset++] = 0; // Filtertyp None
		for (let x = 0; x < WIDTH; x++) {
			raw[offset++] = GRAY;
			raw[offset++] = GRAY;
			raw[offset++] = GRAY;
		}
	}
	const idatData = deflateSync(raw);

	return Buffer.concat([
		signature,
		chunk("IHDR", ihdrData),
		chunk("IDAT", idatData),
		chunk("IEND", Buffer.alloc(0)),
	]);
}

const png = buildPng();
writeFileSync(OUTPUT_PATH, png);
process.stdout.write(`Platzhalter-Textur geschrieben: ${OUTPUT_PATH} (${png.length} Bytes)\n`);
