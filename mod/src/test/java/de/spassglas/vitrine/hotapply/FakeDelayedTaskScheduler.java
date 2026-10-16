package de.spassglas.vitrine.hotapply;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Aufzeichnungs-Attrappe fuer {@link DelayedTaskScheduler} (siehe dessen
 * Klassendoku): haelt jeden geplanten Lauf fest, wartet aber nie wirklich --
 * ein Test entscheidet selbst, wann ein noch nicht abgebrochener Lauf
 * tatsaechlich ausgefuehrt wird ({@link #runAllPending()}), ohne echtes
 * Schlafen zu brauchen. Damit lassen sich {@link Debouncer} und {@link
 * HotApplyCoordinator} deterministisch pruefen, auch fuer "mehrere
 * Aktivierungen innerhalb der Debounce-Zeit" -- das entsprechende Verhalten
 * ergibt sich hier einfach daraus, dass zwischen mehreren {@code trigger}-
 * Aufrufen nie {@link #runAllPending()} aufgerufen wird.
 */
final class FakeDelayedTaskScheduler implements DelayedTaskScheduler {

	private record ScheduledTask(Runnable task, long delayMillis) {
	}

	private final List<ScheduledTask> scheduled = new ArrayList<>();

	@Override
	public Handle scheduleOnce(Runnable task, long delayMillis) {
		ScheduledTask entry = new ScheduledTask(task, delayMillis);
		scheduled.add(entry);
		return () -> scheduled.remove(entry);
	}

	/**
	 * Fuehrt jeden noch nicht abgebrochenen geplanten Lauf sofort aus, in
	 * Planungsreihenfolge, und leert die Liste danach.
	 */
	void runAllPending() {
		List<ScheduledTask> toRun = new ArrayList<>(scheduled);
		scheduled.clear();
		for (ScheduledTask entry : toRun) {
			entry.task().run();
		}
	}

	/** Anzahl noch nicht abgebrochener/ausgefuehrter geplanter Laeufe. */
	int pendingCount() {
		return scheduled.size();
	}

	/** Die Verzoegerung des zuletzt geplanten (noch ausstehenden) Laufs. */
	long lastScheduledDelayMillis() {
		if (scheduled.isEmpty()) {
			throw new IllegalStateException("kein Lauf geplant");
		}
		return scheduled.get(scheduled.size() - 1).delayMillis();
	}
}
