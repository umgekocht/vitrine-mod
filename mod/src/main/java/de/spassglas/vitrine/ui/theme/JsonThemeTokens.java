package de.spassglas.vitrine.ui.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * {@link ThemeTokens} aus einer bereits schema-validierten Farbvariante eines
 * geladenen Theme-JSON, mit einem Fallback-{@link ThemeTokens} (dem
 * Standard-Theme) fuer <em>einzelne</em> fehlende Tokens.
 *
 * <p>ROADMAP T2, "Fallback bei fehlendem Token": fehlt im geladenen Theme ein
 * einzelner Token, liefert dieser Wrapper den Wert des Standard-Themes -- nie
 * {@code 0}/{@code null}. Ein unbekannter (nicht im Schema definierter)
 * Token-Name dagegen erreicht ueber den Fallback dessen
 * {@link IllegalArgumentException} -- ein Tippfehler faellt laut auf.</p>
 *
 * <p>Das JSON wird erst nach erfolgreicher {@link ThemeSchemaValidator}-Pruefung
 * hier hineingereicht; die Wertform je Token (Zahl, {@code "#rrggbb"},
 * {@code {hex, alpha}}) ist damit garantiert -- dieser Wrapper muss sie nur
 * noch in {@code int} bzw. gepacktes ARGB umsetzen.</p>
 */
final class JsonThemeTokens implements ThemeTokens {

	private final JsonObject variant;
	private final ThemeTokens fallback;

	JsonThemeTokens(JsonObject variant, ThemeTokens fallback) {
		this.variant = variant;
		this.fallback = fallback;
	}

	@Override
	public int size(String token) {
		JsonElement value = presentValueOrNull(token);
		if (value == null) {
			return fallback.size(token);
		}
		return value.getAsInt();
	}

	@Override
	public int color(String token) {
		JsonElement value = presentValueOrNull(token);
		if (value == null) {
			return fallback.color(token);
		}
		if (value.isJsonObject()) {
			JsonObject withAlpha = value.getAsJsonObject();
			int rgb = parseRgb(withAlpha.get("hex").getAsString());
			int alpha = Math.round(withAlpha.get("alpha").getAsFloat() * 255.0f);
			return (clampByte(alpha) << 24) | rgb;
		}
		return 0xFF000000 | parseRgb(value.getAsString());
	}

	private JsonElement presentValueOrNull(String token) {
		if (variant.has(token) && !variant.get(token).isJsonNull()) {
			return variant.get(token);
		}
		return null;
	}

	/** {@code "#rrggbb"} (vom Schema-Muster geprueft) -> 0x00RRGGBB. */
	private static int parseRgb(String hex) {
		return Integer.parseInt(hex.substring(1), 16) & 0xFFFFFF;
	}

	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}
}
