package de.spassglas.vitrine.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liest die Vitrine-Sprachdateien unter {@code assets/vitrine/lang/<locale>.json}
 * (Fabric-Konvention, flache Key-Value-JSON-Objekte) und stellt
 * Uebersetzungs-Keys programmatisch bereit.
 *
 * <p>Minecraft selbst laedt {@code assets/vitrine/lang/*.json} automatisch als
 * Teil der eingebauten Ressourcen des Mods, sobald der Store mit
 * {@code Text.translatable(key)} arbeitet -- diese Klasse ist dafuer nicht
 * noetig. Sie deckt die Faelle ab, in denen ein Text ausserhalb des
 * Text-Rendering-Wegs gebraucht wird (z. B. Tests, spaeter evtl.
 * Log-/Diagnose-Ausgaben, die trotzdem konsistent mit den Store-Texten sein
 * sollen) und dient als Fundament fuer F3: eine einzige Stelle, an der Keys
 * nachgeschlagen werden, statt Text irgendwo im Code fest zu verdrahten.</p>
 */
public final class Translations {

	/** Locale, auf die zurueckgefallen wird, wenn ein Key oder eine Locale fehlt. */
	public static final String FALLBACK_LOCALE = "en_us";

	private static final String LANG_RESOURCE_PATTERN = "/assets/vitrine/lang/%s.json";
	private static final Type LANG_MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
	}.getType();

	private static final Gson GSON = new Gson();
	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/I18n");
	private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

	private Translations() {
	}

	/**
	 * Liefert den Text zu {@code key} in {@code locale}. Fehlt der Key dort,
	 * wird auf {@link #FALLBACK_LOCALE} zurueckgefallen. Fehlt er auch dort,
	 * wird der Key selbst zurueckgegeben (nie {@code null}, nie eine
	 * Exception) und eine Warnung geloggt.
	 */
	public static String get(String locale, String key) {
		String direct = load(locale).get(key);
		if (direct != null) {
			return direct;
		}
		if (!locale.equals(FALLBACK_LOCALE)) {
			String fallback = load(FALLBACK_LOCALE).get(key);
			if (fallback != null) {
				return fallback;
			}
		}
		LOGGER.warn("Kein Uebersetzungs-Key '{}' in Locale '{}' oder Fallback '{}' gefunden.",
				key, locale, FALLBACK_LOCALE);
		return key;
	}

	/** Kurzform von {@link #get(String, String)} mit {@link #FALLBACK_LOCALE}. */
	public static String get(String key) {
		return get(FALLBACK_LOCALE, key);
	}

	/**
	 * Wie {@link #get(String, String)}, formatiert das Ergebnis aber zusaetzlich
	 * mit {@link String#format(String, Object...)} -- dieselben
	 * {@code %s}/{@code %d}-Platzhalter, die auch Minecrafts {@code
	 * Text.translatable(key, args)} im laufenden Spiel einsetzt. Gedacht als
	 * Rueckfallebene fuer Code-Pfade, die Text brauchen, ohne dass Minecrafts
	 * {@code Language}-Singleton (und damit die vom Mod mitgebrachten
	 * Sprachdateien) bereits geladen ist -- etwa in Kopffreien Tests oder vor
	 * dem ersten Ressourcen-Reload.
	 */
	public static String format(String locale, String key, Object... args) {
		String pattern = get(locale, key);
		if (args == null || args.length == 0) {
			return pattern;
		}
		return String.format(pattern, args);
	}

	/** Ob {@code locale} genau diesen Key definiert (ohne Fallback). */
	public static boolean has(String locale, String key) {
		return load(locale).containsKey(key);
	}

	/** Alle Keys, die {@code locale} definiert. */
	public static Set<String> keys(String locale) {
		return Collections.unmodifiableSet(load(locale).keySet());
	}

	private static Map<String, String> load(String locale) {
		return CACHE.computeIfAbsent(locale, Translations::readLangFile);
	}

	private static Map<String, String> readLangFile(String locale) {
		String resourcePath = String.format(LANG_RESOURCE_PATTERN, locale);
		try (InputStream in = Translations.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				LOGGER.error("Sprachdatei '{}' wurde auf dem Klassenpfad nicht gefunden.", resourcePath);
				return Map.of();
			}
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> parsed = GSON.fromJson(reader, LANG_MAP_TYPE);
				return parsed == null ? Map.of() : Collections.unmodifiableMap(parsed);
			}
		} catch (IOException e) {
			LOGGER.error("Sprachdatei '{}' konnte nicht gelesen werden: {}", resourcePath, e.toString(), e);
			return Map.of();
		}
	}
}
