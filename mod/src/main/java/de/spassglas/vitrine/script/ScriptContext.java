package de.spassglas.vitrine.script;

/**
 * Der Ausschnitt des Vitrine-Zustands, den ein Sandbox-Skript lesen darf --
 * genau die Werte hinter {@link ScriptDataSource} (ROADMAP X5). Eine schmale
 * Schnittstelle statt eines direkten Zugriffs auf Store-/Katalog-Klassen:
 * das haelt Parser und Interpreter ohne laufenden Client testbar (siehe
 * {@code de.spassglas.vitrine.hud.HudGameState} fuer dasselbe Muster im HUD).
 */
public interface ScriptContext {

	/** Anzahl aller installierten Pakete ({@link ScriptDataSource#INSTALLED_PACKAGE_COUNT}). */
	double installedPackageCount();

	/** Anzahl aller derzeit aktiven Pakete ({@link ScriptDataSource#ACTIVE_PACKAGE_COUNT}). */
	double activePackageCount();

	/** Id des aktiven Themes ({@link ScriptDataSource#ACTIVE_THEME_ID}). */
	String activeThemeId();

	/**
	 * Die folgenden sechs Methoden spiegeln ROADMAP AD2 ("Spielwerte lesen --
	 * wie HUD-Datenquellen") exakt die Methodennamen von
	 * {@code de.spassglas.vitrine.hud.HudGameState} -- bewusst dieselben Namen
	 * statt eines {@code extends}, damit dieses Sandbox-Package weiterhin ohne
	 * Abhaengigkeit auf das {@code hud}-Package bleibt (siehe Klassendoku), ein
	 * Addon-Skript-Laufzeit-Adapter (de.spassglas.vitrine.addon.script.client)
	 * aber trotzdem ein {@code HudGameState} eins zu eins in diese Schnittstelle
	 * durchreichen kann, ohne eine Übersetzungsschicht zu brauchen.
	 */

	/** Aktuelle Bilder pro Sekunde ({@link ScriptDataSource#FPS}). */
	double fps();

	/** Spielerposition X ({@link ScriptDataSource#COORDINATES_X}). */
	double playerX();

	/** Spielerposition Y ({@link ScriptDataSource#COORDINATES_Y}). */
	double playerY();

	/** Spielerposition Z ({@link ScriptDataSource#COORDINATES_Z}). */
	double playerZ();

	/** Biom-Kennung am Spielerort, z. B. {@code "minecraft:plains"} ({@link ScriptDataSource#BIOME}). */
	String biomeId();

	/** Tageszeit der Welt in Ticks (0..23999), siehe {@link ScriptDataSource#TIME}. */
	long dayTimeTicks();
}
