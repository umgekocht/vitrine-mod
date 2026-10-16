package de.spassglas.vitrine.ui.image;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * GATE17: Vorschaubilder sicher aus dem Paket lesen -- echte PNGs in echten
 * ZIP-Archiven, dieselbe Sicherheitspruefung wie bei der Installation.
 */
public class PackageImageBytesTest {

	private Path dir;

	@BeforeMethod
	public void setUp() throws IOException {
		dir = Files.createTempDirectory("vitrine-gate17-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	public void readsAPreviewImageWithItsRealSize() throws IOException {
		Path pkg = packageWith(Map.of("preview/card.png", png(512, 288)));

		PackageImageBytes.Image image = PackageImageBytes.read(pkg, "preview/card.png").orElseThrow();

		assertEquals(image.width(), 512);
		assertEquals(image.height(), 288);
		assertTrue(image.png().length > 0);
	}

	@Test
	public void imageLargerThanThePreviewLimitIsNotLoaded() throws IOException {
		// Fuer P3 als Textur noch erlaubt (bis 4096), als Vorschaubild zu gross.
		Path pkg = packageWith(Map.of("preview/riesig.png", png(PackageImageBytes.MAX_PREVIEW_DIMENSION + 1, 10)));
		assertTrue(PackageImageBytes.read(pkg, "preview/riesig.png").isEmpty());
	}

	@Test
	public void missingEntryAndNonPngYieldNothingInsteadOfThrowing() throws IOException {
		Path pkg = packageWith(Map.of("preview/card.png", png(16, 16)));
		assertTrue(PackageImageBytes.read(pkg, "preview/fehlt.png").isEmpty(), "fehlender Eintrag");
		assertTrue(PackageImageBytes.read(pkg, "preview/card.jpg").isEmpty(), "kein PNG");
	}

	@Test
	public void archiveThatFailsTheSecurityCheckYieldsNoImage() throws IOException {
		Path pkg = dir.resolve("zipslip.vpkg");
		Files.write(pkg, VpkgFixtures.zipSlipPackage());
		assertTrue(PackageImageBytes.read(pkg, "preview/card.png").isEmpty(),
				"ein Paket, das die Sicherheitspruefung nicht besteht, liefert auch kein Bild");
	}

	@Test
	public void tileImagePrefersTheIconThenTheFirstScreenshot() throws Exception {
		Path full = Path.of("..", "registry", "schema", "examples", "valid", "full.json");
		if (!Files.exists(full)) {
			full = Path.of("registry", "schema", "examples", "valid", "full.json");
		}
		Manifest manifest = ManifestParser.parse(Files.readString(full, StandardCharsets.UTF_8));

		Optional<String> chosen = PackageImageBytes.tileImageEntry(manifest);

		String icon = manifest.icon();
		if (icon != null && icon.endsWith(".png")) {
			assertEquals(chosen, Optional.of(icon), "das Icon hat Vorrang");
		} else {
			assertEquals(chosen, manifest.screenshots().stream().filter(s -> s.endsWith(".png")).findFirst());
		}
	}

	// --- Hilfen ---------------------------------------------------------

	private Path packageWith(Map<String, byte[]> images) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, "{}".getBytes(StandardCharsets.UTF_8));
		entries.putAll(images);
		Path pkg = dir.resolve("paket" + System.nanoTime() + ".vpkg");
		Files.write(pkg, VpkgFixtures.buildZip(entries));
		return pkg;
	}

	/**
	 * Ein echtes, dekodierbares PNG (Graustufen, 8 Bit) -- von Hand gebaut,
	 * weil das JDK dieser Umgebung keine AWT-Bibliotheken mitbringt.
	 */
	private static byte[] png(int width, int height) throws IOException {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		for (int y = 0; y < height; y++) {
			raw.write(0); // Filtertyp "None"
			raw.write(new byte[width]);
		}
		java.util.zip.Deflater deflater = new java.util.zip.Deflater();
		deflater.setInput(raw.toByteArray());
		deflater.finish();
		ByteArrayOutputStream idat = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		while (!deflater.finished()) {
			idat.write(buf, 0, deflater.deflate(buf));
		}
		deflater.end();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13);
		ihdr.putInt(width).putInt(height).put((byte) 8).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
		chunk(out, "IHDR", ihdr.array());
		chunk(out, "IDAT", idat.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
		out.write(typeBytes);
		out.write(data);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(typeBytes);
		crc.update(data);
		out.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}
}
