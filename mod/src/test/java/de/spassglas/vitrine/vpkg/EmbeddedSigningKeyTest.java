package de.spassglas.vitrine.vpkg;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

/**
 * ROADMAP G3: der im Jar eingebettete oeffentliche Registry-Signaturschluessel
 * muss auf jedem Rechner (ohne den lokal erzeugten privaten Teil unter
 * {@code %USERPROFILE%\.vitrine\signing-key}) ladbar und als echter
 * Ed25519-Schluessel benutzbar sein -- Charter-Regel 12 ("Es laeuft beim
 * Fremden"). Dieser Test haengt deshalb NICHT von einer lokalen
 * Schluesseldatei ab, sondern nur von der eingebetteten Jar-Resource.
 */
public final class EmbeddedSigningKeyTest {

	@Test
	public void loadDecodesEmbeddedResourceAsEd25519PublicKey() {
		PublicKey key = EmbeddedSigningKey.load();
		assertNotNull(key);
		// JDKs KeyFactory.generatePublic(...) fuer den Algorithmus "Ed25519"
		// liefert einen Schluessel, dessen getAlgorithm() den allgemeineren
		// Namen "EdDSA" traegt -- Signature.getInstance("Ed25519") akzeptiert
		// ihn trotzdem (siehe naechster Test), das ist die eigentliche Probe.
		assertEquals(key.getAlgorithm(), "EdDSA");
	}

	@Test
	public void loadIsCachedAcrossCalls() {
		assertSame(EmbeddedSigningKey.load(), EmbeddedSigningKey.load());
	}

	@Test
	public void embeddedKeyRejectsASignatureItDidNotProduce() throws Exception {
		// Beweist, dass der eingebettete Schluessel tatsaechlich zum Pruefen
		// taugt (nicht nur dekodierbar ist): eine Signatur von einem fremden,
		// zur Laufzeit erzeugten Schluesselpaar muss gegen ihn scheitern.
		var foreignPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		byte[] message = "vitrine-g3-embedded-key-test".getBytes(StandardCharsets.UTF_8);
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(foreignPair.getPrivate());
		signer.update(message);
		byte[] foreignSignature = signer.sign();

		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(EmbeddedSigningKey.load());
		verifier.update(message);
		assertFalse(verifier.verify(foreignSignature));
	}

	@Test(expectedExceptions = VpkgIntegrityException.class)
	public void decodeEd25519PublicKeyRejectsGarbageLikeEmbeddedLoaderWould() throws VpkgIntegrityException {
		VpkgIntegrityValidator.decodeEd25519PublicKey(new byte[] { 1, 2, 3 });
	}
}
