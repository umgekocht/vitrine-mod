package de.spassglas.vitrine.hud;

/**
 * Inhalt eines Text-Widgets oder eines Listeneintrags (Schema:
 * {@code $defs/textContent}): entweder ein fester Text ({@link Static}) oder
 * ein aus einer {@link HudDataSource} gelesener Wert ({@link Dynamic}).
 *
 * <p>{@code label} ist reiner, fester Praefix-Text -- kein Platzhalter, keine
 * Interpolation, keine Formel (Charter-Regel 3).</p>
 */
public sealed interface HudTextContent permits HudTextContent.Static, HudTextContent.Dynamic {

	/** {@code {"kind":"static","value":"..."}}. */
	record Static(String value) implements HudTextContent {
	}

	/**
	 * {@code {"kind":"dynamic","source":"fps","label":"FPS: ","decimals":0}}.
	 *
	 * @param label    fester Praefix vor dem Wert; {@code ""} wenn im JSON
	 *                 weggelassen (nie {@code null}).
	 * @param decimals Nachkommastellen bei numerischen Quellen, oder
	 *                 {@code null} wenn weggelassen -- dann greift
	 *                 {@link HudDataSource#defaultDecimals()}.
	 */
	record Dynamic(HudDataSource source, String label, Integer decimals) implements HudTextContent {
	}
}
