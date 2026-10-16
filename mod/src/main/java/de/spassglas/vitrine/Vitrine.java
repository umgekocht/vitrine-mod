package de.spassglas.vitrine;

import de.spassglas.vitrine.config.SettingsManager;
import de.spassglas.vitrine.config.VitrineSettings;
import de.spassglas.vitrine.hotapply.HotApplyCommand;
import de.spassglas.vitrine.hud.ActiveHudRegistry;
import de.spassglas.vitrine.hud.HudLayoutOverrides;
import de.spassglas.vitrine.hud.client.HudCommand;
import de.spassglas.vitrine.hud.client.HudWidgetRenderer;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.VitrineKeyBindings;
import de.spassglas.vitrine.ui.classb.ClientJavaModStaging;
import de.spassglas.vitrine.ui.screen.StoreEntryPoint;
import de.spassglas.vitrine.ui.screen.StoreMenuIntegration;
import de.spassglas.vitrine.ui.theme.ThemeService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Vitrine implements ClientModInitializer {
	public static final String MOD_ID = "vitrine";
	public static final Logger LOGGER = LoggerFactory.getLogger("Vitrine");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Vitrine startet.");

		var configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		VitrineSettings settings = SettingsManager.load(configDir);
		LOGGER.info("Einstellungen geladen (Schema-Version {}) aus {}", settings.schemaVersion, configDir);

		// ROADMAP H4 ("Zustandsdatei state.json"): welche Pakete installiert
		// und aktiv sind, ueberlebt einen Absturz mitten im Schreiben (siehe
		// PackStateStore). H5/S5 lesen und schreiben denselben Zustand, sobald
		// es Deinstallieren bzw. eine Bibliotheks-Oberflaeche dafuer gibt.
		PackState packState = PackStateStore.load(configDir);
		LOGGER.info("Paket-Zustand geladen (Schema-Version {}): {} installiert, {} aktiv.",
				packState.schemaVersion, packState.installedPackIdsInOrder().size(),
				packState.activePackIdsInOrder().size());

		// ROADMAP GATE115: ein zwischen PackUpdater-Schritt "alte Version zur Seite
		// legen" und "neue Version nach packs/ setzen" abgebrochener Update-Lauf
		// (z. B. Spielabbruch) hinterlaesst ein in state.json installiertes Paket
		// ohne .vpkg in packs/, die alte, gepruefte Version liegt noch im
		// Backup-Ordner. Vor jedem Zugriff auf Bibliothek/Diagnose wird das hier
		// geheilt -- oder bleibt, falls kein Backup mehr auffindbar ist, sichtbar
		// als kaputt/fehlend stehen statt stillschweigend zu verschwinden.
		int recoveredPacks = de.spassglas.vitrine.pack.OrphanedUpdateBackupRecovery.recover(configDir);
		if (recoveredPacks > 0) {
			LOGGER.info("Start-Aufraeumung: {} verwaiste(s) Update-Backup(s) zurueck nach packs/ gestellt.",
					recoveredPacks);
		}

		// ROADMAP H2 ("Anwenden zur Laufzeit"): solange es noch keine
		// Store-Oberflaeche gibt (M3), ist /vitrine hotapply <packId> der
		// verdrahtete Einstiegspunkt in HotApplyCoordinator -- S4 ruft
		// spaeter denselben HotApplyService aus einem Knopf statt einem
		// Befehl heraus auf.
		HotApplyCommand.register();

		// ROADMAP T5 ("HUD-Schema und -Laufzeit"): der Renderer zeichnet je
		// Frame aus ActiveHudRegistry; /vitrine hud <packId> ist -- analog zu
		// /vitrine hotapply -- der verdrahtete Aktivierungsweg, solange es
		// noch keine Store-Oberflaeche dafuer gibt (S4).
		HudWidgetRenderer.register();
		HudCommand.register();
		// HUD-Pakete erscheinen, sobald sie eingeschaltet sind -- ohne Befehl.
		de.spassglas.vitrine.hud.client.HudAutoSync.register();

		// ROADMAP T6 ("HUD-Editor im Spiel"): die Nutzer-Overrides
		// (config/vitrine/hud/overrides.json) liegen beim Aufloesen ueber dem
		// schreibgeschuetzten Paket-Layout -- verschobene/aus-/eingeschaltete
		// Widgets ueberleben so den Neustart. Eine kaputte Datei fuehrt zu
		// leeren Overrides + Backup (siehe HudOverridesStore).
		HudLayoutOverrides hudOverrides = HudLayoutOverrides.forConfigDir(configDir);
		ActiveHudRegistry.instance().setOverrides(hudOverrides);
		LOGGER.info("HUD-Overrides geladen: {} Eintrag/Eintraege.", hudOverrides.asMap().size());

		// ROADMAP T2 ("ThemeManager"): beim Start ist das aus VitrineTheme
		// abgeleitete Standard-Theme aktiv (ohne dass eine Datei da sein muss,
		// Charter-Regel 12). Der offene Store haengt sich als Umfaerb-Zuhoerer
		// ein; T4 laedt spaeter Theme-Pakete ueber denselben ThemeService.
		LOGGER.info("Theme-Manager bereit, aktiv: Standard-Theme ({}-Variante).",
				ThemeService.instance().variant().jsonKey());

		// Vitrine-Bildschirme brauchen ~960 Einheiten Breite; bei hoher
		// GUI-Skalierung wird sie nur fuer diese Bildschirme gesenkt.
		de.spassglas.vitrine.ui.VitrineGuiScale.register();
		// Nur Entwicklung (-Dvitrine.autoshot=<ordner>): Bildschirme automatisch fotografieren.
		de.spassglas.vitrine.dev.AutoShot.registerIfRequested();
		// ROADMAP AD1: Verhaltens-Addons beim Oeffnen einer Welt anschalten.
		de.spassglas.vitrine.addon.AddonDataPacks.register();
		// ROADMAP M10: Verhaltensregeln (regeln/*.json) live, ohne Neustart.
		de.spassglas.vitrine.addon.AddonRules.register();
		// ROADMAP AD2: Skript-Addons (scripts/*.vs) in der X5-Sandbox, live, ohne Neustart.
		de.spassglas.vitrine.addon.script.client.ScriptAddonKeyBinding.register();
		de.spassglas.vitrine.addon.script.client.ScriptAddonCommand.register();
		de.spassglas.vitrine.addon.script.client.ScriptAddons.register();

		// ROADMAP S0 ("Tastenkuerzel M, Knopf im Haupt- und Pausenmenue"):
		// die Store-Oberflaeche selbst ist ein reines Client-Layout-Geruest
		// (de.spassglas.vitrine.ui.screen.StoreScreen) -- hier wird nur
		// verdrahtet, wie man hinkommt.
		// ROADMAP X4: StoreEntryPoint entscheidet dabei zusaetzlich, ob vorher
		// noch der Erster-Start-Ablauf (OnboardingScreen) faellig ist.
		// ROADMAP J2 ("Klasse B"): nach einem echten Neustart sind die zuvor nach
		// mods/ gestageten Jars jetzt von der JVM geladen -- die staged-mods.json-
		// Eintraege wechseln von "pending" auf "geladen", danach verweigert die
		// Ruecknahme (unstage) sie ehrlich. Ohne gestagete Jars ist das ein No-op.
		ClientJavaModStaging.instance().markAllPendingAsLoaded();

		KeyBinding openStore = VitrineKeyBindings.registerOpenStore();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openStore.wasPressed()) {
				if (client.currentScreen == null) {
					StoreEntryPoint.open(client, null);
				}
			}
		});
		StoreMenuIntegration.register();
	}
}
