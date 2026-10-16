package de.spassglas.vitrine.vpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Prueft SHA-256-Pruefsumme und Ed25519-Signatur eines {@code .vpkg}-Pakets
 * (ROADMAP P4, Charter-Regel 3: "Sicherheit vor Bequemlichkeit ... Pruefsumme
 * und Signatur werden vor dem Anwenden geprueft", PLAN.md §6: "Signatur
 * (ed25519) und SHA-256 vor dem Anwenden, nicht danach").
 *
 * <p><b>Was genau gehasht/signiert wird:</b> die vollstaendigen rohen Bytes
 * der {@code .vpkg}-Datei auf der Platte (das gesamte ZIP, nicht entpackt,
 * nicht kanonikalisiert) -- dieselben Bytes, die spaeter im Registry-Index
 * unter {@code sha256} stehen und die ein Autor beim Veroeffentlichen einmal
 * signiert (PLAN.md §4: "Der Index verweist mit URL, Groesse, SHA-256 und
 * Signatur"). Die Ed25519-Signatur selbst liegt nicht ueber der ganzen Datei,
 * sondern ueber deren 32-Byte-SHA-256-Digest -- das ist derselbe Wert, den
 * ein Aufrufer ohnehin schon fuer die Pruefsummenpruefung braucht, spart also
 * ein zweites vollstaendiges Einlesen der Datei, und bindet die Signatur
 * eindeutig an exakt den Inhalt, dessen Pruefsumme geprueft wurde.</p>
 *
 * <p><b>Woher Pruefsumme, Signatur und oeffentlicher Schluessel kommen</b> ist
 * bewusst nicht Sache dieser Klasse: Sie werden NICHT innerhalb des
 * {@code .vpkg} selbst mitgefuehrt (ein Paket koennte sich sonst selbst eine
 * beliebige "gueltige" Pruefsumme mitgeben), sondern kommen von aussen -- aus
 * dem Registry-Index (K1-K3, spaeter) bzw. von umgekocht's oeffentlichem
 * Signaturschluessel im Jar (Charter-Regel: "Oeffentlicher Signaturschluessel
 * liegt im Jar"). Diese Klasse ist die reine Pruef-Primitive, die K3
 * (Download-Verifikation) und ein spaeterer lokaler Installer gleichermassen
 * aufrufen.</p>
 *
 * <p><b>Default ist Ablehnung</b> (Charter-Regel 3 und 4): fehlt die erwartete
 * Pruefsumme, die Signatur oder der oeffentliche Schluessel, gilt das Paket
 * NIE als sicher -- auch nicht "vorlaeufig" oder "mit Warnung". Genau wie
 * {@link VpkgSecurityValidator} liefert {@link #validate} nur ein
 * Ergebnisobjekt; {@link #requireValid} ist der EINE Aufruf, den jeder
 * spaetere Installer/Downloader vor dem Anwenden eines {@code .vpkg} machen
 * muss.</p>
 */
public final class VpkgIntegrityValidator {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgIntegrity");

	/** Algorithmus fuer die Paket-Pruefsumme. */
	public static final String CHECKSUM_ALGORITHM = "SHA-256";

	/**
	 * Algorithmus fuer die Paket-Signatur -- seit JDK 15 nativ ueber
	 * {@code java.security} verfuegbar (JDK 21, das hier verwendete
	 * Fabric/Java-Setup, hat ihn eingebaut), deshalb keine neue Abhaengigkeit.
	 */
	public static final String SIGNATURE_ALGORITHM = "Ed25519";

	private static final int STREAM_BUFFER_SIZE = 8192;

	private VpkgIntegrityValidator() {
	}

	/**
	 * Berechnet die SHA-256-Pruefsumme von {@code file} als Hex-Zeichenkette
	 * (Kleinbuchstaben), streamend -- die Datei wird nie komplett in den
	 * Speicher geladen.
	 */
	public static String sha256Hex(Path file) throws IOException {
		return HexFormat.of().formatHex(computeSha256Digest(file));
	}

	/**
	 * Prueft {@code vpkgFile} gegen die erwartete Pruefsumme und Signatur und
	 * liefert die vollstaendige Liste der gefundenen Verstoesse (leer, wenn
	 * beides gueltig ist). Liest dabei nur die rohen Dateibytes zum Hashen --
	 * kein ZIP-Parsing, kein Schreibzugriff.
	 *
	 * @param vpkgFile          Pfad zur {@code .vpkg}-Datei auf der Platte
	 * @param expectedSha256Hex erwartete SHA-256-Pruefsumme als Hex-Zeichenkette
	 *                            (z. B. aus dem Registry-Index), oder
	 *                            {@code null}/leer, wenn keine bekannt ist --
	 *                            das fuehrt IMMER zu einem Verstoss
	 * @param signature         Ed25519-Signatur ueber den SHA-256-Digest der
	 *                            Datei, oder {@code null}/leer, wenn keine
	 *                            vorliegt -- das fuehrt IMMER zu einem Verstoss
	 * @param signingPublicKey  oeffentlicher Ed25519-Schluessel, gegen den die
	 *                            Signatur geprueft wird, oder {@code null},
	 *                            wenn keiner bekannt ist -- das fuehrt IMMER zu
	 *                            einem Verstoss (solange eine Signatur vorliegt)
	 */
	public static VpkgIntegrityReport validate(Path vpkgFile, String expectedSha256Hex, byte[] signature,
			PublicKey signingPublicKey) {
		List<VpkgIntegrityViolation> violations = new ArrayList<>();

		byte[] actualDigest;
		try {
			actualDigest = computeSha256Digest(vpkgFile);
		} catch (IOException e) {
			LOGGER.warn("VpkgIntegrityValidator: '{}' liess sich nicht lesen: {}", vpkgFile, e.toString());
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.FILE_UNREADABLE,
					"Datei '" + vpkgFile + "' liess sich nicht lesen: " + e.getMessage()));
			return finish(vpkgFile, violations);
		}

		checkChecksum(actualDigest, expectedSha256Hex, violations);
		checkSignature(vpkgFile, actualDigest, signature, signingPublicKey, violations);

		return finish(vpkgFile, violations);
	}

	/**
	 * Wie {@link #validate}, wirft aber statt ein Ergebnisobjekt zurueckzugeben
	 * eine {@link VpkgIntegrityException}, sobald mindestens ein Verstoss
	 * vorliegt. Gedacht als der EINE Aufruf, den jeder spaetere
	 * Installer/Downloader eines {@code .vpkg} vor dem Anwenden machen muss.
	 */
	public static void requireValid(Path vpkgFile, String expectedSha256Hex, byte[] signature,
			PublicKey signingPublicKey) throws VpkgIntegrityException {
		VpkgIntegrityReport report = validate(vpkgFile, expectedSha256Hex, signature, signingPublicKey);
		if (!report.isValid()) {
			throw new VpkgIntegrityException(report.violations());
		}
	}

	/**
	 * Dekodiert einen X.509-kodierten (SubjectPublicKeyInfo, DER) oeffentlichen
	 * Ed25519-Schluessel, wie ihn {@code PublicKey#getEncoded()} liefert --
	 * gedacht fuer den im Jar mitgelieferten Signaturschluessel (Charter:
	 * "Oeffentlicher Signaturschluessel liegt im Jar").
	 */
	public static PublicKey decodeEd25519PublicKey(byte[] x509EncodedPublicKey) throws VpkgIntegrityException {
		try {
			KeyFactory factory = KeyFactory.getInstance(SIGNATURE_ALGORITHM);
			return factory.generatePublic(new X509EncodedKeySpec(x509EncodedPublicKey));
		} catch (GeneralSecurityException e) {
			LOGGER.warn("VpkgIntegrityValidator: oeffentlicher Ed25519-Schluessel liess sich nicht dekodieren: {}",
					e.toString());
			throw new VpkgIntegrityException(List.of(new VpkgIntegrityViolation(
					VpkgIntegrityViolationType.PUBLIC_KEY_MISSING,
					"Oeffentlicher Ed25519-Schluessel liess sich nicht dekodieren: " + e.getMessage())));
		}
	}

	// --- einzelne Pruefungen ---------------------------------------------

	private static void checkChecksum(byte[] actualDigest, String expectedSha256Hex,
			List<VpkgIntegrityViolation> violations) {
		if (expectedSha256Hex == null || expectedSha256Hex.isBlank()) {
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.CHECKSUM_MISSING,
					"Keine erwartete SHA-256-Pruefsumme angegeben -- ein Paket ohne bekannte Pruefsumme gilt nie als sicher"));
			return;
		}
		String actualHex = HexFormat.of().formatHex(actualDigest);
		if (!actualHex.equalsIgnoreCase(expectedSha256Hex.strip())) {
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.CHECKSUM_MISMATCH,
					"SHA-256-Pruefsumme stimmt nicht ueberein: erwartet '" + expectedSha256Hex.strip()
							+ "', tatsaechlich '" + actualHex + "'"));
		}
	}

	private static void checkSignature(Path vpkgFile, byte[] actualDigest, byte[] signature,
			PublicKey signingPublicKey, List<VpkgIntegrityViolation> violations) {
		if (signature == null || signature.length == 0) {
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.SIGNATURE_MISSING,
					"Keine Ed25519-Signatur angegeben -- ein unsigniertes Paket wird nie als sicher eingestuft "
							+ "(Charter-Regel 3: Sicherheit vor Bequemlichkeit)"));
			return;
		}
		if (signingPublicKey == null) {
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.PUBLIC_KEY_MISSING,
					"Keine oeffentlicher Schluessel zum Pruefen der Signatur vorhanden"));
			return;
		}
		try {
			Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
			verifier.initVerify(signingPublicKey);
			verifier.update(actualDigest);
			if (!verifier.verify(signature)) {
				violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.SIGNATURE_INVALID,
						"Ed25519-Signatur ist ungueltig -- Paket wurde nicht mit dem erwarteten Schluessel signiert "
								+ "oder wurde nachtraeglich veraendert"));
			}
		} catch (GeneralSecurityException e) {
			LOGGER.warn("VpkgIntegrityValidator: Signaturpruefung fuer '{}' schlug technisch fehl: {}", vpkgFile,
					e.toString());
			violations.add(new VpkgIntegrityViolation(VpkgIntegrityViolationType.SIGNATURE_INVALID,
					"Ed25519-Signatur liess sich nicht pruefen: " + e.getMessage()));
		}
	}

	private static VpkgIntegrityReport finish(Path vpkgFile, List<VpkgIntegrityViolation> violations) {
		VpkgIntegrityReport report = new VpkgIntegrityReport(violations);
		if (!report.isValid()) {
			LOGGER.warn("VpkgIntegrityValidator: Archiv '{}' abgelehnt, {} Verstoss/Verstoesse: {}",
					vpkgFile, violations.size(), violations);
		}
		return report;
	}

	private static byte[] computeSha256Digest(Path file) throws IOException {
		MessageDigest digest = newSha256Digest();
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[STREAM_BUFFER_SIZE];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
		return digest.digest();
	}

	private static MessageDigest newSha256Digest() {
		try {
			return MessageDigest.getInstance(CHECKSUM_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 ist laut JDK-Spezifikation in jeder konformen Implementierung
			// garantiert vorhanden -- ein Fehlen ist eine kaputte Laufzeitumgebung,
			// kein normaler Laufzeitfall.
			throw new IllegalStateException(
					CHECKSUM_ALGORITHM + " ist in dieser JDK-Laufzeitumgebung nicht verfuegbar", e);
		}
	}
}
