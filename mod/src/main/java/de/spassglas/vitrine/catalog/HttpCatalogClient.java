package de.spassglas.vitrine.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Mittlere Schicht zwischen dem rohen {@link Transport} und dem geparsten
 * {@link CatalogClient}: kuemmert sich um Host-Whitelist, das manuelle Pruefen
 * von Weiterleitungszielen und den ETag-Cache. Kennt kein JSON und kein Schema.
 *
 * <p>Weiterleitungen: eine 30x-Antwort wird <strong>nicht</strong> automatisch
 * verfolgt. Der {@code Location}-Header wird gegen {@code hostAllowed} geprueft;
 * zeigt er auf einen nicht erlaubten Host, bricht der Abruf mit
 * {@link CatalogBrokenException} ab und es geht <em>kein</em> zweiter Request
 * an den fremden Host (woertliches Fertig-Kriterium K2). Nur Ziele, die wieder
 * auf der Whitelist stehen, werden verfolgt -- hoechstens
 * {@link CatalogEndpoints#MAX_REDIRECTS} mal.</p>
 */
public final class HttpCatalogClient {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Catalog");

	private final Transport transport;
	private final EtagCache cache;
	private final Predicate<URI> hostAllowed;

	public HttpCatalogClient(Transport transport, EtagCache cache, Predicate<URI> hostAllowed) {
		this.transport = Objects.requireNonNull(transport, "transport");
		this.cache = Objects.requireNonNull(cache, "cache");
		this.hostAllowed = Objects.requireNonNull(hostAllowed, "hostAllowed");
	}

	/**
	 * Holt den Rumpf der Ressource unter {@code uri} -- entweder frisch (HTTP
	 * 200, danach im Cache aktualisiert) oder bei {@code 304 Not Modified} aus
	 * dem Cache.
	 *
	 * @param cacheKey Schluessel fuer den ETag-Cache-Eintrag dieser Ressource.
	 * @throws CatalogUnavailableException bei Netz-/Timeout-Fehlern.
	 * @throws CatalogBrokenException      bei HTTP 4xx/5xx, Groessen-Limit,
	 *                                     Whitelist-Verstoss, Weiterleitung auf
	 *                                     einen fremden Host oder {@code 304}
	 *                                     ohne brauchbaren Cache.
	 */
	public byte[] fetch(URI uri, String cacheKey) throws CatalogException {
		return fetch(uri, cacheKey, Map.of());
	}

	/**
	 * Wie {@link #fetch(URI, String)}, mit zusaetzlichen, fest vom Aufrufer
	 * gesetzten Anfrage-Headern (z. B. {@code Accept}/{@code User-Agent} fuer
	 * {@code api.github.com}, GATE70 -- die GitHub-API verlangt einen
	 * {@code User-Agent} und antwortet sonst mit HTTP 403). {@code If-None-Match}
	 * aus dem ETag-Cache wird unabhaengig davon weiterhin automatisch gesetzt;
	 * ein gleichnamiger Eintrag in {@code extraHeaders} wuerde ihn ueberschreiben
	 * -- kein Aufrufer in diesem Projekt tut das.
	 */
	public byte[] fetch(URI uri, String cacheKey, Map<String, String> extraHeaders) throws CatalogException {
		if (!hostAllowed.test(uri)) {
			throw new CatalogBrokenException("Host nicht auf der Whitelist, Abruf abgelehnt: " + uri);
		}

		Optional<EtagCache.CacheEntry> cached = cache.load(cacheKey);
		Map<String, String> headers = new HashMap<>(extraHeaders);
		cached.map(EtagCache.CacheEntry::etag)
				.filter(etag -> !etag.isBlank())
				.ifPresent(etag -> headers.put("If-None-Match", etag));

		URI current = uri;
		for (int hop = 0; hop <= CatalogEndpoints.MAX_REDIRECTS; hop++) {
			TransportResponse response;
			try {
				response = transport.get(current, headers);
			} catch (ResponseTooLargeException e) {
				throw new CatalogBrokenException("Antwort von " + current + " ist zu grosz: " + e.getMessage(), e);
			} catch (IOException e) {
				throw new CatalogUnavailableException("Katalog nicht erreichbar (" + current + "): " + e, e);
			}

			int status = response.status();
			if (status == 200) {
				byte[] body = response.body();
				cache.store(cacheKey, body,
						response.firstHeader("ETag").orElse(null),
						response.firstHeader("Last-Modified").orElse(null));
				return body;
			}
			if (status == 304) {
				EtagCache.CacheEntry entry = cached.orElse(null);
				if (entry == null) {
					throw new CatalogBrokenException("Server meldet 304 Not Modified fuer " + current
							+ ", aber es gibt keinen (brauchbaren) Cache-Eintrag '" + cacheKey + "'");
				}
				return entry.body();
			}
			if (response.isRedirect()) {
				String location = response.firstHeader("Location").orElse(null);
				if (location == null) {
					throw new CatalogBrokenException(
							"Weiterleitung (HTTP " + status + ") von " + current + " ohne Location-Header");
				}
				URI target;
				try {
					target = current.resolve(location);
				} catch (IllegalArgumentException e) {
					throw new CatalogBrokenException(
							"Weiterleitung von " + current + " nennt eine ungueltige Location '" + location + "'", e);
				}
				if (!hostAllowed.test(target)) {
					throw new CatalogBrokenException("Weiterleitung auf einen nicht erlaubten Host abgelehnt: "
							+ current + " -> " + target + " (kein weiterer Request)");
				}
				LOGGER.info("Katalog-Weiterleitung innerhalb der Whitelist: {} -> {}", current, target);
				current = target;
				continue;
			}
			throw new CatalogBrokenException("Katalog antwortete mit HTTP " + status + " auf " + current);
		}
		throw new CatalogBrokenException("Zu viele Weiterleitungen (mehr als "
				+ CatalogEndpoints.MAX_REDIRECTS + ") ab " + uri);
	}
}
