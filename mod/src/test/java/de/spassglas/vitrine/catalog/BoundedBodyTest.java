package de.spassglas.vitrine.catalog;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.OptionalLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Groessenlimit am Antwort-Stream (ROADMAP K2): kein Vollpuffern ohne Limit,
 * und eine {@code Content-Length}-Luege wird abgefangen.
 */
public final class BoundedBodyTest {

	@Test
	public void readsStreamThatFitsWithinLimit() throws IOException {
		byte[] data = new byte[4096];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		byte[] read = BoundedBody.readAtMost(new ByteArrayInputStream(data), 4096);
		assertEquals(read, data);
	}

	@Test
	public void abortsWhenStreamExceedsLimit() {
		byte[] data = new byte[4097];
		assertThrows(ResponseTooLargeException.class,
				() -> BoundedBody.readAtMost(new ByteArrayInputStream(data), 4096));
	}

	@Test
	public void contentLengthLieIsCaughtByActualByteCount() {
		// Server kuendigt 10 Bytes an, sendet aber 5000: readAtMost zaehlt die
		// tatsaechlich gelesenen Bytes und bricht ab.
		byte[] data = new byte[5000];
		assertThrows(ResponseTooLargeException.class,
				() -> BoundedBody.readAtMost(new ByteArrayInputStream(data), 4096));
	}

	@Test
	public void rejectsDeclaredLengthOverLimitBeforeReading() {
		assertThrows(ResponseTooLargeException.class,
				() -> BoundedBody.requireDeclaredLengthWithinLimit(OptionalLong.of(4097), 4096));
	}

	@Test
	public void acceptsDeclaredLengthAtOrBelowLimit() throws IOException {
		BoundedBody.requireDeclaredLengthWithinLimit(OptionalLong.of(4096), 4096);
		BoundedBody.requireDeclaredLengthWithinLimit(OptionalLong.empty(), 4096);
	}
}
