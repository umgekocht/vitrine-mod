package de.spassglas.vitrine.catalog.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DownloadTransport}, der {@code file://}-URIs von der lokalen Platte
 * streamt -- fuer Tests (K3-Parallele zu {@code FileUriTransport} aus K2) und
 * lokale Trockenlaeufe. Unterstuetzt Wiederaufnahme durch Ueberspringen von
 * {@code resumeFromOffset} Bytes und antwortet dann mit {@code 206} plus
 * {@code Content-Range}; ohne Versatz mit {@code 200}. Der {@code file://}-Pfad
 * umgeht die HTTPS-Pflicht bewusst nur ueber diesen Seam; die Produktion
 * verdrahtet ihn nicht.
 */
public final class FileUriDownloadTransport implements DownloadTransport {

	@Override
	public DownloadResponse open(URI uri, long resumeFromOffset) throws IOException {
		if (!"file".equalsIgnoreCase(uri.getScheme())) {
			throw new IOException("FileUriDownloadTransport kann nur file://-URIs lesen, war: " + uri);
		}
		if (resumeFromOffset < 0) {
			throw new IllegalArgumentException("resumeFromOffset darf nicht negativ sein: " + resumeFromOffset);
		}
		Path path = Path.of(uri);
		if (!Files.isRegularFile(path)) {
			throw new NoSuchFileException(path.toString());
		}
		long size = Files.size(path);

		if (resumeFromOffset > 0) {
			if (resumeFromOffset >= size) {
				return new DownloadResponse(416, Map.of(), InputStream.nullInputStream());
			}
			InputStream in = Files.newInputStream(path);
			in.skipNBytes(resumeFromOffset);
			long remaining = size - resumeFromOffset;
			return new DownloadResponse(206, headers(
					"content-length", Long.toString(remaining),
					"content-range", "bytes " + resumeFromOffset + "-" + (size - 1) + "/" + size), in);
		}

		return new DownloadResponse(200, headers("content-length", Long.toString(size)),
				Files.newInputStream(path));
	}

	private static Map<String, List<String>> headers(String... pairs) {
		Map<String, List<String>> map = new java.util.HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.computeIfAbsent(pairs[i], k -> new ArrayList<>()).add(pairs[i + 1]);
		}
		return map;
	}
}
