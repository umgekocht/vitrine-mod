package de.spassglas.vitrine.addon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Eine Verhaltensregel eines Addons ({@code regeln/*.json}, ROADMAP AD2):
 * "wenn <Ereignis> [mit <Ziel>], dann <Aktionen>". Regeln wirken sofort beim
 * Einschalten -- kein Neustart, kein Welt-Neuladen (umgekocht, 2026-10-06:
 * "wirklich modden, blos ohne reload").
 *
 * <p>Sicherheit: nur die festen Ereignisse aus {@link #EVENTS} und Aktionen
 * aus {@link Action.Kind}; Befehle nur mit Wurzeln aus
 * {@link #ALLOWED_COMMANDS}; alle Mengen gedeckelt. Es wird nie Java-Code
 * aus einem Paket geladen.</p>
 *
 * <pre>{"regeln": [{"wenn": "block_abgebaut", "ziel": "minecraft:diamond_ore",
 *   "dann": [{"nachricht": "Diamant!", "actionbar": true},
 *            {"effekt": "minecraft:haste", "sekunden": 10, "stufe": 1}]}]}</pre>
 */
public record AddonRule(String event, String target, int everySeconds, double chance, List<Action> actions) {

	/** block_abgebaut, item_benutzt, beigetreten, mob_besiegt, timer. */
	public static final Set<String> EVENTS = Set.of("block_abgebaut", "item_benutzt", "beigetreten", "mob_besiegt",
			"timer");

	public static final Set<String> ALLOWED_COMMANDS = Set.of("give", "effect", "summon", "particle", "playsound",
			"title", "tellraw", "xp", "experience", "weather", "time", "setblock", "say", "enchant");

	public static final int MAX_RULES = 64;
	public static final int MAX_ACTIONS = 16;

	public record Action(Kind kind, String value, int amount, int level, boolean actionbar) {
		public enum Kind { NACHRICHT, GEBEN, EFFEKT, XP, BEFEHL }
	}

	public static List<AddonRule> parse(byte[] json) {
		JsonElement root = JsonParser.parseString(new String(json, java.nio.charset.StandardCharsets.UTF_8));
		JsonArray rules = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("regeln");
		if (rules == null) {
			throw new IllegalArgumentException("'regeln' fehlt");
		}
		if (rules.size() > MAX_RULES) {
			throw new IllegalArgumentException("mehr als " + MAX_RULES + " Regeln");
		}
		List<AddonRule> out = new ArrayList<>();
		for (JsonElement e : rules) {
			JsonObject o = e.getAsJsonObject();
			String event = str(o, "wenn").toLowerCase(Locale.ROOT);
			if (!EVENTS.contains(event)) {
				throw new IllegalArgumentException("unbekanntes Ereignis '" + event + "' (erlaubt: " + EVENTS + ")");
			}
			String target = o.has("ziel") ? str(o, "ziel") : null;
			int every = o.has("alle_sekunden") ? clamp(o.get("alle_sekunden").getAsInt(), 1, 3600) : 10;
			double chance = o.has("chance") ? Math.max(0, Math.min(1, o.get("chance").getAsDouble())) : 1.0;
			JsonArray dann = o.getAsJsonArray("dann");
			if (dann == null || dann.isEmpty() || dann.size() > MAX_ACTIONS) {
				throw new IllegalArgumentException("'dann' braucht 1 bis " + MAX_ACTIONS + " Aktionen");
			}
			List<Action> actions = new ArrayList<>();
			for (JsonElement a : dann) {
				actions.add(action(a.getAsJsonObject()));
			}
			out.add(new AddonRule(event, target, every, chance, List.copyOf(actions)));
		}
		return List.copyOf(out);
	}

	private static Action action(JsonObject a) {
		if (a.has("nachricht")) {
			String text = str(a, "nachricht");
			return new Action(Action.Kind.NACHRICHT, text.substring(0, Math.min(256, text.length())), 0, 0,
					a.has("actionbar") && a.get("actionbar").getAsBoolean());
		}
		if (a.has("geben")) {
			return new Action(Action.Kind.GEBEN, str(a, "geben"), clamp(intOr(a, "anzahl", 1), 1, 64), 0, false);
		}
		if (a.has("effekt")) {
			return new Action(Action.Kind.EFFEKT, str(a, "effekt"), clamp(intOr(a, "sekunden", 10), 1, 600),
					clamp(intOr(a, "stufe", 1), 1, 5), false);
		}
		if (a.has("xp")) {
			return new Action(Action.Kind.XP, "", clamp(a.get("xp").getAsInt(), 1, 1000), 0, false);
		}
		if (a.has("befehl")) {
			String cmd = str(a, "befehl").trim();
			if (cmd.startsWith("/")) {
				cmd = cmd.substring(1);
			}
			String root = cmd.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
			if (!ALLOWED_COMMANDS.contains(root) || cmd.length() > 300) {
				throw new IllegalArgumentException("Befehl '" + root + "' ist fuer Addons nicht erlaubt (erlaubt: "
						+ ALLOWED_COMMANDS + ")");
			}
			return new Action(Action.Kind.BEFEHL, cmd, 0, 0, false);
		}
		throw new IllegalArgumentException("unbekannte Aktion " + a.keySet());
	}

	private static String str(JsonObject o, String key) {
		if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
			throw new IllegalArgumentException("'" + key + "' fehlt");
		}
		return o.get(key).getAsString();
	}

	private static int intOr(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
