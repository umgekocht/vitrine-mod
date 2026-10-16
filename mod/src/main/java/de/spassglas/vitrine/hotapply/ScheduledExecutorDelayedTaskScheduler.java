package de.spassglas.vitrine.hotapply;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Echte Umsetzung von {@link DelayedTaskScheduler} ueber einen
 * {@link ScheduledExecutorService} mit genau einem Hintergrund-Thread --
 * ausreichend, weil {@link Debouncer} ohnehin immer nur einen ausstehenden
 * Lauf gleichzeitig braucht (ein neuer Aufruf ersetzt den alten).
 */
public final class ScheduledExecutorDelayedTaskScheduler implements DelayedTaskScheduler {

	private final ScheduledExecutorService executor;

	public ScheduledExecutorDelayedTaskScheduler(ScheduledExecutorService executor) {
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	/**
	 * Eigener Daemon-Thread ("Vitrine-HotApply-Debounce"): ein noch
	 * ausstehender Debounce darf den Prozess nicht am Beenden hindern (kein
	 * Non-Daemon-Thread, der die JVM offenhaelt).
	 */
	public static ScheduledExecutorDelayedTaskScheduler daemon() {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Vitrine-HotApply-Debounce");
			thread.setDaemon(true);
			return thread;
		});
		return new ScheduledExecutorDelayedTaskScheduler(executor);
	}

	@Override
	public Handle scheduleOnce(Runnable task, long delayMillis) {
		ScheduledFuture<?> future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
		return () -> future.cancel(false);
	}
}
