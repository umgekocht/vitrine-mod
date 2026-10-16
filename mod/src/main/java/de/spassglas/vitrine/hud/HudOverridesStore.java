package de.spassglas.vitrine.hud;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest und schreibt {@code config/vitrine/hud/overrides.json} -- die
 * Nutzer-Anpassungen des In-Game-HUD-Editors (ROADMAP T6). Exakt dasselbe
 * Muster wie {@code de.spassglas.vitrine.config.SettingsManager} und
 * {@code de.spassglas.vitrine.state.PackStateStore}:
 *
 * <ul>
 *   <li>Datei fehlt: leere Overrides (es wird nichts geschrieben, bis der
 *       Nutzer etwas anpasst).</li>
 *   <li>Datei ist kein gueltiges JSON-Objekt, hat kein brauchbares
 *       {@code schemaVersion}-Feld, eine unbekannte Version, oder inhaltlich
 *       widerspruechliche Eintraege (leere Ids, unbekannter Anker, halber
 *       Versatz, doppelte {@code (packId, widgetId)}): Originaldatei nach
 *       {@code overrides.json.bak} (bzw. durchnummeriert) sichern, leere
 *       Overrides zurueckgeben. Es wird nie eine Exception aus {@link #load}
 *       nach aussen geworfen.</li>
 * </ul>
 *
 * <p>{@link #save} schreibt atomar: temporaere Datei im selben Ordner, dann
 * {@code ATOMIC_MOVE}.</p>
 */
public final class HudOverridesStore {

	/** Aktuelle Schema-Version von {@code overrides.json}. */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");
	private static final String HUD_SUBDIR = "hud";
	private static final String OVERRIDES_FILE_NAME = "overrides.json";
	private static final String BACKUP_SUFFIX = ".bak";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private HudOverridesStore() {
	}

	/** Laedt die Overrides aus {@code vitrineConfigDir/hud/overrides.json}. Wirft nie. */
	public static HudLayoutOverrides load(Path vitrineConfigDir) {
		Path file = vitrineConfigDir.resolve(HUD_SUBDIR).resolve(OVERRIDES_FILE_NAME);

		if (!Files.exists(file)) {
			return new HudLayoutOverrides(vitrineConfigDir, Map.of());
		}

		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht lesen, verwende leere HUD-Overrides: {}", file, e.toString(), e);
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
			LOGGER.warn("{} ist kein gueltiges JSON-Objekt, sichere Datei und verwende leere HUD-Overrides: {}",
					file, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		Integer storedVersion = readSchemaVersion(root);
		if (storedVersion == null) {
			LOGGER.warn("{} hat kein gueltiges 'schemaVersion'-Feld, sichere Datei und verwende leere HUD-Overrides.",
					file);
			return recoverWithBackup(vitrineConfigDir, file);
		}
		if (storedVersion != CURRENT_SCHEMA_VERSION) {
			LOGGER.warn("{} hat Schema-Version {}, die dieser Mod nicht lesen kann; "
					+ "sichere Datei und verwende leere HUD-Overrides.", file, storedVersion);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		FileModel model;
		try {
			model = GSON.fromJson(root, FileModel.class);
		} catch (JsonSyntaxException e) {
			LOGGER.warn("{} liess sich nicht auf das Override-Modell abbilden, sichere Datei und verwende "
					+ "leere HUD-Overrides: {}", file, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		Map<HudLayoutOverrides.Key, HudWidgetOverride> parsedEntries;
		try {
			parsedEntries = toEntries(model);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("{} enthaelt widerspruechliche Eintraege ({}), sichere Datei und verwende leere HUD-Overrides.",
					file, e.getMessage());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		return new HudLayoutOverrides(vitrineConfigDir, parsedEntries);
	}

	/**
	 * Schreibt {@code overrides} atomar nach
	 * {@code vitrineConfigDir/hud/overrides.json}. Ein fehlgeschlagenes
	 * Schreiben wird geloggt, nicht nach aussen geworfen -- der Editor darf
	 * daran nicht abstuerzen.
	 */
	public static void save(Path vitrineConfigDir, HudLayoutOverrides overrides) {
		Path hudDir = vitrineConfigDir.resolve(HUD_SUBDIR);
		Path target = hudDir.resolve(OVERRIDES_FILE_NAME);

		FileModel model = fromEntries(overrides.asMap());
		String json = GSON.toJson(model);

		Path tmp = null;
		try {
			Files.createDirectories(hudDir);
			tmp = Files.createTempFile(hudDir, OVERRIDES_FILE_NAME, ".tmp");
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Konnte HUD-Overrides nicht nach {} schreiben: {}", target, e.toString(), e);
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException cleanupFailure) {
					LOGGER.warn("Konnte temporaere Datei {} nach fehlgeschlagenem Schreiben nicht entfernen: {}",
							tmp, cleanupFailure.toString());
				}
			}
		}
	}

	private static Map<HudLayoutOverrides.Key, HudWidgetOverride> toEntries(FileModel model) {
		Map<HudLayoutOverrides.Key, HudWidgetOverride> result = new LinkedHashMap<>();
		if (model == null || model.overrides == null) {
			return result;
		}
		for (EntryModel entry : model.overrides) {
			if (entry == null) {
				throw new IllegalArgumentException("null-Eintrag");
			}
			if (entry.packId == null || entry.packId.isBlank()
					|| entry.widgetId == null || entry.widgetId.isBlank()) {
				throw new IllegalArgumentException("Eintrag ohne packId/widgetId");
			}
			HudAnchor anchor = null;
			if (entry.anchor != null) {
				anchor = HudAnchor.byId(entry.anchor)
						.orElseThrow(() -> new IllegalArgumentException("unbekannter Anker '" + entry.anchor + "'"));
			}
			if ((entry.offsetX == null) != (entry.offsetY == null)) {
				throw new IllegalArgumentException("halber Versatz bei '" + entry.widgetId + "'");
			}
			HudOffset offset = entry.offsetX != null ? new HudOffset(entry.offsetX, entry.offsetY) : null;
			HudWidgetOverride override = new HudWidgetOverride(anchor, offset, entry.visible);
			if (override.isEmpty()) {
				continue;
			}
			HudLayoutOverrides.Key key = new HudLayoutOverrides.Key(entry.packId, entry.widgetId);
			if (result.put(key, override) != null) {
				throw new IllegalArgumentException("doppelter Eintrag fuer " + key);
			}
		}
		return result;
	}

	private static FileModel fromEntries(Map<HudLayoutOverrides.Key, HudWidgetOverride> entries) {
		FileModel model = new FileModel();
		model.schemaVersion = CURRENT_SCHEMA_VERSION;
		model.overrides = new ArrayList<>();
		for (Map.Entry<HudLayoutOverrides.Key, HudWidgetOverride> e : entries.entrySet()) {
			HudWidgetOverride ov = e.getValue();
			if (ov.isEmpty()) {
				continue;
			}
			EntryModel entry = new EntryModel();
			entry.packId = e.getKey().packId();
			entry.widgetId = e.getKey().widgetId();
			entry.anchor = ov.anchor() != null ? ov.anchor().id() : null;
			entry.offsetX = ov.offset() != null ? ov.offset().x() : null;
			entry.offsetY = ov.offset() != null ? ov.offset().y() : null;
			entry.visible = ov.visible();
			model.overrides.add(entry);
		}
		return model;
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

	private static HudLayoutOverrides recoverWithBackup(Path vitrineConfigDir, Path brokenFile) {
		try {
			Path backup = uniqueBackupPath(brokenFile);
			Files.move(brokenFile, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Kaputte HUD-Override-Datei nach {} gesichert.", backup);
		} catch (IOException e) {
			LOGGER.error("Konnte kaputte HUD-Override-Datei {} nicht sichern: {}", brokenFile, e.toString(), e);
		}
		return new HudLayoutOverrides(vitrineConfigDir, Map.of());
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

	/** Gson-Abbild von {@code overrides.json}. */
	private static final class FileModel {
		int schemaVersion = CURRENT_SCHEMA_VERSION;
		List<EntryModel> overrides;
	}

	/** Ein Eintrag: {@code anchor}/{@code offsetX}/{@code offsetY}/{@code visible} je optional. */
	private static final class EntryModel {
		String packId;
		String widgetId;
		String anchor;
		Integer offsetX;
		Integer offsetY;
		Boolean visible;
	}
}
