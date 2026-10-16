package de.spassglas.vitrine.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Zugriff auf die oeffentliche Modrinth-API (api.modrinth.com/v2) -- nur lesen
 * und Dateien von cdn.modrinth.com laden. Kein Konto, kein Token.
 *
 * <p>Vertrauen: Modrinth-Inhalte tragen keine Vitrine-Signatur. Geprueft wird
 * die SHA-512-Pruefsumme, die die Modrinth-API zur Datei nennt, und der Host
 * (nur {@code cdn.modrinth.com}). Der Store kennzeichnet solche Angebote als
 * "Von Modrinth".</p>
 */
public final class ModrinthClient {

	public static final String GAME_VERSION = "1.21.1";
	private static final String API = "https://api.modrinth.com/v2/";
	private static final String USER_AGENT = "umgekocht/vitrine-mod/0.1 (Minecraft-Mod, github.com/umgekocht)";
	static final long MAX_DOWNLOAD = 150L * 1024 * 1024;

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private ModrinthClient() {
	}

	/** Ein Suchtreffer. {@code projectType}: {@code resourcepack} oder {@code mod}. */
	public record Hit(String id, String slug, String title, String author, String description, String iconUrl,
			long downloads, String projectType, String galleryUrl, int color) {
	}

	/** Die passende Datei einer Version samt Pflicht-Abhaengigkeiten (Projekt-IDs). */
	public record FileRef(String url, String filename, String sha512, long size, List<String> requiredProjects) {
	}

	/** Beliebteste Projekte eines Typs fuer {@link #GAME_VERSION} (Mods: nur Fabric). */
	public static List<Hit> popular(String projectType, int limit) throws IOException, InterruptedException {
		StringBuilder facets = new StringBuilder("[[");
		boolean news = projectType.startsWith("news");
		if (projectType.startsWith("top") || news) {
			// Startseite: meistgeladene Inhalte ueber alle Arten (Mods nur mit Opt-in)
			facets.append("\"project_type:resourcepack\",\"project_type:shader\",\"project_type:datapack\"");
			if (projectType.endsWith("_mods")) {
				facets.append(",\"project_type:mod\"");
			}
		} else {
			facets.append("\"project_type:").append(projectType).append("\"");
		}
		facets.append("],[\"versions:").append(GAME_VERSION).append("\"]");
		if ("mod".equals(projectType)) {
			facets.append(",[\"categories:fabric\"],[\"client_side:required\",\"client_side:optional\"]");
		}
		facets.append("]");
		return search(facets.toString(), limit, news ? "updated" : "downloads");
	}

	/** Neueste beliebte Projekte mit Galeriebild (fuer das Banner der Startseite). */
	public static List<Hit> featured(int limit) throws IOException, InterruptedException {
		String facets = "[[\"project_type:resourcepack\",\"project_type:mod\",\"project_type:shader\"],[\"versions:"
				+ GAME_VERSION + "\"]]";
		List<Hit> hits = new ArrayList<>();
		for (Hit h : search(facets, 30, "follows")) {
			if (hits.size() >= limit || h.galleryUrl() == null) {
				continue;
			}
			// Das Original-Upload (PNG/JPG) statt der verkleinerten WebP-Vorschau:
			// scharf im grossen Banner und ohne verlustbehaftetes WebP.
			String raw = rawGallery(h.id());
			if (raw != null) {
				hits.add(new Hit(h.id(), h.slug(), h.title(), h.author(), h.description(), h.iconUrl(), h.downloads(),
						h.projectType(), raw, h.color()));
			}
		}
		return hits;
	}

	/** {@code raw_url} des hervorgehobenen (sonst ersten) Galeriebilds, nur PNG/JPG. */
	private static String rawGallery(String projectId) throws IOException, InterruptedException {
		JsonObject p = JsonParser.parseString(get(API + "project/" + safeId(projectId))).getAsJsonObject();
		if (!p.has("gallery")) {
			return null;
		}
		String first = null;
		for (JsonElement e : p.getAsJsonArray("gallery")) {
			JsonObject g = e.getAsJsonObject();
			String raw = str(g, "raw_url");
			String lower = raw.toLowerCase(java.util.Locale.ROOT);
			if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
				continue;
			}
			if (g.has("featured") && g.get("featured").getAsBoolean()) {
				return raw;
			}
			if (first == null) {
				first = raw;
			}
		}
		return first;
	}

	private static List<Hit> search(String facets, int limit, String index) throws IOException, InterruptedException {
		String url = API + "search?limit=" + limit + "&index=" + index + "&facets="
				+ URLEncoder.encode(facets, StandardCharsets.UTF_8);
		return parseHits(get(url));
	}

	private static List<Hit> parseHits(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<Hit> hits = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("hits")) {
			JsonObject h = e.getAsJsonObject();
			hits.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "author"),
					str(h, "description"), str(h, "icon_url"), h.has("downloads") ? h.get("downloads").getAsLong() : 0,
					str(h, "project_type"), gallery(h),
					h.has("color") && !h.get("color").isJsonNull() ? h.get("color").getAsInt() : 0x2b2d33));
		}
		return hits;
	}

	/** Details fuer die Produktseite: Beschreibung, Galerie (PNG/JPG-Originale), Kategorien, Zahlen. */
	public record Project(String body, List<String> gallery, List<String> categories, long followers,
			String license, String updated) {
	}

	public static Project project(String projectId) throws IOException, InterruptedException {
		JsonObject p = JsonParser.parseString(get(API + "project/" + safeId(projectId))).getAsJsonObject();
		List<String> gallery = new ArrayList<>();
		if (p.has("gallery")) {
			for (JsonElement e : p.getAsJsonArray("gallery")) {
				JsonObject g = e.getAsJsonObject();
				String raw = str(g, "raw_url");
				String lower = raw.toLowerCase(java.util.Locale.ROOT);
				String pick = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? raw : str(g, "url");
				if (!pick.isEmpty()) {
					if (g.has("featured") && g.get("featured").getAsBoolean()) {
						gallery.add(0, pick);
					} else {
						gallery.add(pick);
					}
				}
			}
		}
		List<String> cats = new ArrayList<>();
		if (p.has("categories")) {
			for (JsonElement e : p.getAsJsonArray("categories")) {
				cats.add(e.getAsString());
			}
		}
		String license = p.has("license") && p.get("license").isJsonObject() ? str(p.getAsJsonObject("license"), "id") : "";
		return new Project(str(p, "body"), gallery, cats, p.has("followers") ? p.get("followers").getAsLong() : 0,
				license, str(p, "updated"));
	}

	/** Freitextsuche fuer 1.21.1 ueber alle Inhaltsarten (Mods nur mit Opt-in). */
	public static List<Hit> query(String text, boolean mods, int limit) throws IOException, InterruptedException {
		StringBuilder facets = new StringBuilder("[[\"project_type:resourcepack\",\"project_type:shader\",\"project_type:datapack\"");
		if (mods) {
			facets.append(",\"project_type:mod\"");
		}
		facets.append("],[\"versions:").append(GAME_VERSION).append("\"]]");
		String url = API + "search?limit=" + limit + "&index=relevance&query="
				+ URLEncoder.encode(text, StandardCharsets.UTF_8) + "&facets="
				+ URLEncoder.encode(facets.toString(), StandardCharsets.UTF_8);
		return parseHits(get(url));
	}

	/** Galeriebild in voller Groesse (ohne "_350"-Vorschau-Endung), sonst {@code null}. */
	private static String gallery(JsonObject h) {
		String u = str(h, "featured_gallery");
		if (u.isEmpty()) {
			u = null;
		}
		if (u == null && h.has("gallery") && h.getAsJsonArray("gallery").size() > 0) {
			u = h.getAsJsonArray("gallery").get(0).getAsString();
		}
		return u;
	}

	/** Neueste passende Datei eines Projekts fuer {@link #GAME_VERSION}. */
	public static Optional<FileRef> latestFile(String projectId, String projectType)
			throws IOException, InterruptedException {
		String query = "game_versions=" + URLEncoder.encode("[\"" + GAME_VERSION + "\"]", StandardCharsets.UTF_8);
		if ("mod".equals(projectType)) {
			query += "&loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
		}
		JsonArray versions = JsonParser.parseString(get(API + "project/" + safeId(projectId) + "/version?" + query))
				.getAsJsonArray();
		if (versions.isEmpty()) {
			return Optional.empty();
		}
		JsonObject v = versions.get(0).getAsJsonObject();
		JsonObject file = null;
		for (JsonElement f : v.getAsJsonArray("files")) {
			if (file == null || f.getAsJsonObject().get("primary").getAsBoolean()) {
				file = f.getAsJsonObject();
			}
		}
		if (file == null) {
			return Optional.empty();
		}
		List<String> required = new ArrayList<>();
		if (v.has("dependencies")) {
			for (JsonElement d : v.getAsJsonArray("dependencies")) {
				JsonObject dep = d.getAsJsonObject();
				if ("required".equals(str(dep, "dependency_type")) && dep.has("project_id")
						&& !dep.get("project_id").isJsonNull()) {
					required.add(dep.get("project_id").getAsString());
				}
			}
		}
		return Optional.of(new FileRef(str(file, "url"), str(file, "filename"),
				file.getAsJsonObject("hashes").get("sha512").getAsString(), file.get("size").getAsLong(), required));
	}

	/** Laedt {@code ref} nach {@code target}; nur cdn.modrinth.com, Groesse begrenzt, SHA-512 geprueft. */
	public static void download(FileRef ref, Path target) throws IOException, InterruptedException {
		URI uri = URI.create(ref.url());
		if (!"https".equals(uri.getScheme()) || !"cdn.modrinth.com".equals(uri.getHost())) {
			throw new IOException("Download nur von https://cdn.modrinth.com erlaubt: " + ref.url());
		}
		if (ref.size() > MAX_DOWNLOAD) {
			throw new IOException("Datei zu gross (" + ref.size() + " Bytes)");
		}
		if (!isSafeFileName(ref.filename())) {
			throw new IOException("Unsicherer Dateiname: " + ref.filename());
		}
		Path tmp = Files.createTempFile(target.getParent(), "vitrine-modrinth-", ".part");
		try {
			HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT)
					.timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			if (r.statusCode() != 200) {
				r.body().close();
				throw new IOException("HTTP " + r.statusCode());
			}
			MessageDigest sha = MessageDigest.getInstance("SHA-512");
			long total = 0;
			try (InputStream in = r.body(); var out = Files.newOutputStream(tmp)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) != -1) {
					total += n;
					if (total > MAX_DOWNLOAD) {
						throw new IOException("Download groesser als erlaubt");
					}
					sha.update(buf, 0, n);
					out.write(buf, 0, n);
				}
			}
			String actual = HexFormat.of().formatHex(sha.digest());
			if (!actual.equalsIgnoreCase(ref.sha512())) {
				throw new IOException("SHA-512 passt nicht zur Modrinth-Angabe");
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/** Nur einfache Dateinamen (kein Pfad), wie Modrinth sie vergibt. */
	static boolean isSafeFileName(String name) {
		return name != null && name.matches("[A-Za-z0-9._+\\-\\[\\]() ]{1,120}") && !name.contains("..")
				&& (name.toLowerCase(Locale.ROOT).endsWith(".zip") || name.toLowerCase(Locale.ROOT).endsWith(".jar"));
	}

	private static String safeId(String id) throws IOException {
		if (id == null || !id.matches("[A-Za-z0-9]{4,32}")) {
			throw new IOException("Ungueltige Modrinth-Projekt-ID");
		}
		return id;
	}

	private static String get(String url) throws IOException, InterruptedException {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT)
				.timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IOException("Modrinth antwortete mit HTTP " + r.statusCode());
		}
		return r.body();
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}
}
