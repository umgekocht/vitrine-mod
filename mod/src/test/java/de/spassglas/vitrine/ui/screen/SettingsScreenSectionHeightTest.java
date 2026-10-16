package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.theme.DefaultThemeTokens;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * ROADMAP GATE53-Beweis: {@code SettingsScreen.SECTION_HEIGHT} war ein {@code
 * private static final int}, einmalig beim Klassenladen aus {@link
 * VitrineTheme#TYPE_SUBTITLE}/{@link VitrineTheme#DETAIL_HEADING_GAP}/{@link
 * VitrineTheme#MENU_BUTTON_HEIGHT}/{@link VitrineTheme#TYPE_CAPTION}
 * berechnet. Diese Felder sind seit GATE21 zur Laufzeit veraenderlich ({@link
 * VitrineTheme#applyFrom}), aber der gecachte Wert wurde nie neu berechnet --
 * ein Theme-Wechsel aenderte den Abschnittsabstand im Einstellungen-Bildschirm
 * nicht mehr sichtbar. Dieser Test wechselt {@code menu.button.height} zur
 * Laufzeit und beweist, dass sich die obere Kante des naechsten Abschnitts
 * ({@code SettingsScreen#sectionTopForTest}, dieselbe Formel wie
 * {@code #init()} tatsaechlich benutzt) sofort entsprechend verschiebt.
 */
public final class SettingsScreenSectionHeightTest {

	@AfterMethod
	public void restoreDefaultTheme() {
		VitrineTheme.applyFrom(DefaultThemeTokens.INSTANCE::size);
	}

	@Test
	public void sectionTopRecalculatesAfterAThemeSwitchInsteadOfStayingCached() {
		int before = SettingsScreen.sectionTopForTest(1);

		int originalMenuButtonHeight = VitrineTheme.MENU_BUTTON_HEIGHT;
		VitrineTheme.applyFrom(token -> "menu.button.height".equals(token)
				? originalMenuButtonHeight * 2
				: DefaultThemeTokens.INSTANCE.size(token));

		int after = SettingsScreen.sectionTopForTest(1);

		assertNotEquals(after, before,
				"ein Theme-Wechsel von menu.button.height muss den Abschnittsabstand sofort sichtbar aendern "
						+ "(vorher: SECTION_HEIGHT war beim Klassenladen gecacht)");
		int expectedSectionHeight = VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.DETAIL_HEADING_GAP
				+ VitrineTheme.TYPE_CAPTION;
		int expectedTop = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE
				+ 1 * (expectedSectionHeight + VitrineTheme.DETAIL_SECTION_GAP);
		assertEquals(after, expectedTop, "die neue obere Kante muss exakt aus den aktuellen Theme-Werten folgen");
	}
}
