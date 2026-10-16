package de.spassglas.vitrine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Liest und schreibt {@code settings.json} im Vitrine-Konfigurationsordner
 * (im echten Spiel {@code config/vitrine/settings.json}, ueber
 * {@code FabricLoader.getInstance().getConfigDir().resolve("vitrine")}).
 *
 * <p>Drei Faelle, die diese Klasse abdeckt:</p>
 * <ul>
 *   <li>Datei fehlt: Default anlegen und atomar schreiben.</li>
 *   <li>Datei ist gueltig, aber in einer aelteren Schema-Version: ueber
 *       {@link SchemaMigrations} hochziehen, danach die migrierte Fassung
 *       zurueckschreiben.</li>
 *   <li>Datei ist kein gueltiges JSON, hat kein brauchbares
 *       {@code schemaVersion}-Feld oder eine Version, fuer die es keinen
 *       Migrationsweg gibt: Originaldatei nach {@code settings.json.bak}
 *       (bzw. einer eindeutig durchnummerierten Variante) sichern, Default
 *       zurueckgeben und schreiben. Es wird nie eine Exception aus dem Laden
 *       nach aussen geworfen.</li>
 * </ul>
 */
public final class SettingsManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Config");
	private static final String SETTINGS_FILE_NAME = "settings.json";
	private static final String BACKUP_SUFFIX = ".bak";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SettingsManager() {
	}

	/**
	 * Laedt die Einstellungen aus {@code vitrineConfigDir/settings.json}.
	 * Wirft nie -- jeder Fehlerfall faellt auf Default-Werte zurueck, siehe
	 * Klassendokumentation.
	 *
	 * @param vitrineConfigDir der Vitrine-eigene Konfigurationsordner (z. B.
	 *                          {@code config/vitrine}), nicht die Datei selbst.
	 */
	public static VitrineSettings load(Path vitrineConfigDir) {
		Path file = vitrineConfigDir.resolve(SETTINGS_FILE_NAME);

		if (!Files.exists(file)) {
			LOGGER.info("Keine {} gefunden, lege Standardwerte an.", file);
			VitrineSettings defaults = VitrineSettings.createDefault();
			trySave(vitrineConfigDir, defaults);
			return defaults;
		}

		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht lesen, verwende Standardwerte: {}", file, e.toString(), e);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		JsonObject root;
		try {
			JsonElement parsed = com.google.gson.JsonParser.parseString(content);
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("Wurzelelement ist kein JSON-Objekt");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			LOGGER.warn("{} enthaelt kein gueltiges JSON-Objekt, sichere Datei und verwende Standardwerte: {}",
					file, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		Integer storedVersion = readSchemaVersion(root);
		if (storedVersion == null) {
			LOGGER.warn("{} hat kein gueltiges 'schemaVersion'-Feld, sichere Datei und verwende Standardwerte.", file);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		JsonObject migrated;
		boolean wasMigrated = storedVersion < VitrineSettings.CURRENT_SCHEMA_VERSION;
		try {
			migrated = SchemaMigrations.upgrade(root, storedVersion, VitrineSettings.CURRENT_SCHEMA_VERSION,
					SchemaMigrations.STEPS);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// storedVersion > CURRENT_SCHEMA_VERSION (neuere, uns unbekannte
			// Version) oder eine Version ohne registrierten Migrationsweg.
			LOGGER.warn("{} hat Schema-Version {}, die dieser Mod nicht lesen kann ({}); "
					+ "sichere Datei und verwende Standardwerte.", file, storedVersion, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		VitrineSettings settings;
		try {
			settings = GSON.fromJson(migrated, VitrineSettings.class);
		} catch (JsonSyntaxException e) {
			LOGGER.warn("{} liess sich nicht auf {} abbilden, sichere Datei und verwende Standardwerte: {}",
					file, VitrineSettings.class.getSimpleName(), e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}
		if (settings == null) {
			LOGGER.warn("{} ergab keine Einstellungen (leeres Dokument), sichere Datei und verwende Standardwerte.",
					file);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		settings.schemaVersion = VitrineSettings.CURRENT_SCHEMA_VERSION;

		if (wasMigrated) {
			LOGGER.info("{} von Schema-Version {} auf {} migriert.", file, storedVersion,
					VitrineSettings.CURRENT_SCHEMA_VERSION);
			trySave(vitrineConfigDir, settings);
		}

		return settings;
	}

	/**
	 * Schreibt {@code settings} atomar (temporaere Datei im selben Ordner,
	 * dann {@code ATOMIC_MOVE}) nach {@code vitrineConfigDir/settings.json}.
	 */
	public static void save(Path vitrineConfigDir, VitrineSettings settings) throws IOException {
		Files.createDirectories(vitrineConfigDir);
		Path target = vitrineConfigDir.resolve(SETTINGS_FILE_NAME);
		String json = GSON.toJson(settings);

		Path tmp = Files.createTempFile(vitrineConfigDir, SETTINGS_FILE_NAME, ".tmp");
		try {
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException cleanupFailure) {
				LOGGER.warn("Konnte temporaere Datei {} nach fehlgeschlagenem Schreiben nicht entfernen: {}",
						tmp, cleanupFailure.toString());
			}
			throw e;
		}
	}

	private static void trySave(Path vitrineConfigDir, VitrineSettings settings) {
		try {
			save(vitrineConfigDir, settings);
		} catch (IOException e) {
			LOGGER.error("Konnte Standardwerte nicht nach {} schreiben: {}", vitrineConfigDir, e.toString(), e);
		}
	}

	private static Integer readSchemaVersion(JsonObject root) {
		if (!root.has("schemaVersion")) {
			return null;
		}
		JsonElement element = root.get("schemaVersion");
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		if (value != Math.floor(value) || Double.isInfinite(value) || value < 0) {
			return null;
		}
		return (int) value;
	}

	/**
	 * Sichert eine kaputte Datei unter einem eindeutigen Namen und liefert
	 * frische Default-Einstellungen zurueck (die zusaetzlich sofort als neue
	 * {@code settings.json} geschrieben werden).
	 */
	private static VitrineSettings recoverWithBackup(Path vitrineConfigDir, Path brokenFile) {
		try {
			Path backup = uniqueBackupPath(brokenFile);
			Files.move(brokenFile, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Kaputte Konfigurationsdatei nach {} gesichert.", backup);
		} catch (IOException e) {
			LOGGER.error("Konnte kaputte Konfigurationsdatei {} nicht sichern: {}", brokenFile, e.toString(), e);
		}

		VitrineSettings defaults = VitrineSettings.createDefault();
		trySave(vitrineConfigDir, defaults);
		return defaults;
	}

	private static Path uniqueBackupPath(Path original) {
		Path parent = original.getParent();
		String fileName = original.getFileName().toString();
		Path candidate = parent.resolve(fileName + BACKUP_SUFFIX);
		int counter = 1;
		while (Files.exists(candidate)) {
			candidate = parent.resolve(fileName + BACKUP_SUFFIX + "." + counter);
			counter++;
		}
		return candidate;
	}
}
