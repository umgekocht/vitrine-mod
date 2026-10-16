package de.spassglas.vitrine.catalog;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Seam fuer die eigentliche "hole Bytes von einer URI"-Operation -- analog
 * {@code DelayedTaskScheduler} / {@code PackActivation} in {@code hotapply}.
 * Die Produktion nutzt {@link JdkHttpTransport} (nur {@code https}-GET,
 * {@code followRedirects(NEVER)}, Timeouts, Groessenlimit). Tests setzen einen
 * Fake oder {@link FileUriTransport} ein; der {@code file://}-Pfad umgeht die
 * HTTPS-Pflicht bewusst <em>nur</em> ueber diesen Seam.
 *
 * <p>Ein {@code Transport} folgt <strong>nie</strong> selbst einer
 * Weiterleitung: eine 30x-Antwort wird unveraendert mit ihrem
 * {@code Location}-Header zurueckgegeben, damit {@link HttpCatalogClient} das
 * Ziel gegen die {@link HostWhitelist} pruefen kann.</p>
 */
public interface Transport {

	/**
	 * Fuehrt genau einen GET auf {@code uri} aus.
	 *
	 * @param requestHeaders zusaetzliche Anfrage-Header (z. B.
	 *                       {@code If-None-Match}); darf leer sein.
	 * @return die rohe Antwort inklusive 30x, ohne der Weiterleitung zu folgen.
	 * @throws IOException             bei Verbindungs-, Timeout- oder Lesefehlern
	 *                                 ({@code NO_NETWORK}-Klasse).
	 * @throws ResponseTooLargeException wenn der Koerper das Limit sprengt
	 *                                 ({@code CATALOG_BROKEN}-Klasse).
	 */
	TransportResponse get(URI uri, Map<String, String> requestHeaders) throws IOException;
}
