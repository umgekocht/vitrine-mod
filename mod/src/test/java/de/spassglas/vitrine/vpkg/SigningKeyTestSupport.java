package de.spassglas.vitrine.vpkg;

import java.lang.reflect.Field;
import java.security.PublicKey;
import java.util.Objects;

/**
 * GATE110: der einzige verbleibende Weg, den von {@link EmbeddedSigningKey}
 * zwischengespeicherten Vertrauensanker fuer einen Test durch ein frisches
 * Schluesselpaar zu ersetzen -- und zwar bewusst NICHT mehr ueber eine zur
 * Laufzeit umschaltbare Systemeigenschaft in {@code src/main}
 * (fruehere Fassung: {@code vitrine.allowTestSigningKey}, die jeder Code in
 * derselben JVM per {@code System.setProperty(...)} selbst setzen konnte,
 * siehe PROGRESS.md GATE110/critic-security).
 *
 * <p>Diese Klasse liegt ausschliesslich unter {@code src/test/java} und wird
 * deshalb nie in das ausgelieferte Jar kompiliert -- Produktionscode kann sie
 * also unter keinen Umstaenden erreichen, selbst wenn er versucht, eine
 * Systemeigenschaft oder sonstigen globalen Zustand zu manipulieren.
 * {@link EmbeddedSigningKey} selbst bringt dafuer keine oeffentliche (oder
 * sonstige) Methode mehr mit; diese Klasse setzt das private
 * {@code cached}-Feld direkt per Reflection, was nur innerhalb der Test-JVM
 * (und nur durch Testcode, der diese Klasse explizit importiert) passiert.</p>
 */
public final class SigningKeyTestSupport {

	private SigningKeyTestSupport() {
	}

	/**
	 * Ersetzt den zwischengespeicherten Schluessel durch {@code key}, bis
	 * {@link #reset()} aufgerufen wird. Der zum eingebetteten oeffentlichen
	 * Schluessel passende private Teil liegt laut Charter nie im Repo -- ein
	 * Test, der eine echte Ende-zu-Ende-Signaturpruefung ueben will, braucht
	 * deshalb ein eigenes Test-Schluesselpaar. Die Pruefung laeuft dabei ueber
	 * genau denselben Lesepfad wie in Produktion ({@link EmbeddedSigningKey#load()}).
	 */
	public static void install(PublicKey key) {
		setCached(Objects.requireNonNull(key, "key"));
	}

	/** Macht {@link #install(PublicKey)} rueckgaengig -- der naechste {@code load()} liest wieder die eingebettete Resource. */
	public static void reset() {
		setCached(null);
	}

	private static void setCached(PublicKey key) {
		try {
			Field field = EmbeddedSigningKey.class.getDeclaredField("cached");
			field.setAccessible(true);
			field.set(null, key);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"SigningKeyTestSupport konnte EmbeddedSigningKey.cached nicht per Reflection setzen", e);
		}
	}
}
