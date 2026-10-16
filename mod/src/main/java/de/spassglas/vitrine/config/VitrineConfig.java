package de.spassglas.vitrine.config;

import de.spassglas.vitrine.Vitrine;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Bequemer, gecachter Zugriff auf {@code settings.json} fuer die Oberflaeche:
 * {@link #get()} liest einmal, {@link #update} aendert und speichert sofort.
 * Die bestehenden statischen Abfragen in {@link SettingsModel} lesen dieselbe Datei.
 */
public final class VitrineConfig {

	private static VitrineSettings cached;

	private VitrineConfig() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
	}

	public static synchronized VitrineSettings get() {
		if (cached == null) {
			cached = SettingsManager.load(dir());
		}
		return cached;
	}

	public static synchronized void update(Consumer<VitrineSettings> change) {
		VitrineSettings s = get();
		change.accept(s);
		try {
			SettingsManager.save(dir(), s);
		} catch (IOException e) {
			Vitrine.LOGGER.error("Einstellungen nicht gespeichert: {}", e.toString());
		}
	}

	/** Nach Aenderungen ueber andere Wege (z. B. SettingsModel) neu einlesen. */
	public static synchronized void invalidate() {
		cached = null;
	}
}
