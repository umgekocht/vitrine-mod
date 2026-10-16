package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.state.PackState;

/**
 * Naht (Seam) zwischen dem {@link StoreCatalogController} und dem
 * installierten Paketzustand ({@code state.json}) -- dasselbe Muster wie
 * {@link IndexSource}/{@link FeaturedSource}: Produktion verdrahtet
 * {@code PackStateStore.load(vitrineConfigDir)}, Tests setzen einen Fake ein.
 *
 * <p>ROADMAP GATE46: Katalog-Kacheln (Hauptreihe/Featured/Beliebt/Neu) wurden
 * bislang immer mit {@code TileStatus.NONE} gebaut, unabhaengig davon, ob das
 * Paket laengst installiert ist -- {@link IndexPackageTiles} glich nie gegen
 * {@code state.json} ab, anders als {@code LocalLibrarySource} fuer die
 * lokale Bibliothek. Diese Naht schliesst genau diese Luecke, ohne den
 * Controller an einen laufenden Client zu binden.</p>
 */
@FunctionalInterface
public interface PackStateSource {

	/** Der aktuelle Paketzustand, frisch gelesen. Wirft nie (siehe {@code PackStateStore.load}). */
	PackState currentState();
}
