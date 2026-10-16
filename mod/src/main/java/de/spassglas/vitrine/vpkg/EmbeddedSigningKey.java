package de.spassglas.vitrine.vpkg;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.PublicKey;

/**
 * Der oeffentliche Ed25519-Signaturschluessel der Vitrine-Registry, fest im
 * Jar eingebettet (Charter: "Oeffentlicher Signaturschluessel liegt im Jar,
 * der private nie im Repo" -- ROADMAP G3, Technische Festlegungen Zeile
 * "Oeffentlicher Signaturschluessel liegt im Jar").
 *
 * <p>Der zugehoerige private Schluessel wurde einmalig lokal erzeugt (JDK-
 * Bordmittel {@code KeyPairGenerator.getInstance("Ed25519")}, keine neue
 * Abhaengigkeit) und liegt <b>ausserhalb</b> dieses Repos unter
 * {@code %USERPROFILE%\.vitrine\signing-key} (PKCS8-PEM) -- siehe
 * ALLOWED-REMOTES.md: lokal erzeugen und benutzen ist Loop-Arbeit, nur das
 * spaetere Eintragen des privaten Teils in die GitHub-Secrets der Registry
 * ist umgekocht's Schritt. Diese Klasse kennt nur den <em>oeffentlichen</em>
 * Teil.</p>
 *
 * <p>Die eingebettete Resource liegt bewusst neben dieser Klasse im selben
 * Package (gleiches Muster wie {@code ThemeSchemaValidator}/{@code
 * ManifestSchemaValidator} fuer eingebettete Schema-Kopien) als rohe
 * X.509-SubjectPublicKeyInfo-DER-Bytes -- exakt das Format, das
 * {@link PublicKey#getEncoded()} liefert und
 * {@link VpkgIntegrityValidator#decodeEd25519PublicKey(byte[])} erwartet.</p>
 */
public final class EmbeddedSigningKey {

	/** Resource-Name der eingebetteten, rohen X.509-DER-Bytes, im selben Package. */
	static final String RESOURCE_NAME = "registry-signing-key.pub";

	private static volatile PublicKey cached;

	private EmbeddedSigningKey() {
	}

	/**
	 * Laedt den eingebetteten oeffentlichen Registry-Schluessel einmalig aus
	 * dem Klassenpfad und dekodiert ihn ueber
	 * {@link VpkgIntegrityValidator#decodeEd25519PublicKey(byte[])}.
	 *
	 * @throws IllegalStateException wenn die Resource im Jar fehlt oder sich
	 *                                nicht als Ed25519-Schluessel dekodieren
	 *                                laesst -- ein kaputter Build, kein
	 *                                normaler Laufzeitfall.
	 */
	public static PublicKey load() {
		PublicKey key = cached;
		if (key != null) {
			return key;
		}
		synchronized (EmbeddedSigningKey.class) {
			if (cached == null) {
				cached = decode(readResourceBytes());
			}
			return cached;
		}
	}

	// GATE110: frueher gab es hier `overrideForTesting(PublicKey)`, abgesichert
	// durch die Systemeigenschaft "vitrine.allowTestSigningKey" -- das Loch
	// dabei war, dass JEDER Code in derselben JVM diese Eigenschaft zur
	// Laufzeit per System.setProperty(...) selbst setzen und damit den
	// Vertrauensanker austauschen konnte (critic-security hat das nachgestellt).
	// Der Ersatz lebt jetzt ausschliesslich in
	// src/test/java/.../vpkg/SigningKeyTestSupport.java -- einer Klasse, die
	// NIE ins ausgelieferte Jar kompiliert wird (Gradle baut src/test nicht in
	// das Haupt-Artefakt) und die das private `cached`-Feld dieser Klasse per
	// Reflection direkt setzt. src/main kennt damit ueberhaupt keine Methode
	// mehr, mit der sich der eingebettete Schluessel zur Laufzeit ersetzen
	// liesse -- unabhaengig von jeder Systemeigenschaft.

	private static byte[] readResourceBytes() {
		try (InputStream in = EmbeddedSigningKey.class.getResourceAsStream(RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebetteter Signaturschluessel '" + RESOURCE_NAME
						+ "' im Package " + EmbeddedSigningKey.class.getPackageName() + " fehlt -- Build defekt.");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(
					"Eingebetteter Signaturschluessel '" + RESOURCE_NAME + "' liess sich nicht lesen", e);
		}
	}

	private static PublicKey decode(byte[] der) {
		try {
			return VpkgIntegrityValidator.decodeEd25519PublicKey(der);
		} catch (VpkgIntegrityException e) {
			throw new IllegalStateException(
					"Eingebetteter Signaturschluessel '" + RESOURCE_NAME + "' ist kein gueltiger Ed25519-Schluessel -- "
							+ "Build defekt: " + e.getMessage(), e);
		}
	}
}
