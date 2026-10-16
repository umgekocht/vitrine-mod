package de.spassglas.vitrine.hud;

/**
 * Handgesetzter {@link HudGameState} fuer die HUD-Laufzeit-Tests -- ersetzt
 * {@code de.spassglas.vitrine.hud.client.MinecraftHudGameState}, damit sich
 * Wert-Formatierung ({@link HudDataResolver}) und Sichtbarkeit
 * ({@link HudVisibility}) ohne laufenden Client pruefen lassen (siehe
 * Klassendoku von {@link HudGameState}).
 */
final class FakeHudGameState implements HudGameState {

	double fps = 60.0;
	double playerX = 0.0;
	double playerY = 0.0;
	double playerZ = 0.0;
	String biomeId = "minecraft:plains";
	long dayTimeTicks = 0L;
	String dimensionId = "minecraft:overworld";
	boolean debugScreenShown = false;
	boolean anyScreenOpen = false;

	@Override
	public double fps() {
		return fps;
	}

	@Override
	public double playerX() {
		return playerX;
	}

	@Override
	public double playerY() {
		return playerY;
	}

	@Override
	public double playerZ() {
		return playerZ;
	}

	@Override
	public String biomeId() {
		return biomeId;
	}

	@Override
	public long dayTimeTicks() {
		return dayTimeTicks;
	}

	@Override
	public String dimensionId() {
		return dimensionId;
	}

	@Override
	public boolean debugScreenShown() {
		return debugScreenShown;
	}

	@Override
	public boolean anyScreenOpen() {
		return anyScreenOpen;
	}
}
