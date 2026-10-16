package de.spassglas.vitrine.hotapply;

import java.util.Objects;

/**
 * Allgemeiner Debounce-Baustein (ROADMAP H2: "200-ms-Debounce fuer
 * Mehrfachinstallationen"): jeder Aufruf von {@link #trigger} bricht einen
 * noch nicht gelaufenen, zuvor geplanten Lauf ab und plant einen neuen --
 * {@code action} laeuft also erst, wenn fuer {@code delayMillis} kein
 * weiterer {@link #trigger}-Aufruf mehr kam ("Ruhe"), egal wie oft
 * {@link #trigger} vorher kam. Drei Installationen kurz hintereinander lesen
 * sich damit als drei {@link #trigger}-Aufrufe, ergeben aber genau einen
 * Lauf von {@code action}.
 *
 * <p>Kennt absichtlich keine Minecraft-Typen -- reine Zeitsteuerung, ueber
 * {@link DelayedTaskScheduler} von der eigentlichen Uhr entkoppelt, damit
 * Tests die Debounce-<em>Logik</em> ohne echtes Warten pruefen koennen.</p>
 */
public final class Debouncer {

	private final long delayMillis;
	private final DelayedTaskScheduler scheduler;

	/** Nur unter {@code this} gelesen/geschrieben -- {@link #trigger} kann von mehreren Threads kommen (z. B. mehrere schnelle Installationen). */
	private DelayedTaskScheduler.Handle pending;

	public Debouncer(long delayMillis, DelayedTaskScheduler scheduler) {
		if (delayMillis < 0) {
			throw new IllegalArgumentException("delayMillis darf nicht negativ sein: " + delayMillis);
		}
		this.delayMillis = delayMillis;
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
	}

	/**
	 * Bricht einen noch ausstehenden Lauf ab (falls einer existiert) und
	 * plant {@code action} neu, {@link #delayMillis} Millisekunden ab jetzt.
	 */
	public synchronized void trigger(Runnable action) {
		Objects.requireNonNull(action, "action");
		if (pending != null) {
			pending.cancel();
		}
		pending = scheduler.scheduleOnce(action, delayMillis);
	}

	/** Die konfigurierte Ruhezeit in Millisekunden. */
	public long delayMillis() {
		return delayMillis;
	}
}
