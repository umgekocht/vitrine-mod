package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fuehrt eine Reihe Suchen ueber einen gegebenen Katalog aus, misst die
 * Gesamtdauer und schreibt den Messwert als Klartext in den
 * {@code Vitrine/Search}-Logger (ROADMAP S6 „Fertig"-Kriterium: „Suche ueber
 * 500 Testeintraege bleibt fluessig (Messwert im Log)").
 *
 * <p>Der eigentliche 500-Eintrag-Nachweis liegt im Test
 * {@code SearchModelPerformanceTest}: er baut die 500 Eintraege, ruft diese
 * Messung auf und prueft sowohl ein grosszuegiges Zeitbudget als auch, dass
 * der Messwert erzeugt und ausgegeben wurde. Der Auslieferungscode enthaelt
 * keine 500 fest verdrahteten Eintraege.</p>
 */
public final class SearchBenchmark {

	/** Name des Loggers, ueber den der Messwert im Auslieferungspfad geht (ROADMAP S6 „Messwert im Log"). */
	public static final String LOGGER_NAME = "Vitrine/Search";

	private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

	/** Nicht gemessene Vorlaeufe, damit die Messung nicht die JIT-Aufwaermphase mitzaehlt. */
	private static final int WARMUP_ROUNDS = 3;

	private SearchBenchmark() {
	}

	/** Misst und loggt ueber den {@code Vitrine/Search}-Logger. */
	public static SearchBenchmarkResult measure(List<TileData> catalog, List<String> queryTexts) {
		return measure(catalog, queryTexts, line -> LOGGER.info("{}", line));
	}

	/**
	 * Wie {@link #measure(List, List)}, gibt den Messwert aber zusaetzlich an
	 * {@code logSink} weiter -- so kann ein Test die geloggte Zeile abfangen,
	 * ohne auf eine bestimmte SLF4J-Bindung angewiesen zu sein.
	 */
	public static SearchBenchmarkResult measure(List<TileData> catalog, List<String> queryTexts,
			Consumer<String> logSink) {
		Objects.requireNonNull(catalog, "catalog");
		Objects.requireNonNull(queryTexts, "queryTexts");
		Objects.requireNonNull(logSink, "logSink");
		if (queryTexts.isEmpty()) {
			throw new IllegalArgumentException("mindestens eine Suchanfrage noetig");
		}

		SearchModel model = new SearchModel(catalog);
		SearchSort[] sorts = SearchSort.values();

		for (int round = 0; round < WARMUP_ROUNDS; round++) {
			for (String text : queryTexts) {
				model.search(new SearchQuery(text, Set.of(), false, sorts[0]));
			}
		}

		int lastResultCount = 0;
		long startNanos = System.nanoTime();
		for (int i = 0; i < queryTexts.size(); i++) {
			// Jede Anfrage mit anderer Sortierung und wechselndem „ohne
			// Neustart"-Filter, damit die Messung den ganzen Pfad trifft.
			SearchQuery query = new SearchQuery(queryTexts.get(i), Set.of(), i % 2 == 0, sorts[i % sorts.length]);
			lastResultCount = model.search(query).size();
		}
		long elapsedNanos = System.nanoTime() - startNanos;

		double totalMillis = elapsedNanos / 1_000_000.0;
		double perSearchMillis = totalMillis / queryTexts.size();
		SearchBenchmarkResult result = new SearchBenchmarkResult(catalog.size(), queryTexts.size(), totalMillis,
				perSearchMillis, lastResultCount);
		logSink.accept(result.toLogLine());
		return result;
	}
}
