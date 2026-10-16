package de.spassglas.vitrine.addon.script.client;

import de.spassglas.vitrine.hud.HudGameState;
import de.spassglas.vitrine.hud.client.MinecraftHudGameState;
import de.spassglas.vitrine.script.ScriptContext;
import net.minecraft.client.MinecraftClient;

/**
 * Die einzige Klasse, die fuer Addon-Skripte tatsaechlich {@link
 * MinecraftClient} ausliest (ROADMAP AD2, "Spielwerte lesen -- wie
 * HUD-Datenquellen"). Reiner Adapter: die sechs HUD-aehnlichen Werte
 * (fps/Koordinaten/Biom/Zeit) reicht sie unveraendert an {@link
 * MinecraftHudGameState} weiter -- dieselbe Ausleseklasse, die auch das HUD
 * selbst benutzt (ROADMAP T5) -- statt den Client ein zweites Mal selbst
 * abzufragen. Die drei Addon-eigenen Werte (installierte/aktive Paketzahl,
 * aktives Theme) bekommt sie fertig vom Aufrufer ({@code ScriptAddons}, das
 * sie nur bei jedem Sync-Lauf neu aus {@code state.json} liest, nicht bei
 * jedem Tick) -- genau wie {@link MinecraftHudGameState} braucht dieser reine
 * Adapter deshalb keinen eigenen Unit-Test (siehe {@code ScriptInterpreterTest}
 * fuer die getestete Auswertungslogik).
 */
public final class MinecraftScriptContext implements ScriptContext {

	private final HudGameState hudState;
	private final double installedPackageCount;
	private final double activePackageCount;
	private final String activeThemeId;

	public MinecraftScriptContext(MinecraftClient client, double installedPackageCount, double activePackageCount,
			String activeThemeId) {
		this.hudState = new MinecraftHudGameState(client);
		this.installedPackageCount = installedPackageCount;
		this.activePackageCount = activePackageCount;
		this.activeThemeId = activeThemeId;
	}

	@Override
	public double installedPackageCount() {
		return installedPackageCount;
	}

	@Override
	public double activePackageCount() {
		return activePackageCount;
	}

	@Override
	public String activeThemeId() {
		return activeThemeId;
	}

	@Override
	public double fps() {
		return hudState.fps();
	}

	@Override
	public double playerX() {
		return hudState.playerX();
	}

	@Override
	public double playerY() {
		return hudState.playerY();
	}

	@Override
	public double playerZ() {
		return hudState.playerZ();
	}

	@Override
	public String biomeId() {
		return hudState.biomeId();
	}

	@Override
	public long dayTimeTicks() {
		return hudState.dayTimeTicks();
	}
}
