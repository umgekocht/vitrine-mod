package de.spassglas.vitrine.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rohe HTTP-Antwort eines {@link Transport}: Statuscode, Header (Namen
 * case-insensitiv), bereits laengenbegrenzt gelesener Koerper. Bewusst schlank
 * -- Weiterleitungs-, ETag- und Whitelist-Logik sitzt eine Schicht hoeher in
 * {@link HttpCatalogClient}, transport-unabhaengig und damit headless testbar.
 */
public record TransportResponse(int status, Map<String, List<String>> headers, byte[] body) {

	public TransportResponse {
		headers = Map.copyOf(headers);
		body = body.clone();
	}

	@Override
	public byte[] body() {
		return body.clone();
	}

	/** Erster Wert des Headers {@code name} (case-insensitiv), oder leer. */
	public Optional<String> firstHeader(String name) {
		String wanted = name.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey().toLowerCase(Locale.ROOT).equals(wanted) && !entry.getValue().isEmpty()) {
				return Optional.ofNullable(entry.getValue().get(0));
			}
		}
		return Optional.empty();
	}

	public boolean isRedirect() {
		return status >= 300 && status < 400 && status != 304;
	}
}
