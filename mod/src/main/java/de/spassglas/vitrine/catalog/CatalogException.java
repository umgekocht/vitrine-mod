package de.spassglas.vitrine.catalog;

/**
 * Gemeinsame Oberklasse der beiden Katalog-Fehlerarten. Wird prozessintern von
 * {@link HttpCatalogClient} geworfen und von {@link CatalogClient} in ein
 * typisiertes {@link CatalogResult} uebersetzt -- nie nach aussen an UI-Code
 * durchgereicht (ROADMAP K2: "Nie Exception nach aussen").
 */
public abstract class CatalogException extends Exception {

	protected CatalogException(String message) {
		super(message);
	}

	protected CatalogException(String message, Throwable cause) {
		super(message, cause);
	}
}
