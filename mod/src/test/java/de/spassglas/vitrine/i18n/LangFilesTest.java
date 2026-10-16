package de.spassglas.vitrine.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Deckt den ersten Teil des Fertig-Kriteriums von F3 ab: {@code en_us.json}
 * und {@code de_de.json} sind gueltiges JSON, tragen exakt dieselbe
 * Schluesselmenge und keine leeren Werte.
 */
public final class LangFilesTest {

	private static final String[] LOCALES = {"en_us", "de_de"};
	private static final Gson GSON = new Gson();
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
	}.getType();

	@Test
	public void bothLangFilesAreValidNonEmptyJsonObjects() throws IOException {
		for (String locale : LOCALES) {
			Map<String, String> parsed = readLangFile(locale);
			assertNotNull(parsed, locale + ".json muss sich als JSON-Objekt lesen lassen");
			assertFalse(parsed.isEmpty(), locale + ".json darf nicht leer sein");
		}
	}

	@Test
	public void bothLocalesShareExactlyTheSameKeySet() throws IOException {
		Set<String> enKeys = readLangFile("en_us").keySet();
		Set<String> deKeys = readLangFile("de_de").keySet();

		assertEquals(deKeys, enKeys,
				"de_de.json muss exakt dieselben Keys wie en_us.json haben -- kein Key nur in einer Sprache");
	}

	@Test
	public void noValueIsEmptyOrBlankInAnyLocale() throws IOException {
		for (String locale : LOCALES) {
			for (Map.Entry<String, String> entry : readLangFile(locale).entrySet()) {
				assertTrue(entry.getValue() != null && !entry.getValue().isBlank(),
						locale + ".json: Key '" + entry.getKey() + "' hat einen leeren Wert");
			}
		}
	}

	/**
	 * ROADMAP GATE12: kein sichtbarer Text nennt eine interne
	 * Backlog-/Gate-Kennung. Ausloeser war
	 * {@code vitrine.downloads.progress.seam_note}, in dem woertlich „K3"
	 * stand -- fuer einen Spieler bedeutungslose Entwicklersprache (und seit
	 * dem echten Fortschritt aus GATE4 zusaetzlich falsch).
	 */
	@Test
	public void noVisibleTextMentionsAnInternalBacklogIdentifier() throws IOException {
		Pattern internalId = Pattern.compile("(?<![A-Za-z0-9])(GATE ?\\d+|[KSHMTPXDAFJ]\\d+)(?![A-Za-z0-9])");

		for (String locale : LOCALES) {
			for (Map.Entry<String, String> entry : readLangFile(locale).entrySet()) {
				Matcher matcher = internalId.matcher(entry.getValue());
				boolean found = matcher.find();
				assertFalse(found,
						locale + ".json: Key '" + entry.getKey() + "' nennt die interne Kennung '"
								+ (found ? matcher.group() : "") + "' im sichtbaren Text: "
								+ entry.getValue());
			}
		}
	}

	/**
	 * GATE92: ASCII-Ersatzschreibung deutscher Umlaute/ß ("liess" statt
	 * "ließ", "Groesse" statt "Größe") gehoert ins Log, nicht ins sichtbare
	 * UI. Prueft gezielt die Woerter, die frueher ersatzgeschrieben waren --
	 * ein generischer "ae|oe|ue"-Scan wuerde bei echten deutschen Woertern
	 * (z. B. "Museum", "Abteilung") falsch anschlagen.
	 */
	@Test
	public void germanLocaleUsesRealUmlautsInsteadOfAsciiSubstitution() throws IOException {
		Pattern asciiSubstitution = Pattern.compile(
				"\\b\\w*(?:liess|Faehig|faehig|pruef|Pruef|ungueltig|Ungueltig|Integritaets|fuer)\\w*\\b");

		for (Map.Entry<String, String> entry : readLangFile("de_de").entrySet()) {
			Matcher matcher = asciiSubstitution.matcher(entry.getValue());
			boolean found = matcher.find();
			assertFalse(found,
					"de_de.json: Key '" + entry.getKey() + "' nutzt eine ASCII-Ersatzschreibung ('"
							+ (found ? matcher.group() : "") + "') statt echter Umlaute/ß: "
							+ entry.getValue());
		}
	}

	@Test
	public void translationsHelperResolvesKnownKeyPerLocale() {
		assertEquals(Translations.get("de_de", "vitrine.store.title"), "Vitrine");
		assertEquals(Translations.get("en_us", "vitrine.action.install"), "Install");
		assertEquals(Translations.get("de_de", "vitrine.action.install"), "Installieren");
	}

	@Test
	public void translationsHelperFallsBackToDefaultLocaleWhenLocaleUnknown() {
		String fallback = Translations.get("xx_yy", "vitrine.store.title");
		assertEquals(fallback, Translations.get(Translations.FALLBACK_LOCALE, "vitrine.store.title"));
	}

	@Test
	public void translationsHelperReturnsKeyItselfWhenKeyIsTrulyMissing() {
		String missingKey = "vitrine.this.key.does.not.exist";
		assertEquals(Translations.get("de_de", missingKey), missingKey);
	}

	private static Map<String, String> readLangFile(String locale) throws IOException {
		String resource = "/assets/vitrine/lang/" + locale + ".json";
		try (InputStream in = LangFilesTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "Ressource '" + resource + "' nicht auf dem Testklassenpfad gefunden");
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return GSON.fromJson(reader, MAP_TYPE);
			}
		} catch (JsonSyntaxException e) {
			throw new AssertionError(resource + " ist kein gueltiges JSON: " + e.getMessage(), e);
		}
	}
}
