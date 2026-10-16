package de.spassglas.vitrine.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;

/**
 * Eigene GUI-Skalierung fuer Vitrine-Bildschirme.
 *
 * <p>Vitrine ist auf eine Arbeitsflaeche von rund 1000 Einheiten Breite
 * ausgelegt (docs/UI-SPEC.md, Microsoft-Store-Masse). Minecraft waehlt bei
 * einem 1080p-Fenster mit "Auto" aber Skalierung 4 -- uebrig bleiben ~480
 * Einheiten: Text lief aus dem Bild, Abschnitte passten nicht ins Fenster
 * (im Spiel gefunden, 2026-10-06). Solange ein Vitrine-Bildschirm offen ist,
 * wird die Skalierung deshalb so weit gesenkt, dass mindestens
 * {@link #MIN_WIDTH} x {@link #MIN_HEIGHT} Einheiten Platz sind -- nie
 * erhoeht. Beim Schliessen gilt sofort wieder die Einstellung des Spielers;
 * die Optionen selbst werden nie veraendert.</p>
 */
public final class VitrineGuiScale {

	public static final int MIN_WIDTH = 960;
	public static final int MIN_HEIGHT = 480;

	private static boolean resizing;

	private VitrineGuiScale() {
	}

	/**
	 * Skalierung fuer ein Fenster mit {@code framebufferWidth} x
	 * {@code framebufferHeight} Pixeln: hoechstens {@code playerScale}, so gross
	 * wie moeglich, solange mindestens {@link #MIN_WIDTH} x {@link #MIN_HEIGHT}
	 * Einheiten bleiben, und nie unter 1.
	 */
	public static int targetScale(int framebufferWidth, int framebufferHeight, int playerScale) {
		int fit = Math.min(framebufferWidth / MIN_WIDTH, framebufferHeight / MIN_HEIGHT);
		return Math.max(1, Math.min(playerScale, fit));
	}

	/** Ob {@code screen} zur Vitrine-Oberflaeche gehoert. */
	public static boolean isVitrineScreen(Screen screen) {
		return screen != null && screen.getClass().getName().startsWith("de.spassglas.vitrine.");
	}

	/** Einmal beim Client-Start: haengt sich an jeden Bildschirmaufbau (auch nach Fenstergroessen-Aenderung). */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!isVitrineScreen(screen) || resizing) {
				return;
			}
			apply(client, screen);
			ScreenEvents.remove(screen).register(removed -> restore(client));
		});
	}

	private static void apply(MinecraftClient client, Screen screen) {
		Window window = client.getWindow();
		int current = (int) window.getScaleFactor();
		int target = targetScale(window.getFramebufferWidth(), window.getFramebufferHeight(), playerScale(client));
		if (target == current) {
			return;
		}
		window.setScaleFactor(target);
		resizing = true;
		try {
			screen.resize(client, window.getScaledWidth(), window.getScaledHeight());
		} finally {
			resizing = false;
		}
	}

	/** Zurueck auf die Skalierung, die Minecraft aus den Optionen des Spielers berechnet. */
	private static void restore(MinecraftClient client) {
		Window window = client.getWindow();
		int vanilla = playerScale(client);
		if ((int) window.getScaleFactor() != vanilla) {
			window.setScaleFactor(vanilla);
		}
	}

	private static int playerScale(MinecraftClient client) {
		return client.getWindow().calculateScaleFactor(client.options.getGuiScale().getValue(),
				client.forcesUnicodeFont());
	}
}
