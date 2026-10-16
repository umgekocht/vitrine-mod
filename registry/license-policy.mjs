// Automatisierte Pruefung des `license`-Felds gegen LICENSE-WHITELIST.md
// (GATE23). Wird vom Index-Builder (build-index.js -> loadPackages) benutzt
// -- dieselbe Funktion laeuft damit sowohl beim Index-Bau als auch bei der
// PR-Pruefung (validate-changed-packages.mjs importiert buildIndex fuer
// ihren zweiten, vollstaendigen Pruefdurchlauf).
//
// Deckt die kurzen, maschinell erkennbaren Formen aus der "Erlaubt"-Tabelle
// von LICENSE-WHITELIST.md ab (CC0/CC-BY/CC-BY-SA/MIT/Apache-2.0/Unlicense).
// Die dort ebenfalls erlaubte Kategorie "Eigene, offene 'freie Nutzung'-
// Erklaerung" ist per Definition Freitext und laesst sich nicht generisch
// gegenpruefen -- dafuer gibt es CUSTOM_ALLOWED_LICENSES unten: ein Autor mit
// einer echten freien Sonderlizenz braucht dafuer einen expliziten Eintrag
// hier, der selbst durch PR-Review geht (derselbe Mechanismus wie jede andere
// Aenderung an diesem Repo) -- kein automatischer Blanko-Freifahrtschein fuer
// beliebigen Freitext.
//
// Explizit abgelehnt (siehe "Nicht erlaubt" in LICENSE-WHITELIST.md): leere
// Werte, "Alle Rechte vorbehalten"/"All Rights Reserved", Non-Commercial-
// CC-Varianten (CC-BY-NC*), "nur fuer Vitrine"-Sonderlizenzen (erkennbar an
// den Stichworten "nur fuer vitrine"/"exklusiv").

/** Bekannte, whitelistete Kurzformen (case-insensitive). */
const ALLOWED_EXACT = new Set(["cc0-1.0", "mit", "apache-2.0", "unlicense"]);

const ALLOWED_PATTERNS = [
	/^cc-by-(2\.0|2\.5|3\.0|4\.0)$/i,
	/^cc-by-sa-(2\.0|2\.5|3\.0|4\.0)$/i,
];

/**
 * Freie Sonderlizenzen, die manuell (per PR-Review dieser Datei) auf die
 * Whitelist genommen wurden -- siehe Kommentar oben. Leer, solange kein
 * Autor eine solche Lizenz eingereicht hat.
 * @type {string[]}
 */
export const CUSTOM_ALLOWED_LICENSES = [];

const DENY_PATTERNS = [
	/all rights reserved/i,
	/alle rechte vorbehalten/i,
	/^cc-by(-sa)?-nc/i, // Non-Commercial-Varianten
	/nur f[uü]r vitrine/i,
	/only for vitrine/i,
	/proprietary/i,
];

/**
 * Prueft einen `license`-Wert gegen die Whitelist. Gibt bei Ablehnung einen
 * Klartext-Grund zurueck, sonst `null`.
 * @param {string} license
 * @returns {string | null}
 */
export function licenseWhitelistViolation(license) {
	if (typeof license !== "string" || license.trim().length === 0) {
		return "license fehlt oder ist leer";
	}
	const trimmed = license.trim();

	for (const pattern of DENY_PATTERNS) {
		if (pattern.test(trimmed)) {
			return `license '${license}' steht ausdruecklich auf der Verbotsliste (siehe LICENSE-WHITELIST.md, Abschnitt "Nicht erlaubt")`;
		}
	}

	const lower = trimmed.toLowerCase();
	if (ALLOWED_EXACT.has(lower)) return null;
	if (ALLOWED_PATTERNS.some((p) => p.test(trimmed))) return null;
	if (CUSTOM_ALLOWED_LICENSES.some((allowed) => allowed.toLowerCase() === lower)) return null;

	return (
		`license '${license}' steht nicht auf der Whitelist (registry/LICENSE-WHITELIST.md). ` +
		`Erlaubt: CC0-1.0, CC-BY-<2.0|2.5|3.0|4.0>, CC-BY-SA-<2.0|2.5|3.0|4.0>, MIT, Apache-2.0, Unlicense, ` +
		`oder ein Eintrag in registry/license-policy.mjs (CUSTOM_ALLOWED_LICENSES) fuer eine echte freie Sonderlizenz.`
	);
}
