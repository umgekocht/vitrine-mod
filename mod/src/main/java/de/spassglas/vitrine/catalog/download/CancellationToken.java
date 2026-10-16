package de.spassglas.vitrine.catalog.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Expliziter Abbruch-Mechanismus fuer einen laufenden Download (ROADMAP K3) --
 * kein {@code Thread.interrupt}-Ratespiel. Der Aufrufer (spaeter die
 * Download-Anzeige aus S8, verdrahtet in K4) haelt das Token und ruft
 * {@link #cancel()}; der Downloader prueft {@link #isCancelled()} in seiner
 * Streaming-Schleife und registriert ueber {@link #onCancel(Runnable)} zusaetzlich
 * ein sofortiges Schliessen des offenen Streams, damit ein gerade blockierendes
 * {@code read} unmittelbar abbricht.
 *
 * <p>Alle Methoden sind thread-sicher; {@link #cancel()} darf aus einem anderen
 * Thread als der Download kommen.</p>
 */
public final class CancellationToken {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Download");

	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private final Queue<Runnable> listeners = new ConcurrentLinkedQueue<>();

	/** Bricht den Download ab. Idempotent: ein zweiter Aufruf tut nichts. */
	public void cancel() {
		if (cancelled.compareAndSet(false, true)) {
			Runnable listener;
			while ((listener = listeners.poll()) != null) {
				runQuietly(listener);
			}
		}
	}

	public boolean isCancelled() {
		return cancelled.get();
	}

	/** Wirft {@link DownloadCancelledException}, wenn bereits abgebrochen wurde. */
	public void throwIfCancelled() throws DownloadCancelledException {
		if (cancelled.get()) {
			throw new DownloadCancelledException();
		}
	}

	/**
	 * Registriert eine Aufraeum-Aktion (z. B. Stream schliessen), die bei
	 * {@link #cancel()} sofort laeuft. Ist bereits abgebrochen, laeuft sie
	 * unmittelbar in diesem Aufruf.
	 */
	public void onCancel(Runnable action) {
		Objects.requireNonNull(action, "action");
		listeners.add(action);
		if (cancelled.get() && listeners.remove(action)) {
			runQuietly(action);
		}
	}

	/** Meldet eine zuvor registrierte Aktion wieder ab (Download regulaer beendet). */
	public void removeListener(Runnable action) {
		listeners.remove(action);
	}

	private static void runQuietly(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			LOGGER.warn("CancellationToken-Listener warf eine Ausnahme: {}", e.toString(), e);
		}
	}
}
