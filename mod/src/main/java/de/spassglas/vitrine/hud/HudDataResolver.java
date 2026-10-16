package de.spassglas.vitrine.hud;

import java.util.Locale;

/**
 * Setzt die Werte hinter der geschlossenen {@link HudDataSource}-Whitelist
 * aus einem {@link HudGameState} in Anzeigetext um (ROADMAP T5). Reine
 * Wertsubstitution -- kein {@code eval}, keine Formel, kein dynamischer
 * Codepfad (Charter-Regel 3).
 */
public final class HudDataResolver {

	private HudDataResolver() {
	}

	/**
	 * Formatiert genau einen {@link HudTextContent} zu der Zeile, die auf dem
	 * Bildschirm steht: bei {@link HudTextContent.Static} der feste Text, bei
	 * {@link HudTextContent.Dynamic} das feste {@code label} plus der aus dem
	 * Spielzustand gelesene Wert.
	 */
	public static String render(HudTextContent content, HudGameState state) {
		if (content instanceof HudTextContent.Static staticContent) {
			return staticContent.value();
		}
		HudTextContent.Dynamic dynamic = (HudTextContent.Dynamic) content;
		return dynamic.label() + renderValue(dynamic.source(), dynamic.decimals(), state);
	}

	/**
	 * Der reine Wert einer Datenquelle als Text, ohne Label.
	 *
	 * @param decimals gewuenschte Nachkommastellen bei numerischen Quellen,
	 *                 oder {@code null} fuer {@link HudDataSource#defaultDecimals()}.
	 */
	public static String renderValue(HudDataSource source, Integer decimals, HudGameState state) {
		return switch (source) {
			case FPS -> formatNumber(state.fps(), decimalsOrDefault(source, decimals));
			case COORDINATES_X -> formatNumber(state.playerX(), decimalsOrDefault(source, decimals));
			case COORDINATES_Y -> formatNumber(state.playerY(), decimalsOrDefault(source, decimals));
			case COORDINATES_Z -> formatNumber(state.playerZ(), decimalsOrDefault(source, decimals));
			case BIOME -> state.biomeId();
			case TIME -> formatClock(state.dayTimeTicks());
		};
	}

	/** Roher numerischer Wert einer numerischen Quelle -- fuer den Fuellgrad eines Balken-Widgets. */
	public static double numericValue(HudDataSource source, HudGameState state) {
		return switch (source) {
			case FPS -> state.fps();
			case COORDINATES_X -> state.playerX();
			case COORDINATES_Y -> state.playerY();
			case COORDINATES_Z -> state.playerZ();
			case BIOME, TIME -> throw new IllegalArgumentException(
					"Datenquelle '" + source.id() + "' liefert keinen Zahlenwert fuer einen Balken");
		};
	}

	private static int decimalsOrDefault(HudDataSource source, Integer decimals) {
		return decimals != null ? decimals : source.defaultDecimals();
	}

	private static String formatNumber(double value, int decimals) {
		if (decimals <= 0) {
			return Long.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	/**
	 * Rechnet die Welt-Tageszeit (Ticks, 0 = Sonnenaufgang 06:00, 24000 Ticks
	 * pro Minecraft-Tag) in eine {@code HH:MM}-Uhrzeit um -- feste, bekannte
	 * Umrechnung, keine konfigurierbare Formel.
	 */
	static String formatClock(long dayTimeTicks) {
		long normalized = ((dayTimeTicks % 24000L) + 24000L) % 24000L;
		long totalMinutes = (long) Math.floor(normalized / 1000.0 * 60.0) + 6L * 60L;
		totalMinutes %= 24L * 60L;
		long hours = totalMinutes / 60L;
		long minutes = totalMinutes % 60L;
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}
}
