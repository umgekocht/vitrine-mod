package de.spassglas.vitrine.ui.nav;

/**
 * Die Eintraege der Nav-Rail in der Reihenfolge aus {@code docs/UI-SPEC.md}
 * ("2. Nav-Rail links"): Start, Texturen, Themes, HUD, Sound & Sprache,
 * Cosmetics, Shader, Trennlinie, Bibliothek, unten abgesetzt Einstellungen.
 * Uebersetzungs-Keys existieren bereits seit F3 in beiden Sprachdateien.
 */
public enum NavRailEntry {
	START("vitrine.nav.start", false, false, "start"),
	TEXTURES("vitrine.nav.texturen", false, false, "texturen"),
	THEMES("vitrine.nav.themes", false, false, "themes"),
	HUD("vitrine.nav.hud", false, false, "hud"),
	SOUND_LANGUAGE("vitrine.nav.sound_sprache", false, false, "sound_sprache"),
	COSMETICS("vitrine.nav.cosmetics", false, false, "cosmetics"),
	SHADER("vitrine.nav.shader", false, false, "shader"),
	/** ROADMAP M10: Addons (Verhalten/Skripte), wie der Bedrock-Marketplace. */
	ADDONS("vitrine.nav.addons", false, false, "addons"),
	/** Fabric-Mods von Modrinth (Klasse B, Neustart noetig). */
	MODS("vitrine.nav.mods", false, false, "mods"),
	/** Neuigkeiten: zuletzt aktualisierte Inhalte (Microsoft-Store-Seitenleiste). */
	NEWS("vitrine.nav.news", false, false, "news"),
	LIBRARY("vitrine.nav.bibliothek", true, false, "bibliothek"),
	SETTINGS("vitrine.nav.einstellungen", false, true, "einstellungen");

	private final String translationKey;
	private final boolean separatorBefore;
	private final boolean bottomAligned;
	private final String iconName;

	NavRailEntry(String translationKey, boolean separatorBefore, boolean bottomAligned, String iconName) {
		this.translationKey = translationKey;
		this.separatorBefore = separatorBefore;
		this.bottomAligned = bottomAligned;
		this.iconName = iconName;
	}

	/**
	 * GATE18: Pfad des mitgelieferten Umriss-Icons (inaktiv), z. B.
	 * {@code textures/gui/icons/nav_start_outline.png} im Namensraum {@code vitrine}.
	 */
	public String outlineIconPath() {
		return "textures/gui/icons/nav_" + iconName + "_outline.png";
	}

	/** GATE18: Pfad des gefuellten Icons (aktive Seite). */
	public String filledIconPath() {
		// assets v2: eigene "active"-Variante (blau gefuellt) fuer den gewaehlten Eintrag
		return "textures/gui/icons/nav_" + iconName + "_active.png";
	}

	public String translationKey() {
		return translationKey;
	}

	/** Ob vor diesem Eintrag die Trennlinie aus der Spec gezeichnet wird (vor "Bibliothek"). */
	public boolean separatorBefore() {
		return separatorBefore;
	}

	/** Ob dieser Eintrag "unten abgesetzt" sitzt statt in der normalen Reihenfolge ("Einstellungen"). */
	public boolean bottomAligned() {
		return bottomAligned;
	}
}
