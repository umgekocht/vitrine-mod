package de.spassglas.vitrine.catalog.download;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Rohe, <em>noch nicht gepufferte</em> Antwort eines {@link DownloadTransport}:
 * Statuscode, Header (Namen klein geschrieben) und der offene Body-Stream. Im
 * Gegensatz zu {@code de.spassglas.vitrine.catalog.TransportResponse} (bis 8 MiB
 * komplett im Speicher) wird der Body hier <strong>gestreamt</strong> -- ein
 * {@code .vpkg} kann bis 512 MiB gross sein.
 *
 * <p>Der Aufrufer ist fuer {@link #close()} verantwortlich; das schliesst den
 * Body-Stream und damit die Verbindung.</p>
 */
public final class DownloadResponse implements Closeable {

	private final int status;
	private final Map<String, List<String>> headers;
	private final InputStream body;

	public DownloadResponse(int status, Map<String, List<String>> headers, InputStream body) {
		this.status = status;
		this.body = Objects.requireNonNull(body, "body");
		this.headers = lowerCaseKeys(Objects.requireNonNull(headers, "headers"));
	}

	public int status() {
		return status;
	}

	/** Der offene Body-Stream. Nicht erneut puffern -- direkt in die Datei schreiben. */
	public InputStream body() {
		return body;
	}

	/** Erster Wert des Headers {@code name} (case-insensitiv), oder leer. */
	public Optional<String> firstHeader(String name) {
		List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
		if (values == null || values.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(values.get(0));
	}

	/** {@code Content-Length} als Zahl, oder leer, wenn fehlend/unparsbar. */
	public OptionalLong contentLength() {
		return firstHeader("content-length")
				.map(String::trim)
				.flatMap(DownloadResponse::parseLong)
				.map(OptionalLong::of)
				.orElse(OptionalLong.empty());
	}

	/**
	 * Startversatz aus {@code Content-Range: bytes <start>-<end>/<total>}, oder
	 * leer, wenn der Header fehlt oder nicht diesem Muster folgt. Nur fuer die
	 * Plausibilitaetspruefung bei der Wiederaufnahme (HTTP 206).
	 */
	public OptionalLong contentRangeStart() {
		Optional<String> raw = firstHeader("content-range");
		if (raw.isEmpty()) {
			return OptionalLong.empty();
		}
		String value = raw.get().trim();
		if (!value.regionMatches(true, 0, "bytes", 0, "bytes".length())) {
			return OptionalLong.empty();
		}
		String rest = value.substring("bytes".length()).trim();
		int dash = rest.indexOf('-');
		if (dash <= 0) {
			return OptionalLong.empty();
		}
		return parseLong(rest.substring(0, dash).trim())
				.map(OptionalLong::of)
				.orElse(OptionalLong.empty());
	}

	/** {@code Location}-Header einer Weiterleitung, oder leer. */
	public Optional<String> location() {
		return firstHeader("location");
	}

	/** {@code true} fuer 3xx ausser {@code 304 Not Modified}. */
	public boolean isRedirect() {
		return status >= 300 && status < 400 && status != 304;
	}

	@Override
	public void close() throws IOException {
		body.close();
	}

	private static Optional<Long> parseLong(String s) {
		try {
			return Optional.of(Long.parseLong(s));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static Map<String, List<String>> lowerCaseKeys(Map<String, List<String>> in) {
		Map<String, List<String>> out = new java.util.HashMap<>();
		for (Map.Entry<String, List<String>> entry : in.entrySet()) {
			out.computeIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
					.addAll(entry.getValue());
		}
		return Map.copyOf(out);
	}
}
