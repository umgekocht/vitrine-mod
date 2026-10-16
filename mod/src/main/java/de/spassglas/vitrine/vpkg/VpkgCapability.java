package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.i18n.Translations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Die vollstaendige, feste Menge aller Faehigkeiten ("APIs"), die ein
 * {@code .vpkg}-Paket ueberhaupt anmelden kann (ROADMAP P5, Charter-Regel 4:
 * "Faehigkeiten sind eine Whitelist. Default ist Verbot. Kein Paket bekommt
 * eine API, die es nicht im Manifest angemeldet hat."). Diese Aufzaehlung
 * IST die Whitelist -- ein Manifest-Eintrag unter {@code capabilities}, der
 * hier nicht auftaucht, ist keine gueltige Faehigkeit (siehe
 * {@link VpkgCapabilityGrant}).
 *
 * <p><b>Warum genau diese neun, und keine {@code hud.read}/
 * {@code texturen.override}-Unterteilung:</b> {@code vitrine.schema.json}
 * nennt "hud.read"/"texturen.override" nur als Beispiele fuer das erlaubte
 * Zeichenmuster, nicht als verbindliche Bezeichner. In
 * {@code assets/vitrine/lang/*.json} liegen bereits fertige Klartexttexte
 * unter {@code vitrine.capability.<name>} fuer genau die sieben
 * Ressourcen-Kategorien, die {@code vitrine.schema.json} unter {@code category}
 * kennt (alle bis auf {@code bundle} -- ein Bundle ist nur ein Container, die
 * darin enthaltenen Pakete melden ihre Faehigkeiten selbst an), plus
 * {@code verhalten} und {@code skript} fuer Addons (ROADMAP M10/AD1/AD2/AD4).
 * Diese Klasse baut auf genau diesen bereits vorhandenen, uebersetzten
 * Bezeichnern auf, statt zusaetzliche, noch unuebersetzte Fein-Unterteilungen
 * zu erfinden, die heute von keinem Aufrufer gebraucht werden. Eine spaetere
 * Aufspaltung (z. B. {@code hud.read} vs. {@code hud.write}) bleibt moeglich,
 * ohne dieses Modell zu brechen -- sie kaeme als zusaetzlicher Aufzaehlungswert
 * samt eigenem Uebersetzungs-Key hinzu. {@code skript} schaltet seit ROADMAP
 * AD2 die X5-Skript-Sandbox fuer {@code scripts/*.vs} frei (siehe
 * {@code de.spassglas.vitrine.addon.script.client.ScriptAddons}).</p>
 */
public enum VpkgCapability {

	/** Darf Vanilla-/andere Texturen ersetzen (Resource-Pack-Bild-Ebene). */
	TEXTUREN("texturen", "vitrine.capability.texturen"),

	/** Darf das aktive Vitrine-Store-Theme (Farb-/Radius-/Blur-Tokens) ersetzen. */
	THEME("theme", "vitrine.capability.theme"),

	/** Darf HUD-Elemente lesen/anzeigen bzw. eigene HUD-Widgets einblenden. */
	HUD("hud", "vitrine.capability.hud"),

	/** Darf einen Shader anwenden. */
	SHADER("shader", "vitrine.capability.shader"),

	/** Darf Sounds ersetzen. */
	SOUND("sound", "vitrine.capability.sound"),

	/** Darf Sprachdateien (Uebersetzungen) ersetzen. */
	SPRACHE("sprache", "vitrine.capability.sprache"),

	/** Darf Kosmetik (z. B. Cape/Skin-Anhaenge) veraendern. */
	COSMETICS("cosmetics", "vitrine.capability.cosmetics"),
	/**
	 * ROADMAP M10/AD1: Verhaltens-Addon -- {@code data/**} wird als Datenpaket
	 * des integrierten Servers angemeldet (Rezepte, Loot, Fortschritte, Tags,
	 * Funktionen). Veraendert Spielregeln der Welt, fuehrt aber keinen
	 * Java-Code aus.
	 */
	VERHALTEN("verhalten", "vitrine.capability.verhalten"),

	/**
	 * ROADMAP AD2: Skript-Addon -- {@code scripts/*.vs} laeuft ereignisgesteuert
	 * (Tick, Welt betreten, Chat-Befehl {@code /v <name>}, Taste) in der
	 * X5-Skript-Sandbox ({@code de.spassglas.vitrine.script}), verdrahtet ueber
	 * {@code de.spassglas.vitrine.addon.script.client.ScriptAddons}. Kein
	 * Reflection, kein IO/Netz, Budget je Tick (siehe
	 * {@code ScriptAddonExecutor#STEP_BUDGET}).
	 */
	SKRIPT("skript", "vitrine.capability.skript");

	private static final Map<String, VpkgCapability> BY_ID = buildIndex();

	private final String id;
	private final String translationKey;

	VpkgCapability(String id, String translationKey) {
		this.id = id;
		this.translationKey = translationKey;
	}

	/** Der Bezeichner, wie er im Manifest unter {@code capabilities} steht (z. B. {@code "hud"}). */
	public String id() {
		return id;
	}

	/** Uebersetzungs-Key des Klartexttexts fuer diese Faehigkeit (S4-Faehigkeiten-Dialog). */
	public String translationKey() {
		return translationKey;
	}

	/** Klartexttext dieser Faehigkeit in {@code locale} (fuer Laien verstaendlich, siehe {@link Translations}). */
	public String describe(String locale) {
		return Translations.get(locale, translationKey);
	}

	/** Wie {@link #describe(String)}, mit {@link Translations#FALLBACK_LOCALE}. */
	public String describe() {
		return Translations.get(translationKey);
	}

	/** Loest einen Manifest-Bezeichner (z. B. {@code "hud"}) auf, oder {@link Optional#empty()}, wenn unbekannt. */
	public static Optional<VpkgCapability> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, VpkgCapability> buildIndex() {
		Map<String, VpkgCapability> index = new LinkedHashMap<>();
		for (VpkgCapability capability : values()) {
			index.put(capability.id, capability);
		}
		return index;
	}
}
