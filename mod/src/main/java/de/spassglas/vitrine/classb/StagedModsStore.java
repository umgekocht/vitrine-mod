package de.spassglas.vitrine.classb;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ROADMAP J2: liest und schreibt {@code staged-mods.json} im Vitrine-
 * Konfigordner -- die absturzsichere Liste der nach {@code mods/} gestageten
 * Klasse-B-Jars (Datei, Pruefsumme, Autor, Anzeigename, Zeitpunkt,
 * {@code pendingRestart}).
 *
 * <p>Exakt dasselbe Muster wie {@code de.spassglas.vitrine.state.PackStateStore}
 * und {@code de.spassglas.vitrine.hud.HudOverridesStore}:</p>
 * <ul>
 *   <li>Datei fehlt: leere Liste (es wird nichts geschrieben, bis etwas
 *       gestaget wird).</li>
 *   <li>Datei ist kein gueltiges JSON-Objekt, hat kein brauchbares
 *       {@code schemaVersion}-Feld, eine unbekannte Version oder inhaltlich
 *       widerspruechliche Eintraege (leere Ids/Dateinamen, doppelte Ids,
 *       unsicherer Dateiname): Originaldatei nach {@code staged-mods.json.bak}
 *       (bzw. durchnummeriert) sichern, leere Liste zurueckgeben. {@link #load}
 *       wirft nie -- das deckt insbesondere einen Absturz mitten im Schreiben
 *       ab (halb geschriebene, abgeschnittene Datei).</li>
 * </ul>
 *
 * <p>{@link #save} schreibt atomar: temporaere Datei im selben Ordner, dann
 * {@code ATOMIC_MOVE}.</p>
 */
final class StagedModsStore {

	static final int CURRENT_SCHEMA_VERSION = 1;

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/ClassB");
	private static final String FILE_NAME = "staged-mods.json";
	private static final String BACKUP_SUFFIX = ".bak";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private StagedModsStore() {
	}

	/** Laedt die Liste aus {@code stagingStateDir/staged-mods.json}. Wirft nie. */
	static List<StagedMod> load(Path stagingStateDir) {
		Path file = stagingStateDir.resolve(FILE_NAME);
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}

		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht lesen, verwende leere Liste: {}", file, e.toString(), e);
			return recoverWithBackup(file);
		}

		JsonObject root;
		try {
			JsonElement parsed = com.google.gson.JsonParser.parseString(content);
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("Wurzelelement ist kein JSON-Objekt");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			LOGGER.warn("{} ist kein gueltiges JSON-Objekt (Absturz mitten im Schreiben?), sichere Datei und "
					+ "verwende leere Liste: {}", file, e.toString());
			return recoverWithBackup(file);
		}

		Integer storedVersion = readSchemaVersion(root);
		if (storedVersion == null) {
			LOGGER.warn("{} hat kein gueltiges 'schemaVersion'-Feld, sichere Datei und verwende leere Liste.", file);
			return recoverWithBackup(file);
		}
		if (storedVersion != CURRENT_SCHEMA_VERSION) {
			LOGGER.warn("{} hat Schema-Version {}, die dieser Mod nicht lesen kann; sichere Datei und verwende "
					+ "leere Liste.", file, storedVersion);
			return recoverWithBackup(file);
		}

		FileModel model;
		try {
			model = GSON.fromJson(root, FileModel.class);
		} catch (JsonSyntaxException e) {
			LOGGER.warn("{} liess sich nicht auf das Staging-Modell abbilden, sichere Datei und verwende leere "
					+ "Liste: {}", file, e.toString());
			return recoverWithBackup(file);
		}

		try {
			return toEntries(model);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("{} enthaelt widerspruechliche Eintraege ({}), sichere Datei und verwende leere Liste.",
					file, e.getMessage());
			return recoverWithBackup(file);
		}
	}

	/**
	 * Schreibt {@code mods} atomar nach {@code stagingStateDir/staged-mods.json}
	 * (temporaere Datei + {@code ATOMIC_MOVE}). Stirbt der Prozess zwischen
	 * Temp-Datei und {@code move}, bleibt hoechstens die Temp-Datei halb
	 * geschrieben liegen -- {@code staged-mods.json} selbst wird nie
	 * angefasst, bevor der neue Inhalt vollstaendig geschrieben ist.
	 */
	static void save(Path stagingStateDir, List<StagedMod> mods) throws IOException {
		Files.createDirectories(stagingStateDir);
		Path target = stagingStateDir.resolve(FILE_NAME);

		FileModel model = new FileModel();
		model.schemaVersion = CURRENT_SCHEMA_VERSION;
		model.staged = new ArrayList<>();
		for (StagedMod m : mods) {
			EntryModel entry = new EntryModel();
			entry.id = m.id();
			entry.fileName = m.fileName();
			entry.sha256 = m.sha256();
			entry.author = m.author();
			entry.displayName = m.displayName();
			entry.stagedAt = m.stagedAt();
			entry.pendingRestart = m.pendingRestart();
			model.staged.add(entry);
		}
		String json = GSON.toJson(model);

		Path tmp = Files.createTempFile(stagingStateDir, FILE_NAME, ".tmp");
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

	private static List<StagedMod> toEntries(FileModel model) {
		List<StagedMod> result = new ArrayList<>();
		if (model == null || model.staged == null) {
			return result;
		}
		Set<String> seenIds = new HashSet<>();
		for (EntryModel entry : model.staged) {
			if (entry == null) {
				throw new IllegalArgumentException("null-Eintrag");
			}
			if (isBlank(entry.id) || isBlank(entry.fileName) || isBlank(entry.sha256)) {
				throw new IllegalArgumentException("Eintrag ohne id/fileName/sha256");
			}
			String name = entry.fileName.strip();
			if (name.contains("/") || name.contains("\\") || name.contains("..")
					|| (name.length() >= 2 && name.charAt(1) == ':')) {
				throw new IllegalArgumentException("unsicherer Dateiname '" + entry.fileName + "'");
			}
			if (!seenIds.add(entry.id)) {
				throw new IllegalArgumentException("doppelte id '" + entry.id + "'");
			}
			boolean pending = entry.pendingRestart == null || entry.pendingRestart;
			result.add(new StagedMod(entry.id, entry.fileName, entry.sha256,
					entry.author == null ? "" : entry.author,
					entry.displayName == null ? entry.id : entry.displayName,
					entry.stagedAt == null ? "" : entry.stagedAt,
					pending));
		}
		return result;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
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

	private static List<StagedMod> recoverWithBackup(Path brokenFile) {
		try {
			Path backup = uniqueBackupPath(brokenFile);
			Files.move(brokenFile, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Kaputte {} nach {} gesichert.", FILE_NAME, backup);
		} catch (IOException e) {
			LOGGER.error("Konnte kaputte {} nicht sichern: {}", brokenFile, e.toString(), e);
		}
		return new ArrayList<>();
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

	/** Gson-Abbild von {@code staged-mods.json}. */
	private static final class FileModel {
		int schemaVersion = CURRENT_SCHEMA_VERSION;
		List<EntryModel> staged;
	}

	private static final class EntryModel {
		String id;
		String fileName;
		String sha256;
		String author;
		String displayName;
		String stagedAt;
		Boolean pendingRestart;
	}
}
