package de.spassglas.vitrine.addon.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import de.spassglas.vitrine.script.ScriptExpr;
import de.spassglas.vitrine.script.ScriptParseException;
import de.spassglas.vitrine.script.ScriptParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parst den Inhalt genau einer {@code scripts/<name>.vs}-Datei (ROADMAP AD2)
 * in ein {@link ScriptAddon}. Aufgebaut nach demselben Muster wie {@link
 * de.spassglas.vitrine.addon.AddonRule#parse}: feste Grammatik, geschlossene
 * Whitelists ({@link ScriptAddonEvent}, {@link ScriptAddonAction}), jede
 * eingebettete Formel/Bedingung geht an {@link ScriptParser} (die X5-Sandbox)
 * -- nie ein eigener zweiter Ausdrucks-Parser.
 *
 * <p><b>Format</b> (ein JSON-Objekt je Datei):</p>
 * <pre>{
 *   "ereignis": "welt_betreten",
 *   "bedingung": { "op": "lit", "value": true },
 *   "aktionen": [
 *     { "nachricht": { "op": "lit", "value": "Willkommen!" } },
 *     { "ton": "minecraft:entity.experience_orb.pickup" },
 *     { "hud_wert": { "schluessel": "punkte", "wert": { "op": "lit", "value": 1 } } }
 *   ],
 *   "befehl": "gruss"
 * }</pre>
 * <p>{@code "bedingung"} ist optional (fehlt sie, feuert das Skript
 * bedingungslos). {@code "befehl"} ist nur bei {@code "ereignis": "befehl"}
 * erlaubt/erforderlich und muss zu {@code /v <name>} passen.</p>
 */
public final class ScriptAddonParser {

	/** Wie {@link de.spassglas.vitrine.addon.AddonRule#MAX_ACTIONS} -- dieselbe Deckelung, derselbe Grund. */
	public static final int MAX_ACTIONS = 16;

	/** Erlaubtes Muster fuer {@code befehl} (passend zu {@code /v <name>}) und {@code hud_wert.schluessel}. */
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_]{1,32}");

	/** Obergrenze fuer eine Sound-Id -- reine Eingabe-Hygiene, keine Registry-Pruefung (die passiert erst zur Laufzeit). */
	private static final int MAX_SOUND_ID_LENGTH = 128;

	private ScriptAddonParser() {
	}

	/** Wie {@link #parse(String)}, mit UTF-8-Bytes einer {@code .vs}-Datei. */
	public static ScriptAddon parse(byte[] json) throws ScriptAddonParseException {
		return parse(new String(json, StandardCharsets.UTF_8));
	}

	public static ScriptAddon parse(String json) throws ScriptAddonParseException {
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new ScriptAddonParseException("Addon-Skript ist kein gueltiges JSON: " + e.getMessage());
		}
		if (!root.isJsonObject()) {
			throw new ScriptAddonParseException("ein Addon-Skript muss ein JSON-Objekt sein");
		}
		JsonObject node = root.getAsJsonObject();

		String eventId = requireString(node, "ereignis");
		ScriptAddonEvent event = ScriptAddonEvent.byId(eventId).orElseThrow(() -> new ScriptAddonParseException(
				"unbekanntes Ereignis '" + eventId + "' (erlaubt: tick, welt_betreten, befehl, taste)"));

		ScriptExpr condition = null;
		if (node.has("bedingung")) {
			condition = parseExpr(node.get("bedingung"), "bedingung");
		}

		String commandName = null;
		if (event == ScriptAddonEvent.BEFEHL) {
			commandName = requireString(node, "befehl");
			if (!IDENTIFIER.matcher(commandName).matches()) {
				throw new ScriptAddonParseException(
						"'befehl' muss aus Kleinbuchstaben, Ziffern und '_' bestehen (1-32 Zeichen), war '"
								+ commandName + "'");
			}
		} else if (node.has("befehl")) {
			throw new ScriptAddonParseException("'befehl' ist nur fuer das Ereignis 'befehl' erlaubt");
		}

		List<ScriptAddonAction> actions = parseActions(node);

		try {
			return new ScriptAddon(event, condition, actions, commandName);
		} catch (IllegalArgumentException e) {
			throw new ScriptAddonParseException(e.getMessage(), e);
		}
	}

	private static List<ScriptAddonAction> parseActions(JsonObject node) throws ScriptAddonParseException {
		if (!node.has("aktionen") || !node.get("aktionen").isJsonArray()) {
			throw new ScriptAddonParseException("'aktionen' fehlt oder ist kein Array");
		}
		JsonArray array = node.getAsJsonArray("aktionen");
		if (array.isEmpty() || array.size() > MAX_ACTIONS) {
			throw new ScriptAddonParseException("'aktionen' braucht 1 bis " + MAX_ACTIONS + " Eintraege, hatte "
					+ array.size());
		}
		List<ScriptAddonAction> actions = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				throw new ScriptAddonParseException("jede Aktion muss ein JSON-Objekt sein");
			}
			actions.add(parseAction(element.getAsJsonObject()));
		}
		return actions;
	}

	private static ScriptAddonAction parseAction(JsonObject action) throws ScriptAddonParseException {
		if (action.has("nachricht")) {
			return new ScriptAddonAction.ZeigeNachricht(parseExpr(action.get("nachricht"), "nachricht"));
		}
		if (action.has("ton")) {
			JsonElement value = action.get("ton");
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				throw new ScriptAddonParseException("'ton' braucht ein String-Feld (eine Sound-Id)");
			}
			String soundId = value.getAsString();
			if (soundId.isBlank() || soundId.length() > MAX_SOUND_ID_LENGTH) {
				throw new ScriptAddonParseException(
						"'ton' muss 1 bis " + MAX_SOUND_ID_LENGTH + " Zeichen lang sein, war " + soundId.length());
			}
			return new ScriptAddonAction.SpieleTon(soundId);
		}
		if (action.has("hud_wert")) {
			if (!action.get("hud_wert").isJsonObject()) {
				throw new ScriptAddonParseException("'hud_wert' braucht ein Objekt mit 'schluessel' und 'wert'");
			}
			JsonObject hudWert = action.getAsJsonObject("hud_wert");
			String key = requireString(hudWert, "schluessel");
			if (!IDENTIFIER.matcher(key).matches()) {
				throw new ScriptAddonParseException(
						"'hud_wert.schluessel' muss aus Kleinbuchstaben, Ziffern und '_' bestehen (1-32 Zeichen), war '"
								+ key + "'");
			}
			ScriptExpr value = parseExpr(requireChild(hudWert, "wert"), "hud_wert.wert");
			return new ScriptAddonAction.SetzeHudWert(key, value);
		}
		throw new ScriptAddonParseException(
				"unbekannte Aktion " + action.keySet() + " (erlaubt: nachricht, ton, hud_wert)");
	}

	private static JsonElement requireChild(JsonObject node, String field) throws ScriptAddonParseException {
		if (!node.has(field)) {
			throw new ScriptAddonParseException("Feld '" + field + "' fehlt");
		}
		return node.get(field);
	}

	private static ScriptExpr parseExpr(JsonElement element, String field) throws ScriptAddonParseException {
		try {
			return ScriptParser.parse(element.toString());
		} catch (ScriptParseException e) {
			throw new ScriptAddonParseException("'" + field + "': " + e.getMessage(), e);
		}
	}

	private static String requireString(JsonObject node, String field) throws ScriptAddonParseException {
		if (!node.has(field) || !node.get(field).isJsonPrimitive() || !node.get(field).getAsJsonPrimitive().isString()) {
			throw new ScriptAddonParseException("'" + field + "' fehlt oder ist kein String");
		}
		return node.get(field).getAsString().toLowerCase(Locale.ROOT);
	}
}
