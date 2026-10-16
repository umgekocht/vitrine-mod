package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.DemoCatalog;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * GATE42 (Charter-Regel 2): {@link DemoCatalog#statusShowcaseRow()} darf in
 * der Produktionssuche nur auftauchen, solange es noch keinen echten,
 * geladenen Katalog gibt -- vorher mischte {@code StoreScreen} die
 * Demo-Reihe unbedingt in die durchsuchte Menge, auch bei voll geladenem
 * Live-Katalog; ein Klick auf so einen Treffer lief auf "Paket kaputt"
 * (keine Registry-Herkunft fuer eine erfundene {@code demo.*}-Id).
 */
public final class CatalogSearchMergeTest {

	private static TileData localTile(String id) {
		return TileData.withoutImage(id, "Lokal " + id, "someone", TilePackageType.THEME, TileStatus.NONE);
	}

	private static TileData catalogTile(String id) {
		return TileData.withoutImage(id, "Katalog " + id, "someone", TilePackageType.THEME, TileStatus.NONE);
	}

	@Test
	public void withoutRealCatalogDemoRowStillAppearsInSearch() {
		CatalogSearchMerge.Result result = CatalogSearchMerge.merge(List.of(), List.of(localTile("local.a")));

		Set<String> ids = result.tiles().stream().map(TileData::id).collect(Collectors.toSet());
		for (TileData demoTile : DemoCatalog.statusShowcaseRow()) {
			assertTrue(ids.contains(demoTile.id()),
					"Demo-Reihe soll ohne echten Katalog weiter durchsuchbar sein: " + demoTile.id());
		}
		assertTrue(ids.contains("local.a"));
		assertTrue(result.catalogIds().isEmpty(), "ohne echten Katalog gibt es keine Katalog-Ids");
	}

	@Test
	public void withRealCatalogLoadedDemoRowDisappearsFromSearch() {
		List<TileData> realCatalog = List.of(catalogTile("real.one"), catalogTile("real.two"));
		CatalogSearchMerge.Result result = CatalogSearchMerge.merge(realCatalog, List.of(localTile("local.a")));

		Set<String> ids = result.tiles().stream().map(TileData::id).collect(Collectors.toSet());
		for (TileData demoTile : DemoCatalog.statusShowcaseRow()) {
			assertFalse(ids.contains(demoTile.id()),
					"Fertig-Kriterium GATE42: Demo-Pakete duerfen bei geladenem Katalog nicht mehr in der Suche "
							+ "auftauchen: " + demoTile.id());
		}
		assertTrue(ids.contains("local.a"));
		assertTrue(ids.contains("real.one"));
		assertTrue(ids.contains("real.two"));
		assertEquals(result.catalogIds(), Set.of("real.one", "real.two"));
	}

	@Test
	public void localEntryWinsOverCatalogEntryWithSameIdButIdStillCountsAsCatalogSourced() {
		// Entdoppelung nach Paket-ID (ROADMAP GATE5): die lokale Kachel (z. B.
		// bereits installiert) bleibt in der durchsuchten Menge stehen, aber die
		// Id gilt weiter als Katalog-Herkunft fuer den Treffer-Klick-Pfad.
		TileData local = localTile("shared.id");
		TileData remote = catalogTile("shared.id");
		CatalogSearchMerge.Result result = CatalogSearchMerge.merge(List.of(remote), List.of(local));

		assertEquals(result.tiles().size(), 1);
		assertEquals(result.tiles().get(0).name(), local.name());
		assertTrue(result.catalogIds().contains("shared.id"));
	}
}
