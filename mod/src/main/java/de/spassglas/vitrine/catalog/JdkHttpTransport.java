package de.spassglas.vitrine.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;

/**
 * Produktions-{@link Transport} auf JDK-Bordmitteln ({@code java.net.http},
 * keine neue Abhaengigkeit). Nur {@code https}-GET;
 * {@link HttpClient.Redirect#NEVER}, damit eine 30x-Antwort <em>nicht</em>
 * automatisch verfolgt wird -- das Weiterleitungsziel prueft
 * {@link HttpCatalogClient} selbst gegen die {@link HostWhitelist}. Connect- und
 * Request-Timeout sowie das Groessenlimit stammen aus {@link CatalogEndpoints}.
 */
public final class JdkHttpTransport implements Transport {

	private final HttpClient client;

	public JdkHttpTransport() {
		this(HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(CatalogEndpoints.CONNECT_TIMEOUT)
				.build());
	}

	JdkHttpTransport(HttpClient client) {
		this.client = client;
	}

	@Override
	public TransportResponse get(URI uri, Map<String, String> requestHeaders) throws IOException {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			// Unerreichbar in Produktion: CatalogClient verdrahtet HostWhitelist.PRODUCTION,
			// das jede nicht-https-URI schon vorher ablehnt. Defense in depth.
			throw new IllegalArgumentException("JdkHttpTransport erlaubt nur https, war: " + uri.getScheme());
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.GET()
				.timeout(CatalogEndpoints.REQUEST_TIMEOUT);
		for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}

		HttpResponse<InputStream> response;
		try {
			response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Katalog-Abfrage unterbrochen: " + uri, e);
		}

		BoundedBody.requireDeclaredLengthWithinLimit(
				response.headers().firstValueAsLong("content-length"), CatalogEndpoints.MAX_BODY_BYTES);

		byte[] body;
		try (InputStream in = response.body()) {
			body = BoundedBody.readAtMost(in, CatalogEndpoints.MAX_BODY_BYTES);
		}

		return new TransportResponse(response.statusCode(), lowerCaseKeys(response.headers().map()), body);
	}

	private static Map<String, java.util.List<String>> lowerCaseKeys(Map<String, java.util.List<String>> in) {
		java.util.Map<String, java.util.List<String>> out = new java.util.HashMap<>();
		for (Map.Entry<String, java.util.List<String>> entry : in.entrySet()) {
			out.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
		}
		return out;
	}
}
