package de.spassglas.vitrine.state;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * GATE41: {@code state.json} wurde ueberall als "laden, aendern, speichern"
 * ohne Sperre fortgeschrieben. Bei 40 gleichzeitigen Installationen
 * ueberlebte genau eine -- die anderen ueberschrieben einander mit ihrem
 * jeweils veralteten Stand. {@link PackStateStore#update} haelt den ganzen
 * Zug unter einer Sperre je Ordner.
 *
 * <p>Alle Schreiber warten an einem gemeinsamen Startsignal, damit sie
 * wirklich gleichzeitig losrennen -- sonst wuerde der Test auch ohne Sperre
 * zufaellig gruen.</p>
 */
public class PackStateStoreConcurrencyTest {

	private static final int WRITERS = 40;

	@Test
	public void vierzigGleichzeitigeInstallationenGehenAlleNichtVerloren() throws Exception {
		Path dir = Files.createTempDirectory("vitrine-state-gate41");
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < WRITERS; i++) {
			ids.add("vitrine/test.paket" + i);
		}

		runConcurrently(ids, id -> PackStateStore.update(dir, state -> {
			state.addOrUpdate(id, true);
			return null;
		}));

		PackState result = PackStateStore.load(dir);
		assertEquals(result.packs.size(), WRITERS,
				"jede der " + WRITERS + " gleichzeitigen Installationen muss in state.json stehen");
		for (String id : ids) {
			assertTrue(result.isInstalled(id), "verloren gegangen: " + id);
		}
	}

	@Test
	public void gleichzeitigesInstallierenUndDeinstallierenBleibtKonsistent() throws Exception {
		Path dir = Files.createTempDirectory("vitrine-state-gate41-mixed");
		List<String> alt = new ArrayList<>();
		List<String> neu = new ArrayList<>();
		for (int i = 0; i < WRITERS / 2; i++) {
			alt.add("vitrine/alt" + i);
			neu.add("vitrine/neu" + i);
		}
		PackStateStore.update(dir, state -> {
			alt.forEach(id -> state.addOrUpdate(id, true));
			return null;
		});

		List<String> alle = new ArrayList<>(alt);
		alle.addAll(neu);
		runConcurrently(alle, id -> PackStateStore.update(dir, state -> {
			if (id.contains("/alt")) {
				state.remove(id);
			} else {
				state.addOrUpdate(id, true);
			}
			return null;
		}));

		PackState result = PackStateStore.load(dir);
		for (String id : alt) {
			assertTrue(!result.isInstalled(id), "Deinstallation verloren gegangen: " + id);
		}
		for (String id : neu) {
			assertTrue(result.isInstalled(id), "Installation verloren gegangen: " + id);
		}
		assertEquals(result.packs.size(), neu.size());
	}

	@FunctionalInterface
	private interface Writer {
		void write(String id) throws Exception;
	}

	private static void runConcurrently(List<String> ids, Writer writer) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(ids.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (String id : ids) {
				futures.add(pool.submit(() -> {
					start.await();
					writer.write(id);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> f : futures) {
				f.get(30, TimeUnit.SECONDS); // wirft, wenn ein Schreiber scheiterte
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
