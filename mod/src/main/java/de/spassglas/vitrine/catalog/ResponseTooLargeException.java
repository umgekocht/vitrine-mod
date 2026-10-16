package de.spassglas.vitrine.catalog;

import java.io.IOException;

/**
 * Der Antwortkoerper hat {@link CatalogEndpoints#MAX_BODY_BYTES} ueberschritten
 * -- entweder tatsaechlich beim Lesen oder bereits laut {@code Content-Length}.
 * Eine {@link IOException}, damit der {@link Transport}-Seam sie beim
 * Stream-Lesen werfen kann; {@link HttpCatalogClient} faengt sie <em>vor</em>
 * dem generischen {@code IOException}-Zweig ab und macht daraus einen
 * {@link CatalogBrokenException} (nicht {@code NO_NETWORK}).
 */
public final class ResponseTooLargeException extends IOException {

	public ResponseTooLargeException(String message) {
		super(message);
	}
}
