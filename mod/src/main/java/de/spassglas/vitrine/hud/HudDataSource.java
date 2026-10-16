package de.spassglas.vitrine.hud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geschlossene Whitelist erlaubter dynamischer Werte eines HUD-Widgets
 * (ROADMAP T5, Charter-Regel 3). Deckt sich eins zu eins mit
 * {@code $defs/dataSource} in {@code registry/schema/hud.schema.json}: keine
 * freie Formel, kein Skript, kein Java-Bezug -- jeder Wert wird vom
 * Mod-Renderer aus dem Spielzustand gelesen (siehe {@link HudDataResolver}),
 * niemals aus Paket-Code.
 *
 * <p>{@link Kind} unterscheidet, ob der Rohwert eine Zahl ist (dann greifen
 * {@code decimals} und, beim Balken-Widget, {@code min}/{@code max}) oder ein
 * fertiger Text.</p>
 */
public enum HudDataSource {

	FPS("fps", Kind.NUMERIC, 0),
	COORDINATES_X("coordinates.x", Kind.NUMERIC, 1),
	COORDINATES_Y("coordinates.y", Kind.NUMERIC, 1),
	COORDINATES_Z("coordinates.z", Kind.NUMERIC, 1),
	BIOME("biome", Kind.TEXT, 0),
	TIME("time", Kind.TEXT, 0);

	/** Ob die Quelle einen numerischen Rohwert oder direkt Text liefert. */
	public enum Kind { NUMERIC, TEXT }

	private static final Map<String, HudDataSource> BY_ID = buildIndex();

	private final String id;
	private final Kind kind;
	private final int defaultDecimals;

	HudDataSource(String id, Kind kind, int defaultDecimals) {
		this.id = id;
		this.kind = kind;
		this.defaultDecimals = defaultDecimals;
	}

	/** Der Bezeichner wie im HUD-JSON ({@code "fps"}, {@code "coordinates.x"} usw.). */
	public String id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	/**
	 * Nachkommastellen, wenn das Widget selbst kein {@code decimals} angibt
	 * (Schema: "Ohne Angabe entscheidet der Mod-Renderer ueber eine sinnvolle
	 * Default-Genauigkeit"). Nur fuer {@link Kind#NUMERIC} von Belang.
	 */
	public int defaultDecimals() {
		return defaultDecimals;
	}

	/** Loest einen JSON-Bezeichner auf, oder {@link Optional#empty()} wenn unbekannt. */
	public static Optional<HudDataSource> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, HudDataSource> buildIndex() {
		Map<String, HudDataSource> index = new LinkedHashMap<>();
		for (HudDataSource source : values()) {
			index.put(source.id, source);
		}
		return index;
	}
}
