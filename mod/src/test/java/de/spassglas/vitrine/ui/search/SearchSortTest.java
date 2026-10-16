package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die Sortier-Beschriftungs-Keys (ROADMAP S6) und den durchschaltenden
 * Ring des Sortier-Knopfs ({@link SearchSort#next()}).
 */
public final class SearchSortTest {

	@Test
	public void translationKeysMatchRoadmapWording() {
		assertEquals(SearchSort.NAME_ASC.translationKey(), "vitrine.search.sort.name");
		assertEquals(SearchSort.AUTHOR_ASC.translationKey(), "vitrine.search.sort.author");
		assertEquals(SearchSort.TYPE.translationKey(), "vitrine.search.sort.type");
	}

	@Test
	public void everySortKeyExistsInBothLangFiles() {
		for (SearchSort sort : SearchSort.values()) {
			String key = sort.translationKey();
			assertTrue(Translations.has("en_us", key), "fehlt in en_us.json: " + key);
			assertTrue(Translations.has("de_de", key), "fehlt in de_de.json: " + key);
		}
	}

	@Test
	public void nextCyclesThroughAllValuesAndWrapsAround() {
		assertEquals(SearchSort.NAME_ASC.next(), SearchSort.AUTHOR_ASC);
		assertEquals(SearchSort.AUTHOR_ASC.next(), SearchSort.TYPE);
		assertEquals(SearchSort.TYPE.next(), SearchSort.NAME_ASC);
	}

	@Test
	public void cyclingThreeTimesReturnsToStart() {
		SearchSort sort = SearchSort.NAME_ASC;
		for (int i = 0; i < SearchSort.values().length; i++) {
			sort = sort.next();
		}
		assertEquals(sort, SearchSort.NAME_ASC);
	}
}
