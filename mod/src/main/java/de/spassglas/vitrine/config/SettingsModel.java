package de.spassglas.vitrine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Client-freie Schalter-Logik der Vitrine-Einstellungen (ROADMAP S9). Haelt die
 * geladenen {@link VitrineSettings} im Speicher und schreibt jede Aenderung
 * sofort ueber einen {@link Store}-Seam zurueck -- im echten Spiel
 * {@link SettingsManager} auf {@code config/vitrine/settings.json} (atomar),
 * im Test ein einfacher Ersatz. Kein Minecraft-Typ, damit
 * "Schalter schreibt settings.json" headless pruefbar ist.
 *
 * <p>S9 brachte den ersten Schalter ("Animationen reduzieren"), ROADMAP J1 den
 * zweiten ("Java-Mods anzeigen", Opt-in fuer Klasse-B-Angebote). Weitere
 * Optionen kommen erst, wenn eine spaetere Zeile sie verlangt.</p>
 */
public final class SettingsModel {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Config");

	/** Persistenz-Seam: laedt und speichert die Einstellungen. */
	public interface Store {
		VitrineSettings load();

		void save(VitrineSettings settings);
	}

	private final Store store;
	private VitrineSettings current;

	public SettingsModel(Store store) {
		this.store = Objects.requireNonNull(store, "store");
		this.current = Objects.requireNonNull(store.load(), "store.load() lieferte null");
	}

	/** Einstellungs-Modell auf {@code config/vitrine/settings.json} im angegebenen Vitrine-Konfigordner. */
	public static SettingsModel forConfigDir(Path vitrineConfigDir) {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		return new SettingsModel(new FileStore(vitrineConfigDir));
	}

	/**
	 * Bequemer Lesezugriff auf {@code reduceMotion} ohne ein Modell zu halten --
	 * denselben Weg nimmt der offene {@code StoreScreen} bei jedem Aufbau, damit
	 * ein anderswo umgelegter Schalter sofort wirkt.
	 */
	public static boolean reduceMotion(Path vitrineConfigDir) {
		return SettingsManager.load(vitrineConfigDir).reduceMotion;
	}

	public boolean reduceMotion() {
		return current.reduceMotion;
	}

	/**
	 * Bequemer Lesezugriff auf {@code showJavaModOffers} (ROADMAP J1) ohne ein
	 * Modell zu halten -- denselben Weg nimmt {@code JavaModOfferGate} bei jeder
	 * Filterung, damit ein umgelegter Opt-in-Schalter sofort greift.
	 */
	public static boolean showJavaModOffers(Path vitrineConfigDir) {
		return SettingsManager.load(vitrineConfigDir).showJavaModOffers;
	}

	public boolean showJavaModOffers() {
		return current.showJavaModOffers;
	}

	/** Setzt "Java-Mods anzeigen" und schreibt sofort zurueck, falls sich der Wert aendert. */
	public void setShowJavaModOffers(boolean value) {
		if (current.showJavaModOffers == value) {
			return;
		}
		current.showJavaModOffers = value;
		store.save(current);
	}

	/** Legt "Java-Mods anzeigen" um und schreibt sofort zurueck. */
	public boolean toggleShowJavaModOffers() {
		setShowJavaModOffers(!current.showJavaModOffers);
		return current.showJavaModOffers;
	}

	/**
	 * Bequemer Lesezugriff auf {@code onboardingSeen} (ROADMAP X4) ohne ein
	 * Modell zu halten -- denselben Weg nimmt {@code StoreEntryPoint} bei
	 * jedem Oeffnen des Stores, um zu entscheiden, ob der Erster-Start-Ablauf
	 * noch fehlt.
	 */
	public static boolean onboardingSeen(Path vitrineConfigDir) {
		return SettingsManager.load(vitrineConfigDir).onboardingSeen;
	}

	public boolean onboardingSeen() {
		return current.onboardingSeen;
	}

	/** Setzt "Erster-Start-Ablauf gesehen" und schreibt sofort zurueck, falls sich der Wert aendert. */
	public void setOnboardingSeen(boolean value) {
		if (current.onboardingSeen == value) {
			return;
		}
		current.onboardingSeen = value;
		store.save(current);
	}

	/** Setzt "Animationen reduzieren" und schreibt sofort zurueck, falls sich der Wert aendert. */
	public void setReduceMotion(boolean value) {
		if (current.reduceMotion == value) {
			return;
		}
		current.reduceMotion = value;
		store.save(current);
	}

	/** Legt "Animationen reduzieren" um und schreibt sofort zurueck. */
	public boolean toggleReduceMotion() {
		setReduceMotion(!current.reduceMotion);
		return current.reduceMotion;
	}

	/** {@link Store} auf {@link SettingsManager}. Ein fehlgeschlagenes Schreiben wird geloggt, nicht verschluckt. */
	private static final class FileStore implements Store {
		private final Path dir;

		FileStore(Path dir) {
			this.dir = dir;
		}

		@Override
		public VitrineSettings load() {
			return SettingsManager.load(dir);
		}

		@Override
		public void save(VitrineSettings settings) {
			try {
				SettingsManager.save(dir, settings);
			} catch (IOException e) {
				LOGGER.error("Konnte Einstellungen nicht nach {} schreiben: {}", dir, e.toString(), e);
			}
		}
	}
}
