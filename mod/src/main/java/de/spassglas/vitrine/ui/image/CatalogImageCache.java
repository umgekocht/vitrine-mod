package de.spassglas.vitrine.ui.image;

import de.spassglas.vitrine.Vitrine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laedt Vorschaubilder des Katalogs ({@code icon}-URL im Index) im Hintergrund,
 * prueft sie und haelt sie als Textur bereit.
 *
 * <p>Sicherheit: nur {@code https} auf GitHub-Hosts ({@link #ALLOWED_HOSTS}),
 * hoechstens {@link #MAX_BYTES}, nur PNG mit Kantenlaenge bis
 * {@link PackageImageBytes#MAX_PREVIEW_DIMENSION} -- dieselbe Pruefung wie
 * fuer Bilder in Paketen. Bilder sind reine Anzeige, sie werden nie
 * ausgefuehrt. Zwischenspeicher: {@code config/vitrine/cache/images/}, damit
 * der Store auch offline Bilder zeigt.</p>
 *
 * <p>Bis ein Bild da ist (oder wenn es scheitert), zeichnet die Kachel ihr
 * Kategoriebild -- {@link #get(String)} blockiert nie.</p>
 */
public final class CatalogImageCache {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/CatalogImages");

	static final Set<String> ALLOWED_HOSTS = Set.of("raw.githubusercontent.com", "umgekocht.github.io",
			"objects.githubusercontent.com", "cdn.modrinth.com",
			// Profilbilder der kommenden Vitrine-Website (Platzhalter-Domain, siehe AccountMenu)
			"vitrine.example");
	static final long MAX_BYTES = 8L * 1024 * 1024;

	private static final Map<String, PackageImageTextures.Texture> READY = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
	private static final java.util.concurrent.ExecutorService LOADER = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Vitrine-Catalog-Image");
		t.setDaemon(true);
		return t;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

	private CatalogImageCache() {
	}

	/** Ob {@code url} ein erlaubtes Katalogbild sein kann (https, erlaubter Host). */
	public static boolean isAllowedUrl(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		try {
			URI uri = URI.create(url);
			return "https".equals(uri.getScheme()) && uri.getHost() != null
					&& ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
					&& uri.getPath() != null && (uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
							|| uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".webp")
							|| uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".jpg")
							|| uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".jpeg"));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Die fertige Textur, oder leer -- dann laeuft der Abruf (einmalig) im Hintergrund an. */
	public static Optional<PackageImageTextures.Texture> get(String url) {
		PackageImageTextures.Texture ready = READY.get(url);
		if (ready != null) {
			return Optional.of(ready);
		}
		if (!isAllowedUrl(url) || FAILED.contains(url) || !PENDING.add(url)) {
			return Optional.empty();
		}
		// hoechstens zwei Bilder gleichzeitig: grosse Galeriebilder brauchen beim Entpacken viel Speicher
		LOADER.execute(() -> load(url));
		return Optional.empty();
	}

	private static void load(String url) {
		try {
			byte[] bytes = readCachedOrDownload(url);
			// Modrinth: verlustfreies WebP -> eigener Decoder (kein AWT in Minecrafts Laufzeit).
			WebpLossless.Image webp = null;
			int[] size;
			if (WebpLossless.isWebp(bytes)) {
				if (bytes.length > MAX_BYTES) {
					throw new IOException("Bild zu gross");
				}
				webp = WebpLossless.decode(bytes);
				size = new int[] {webp.width(), webp.height()};
			} else if (isJpeg(bytes)) {
				// Modrinth-Galerie: JPG ueber stb_image (liegt Minecraft ohnehin bei)
				if (bytes.length > MAX_BYTES) {
					throw new IOException("Bild zu gross");
				}
				webp = StbDecode.decode(bytes);
				size = new int[] {webp.width(), webp.height()};
			} else if (PackageImageBytes.pngSize(bytes) != null && bytes.length <= MAX_BYTES) {
				// nicht NativeImage.read(byte[]): das legt die Datei auf LWJGLs kleinen
				// MemoryStack und wirft bei PNGs ueber ~64 KB "Out of stack space".
				webp = StbDecode.decode(bytes);
				size = new int[] {webp.width(), webp.height()};
			} else {
				throw new IOException("kein gueltiges Bild im erlaubten Groessenrahmen");
			}
			if (webp != null) {
				webp = shrink(webp, MAX_TEXTURE_SIDE);
				size = new int[] {webp.width(), webp.height()};
			}
			WebpLossless.Image decoded = webp;
			int[] finalSize = size;
			MinecraftClient.getInstance().execute(() -> {
				try {
					NativeImage image = toNativeImage(decoded);
					NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
					texture.setFilter(true, false);
					Identifier id = Identifier.of(Vitrine.MOD_ID, "catalog_image/" + sha256(url).substring(0, 16));
					MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
					READY.put(url, new PackageImageTextures.Texture(id, finalSize[0], finalSize[1]));
				} catch (RuntimeException e) {
					fail(url, e);
				} finally {
					PENDING.remove(url);
				}
			});
		} catch (IOException | InterruptedException | RuntimeException e) {
			fail(url, e);
			PENDING.remove(url);
		}
	}

	/** Groesste Kantenlaenge einer Katalog-Textur -- Banner brauchen nicht mehr, und Speicher bleibt klein. */
	static final int MAX_TEXTURE_SIDE = 1280;

	/** Verkleinert per Flaechenmittel (scharf und ohne Flimmern), wenn groesser als {@code max}. */
	static WebpLossless.Image shrink(WebpLossless.Image img, int max) {
		int w = img.width();
		int h = img.height();
		if (w <= max && h <= max) {
			return img;
		}
		double f = (double) max / Math.max(w, h);
		int nw = Math.max(1, (int) Math.round(w * f));
		int nh = Math.max(1, (int) Math.round(h * f));
		int[] src = img.argb();
		int[] out = new int[nw * nh];
		for (int y = 0; y < nh; y++) {
			int y0 = y * h / nh;
			int y1 = Math.max(y0 + 1, (y + 1) * h / nh);
			for (int x = 0; x < nw; x++) {
				int x0 = x * w / nw;
				int x1 = Math.max(x0 + 1, (x + 1) * w / nw);
				long a = 0, r = 0, g = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < y1; yy++) {
					for (int xx = x0; xx < x1; xx++) {
						int p = src[yy * w + xx];
						a += p >>> 24;
						r += (p >> 16) & 0xff;
						g += (p >> 8) & 0xff;
						b += p & 0xff;
						n++;
					}
				}
				out[y * nw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return new WebpLossless.Image(nw, nh, out);
	}

	private static boolean isJpeg(byte[] b) {
		return b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF;
	}

	private static NativeImage toNativeImage(WebpLossless.Image img) {
		NativeImage image = new NativeImage(img.width(), img.height(), false);
		int[] px = img.argb();
		for (int y = 0; y < img.height(); y++) {
			for (int x = 0; x < img.width(); x++) {
				int argb = px[y * img.width() + x];
				// NativeImage erwartet ABGR
				int abgr = (argb & 0xff00ff00) | (argb >> 16 & 0xff) | (argb & 0xff) << 16;
				image.setColor(x, y, abgr);
			}
		}
		return image;
	}

	private static void fail(String url, Exception e) {
		FAILED.add(url);
		LOGGER.info("Katalogbild '{}' nicht geladen, Kategoriebild bleibt: {}", url, e.toString());
	}

	static boolean validPng(byte[] bytes) {
		int[] size = PackageImageBytes.pngSize(bytes);
		return size != null && bytes.length <= MAX_BYTES
				&& size[0] <= PackageImageBytes.MAX_PREVIEW_DIMENSION * 2
				&& size[1] <= PackageImageBytes.MAX_PREVIEW_DIMENSION * 2;
	}

	private static byte[] readCachedOrDownload(String url) throws IOException, InterruptedException {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID).resolve("cache").resolve("images");
		Path file = dir.resolve(sha256(url) + ".img");
		try {
			HttpResponse<InputStream> response = HTTP.send(
					HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
					HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("HTTP " + response.statusCode());
			}
			byte[] bytes;
			try (InputStream in = response.body()) {
				bytes = in.readNBytes((int) MAX_BYTES + 1);
			}
			if (bytes.length > MAX_BYTES) {
				throw new IOException("Bild groesser als " + MAX_BYTES + " Bytes");
			}
			Files.createDirectories(dir);
			Files.write(file, bytes);
			return bytes;
		} catch (IOException e) {
			// offline: letzter gespeicherter Stand
			if (Files.isRegularFile(file)) {
				return Files.readAllBytes(file);
			}
			throw e;
		}
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
