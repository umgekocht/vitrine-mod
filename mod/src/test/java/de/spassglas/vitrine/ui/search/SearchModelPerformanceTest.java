package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Fertig-Kriterium von ROADMAP S6: „Suche ueber 500 Testeintraege bleibt
 * fluessig (Messwert im Log)."
 *
 * <p>Baut 500 {@link TileData}-Testeintraege, laesst {@link SearchBenchmark}
 * eine Reihe Suchen darueber laufen und prueft (a) es entsteht genau eine
 * Messwert-Zeile, die die Kataloggroesse 500 nennt, und (b) ein grosszuegiges
 * Zeitbudget wird eingehalten. Ein zweiter Test beweist, dass der Messwert im
 * Auslieferungspfad tatsaechlich ueber den {@code Vitrine/Search}-Logger
 * geht, nicht nur ueber einen Test-Sink.</p>
 */
public final class SearchModelPerformanceTest {

	private static final int CATALOG_SIZE = 500;

	/** Grosszuegig: die Freitext-Suche ist reine String-Arithmetik, real weit darunter. */
	private static final double MAX_PER_SEARCH_MILLIS = 25.0;
	private static final double MAX_TOTAL_MILLIS = 2_000.0;

	private static final List<String> QUERIES = List.of(
			"pack", "the", "dark", "hud", "a", "blue", "stone", "theme",
			"light", "mira", "07", "zzz-nothing", "aur", "sound", "ui", "x",
			"green", "retro", "hd", "glass");

	private static List<TileData> buildCatalog() {
		TilePackageType[] types = TilePackageType.values();
		TileStatus[] statuses = TileStatus.values();
		String[] adjectives = {"Dark", "Bright", "Retro", "Smooth", "Crisp", "Blue", "Green", "Pastel", "Golden", "Aurora"};
		String[] nouns = {"Pack", "Theme", "HUD", "Set", "Blocks", "UI", "Sounds", "Skies", "Glass", "Kit"};
		String[] authors = {"mira", "wolf", "lena", "kai", "spassglas", "bee", "ora", "zed", "nova", "finn"};

		List<TileData> catalog = new ArrayList<>(CATALOG_SIZE);
		for (int i = 0; i < CATALOG_SIZE; i++) {
			String name = adjectives[i % adjectives.length] + " " + nouns[(i / 3) % nouns.length] + " " + i;
			String author = authors[(i / 2) % authors.length];
			TilePackageType type = types[i % types.length];
			TileStatus status = statuses[i % statuses.length];
			// GATE16: "Neustart noetig" gibt es nur fuer Klasse-B-Angebote.
			catalog.add(status == TileStatus.RESTART_REQUIRED
					? TileData.javaModOffer("perf.entry." + i, name, author, type, status)
					: TileData.withoutImage("perf.entry." + i, name, author, type, status));
		}
		return catalog;
	}

	@Test
	public void searchOverFiveHundredEntriesStaysFluidAndIsMeasured() {
		List<TileData> catalog = buildCatalog();
		assertEquals(catalog.size(), CATALOG_SIZE);

		List<String> logged = new ArrayList<>();
		SearchBenchmarkResult result = SearchBenchmark.measure(catalog, QUERIES, logged::add);

		// (a) genau eine Messwert-Zeile, nennt die Kataloggroesse 500
		assertEquals(logged.size(), 1, "genau eine Messwert-Zeile");
		assertEquals(logged.get(0), result.toLogLine(), "geloggte Zeile == SearchBenchmarkResult.toLogLine()");
		assertTrue(logged.get(0).contains(String.valueOf(CATALOG_SIZE)),
				"Messwert-Zeile muss die Kataloggroesse 500 enthalten: " + logged.get(0));
		assertEquals(result.catalogSize(), CATALOG_SIZE);
		assertEquals(result.searchCount(), QUERIES.size());

		// (b) grosszuegiges Zeitbudget -> „fluessig"
		assertTrue(result.perSearchMillis() < MAX_PER_SEARCH_MILLIS,
				"je Suche " + result.perSearchMillis() + " ms, Budget " + MAX_PER_SEARCH_MILLIS + " ms");
		assertTrue(result.totalMillis() < MAX_TOTAL_MILLIS,
				"gesamt " + result.totalMillis() + " ms, Budget " + MAX_TOTAL_MILLIS + " ms");

		Reporter.log("S6-Messwert: " + logged.get(0), true);
	}

	@Test
	public void productionMeasureEmitsTheLineThroughTheVitrineSearchLogger() {
		List<TileData> catalog = buildCatalog();

		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		org.apache.logging.log4j.core.Logger logger = ctx.getLogger(SearchBenchmark.LOGGER_NAME);
		Level previous = logger.getLevel();

		List<String> captured = Collections.synchronizedList(new ArrayList<>());
		AbstractAppender appender = new AbstractAppender("VitrineSearchPerfCapture", null, null, true,
				Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				captured.add(event.getMessage().getFormattedMessage());
			}
		};
		appender.start();
		logger.addAppender(appender);
		Configurator.setLevel(SearchBenchmark.LOGGER_NAME, Level.INFO);
		try {
			// Die Zwei-Argument-Ueberladung ist der Auslieferungspfad -- sie
			// schreibt ueber LoggerFactory.getLogger("Vitrine/Search").
			SearchBenchmark.measure(catalog, QUERIES);
		} finally {
			logger.removeAppender(appender);
			appender.stop();
			Configurator.setLevel(SearchBenchmark.LOGGER_NAME, previous == null ? Level.ERROR : previous);
		}

		assertEquals(captured.size(), 1, "genau eine Messwert-Zeile ueber den echten Logger");
		assertTrue(captured.get(0).startsWith("Suchleistung:"), captured.get(0));
		assertTrue(captured.get(0).contains(String.valueOf(CATALOG_SIZE)), captured.get(0));
	}
}
