package de.spassglas.vitrine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * High-Level-Katalog-Client (ROADMAP K2). Liest <em>nur</em> Katalog-Metadaten
 * -- kein Codepfad wendet ein Paket an (das ist K3/K4). Liefert immer ein
 * typisiertes {@link CatalogResult}, nie eine Exception:
 * <ul>
 *   <li>Netzfehler/Timeout -&gt; {@link CatalogResult.Availability#NO_NETWORK}</li>
 *   <li>HTTP 4xx/5xx, ungueltiges JSON, Schemaverstoss, Groessen-/Whitelist-
 *       Verstoss, Weiterleitung auf einen fremden Host
 *       -&gt; {@link CatalogResult.Availability#CATALOG_BROKEN}</li>
 * </ul>
 * Jeder Fehlzustand wird unter {@code Vitrine/Catalog} geloggt, mit der
 * geprueften URL.
 *
 * <p>Abgedeckte Endpunkte: die paginierte Index-Seite ({@code index/v1/index.json},
 * {@code index/v1/index-2.json}, ...), die kuratierte Auswahl
 * ({@code index/v1/featured.json}, ROADMAP X2) und der flache Suchindex
 * ({@code index/v1/search.json}, ROADMAP K1/GATE72, {@link #fetchSearchIndex()}),
 * der Grundlage der Live-Suche (docs/UI-SPEC.md "1. Kopfleiste").</p>
 */
public final class CatalogClient {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Catalog");

	private final HttpCatalogClient http;
	private final URI indexUrl;

	public CatalogClient(HttpCatalogClient http, URI indexUrl) {
		this.http = Objects.requireNonNull(http, "http");
		this.indexUrl = Objects.requireNonNull(indexUrl, "indexUrl");
	}

	/**
	 * Verdrahtet den Produktions-Client: JDK-HTTP-Transport, ETag-Cache in
	 * {@code &lt;vitrineConfigDir&gt;/cache/}, {@link HostWhitelist#PRODUCTION},
	 * einkompilierte {@link CatalogEndpoints#DEFAULT_INDEX_URL}.
	 *
	 * @param vitrineConfigDir der Vitrine-Konfigordner (z. B. {@code config/vitrine}),
	 *                         kein absoluter Pfad im Code -- der Aufrufer reicht
	 *                         {@code FabricLoader.getInstance().getConfigDir().resolve("vitrine")}.
	 */
	public static CatalogClient production(Path vitrineConfigDir) {
		EtagCache cache = new EtagCache(vitrineConfigDir.resolve(CatalogEndpoints.CACHE_DIR_NAME));
		HttpCatalogClient http = new HttpCatalogClient(new JdkHttpTransport(), cache, HostWhitelist.PRODUCTION);
		return new CatalogClient(http, CatalogEndpoints.DEFAULT_INDEX_URL);
	}

	/** Die erste Index-Seite. */
	public CatalogResult<IndexPage> fetchIndex() {
		return fetchIndexPage(1);
	}

	/**
	 * Die 1-basierte Index-Seite {@code pageNumber}. Seite 1 ist die
	 * einkompilierte URL, jede weitere Seite die Geschwisterdatei
	 * {@code index-&lt;n&gt;.json}.
	 */
	public CatalogResult<IndexPage> fetchIndexPage(int pageNumber) {
		if (pageNumber < 1) {
			return brokenAndLog(indexUrl, "Ungueltige Seitennummer " + pageNumber + " (muss >= 1 sein)");
		}
		URI uri = pageUri(pageNumber);
		String cacheKey = indexCacheKey(pageNumber);

		byte[] body;
		try {
			body = http.fetch(uri, cacheKey);
		} catch (CatalogUnavailableException e) {
			LOGGER.warn("Katalog nicht erreichbar ({}): {}", uri, e.getMessage());
			return CatalogResult.noNetwork(e.getMessage());
		} catch (CatalogBrokenException e) {
			LOGGER.warn("Katalog defekt ({}): {}", uri, e.getMessage());
			return CatalogResult.catalogBroken(e.getMessage());
		} catch (CatalogException e) {
			// Kann nicht vorkommen (nur die zwei Unterklassen existieren), aber
			// kein verschlucktes catch: ehrlich als kaputt melden statt weiterreichen.
			LOGGER.warn("Katalog: unerwarteter Fehler ({}): {}", uri, e.toString(), e);
			return CatalogResult.catalogBroken(e.getMessage());
		}

		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return brokenAndLog(uri, "Index-Seite ist kein JSON-Objekt");
			}
			json = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			return brokenAndLog(uri, "Index-Seite ist kein gueltiges JSON: " + e.getMessage());
		}

		List<String> violations = IndexPageSchemaValidator.validate(json);
		if (!violations.isEmpty()) {
			return brokenAndLog(uri, "Index-Seite verletzt das Schema: " + String.join("; ", violations));
		}

		IndexPage page = IndexPage.fromJson(json);
		LOGGER.info("Index-Seite {} geladen: {} Pakete, geprueft gegen {}",
				pageNumber, page.packages().size(), uri);
		return CatalogResult.online(page);
	}

	/**
	 * Die kuratierte Auswahl (ROADMAP K1/X2, {@code featured.json}) -- die
	 * Geschwisterdatei von {@code index.json} unter demselben versionierten
	 * Pfad, ermittelt genauso wie eine weitere Index-Seite ueber
	 * {@link URI#resolve(String)}. Nie eine Exception: Netzfehler werden
	 * {@code NO_NETWORK}, alles andere (HTTP-Fehler, kaputtes JSON,
	 * Schemaverstoss) {@code CATALOG_BROKEN}.
	 */
	public CatalogResult<Featured> fetchFeatured() {
		URI uri = indexUrl.resolve("featured.json");

		byte[] body;
		try {
			body = http.fetch(uri, FEATURED_CACHE_KEY);
		} catch (CatalogUnavailableException e) {
			LOGGER.warn("Kuratierte Auswahl nicht erreichbar ({}): {}", uri, e.getMessage());
			return CatalogResult.noNetwork(e.getMessage());
		} catch (CatalogBrokenException e) {
			LOGGER.warn("Kuratierte Auswahl defekt ({}): {}", uri, e.getMessage());
			return CatalogResult.catalogBroken(e.getMessage());
		} catch (CatalogException e) {
			LOGGER.warn("Kuratierte Auswahl: unerwarteter Fehler ({}): {}", uri, e.toString(), e);
			return CatalogResult.catalogBroken(e.getMessage());
		}

		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return brokenAndLog(uri, "featured.json ist kein JSON-Objekt");
			}
			json = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			return brokenAndLog(uri, "featured.json ist kein gueltiges JSON: " + e.getMessage());
		}

		List<String> violations = FeaturedSchemaValidator.validate(json);
		if (!violations.isEmpty()) {
			return brokenAndLog(uri, "featured.json verletzt das Schema: " + String.join("; ", violations));
		}

		Featured featured = Featured.fromJson(json);
		LOGGER.info("Kuratierte Auswahl geladen: {} Eintraege, geprueft gegen {}", featured.items().size(), uri);
		return CatalogResult.online(featured);
	}

	/**
	 * Der flache Suchindex (ROADMAP K1/GATE72, {@code search.json}) -- die
	 * Geschwisterdatei von {@code index.json} unter demselben versionierten
	 * Pfad, genauso ermittelt wie {@link #fetchFeatured()}. Nie eine Exception:
	 * Netzfehler werden {@code NO_NETWORK}, alles andere (HTTP-Fehler, kaputtes
	 * JSON, Schemaverstoss) {@code CATALOG_BROKEN}. Laeuft ueber denselben
	 * {@link HttpCatalogClient}-Weg wie jeder andere Katalog-Abruf -- gleiche
	 * Host-Whitelist, gleicher ETag-Cache, gleiche Groessen-/Weiterleitungs-
	 * Pruefung (K2).
	 */
	public CatalogResult<SearchIndex> fetchSearchIndex() {
		URI uri = indexUrl.resolve("search.json");

		byte[] body;
		try {
			body = http.fetch(uri, SEARCH_CACHE_KEY);
		} catch (CatalogUnavailableException e) {
			LOGGER.warn("Suchindex nicht erreichbar ({}): {}", uri, e.getMessage());
			return CatalogResult.noNetwork(e.getMessage());
		} catch (CatalogBrokenException e) {
			LOGGER.warn("Suchindex defekt ({}): {}", uri, e.getMessage());
			return CatalogResult.catalogBroken(e.getMessage());
		} catch (CatalogException e) {
			LOGGER.warn("Suchindex: unerwarteter Fehler ({}): {}", uri, e.toString(), e);
			return CatalogResult.catalogBroken(e.getMessage());
		}

		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return brokenAndLog(uri, "search.json ist kein JSON-Objekt");
			}
			json = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			return brokenAndLog(uri, "search.json ist kein gueltiges JSON: " + e.getMessage());
		}

		List<String> violations = SearchIndexSchemaValidator.validate(json);
		if (!violations.isEmpty()) {
			return brokenAndLog(uri, "search.json verletzt das Schema: " + String.join("; ", violations));
		}

		SearchIndex index = SearchIndex.fromJson(json);
		LOGGER.info("Suchindex geladen: {} Eintraege, geprueft gegen {}", index.entries().size(), uri);
		return CatalogResult.online(index);
	}

	private URI pageUri(int pageNumber) {
		return pageNumber <= 1 ? indexUrl : indexUrl.resolve("index-" + pageNumber + ".json");
	}

	/**
	 * ETag-Cache-Schluessel der 1-basierten Index-Seite {@code pageNumber}. Eine
	 * Wahrheit -- der Offline-Leser ({@code CachedIndexStore}, ROADMAP K4) greift
	 * denselben Cache-Eintrag ab.
	 */
	static String indexCacheKey(int pageNumber) {
		return "index-v1-p" + pageNumber;
	}

	/** ETag-Cache-Schluessel der kuratierten Auswahl ({@code featured.json}). */
	private static final String FEATURED_CACHE_KEY = "featured-v1";

	/** ETag-Cache-Schluessel des flachen Suchindex ({@code search.json}, ROADMAP GATE72). */
	private static final String SEARCH_CACHE_KEY = "search-v1";

	private static <T> CatalogResult<T> brokenAndLog(URI uri, String message) {
		LOGGER.warn("Katalog defekt ({}): {}", uri, message);
		return CatalogResult.catalogBroken(message);
	}
}
