package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.config.SettingsModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Der eine Weg, den Store zu oeffnen (ROADMAP X4). Sowohl die Taste M
 * ({@code Vitrine.onInitializeClient}) als auch die Menue-Knopfe
 * ({@link StoreMenuIntegration}) rufen ausschliesslich {@link #open} auf,
 * damit es nur eine Stelle gibt, die entscheidet: wurde der Erster-Start-
 * Ablauf ({@link OnboardingScreen}) schon einmal bestaetigt
 * ({@link SettingsModel#onboardingSeen()})? Wenn nicht, erscheint er jetzt
 * -- vor dem {@link StoreScreen} selbst; danach nie wieder.
 */
public final class StoreEntryPoint {

	private StoreEntryPoint() {
	}

	/** @param parentScreen der Bildschirm, zu dem am Ende "Zurueck"/ESC zurueckkehrt, oder {@code null}. */
	public static void open(MinecraftClient client, Screen parentScreen) {
		var configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		if (SettingsModel.onboardingSeen(configDir)) {
			client.setScreen(new StoreScreen(parentScreen));
		} else {
			client.setScreen(new OnboardingScreen(parentScreen));
		}
	}
}
