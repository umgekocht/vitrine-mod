package de.spassglas.vitrine.hud.client;

import de.spassglas.vitrine.hud.HudGameState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * Die einzige Klasse, die {@link MinecraftClient} tatsaechlich ausliest und
 * {@link HudGameState} implementiert (ROADMAP T5). Reine Adapterschicht: kein
 * Zustand, keine Logik -- jede Methode liest genau einen Wert aus dem
 * laufenden Client. Die gesamte Widget-Auswahl, Wert-Formatierung und
 * Anker-Geometrie sitzt hinter der schmalen {@link HudGameState}-Schnittstelle
 * und ist ohne Client testbar (siehe {@code HudDataResolverTest},
 * {@code HudVisibilityTest}); diese Klasse braucht deshalb keinen Unit-Test.
 *
 * <p>Alle Zugriffe sind null-sicher: der HUD-Renderer laeuft auch im Hauptmenue
 * (kein {@code world}/{@code player}) -- dann liefern die Quellen neutrale
 * Werte statt einer {@link NullPointerException}.</p>
 */
public final class MinecraftHudGameState implements HudGameState {

	private final MinecraftClient client;

	public MinecraftHudGameState(MinecraftClient client) {
		this.client = client;
	}

	@Override
	public double fps() {
		return client.getCurrentFps();
	}

	@Override
	public double playerX() {
		ClientPlayerEntity player = client.player;
		return player != null ? player.getX() : 0.0;
	}

	@Override
	public double playerY() {
		ClientPlayerEntity player = client.player;
		return player != null ? player.getY() : 0.0;
	}

	@Override
	public double playerZ() {
		ClientPlayerEntity player = client.player;
		return player != null ? player.getZ() : 0.0;
	}

	@Override
	public String biomeId() {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return "";
		}
		return world.getBiome(player.getBlockPos())
				.getKey()
				.map(key -> key.getValue().toString())
				.orElse("");
	}

	@Override
	public long dayTimeTicks() {
		ClientWorld world = client.world;
		return world != null ? world.getTimeOfDay() : 0L;
	}

	@Override
	public String dimensionId() {
		ClientWorld world = client.world;
		return world != null ? world.getRegistryKey().getValue().toString() : "";
	}

	@Override
	public boolean debugScreenShown() {
		return client.getDebugHud() != null && client.getDebugHud().shouldShowDebugHud();
	}

	@Override
	public boolean anyScreenOpen() {
		return client.currentScreen != null;
	}
}
