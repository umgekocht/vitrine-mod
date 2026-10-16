package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt das Fertig-Kriterium von P2 ab: "Test oeffnet ein Paket und liest
 * Manifest + eine Textur." Jedes {@code .vpkg} kommt hier aus den F4-Fixtures
 * ({@link VpkgFixtures}) bzw. wird mit demselben Werkzeug
 * ({@link VpkgFixtures#buildZip(java.util.Map)}) im Speicher gebaut, dann
 * einmal in eine temporaere Datei geschrieben -- {@link VpkgArchive} liest
 * bewusst von der Platte (wie ein echtes installiertes Paket), nicht aus
 * einem Byte-Array, deshalb der Umweg ueber ein Temp-Verzeichnis wie schon in
 * {@code SettingsManagerTest}.
 */
public final class VpkgArchiveTest {

	private Path tempDir;

	@BeforeMethod
	public void createTempDir() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-vpkg-archive-test-");
	}

	@AfterMethod
	public void deleteTempDir() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (var paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new UncheckedTestIOException(e);
						}
					});
		}
	}

	private static final class UncheckedTestIOException extends RuntimeException {
		UncheckedTestIOException(IOException cause) {
			super(cause);
		}
	}

	// --- Fertig-Kriterium: Manifest + eine Textur -----------------------

	@Test
	public void opensPackageAndReadsManifestPlusOneTexture() throws IOException, ManifestParseException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.minimalValidPackage(), "minimal.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			List<VpkgEntry> entries = archive.entries();
			List<String> entryNames = entries.stream().map(VpkgEntry::name).toList();
			assertEquals(entryNames, List.of(VpkgArchive.MANIFEST_ENTRY_NAME, "textures/example.png"),
					"Archiv muss genau Manifest + eine Textur listen, ohne zu entpacken");

			byte[] manifestBytes = archive.readManifestBytes();
			Manifest manifest = ManifestParser.parse(manifestBytes);
			assertEquals(manifest.id(), "beispiel.minimalpaket");
			assertEquals(manifest.category(), "texturen");

			try (InputStream textureStream = archive.openEntry("textures/example.png")) {
				byte[] textureBytes = textureStream.readAllBytes();
				assertPngSignature(textureBytes);
			}
		}
	}

	@Test
	public void multiResourcePackageExposesEveryEntryAsAnIndependentStream() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.multiResourcePackage(), "multi.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			assertTrue(archive.hasEntry("textures/gui/erste.png"));
			assertTrue(archive.hasEntry("sounds/klick.ogg"));

			byte[] langBytes = archive.readEntryBytes("lang/de_de.json");
			String lang = new String(langBytes, StandardCharsets.UTF_8);
			assertTrue(lang.contains("Hallo"), "muss den unveraenderten Inhalt der Sprachdatei liefern: " + lang);

			byte[] firstTexture = archive.readEntryBytes("textures/gui/erste.png");
			byte[] secondTexture = archive.readEntryBytes("textures/gui/zweite.png");
			assertPngSignature(firstTexture);
			assertPngSignature(secondTexture);
		}
	}

	// --- Vorschaubilder (Manifest-Feld "icon") ---------------------------

	@Test
	public void iconEntryNamedInManifestIsReadableAsAPreviewImage() throws IOException, ManifestParseException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgArchive.MANIFEST_ENTRY_NAME, manifestJsonWithIcon().getBytes(StandardCharsets.UTF_8));
		byte[] iconBytes = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 'i', 'c', 'o', 'n' };
		entries.put("icon.png", iconBytes);
		entries.put("textures/example.png", new byte[] {
				(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 't', 'e', 'x' });
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.buildZip(entries), "with-icon.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			byte[] manifestBytes = archive.readManifestBytes();
			Manifest manifest = ManifestParser.parse(manifestBytes);
			assertEquals(manifest.icon(), "icon.png");

			assertTrue(archive.hasEntry(manifest.icon()), "Vorschaubild-Pfad aus dem Manifest muss im Archiv stehen");
			byte[] readBackIcon = archive.readEntryBytes(manifest.icon());
			assertEquals(readBackIcon, iconBytes, "Vorschaubild muss byte-identisch aus dem Archiv gelesen werden");
			assertPngSignature(readBackIcon);
		}
	}

	// --- Eintragsliste traegt genug fuer eine spaetere P3-Pruefung -------

	@Test
	public void entriesExposeUncompressedAndCompressedSizeForFutureSecurityChecks() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.extremeCompressionRatioPackage(), "bomb.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgEntry bombEntry = archive.entries().stream()
					.filter(e -> e.name().equals("textures/bombe.png"))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Bomben-Eintrag fehlt in entries()"));

			assertEquals(bombEntry.size(), VpkgFixtures.EXTREME_UNCOMPRESSED_SIZE);
			assertTrue(bombEntry.compressedSize() < bombEntry.size() / 100,
					"entries() muss die komprimierte Groesse liefern, damit P3 das Verhaeltnis pruefen kann, "
							+ "ohne den Eintrag vorher zu entpacken: compressed=" + bombEntry.compressedSize()
							+ " uncompressed=" + bombEntry.size());
		}
	}

	// --- fehlender Eintrag --------------------------------------------

	@Test
	public void openingMissingEntryFailsWithAClearMessage() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.minimalValidPackage(), "minimal-for-missing.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			archive.openEntry("does/not/exist.png");
			fail("nicht vorhandener Eintrag haette eine IOException werfen muessen");
		} catch (FileNotFoundException e) {
			assertTrue(e.getMessage().contains("does/not/exist.png"),
					"Meldung muss den fehlenden Pfad nennen: " + e.getMessage());
		}
	}

	// --- GATE31: Deckel beim tatsaechlichen Lesen (Zip-Bomben-Schutz) -----

	/**
	 * {@code VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage()} deklariert
	 * im zentralen Verzeichnis nur {@code VpkgFixtures.FORGED_DECLARED_SIZE_BYTES}
	 * (10) Bytes, liefert beim tatsaechlichen Lesen aber
	 * {@code VpkgFixtures.FORGED_REAL_CONTENT_SIZE} (64 KiB) Bytes -- genau der
	 * GATE30/GATE31-Befund ("klein deklariert, riesig real"). Ein kuenstlich
	 * kleiner Deckel (1 KiB, package-private Test-Ueberladung) deckt das auf,
	 * ohne ein hunderte-Megabyte-Fixture zu brauchen: {@code readEntryBytes}
	 * wirft eine gefangene {@link VpkgEntryTooLargeException} statt entweder
	 * unbegrenzt zu lesen oder ein {@link OutOfMemoryError} zu riskieren.
	 */
	@Test
	public void readingEntryBeyondCapThrowsCaughtExceptionInsteadOfUnboundedRead() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage(), "forged-size.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			long tinyCap = 1024L;
			try {
				archive.readEntryBytes("textures/klein-deklariert-real-riesig.txt", tinyCap);
				fail("Lesen ueber den Deckel haette VpkgEntryTooLargeException werfen muessen");
			} catch (VpkgEntryTooLargeException e) {
				assertTrue(e.getMessage().contains(String.valueOf(tinyCap)),
						"Meldung muss den ueberschrittenen Deckel nennen: " + e.getMessage());
			}
		}
	}

	/**
	 * Wie {@link #readingEntryBeyondCapThrowsCaughtExceptionInsteadOfUnboundedRead()},
	 * aber direkt am {@link InputStream} von {@link VpkgArchive#openEntry(String, long)}
	 * geprueft -- der Deckel greift schon beim Lesen des Streams (wie beim
	 * Download {@code BoundedBody}), nicht erst hinterher gegen die deklarierte
	 * Groesse.
	 */
	@Test
	public void openEntryStreamThrowsAsSoonAsCapIsExceeded() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage(),
				"forged-size-stream.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			long tinyCap = 100L;
			try (InputStream in = archive.openEntry("textures/klein-deklariert-real-riesig.txt", tinyCap)) {
				in.readAllBytes();
				fail("Stream haette ueber dem Deckel VpkgEntryTooLargeException werfen muessen");
			} catch (VpkgEntryTooLargeException expected) {
				// erwartet -- der Deckel greift am Stream.
			}
		}
	}

	/** Gegenprobe: innerhalb des Deckels liest {@code readEntryBytes} unveraendert vollstaendig. */
	@Test
	public void readingEntryWithinCapStillReturnsCompleteContent() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.minimalValidPackage(), "within-cap.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			byte[] textureBytes = archive.readEntryBytes("textures/example.png", 1024L);
			assertPngSignature(textureBytes);
		}
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private Path writeFixtureToTempFile(byte[] vpkgBytes, String fileName) throws IOException {
		Path file = tempDir.resolve(fileName);
		Files.write(file, vpkgBytes);
		return file;
	}

	private static void assertPngSignature(byte[] bytes) {
		assertTrue(bytes.length >= 8, "PNG-Signatur braucht mindestens 8 Bytes, hat aber " + bytes.length);
		byte[] expected = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		for (int i = 0; i < expected.length; i++) {
			assertEquals(bytes[i], expected[i], "Byte " + i + " weicht von der PNG-Signatur ab");
		}
	}

	private static String manifestJsonWithIcon() {
		return "{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"beispiel.mitvorschau\",\n"
				+ "  \"name\": \"Paket mit Vorschau\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"texturen\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"icon\": \"icon.png\"\n"
				+ "}\n";
	}
}
