package de.spassglas.vitrine.ui.theme;

/**
 * Die beiden Farbvarianten, die {@code registry/schema/theme.schema.json} als
 * Pflichtfelder {@code light} und {@code dark} fuehrt. Der {@link ThemeManager}
 * loest Tokens gegen genau eine davon auf.
 *
 * <p>Vorgabe ist {@link #DARK}: der Store liegt als durchscheinende Flaeche
 * ueber dem (meist dunklen) Spielhintergrund, und die aktuellen
 * {@link VitrineTheme}-Konstanten sind bereits die dunkle "Klarglas"-Variante
 * (siehe deren Klassendoku). Eine echte hell/dunkel-Umschaltung im laufenden
 * Client ist Sache einer spaeteren Zeile -- hier ist nur festgelegt, welche
 * Seite eines geladenen Theme-JSON gelesen wird.</p>
 */
public enum ThemeVariant {

	LIGHT("light"),
	DARK("dark");

	private final String jsonKey;

	ThemeVariant(String jsonKey) {
		this.jsonKey = jsonKey;
	}

	/** Feldname dieser Variante im Theme-JSON ({@code "light"} bzw. {@code "dark"}). */
	public String jsonKey() {
		return jsonKey;
	}
}
