package de.spassglas.vitrine.hud;

/**
 * Der Ausschnitt des Spielzustands, den die HUD-Laufzeit lesen darf --
 * ausschliesslich die Werte hinter der geschlossenen
 * {@link HudDataSource}-Whitelist plus die drei Zustaende, die eine
 * {@link HudVisibility}-Bedingung auswerten kann (aktuelle Dimension,
 * Debug-Bildschirm an/aus, ein Menue/Screen offen ja/nein).
 *
 * <p>Bewusst eine schmale Schnittstelle statt einer direkten Abhaengigkeit
 * von {@code net.minecraft.client.MinecraftClient}: so bleibt die gesamte
 * Widget-Auswahl-, Formatierungs- und Anker-Logik ohne laufenden Client
 * testbar (siehe {@code HudDataResolverTest}, {@code HudVisibilityTest}). Die
 * einzige Stelle, die {@code MinecraftClient} tatsaechlich ausliest, ist
 * {@code de.spassglas.vitrine.hud.client.MinecraftHudGameState}.</p>
 */
public interface HudGameState {

	/** Aktuelle Bilder pro Sekunde ({@link HudDataSource#FPS}). */
	double fps();

	/** Spielerposition X ({@link HudDataSource#COORDINATES_X}). */
	double playerX();

	/** Spielerposition Y ({@link HudDataSource#COORDINATES_Y}). */
	double playerY();

	/** Spielerposition Z ({@link HudDataSource#COORDINATES_Z}). */
	double playerZ();

	/** Biom-Kennung am Spielerort, z. B. {@code "minecraft:plains"} ({@link HudDataSource#BIOME}). */
	String biomeId();

	/**
	 * Tageszeit der Welt in Ticks (0..23999, 0 = Sonnenaufgang um 06:00) --
	 * {@link HudDataResolver} rechnet daraus die Uhrzeit fuer
	 * {@link HudDataSource#TIME}.
	 */
	long dayTimeTicks();

	/** Dimensions-Id des Spielers, z. B. {@code "minecraft:overworld"} (Sichtbarkeit {@code dimension}). */
	String dimensionId();

	/** Ob der Vanilla-Debug-Bildschirm (F3) gerade sichtbar ist (Sichtbarkeit {@code debugScreen}). */
	boolean debugScreenShown();

	/** Ob irgendein Screen/Menue (Inventar, Chat, Pause, ...) offen ist (Sichtbarkeit {@code outsideMenu}). */
	boolean anyScreenOpen();
}
