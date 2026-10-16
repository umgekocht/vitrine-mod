package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt das Fertig-Kriterium von P3 ab: "je ein Test pro Angriff, alle
 * weisen ab; kein Angriff schreibt eine Datei ausserhalb des
 * Sandbox-Ordners." Jeder Angriffstyp bekommt einen eigenen Test, der
 * beweist, dass {@link VpkgSecurityValidator#validate(VpkgArchive)} ihn mit
 * dem passenden {@link VpkgSecurityViolationType} ablehnt und
 * {@link VpkgSecurityValidator#requireSafe(VpkgArchive)} eine
 * {@link VpkgSecurityException} wirft. Zusaetzlich: die gutartigen Fixtures
 * muessen sauber durchgehen, und keiner der Angriffe darf irgendwo ausserhalb
 * des eigenen Test-Sandbox-Ordners eine Datei anlegen (rein lesende
 * Pruefung).
 */
public final class VpkgSecurityValidatorTest {

	private Path tempDir;

	@BeforeMethod
	public void createTempDir() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-vpkg-security-test-");
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

	// --- gutartige Fixtures muessen sauber durchgehen ---------------------

	@Test
	public void benignFixturesPassValidationCleanly() throws IOException, VpkgSecurityException {
		Path minimal = writeFixtureToTempFile(VpkgFixtures.minimalValidPackage(), "minimal.vpkg");
		Path multi = writeFixtureToTempFile(VpkgFixtures.multiResourcePackage(), "multi.vpkg");

		try (VpkgArchive minimalArchive = VpkgArchive.open(minimal);
				VpkgArchive multiArchive = VpkgArchive.open(multi)) {
			VpkgSecurityReport minimalReport = VpkgSecurityValidator.validate(minimalArchive);
			assertTrue(minimalReport.isSafe(), "minimalValidPackage() muss sauber sein: " + minimalReport.violations());
			VpkgSecurityValidator.requireSafe(minimalArchive);

			VpkgSecurityReport multiReport = VpkgSecurityValidator.validate(multiArchive);
			assertTrue(multiReport.isSafe(), "multiResourcePackage() muss sauber sein: " + multiReport.violations());
			VpkgSecurityValidator.requireSafe(multiArchive);
		}
	}

	// --- ein Test je Angriffsart ------------------------------------------

	@Test
	public void zipSlipTraversalIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.zipSlipPackage(), "zip-slip.vpkg", VpkgSecurityViolationType.PATH_TRAVERSAL);
	}

	@Test
	public void absolutePathEntryIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.absolutePathEntryPackage(), "absolute-path.vpkg",
				VpkgSecurityViolationType.ABSOLUTE_PATH);
	}

	// --- GATE108: Traversal-Varianten jenseits des woertlichen ".." ---------

	@Test
	public void dotsOnlyTraversalSegmentIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.dotsOnlyTraversalPackage(), "dots-only-traversal.vpkg",
				VpkgSecurityViolationType.PATH_TRAVERSAL);
	}

	@Test
	public void reservedWindowsDeviceNameIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.reservedWindowsDeviceNamePackage(), "reserved-device-name.vpkg",
				VpkgSecurityViolationType.UNSAFE_ENTRY_NAME);
	}

	@Test
	public void symlinkEntryIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.symlinkEntryPackage(), "symlink.vpkg",
				VpkgSecurityViolationType.NON_REGULAR_ENTRY);
	}

	@Test
	public void totalUncompressedSizeOverLimitIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.totalUncompressedSizeExceededPackage(), "total-size.vpkg",
				VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED);
	}

	@Test
	public void compressionRatioOverLimitIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.extremeCompressionRatioPackage(), "ratio.vpkg",
				VpkgSecurityViolationType.COMPRESSION_RATIO_EXCEEDED);
	}

	@Test
	public void entryCountOverLimitIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.entryCountExceededPackage(), "entry-count.vpkg",
				VpkgSecurityViolationType.ENTRY_COUNT_EXCEEDED);
	}

	@Test
	public void forbiddenFileTypeIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.forbiddenFileTypePackage(), "forbidden-type.vpkg",
				VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE);
	}

	@Test
	public void oversizedPngDimensionsAreRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.oversizedPngDimensionsPackage(), "png-dimensions.vpkg",
				VpkgSecurityViolationType.PNG_DIMENSIONS_EXCEEDED);
	}

	@Test
	public void invalidPngSignatureIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.invalidPngSignaturePackage(), "png-signature.vpkg",
				VpkgSecurityViolationType.PNG_HEADER_INVALID);
	}

	// --- GATE111: Massenpruefung gilt auch fuer JPEG, nicht nur PNG ---------

	@Test
	public void oversizedJpegDimensionsAreRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.oversizedJpegDimensionsPackage(), "jpg-dimensions.vpkg",
				VpkgSecurityViolationType.JPEG_DIMENSIONS_EXCEEDED);
	}

	@Test
	public void invalidJpegSignatureIsRejected() throws IOException {
		assertRejectedWith(VpkgFixtures.invalidJpegSignaturePackage(), "jpg-signature.vpkg",
				VpkgSecurityViolationType.JPEG_HEADER_INVALID);
	}

	// --- GATE30: Deckel zaehlt tatsaechlich gelesene Bytes am Stream --------

	/**
	 * {@code VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage()} deklariert
	 * im zentralen Verzeichnis nur 10 Bytes, liefert beim tatsaechlichen Lesen
	 * aber 64 KiB -- der alte, rein deklarationsbasierte Groessencheck haette
	 * dieses Archiv als {@code safe=true} durchgewunken (genau der GATE30-
	 * Befund). Ein kuenstlich kleines Test-Budget (package-private
	 * {@code validate(archive, long)}-Ueberladung) deckt den Angriff auf, ohne
	 * ein hunderte-Megabyte-Fixture zu brauchen.
	 */
	@Test
	public void forgedCentralDirectoryWithSmallDeclaredButLargeRealSizeIsRejected() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage(),
				"forged-size.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			long tinyTotalBudget = 1024L;
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive, tinyTotalBudget);
			assertFalse(report.isSafe(), "gefaelschtes zentrales Verzeichnis (klein deklariert, real darueber) "
					+ "haette als unsicher erkannt werden muessen");
			boolean hasExpectedType = report.violations().stream()
					.anyMatch(v -> v.type() == VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED);
			assertTrue(hasExpectedType, "Verstoss haette TOTAL_UNCOMPRESSED_SIZE_EXCEEDED melden muessen, gemeldet: "
					+ report.violations());
		}
	}

	/**
	 * Gegenprobe: mit dem echten Produktionsbudget (256 MiB) bleibt dasselbe
	 * Fixture sauber -- der real 64 KiB grosse Inhalt liegt weit darunter. Der
	 * neue Deckel liest zwar tatsaechliche Bytes statt der (hier zu klein)
	 * deklarierten Groesse zu vertrauen, weist aber kein Paket grundlos ab,
	 * dessen realer Inhalt innerhalb des Budgets bleibt.
	 */
	@Test
	public void defaultBudgetAcceptsForgedDeclarationWhenRealContentStaysUnderTheLimit() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.forgedSmallDeclaredLargeRealSizePackage(),
				"forged-size-default-budget.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive);
			assertTrue(report.isSafe(), "64 KiB realer Inhalt liegt weit unter dem 256-MiB-Produktionsbudget: "
					+ report.violations());
		}
	}

	// --- GATE107: Verzeichnis-Namensmuster ("endet auf /") darf die Groessenpruefung nicht umgehen ---

	/**
	 * {@code VpkgFixtures.directoryNamedEntryWithRealPayloadPackage()} nennt
	 * einen Eintrag {@code "evil/"} und deklariert 0 Bytes (wie ein echtes,
	 * immer leeres Verzeichnis), traegt aber tatsaechlich 64 KiB echten
	 * Inhalt. Vor GATE107 ueberspringt {@code checkActualUncompressedSize}
	 * jeden Eintrag, dessen Name auf {@code /} endet, komplett -- dieser
	 * Eintrag waere nie tatsaechlich gelesen, geschweige denn gegen ein
	 * Budget gezaehlt worden. Ein kuenstlich kleines Test-Budget (dieselbe
	 * package-private Ueberladung wie beim GATE30-Test) deckt das auf, ohne
	 * dass das Fixture hunderte MiB real enthalten muesste.
	 */
	@Test
	public void directoryNamedEntryWithRealPayloadIsRejectedRegardlessOfTrailingSlashName() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.directoryNamedEntryWithRealPayloadPackage(),
				"directory-named-payload.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			long tinyTotalBudget = 1024L;
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive, tinyTotalBudget);
			assertFalse(report.isSafe(), "ein auf '/' endender Eintrag mit echtem Inhalt haette trotz seines "
					+ "Verzeichnis-aehnlichen Namens als unsicher erkannt werden muessen");
			boolean hasExpectedType = report.violations().stream()
					.anyMatch(v -> v.type() == VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED);
			assertTrue(hasExpectedType, "Verstoss haette TOTAL_UNCOMPRESSED_SIZE_EXCEEDED melden muessen, gemeldet: "
					+ report.violations());
		}
	}

	// --- GATE109: STORED-Eintraege werden anhand der deklarierten Groesse geprueft, nie geoeffnet ---

	/**
	 * {@code VpkgFixtures.storedEntryDeclaringMoreThanPhysicallyPresentPackage()}
	 * deklariert fuer einen STORED-Eintrag {@code 5.000.000} Bytes, obwohl nur
	 * 16 Bytes physisch im Archiv liegen. Tatsaechliches Oeffnen/Lesen dieses
	 * Eintrags scheitert nachweislich mit einer {@link IOException} (zweiter
	 * Teil dieses Tests) -- {@code checkActualUncompressedSize} darf diesen
	 * Eintrag deshalb fuer GATE109 NIE oeffnen, sondern muss die deklarierte
	 * Groesse direkt vertrauen (ZIP-Garantie: STORED bedeutet
	 * {@code compressedSize == size}). Bleibt die deklarierte Groesse unter
	 * dem Budget, muss das Paket als sicher durchgehen, trotz des Fehlers, der
	 * beim tatsaechlichen Oeffnen auftreten wuerde.
	 */
	@Test
	public void storedEntryIsTrustedByDeclaredSizeWithoutBeingOpened() throws IOException {
		Path vpkgFile = writeFixtureToTempFile(VpkgFixtures.storedEntryDeclaringMoreThanPhysicallyPresentPackage(),
				"stored-trust.vpkg");

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			// Beweis Nr. 1: tatsaechliches Oeffnen/Lesen dieses Eintrags scheitert --
			// es gibt physisch zu wenige Bytes fuer die deklarierte STORED-Groesse.
			assertThrows(IOException.class, () -> {
				try (InputStream in = archive.openEntry(VpkgFixtures.STORED_TRUST_ENTRY_NAME,
						VpkgFixtures.STORED_TRUST_DECLARED_SIZE_BYTES)) {
					in.readAllBytes();
				}
			});

			// Beweis Nr. 2: die volle P3-Pruefung oeffnet diesen Eintrag trotzdem
			// NICHT -- sonst waere das Paket (mit demselben Fehler) als unsicher
			// markiert worden, statt als innerhalb des Budgets sauber zu gelten.
			long budget = 10_000_000L; // ueber der deklarierten Groesse (5.000.000)
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive, budget);
			assertTrue(report.isSafe(), "GATE109: ein STORED-Eintrag muss anhand seiner deklarierten Groesse "
					+ "geprueft werden, ohne tatsaechlich geoeffnet zu werden: " + report.violations());
		}
	}

	// --- kein Angriff schreibt jemals ausserhalb der Sandbox ---------------

	/**
	 * Nimmt eine Momentaufnahme der obersten Ebene des System-Temp-Verzeichnisses
	 * vor und nach dem Pruefen JEDES boesartigen Fixtures (validate() UND
	 * requireSafe(), jeweils bis zum Ende durchlaufen bzw. mit try/catch
	 * abgefangen). Der eigene Sandbox-Ordner {@code tempDir} existiert schon
	 * vor der ersten Momentaufnahme (angelegt in {@link #createTempDir()}) und
	 * alle Fixture-Dateien landen als Unterdateien darin -- sie tauchen in der
	 * obersten Ebene also gar nicht erst auf. Wenn danach trotzdem ein NEUER
	 * Eintrag in der obersten Ebene erscheint, hat irgendein Pruefschritt
	 * ausserhalb der eigenen Sandbox geschrieben.
	 */
	@Test
	public void noAttackWritesAnyFileOutsideTheSandboxDirectory() throws IOException {
		Path systemTempRoot = Path.of(System.getProperty("java.io.tmpdir"));
		Set<Path> before = listTopLevelEntries(systemTempRoot);

		List<NamedFixture> maliciousFixtures = List.of(
				new NamedFixture("zip-slip.vpkg", VpkgFixtures.zipSlipPackage()),
				new NamedFixture("absolute-path.vpkg", VpkgFixtures.absolutePathEntryPackage()),
				new NamedFixture("symlink.vpkg", VpkgFixtures.symlinkEntryPackage()),
				new NamedFixture("total-size.vpkg", VpkgFixtures.totalUncompressedSizeExceededPackage()),
				new NamedFixture("ratio.vpkg", VpkgFixtures.extremeCompressionRatioPackage()),
				new NamedFixture("entry-count.vpkg", VpkgFixtures.entryCountExceededPackage()),
				new NamedFixture("forbidden-type.vpkg", VpkgFixtures.forbiddenFileTypePackage()),
				new NamedFixture("png-dimensions.vpkg", VpkgFixtures.oversizedPngDimensionsPackage()),
				new NamedFixture("png-signature.vpkg", VpkgFixtures.invalidPngSignaturePackage()));

		for (NamedFixture fixture : maliciousFixtures) {
			Path file = writeFixtureToTempFile(fixture.bytes(), fixture.fileName());
			try (VpkgArchive archive = VpkgArchive.open(file)) {
				VpkgSecurityReport report = VpkgSecurityValidator.validate(archive);
				assertFalse(report.isSafe(), fixture.fileName() + " haette abgelehnt werden muessen");

				try {
					VpkgSecurityValidator.requireSafe(archive);
					fail(fixture.fileName() + ": requireSafe() haette werfen muessen");
				} catch (VpkgSecurityException expected) {
					assertFalse(expected.violations().isEmpty());
				}
			}
		}

		Set<Path> after = listTopLevelEntries(systemTempRoot);
		Set<Path> newlyCreated = new HashSet<>(after);
		newlyCreated.removeAll(before);
		assertTrue(newlyCreated.isEmpty(),
				"keine der Pruefungen darf ausserhalb der eigenen Sandbox etwas anlegen, neu aufgetaucht: "
						+ newlyCreated);
	}

	private record NamedFixture(String fileName, byte[] bytes) {
	}

	private static Set<Path> listTopLevelEntries(Path directory) throws IOException {
		try (var paths = Files.list(directory)) {
			return paths.collect(Collectors.toCollection(HashSet::new));
		}
	}

	// --- Hilfsfunktionen ----------------------------------------------------

	private void assertRejectedWith(byte[] vpkgBytes, String fileName, VpkgSecurityViolationType expectedType)
			throws IOException {
		Path vpkgFile = writeFixtureToTempFile(vpkgBytes, fileName);

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive);
			assertFalse(report.isSafe(), fileName + " haette als unsicher erkannt werden muessen");
			boolean hasExpectedType = report.violations().stream().anyMatch(v -> v.type() == expectedType);
			assertTrue(hasExpectedType, fileName + " haette " + expectedType + " melden muessen, gemeldet wurden: "
					+ report.violations());

			try {
				VpkgSecurityValidator.requireSafe(archive);
				fail(fileName + ": requireSafe() haette eine VpkgSecurityException werfen muessen");
			} catch (VpkgSecurityException e) {
				boolean exceptionHasExpectedType = e.violations().stream().anyMatch(v -> v.type() == expectedType);
				assertTrue(exceptionHasExpectedType, fileName + ": Exception haette " + expectedType
						+ " enthalten muessen, enthielt: " + e.violations());
			}
		}
	}

	private Path writeFixtureToTempFile(byte[] vpkgBytes, String fileName) throws IOException {
		Path file = tempDir.resolve(fileName);
		Files.write(file, vpkgBytes);
		return file;
	}
}
