package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP-GATE14-Beweis: die Beschriftung einer Kachelreihe folgt ihrem
 * tatsaechlichen Inhalt. Vorher lief die Hauptreihe der Startseite ueber einen
 * Konstruktor ohne Keys und trug deshalb <em>immer</em> "Beispielkacheln" /
 * "Platzhalter-Reihe" -- auch wenn echte Registry-Pakete darin standen.
 */
public final class TileRowLabelsTest {

	private static final List<String> LOCALES = List.of("de_de", "en_us");

	@Test
	public void realCatalogRowNeverUsesTheDemoLabels() {
		TileRowLabels real = TileRowLabels.forStartRow(true);

		assertEquals(real, TileRowLabels.catalogRow());
		assertNotEquals(real.titleKey(), TileRowLabels.demoRow().titleKey());
		assertNotEquals(real.subtitleKey(), TileRowLabels.demoRow().subtitleKey());
	}

	@Test
	public void demoRowKeepsTheDemoLabels() {
		assertEquals(TileRowLabels.forStartRow(false), TileRowLabels.demoRow());
	}

	@Test
	public void cachedCatalogRowIsMarkedAsCatalogButDistinguishableFromTheLiveOne() {
		TileRowLabels cached = TileRowLabels.cachedCatalogRow();

		assertNotEquals(cached, TileRowLabels.demoRow());
		assertNotEquals(cached, TileRowLabels.catalogRow());
		for (String locale : LOCALES) {
			assertNotEquals(Translations.get(locale, cached.titleKey()),
					Translations.get(locale, TileRowLabels.catalogRow().titleKey()),
					locale + ": gecachter und frischer Katalog muessen sich sichtbar unterscheiden");
		}
	}

	/** Jede Beschriftung existiert in beiden Sprachen und ist dort nicht leer. */
	@Test
	public void everyLabelKeyIsTranslatedInBothLocales() {
		for (TileRowLabels labels : List.of(TileRowLabels.catalogRow(), TileRowLabels.cachedCatalogRow(),
				TileRowLabels.demoRow())) {
			for (String key : List.of(labels.titleKey(), labels.subtitleKey())) {
				for (String locale : LOCALES) {
					String text = Translations.get(locale, key);
					assertNotEquals(text, key, locale + ": '" + key + "' fehlt in der Sprachdatei");
					assertFalse(text.isBlank(), locale + ": '" + key + "' ist leer");
				}
			}
		}
	}

	/**
	 * Die Kernaussage von GATE14 im Text selbst: nur die Demo-Reihe darf sich
	 * als Beispiel/Platzhalter bezeichnen.
	 */
	@Test
	public void onlyTheDemoRowCallsItselfASampleOrPlaceholder() {
		List<String> demoWords = List.of("beispiel", "platzhalter", "sample", "placeholder");

		for (String locale : LOCALES) {
			String demoText = (Translations.get(locale, TileRowLabels.demoRow().titleKey()) + " "
					+ Translations.get(locale, TileRowLabels.demoRow().subtitleKey())).toLowerCase();
			assertTrue(demoWords.stream().anyMatch(demoText::contains),
					locale + ": die Demo-Reihe muss sich als solche zu erkennen geben");

			for (TileRowLabels real : List.of(TileRowLabels.catalogRow(), TileRowLabels.cachedCatalogRow())) {
				String realText = (Translations.get(locale, real.titleKey()) + " "
						+ Translations.get(locale, real.subtitleKey())).toLowerCase();
				for (String word : demoWords) {
					assertFalse(realText.contains(word),
							locale + ": echte Katalogreihe darf sich nicht '" + word + "' nennen: " + realText);
				}
			}
		}
	}
}
