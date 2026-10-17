package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.nav.NavRailEntry;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE88-Beweis: der Platzhaltertext "%s-Inhalte folgen in einem
 * spaeteren Update" (de_de.json {@code vitrine.content.placeholder}) erschien
 * vorher auch ueber Kategorien mit echtem Inhalt (z. B. "News", das ueber
 * {@code buildModrinthRow} eine echte Modrinth-Reihe zeigt) -- {@code
 * StoreScreen} selbst laesst sich ohne laufenden Client nicht aufbauen
 * (siehe {@code StoreScreenLocalLibraryRowTest}), deshalb beweist dieser Test
 * die reine, extrahierte Entscheidung {@link StoreScreen#showsRoadmapPlaceholder}.
 */
public final class StoreScreenRoadmapPlaceholderTest {

	@Test
	public void startNeverShowsThePlaceholderEvenWithoutContent() {
		assertFalse(StoreScreen.showsRoadmapPlaceholder(NavRailEntry.START, false, false));
	}

	@Test
	public void aCategoryWithRealContentNeverShowsThePlaceholder() {
		assertFalse(StoreScreen.showsRoadmapPlaceholder(NavRailEntry.NEWS, false, true),
				"'News' mit echter Modrinth-Reihe darf den generischen Platzhaltertext nicht zeigen");
		assertFalse(StoreScreen.showsRoadmapPlaceholder(NavRailEntry.TEXTURES, false, true));
	}

	@Test
	public void aCategoryWithoutAnyContentRightNowShowsThePlaceholder() {
		assertTrue(StoreScreen.showsRoadmapPlaceholder(NavRailEntry.NEWS, false, false),
				"eine Kategorie mit 0 Eintraegen gerade jetzt darf den Platzhaltertext zeigen");
	}

	@Test
	public void anOpenNavOverlaySuppressesThePlaceholderRegardlessOfContent() {
		assertFalse(StoreScreen.showsRoadmapPlaceholder(NavRailEntry.NEWS, true, false),
				"waehrend die Nav-Rail-Ueberlagerung offen ist, bleibt der Inhaltsbereich leer, kein Text darueber");
	}
}
