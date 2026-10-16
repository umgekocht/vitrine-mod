package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.SearchIndexEntry;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Uebersetzt einen einzelnen Suchindex-Eintrag ({@link SearchIndexEntry}) in
 * eine Kachel ({@link TileData}) -- das Gegenstueck zu
 * {@link IndexPackageTiles} fuer {@code search.json} statt der paginierten
 * Index-Seiten (ROADMAP K1/GATE72). {@code search.json} kennt kein
 * {@code javaMod}-Feld (registry-search.schema.json); jeder Treffer ist daher
 * ein Klasse-A-Angebot.
 */
final class SearchIndexTiles {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/StoreCatalog");

	/** Siehe {@code IndexPackageTiles#STATE_PACK_ID_PREFIX} -- derselbe Abgleich wie dort. */
	private static final String STATE_PACK_ID_PREFIX = "vitrine/";

	private SearchIndexTiles() {
	}

	/**
	 * {@link Optional#empty()}, wenn {@code entry} eine dem Store unbekannte
	 * Kategorie nennt -- geloggt, der Eintrag wird ausgelassen statt den Store
	 * abstuerzen zu lassen.
	 */
	static Optional<TileData> toTile(SearchIndexEntry entry, PackState installedState) {
		Optional<TilePackageType> type = TilePackageType.byId(entry.category());
		if (type.isEmpty()) {
			LOGGER.warn("Suchindex-Eintrag '{}' nennt eine unbekannte Kategorie '{}', wird ausgelassen.",
					entry.id(), entry.category());
			return Optional.empty();
		}
		TileStatus status = isInstalled(installedState, entry.id()) ? TileStatus.INSTALLED : TileStatus.NONE;
		return Optional.of(TileData.withoutImage(entry.id(), entry.name(), entry.author(), type.get(), status));
	}

	private static boolean isInstalled(PackState state, String manifestId) {
		if (state == null) {
			return false;
		}
		return state.isInstalled(manifestId) || state.isInstalled(STATE_PACK_ID_PREFIX + manifestId);
	}
}
