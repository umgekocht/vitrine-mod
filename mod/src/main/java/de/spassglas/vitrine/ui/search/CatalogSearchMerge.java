package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.DemoCatalog;
import de.spassglas.vitrine.ui.tile.TileData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ROADMAP GATE5/GATE42: fuehrt die von {@code StoreScreen.ensureSearchController()}
 * durchsuchte Kachelmenge zusammen -- eine reine Funktion, ohne laufenden
 * Client testbar (Muster wie {@code CuratedHomeSections}).
 *
 * <p>GATE42: die Demo-Statusvitrine aus {@link DemoCatalog} ist nur Teil der
 * durchsuchten Menge, solange der echte Katalog noch nichts geliefert hat
 * ({@code catalogTiles} leer) -- derselbe Grundsatz wie fuer die Start-Reihe
 * (GATE14/GATE15, Charter-Regel 2). Die Demo-Ids existieren in keiner
 * Registry; ein Treffer-Klick darauf liefe ueber
 * {@code LocalPackageDetailSource} auf "Paket kaputt". Sobald der Katalog
 * geladen ist, verschwindet die Demo-Reihe vollstaendig aus der Suche.</p>
 */
public final class CatalogSearchMerge {

	private CatalogSearchMerge() {
	}

	/** Zusammengefuehrte Kachelmenge plus die Ids, die tatsaechlich aus dem echten Katalog stammen. */
	public record Result(List<TileData> tiles, Set<String> catalogIds) {
	}

	/**
	 * @param catalogTiles       die vom {@code StoreCatalogController} zuletzt geladenen Katalog-Kacheln
	 *                           (leer, solange kein echter Katalog geladen wurde).
	 * @param localLibraryTiles  die lokalen Bibliotheks-Angebote (ROADMAP S2).
	 */
	public static Result merge(List<TileData> catalogTiles, List<TileData> localLibraryTiles) {
		boolean catalogIsReal = !catalogTiles.isEmpty();
		Map<String, TileData> byId = new LinkedHashMap<>();
		if (!catalogIsReal) {
			for (TileData tile : DemoCatalog.statusShowcaseRow()) {
				byId.putIfAbsent(tile.id(), tile);
			}
		}
		for (TileData tile : localLibraryTiles) {
			byId.putIfAbsent(tile.id(), tile);
		}
		Set<String> catalogIds = new LinkedHashSet<>();
		for (TileData tile : catalogTiles) {
			byId.putIfAbsent(tile.id(), tile);
			catalogIds.add(tile.id());
		}
		return new Result(List.copyOf(byId.values()), Set.copyOf(catalogIds));
	}
}
