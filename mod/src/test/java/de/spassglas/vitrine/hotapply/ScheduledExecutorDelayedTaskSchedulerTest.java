package de.spassglas.vitrine.hotapply;

import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt die echte Zeit-Umsetzung von {@link DelayedTaskScheduler} ab: ein
 * geplanter Lauf feuert tatsaechlich nach der angegebenen Verzoegerung, und
 * {@link DelayedTaskScheduler.Handle#cancel()} verhindert ihn zuverlaessig,
 * wenn er noch nicht begonnen hat. Bewusst kurze Verzoegerungen (wenige
 * Millisekunden) mit grosszuegigem Warte-Timeout, um auf einem ausgelasteten
 * Testrechner nicht zu flackern, ohne den Test spuerbar zu verlangsamen.
 */
public final class ScheduledExecutorDelayedTaskSchedulerTest {

	@Test
	public void scheduledTaskRunsAfterTheDelay() throws InterruptedException {
		ScheduledExecutorDelayedTaskScheduler scheduler = ScheduledExecutorDelayedTaskScheduler.daemon();
		CountDownLatch latch = new CountDownLatch(1);

		scheduler.scheduleOnce(latch::countDown, 10);

		assertTrue(latch.await(5, TimeUnit.SECONDS), "geplanter Lauf haette innerhalb des Timeouts feuern muessen");
	}

	@Test
	public void cancellingBeforeTheDelayElapsesPreventsExecution() throws InterruptedException {
		ScheduledExecutorDelayedTaskScheduler scheduler = ScheduledExecutorDelayedTaskScheduler.daemon();
		AtomicInteger runs = new AtomicInteger();

		DelayedTaskScheduler.Handle handle = scheduler.scheduleOnce(runs::incrementAndGet, 500);
		handle.cancel();

		// Grosszuegig warten, um sicherzugehen, dass der Lauf (bei einem
		// Bug) tatsaechlich Zeit gehabt haette zu feuern -- 500 ms geplante
		// Verzoegerung, 700 ms Wartezeit.
		Thread.sleep(700);

		assertEquals(runs.get(), 0, "ein abgebrochener Lauf darf nie ausgefuehrt werden");
	}
}
