package de.spassglas.vitrine.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ETag-Cache fuer Katalog-Ressourcen in {@code config/vitrine/cache/} (ROADMAP
 * K2). Je Ressource werden zwei Dateien atomar geschrieben (Temp +
 * {@code ATOMIC_MOVE}, wie {@code SettingsManager}):
 * <ul>
 *   <li>{@code &lt;key&gt;.body} -- der rohe Antwortkoerper (JSON-Bytes),</li>
 *   <li>{@code &lt;key&gt;.meta.json} -- {@code ETag} und {@code Last-Modified}.</li>
 * </ul>
 * Eine Folge-Abfrage schickt {@code If-None-Match}; bei {@code 304 Not Modified}
 * kommt der Rumpf aus diesem Cache. Eine kaputte oder nur teilweise geschriebene
 * Cache-Datei wird wie ein fehlender Eintrag behandelt -- nie ein Crash.
 */
public final class EtagCache {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Catalog");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]");

	private final Path cacheDir;

	public EtagCache(Path cacheDir) {
		this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir");
	}

	/** Gecachter Rumpf plus die Validatoren fuer die naechste bedingte Anfrage. */
	public record CacheEntry(byte[] body, String etag, String lastModified) {

		public CacheEntry {
			body = body.clone();
		}

		@Override
		public byte[] body() {
			return body.clone();
		}
	}

	/**
	 * Laedt den Cache-Eintrag zu {@code key}, oder {@link Optional#empty()}, wenn
	 * er fehlt, unvollstaendig ist oder sich nicht lesen laesst.
	 */
	public Optional<CacheEntry> load(String key) {
		String safe = sanitize(key);
		Path bodyFile = cacheDir.resolve(safe + ".body");
		Path metaFile = cacheDir.resolve(safe + ".meta.json");
		if (!Files.isRegularFile(bodyFile) || !Files.isRegularFile(metaFile)) {
			return Optional.empty();
		}
		try {
			byte[] body = Files.readAllBytes(bodyFile);
			JsonObject meta = JsonParser.parseString(
					Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
			return Optional.of(new CacheEntry(body, stringOrNull(meta, "etag"), stringOrNull(meta, "lastModified")));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOGGER.warn("Cache-Eintrag '{}' ist unlesbar, behandle ihn als fehlend: {}", safe, e.toString());
			return Optional.empty();
		}
	}

	/**
	 * Zeitpunkt der letzten Aenderung des gecachten Rumpfes zu {@code key}, oder
	 * {@link Optional#empty()}, wenn es keinen (lesbaren) Eintrag gibt. Fuer den
	 * Offline-Modus (ROADMAP K4): daraus wird das "du siehst den Stand von
	 * &lt;Datum&gt;" abgeleitet -- kein erfundenes Datum (Charter-Regel 2).
	 */
	public Optional<Instant> storedAt(String key) {
		String safe = sanitize(key);
		Path bodyFile = cacheDir.resolve(safe + ".body");
		if (!Files.isRegularFile(bodyFile)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.getLastModifiedTime(bodyFile).toInstant());
		} catch (IOException e) {
			LOGGER.warn("Cache-Eintrag '{}': Aenderungszeit nicht lesbar, behandle ihn als fehlend: {}",
					safe, e.toString());
			return Optional.empty();
		}
	}

	/**
	 * Schreibt Rumpf und Metadaten atomar. Ein Schreibfehler wird geloggt, aber
	 * nicht geworfen -- ein nicht geschriebener Cache ist kein Grund, den Abruf
	 * scheitern zu lassen.
	 */
	public void store(String key, byte[] body, String etag, String lastModified) {
		String safe = sanitize(key);
		try {
			Files.createDirectories(cacheDir);
			writeAtomically(cacheDir.resolve(safe + ".body"), body);

			JsonObject meta = new JsonObject();
			meta.addProperty("etag", etag);
			meta.addProperty("lastModified", lastModified);
			writeAtomically(cacheDir.resolve(safe + ".meta.json"),
					GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			LOGGER.warn("Cache-Eintrag '{}' liess sich nicht schreiben: {}", safe, e.toString());
		}
	}

	private void writeAtomically(Path target, byte[] data) throws IOException {
		Path tmp = Files.createTempFile(cacheDir, target.getFileName().toString(), ".tmp");
		try {
			Files.write(tmp, data);
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				// Netzfreigabe Z: kann ATOMIC_MOVE nicht immer -- Temp-Datei liegt
				// im selben Ordner, ein einfaches REPLACE ist der beste verfuegbare
				// Ersatz und weiterhin ein Ein-Schritt-rename auf demselben Volume.
				LOGGER.debug("ATOMIC_MOVE nicht unterstuetzt fuer {}, nutze REPLACE_EXISTING.", target);
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException cleanup) {
				LOGGER.warn("Temporaere Cache-Datei {} liess sich nach Fehler nicht entfernen: {}",
						tmp, cleanup.toString());
			}
			throw e;
		}
	}

	private static String stringOrNull(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
	}

	/** Macht aus einem Ressourcen-Schluessel einen sicheren, pfad-freien Dateinamen. */
	static String sanitize(String key) {
		Objects.requireNonNull(key, "key");
		String safe = UNSAFE.matcher(key).replaceAll("_");
		if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) {
			throw new IllegalArgumentException("Ungueltiger Cache-Schluessel: '" + key + "'");
		}
		return safe;
	}
}
