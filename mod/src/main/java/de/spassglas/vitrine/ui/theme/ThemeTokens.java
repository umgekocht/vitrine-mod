package de.spassglas.vitrine.ui.theme;

/**
 * Laufzeit-Zugriff auf ein aufgeloestes Theme-Token-Set (ROADMAP T2). Die
 * Token-Namen sind die kanonische, punktgetrennte Schreibweise aus
 * {@code registry/schema/theme.schema.json} (z. B. {@code "color.accent"},
 * {@code "card.width"}) -- dieselbe Datei, die der Designer schreibt
 * (Charter-Regel 8).
 *
 * <p>Zwei Zugriffsarten, weil die Schema-{@code $defs} zwei Wertformen kennen:
 * <ul>
 *   <li>{@link #size(String)} fuer {@code px}/{@code pxPositive}/{@code ms}
 *       sowie ganzzahlige Verhaeltnis-/Umbruchwerte -- ein roher {@code int}.</li>
 *   <li>{@link #color(String)} fuer {@code color} ({@code "#rrggbb"}) und
 *       {@code colorAlpha} ({@code {hex, alpha}}) -- immer als voll gepacktes
 *       ARGB ({@code 0xAARRGGBB}), damit der Aufrufer nicht zwischen
 *       deckend/durchscheinend unterscheiden muss.</li>
 * </ul>
 *
 * <p>Ein unbekannter Token-Name ist ein Programmierfehler und fuehrt zu einer
 * {@link IllegalArgumentException} -- nie stillschweigend {@code 0}/{@code null}
 * (das waere ein unsichtbarer Layoutfehler statt eines lauten).</p>
 */
public interface ThemeTokens {

	/**
	 * Ganzzahliger Wert eines Masz-/Dauer-/Verhaeltnis-Tokens.
	 *
	 * @throws IllegalArgumentException wenn {@code token} kein bekannter
	 *                                  Masz-Token des Schemas ist
	 */
	int size(String token);

	/**
	 * Voll gepacktes ARGB ({@code 0xAARRGGBB}) eines Farb-Tokens. Deckende
	 * {@code color}-Tokens liefern {@code 0xFF} im Alpha-Byte,
	 * {@code colorAlpha}-Tokens den gerundeten Alpha-Wert.
	 *
	 * @throws IllegalArgumentException wenn {@code token} kein bekannter
	 *                                  Farb-Token des Schemas ist
	 */
	int color(String token);
}
