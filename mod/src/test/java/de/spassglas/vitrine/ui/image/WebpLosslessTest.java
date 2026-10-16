package de.spassglas.vitrine.ui.image;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/** Vergleicht den eigenen VP8L-Decoder Pixel fuer Pixel mit libwebp (Fixtures aus Pillow). */
public class WebpLosslessTest {

	private static byte[] res(String name) throws IOException {
		try (InputStream in = WebpLosslessTest.class.getResourceAsStream("/webp/" + name)) {
			if (in == null) {
				throw new IOException("Fixture fehlt: " + name);
			}
			return in.readAllBytes();
		}
	}

	@DataProvider
	public Object[][] fixtures() {
		return new Object[][] {{"gradient"}, {"palette"}, {"noise"}, {"pattern"}, {"logo"}};
	}

	@Test(dataProvider = "fixtures")
	public void decodesLikeLibwebp(String name) throws IOException {
		WebpLossless.Image img = WebpLossless.decode(res(name + ".webp"));
		byte[] rgba = res(name + ".rgba");
		assertEquals(img.argb().length * 4, rgba.length, "Groesse");
		for (int i = 0; i < img.argb().length; i++) {
			int p = img.argb()[i];
			int expected = (rgba[i * 4 + 3] & 0xff) << 24 | (rgba[i * 4] & 0xff) << 16 | (rgba[i * 4 + 1] & 0xff) << 8
					| (rgba[i * 4 + 2] & 0xff);
			if (p != expected) {
				throw new AssertionError(name + ": Pixel " + i + " (" + (i % img.width()) + "," + (i / img.width())
						+ ") ist " + Integer.toHexString(p) + ", erwartet " + Integer.toHexString(expected));
			}
		}
	}

	@Test
	public void rejectsGarbage() {
		assertThrows(IOException.class, () -> WebpLossless.decode(new byte[] {1, 2, 3}));
		byte[] truncated = new byte[40];
		System.arraycopy("RIFF\0\0\0\0WEBPVP8L".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, truncated, 0, 16);
		assertTrue(WebpLossless.isWebp(truncated));
		assertThrows(IOException.class, () -> WebpLossless.decode(truncated));
	}
}
