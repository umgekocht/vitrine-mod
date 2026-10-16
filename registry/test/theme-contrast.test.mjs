// Test fuer registry/schema/examples/theme/valid/klarglas.json (ROADMAP T3).
//
// T3 "Fertig"-Kriterium: die Kontrastwerte des Standard-Themes "Klarglas"
// werden im Test NACHGERECHNET (>= 4.5:1 bzw. >= 3:1). Die Mod-Seite tut das
// fuer die DUNKLE Reihe bereits (mod/.../ui/theme/KlarglasContrastTest.java);
// dieser Test rechnet BEIDE Reihen (light UND dark) von klarglas.json nach,
// gibt jeden Wert per console.log aus und assertet die Schwellen.
//
// Die WCAG-2.1-Formel ist hier bewusst selbst implementiert (kein Paket,
// keine aus dem Netz kopierte Regex):
//   - sRGB-Kanal -> linear: cs/12.92           fuer cs <= 0.04045
//                           ((cs+0.055)/1.055)^2.4 sonst
//   - relative Luminanz L = 0.2126 R + 0.7152 G + 0.0722 B  (linear)
//   - Kontrast = (L_hell + 0.05) / (L_dunkel + 0.05)
//
// Farbwerte: "#rrggbb" (Solidfarbe) oder { hex, alpha } (durchscheinend).
// Ein { hex, alpha }-Wert wird ueber der jeweiligen color.bg der Reihe
// alpha-kompositet (Porter-Duff "over", Deckfarbe undurchsichtig). Die
// geprueften Paare unten sind allesamt Solid-auf-Solid -- die drei
// durchscheinenden Tokens (color.surfaceTranslucent / borderTranslucent /
// overlayScrim) sind Flaechen/Raender/Scrim, kein Text- oder CTA-Paar, und
// werden hier bewusst NICHT als Kontrastpaar gefuehrt. Der Kompositions-Pfad
// ist trotzdem implementiert und im Formel-Selbsttest mit abgedeckt.
//
// Aufruf: node --test registry/test/theme-contrast.test.mjs
// (vorher einmalig: npm install --prefix registry)

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const registryRoot = path.join(here, "..");
const themePath = path.join(
	registryRoot, "schema", "examples", "theme", "valid", "klarglas.json",
);
const theme = JSON.parse(readFileSync(themePath, "utf8"));

// --- WCAG-2.1-Formel, strukturell ------------------------------------------

/** "#rrggbb" -> { r, g, b } mit 0..255-Ganzzahlen. */
function parseHex(hex) {
	assert.match(hex, /^#[0-9a-fA-F]{6}$/, `kein #rrggbb: ${hex}`);
	return {
		r: parseInt(hex.slice(1, 3), 16),
		g: parseInt(hex.slice(3, 5), 16),
		b: parseInt(hex.slice(5, 7), 16),
	};
}

/** Ein 0..255-sRGB-Kanal auf seinen linearen Anteil (0..1) abbilden. */
function channelToLinear(value255) {
	const cs = value255 / 255;
	return cs <= 0.04045 ? cs / 12.92 : Math.pow((cs + 0.055) / 1.055, 2.4);
}

/** WCAG-2.1 relative Luminanz (0..1) eines { r, g, b }-Tripels. */
function relativeLuminance({ r, g, b }) {
	return (
		0.2126 * channelToLinear(r) +
		0.7152 * channelToLinear(g) +
		0.0722 * channelToLinear(b)
	);
}

/**
 * Loest einen Theme-Farbwert zu einem undurchsichtigen { r, g, b } auf.
 * - "#rrggbb": direkt.
 * - { hex, alpha }: ueber bgHex kompositet (result = fg*alpha + bg*(1-alpha)).
 */
function resolveColor(value, bgHex) {
	if (typeof value === "string") {
		return parseHex(value);
	}
	assert.ok(
		value && typeof value === "object" &&
		typeof value.hex === "string" && typeof value.alpha === "number",
		`unerwarteter Farbwert: ${JSON.stringify(value)}`,
	);
	const fg = parseHex(value.hex);
	const bg = parseHex(bgHex);
	const a = value.alpha;
	const mix = (f, b) => Math.round(f * a + b * (1 - a));
	return { r: mix(fg.r, bg.r), g: mix(fg.g, bg.g), b: mix(fg.b, bg.b) };
}

/** Kontrastverhaeltnis (1..21) zweier bereits aufgeloester { r, g, b }-Farben. */
function contrast(a, b) {
	const la = relativeLuminance(a);
	const lb = relativeLuminance(b);
	const hi = Math.max(la, lb);
	const lo = Math.min(la, lb);
	return (hi + 0.05) / (lo + 0.05);
}

/** Kontrast zwischen zwei Theme-Farbwerten; { hex, alpha } wird ueber bgHex gelegt. */
function ratio(fgValue, bgValue, bgHex) {
	return contrast(resolveColor(fgValue, bgHex), resolveColor(bgValue, bgHex));
}

// --- Formel-Selbsttest ----------------------------------------------------

test("Formel-Selbsttest: Schwarz/Weiss = 21:1, gleiche Farbe = 1:1", () => {
	const bw = ratio("#000000", "#ffffff", "#ffffff");
	console.log(`selbsttest schwarz/weiss = ${bw.toFixed(4)}:1`);
	assert.ok(Math.abs(bw - 21) < 1e-9, `erwartet 21, war ${bw}`);

	const same = ratio("#3a6dec", "#3a6dec", "#ffffff");
	console.log(`selbsttest gleiche Farbe = ${same.toFixed(4)}:1`);
	assert.equal(same, 1);

	// Alpha-Kompositions-Pfad: 100 % Deckkraft == Solidfarbe.
	const opaque = ratio({ hex: "#000000", alpha: 1 }, "#ffffff", "#ffffff");
	assert.ok(Math.abs(opaque - 21) < 1e-9, `erwartet 21, war ${opaque}`);
	// 0 % Deckkraft == reiner Hintergrund -> Kontrast 1:1 gegen denselben bg.
	const transparent = ratio({ hex: "#000000", alpha: 0 }, "#f3f3f4", "#f3f3f4");
	assert.equal(transparent, 1);
});

// --- Kontrastpaare je Reihe --------------------------------------------------

// Schwelle 4.5: WCAG-2.1 AA fuer normalen Text (docs/UI-SPEC.md "Barrierefreiheit").
// Schwelle 3.0: AA fuer Bedienelement-/Grafik-Kontrast (1.4.11). Der
// Fehlerzustand (color.danger) traegt laut ROADMAP S9 zusaetzlich Symbol + Text,
// haengt also nicht allein an der Farbe -> hier reicht die 3:1-Schwelle.
const PAIRS = [
	{ label: "text auf bg", fg: "color.text", bg: "color.bg", min: 4.5 },
	{ label: "text auf surfaceSolid", fg: "color.text", bg: "color.surfaceSolid", min: 4.5 },
	{ label: "textMuted auf bg", fg: "color.textMuted", bg: "color.bg", min: 4.5 },
	{ label: "accentContrast auf accent", fg: "color.accentContrast", bg: "color.accent", min: 4.5 },
	{ label: "danger auf bg", fg: "color.danger", bg: "color.bg", min: 3.0 },
	{ label: "disabledText auf bg", fg: "color.disabledText", bg: "color.bg", min: 3.0 },
	{ label: "focus.ring.color auf bg", fg: "focus.ring.color", bg: "color.bg", min: 3.0 },
];

for (const row of ["light", "dark"]) {
	test(`Klarglas ${row}: alle Kontrastpaare halten ihre Schwelle`, () => {
		const variant = theme[row];
		assert.ok(variant, `klarglas.json hat keine Reihe "${row}"`);
		const bgHex = variant["color.bg"];

		for (const { label, fg, bg, min } of PAIRS) {
			const value = ratio(variant[fg], variant[bg], bgHex);
			console.log(
				`${row.padEnd(5)} ${label.padEnd(28)} ${value.toFixed(4)}:1  (>= ${min})`,
			);
			assert.ok(
				value >= min,
				`${row}/${label}: ${value.toFixed(4)}:1 unter ${min}:1`,
			);
		}
	});
}
