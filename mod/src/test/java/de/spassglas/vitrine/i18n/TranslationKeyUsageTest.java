package de.spassglas.vitrine.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE68: {@link LangFilesTest} prueft bisher nur, dass {@code
 * en_us.json} und {@code de_de.json} <i>untereinander</i> dieselbe
 * Schluesselmenge tragen -- nie, ob ein im Quellcode per {@code
 * Text.translatable("vitrine...."} tatsaechlich benutzter Schluessel in
 * beiden Sprachdateien <i>existiert</i>. Genau diese Luecke hat GATE66
 * verursacht: ein Vorlauf hat 27 neue Schluessel im Code verdrahtet, ohne sie
 * je in die Sprachdateien einzutragen -- kein Test ist deswegen rot
 * gegangen.
 *
 * <p>Dieser Test scannt {@code mod/src/main/java} nach woertlichen
 * String-Literalen der Form {@code "vitrine.<segment>.<segment>..."} (mit
 * mindestens zwei Segmenten nach {@code vitrine}, so wie jeder echte
 * Uebersetzungs-Key aufgebaut ist) -- unabhaengig davon, ob das Literal
 * direkt in einem {@code Text.translatable(...)}-Aufruf steht oder (wie bei
 * {@link de.spassglas.vitrine.ui.classb.RestartRequiredBanner}) erst in einer
 * Konstante landet, die {@code Text.translatable(...)} spaeter liest -- und
 * beweist, dass jeder gefundene Schluessel in <b>beiden</b> Sprachdateien
 * steht. Dateinamen-Literale, die zufaellig wie ein Key aussehen (z. B.
 * {@code "vitrine.json"}, der Manifest-Dateiname in {@link
 * de.spassglas.vitrine.vpkg.VpkgArchive}), enden auf {@code .json} und werden
 * deshalb bewusst ausgenommen -- kein echter Uebersetzungs-Key endet auf
 * dieses Segment.</p>
 */
public final class TranslationKeyUsageTest {

	private static final String[] LOCALES = {"en_us", "de_de"};
	private static final Gson GSON = new Gson();
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
	}.getType();

	/**
	 * Mindestens zwei Segmente nach {@code vitrine} (z. B. {@code
	 * vitrine.pack.source}), damit dynamisch zusammengesetzte Praefixe wie
	 * {@code "vitrine.category." + id} (volles Literal endet auf einen
	 * unvollstaendigen, abschliessenden Punkt) nicht faelschlich als
	 * vollstaendiger Schluessel durchgehen -- {@link Matcher#matches()} prueft
	 * das gesamte Literal, nicht nur einen Teil davon.
	 */
	private static final Pattern FULL_KEY_LITERAL = Pattern.compile("vitrine(?:\\.[A-Za-z0-9_]+){2,}");
	private static final Pattern QUOTED_LITERAL = Pattern.compile("\"(vitrine(?:\\.[A-Za-z0-9_]+){2,})\"");

	@Test
	public void everyTranslationKeyLiteralUsedInMainSourceExistsInBothLocales() throws IOException {
		Path javaRoot = resolveMainJavaRoot();

		TreeSet<String> usedKeys = new TreeSet<>();
		try (Stream<Path> files = Files.walk(javaRoot)) {
			files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
				try {
					String source = Files.readString(file, StandardCharsets.UTF_8);
					Matcher matcher = QUOTED_LITERAL.matcher(source);
					while (matcher.find()) {
						String candidate = matcher.group(1);
						// Dateinamen wie "vitrine.json"/"vitrine.schema.json" sehen wie ein
						// Key aus, sind aber keiner -- kein echter Uebersetzungs-Key endet
						// auf das Segment "json".
						if (FULL_KEY_LITERAL.matcher(candidate).matches() && !candidate.endsWith(".json")) {
							usedKeys.add(candidate);
						}
					}
				} catch (IOException e) {
					throw new UncheckedIOException("Konnte '" + file.getFileName() + "' nicht lesen", e);
				}
			});
		}

		assertTrue(usedKeys.size() > 50,
				"Scanner muss den real gewachsenen UI-Code finden -- nur " + usedKeys.size()
						+ " Keys gefunden, das wirkt wie ein kaputter Scan statt wie ein echter Baum");

		for (String locale : LOCALES) {
			Map<String, String> lang = readLangFile(locale);
			TreeSet<String> missing = new TreeSet<>();
			for (String key : usedKeys) {
				if (!lang.containsKey(key)) {
					missing.add(key);
				}
			}
			assertTrue(missing.isEmpty(),
					locale + ".json: " + missing.size() + " im Code benutzte Uebersetzungs-Key(s) fehlen: "
							+ missing);
		}
	}

	/**
	 * Vorbedingung des eigentlichen Tests: ein neu im Code auftauchender Key
	 * ohne Eintrag in einer der beiden Sprachdateien muss diesen Test rot
	 * machen. Konstruiert dafuer einen eigenen, garantiert nicht
	 * eingetragenen Schluessel und laesst ihn durch denselben Abgleich laufen
	 * wie {@link #everyTranslationKeyLiteralUsedInMainSourceExistsInBothLocales()}.
	 */
	@Test
	public void missingKeyWouldFailTheSameCheck() throws IOException {
		String neverEnteredKey = "vitrine.this.key.is.deliberately.not.in.any.lang.file";
		assertTrue(FULL_KEY_LITERAL.matcher(neverEnteredKey).matches(),
				"Testvoraussetzung: das konstruierte Beispiel muss wie ein echter Key aussehen");

		for (String locale : LOCALES) {
			assertTrue(!readLangFile(locale).containsKey(neverEnteredKey),
					locale + ".json enthaelt unerwartet den extra fuer diesen Test erfundenen Schluessel");
		}
	}

	private static Path resolveMainJavaRoot() {
		Path direct = Path.of("src", "main", "java");
		if (Files.exists(direct)) {
			return direct;
		}
		Path viaModuleRoot = Path.of("mod", "src", "main", "java");
		if (Files.exists(viaModuleRoot)) {
			return viaModuleRoot;
		}
		throw new IllegalStateException(
				"Konnte src/main/java nicht finden, Arbeitsverzeichnis: " + Path.of("").toAbsolutePath());
	}

	private static Map<String, String> readLangFile(String locale) throws IOException {
		String resource = "/assets/vitrine/lang/" + locale + ".json";
		try (InputStream in = TranslationKeyUsageTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "Ressource '" + resource + "' nicht auf dem Testklassenpfad gefunden");
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return GSON.fromJson(reader, MAP_TYPE);
			}
		}
	}
}
