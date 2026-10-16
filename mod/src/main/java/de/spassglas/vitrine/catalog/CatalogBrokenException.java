package de.spassglas.vitrine.catalog;

/**
 * Die Gegenseite hat geantwortet, aber die Antwort taugt nicht: HTTP 4xx/5xx,
 * ungueltiges JSON, Schemaverstoss, Groessen-Limit ueberschritten, Host nicht
 * auf der Whitelist, Weiterleitung auf einen fremden Host. Entspricht dem
 * Zustand {@link CatalogResult.Availability#CATALOG_BROKEN} (S7
 * {@code CatalogAvailability}-Seam).
 */
public final class CatalogBrokenException extends CatalogException {

	public CatalogBrokenException(String message) {
		super(message);
	}

	public CatalogBrokenException(String message, Throwable cause) {
		super(message, cause);
	}
}
