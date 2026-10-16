package de.spassglas.vitrine.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parst und validiert eine {@code hud/*.json}-Zeichenkette gegen das Schema
 * {@code registry/schema/hud.schema.json} (ROADMAP T5) und baut daraus ein
 * {@link HudLayout}. Aufgebaut nach demselben Muster wie
 * {@code de.spassglas.vitrine.manifest.ManifestParser} (P1): im Jar
 * eingebettete, byte-identische Schema-Kopie ({@link HudSchemaResourceSyncTest}
 * haelt beide synchron), reiner Draft-2020-12-Teilmengen-Auswerter
 * ({@link HudSchemaValidator}) statt einer neuen Abhaengigkeit.
 *
 * <p>Ueber die reine Schema-Pruefung hinaus setzt dieser Parser eine
 * Laufzeitregel durch, die JSON Schema fuer das Unterfeld eines
 * Array-Elements nicht ausdruecken kann: <b>Widget-Ids muessen innerhalb
 * eines HUD-Pakets eindeutig sein</b> (siehe {@code widgets}-Beschreibung im
 * Schema). Eine doppelte Id fuehrt zu einer {@link HudParseException} mit
 * klarer Meldung, nicht zu stillschweigendem Ueberschreiben.</p>
 *
 * <p>Beim Bauen des Modells werden die im Schema-Kopf dokumentierten Defaults
 * gesetzt: fehlendes {@code offset} = {@link HudOffset#ZERO}, fehlendes
 * {@code scale} = {@code 1.0}, fehlendes {@code visibility} =
 * {@link HudVisibility.Always}, fehlendes {@code min}/{@code max} eines
 * Balkens = {@code 0.0}/{@code 1.0}, fehlendes {@code label} =
 * {@code ""}, fehlendes {@code decimals} = {@code null} (Renderer entscheidet,
 * siehe {@link HudDataSource#defaultDecimals()}).</p>
 */
public final class HudLayoutParser {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	/** Resource-Name der eingebetteten, byte-identischen Schema-Kopie, im selben Package. */
	private static final String SCHEMA_RESOURCE_NAME = "hud.schema.json";

	private static volatile JsonObject cachedSchema;

	private HudLayoutParser() {
	}

	/** Wie {@link #parse(String)}, mit UTF-8-Bytes einer {@code hud/*.json}. */
	public static HudLayout parse(byte[] json) throws HudParseException {
		return parse(new String(json, StandardCharsets.UTF_8));
	}

	/**
	 * Parst {@code json} (Inhalt einer {@code hud/*.json}) und validiert es
	 * gegen {@code hud.schema.json} plus die Id-Eindeutigkeitsregel.
	 *
	 * @throws HudParseException wenn {@code json} kein gueltiges JSON ist, das
	 *                           Schema verletzt oder doppelte Widget-Ids
	 *                           enthaelt -- die Meldung nennt konkret, was.
	 */
	public static HudLayout parse(String json) throws HudParseException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new HudParseException("hud.json ist kein gueltiges JSON: " + e.getMessage());
		}
		if (parsed == null || !parsed.isJsonObject()) {
			throw new HudParseException("hud.json muss ein JSON-Objekt als Wurzelelement haben, ist aber "
					+ (parsed == null || parsed.isJsonNull() ? "null" : parsed.getClass().getSimpleName()));
		}

		List<String> violations = HudSchemaValidator.validate(loadSchema(), parsed);
		if (!violations.isEmpty()) {
			String message = "hud.json entspricht nicht dem Schema: " + String.join("; ", violations);
			LOGGER.debug("HUD-JSON abgelehnt: {}", message);
			throw new HudParseException(message, violations);
		}

		JsonObject root = parsed.getAsJsonObject();
		List<HudWidget> widgets = new ArrayList<>();
		Set<String> seenIds = new LinkedHashSet<>();
		List<String> duplicateIds = new ArrayList<>();
		for (JsonElement widgetElement : root.getAsJsonArray("widgets")) {
			HudWidget widget = toWidget(widgetElement.getAsJsonObject());
			if (!seenIds.add(widget.id())) {
				duplicateIds.add(widget.id());
			}
			widgets.add(widget);
		}
		if (!duplicateIds.isEmpty()) {
			String message = "hud.json vergibt Widget-Id(s) mehrfach: " + String.join(", ", duplicateIds)
					+ " -- jede 'id' muss innerhalb eines HUD-Pakets eindeutig sein "
					+ "(Laufzeitpruefung, siehe hud.schema.json, Beschreibung von 'widgets')";
			LOGGER.debug("HUD-JSON abgelehnt: {}", message);
			throw new HudParseException(message, List.of(message));
		}

		return new HudLayout(root.get("schemaVersion").getAsInt(), widgets);
	}

	private static HudWidget toWidget(JsonObject widget) {
		String id = widget.get("id").getAsString();
		HudAnchor anchor = HudAnchor.byId(widget.get("anchor").getAsString()).orElseThrow();
		HudOffset offset = widget.has("offset") ? toOffset(widget.getAsJsonObject("offset")) : HudOffset.ZERO;
		double scale = widget.has("scale") ? widget.get("scale").getAsDouble() : HudWidget.DEFAULT_SCALE;
		HudVisibility visibility = widget.has("visibility")
				? toVisibility(widget.getAsJsonObject("visibility"))
				: HudVisibility.Always.INSTANCE;

		return switch (widget.get("type").getAsString()) {
			case "text" -> new HudWidget.Text(id, anchor, offset, scale, visibility,
					toTextContent(widget.getAsJsonObject("content")));
			case "icon" -> new HudWidget.Icon(id, anchor, offset, scale, visibility,
					widget.get("texture").getAsString(),
					optionalInt(widget, "width"), optionalInt(widget, "height"));
			case "bar" -> new HudWidget.Bar(id, anchor, offset, scale, visibility,
					HudDataSource.byId(widget.get("source").getAsString()).orElseThrow(),
					widget.has("min") ? widget.get("min").getAsDouble() : HudWidget.Bar.DEFAULT_MIN,
					widget.has("max") ? widget.get("max").getAsDouble() : HudWidget.Bar.DEFAULT_MAX,
					optionalInt(widget, "width"), optionalInt(widget, "height"),
					widget.has("color") ? parseHexColorArgb(widget.get("color").getAsString()) : null);
			case "list" -> new HudWidget.List(id, anchor, offset, scale, visibility,
					toTextContents(widget.getAsJsonArray("entries")));
			default -> throw new IllegalStateException(
					"hud.schema.json haette den Widget-Typ '" + widget.get("type").getAsString()
							+ "' abweisen muessen, bevor toWidget() ihn sieht");
		};
	}

	private static HudOffset toOffset(JsonObject offset) {
		return new HudOffset(offset.get("x").getAsInt(), offset.get("y").getAsInt());
	}

	private static HudVisibility toVisibility(JsonObject visibility) {
		return switch (visibility.get("type").getAsString()) {
			case "always" -> HudVisibility.Always.INSTANCE;
			case "dimension" -> new HudVisibility.InDimension(visibility.get("dimension").getAsString());
			case "debugScreen" -> new HudVisibility.DebugScreen("on".equals(visibility.get("debugScreen").getAsString()));
			case "outsideMenu" -> HudVisibility.OutsideMenu.INSTANCE;
			default -> throw new IllegalStateException(
					"hud.schema.json haette die Sichtbarkeitsbedingung '" + visibility.get("type").getAsString()
							+ "' abweisen muessen");
		};
	}

	private static List<HudTextContent> toTextContents(JsonArray entries) {
		List<HudTextContent> result = new ArrayList<>(entries.size());
		for (JsonElement entry : entries) {
			result.add(toTextContent(entry.getAsJsonObject()));
		}
		return result;
	}

	private static HudTextContent toTextContent(JsonObject content) {
		if ("static".equals(content.get("kind").getAsString())) {
			return new HudTextContent.Static(content.get("value").getAsString());
		}
		return new HudTextContent.Dynamic(
				HudDataSource.byId(content.get("source").getAsString()).orElseThrow(),
				content.has("label") ? content.get("label").getAsString() : "",
				optionalInt(content, "decimals"));
	}

	private static Integer optionalInt(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : null;
	}

	/** {@code "#rrggbb"} (bereits vom Schema-Muster geprueft) -> voll deckendes ARGB. */
	private static int parseHexColorArgb(String hex) {
		return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
	}

	/**
	 * Laedt die eingebettete Schema-Kopie einmalig aus dem Klassenpfad
	 * (selbes Package, siehe
	 * {@code mod/src/main/resources/de/spassglas/vitrine/hud/hud.schema.json}).
	 * Byte-identisch zu {@code registry/schema/hud.schema.json} --
	 * {@link HudSchemaResourceSyncTest} haelt beide zusammen.
	 */
	private static JsonObject loadSchema() {
		JsonObject schema = cachedSchema;
		if (schema != null) {
			return schema;
		}
		synchronized (HudLayoutParser.class) {
			if (cachedSchema == null) {
				cachedSchema = readSchemaResource();
			}
			return cachedSchema;
		}
	}

	private static JsonObject readSchemaResource() {
		try (InputStream in = HudLayoutParser.class.getResourceAsStream(SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME
						+ "' im Package " + HudLayoutParser.class.getPackageName() + " fehlt -- Build defekt.");
			}
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			LOGGER.error("Konnte eingebettete Schema-Resource '{}' nicht lesen: {}", SCHEMA_RESOURCE_NAME, e.toString(), e);
			throw new UncheckedIOException(
					"Eingebettete Schema-Resource '" + SCHEMA_RESOURCE_NAME + "' liess sich nicht lesen", e);
		}
	}
}
