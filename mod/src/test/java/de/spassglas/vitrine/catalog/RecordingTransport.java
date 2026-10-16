package de.spassglas.vitrine.catalog;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-{@link Transport}: gibt vorher eingereihte Antworten (oder Fehler) in
 * Reihenfolge zurueck und protokolliert jede angefragte URI samt Headern.
 * Damit laesst sich pruefen, dass nach einer abgelehnten Weiterleitung
 * <em>kein</em> weiterer Request rausgeht.
 */
final class RecordingTransport implements Transport {

	interface Handler {
		TransportResponse handle(URI uri, Map<String, String> headers) throws IOException;
	}

	private final Deque<Handler> handlers = new ArrayDeque<>();
	private final List<URI> requestedUris = new ArrayList<>();
	private final List<Map<String, String>> capturedHeaders = new ArrayList<>();

	RecordingTransport enqueue(Handler handler) {
		handlers.add(handler);
		return this;
	}

	RecordingTransport enqueueResponse(int status, byte[] body, String... headerPairs) {
		return enqueue((uri, headers) -> response(status, body, headerPairs));
	}

	RecordingTransport enqueueFailure(IOException failure) {
		return enqueue((uri, headers) -> {
			throw failure;
		});
	}

	@Override
	public TransportResponse get(URI uri, Map<String, String> requestHeaders) throws IOException {
		requestedUris.add(uri);
		capturedHeaders.add(Map.copyOf(requestHeaders));
		Handler handler = handlers.poll();
		if (handler == null) {
			throw new AssertionError("RecordingTransport: unerwarteter Request an " + uri);
		}
		return handler.handle(uri, requestHeaders);
	}

	List<URI> requestedUris() {
		return requestedUris;
	}

	Map<String, String> headersOfRequest(int index) {
		return capturedHeaders.get(index);
	}

	int requestCount() {
		return requestedUris.size();
	}

	static TransportResponse response(int status, byte[] body, String... headerPairs) {
		if (headerPairs.length % 2 != 0) {
			throw new IllegalArgumentException("headerPairs muss gerade sein");
		}
		Map<String, List<String>> headers = new HashMap<>();
		for (int i = 0; i < headerPairs.length; i += 2) {
			headers.computeIfAbsent(headerPairs[i], key -> new ArrayList<>()).add(headerPairs[i + 1]);
		}
		return new TransportResponse(status, headers, body == null ? new byte[0] : body);
	}
}
