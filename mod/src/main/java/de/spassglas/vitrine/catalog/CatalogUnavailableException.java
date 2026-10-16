package de.spassglas.vitrine.catalog;

/**
 * Der Katalog war nicht erreichbar: Verbindungsfehler, Timeout, DNS, abgebrochene
 * Leitung. Entspricht dem Zustand {@link CatalogResult.Availability#NO_NETWORK}
 * (S7 {@code CatalogAvailability}-Seam) -- der Store kann aus dem Cache oder mit
 * lokalen Paketen weiterarbeiten (K4).
 */
public final class CatalogUnavailableException extends CatalogException {

	public CatalogUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
