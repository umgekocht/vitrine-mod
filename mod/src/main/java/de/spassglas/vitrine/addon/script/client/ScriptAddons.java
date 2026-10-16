package de.spassglas.vitrine.addon.script.client;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.addon.script.ScriptAddon;
import de.spassglas.vitrine.addon.script.ScriptAddonEvent;
import de.spassglas.vitrine.addon.script.ScriptAddonExecutor;
import de.spassglas.vitrine.addon.script.ScriptAddonLoader;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.script.ScriptContext;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.theme.ThemeService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laufzeit der Addon-Skripte (ROADMAP AD2): haengt sich an den Client-Tick,
 * gleicht alle zwei Sekunden (wie
 * {@code de.spassglas.vitrine.addon.AddonRules#sync}) mit dem Aktiv-Schalter
 * der Bibliothek ab -- neu eingeschaltete Addons mit {@code scripts/*.vs}
 * wirken ab dem naechsten Ereignis, ausgeschaltete sofort nicht mehr, ohne
 * Neustart -- und feuert die vier Ereignisse aus {@link ScriptAddonEvent}.
 *
 * <p><b>Bewusst client-seitig</b> (anders als das server-seitige {@code
 * AddonRules}): die gesamte Vitrine-API fuer Skripte (Toast/Nachricht, Ton,
 * HUD-Wert, Tastenereignis) existiert nur auf dem Client.</p>
 */
public final class ScriptAddons {

	private static final Map<String, List<ScriptAddon>> ACTIVE = new HashMap<>();
	private static final Set<String> SEEN = new HashSet<>();

	private static long ticks;
	private static boolean wasInWorld;
	private static double cachedInstalledCount;
	private static double cachedActiveCount;

	private ScriptAddons() {
	}

	/** Einmal beim Client-Start: haengt Sync-, Tick-, Welt- und Tasten-Ereignisse an den Client-Tick. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ScriptAddons::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ticks++;
		if (ticks % 40 == 0) {
			sync();
		}
		fireWorldJoinIfNeeded(client);
		fireByEvent(ScriptAddonEvent.TICK, client);
		fireKeyIfPressed(client);
	}

	private static void fireWorldJoinIfNeeded(MinecraftClient client) {
		boolean inWorld = client.world != null;
		if (inWorld && !wasInWorld) {
			fireByEvent(ScriptAddonEvent.WELT_BETRETEN, client);
		}
		wasInWorld = inWorld;
	}

	private static void fireKeyIfPressed(MinecraftClient client) {
		KeyBinding key = ScriptAddonKeyBinding.instance();
		if (key == null) {
			return;
		}
		while (key.wasPressed()) {
			fireByEvent(ScriptAddonEvent.TASTE, client);
		}
	}

	private static synchronized void fireByEvent(ScriptAddonEvent event, MinecraftClient client) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ScriptContext context = buildContext(client);
		MinecraftScriptAddonRuntime runtime = new MinecraftScriptAddonRuntime(client);
		for (List<ScriptAddon> scripts : ACTIVE.values()) {
			for (ScriptAddon script : scripts) {
				if (script.event() == event) {
					ScriptAddonExecutor.run(script, context, runtime);
				}
			}
		}
	}

	/**
	 * {@code /v <name>} (ROADMAP AD2): feuert jedes aktive {@code befehl}-Skript
	 * mit passendem {@code commandName}. Liefert, ob ueberhaupt eines passte --
	 * {@code ScriptAddonCommand} zeigt sonst eine ehrliche "kein Skript dafuer"-
	 * Rueckmeldung statt stillschweigend nichts zu tun.
	 */
	public static synchronized boolean fireCommand(String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		ScriptContext context = buildContext(client);
		MinecraftScriptAddonRuntime runtime = new MinecraftScriptAddonRuntime(client);
		boolean matched = false;
		for (List<ScriptAddon> scripts : ACTIVE.values()) {
			for (ScriptAddon script : scripts) {
				if (script.event() == ScriptAddonEvent.BEFEHL && script.commandName().equals(name)) {
					matched = true;
					ScriptAddonExecutor.run(script, context, runtime);
				}
			}
		}
		return matched;
	}

	private static ScriptContext buildContext(MinecraftClient client) {
		return new MinecraftScriptContext(client, cachedInstalledCount, cachedActiveCount,
				ThemeService.instance().variant().jsonKey());
	}

	/** Gleicht die aktiven Addon-Skripte mit dem Aktiv-Schalter in state.json ab. */
	static void sync() {
		syncWith(VitrinePackProvider.clientInstance(), FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID));
	}

	/**
	 * Wie {@link #sync()}, mit injizierbarem Provider/Konfigurationsordner --
	 * package-sichtbar fuer Tests ohne laufenden Fabric-Loader/Client (siehe
	 * {@code de.spassglas.vitrine.hud.client.HudAutoSync#syncNow}).
	 */
	static synchronized void syncWith(VitrinePackProvider provider, Path configDir) {
		Set<String> enabled;
		PackState state;
		try {
			state = PackStateStore.load(configDir);
			enabled = new HashSet<>(state.activePackIdsInOrder());
		} catch (RuntimeException e) {
			return;
		}
		cachedInstalledCount = state.installedPackIdsInOrder().size();
		cachedActiveCount = state.activePackIdsInOrder().size();
		ACTIVE.keySet().retainAll(enabled);
		SEEN.retainAll(enabled);
		for (String packId : enabled) {
			if (SEEN.add(packId)) {
				ScriptAddonLoader.load(provider, packId).ifPresent(scripts -> {
					ACTIVE.put(packId, scripts);
					Vitrine.LOGGER.info("Addon '{}': {} Skript(e) aktiv -- ohne Neustart.", packId, scripts.size());
				});
			}
		}
	}

	/** Test-Zugang: ob {@code packId} gerade aktive Skripte hat. */
	static synchronized boolean hasActiveScripts(String packId) {
		return ACTIVE.containsKey(packId);
	}

	/** Test-Zugang: setzt den gesamten Laufzeitzustand zwischen Tests zurueck. */
	static synchronized void resetForTest() {
		ACTIVE.clear();
		SEEN.clear();
		ticks = 0;
		wasInWorld = false;
		cachedInstalledCount = 0;
		cachedActiveCount = 0;
	}
}
