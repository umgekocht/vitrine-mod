package de.spassglas.vitrine.classb;

import de.spassglas.vitrine.catalog.download.DownloadEndpoints;
import de.spassglas.vitrine.catalog.download.DownloadHostWhitelist;
import de.spassglas.vitrine.catalog.download.DownloadResponse;
import de.spassglas.vitrine.catalog.download.DownloadTransport;
import de.spassglas.vitrine.catalog.download.JdkDownloadTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * ROADMAP J2 -- Produktions-{@link JarSource}: laedt die Klasse-B-Jar von
 * {@link JavaModOffer#sourceUrl()} an den temporaeren Ort. Reicht denselben
 * Streaming-/Whitelist-Unterbau wie {@code PackageDownloader} (K3) durch:
 * {@link DownloadTransport} ({@link JdkDownloadTransport} in Produktion,
 * {@code followRedirects(NEVER)}), Host-Whitelist
 * ({@link DownloadHostWhitelist#RELEASE_ASSETS}: {@code github.com},
 * {@code objects.githubusercontent.com}, {@code release-assets.githubusercontent.com}),
 * Weiterleitungen nur auf erneut erlaubte Hosts, harte Groessenobergrenze am
 * Stream.
 *
 * <p>Bewusst schlanker als {@code PackageDownloader}: kein {@code .part}, keine
 * Wiederaufnahme, keine Signaturpflicht -- die Datei landet an einem
 * temporaeren Ort und {@link JavaModStager#stage} prueft dort die Pruefsumme
 * (und ggf. Signatur), <em>bevor</em> irgendetwas nach {@code mods/} wandert
 * (Charter-Regel 3).</p>
 */
public final class HttpJarSource implements JarSource {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/ClassB");

	private final DownloadTransport transport;
	private final Predicate<URI> hostRule;
	private final long maxBytes;

	public HttpJarSource(DownloadTransport transport, Predicate<URI> hostRule, long maxBytes) {
		this.transport = Objects.requireNonNull(transport, "transport");
		this.hostRule = Objects.requireNonNull(hostRule, "hostRule");
		if (maxBytes <= 0) {
			throw new IllegalArgumentException("maxBytes muss > 0 sein");
		}
		this.maxBytes = maxBytes;
	}

	/** Produktions-Verdrahtung: echter JDK-Transport + GitHub-Release-Asset-Whitelist. */
	public static HttpJarSource production() {
		return new HttpJarSource(new JdkDownloadTransport(), DownloadHostWhitelist.RELEASE_ASSETS,
				DownloadEndpoints.MAX_PACKAGE_BYTES);
	}

	@Override
	public Path fetch(JavaModOffer offer, Path tempDir) throws IOException {
		Objects.requireNonNull(offer, "offer");
		Objects.requireNonNull(tempDir, "tempDir");
		if (offer.sourceUrl() == null || offer.sourceUrl().isBlank()) {
			throw new IOException("Angebot '" + offer.id() + "' hat keine sourceUrl -- HttpJarSource kann nichts laden.");
		}
		URI uri;
		try {
			uri = new URI(offer.sourceUrl());
		} catch (URISyntaxException e) {
			throw new IOException("sourceUrl von '" + offer.id() + "' ist keine gueltige URI: " + e.getMessage(), e);
		}

		Path target = tempDir.resolve(offer.fileName());
		int redirects = 0;
		while (true) {
			final URI current = uri;
			if (!hostRule.test(current)) {
				throw new IOException("Jar-URL nicht erlaubt (nicht auf der Whitelist): " + current);
			}
			LOGGER.info("Klasse-B-Jar laden: {}", current);
			try (DownloadResponse response = transport.open(current, 0L)) {
				if (response.isRedirect()) {
					redirects++;
					if (redirects > DownloadEndpoints.MAX_REDIRECTS) {
						throw new IOException("Zu viele Weiterleitungen (> " + DownloadEndpoints.MAX_REDIRECTS + ")");
					}
					String location = response.location().map(String::trim).filter(s -> !s.isEmpty())
							.orElseThrow(() -> new IOException("Weiterleitung ohne Location-Header von " + current));
					URI next = current.resolve(location);
					if (!hostRule.test(next)) {
						throw new IOException("Weiterleitung auf nicht erlaubtes Ziel: " + next);
					}
					uri = next;
					continue;
				}
				if (response.status() != 200) {
					throw new IOException("HTTP " + response.status() + " von " + current);
				}
				long written = streamToFile(response.body(), target);
				LOGGER.info("Klasse-B-Jar '{}' bezogen ({} Bytes) -> {}", offer.id(), written, target);
				return target;
			}
		}
	}

	private long streamToFile(InputStream body, Path target) throws IOException {
		long written = 0L;
		byte[] buffer = new byte[DownloadEndpoints.TRANSFER_BUFFER_BYTES];
		try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE)) {
			int read;
			while ((read = body.read(buffer)) != -1) {
				written += read;
				if (written > maxBytes) {
					throw new IOException("Jar ueberschreitet die Obergrenze von " + maxBytes + " Bytes");
				}
				out.write(buffer, 0, read);
			}
			out.flush();
		}
		return written;
	}
}
