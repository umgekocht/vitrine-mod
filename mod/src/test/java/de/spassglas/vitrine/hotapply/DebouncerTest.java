package de.spassglas.vitrine.hotapply;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Deckt {@link Debouncer} direkt ab (ROADMAP H2: "200-ms-Debounce fuer
 * Mehrfachinstallationen") -- ueber {@link FakeDelayedTaskScheduler}, die nie
 * wirklich wartet: ein Test steuert selbst, wann ein noch nicht
 * abgebrochener geplanter Lauf tatsaechlich ausgefuehrt wird.
 */
public final class DebouncerTest {

	@Test
	public void singleTriggerRunsExactlyOnceWhenFlushed() {
		FakeDelayedTaskScheduler scheduler = new FakeDelayedTaskScheduler();
		Debouncer debouncer = new Debouncer(200, scheduler);
		AtomicInteger runs = new AtomicInteger();

		debouncer.trigger(runs::incrementAndGet);
		scheduler.runAllPending();

		assertEquals(runs.get(), 1, "eine einzelne Aktivierung muss genau einen Lauf ergeben");
	}

	@Test
	public void everyTriggerSchedulesWithTheConfiguredDelay() {
		FakeDelayedTaskScheduler scheduler = new FakeDelayedTaskScheduler();
		Debouncer debouncer = new Debouncer(200, scheduler);

		debouncer.trigger(() -> {
		});

		assertEquals(scheduler.lastScheduledDelayMillis(), 200, "Debouncer muss mit der konfigurierten Verzoegerung planen");
	}

	@Test
	public void secondTriggerBeforeFlushCancelsThePreviouslyScheduledRun() {
		FakeDelayedTaskScheduler scheduler = new FakeDelayedTaskScheduler();
		Debouncer debouncer = new Debouncer(200, scheduler);
		AtomicInteger firstRuns = new AtomicInteger();
		AtomicInteger secondRuns = new AtomicInteger();

		debouncer.trigger(firstRuns::incrementAndGet);
		debouncer.trigger(secondRuns::incrementAndGet);
		scheduler.runAllPending();

		assertEquals(firstRuns.get(), 0,
				"der zuerst geplante Lauf haette durch den zweiten Trigger abgebrochen werden muessen");
		assertEquals(secondRuns.get(), 1, "der zuletzt geplante Lauf muss laufen");
		assertEquals(scheduler.pendingCount(), 0, "nach dem Flush darf kein Lauf mehr ausstehen");
	}

	@Test
	public void threeTriggersWithinDebounceWindowResultInExactlyOneRun() {
		FakeDelayedTaskScheduler scheduler = new FakeDelayedTaskScheduler();
		Debouncer debouncer = new Debouncer(200, scheduler);
		AtomicInteger runs = new AtomicInteger();
		Runnable action = runs::incrementAndGet;

		debouncer.trigger(action);
		debouncer.trigger(action);
		debouncer.trigger(action);
		scheduler.runAllPending();

		assertEquals(runs.get(), 1, "drei Trigger kurz hintereinander duerfen nur einen Lauf ergeben, nicht drei");
	}

	@Test
	public void triggerAfterAFlushSchedulesANewIndependentRun() {
		FakeDelayedTaskScheduler scheduler = new FakeDelayedTaskScheduler();
		Debouncer debouncer = new Debouncer(200, scheduler);
		AtomicInteger runs = new AtomicInteger();
		Runnable action = runs::incrementAndGet;

		debouncer.trigger(action);
		scheduler.runAllPending();
		debouncer.trigger(action);
		scheduler.runAllPending();

		assertEquals(runs.get(), 2, "nach einem abgeschlossenen Lauf muss ein neuer Trigger wieder laufen");
	}
}
