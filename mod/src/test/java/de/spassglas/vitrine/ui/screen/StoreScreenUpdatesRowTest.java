package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE73-Fertig-Kriterium: die Reihe "Deine Updates" (docs/UI-SPEC.md
 * "3. Start": "nur wenn es welche gibt") existiert genau dann, wenn
 * installierte Pakete mit {@link TileStatus#UPDATE_AVAILABLE} vorliegen.
 * {@code StoreScreen} selbst laesst sich ohne laufenden Client nicht
 * aufbauen/rendern (siehe {@code StoreScreenLocalLibraryRowTest}); deshalb
 * beweist dieser Test die reine, extrahierte Filterlogik hinter
 * {@code buildUpdatesRow}, genauso wie {@code localLibraryRowTopY} fuer die
 * Positionierung.
 */
public final class StoreScreenUpdatesRowTest {

	private static TileData tile(String id, TileStatus status) {
		return TileData.withoutImage(id, "Paket " + id, "autorin", TilePackageType.THEME, status);
	}

	@Test
	public void onlyPackagesWithAnAvailableUpdateAreKept() {
		TileData installed = tile("a", TileStatus.INSTALLED);
		TileData upToDate = tile("b", TileStatus.NONE);
		TileData withUpdate = tile("c", TileStatus.UPDATE_AVAILABLE);

		List<TileData> result = StoreScreen.updateOffers(List.of(installed, upToDate, withUpdate));

		assertEquals(result, List.of(withUpdate), "nur die Kachel mit UPDATE_AVAILABLE darf in der Reihe stehen");
	}

	@Test
	public void emptyWithoutAnyAvailableUpdateMeansNoRow() {
		List<TileData> result = StoreScreen.updateOffers(
				List.of(tile("a", TileStatus.INSTALLED), tile("b", TileStatus.NONE)));

		assertTrue(result.isEmpty(), "ohne Update-Kandidaten darf die Reihe 'Deine Updates' nicht entstehen");
	}

	@Test
	public void keepsTheOriginalOrder() {
		TileData first = tile("a", TileStatus.UPDATE_AVAILABLE);
		TileData second = tile("b", TileStatus.UPDATE_AVAILABLE);

		List<TileData> result = StoreScreen.updateOffers(List.of(first, second));

		assertEquals(result, List.of(first, second));
	}
}
