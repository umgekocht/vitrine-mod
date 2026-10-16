package de.spassglas.vitrine.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geschlossene Whitelist der Vitrine-Werte, die ein Sandbox-Skript lesen darf
 * (ROADMAP X5). Genau wie {@code de.spassglas.vitrine.hud.HudDataSource} fuer
 * das HUD: ein Bezeichner aus dieser Aufzaehlung, niemals ein freier
 * Java-Feld- oder Methodenname -- ein unbekannter Bezeichner wird von
 * {@link ScriptParser} abgelehnt, nie dynamisch aufgeloest. Das ist der
 * Kern der Reflection-Sperre: es gibt schlicht keinen Codepfad, der einen
 * String zu einer Klasse, Methode oder einem Feld macht.
 */
public enum ScriptDataSource {

	INSTALLED_PACKAGE_COUNT("installedPackageCount", Kind.NUMERIC),
	ACTIVE_PACKAGE_COUNT("activePackageCount", Kind.NUMERIC),
	ACTIVE_THEME_ID("activeThemeId", Kind.TEXT),

	/**
	 * ROADMAP AD2 ("Spielwerte lesen -- wie HUD-Datenquellen"): dieselben
	 * sechs Bezeichner wie {@code de.spassglas.vitrine.hud.HudDataSource},
	 * damit ein Addon-Skript-Autor, der bereits ein HUD-Paket geschrieben hat,
	 * keine zweite Namensliste lernen muss.
	 */
	FPS("fps", Kind.NUMERIC),
	COORDINATES_X("coordinates.x", Kind.NUMERIC),
	COORDINATES_Y("coordinates.y", Kind.NUMERIC),
	COORDINATES_Z("coordinates.z", Kind.NUMERIC),
	BIOME("biome", Kind.TEXT),
	TIME("time", Kind.TEXT);

	/** Ob die Quelle einen Zahlenwert oder Text liefert -- fuer Typpruefungen zur Parsezeit. */
	public enum Kind { NUMERIC, TEXT }

	private static final Map<String, ScriptDataSource> BY_ID = buildIndex();

	private final String id;
	private final Kind kind;

	ScriptDataSource(String id, Kind kind) {
		this.id = id;
		this.kind = kind;
	}

	/** Der Bezeichner wie im Skript-JSON ({@code "installedPackageCount"} usw.). */
	public String id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	/** Loest einen JSON-Bezeichner auf, oder {@link Optional#empty()} wenn unbekannt. */
	public static Optional<ScriptDataSource> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, ScriptDataSource> buildIndex() {
		Map<String, ScriptDataSource> index = new LinkedHashMap<>();
		for (ScriptDataSource source : values()) {
			index.put(source.id, source);
		}
		return index;
	}
}
