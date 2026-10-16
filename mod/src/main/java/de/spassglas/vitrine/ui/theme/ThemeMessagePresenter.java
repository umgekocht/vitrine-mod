package de.spassglas.vitrine.ui.theme;

/**
 * Abstraktion ueber die Klartext-Meldung aus dem Fertig-Kriterium von T2
 * ("kaputtes Theme &rArr; Standard + Meldung"). Getrennt von Minecrafts
 * {@code SystemToast}, damit {@link ThemeManager} in Tests ohne Rendering und
 * ohne laufenden Client geprueft werden kann -- gleiches Muster wie
 * {@code de.spassglas.vitrine.hotapply.HotApplyToastPresenter}.
 */
public interface ThemeMessagePresenter {

	/**
	 * Ein Theme liesz sich nicht laden (kein gueltiges JSON oder Schema-
	 * Verstosz); das bisher aktive Theme bleibt in Kraft.
	 *
	 * @param themeName menschenlesbarer Name/Bezeichner des abgelehnten Themes
	 * @param reason    Klartext-Grund (erste Schema-/Parse-Meldung)
	 */
	void showBrokenThemeKeptCurrent(String themeName, String reason);
}
