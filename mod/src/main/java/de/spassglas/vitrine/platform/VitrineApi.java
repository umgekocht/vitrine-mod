package de.spassglas.vitrine.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.spassglas.vitrine.platform.ApiFunction.bool;
import static de.spassglas.vitrine.platform.ApiFunction.fn;
import static de.spassglas.vitrine.platform.ApiFunction.num;
import static de.spassglas.vitrine.platform.ApiFunction.str;

/**
 * Die Vitrine-API v1, wie Addons sie als globales {@code vitrine} sehen --
 * identisch in Lua und JavaScript. Dokumentation: {@code docs/ADDON-API.md}.
 */
final class VitrineApi {

	static final Set<String> EVENTS = Set.of("tick", "hud", "join", "leave", "chat", "hurt", "unload");

	private VitrineApi() {
	}

	static Map<String, Object> build(LoadedAddon a) {
		Map<String, Object> api = new LinkedHashMap<>();
		MinecraftClient mc = MinecraftClient.getInstance();

		Map<String, Object> info = new LinkedHashMap<>();
		info.put("id", a.id);
		info.put("name", a.name);
		info.put("apiVersion", 1.0);
		api.put("addon", info);

		// --- Ausgabe ---
		api.put("log", (ApiFunction) args -> {
			StringBuilder sb = new StringBuilder();
			for (Object o : args) {
				sb.append(sb.length() > 0 ? " " : "").append(o);
			}
			AddonConsole.log(a.name, AddonConsole.Level.INFO, sb.toString());
			return null;
		});
		api.put("chat", (ApiFunction) args -> {
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal(str(args, 0, "")), false);
			}
			return null;
		});
		api.put("actionbar", (ApiFunction) args -> {
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal(str(args, 0, "")), true);
			}
			return null;
		});
		api.put("title", (ApiFunction) args -> {
			mc.inGameHud.setTitle(Text.literal(str(args, 0, "")));
			mc.inGameHud.setSubtitle(Text.literal(str(args, 1, "")));
			mc.inGameHud.setTitleTicks(5, (int) num(args, 2, 40), 10);
			return null;
		});

		// --- Ereignisse, Tasten, Zeitgeber ---
		api.put("on", (ApiFunction) args -> {
			String event = str(args, 0, "");
			Callback c = fn(args, 1);
			if (!EVENTS.contains(event)) {
				throw new IllegalArgumentException("unbekanntes Ereignis '" + event + "' (bekannt: " + EVENTS + ")");
			}
			if (c == null) {
				throw new IllegalArgumentException("on(ereignis, funktion): Funktion fehlt");
			}
			a.on(event, c);
			return null;
		});
		api.put("key", (ApiFunction) args -> {
			Callback c = fn(args, 1);
			if (c == null) {
				throw new IllegalArgumentException("key(taste, funktion): Funktion fehlt");
			}
			int code = PlatformHost.keyCode(str(args, 0, ""));
			if (code < 0) {
				throw new IllegalArgumentException("unbekannte Taste '" + str(args, 0, "") + "'");
			}
			a.keys.put(code, c);
			return null;
		});
		api.put("every", (ApiFunction) args -> timer(a, args, true));
		api.put("after", (ApiFunction) args -> timer(a, args, false));
		api.put("cancel", (ApiFunction) args -> {
			int id = (int) num(args, 0, -1);
			a.timers.removeIf(t -> t.id == id);
			return null;
		});

		// --- Spiel lesen ---
		api.put("player", (ApiFunction) args -> PlatformHost.player());
		api.put("world", (ApiFunction) args -> PlatformHost.world());
		api.put("block", (ApiFunction) args -> PlatformHost.block(num(args, 0, 0), num(args, 1, 0), num(args, 2, 0)));

		// --- Spiel veraendern ---
		api.put("command", (ApiFunction) args -> {
			PlatformHost.command(str(args, 0, ""));
			return null;
		});
		api.put("sound", (ApiFunction) args -> {
			PlatformHost.playSound(str(args, 0, "minecraft:ui.button.click"), num(args, 1, 1), num(args, 2, 1));
			return null;
		});
		api.put("particle", (ApiFunction) args -> {
			PlatformHost.particle(str(args, 0, "minecraft:happy_villager"), num(args, 1, 0), num(args, 2, 0),
					num(args, 3, 0), (int) num(args, 4, 10));
			return null;
		});
		api.put("shader", (ApiFunction) args -> {
			PlatformHost.setShader(a, str(args, 0, null));
			return null;
		});

		// --- Oberflaeche ---
		api.put("menu", (ApiFunction) args -> {
			Callback builder = fn(args, 1);
			if (builder == null) {
				throw new IllegalArgumentException("menu(titel, funktion(ui)): Funktion fehlt");
			}
			String title = str(args, 0, a.name);
			mc.send(() -> mc.setScreen(new ScriptMenuScreen(a, title, builder)));
			return null;
		});
		api.put("closeMenu", (ApiFunction) args -> {
			if (mc.currentScreen instanceof ScriptMenuScreen m && m.owner == a) {
				mc.send(() -> mc.setScreen(null));
			}
			return null;
		});

		// --- Speicher ---
		Map<String, Object> storage = new LinkedHashMap<>();
		storage.put("get", (ApiFunction) args -> PlatformHost.loadStorage(a).get(str(args, 0, "")));
		storage.put("set", (ApiFunction) args -> {
			Map<String, Object> data = PlatformHost.loadStorage(a);
			data.put(str(args, 0, ""), args.size() > 1 ? args.get(1) : null);
			PlatformHost.saveStorage(a, data);
			return null;
		});
		storage.put("all", (ApiFunction) args -> PlatformHost.loadStorage(a));
		api.put("storage", storage);

		// --- Weitere Dateien des Addons ---
		api.put("require", (ApiFunction) args -> {
			String file = str(args, 0, "");
			String src = a.files.get(file);
			if (src == null) {
				throw new IllegalArgumentException("Datei '" + file + "' gibt es im Addon nicht");
			}
			try {
				return a.runtime.run(file, src);
			} catch (Exception e) {
				throw new IllegalStateException(file + ": " + e.getMessage());
			}
		});

		// --- Kennzeichnung "Neustart noetig" ---
		api.put("restartNeeded", (ApiFunction) args -> {
			PlatformHost.flagRestart(a, str(args, 0, "nicht naeher angegeben"));
			return null;
		});
		return api;
	}

	private static Object timer(LoadedAddon a, List<Object> args, boolean repeat) {
		Callback c = fn(args, 1);
		if (c == null) {
			throw new IllegalArgumentException((repeat ? "every" : "after") + "(sekunden, funktion): Funktion fehlt");
		}
		long ticks = Math.max(1, Math.round(num(args, 0, 1) * 20));
		LoadedAddon.Timer t = new LoadedAddon.Timer(a.nextTimerId++, ticks, repeat, c);
		a.timers.add(t);
		return (double) t.id;
	}

	/** Zeichenfunktionen fuer das Ereignis "hud", gueltig nur waehrend des Aufrufs. */
	static Map<String, Object> graphics() {
		MinecraftClient mc = MinecraftClient.getInstance();
		Map<String, Object> g = new LinkedHashMap<>();
		g.put("width", (double) mc.getWindow().getScaledWidth());
		g.put("height", (double) mc.getWindow().getScaledHeight());
		g.put("text", (ApiFunction) args -> {
			if (PlatformHost.hudContext != null) {
				PlatformHost.hudContext.drawTextWithShadow(mc.textRenderer, Text.literal(str(args, 0, "")),
						(int) num(args, 1, 0), (int) num(args, 2, 0), color(args, 3, 0xFFFFFFFF));
			}
			return null;
		});
		g.put("rect", (ApiFunction) args -> {
			if (PlatformHost.hudContext != null) {
				int x = (int) num(args, 0, 0);
				int y = (int) num(args, 1, 0);
				PlatformHost.hudContext.fill(x, y, x + (int) num(args, 2, 0), y + (int) num(args, 3, 0),
						color(args, 4, 0x80000000));
			}
			return null;
		});
		g.put("round", (ApiFunction) args -> {
			if (PlatformHost.hudContext != null) {
				de.spassglas.vitrine.ui.theme.RoundedRect.fill(PlatformHost.hudContext, (int) num(args, 0, 0),
						(int) num(args, 1, 0), (int) num(args, 2, 0), (int) num(args, 3, 0), (int) num(args, 4, 4),
						color(args, 5, 0x80000000));
			}
			return null;
		});
		g.put("textWidth", (ApiFunction) args -> (double) mc.textRenderer.getWidth(str(args, 0, "")));
		return g;
	}

	/** Farbe als Zahl (0xAARRGGBB) oder Text "#RRGGBB"/"#AARRGGBB". */
	static int color(List<Object> args, int i, int def) {
		if (i >= args.size() || args.get(i) == null) {
			return def;
		}
		Object o = args.get(i);
		if (o instanceof Number n) {
			long v = n.longValue();
			return (int) (v <= 0xFFFFFF && v >= 0 ? 0xFF000000L | v : v);
		}
		String s = String.valueOf(o).replace("#", "");
		long v = Long.parseLong(s, 16);
		return (int) (s.length() <= 6 ? 0xFF000000L | v : v);
	}

	@SuppressWarnings("unused")
	private static boolean unusedBool(List<Object> a) {
		return bool(a, 0, false);
	}
}
