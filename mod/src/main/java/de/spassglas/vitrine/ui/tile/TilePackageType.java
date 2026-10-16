package de.spassglas.vitrine.ui.tile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Die Typ-Badge-Kategorien einer Kachel (docs/UI-SPEC.md "4. Kachel": "Typ-
 * Badge"). Die acht Bezeichner sind bereits als {@code category}-Werte im
 * Manifest-Schema festgelegt ({@code vitrine.schema.json}) und haben bereits
 * fertige Klartexttexte unter {@code vitrine.category.<id>} in beiden
 * Sprachdateien sowie passende Icon-Assets ({@code category_<id>.png},
 * ASSETS-NEEDED.md) -- diese Aufzaehlung erfindet also keine neuen Namen,
 * sondern macht die bereits vorhandenen Bezeichner fuer die Kachel-Logik
 * typsicher nutzbar, analog zu {@code VpkgCapability}.
 */
public enum TilePackageType {

	TEXTUREN("texturen"),
	THEME("theme"),
	HUD("hud"),
	SHADER("shader"),
	SOUND("sound"),
	SPRACHE("sprache"),
	COSMETICS("cosmetics"),
	BUNDLE("bundle"),
	/** ROADMAP M10: Addons (Verhalten/Skripte), wirken ohne Neustart. */
	ADDON("addon"),
	/** Java-Mod von Modrinth (Klasse B). */
	MOD("mod");

	private static final Map<String, TilePackageType> BY_ID = buildIndex();

	private final String id;

	TilePackageType(String id) {
		this.id = id;
	}

	/** Der Bezeichner, wie er im Manifest unter {@code category} steht (z. B. {@code "hud"}). */
	public String id() {
		return id;
	}

	/** Uebersetzungs-Key des Typ-Badge-Klartexts, z. B. {@code vitrine.category.hud}. */
	public String translationKey() {
		return "vitrine.category." + id;
	}

	/** Pfad des Typ-Icons relativ zu {@code assets/vitrine/}, z. B. {@code textures/gui/icons/category_hud.png}. */
	public String iconPath() {
		return "textures/gui/icons/category_" + id + ".png";
	}

	/** Loest einen Manifest-Bezeichner (z. B. {@code "hud"}) auf, oder {@link Optional#empty()}, wenn unbekannt. */
	public static Optional<TilePackageType> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, TilePackageType> buildIndex() {
		Map<String, TilePackageType> index = new LinkedHashMap<>();
		for (TilePackageType type : values()) {
			index.put(type.id, type);
		}
		return index;
	}
}
