package de.spassglas.vitrine.catalog.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.spassglas.vitrine.catalog.CatalogEndpoints;
import de.spassglas.vitrine.catalog.CatalogException;
import de.spassglas.vitrine.catalog.EtagCache;
import de.spassglas.vitrine.catalog.HttpCatalogClient;
import de.spassglas.vitrine.catalog.JdkHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Holt Sterne/Downloads eines Autoren-Repos von {@code api.github.com}
 * (ROADMAP GATE70, docs/UI-SPEC.md:118-120: "Sterne/Downloads aus der
 * GitHub-API (read-only)"). Read-only: nur zwei {@code GET}s, nie ein
 * schreibender Aufruf, keine Authentifizierung -- die GitHub-API beantwortet
 * diese beiden Endpunkte auch unauthentifiziert (mit einem niedrigeren
 * Rate-Limit, das fuer einen einzelnen Detailseiten-Aufruf reicht).
 *
 * <p>Liefert <b>nie</b> eine Exception nach aussen (Charter-Regel 2: ehrlich
 * "keine Angaben" statt eines Absturzes). Ein nicht erkennbares Repo, ein
 * Netzfehler, HTTP 404 oder eine kaputte Antwort fuehren je Teilwert
 * (Sterne/Downloads) <em>einzeln</em> zu {@link Optional#empty()} -- ein
 * Fehlschlag beim Zaehlen der Downloads darf die (bereits erfolgreich
 * gelesene) Sternezahl nicht mit wegwerfen, siehe {@link GitHubRepoStats}.</p>
 */
public final class GitHubRepoStatsClient {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/GitHubRepoStats");

	/**
	 * GitHub verlangt einen {@code User-Agent}, sonst HTTP 403 (siehe
	 * {@link HttpCatalogClient#fetch(URI, String, Map)}, GATE70).
	 */
	private static final Map<String, String> REQUEST_HEADERS = Map.of(
			"User-Agent", "Vitrine-Minecraft-Mod",
			"Accept", "application/vnd.github+json");

	private final HttpCatalogClient http;

	public GitHubRepoStatsClient(HttpCatalogClient http) {
		this.http = Objects.requireNonNull(http, "http");
	}

	/**
	 * Produktions-Verdrahtung: echter JDK-HTTP-Transport, die eigene,
	 * schmale {@link GitHubRepoStatsHostWhitelist}, geteilter ETag-Cache-
	 * Ordner mit dem uebrigen Katalog (eigene Schluessel-Praefixe verhindern
	 * Kollisionen, siehe {@link #cacheKey}).
	 */
	public static GitHubRepoStatsClient production(Path vitrineConfigDir) {
		EtagCache cache = new EtagCache(vitrineConfigDir.resolve(CatalogEndpoints.CACHE_DIR_NAME));
		HttpCatalogClient httpClient = new HttpCatalogClient(new JdkHttpTransport(), cache,
				GitHubRepoStatsHostWhitelist.PRODUCTION);
		return new GitHubRepoStatsClient(httpClient);
	}

	/**
	 * @param repositoryUrl eine beliebige Zeichenkette (Registry-Feld {@code repository} oder
	 *                      Manifest-Feld {@code homepage}); {@code null}/kein erkennbares GitHub-Repo
	 *                      liefert sofort {@link GitHubRepoStats#UNKNOWN}, <em>ohne</em> einen Request.
	 */
	public GitHubRepoStats fetch(String repositoryUrl) {
		Optional<GitHubRepositoryReference> reference = GitHubRepositoryReference.parse(repositoryUrl);
		if (reference.isEmpty()) {
			return GitHubRepoStats.UNKNOWN;
		}
		GitHubRepositoryReference ref = reference.get();
		Optional<Long> stars = fetchStargazerCount(ref);
		Optional<Long> downloads = fetchDownloadCount(ref);
		return new GitHubRepoStats(stars, downloads);
	}

	/** {@code GET /repos/<owner>/<repo>} -- {@code stargazers_count}. */
	private Optional<Long> fetchStargazerCount(GitHubRepositoryReference ref) {
		URI uri = URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo());
		try {
			byte[] body = http.fetch(uri, cacheKey(ref, "repo"), REQUEST_HEADERS);
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				LOGGER.warn("GitHub-Repo-Antwort fuer '{}/{}' ist kein JSON-Objekt.", ref.owner(), ref.repo());
				return Optional.empty();
			}
			JsonObject object = parsed.getAsJsonObject();
			if (!object.has("stargazers_count") || object.get("stargazers_count").isJsonNull()) {
				return Optional.empty();
			}
			return Optional.of(object.get("stargazers_count").getAsLong());
		} catch (CatalogException e) {
			LOGGER.info("Sternezahl von '{}/{}' nicht abrufbar: {}", ref.owner(), ref.repo(), e.getMessage());
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException | NumberFormatException | ClassCastException e) {
			LOGGER.warn("Sternezahl von '{}/{}': kaputte Antwort: {}", ref.owner(), ref.repo(), e.toString());
			return Optional.empty();
		}
	}

	/** {@code GET /repos/<owner>/<repo>/releases} -- Summe {@code download_count} ueber die erste Seite. */
	private Optional<Long> fetchDownloadCount(GitHubRepositoryReference ref) {
		URI uri = URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo() + "/releases");
		try {
			byte[] body = http.fetch(uri, cacheKey(ref, "releases"), REQUEST_HEADERS);
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				LOGGER.warn("GitHub-Releases-Antwort fuer '{}/{}' ist kein JSON-Array.", ref.owner(), ref.repo());
				return Optional.empty();
			}
			long sum = 0L;
			for (JsonElement releaseElement : parsed.getAsJsonArray()) {
				if (!releaseElement.isJsonObject()) {
					continue;
				}
				JsonObject release = releaseElement.getAsJsonObject();
				if (!release.has("assets") || !release.get("assets").isJsonArray()) {
					continue;
				}
				for (JsonElement assetElement : release.getAsJsonArray("assets")) {
					if (!assetElement.isJsonObject()) {
						continue;
					}
					JsonObject asset = assetElement.getAsJsonObject();
					if (asset.has("download_count") && !asset.get("download_count").isJsonNull()) {
						sum += asset.get("download_count").getAsLong();
					}
				}
			}
			return Optional.of(sum);
		} catch (CatalogException e) {
			LOGGER.info("Downloadzahl von '{}/{}' nicht abrufbar: {}", ref.owner(), ref.repo(), e.getMessage());
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException | NumberFormatException | ClassCastException e) {
			LOGGER.warn("Downloadzahl von '{}/{}': kaputte Antwort: {}", ref.owner(), ref.repo(), e.toString());
			return Optional.empty();
		}
	}

	private static String cacheKey(GitHubRepositoryReference ref, String suffix) {
		return "github-" + suffix + "-" + ref.owner() + "-" + ref.repo();
	}
}
