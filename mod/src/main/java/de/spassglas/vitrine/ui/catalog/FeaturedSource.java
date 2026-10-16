package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.Featured;

/**
 * Naht (Seam) zwischen dem {@link StoreCatalogController} und dem echten
 * {@code featured.json}-Abruf (ROADMAP K1/X2) -- dasselbe Muster wie
 * {@link IndexSource}: die Produktion verdrahtet
 * {@code CatalogClient.production(configDir)::fetchFeatured}, Tests setzen
 * einen Fake ein, der ONLINE / NO_NETWORK / CATALOG_BROKEN liefert.
 *
 * <p>Wie {@code CatalogClient} gibt diese Methode <em>immer</em> ein
 * {@link CatalogResult} zurueck und wirft nie.</p>
 */
@FunctionalInterface
public interface FeaturedSource {

	/** Die kuratierte Auswahl des Katalogs, als typisiertes Ergebnis (nie eine Exception). */
	CatalogResult<Featured> fetchFeatured();
}
