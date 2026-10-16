package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.nav.NavRailEntry;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die Zuordnung Nav-Rail-Eintrag -> Pakettypen (ROADMAP S6:
 * „Kategorien ueber die Nav-Rail"). „Start", „Bibliothek" und
 * „Einstellungen" sind keine Kategorien und schraenken nichts ein.
 */
public final class NavRailCategoriesTest {

	@Test
	public void singleTypeCategoriesMapToTheirType() {
		assertEquals(NavRailCategories.typesFor(NavRailEntry.TEXTURES), Set.of(TilePackageType.TEXTUREN));
		assertEquals(NavRailCategories.typesFor(NavRailEntry.THEMES), Set.of(TilePackageType.THEME));
		assertEquals(NavRailCategories.typesFor(NavRailEntry.HUD), Set.of(TilePackageType.HUD));
		assertEquals(NavRailCategories.typesFor(NavRailEntry.COSMETICS), Set.of(TilePackageType.COSMETICS));
		assertEquals(NavRailCategories.typesFor(NavRailEntry.SHADER), Set.of(TilePackageType.SHADER));
	}

	@Test
	public void soundLanguageBundlesTwoTypes() {
		assertEquals(NavRailCategories.typesFor(NavRailEntry.SOUND_LANGUAGE),
				Set.of(TilePackageType.SOUND, TilePackageType.SPRACHE));
	}

	@Test
	public void startLibraryAndSettingsAreNotCategories() {
		assertTrue(NavRailCategories.typesFor(NavRailEntry.START).isEmpty());
		assertTrue(NavRailCategories.typesFor(NavRailEntry.LIBRARY).isEmpty());
		assertTrue(NavRailCategories.typesFor(NavRailEntry.SETTINGS).isEmpty());

		assertFalse(NavRailCategories.isCategory(NavRailEntry.START));
		assertFalse(NavRailCategories.isCategory(NavRailEntry.LIBRARY));
		assertFalse(NavRailCategories.isCategory(NavRailEntry.SETTINGS));
	}

	@Test
	public void categoryEntriesReportAsCategory() {
		assertTrue(NavRailCategories.isCategory(NavRailEntry.TEXTURES));
		assertTrue(NavRailCategories.isCategory(NavRailEntry.SOUND_LANGUAGE));
	}

	@Test
	public void everyNavRailEntryIsCovered() {
		for (NavRailEntry entry : NavRailEntry.values()) {
			// Wirft nicht (Switch ist erschoepfend) und liefert nie null.
			assertFalse(NavRailCategories.typesFor(entry) == null, entry.name());
		}
	}
}
