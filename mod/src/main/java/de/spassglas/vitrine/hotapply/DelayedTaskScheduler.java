package de.spassglas.vitrine.hotapply;

/**
 * Schmale Abstraktion ueber "fuehre etwas in X Millisekunden aus, sofern es
 * nicht vorher abgebrochen wird" -- der einzige Zeit-Baustein, den {@link
 * Debouncer} braucht (ROADMAP H2, "200-ms-Debounce fuer
 * Mehrfachinstallationen").
 *
 * <p>Bewusst kein direkter Griff zu {@code java.util.concurrent.ScheduledExecutorService}
 * im {@link Debouncer} selbst: diese Schnittstelle ist so schmal, dass ein
 * Test sie durch eine reine Aufzeichnungs-Attrappe ersetzen kann, die
 * <em>nicht</em> wirklich wartet, sondern den geplanten {@link Runnable} und
 * die Verzoegerung einfach festhaelt -- damit laesst sich die eigentliche
 * Debounce-<em>Logik</em> (alter Plan abbrechen, neuer Plan ersetzt ihn)
 * deterministisch und ohne echtes Schlafen pruefen. Fuer echtes Zeitverhalten
 * gibt es zusaetzlich {@link ScheduledExecutorDelayedTaskScheduler}, der
 * dieselbe Schnittstelle mit einem echten Hintergrund-Thread bedient.</p>
 */
public interface DelayedTaskScheduler {

	/** Griff auf einen einzelnen geplanten Lauf. */
	interface Handle {
		/**
		 * Verhindert die Ausfuehrung, falls sie noch nicht begonnen hat.
		 * Wirkungslos (kein Fehler), wenn der Lauf schon gestartet ist oder
		 * bereits abgebrochen wurde.
		 */
		void cancel();
	}

	/** Plant {@code task}, in {@code delayMillis} Millisekunden genau einmal zu laufen. */
	Handle scheduleOnce(Runnable task, long delayMillis);
}
