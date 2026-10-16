package de.spassglas.vitrine.dev;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.ui.screen.LibraryScreen;
import de.spassglas.vitrine.ui.screen.OnboardingScreen;
import de.spassglas.vitrine.ui.screen.SettingsScreen;
import de.spassglas.vitrine.ui.screen.StoreScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.util.List;
import java.util.function.Function;

/**
 * Nur fuer die Entwicklung: mit {@code -Dvitrine.autoshot=<ordner>} oeffnet
 * der Client nacheinander die Vitrine-Bildschirme, speichert je einen
 * Screenshot nach {@code <ordner>/screenshots/} und beendet sich. So laesst
 * sich die Oberflaeche nach einer Aenderung pruefen, ohne dass jemand klickt.
 * Ohne die Systemeigenschaft passiert nichts.
 */
public final class AutoShot {

	private static final int SETTLE_TICKS = 100;

	private record Step(String name, Function<Screen, Screen> open) {
	}

	private AutoShot() {
	}

	public static void registerIfRequested() {
		String dir = System.getProperty("vitrine.autoshot");
		if (dir == null || dir.isBlank()) {
			return;
		}
		File out = new File(dir);
		List<Step> steps = List.of(
				new Step("01-onboarding", OnboardingScreen::new),
				new Step("02-store-start", StoreScreen::new),
				new Step("02b-store-scrolled", StoreScreen::new),
				new Step("02c-store-addons", StoreScreen::new),
				new Step("02d-store-textures", StoreScreen::new),
				new Step("02e-store-mods", StoreScreen::new),
				new Step("02f-store-suggest", StoreScreen::new),
				new Step("02g-store-results", StoreScreen::new),
				new Step("02h-store-account", StoreScreen::new),
				new Step("02i-store-product", StoreScreen::new),
				new Step("02j-store-settings", StoreScreen::new),
				new Step("03-settings", SettingsScreen::new),
				new Step("04-library", LibraryScreen::new));
		int[] state = {-1, 0};
		Screen[] title = {null};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (state[0] == -1) {
				// Ein frisches Profil zeigt zuerst Minecrafts Barrierefreiheits-Hinweis,
				// nicht den Titelbildschirm -- jeder fremde Startbildschirm reicht.
				Screen first = client.currentScreen;
				if (first != null && !de.spassglas.vitrine.ui.VitrineGuiScale.isVitrineScreen(first)
						&& client.getOverlay() == null && ++state[1] > SETTLE_TICKS) {
					Screen ts = first instanceof TitleScreen ? first : new TitleScreen();
					shoot(client, out, "00-start-screen");
					title[0] = ts;
					state[0] = 0;
					state[1] = 0;
					client.setScreen(steps.get(0).open().apply(ts));
				}
				return;
			}
			if (state[0] >= steps.size()) {
				return;
			}
			++state[1];
			// "-addons"/"-textures"/"-mods": den Bereich oeffnen (Modrinth laedt nach)
			String stepName = steps.get(state[0]).name();
			if (state[1] == 5 && client.currentScreen instanceof StoreScreen store) {
				if (stepName.endsWith("-addons")) {
					store.openSection(de.spassglas.vitrine.ui.nav.NavRailEntry.ADDONS);
				} else if (stepName.endsWith("-textures")) {
					store.openSection(de.spassglas.vitrine.ui.nav.NavRailEntry.TEXTURES);
				} else if (stepName.endsWith("-mods")) {
					store.openSection(de.spassglas.vitrine.ui.nav.NavRailEntry.MODS);
				} else if (stepName.endsWith("-suggest")) {
					store.autoShotSearch("so", false);
				} else if (stepName.endsWith("-results")) {
					store.autoShotSearch("sodium", true);
				} else if (stepName.endsWith("-account")) {
					store.autoShotAccount();
				} else if (stepName.endsWith("-product")) {
					store.autoShotProduct();
				} else if (stepName.endsWith("-settings")) {
					store.autoShotSettings();
				}
			}
			// "-scrolled": nach dem Laden einmal kraeftig nach unten scrollen.
			if (state[1] == SETTLE_TICKS / 2 && steps.get(state[0]).name().endsWith("-scrolled")
					&& client.currentScreen != null) {
				Screen sc = client.currentScreen;
				for (int i = 0; i < 4; i++) {
					sc.mouseScrolled(sc.width * 0.6, sc.height * 0.6, 0, -1);
				}
			}
			if (state[1] < SETTLE_TICKS) {
				return;
			}
			shoot(client, out, steps.get(state[0]).name());
			state[0]++;
			state[1] = 0;
			if (state[0] < steps.size()) {
				client.setScreen(steps.get(state[0]).open().apply(title[0]));
			} else {
				Vitrine.LOGGER.info("AutoShot fertig, beende den Client.");
				client.scheduleStop();
			}
		});
		Vitrine.LOGGER.info("AutoShot aktiv, Screenshots nach {}", out);
	}

	private static void shoot(MinecraftClient client, File out, String name) {
		ScreenshotRecorder.saveScreenshot(out, name + ".png", client.getFramebuffer(),
				message -> Vitrine.LOGGER.info("AutoShot: {}", message.getString()));
	}
}
