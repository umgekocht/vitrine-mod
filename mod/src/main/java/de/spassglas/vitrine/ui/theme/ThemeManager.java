package de.spassglas.vitrine.ui.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Laedt Themes, wechselt sie zur Laufzeit und benachrichtigt registrierte
 * Zuhoerer, damit ein offener Bildschirm sich neu aufbauen und damit sofort
 * umfaerben kann (ROADMAP T2, Fertig-Kriterium: "Theme-Wechsel faerbt den
 * offenen Store sofort um").
 *
 * <p>Client-frei testbar: der {@link ThemeMessagePresenter} ist eine
 * Schnittstelle (Test-Double zaehlt die Meldungen), die Zuhoerer sind reine
 * {@link Runnable}. Den einen echten Client-Manager haelt
 * {@link ThemeService}.</p>
 *
 * <p>Verhalten laut ROADMAP-Zeile:
 * <ul>
 *   <li><b>Laden/Wechseln:</b> {@link #loadAndSwitch(String, String)} parst
 *       und validiert gegen die eingebettete, byte-identische Kopie von
 *       {@code theme.schema.json} und macht die geladene Variante zum aktiven
 *       Token-Satz.</li>
 *   <li><b>Fallback bei fehlendem Token:</b> ein einzelner im geladenen Theme
 *       fehlender Token kommt aus dem Standard-Theme (siehe
 *       {@link JsonThemeTokens}), nie {@code 0}/{@code null}.</li>
 *   <li><b>Kaputtes Theme:</b> unparsebar oder Schema-Verstosz &rArr; das
 *       zuletzt aktive Theme bleibt in Kraft <em>und</em> es geht eine
 *       Klartext-Meldung ueber den {@link ThemeMessagePresenter} raus --
 *       keine Exception nach oben, kein stilles Schlucken.</li>
 * </ul>
 */
public final class ThemeManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Theme");

	private final ThemeTokens defaultTokens;
	private final ThemeMessagePresenter presenter;
	private final CopyOnWriteArrayList<Runnable> repaintListeners = new CopyOnWriteArrayList<>();

	private volatile ThemeVariant variant;
	private volatile ThemeTokens active;

	/**
	 * @param defaultTokens das Standard-/Fallback-Theme (normalerweise
	 *                      {@link DefaultThemeTokens#INSTANCE}); zugleich der
	 *                      Startzustand von {@link #active()}
	 * @param presenter     Ziel fuer die "kaputtes Theme"-Meldung
	 */
	public ThemeManager(ThemeTokens defaultTokens, ThemeMessagePresenter presenter) {
		this.defaultTokens = defaultTokens;
		this.presenter = presenter;
		this.variant = ThemeVariant.DARK;
		this.active = defaultTokens;
	}

	/** Der aktuell aktive Token-Satz. Beim Start das Standard-Theme. Nie {@code null}. */
	public ThemeTokens active() {
		return active;
	}

	/** Das unveraenderliche Standard-/Fallback-Theme. */
	public ThemeTokens defaultTokens() {
		return defaultTokens;
	}

	/** Kurzform fuer {@code active().size(token)}. */
	public int size(String token) {
		return active.size(token);
	}

	/** Kurzform fuer {@code active().color(token)}. */
	public int color(String token) {
		return active.color(token);
	}

	/** Welche Farbvariante ({@code light}/{@code dark}) geladene Themes gelesen werden. */
	public ThemeVariant variant() {
		return variant;
	}

	/**
	 * Setzt die Farbvariante fuer <em>kuenftige</em> Ladevorgaenge. Aendert das
	 * bereits aktive Theme nicht -- ein erneutes {@link #loadAndSwitch} bzw.
	 * {@link #resetToDefault()} zieht die neue Variante.
	 */
	public void setVariant(ThemeVariant variant) {
		this.variant = variant;
	}

	/**
	 * Registriert einen Zuhoerer, der bei jedem erfolgreichen Theme-Wechsel
	 * (auch {@link #resetToDefault()}) genau einmal laeuft -- gedacht fuer
	 * {@code StoreScreen}, der sich daraufhin neu aufbaut und sofort umfaerbt.
	 *
	 * @return ein Handle, dessen {@link Runnable#run()} den Zuhoerer wieder
	 *         abmeldet (bequem fuer {@code Screen#removed()})
	 */
	public Runnable addRepaintListener(Runnable listener) {
		repaintListeners.addIfAbsent(listener);
		return () -> removeRepaintListener(listener);
	}

	/** Meldet einen mit {@link #addRepaintListener} registrierten Zuhoerer ab. */
	public void removeRepaintListener(Runnable listener) {
		repaintListeners.remove(listener);
	}

	/** Nur fuer Tests/Diagnose: Anzahl aktuell registrierter Zuhoerer. */
	public int repaintListenerCount() {
		return repaintListeners.size();
	}

	/**
	 * Macht {@code tokens} zum aktiven Token-Satz und benachrichtigt alle
	 * Zuhoerer genau einmal.
	 */
	public void switchTo(ThemeTokens tokens) {
		this.active = tokens;
		notifyRepaintListeners();
	}

	/** Faellt auf das Standard-Theme zurueck und benachrichtigt die Zuhoerer. */
	public void resetToDefault() {
		switchTo(defaultTokens);
	}

	/**
	 * Parst {@code themeJson} (Inhalt einer {@code theme.json}), validiert es
	 * gegen {@code theme.schema.json} und macht die {@link #variant()}-Seite
	 * zum aktiven Theme.
	 *
	 * @param themeName menschenlesbarer Name fuer Log und Meldung
	 * @return {@code true} bei Erfolg; {@code false}, wenn das Theme kaputt war
	 *         (dann bleibt das bisherige aktiv und es ging eine Meldung raus)
	 */
	public boolean loadAndSwitch(String themeJson, String themeName) {
		try {
			switchTo(parseAndResolve(themeJson));
			LOGGER.info("Theme '{}' geladen und aktiv ({}-Variante).", themeName, variant.jsonKey());
			return true;
		} catch (ThemeFormatException e) {
			LOGGER.warn("Theme '{}' abgelehnt, Standard bleibt aktiv: {}", themeName, e.getMessage());
			presenter.showBrokenThemeKeptCurrent(themeName, e.getShortReason());
			return false;
		}
	}

	/** Wie {@link #loadAndSwitch(String, String)}, mit UTF-8-Bytes. */
	public boolean loadAndSwitch(byte[] themeJson, String themeName) {
		return loadAndSwitch(new String(themeJson, StandardCharsets.UTF_8), themeName);
	}

	private ThemeTokens parseAndResolve(String themeJson) throws ThemeFormatException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(themeJson);
		} catch (JsonParseException e) {
			throw new ThemeFormatException("theme.json ist kein gueltiges JSON: " + e.getMessage(),
					"kein gueltiges JSON");
		}
		if (parsed == null || !parsed.isJsonObject()) {
			throw new ThemeFormatException("theme.json muss ein JSON-Objekt als Wurzelelement haben",
					"Wurzelelement ist kein JSON-Objekt");
		}

		// T2 "Fallback bei fehlendem Token": ein einzelner fehlender Variant-
		// Token ist kein kaputtes Theme -- sein Wert kommt aus dem Standard
		// (siehe JsonThemeTokens). Falscher Typ, unbekanntes Feld oder eine
		// ganz fehlende Variante bleiben ein Fehler.
		List<String> violations = ThemeSchemaValidator.validate(
				ThemeSchemaValidator.embeddedSchema(), parsed, variantTokenNames());
		if (!violations.isEmpty()) {
			throw new ThemeFormatException(
					"theme.json entspricht nicht dem Schema: " + String.join("; ", violations),
					violations.get(0));
		}

		JsonObject variantObject = parsed.getAsJsonObject().getAsJsonObject(variant.jsonKey());
		return new JsonThemeTokens(variantObject, defaultTokens);
	}

	private volatile Set<String> cachedVariantTokenNames;

	/** Die Token-Namen der {@code variant}-{@code required}-Liste des eingebetteten Schemas. */
	private Set<String> variantTokenNames() {
		Set<String> names = cachedVariantTokenNames;
		if (names != null) {
			return names;
		}
		Set<String> built = new LinkedHashSet<>();
		ThemeSchemaValidator.embeddedSchema()
				.getAsJsonObject("$defs").getAsJsonObject("variant").getAsJsonArray("required")
				.forEach(e -> built.add(e.getAsString()));
		cachedVariantTokenNames = built;
		return built;
	}

	private void notifyRepaintListeners() {
		for (Runnable listener : repaintListeners) {
			try {
				listener.run();
			} catch (RuntimeException e) {
				// Ein fehlerhafter Zuhoerer darf die anderen nicht mitreiszen
				// und den Theme-Wechsel nicht abbrechen -- aber er wird geloggt,
				// nicht verschluckt.
				LOGGER.error("Theme-Wechsel-Zuhoerer hat eine Ausnahme geworfen: {}", e.toString(), e);
			}
		}
	}

	/** Intern: unterscheidbarer Grund fuer Log (ausfuehrlich) und Toast (kurz). */
	private static final class ThemeFormatException extends Exception {
		private final String shortReason;

		ThemeFormatException(String message, String shortReason) {
			super(message);
			this.shortReason = shortReason;
		}

		String getShortReason() {
			return shortReason;
		}
	}
}
