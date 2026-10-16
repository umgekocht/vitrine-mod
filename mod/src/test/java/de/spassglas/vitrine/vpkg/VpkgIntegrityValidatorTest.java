package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt das Fertig-Kriterium von P4 ab: "Test mit gueltiger, ungueltiger und
 * fehlender Signatur." Jeder Fall bekommt einen eigenen Test, der beweist,
 * dass {@link VpkgIntegrityValidator#validate} das richtige Urteil faellt und
 * {@link VpkgIntegrityValidator#requireValid} bei jedem Verstoss eine
 * {@link VpkgIntegrityException} wirft -- nie stillschweigend durchlaesst.
 */
public final class VpkgIntegrityValidatorTest {

	private Path tempDir;
	private byte[] packageBytes;
	private Path packageFile;
	private String correctChecksum;
	private KeyPair signerKeyPair;
	private KeyPair attackerKeyPair;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-vpkg-integrity-test-");
		packageBytes = VpkgFixtures.minimalValidPackage();
		packageFile = tempDir.resolve("package.vpkg");
		Files.write(packageFile, packageBytes);
		correctChecksum = VpkgFixtures.sha256Hex(packageBytes);
		signerKeyPair = VpkgFixtures.generateEd25519KeyPair();
		attackerKeyPair = VpkgFixtures.generateEd25519KeyPair();
	}

	@AfterMethod
	public void tearDown() throws IOException {
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

	// --- Fall 1: gueltige Signatur -----------------------------------------

	@Test
	public void validChecksumAndSignatureAreAccepted() throws VpkgIntegrityException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		PublicKey publicKey = signerKeyPair.getPublic();

		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, signature, publicKey);
		assertTrue(report.isValid(), "gueltige Pruefsumme + Signatur muessen akzeptiert werden: " + report.violations());

		VpkgIntegrityValidator.requireValid(packageFile, correctChecksum, signature, publicKey);
	}

	@Test
	public void sha256HexMatchesActualFileContent() throws IOException {
		String computed = VpkgIntegrityValidator.sha256Hex(packageFile);
		assertEquals(computed, correctChecksum, "sha256Hex() muss dieselbe Pruefsumme liefern wie das Test-Fixture");
	}

	// --- Fall 2: ungueltige/manipulierte Signatur --------------------------

	@Test
	public void tamperedSignatureBytesAreRejected() {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		byte[] tampered = signature.clone();
		tampered[0] ^= 0xFF; // ein einziges gekipptes Bit reicht, um Ed25519 scheitern zu lassen
		PublicKey publicKey = signerKeyPair.getPublic();

		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, tampered, publicKey);
		assertFalse(report.isValid(), "manipulierte Signatur-Bytes muessen abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.SIGNATURE_INVALID);

		assertRequireValidThrows(correctChecksum, tampered, publicKey, VpkgIntegrityViolationType.SIGNATURE_INVALID);
	}

	@Test
	public void signatureFromWrongKeyPairIsRejected() {
		// mit dem falschen (Angreifer-)Schluessel signiert, aber gegen den
		// echten oeffentlichen Schluessel geprueft -- muss scheitern.
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, attackerKeyPair.getPrivate());
		PublicKey publicKey = signerKeyPair.getPublic();

		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, signature, publicKey);
		assertFalse(report.isValid(), "Signatur eines fremden Schluessels muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.SIGNATURE_INVALID);

		assertRequireValidThrows(correctChecksum, signature, publicKey, VpkgIntegrityViolationType.SIGNATURE_INVALID);
	}

	@Test
	public void tamperedFileContentInvalidatesSignature() throws IOException {
		// Datei nach dem Signieren veraendert -- Pruefsumme UND Signatur
		// muessen beide nicht mehr zur (unveraenderten) erwarteten Pruefsumme passen.
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		byte[] tamperedContent = packageBytes.clone();
		tamperedContent[tamperedContent.length - 1] ^= 0xFF;
		Files.write(packageFile, tamperedContent);
		PublicKey publicKey = signerKeyPair.getPublic();

		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, signature, publicKey);
		assertFalse(report.isValid(), "veraenderter Dateiinhalt muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.CHECKSUM_MISMATCH);
	}

	// --- Fall 3: fehlende Signatur -------------------------------------------

	@Test
	public void nullSignatureIsRejected() {
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, null,
				signerKeyPair.getPublic());
		assertFalse(report.isValid(), "fehlende (null) Signatur muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.SIGNATURE_MISSING);

		assertRequireValidThrows(correctChecksum, null, signerKeyPair.getPublic(),
				VpkgIntegrityViolationType.SIGNATURE_MISSING);
	}

	@Test
	public void emptySignatureIsRejected() {
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, new byte[0],
				signerKeyPair.getPublic());
		assertFalse(report.isValid(), "leere Signatur muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.SIGNATURE_MISSING);
	}

	// --- weitere Verstoesse: fehlende Pruefsumme, fehlender Schluessel ------

	@Test
	public void missingExpectedChecksumIsRejected() {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, null, signature,
				signerKeyPair.getPublic());
		assertFalse(report.isValid(), "fehlende erwartete Pruefsumme muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.CHECKSUM_MISSING);
	}

	@Test
	public void mismatchedChecksumIsRejected() {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		String wrongChecksum = "0".repeat(64);
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, wrongChecksum, signature,
				signerKeyPair.getPublic());
		assertFalse(report.isValid(), "falsche erwartete Pruefsumme muss abgelehnt werden");
		assertHasViolation(report, VpkgIntegrityViolationType.CHECKSUM_MISMATCH);
	}

	@Test
	public void missingPublicKeyIsRejected() {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, signature, null);
		assertFalse(report.isValid(), "fehlender oeffentlicher Schluessel muss abgelehnt werden, obwohl eine Signatur vorliegt");
		assertHasViolation(report, VpkgIntegrityViolationType.PUBLIC_KEY_MISSING);
	}

	@Test
	public void unreadableFileIsRejectedNotThrown() {
		Path missing = tempDir.resolve("does-not-exist.vpkg");
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());

		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(missing, correctChecksum, signature,
				signerKeyPair.getPublic());
		assertFalse(report.isValid(), "fehlende Datei muss als Verstoss gemeldet werden, nicht als unbehandelte Exception");
		assertHasViolation(report, VpkgIntegrityViolationType.FILE_UNREADABLE);
	}

	@Test
	public void decodedX509PublicKeyVerifiesSignatureProducedByMatchingPrivateKey() throws VpkgIntegrityException {
		byte[] encodedPublicKey = signerKeyPair.getPublic().getEncoded();
		PublicKey decoded = VpkgIntegrityValidator.decodeEd25519PublicKey(encodedPublicKey);

		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signerKeyPair.getPrivate());
		VpkgIntegrityReport report = VpkgIntegrityValidator.validate(packageFile, correctChecksum, signature, decoded);
		assertTrue(report.isValid(), "aus X.509-Bytes dekodierter Schluessel muss dieselbe Signatur pruefen koennen: "
				+ report.violations());
	}

	// --- Hilfsfunktionen ----------------------------------------------------

	private void assertHasViolation(VpkgIntegrityReport report, VpkgIntegrityViolationType expectedType) {
		boolean hasExpectedType = report.violations().stream().anyMatch(v -> v.type() == expectedType);
		assertTrue(hasExpectedType,
				"Report haette " + expectedType + " melden muessen, gemeldet wurden: " + report.violations());
	}

	private void assertRequireValidThrows(String checksum, byte[] signature, PublicKey publicKey,
			VpkgIntegrityViolationType expectedType) {
		try {
			VpkgIntegrityValidator.requireValid(packageFile, checksum, signature, publicKey);
			fail("requireValid() haette eine VpkgIntegrityException werfen muessen");
		} catch (VpkgIntegrityException e) {
			boolean hasExpectedType = e.violations().stream().anyMatch(v -> v.type() == expectedType);
			assertTrue(hasExpectedType,
					"Exception haette " + expectedType + " enthalten muessen, enthielt: " + e.violations());
		}
	}
}
