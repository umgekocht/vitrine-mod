package de.spassglas.vitrine.addon;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ROADMAP M10/AD1: Verhaltens-Addons (Datenpakete) wirken <b>ohne Neustart</b>.
 *
 * <p>Vitrine meldet jedes installierte Addon mit der Faehigkeit
 * {@code verhalten} als Datenpaket {@code vitrine/<id>} an (siehe
 * {@code VitrinePackProvider#dataInstance}). Diese Klasse gleicht die Liste
 * der aktiven Datenpakete des laufenden integrierten Servers mit dem
 * Paket-Zustand ab: aktive Addons an, deaktivierte/entfernte aus -- und laedt
 * die Daten danach neu ({@code reloadResources}, dasselbe wie
 * {@code /reload}). Rezepte, Loot, Fortschritte und Funktionen wirken damit
 * sofort in der offenen Welt.</p>
 *
 * <p>Wirkt in Einzelspieler-Welten und als LAN-Host. Auf fremden Servern gilt
 * deren Datenpaket-Liste -- das sagt der Store ehrlich dazu.</p>
 */
public final class AddonDataPacks {

	private static final String PREFIX = "vitrine/";

	private AddonDataPacks() {
	}

	/** Einmal beim Client-Start: jede neu geoeffnete Welt bekommt die aktiven Addons. */
	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(AddonDataPacks::syncOn);
	}

	/** Nach Installieren/An/Aus/Deinstallieren: laufende Welt sofort angleichen (no-op ohne Welt). */
	public static void syncNow() {
		MinecraftServer server = MinecraftClient.getInstance().getServer();
		if (server != null) {
			syncOn(server);
		}
	}

	private static void syncOn(MinecraftServer server) {
		server.execute(() -> {
			ResourcePackManager manager = server.getDataPackManager();
			manager.scanPacks();
			Set<String> wanted = activeAddonIds(manager.getIds());
			List<String> next = plan(manager.getEnabledIds(), manager.getIds(), wanted);
			if (next.equals(new ArrayList<>(manager.getEnabledIds()))) {
				return;
			}
			Vitrine.LOGGER.info("Vitrine-Addons: Datenpakete werden ohne Neustart neu geladen ({}).", wanted);
			server.reloadResources(next).whenComplete((ok, error) -> {
				if (error != null) {
					Vitrine.LOGGER.warn("Vitrine-Addons: Neuladen der Datenpakete fehlgeschlagen: {}", error.toString());
					return;
				}
				MinecraftClient client = MinecraftClient.getInstance();
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendMessage(Text.translatable("vitrine.addon.reloaded"), true);
					}
				});
			});
		});
	}

	/**
	 * Neue Liste aktiver Datenpakete: alle bisherigen ohne Vitrine-Addons, die
	 * nicht mehr gewollt sind, plus gewollte, die verfuegbar sind. Reihenfolge
	 * der uebrigen Pakete bleibt erhalten (rein, testbar).
	 */
	static List<String> plan(Collection<String> enabled, Collection<String> available, Set<String> wanted) {
		LinkedHashSet<String> next = new LinkedHashSet<>();
		for (String id : enabled) {
			if (!id.startsWith(PREFIX) || wanted.contains(id)) {
				next.add(id);
			}
		}
		for (String id : wanted) {
			if (available.contains(id)) {
				next.add(id);
			}
		}
		return new ArrayList<>(next);
	}

	private static Set<String> activeAddonIds(Collection<String> available) {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		Set<String> wanted = new LinkedHashSet<>();
		if (!Files.isRegularFile(configDir.resolve("state.json"))) {
			return wanted;
		}
		PackState state = PackStateStore.load(configDir);
		for (String id : available) {
			if (id.startsWith(PREFIX) && state.isActive(id)) {
				wanted.add(id);
			}
		}
		return wanted;
	}
}
