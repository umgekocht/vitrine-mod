package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.IndexPage;

/**
 * Naht (Seam) zwischen dem {@link StoreCatalogController} und dem echten
 * Katalog-Abruf (ROADMAP K2/K4) -- dasselbe Muster wie
 * {@code Transport}/{@code FileUriTransport} im {@code catalog}-Package: die
 * Produktion verdrahtet {@code CatalogClient.production(configDir)::fetchIndexPage},
 * Tests setzen einen Fake ein, der ONLINE / NO_NETWORK / CATALOG_BROKEN liefert.
 *
 * <p>Wie {@code CatalogClient} gibt diese Methode <em>immer</em> ein
 * {@link CatalogResult} zurueck und wirft nie.</p>
 *
 * <p>ROADMAP GATE72: die einzige abstrakte Methode nimmt die 1-basierte
 * Seitennummer entgegen, damit {@link StoreCatalogController} ueber
 * {@code totalPages}/{@code nextPage} hinweg alle Seiten laden kann -- nicht
 * nur die erste ({@link #fetchFirstPage()} bleibt als Bequemlichkeits-Default
 * fuer Seite 1 erhalten).</p>
 */
@FunctionalInterface
public interface IndexSource {

	/** Die 1-basierte Index-Seite {@code pageNumber}, als typisiertes Ergebnis (nie eine Exception). */
	CatalogResult<IndexPage> fetchPage(int pageNumber);

	/** Die erste Index-Seite des Katalogs. */
	default CatalogResult<IndexPage> fetchFirstPage() {
		return fetchPage(1);
	}
}
