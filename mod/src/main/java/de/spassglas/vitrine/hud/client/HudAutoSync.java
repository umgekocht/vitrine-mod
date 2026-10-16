package de.spassglas.vitrine.hud.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.HashSet;
import java.util.Set;

/**
 * HUD-Pakete folgen automatisch dem Aktiv-Schalter (umgekocht, 2026-10-06: "die
 * HUD-Elemente werden nicht angezeigt"). Bisher kam ein HUD nur ueber den
 * Befehl {@code /vitrine hud <id>} auf den Bildschirm. Jetzt gleicht dieser
 * Haken jede Sekunde die eingeschalteten Vitrine-Pakete mit der
 * {@code ActiveHudRegistry} ab: neu eingeschaltet -> {@link HudActivation#activate},
 * ausgeschaltet -> {@link HudActivation#deactivate}. Pakete ohne {@code hud/}
 * sind dabei ein No-op. Alle Pruefungen (Signatur, Zustimmung) bleiben die
 * von {@link HudActivation}.
 */
public final class HudAutoSync {

	private static final Set<String> APPLIED = new HashSet<>();
	private static int ticks;

	private HudAutoSync() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % 20 == 0) {
				syncNow(client);
			}
		});
	}

	public static void syncNow(MinecraftClient client) {
		// Quelle der Wahrheit ist der Aktiv-Schalter in state.json (Bibliothek).
		Set<String> enabled = new HashSet<>();
		try {
			var state = de.spassglas.vitrine.state.PackStateStore.load(net.fabricmc.loader.api.FabricLoader.getInstance()
					.getConfigDir().resolve(de.spassglas.vitrine.Vitrine.MOD_ID));
			enabled.addAll(state.activePackIdsInOrder());
		} catch (RuntimeException e) {
			return;
		}
		for (String id : enabled) {
			if (APPLIED.add(id)) {
				HudActivation.activate(id);
			}
		}
		for (String id : Set.copyOf(APPLIED)) {
			if (!enabled.contains(id)) {
				APPLIED.remove(id);
				HudActivation.deactivate(id);
			}
		}
	}
}
