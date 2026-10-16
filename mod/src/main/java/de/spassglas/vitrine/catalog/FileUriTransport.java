package de.spassglas.vitrine.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@link Transport}, der {@code file://}-URIs von der lokalen Platte liest --
 * gedacht fuer Tests (Fertig-Kriterium K2: "lokaler {@code file://}-URL ueber
 * den Transport-Seam") und lokale Trockenlaeufe. Liefert immer {@code HTTP 200}
 * und respektiert dabei dasselbe Groessenlimit wie {@link JdkHttpTransport}.
 * Der {@code file://}-Pfad umgeht die HTTPS-Pflicht bewusst nur ueber diesen
 * Seam; die Produktion verdrahtet ihn nicht.
 */
public final class FileUriTransport implements Transport {

	@Override
	public TransportResponse get(URI uri, Map<String, String> requestHeaders) throws IOException {
		if (!"file".equalsIgnoreCase(uri.getScheme())) {
			throw new IOException("FileUriTransport kann nur file://-URIs lesen, war: " + uri);
		}
		Path path = Path.of(uri);
		if (!Files.isRegularFile(path)) {
			throw new NoSuchFileException(path.toString());
		}
		byte[] body;
		try (InputStream in = Files.newInputStream(path)) {
			body = BoundedBody.readAtMost(in, CatalogEndpoints.MAX_BODY_BYTES);
		}
		return new TransportResponse(200, Map.<String, List<String>>of(), body);
	}
}
