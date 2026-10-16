package de.spassglas.vitrine.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;

/**
 * Liest einen Antwort-Stream mit harter Byte-Obergrenze. Kein Vollpuffern ohne
 * Limit: sobald mehr als {@code maxBytes} gelesen wurden, bricht das Lesen ab.
 * Dadurch ist auch eine "Content-Length-Luege" (Header kuendigt weniger an, als
 * der Server sendet) abgedeckt -- gezaehlt werden die tatsaechlich gelesenen
 * Bytes, nicht der angekuendigte Wert.
 */
final class BoundedBody {

	private BoundedBody() {
	}

	/**
	 * Liest {@code in} vollstaendig, aber hoechstens {@code maxBytes}.
	 *
	 * @throws ResponseTooLargeException wenn der Stream mehr als {@code maxBytes}
	 *                                   Bytes liefert.
	 */
	static byte[] readAtMost(InputStream in, long maxBytes) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		long total = 0;
		int read;
		while ((read = in.read(chunk)) != -1) {
			total += read;
			if (total > maxBytes) {
				throw new ResponseTooLargeException(
						"Antwortkoerper ueberschreitet das Limit von " + maxBytes + " Bytes");
			}
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

	/**
	 * Lehnt eine Antwort ab, bevor auch nur ein Byte gelesen wird, wenn ihr
	 * {@code Content-Length}-Header bereits ueber dem Limit liegt.
	 */
	static void requireDeclaredLengthWithinLimit(OptionalLong contentLength, long maxBytes)
			throws ResponseTooLargeException {
		if (contentLength.isPresent() && contentLength.getAsLong() > maxBytes) {
			throw new ResponseTooLargeException("Content-Length " + contentLength.getAsLong()
					+ " ueberschreitet das Limit von " + maxBytes + " Bytes");
		}
	}
}
