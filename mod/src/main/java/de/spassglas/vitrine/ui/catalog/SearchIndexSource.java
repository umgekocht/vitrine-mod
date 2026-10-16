package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.SearchIndex;

/**
 * Naht (Seam) zwischen dem {@link StoreCatalogController} und dem echten
 * {@code search.json}-Abruf (ROADMAP K1/GATE72) -- dasselbe Muster wie
 * {@link FeaturedSource}: die Produktion verdrahtet
 * {@code CatalogClient.production(configDir)::fetchSearchIndex}, Tests setzen
 * einen Fake ein, der ONLINE / NO_NETWORK / CATALOG_BROKEN liefert.
 *
 * <p>Wie {@code CatalogClient} gibt diese Methode <em>immer</em> ein
 * {@link CatalogResult} zurueck und wirft nie.</p>
 */
@FunctionalInterface
public interface SearchIndexSource {

	/** Der flache Suchindex des Katalogs, als typisiertes Ergebnis (nie eine Exception). */
	CatalogResult<SearchIndex> fetchSearchIndex();
}
