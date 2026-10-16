package de.spassglas.vitrine.catalog.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Produktions-{@link DownloadTransport} auf JDK-Bordmitteln
 * ({@code java.net.http}, keine neue Abhaengigkeit). Nur {@code https}-GET;
 * {@link HttpClient.Redirect#NEVER}, damit eine 30x-Antwort <em>nicht</em>
 * automatisch verfolgt wird -- das Weiterleitungsziel prueft
 * {@link PackageDownloader} selbst gegen {@link DownloadHostWhitelist}.
 *
 * <p>Der Body kommt als {@link HttpResponse.BodyHandlers#ofInputStream()
 * InputStream} und wird mit {@link ReadTimeoutInputStream} gegen einen
 * haengenden Transfer abgesichert. {@code connectTimeout} und
 * {@code request.timeout} decken nur Verbindungsaufbau und Antwort-Header ab --
 * bewusst kein hartes Gesamt-Timeout ueber das Streamen eines bis zu 512 MiB
 * grossen Pakets.</p>
 */
public final class JdkDownloadTransport implements DownloadTransport {

	private final HttpClient client;

	public JdkDownloadTransport() {
		this(HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(DownloadEndpoints.CONNECT_TIMEOUT)
				.build());
	}

	JdkDownloadTransport(HttpClient client) {
		this.client = client;
	}

	@Override
	public DownloadResponse open(URI uri, long resumeFromOffset) throws IOException {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			// Unerreichbar in Produktion: PackageDownloader verdrahtet
			// DownloadHostWhitelist.RELEASE_ASSETS, das jede nicht-https-URI
			// schon vorher ablehnt. Defense in depth.
			throw new IllegalArgumentException("JdkDownloadTransport erlaubt nur https, war: " + uri.getScheme());
		}
		if (resumeFromOffset < 0) {
			throw new IllegalArgumentException("resumeFromOffset darf nicht negativ sein: " + resumeFromOffset);
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.GET()
				.timeout(DownloadEndpoints.RESPONSE_HEADER_TIMEOUT);
		if (resumeFromOffset > 0) {
			builder.header("Range", "bytes=" + resumeFromOffset + "-");
		}

		HttpResponse<InputStream> response;
		try {
			response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Download unterbrochen: " + uri, e);
		}

		InputStream guarded = new ReadTimeoutInputStream(response.body(), DownloadEndpoints.READ_TIMEOUT);
		return new DownloadResponse(response.statusCode(), response.headers().map(), guarded);
	}
}
