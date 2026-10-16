package de.spassglas.vitrine.ui.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das Fertig-Kriterium von ROADMAP T2 ("Theme-Wechsel faerbt den
 * offenen Store sofort um"), soweit ohne laufenden Client pruefbar:
 * <ul>
 *   <li>(a) das Standard-Theme loest jeden Schema-Pflicht-Token auf;</li>
 *   <li>(b) ein Theme mit genau einem fehlenden Token nimmt diesen einen aus
 *       dem Standard, den Rest aus dem geladenen Theme;</li>
 *   <li>(c) ein kaputtes Theme (unparsebar / Schema-ungueltig) laesst das
 *       aktive Theme unveraendert und erzeugt eine Klartext-Meldung
 *       (Fake-Presenter zaehlt);</li>
 *   <li>(d) das woertliche Kriterium: ein registrierter Zuhoerer wird bei
 *       {@code switchTo}/{@code loadAndSwitch} genau einmal benachrichtigt und
 *       sieht danach die neuen Token-Werte -- headless-Stellvertreter fuer
 *       "der offene Store faerbt sofort um".</li>
 * </ul>
 */
public final class ThemeManagerTest {

	private RecordingPresenter presenter;
	private ThemeManager manager;

	@BeforeMethod
	public void setUp() {
		presenter = new RecordingPresenter();
		manager = new ThemeManager(DefaultThemeTokens.INSTANCE, presenter);
	}

	// --- (a) Standard-Theme loest jeden Schema-Pflicht-Token auf ------------

	@Test
	public void defaultThemeResolvesEverySchemaRequiredToken() {
		Set<String> required = schemaRequiredVariantTokens();
		Set<String> colorTokens = schemaColorTokens();

		Set<String> covered = new LinkedHashSet<>(DefaultThemeTokens.INSTANCE.sizeTokens());
		covered.addAll(DefaultThemeTokens.INSTANCE.colorTokens());
		assertEquals(covered, required,
				"Standard-Theme (DefaultThemeTokens) und theme.schema.json required-Liste sind auseinandergelaufen");

		for (String token : required) {
			if (colorTokens.contains(token)) {
				int argb = manager.color(token);
				assertNotEquals(argb & 0xFF000000, 0,
						"Farb-Token '" + token + "' hat ein Alpha von 0 -- unsichtbar, kann kein Default sein");
			} else {
				// Masz-Tokens: nur sicherstellen, dass kein Zugriff wirft.
				manager.size(token);
			}
		}
	}

	@Test
	public void unknownTokenFailsLoudInsteadOfReturningZero() {
		assertThrows(IllegalArgumentException.class, () -> manager.size("does.not.exist"));
		assertThrows(IllegalArgumentException.class, () -> manager.color("also.not.a.color"));
	}

	// --- (b) ein fehlender Token -> Default, Rest aus dem geladenen Theme ---

	@Test
	public void singleMissingTokenFallsBackToDefaultWhileRestComesFromLoadedTheme() {
		JsonObject theme = readKlarglas();
		// klarglas.dark: elevation.hover = 14 (Default aus VitrineTheme = 8) --
		// ein Token, an dem sich "kommt aus dem geladenen Theme" zeigen laesst.
		int loadedElevationHover = theme.getAsJsonObject("dark").get("elevation.hover").getAsInt();
		assertNotEquals(loadedElevationHover, VitrineTheme.ELEVATION_HOVER,
				"Testvoraussetzung: klarglas.dark.elevation.hover muss sich vom Default unterscheiden");

		theme.getAsJsonObject("dark").remove("color.accent");

		boolean ok = manager.loadAndSwitch(theme.toString(), "fehlt-ein-token");
		assertTrue(ok, "ein Theme mit nur einem fehlenden Token ist nicht 'kaputt' -- es muss laden");
		assertTrue(presenter.messages.isEmpty(), "kein Fehler -> keine 'kaputt'-Meldung");

		assertEquals(manager.color("color.accent"), VitrineTheme.COLOR_ACCENT,
				"der eine fehlende Token muss aus dem Standard-Theme kommen");
		assertEquals(manager.size("elevation.hover"), loadedElevationHover,
				"alle anderen Tokens muessen aus dem geladenen Theme kommen, nicht aus dem Default");
	}

	// --- (c) kaputtes Theme -> aktives bleibt Standard + Meldung -----------

	@Test
	public void unparseableThemeKeepsCurrentThemeAndEmitsExactlyOneMessage() {
		int accentBefore = manager.color("color.accent");

		boolean ok = manager.loadAndSwitch("{ das ist kein JSON ", "kaputt-json");

		assertFalse(ok);
		assertEquals(manager.color("color.accent"), accentBefore, "aktives Theme muss unveraendert bleiben");
		assertEquals(presenter.messages.size(), 1, "genau eine Klartext-Meldung");
		assertEquals(presenter.messages.get(0).themeName(), "kaputt-json");
		assertFalse(presenter.messages.get(0).reason().isBlank(), "die Meldung braucht einen Klartext-Grund");
	}

	@Test
	public void schemaInvalidThemeKeepsCurrentThemeAndEmitsExactlyOneMessage() {
		JsonObject theme = readKlarglas();
		theme.remove("dark"); // Pflichtvariante entfernt -> Schema-Verstosz, aber gueltiges JSON

		int bgBefore = manager.color("color.bg");
		boolean ok = manager.loadAndSwitch(theme.toString(), "ohne-dark");

		assertFalse(ok);
		assertEquals(manager.color("color.bg"), bgBefore);
		assertEquals(presenter.messages.size(), 1);
		assertEquals(presenter.messages.get(0).themeName(), "ohne-dark");
	}

	@Test
	public void brokenThemeDoesNotNotifyRepaintListeners() {
		AtomicInteger hits = new AtomicInteger();
		manager.addRepaintListener(hits::incrementAndGet);

		manager.loadAndSwitch("nicht mal ansatzweise JSON", "kaputt");

		assertEquals(hits.get(), 0, "ein abgelehntes Theme darf keinen Umfaerb-Rueckruf ausloesen");
	}

	// --- (d) das woertliche Kriterium: Zuhoerer + neue Werte ---------------

	@Test
	public void registeredListenerIsNotifiedExactlyOnceAndSeesNewTokenValues() {
		AtomicInteger hits = new AtomicInteger();
		List<Integer> accentSeenByListener = new ArrayList<>();
		manager.addRepaintListener(() -> {
			hits.incrementAndGet();
			accentSeenByListener.add(manager.color("color.accent"));
		});

		JsonObject theme = readKlarglas();
		theme.getAsJsonObject("dark").addProperty("color.accent", "#ff0000");

		boolean ok = manager.loadAndSwitch(theme.toString(), "rot-akzent");

		assertTrue(ok);
		assertEquals(hits.get(), 1, "genau einmal benachrichtigt");
		assertEquals(accentSeenByListener, List.of(0xFFFF0000),
				"der Zuhoerer sieht beim Rueckruf bereits den neuen Token-Wert -> sofortige Umfaerbung");
		assertEquals(manager.color("color.accent"), 0xFFFF0000);
	}

	@Test
	public void switchingAgainNotifiesAgainAndUnregisterStopsNotifications() {
		AtomicInteger hits = new AtomicInteger();
		Runnable unregister = manager.addRepaintListener(hits::incrementAndGet);

		manager.resetToDefault();
		manager.resetToDefault();
		assertEquals(hits.get(), 2, "jeder Wechsel benachrichtigt erneut");

		unregister.run();
		manager.resetToDefault();
		assertEquals(hits.get(), 2, "nach dem Abmelden keine Rueckrufe mehr");
	}

	@Test
	public void colorAlphaTokenIsResolvedToPackedArgb() {
		JsonObject theme = readKlarglas();
		JsonObject scrim = new JsonObject();
		scrim.addProperty("hex", "#010203");
		scrim.addProperty("alpha", 0.5);
		theme.getAsJsonObject("dark").add("color.overlayScrim", scrim);

		assertTrue(manager.loadAndSwitch(theme.toString(), "scrim-test"));

		int argb = manager.color("color.overlayScrim");
		assertEquals((argb >>> 24), Math.round(0.5f * 255), "Alpha-Byte aus dem alpha-Feld");
		assertEquals(argb & 0xFFFFFF, 0x010203, "RGB aus dem hex-Feld");
	}

	// --- Helfer -----------------------------------------------------------

	private record PresenterCall(String themeName, String reason) {
	}

	private static final class RecordingPresenter implements ThemeMessagePresenter {
		private final List<PresenterCall> messages = new ArrayList<>();

		@Override
		public void showBrokenThemeKeptCurrent(String themeName, String reason) {
			messages.add(new PresenterCall(themeName, reason));
		}
	}

	private static JsonObject embeddedSchema() {
		try (InputStream in = ThemeManagerTest.class.getResourceAsStream(
				"/de/spassglas/vitrine/ui/theme/theme.schema.json")) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete theme.schema.json nicht auf dem Testklassenpfad");
			}
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Konnte eingebettete theme.schema.json nicht lesen", e);
		}
	}

	private static Set<String> schemaRequiredVariantTokens() {
		Set<String> tokens = new LinkedHashSet<>();
		embeddedSchema().getAsJsonObject("$defs").getAsJsonObject("variant").getAsJsonArray("required")
				.forEach(e -> tokens.add(e.getAsString()));
		return tokens;
	}

	/** Tokens, deren $ref auf color / colorAlpha zeigt. */
	private static Set<String> schemaColorTokens() {
		JsonObject props = embeddedSchema().getAsJsonObject("$defs").getAsJsonObject("variant")
				.getAsJsonObject("properties");
		Set<String> colors = new LinkedHashSet<>();
		for (String token : props.keySet()) {
			String ref = props.getAsJsonObject(token).get("$ref").getAsString();
			if (ref.equals("#/$defs/color") || ref.equals("#/$defs/colorAlpha")) {
				colors.add(token);
			}
		}
		return colors;
	}

	private static JsonObject readKlarglas() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "examples", "theme", "valid", "klarglas.json");
		Path viaRepoRoot = Path.of("registry", "schema", "examples", "theme", "valid", "klarglas.json");
		Path source = Files.exists(viaModuleWorkingDir) ? viaModuleWorkingDir
				: Files.exists(viaRepoRoot) ? viaRepoRoot : null;
		if (source == null) {
			throw new IllegalStateException("klarglas.json nicht gefunden, Arbeitsverzeichnis: "
					+ Path.of("").toAbsolutePath());
		}
		try {
			return JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Konnte klarglas.json nicht lesen", e);
		}
	}
}
