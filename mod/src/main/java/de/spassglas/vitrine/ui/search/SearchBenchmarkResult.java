package de.spassglas.vitrine.ui.search;

import java.util.Locale;

/**
 * Messwert eines {@link SearchBenchmark}-Laufs (ROADMAP S6 „Fertig": „Suche
 * ueber 500 Testeintraege bleibt fluessig (Messwert im Log)"). {@link #toLogLine()}
 * ist genau der Klartext, den {@link SearchBenchmark} in den
 * {@code Vitrine/Search}-Logger schreibt.
 */
public record SearchBenchmarkResult(int catalogSize, int searchCount, double totalMillis, double perSearchMillis,
		int lastResultCount) {

	/** Menschlesbarer Messwert -- identisch mit der geloggten Zeile. */
	public String toLogLine() {
		return String.format(Locale.ROOT,
				"Suchleistung: %d Suchlaeufe ueber %d Katalogeintraege in %.2f ms gesamt, "
						+ "%.3f ms je Suche (letzter Lauf: %d Treffer)",
				searchCount, catalogSize, totalMillis, perSearchMillis, lastResultCount);
	}
}
