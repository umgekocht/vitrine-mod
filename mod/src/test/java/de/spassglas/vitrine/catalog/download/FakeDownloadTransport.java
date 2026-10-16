package de.spassglas.vitrine.catalog.download;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Steuerbarer {@link DownloadTransport} fuer die K3-Tests (Gegenstueck zu
 * {@code de.spassglas.vitrine.catalog.RecordingTransport}). Gibt vorher
 * eingereihte Antworten in Reihenfolge zurueck, protokolliert jeden
 * {@code open}-Aufruf samt {@code resumeFromOffset} und liefert die Bausteine
 * fuer einen mitten im Lesen abbrechbaren bzw. nach N Bytes scheiternden Body.
 */
final class FakeDownloadTransport implements DownloadTransport {

	interface Handler {
		DownloadResponse handle(URI uri, long resumeFromOffset) throws IOException;
	}

	private final Deque<Handler> handlers = new ArrayDeque<>();
	private final List<URI> openedUris = new ArrayList<>();
	private final List<Long> openedOffsets = new ArrayList<>();

	FakeDownloadTransport enqueue(Handler handler) {
		handlers.add(handler);
		return this;
	}

	FakeDownloadTransport enqueueOk(byte[] body) {
		return enqueue((uri, offset) -> response(200, new ByteArrayInputStream(body),
				"content-length", Integer.toString(body.length)));
	}

	FakeDownloadTransport enqueueStatus(int status) {
		return enqueue((uri, offset) -> response(status, InputStream.nullInputStream()));
	}

	FakeDownloadTransport enqueueRedirect(int status, String location) {
		return enqueue((uri, offset) -> response(status, InputStream.nullInputStream(), "location", location));
	}

	FakeDownloadTransport enqueue206(byte[] fullBody, int from) {
		return enqueue((uri, offset) -> {
			int remaining = fullBody.length - from;
			return response(206, new ByteArrayInputStream(fullBody, from, remaining),
					"content-length", Integer.toString(remaining),
					"content-range", "bytes " + from + "-" + (fullBody.length - 1) + "/" + fullBody.length);
		});
	}

	@Override
	public DownloadResponse open(URI uri, long resumeFromOffset) throws IOException {
		openedUris.add(uri);
		openedOffsets.add(resumeFromOffset);
		Handler handler = handlers.poll();
		if (handler == null) {
			throw new AssertionError("FakeDownloadTransport: unerwarteter open() an " + uri
					+ " (offset " + resumeFromOffset + ")");
		}
		return handler.handle(uri, resumeFromOffset);
	}

	int openCount() {
		return openedUris.size();
	}

	URI openedUri(int index) {
		return openedUris.get(index);
	}

	long openedOffset(int index) {
		return openedOffsets.get(index);
	}

	static DownloadResponse response(int status, InputStream body, String... headerPairs) {
		if (headerPairs.length % 2 != 0) {
			throw new IllegalArgumentException("headerPairs muss gerade sein");
		}
		Map<String, List<String>> headers = new HashMap<>();
		for (int i = 0; i < headerPairs.length; i += 2) {
			headers.computeIfAbsent(headerPairs[i], k -> new ArrayList<>()).add(headerPairs[i + 1]);
		}
		return new DownloadResponse(status, headers, body);
	}

	// --- Body-Streams mit steuerbarem Verhalten --------------------------

	/**
	 * Liefert {@code data} in 4-KiB-Bloecken, bis genau {@code throwAfter} Bytes
	 * ausgegeben sind, und wirft danach eine {@link IOException} -- simuliert
	 * einen mitten im Transfer abgerissenen Netz-Download.
	 */
	static final class ThrowingBody extends InputStream {
		private final byte[] data;
		private final int throwAfter;
		private int pos;

		ThrowingBody(byte[] data, int throwAfter) {
			this.data = data;
			this.throwAfter = throwAfter;
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int n = read(one, 0, 1);
			return n == -1 ? -1 : (one[0] & 0xff);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (pos >= throwAfter) {
				throw new IOException("simulierter Netz-Abbruch nach " + throwAfter + " Bytes");
			}
			int chunk = Math.min(len, Math.min(throwAfter - pos, 4096));
			System.arraycopy(data, pos, b, off, chunk);
			pos += chunk;
			return chunk;
		}
	}

	/**
	 * Liefert {@code data} in 4-KiB-Bloecken, blockiert aber, sobald
	 * {@code gateAt} Bytes ausgegeben sind, bis der Test {@link #openGate()}
	 * ruft -- oder bis {@link #close()} (durch {@code CancellationToken.cancel()})
	 * den blockierten {@code read} mit einer {@link IOException} aufweckt.
	 */
	static final class GatedBody extends InputStream {
		private final byte[] data;
		private final int gateAt;
		private final Object lock = new Object();
		private final CountDownLatch reachedGate = new CountDownLatch(1);
		private int pos;
		private volatile boolean gateOpen;
		private volatile boolean closed;

		GatedBody(byte[] data, int gateAt) {
			this.data = data;
			this.gateAt = gateAt;
		}

		void openGate() {
			synchronized (lock) {
				gateOpen = true;
				lock.notifyAll();
			}
		}

		void awaitGateReached() throws InterruptedException {
			if (!reachedGate.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
				throw new AssertionError("Body-Stream hat den Gate-Punkt nie erreicht");
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
			if (closed) {
				throw new IOException("Body-Stream geschlossen");
			}
			if (pos >= data.length) {
				return -1;
			}
			if (pos >= gateAt && !gateOpen) {
				reachedGate.countDown();
				synchronized (lock) {
					while (!gateOpen && !closed) {
						try {
							lock.wait();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new IOException("unterbrochen", e);
						}
					}
				}
				if (closed) {
					throw new IOException("Body-Stream waehrend der Blockade geschlossen");
				}
			}
			int chunk = Math.min(len, Math.min(data.length - pos, 4096));
			System.arraycopy(data, pos, b, off, chunk);
			pos += chunk;
			return chunk;
		}

		@Override
		public void close() {
			synchronized (lock) {
				closed = true;
				lock.notifyAll();
			}
		}
	}

	/** Liefert {@code data} in 4-KiB-Bloecken -- gibt aber mehr Bytes aus, als jeder Header ankuendigt. */
	static final class OversizeBody extends InputStream {
		private final byte[] data;
		private int pos;

		OversizeBody(byte[] data) {
			this.data = data;
		}

		@Override
		public int read() {
			byte[] one = new byte[1];
			int n = read(one, 0, 1);
			return n == -1 ? -1 : (one[0] & 0xff);
		}

		@Override
		public int read(byte[] b, int off, int len) {
			if (pos >= data.length) {
				return -1;
			}
			int chunk = Math.min(len, Math.min(data.length - pos, 4096));
			System.arraycopy(data, pos, b, off, chunk);
			pos += chunk;
			return chunk;
		}
	}
}
