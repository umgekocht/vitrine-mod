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
 * Holt den vollstaendigen Registry-Paketeintrag {@code packages/&lt;id&gt;.json}
 * (ROADMAP G3: "Vollstaendigen Registry-Paket-Eintrag holen") -- dasselbe
 * Muster wie {@link CatalogClient} fuer die Index-Seite: HTTPS-/Host-/Groessen-
 * geschuetzter Abruf ueber {@link HttpCatalogClient}, danach Validierung gegen
 * eine eingebettete, byte-identische Schema-Kopie ({@link
 * RegistryPackageSchemaValidator}), danach das geparste Modell ({@link
 * RegistryPackageEntry}).
 *
 * <p>Liefert -- wie {@link CatalogClient} -- immer ein typisiertes {@link
 * CatalogResult}, nie eine Exception. Wird von zwei Seiten genutzt: der
 * Store-Detailseite fuer ein Katalog-Angebot ({@code CatalogPackageDetailSource},
 * G3) und der Update-Pruefung ({@code RegistryPackageVersionSource}, K5).</p>
 */
public final class RegistryPackageClient {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Catalog");

	private final HttpCatalogClient http;
	private final URI packagesBaseUrl;

	public RegistryPackageClient(HttpCatalogClient http, URI packagesBaseUrl) {
		this.http = Objects.requireNonNull(http, "http");
		this.packagesBaseUrl = Objects.requireNonNull(packagesBaseUrl, "packagesBaseUrl");
	}

	/**
	 * Produktions-Verdrahtung: echter JDK-HTTP-Transport, derselbe ETag-Cache-
	 * Ordner wie {@link CatalogClient#production}, {@link HostWhitelist#PRODUCTION},
	 * einkompilierte {@link CatalogEndpoints#DEFAULT_PACKAGES_BASE_URL}.
	 */
	public static RegistryPackageClient production(Path vitrineConfigDir) {
		EtagCache cache = new EtagCache(vitrineConfigDir.resolve(CatalogEndpoints.CACHE_DIR_NAME));
		HttpCatalogClient http = new HttpCatalogClient(new JdkHttpTransport(), cache, HostWhitelist.PRODUCTION);
		return new RegistryPackageClient(http, CatalogEndpoints.DEFAULT_PACKAGES_BASE_URL);
	}

	/** Holt und validiert {@code packages/<id>.json} fuer {@code id}. */
	public CatalogResult<RegistryPackageEntry> fetchPackage(String id) {
		Objects.requireNonNull(id, "id");
		URI uri = packagesBaseUrl.resolve(id + ".json");
		String cacheKey = "package-v1-" + id;

		byte[] body;
		try {
			body = http.fetch(uri, cacheKey);
		} catch (CatalogUnavailableException e) {
			LOGGER.warn("Registry-Paketeintrag '{}' nicht erreichbar ({}): {}", id, uri, e.getMessage());
			return CatalogResult.noNetwork(e.getMessage());
		} catch (CatalogBrokenException e) {
			LOGGER.warn("Registry-Paketeintrag '{}' defekt ({}): {}", id, uri, e.getMessage());
			return CatalogResult.catalogBroken(e.getMessage());
		} catch (CatalogException e) {
			LOGGER.warn("Registry-Paketeintrag '{}': unerwarteter Fehler ({}): {}", id, uri, e.toString(), e);
			return CatalogResult.catalogBroken(e.getMessage());
		}

		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return brokenAndLog(id, uri, "Registry-Paketeintrag ist kein JSON-Objekt");
			}
			json = parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			return brokenAndLog(id, uri, "Registry-Paketeintrag ist kein gueltiges JSON: " + e.getMessage());
		}

		List<String> violations = RegistryPackageSchemaValidator.validate(json);
		if (!violations.isEmpty()) {
			return brokenAndLog(id, uri, "Registry-Paketeintrag verletzt das Schema: " + String.join("; ", violations));
		}

		String actualId = json.get("id").getAsString();
		if (!id.equals(actualId)) {
			return brokenAndLog(id, uri,
					"Registry-Paketeintrag traegt eine andere Id ('" + actualId + "') als angefragt ('" + id + "')");
		}

		RegistryPackageEntry entry = RegistryPackageEntry.fromJson(json);
		LOGGER.info("Registry-Paketeintrag '{}' geladen ({} Version(en)), geprueft gegen {}", id,
				entry.versions().size(), uri);
		return CatalogResult.online(entry);
	}

	private static <T> CatalogResult<T> brokenAndLog(String id, URI uri, String message) {
		LOGGER.warn("Registry-Paketeintrag '{}' defekt ({}): {}", id, uri, message);
		return CatalogResult.catalogBroken(message);
	}
}
