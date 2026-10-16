package de.spassglas.vitrine.platform;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.spassglas.vitrine.config.VitrineConfig;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.mixin.client.GameRendererPostAccessor;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herz der Vitrine-Addon-Plattform (umgekocht, 2026-10-13: "mit den Addons alles
 * machen, was ein Mensch sich ausdenken kann"). Laedt Skript-Addons aus
 * eingeschalteten Paketen ({@code scripts/main.lua} oder {@code scripts/main.js})
 * und -- im Entwickler-Modus -- aus {@code config/vitrine/dev-addons/<name>/},
 * verteilt Ereignisse, und entlaedt Addons restlos, sobald sie ausgeschaltet
 * werden. Alles ohne Neustart.
 *
 * <p>Was ohne Neustart technisch nicht geht, meldet ein Addon ueber
 * {@code vitrine.restartNeeded(grund)} an; die Bibliothek zeigt dann
 * "Neustart noetig" (siehe {@link #restartReason(String)}).</p>
 */
public final class PlatformHost {

	private static final Map<String, LoadedAddon> ADDONS = new LinkedHashMap<>();
	private static final Map<String, String> RESTART = new ConcurrentHashMap<>();
	/** Abgestuerzte Addons: nicht neu laden, bis sich der Quelltext aendert. */
	private static final Map<String, String> CRASHED = new HashMap<>();
	private static final int MAX_ERRORS = 10;
	private static final Gson GSON = new Gson();

	static DrawContext hudContext;
	private static long tick;
	private static float lastHealth = -1;

	private PlatformHost() {
	}

	/** Grund, warum ein Addon einen Neustart braucht, oder {@code null}. Fuer die Bibliothek. */
	public static String restartReason(String packId) {
		return RESTART.get(packId);
	}

	public static List<String> loadedNames() {
		List<String> out = new ArrayList<>();
		for (LoadedAddon a : ADDONS.values()) {
			out.add(a.name);
		}
		return out;
	}

	public static Path devDir() {
		return VitrineConfig.dir().resolve("dev-addons");
	}

	// --- Abgleich: was soll geladen sein? ----------------------------------------

	private record Source(String id, String name, String key, Map<String, String> files) {
	}

	static void sync() {
		Map<String, Source> wanted = new LinkedHashMap<>();
		try {
			for (String packId : PackStateStore.load(VitrineConfig.dir()).activePackIdsInOrder()) {
				readPack(packId).ifPresent(s -> wanted.put(s.id(), s));
			}
		} catch (RuntimeException e) {
			return;
		}
		if (VitrineConfig.get().devAddons) {
			readDevAddons(wanted);
		}
		for (String id : List.copyOf(ADDONS.keySet())) {
			Source s = wanted.get(id);
			if (s == null || !s.key().equals(ADDONS.get(id).sourceKey)) {
				unload(id, s == null ? "ausgeschaltet" : "geaendert, wird neu geladen");
			}
		}
		for (Source s : wanted.values()) {
			if (!ADDONS.containsKey(s.id()) && !s.key().equals(CRASHED.get(s.id()))) {
				load(s);
			}
		}
	}

	private static Optional<Source> readPack(String packId) {
		VitrinePackProvider provider = VitrinePackProvider.clientInstance();
		Optional<Path> file = provider.findPackFile(packId);
		if (file.isEmpty()) {
			return Optional.empty();
		}
		try (VpkgArchive archive = VpkgArchive.open(file.get())) {
			Map<String, String> files = new HashMap<>();
			for (VpkgEntry e : archive.entries()) {
				String n = e.name();
				String lower = n.toLowerCase(Locale.ROOT);
				if (!e.directory() && n.startsWith("scripts/") && (lower.endsWith(".lua") || lower.endsWith(".js"))) {
					files.put(n.substring("scripts/".length()),
							new String(archive.readEntryBytes(n), StandardCharsets.UTF_8));
				}
			}
			if (!files.containsKey("main.lua") && !files.containsKey("main.js")) {
				return Optional.empty();
			}
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			if (!VitrinePackProvider.hasValidIntegrity(file.get()) || !provider.hasCapabilityConsent(file.get(), manifest)
					|| !manifest.capabilities().contains("skript")) {
				return Optional.empty();
			}
			String key = packId + "@" + Files.getLastModifiedTime(file.get()).toMillis();
			return Optional.of(new Source(packId, manifest.name(), key, files));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static void readDevAddons(Map<String, Source> wanted) {
		Path dir = devDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var dirs = Files.list(dir)) {
			for (Path d : (Iterable<Path>) dirs::iterator) {
				if (!Files.isDirectory(d)) {
					continue;
				}
				Map<String, String> files = new HashMap<>();
				long newest = 0;
				try (var list = Files.list(d)) {
					for (Path f : (Iterable<Path>) list::iterator) {
						String n = f.getFileName().toString();
						String lower = n.toLowerCase(Locale.ROOT);
						if (Files.isRegularFile(f) && (lower.endsWith(".lua") || lower.endsWith(".js"))) {
							files.put(n, Files.readString(f, StandardCharsets.UTF_8));
							newest = Math.max(newest, Files.getLastModifiedTime(f).toMillis());
						}
					}
				}
				if (files.containsKey("main.lua") || files.containsKey("main.js")) {
					String id = "dev/" + d.getFileName();
					wanted.put(id, new Source(id, d.getFileName() + " (dev)", id + "@" + newest, files));
				}
			}
		} catch (IOException ignored) {
			// Ordner gerade nicht lesbar -- beim naechsten Abgleich erneut
		}
	}

	// --- Laden und Entladen -------------------------------------------------------

	private static void load(Source s) {
		LoadedAddon a = new LoadedAddon(s.id(), s.name(), s.key(), s.files());
		boolean lua = s.files().containsKey("main.lua");
		try {
			a.runtime = lua ? new LuaScriptRuntime() : new JsScriptRuntime();
			ADDONS.put(a.id, a);
			a.runtime.start(lua ? "main.lua" : "main.js", s.files().get(lua ? "main.lua" : "main.js"), VitrineApi.build(a));
			AddonConsole.log(a.name, AddonConsole.Level.INFO, "geladen (" + (lua ? "Lua" : "JavaScript") + ")");
			CRASHED.remove(a.id);
		} catch (Throwable e) {
			AddonConsole.log(a.name, AddonConsole.Level.ERROR, "Start fehlgeschlagen: " + message(e));
			chatError(a, "konnte nicht starten: " + message(e));
			CRASHED.put(a.id, a.sourceKey);
			unload(a.id, null);
		}
	}

	static void unload(String id, String why) {
		LoadedAddon a = ADDONS.remove(id);
		if (a == null) {
			return;
		}
		fireOne(a, "unload");
		if (a.ownsShader) {
			MinecraftClient.getInstance().gameRenderer.disablePostProcessor();
		}
		if (MinecraftClient.getInstance().currentScreen instanceof ScriptMenuScreen m && m.owner == a) {
			MinecraftClient.getInstance().setScreen(null);
		}
		if (a.runtime != null) {
			a.runtime.close();
		}
		RESTART.remove(id);
		if (why != null) {
			AddonConsole.log(a.name, AddonConsole.Level.INFO, "entladen (" + why + ")");
		}
	}

	// --- Ereignisse ---------------------------------------------------------------

	static void fire(String event, Object... args) {
		for (LoadedAddon a : List.copyOf(ADDONS.values())) {
			fireOne(a, event, args);
		}
	}

	private static void fireOne(LoadedAddon a, String event, Object... args) {
		List<Callback> list = a.handlers.get(event);
		if (list == null) {
			return;
		}
		for (Callback c : List.copyOf(list)) {
			safeCall(a, c, args);
		}
	}

	static Object safeCall(LoadedAddon a, Callback c, Object... args) {
		if (!ADDONS.containsKey(a.id) && a.runtime == null) {
			return null;
		}
		try {
			return c.call(args);
		} catch (Throwable e) {
			a.errors++;
			AddonConsole.log(a.name, AddonConsole.Level.ERROR, message(e));
			if (a.errors == 1) {
				chatError(a, message(e));
			}
			if (a.errors >= MAX_ERRORS) {
				AddonConsole.log(a.name, AddonConsole.Level.ERROR, "zu viele Fehler -- Addon wurde angehalten");
				chatError(a, "zu viele Fehler, angehalten. Details in der Addon-Konsole.");
				CRASHED.put(a.id, a.sourceKey);
				unload(a.id, "abgestuerzt");
			}
			return null;
		}
	}

	private static String message(Throwable e) {
		String m = e.getMessage();
		return m == null ? e.getClass().getSimpleName() : m.length() > 300 ? m.substring(0, 300) + "..." : m;
	}

	private static void chatError(LoadedAddon a, String text) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal("[" + a.name + "] " + text).formatted(Formatting.RED), false);
		}
	}

	// --- Takt aus VitrinePlatform -------------------------------------------------

	static void clientTick(MinecraftClient mc) {
		tick++;
		if (tick % 20 == 0) {
			sync();
		}
		if (mc.player == null) {
			lastHealth = -1;
			return;
		}
		// Treffer erkennen (Lebenspunkte sinken)
		float hp = mc.player.getHealth();
		if (lastHealth >= 0 && hp < lastHealth) {
			fire("hurt", (double) (lastHealth - hp));
		}
		lastHealth = hp;
		// Tasten der Addons (nur ohne offenes Menue)
		long window = mc.getWindow().getHandle();
		for (LoadedAddon a : List.copyOf(ADDONS.values())) {
			for (Map.Entry<Integer, Callback> k : Map.copyOf(a.keys).entrySet()) {
				boolean down = mc.currentScreen == null && InputUtil.isKeyPressed(window, k.getKey());
				boolean was = a.keyDown.getOrDefault(k.getKey(), false);
				a.keyDown.put(k.getKey(), down);
				if (down && !was) {
					safeCall(a, k.getValue());
				}
			}
			for (LoadedAddon.Timer t : List.copyOf(a.timers)) {
				if (--t.remaining <= 0) {
					if (t.repeat) {
						t.remaining = t.periodTicks;
					} else {
						a.timers.remove(t);
					}
					safeCall(a, t.fn);
				}
			}
		}
		fire("tick");
	}

	// --- Bausteine fuer die API ------------------------------------------------------

	static int keyCode(String name) {
		String n = name.trim().toLowerCase(Locale.ROOT);
		return InputUtil.fromTranslationKey(n.startsWith("key.") ? n : "key.keyboard." + n).getCode();
	}

	static void setShader(LoadedAddon a, String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (name == null || name.isBlank()) {
			mc.gameRenderer.disablePostProcessor();
			a.ownsShader = false;
			return;
		}
		Identifier id = name.contains(":") ? Identifier.of(name)
				: Identifier.ofVanilla("shaders/post/" + name + ".json");
		if (!id.getPath().startsWith("shaders/post/")) {
			id = Identifier.of(id.getNamespace(), "shaders/post/" + id.getPath() + ".json");
		}
		((GameRendererPostAccessor) mc.gameRenderer).vitrine$loadPostProcessor(id);
		a.ownsShader = true;
	}

	static void playSound(String id, double volume, double pitch) {
		MinecraftClient.getInstance().getSoundManager().play(
				PositionedSoundInstance.master(SoundEvent.of(Identifier.of(id)), (float) pitch, (float) volume));
	}

	static void particle(String id, double x, double y, double z, int count) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		if (Registries.PARTICLE_TYPE.get(Identifier.of(id)) instanceof SimpleParticleType type) {
			for (int i = 0; i < Math.min(count, 200); i++) {
				mc.world.addParticle(type, x + (Math.random() - 0.5) * 0.6, y + Math.random() * 0.6,
						z + (Math.random() - 0.5) * 0.6, 0, 0.02, 0);
			}
		}
	}

	static void command(String cmd) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
		var server = mc.getServer();
		if (server != null) {
			var uuid = mc.player.getUuid();
			server.execute(() -> {
				var sp = server.getPlayerManager().getPlayer(uuid);
				if (sp != null) {
					server.getCommandManager().executeWithPrefix(sp.getCommandSource().withLevel(2), c);
				}
			});
		} else {
			mc.player.networkHandler.sendChatCommand(c);
		}
	}

	static Map<String, Object> player() {
		MinecraftClient mc = MinecraftClient.getInstance();
		var p = mc.player;
		if (p == null) {
			return null;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", p.getName().getString());
		m.put("x", p.getX());
		m.put("y", p.getY());
		m.put("z", p.getZ());
		m.put("yaw", (double) p.getYaw());
		m.put("pitch", (double) p.getPitch());
		m.put("health", (double) p.getHealth());
		m.put("maxHealth", (double) p.getMaxHealth());
		m.put("food", (double) p.getHungerManager().getFoodLevel());
		m.put("onGround", p.isOnGround());
		m.put("sneaking", p.isSneaking());
		m.put("sprinting", p.isSprinting());
		m.put("dimension", p.getWorld().getRegistryKey().getValue().toString());
		m.put("item", Registries.ITEM.getId(p.getMainHandStack().getItem()).toString());
		return m;
	}

	static Map<String, Object> world() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) {
			return null;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("time", (double) (mc.world.getTimeOfDay() % 24000));
		m.put("day", (double) (mc.world.getTimeOfDay() / 24000));
		m.put("raining", mc.world.isRaining());
		m.put("thundering", mc.world.isThundering());
		m.put("biome", mc.world.getBiome(mc.player.getBlockPos()).getKey()
				.map(k -> k.getValue().toString()).orElse("?"));
		m.put("singleplayer", mc.isInSingleplayer());
		return m;
	}

	static String block(double x, double y, double z) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return null;
		}
		return Registries.BLOCK.getId(mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).getBlock()).toString();
	}

	// --- Speicher je Addon ---------------------------------------------------------

	private static Path storageFile(LoadedAddon a) {
		return VitrineConfig.dir().resolve("addon-data").resolve(a.id.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
	}

	static Map<String, Object> loadStorage(LoadedAddon a) {
		try {
			Path f = storageFile(a);
			if (Files.isRegularFile(f)) {
				Map<String, Object> m = GSON.fromJson(Files.readString(f), new TypeToken<Map<String, Object>>() {
				}.getType());
				return m != null ? m : new LinkedHashMap<>();
			}
		} catch (Exception ignored) {
			// kaputte Datei: leer anfangen
		}
		return new LinkedHashMap<>();
	}

	static void saveStorage(LoadedAddon a, Map<String, Object> data) {
		try {
			Path f = storageFile(a);
			Files.createDirectories(f.getParent());
			String json = GSON.toJson(data);
			if (json.length() > 1_000_000) {
				throw new IllegalStateException("Speicher zu gross (max. 1 MB je Addon)");
			}
			Files.writeString(f, json);
		} catch (IOException e) {
			throw new IllegalStateException("Speichern fehlgeschlagen: " + e.getMessage());
		}
	}

	static void flagRestart(LoadedAddon a, String reason) {
		a.restartReason = reason;
		RESTART.put(a.id, reason);
		AddonConsole.log(a.name, AddonConsole.Level.WARN, "braucht einen Neustart: " + reason);
	}
}
