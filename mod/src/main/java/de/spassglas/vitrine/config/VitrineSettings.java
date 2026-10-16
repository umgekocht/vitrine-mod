package de.spassglas.vitrine.config;

/**
 * Inhalt von {@code config/vitrine/settings.json}.
 *
 * <p>Neue Felder kommen als zusaetzliche, mit sinnvollem Default versehene
 * Member dazu. Aendert sich die Bedeutung eines bestehenden Feldes so, dass
 * alte Dateien nicht mehr direkt lesbar sind, steigt
 * {@link #CURRENT_SCHEMA_VERSION} und in {@link SchemaMigrations#STEPS} kommt
 * ein Umwandlungsschritt fuer die alte Version dazu -- siehe dort.</p>
 */
public final class VitrineSettings {

	/** Aktuelle Schema-Version. Muss zu {@link SchemaMigrations#STEPS} passen. */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public int schemaVersion = CURRENT_SCHEMA_VERSION;

	/**
	 * "Animationen reduzieren" (docs/UI-SPEC.md "Bewegung": "alles sofort, ohne
	 * Uebergang"). Default {@code false} = Animationen an. ROADMAP S9 baut die
	 * Einstellungs-Oberflaeche dazu; S8 liest den Schalter bereits, damit der
	 * Hero-Karussell-Wechsel bei aktiviertem Schalter ohne Uebergang stattfindet.
	 */
	public boolean reduceMotion = false;

	/**
	 * "Java-Mods anzeigen" -- Opt-in fuer Klasse-B-Angebote (ROADMAP J1).
	 * Klasse B sind {@code .jar}-Mods: sie brauchen einen Neustart und werden
	 * nie sofort ("hot") aktiv (Charter-Regel 2). Default {@code false}: ohne
	 * diesen Schalter taucht an keiner Store-Stelle ein Jar-Angebot auf.
	 *
	 * <p>Rein additiv -- eine alte {@code settings.json} ohne das Feld ergibt
	 * ueber den Gson-Default ebenfalls {@code false}; es braucht keinen
	 * Migrationsschritt und {@link #CURRENT_SCHEMA_VERSION} bleibt gleich.</p>
	 */
	public boolean showJavaModOffers = true;

	// --- Einstellungsseite im Microsoft-Store-Stil (umgekocht, 2026-10-07) ---
	// Fehlende Felder in alten settings.json bekommen beim Laden diese Vorgaben.

	/** Modrinth-Inhalte im Store zeigen (Startseite, Suche, Modrinth-Bereich). */
	public boolean showModrinth = true;
	/** Banner auf der Startseite wechselt von selbst. */
	public boolean bannerAutoplay = true;
	/** Leuchten um das Banner. */
	public boolean bannerGlow = true;
	/** Quell-Abzeichen (Vitrine/Modrinth) an den Logos. */
	public boolean showSourceBadges = true;
	/** Vor einer Installation von Modrinth die Detailseite zeigen statt direkt zu installieren. */
	public boolean confirmModrinthInstall = false;
	/** Suchverlauf merken und beim Antippen der Suche anzeigen. */
	public boolean saveSearchHistory = true;
	/** Live-Vorschlaege von Modrinth beim Tippen. */
	public boolean liveSuggestions = true;
	/** Mods mit Pflicht-Abhaengigkeiten automatisch mitinstallieren. */
	public boolean installDependencies = true;
	/** Addon-Plattform: Skript-Addons aus config/vitrine/dev-addons/ live laden (Entwickler-Modus). */
	public boolean devAddons = false;
	/** Gemerkter Suchverlauf (neueste zuerst). */
	public java.util.List<String> searchHistory = new java.util.ArrayList<>();

	/**
	 * Ob der Erster-Start-Ablauf (ROADMAP X4: Erklaerung von Klasse A/B und
	 * Faehigkeiten) bereits einmal bestaetigt wurde. Default {@code false}:
	 * ein frischer Spielstand zeigt {@code OnboardingScreen} beim ersten
	 * Oeffnen des Stores (Taste M oder Menue-Knopf, siehe
	 * {@code StoreEntryPoint}); danach nie wieder.
	 *
	 * <p>Rein additiv, wie {@link #showJavaModOffers} -- eine alte
	 * {@code settings.json} ohne das Feld ergibt ueber den Gson-Default
	 * ebenfalls {@code false} (Onboarding erscheint einmal nachtraeglich),
	 * kein Migrationsschritt, {@link #CURRENT_SCHEMA_VERSION} bleibt gleich.</p>
	 */
	public boolean onboardingSeen = false;

	public VitrineSettings() {
		// Gson braucht einen No-Arg-Konstruktor zum Deserialisieren.
	}

	public static VitrineSettings createDefault() {
		return new VitrineSettings();
	}
}
