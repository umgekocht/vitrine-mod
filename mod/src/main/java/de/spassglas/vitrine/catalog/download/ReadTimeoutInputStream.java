package de.spassglas.vitrine.catalog.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Legt eine <em>Leerlauf</em>-Grenze zwischen zwei empfangenen Datenbloecken um
 * einen Body-Stream (ROADMAP K3: "kein hartes Gesamt-Timeout beim
 * Body-Streaming, aber ein Read-Timeout"). Kein Gesamt-Timeout -- ein grosses
 * Paket ueber eine langsame Leitung darf lange dauern, solange <em>ueberhaupt</em>
 * Daten fliessen. Bleibt ein blockierendes {@code read} laenger als das Timeout
 * ohne Fortschritt, schliesst ein Wachhund-Thread den darunterliegenden Stream;
 * das blockierte {@code read} bricht dann mit einer {@link IOException} ab, die
 * hier in eine sprechende "Lese-Timeout"-Meldung uebersetzt wird.
 *
 * <p>Der Wachhund ist ein einziger, prozessweiter Daemon-Thread (Downloads sind
 * selten und seriell) -- er verhindert den JVM-Exit nicht. Jede Instanz meldet
 * beim {@link #close()} ihre periodische Pruefaufgabe wieder ab.</p>
 */
final class ReadTimeoutInputStream extends FilterInputStream {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Download");

	private static final ScheduledExecutorService WATCHDOG =
			Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "Vitrine-Download-ReadTimeout");
				thread.setDaemon(true);
				return thread;
			});

	private final long timeoutNanos;
	private final ScheduledFuture<?> ticker;

	private volatile boolean armed;
	private volatile long deadlineNanos;
	private volatile boolean timedOut;

	ReadTimeoutInputStream(InputStream in, Duration timeout) {
		super(Objects.requireNonNull(in, "in"));
		this.timeoutNanos = timeout.toNanos();
		long pollNanos = Math.min(
				Math.max(timeoutNanos / 4, TimeUnit.MILLISECONDS.toNanos(20)),
				Math.max(timeoutNanos, 1));
		this.ticker = WATCHDOG.scheduleAtFixedRate(this::tick, pollNanos, pollNanos, TimeUnit.NANOSECONDS);
	}

	private void tick() {
		if (armed && !timedOut && System.nanoTime() - deadlineNanos >= 0) {
			timedOut = true;
			LOGGER.warn("Lese-Timeout: laenger als {} ms keine Daten empfangen -- Verbindung wird geschlossen",
					TimeUnit.NANOSECONDS.toMillis(timeoutNanos));
			try {
				in.close();
			} catch (IOException e) {
				LOGGER.warn("Schliessen des Streams nach Lese-Timeout schlug fehl: {}", e.toString());
			}
		}
	}

	@Override
	public int read() throws IOException {
		byte[] one = new byte[1];
		int n = read(one, 0, 1);
		return n == -1 ? -1 : (one[0] & 0xff);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		deadlineNanos = System.nanoTime() + timeoutNanos;
		armed = true;
		try {
			return in.read(b, off, len);
		} catch (IOException e) {
			if (timedOut) {
				throw new IOException("Lese-Timeout: laenger als "
						+ TimeUnit.NANOSECONDS.toMillis(timeoutNanos) + " ms keine Daten empfangen", e);
			}
			throw e;
		} finally {
			armed = false;
		}
	}

	@Override
	public void close() throws IOException {
		ticker.cancel(false);
		armed = false;
		super.close();
	}
}
