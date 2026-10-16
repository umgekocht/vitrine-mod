package de.spassglas.vitrine.classb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * ROADMAP J2: die vom Store an {@link JavaModStager#stage} gereichte Beschreibung
 * eines Klasse-B-Angebots ({@code .jar}-Mod, "Neustart noetig", nie hot --
 * Charter-Regel 2).
 *
 * <p>Traegt genau die Angaben, die J2 verlangt: den Anzeigenamen und den Autor
 * fuer die Autorenanzeige und die Klartextwarnung, den Zieldateinamen in
 * {@code mods/} und die <b>erwartete SHA-256-Pruefsumme</b>, gegen die die
 * bezogene Jar geprueft wird, <em>bevor</em> sie an ihren Staging-Platz kommt
 * (Charter-Regel 3: "Sicherheit vor Bequemlichkeit"). Seit GATE33 ist die
 * Ed25519-Signatur ({@link #signatureBytes()}) <b>Pflicht</b>: {@link
 * JavaModStager} verweigert jedes unsignierte Angebot und prueft eine
 * vorhandene Signatur ausschliesslich gegen den im Jar eingebetteten,
 * vertrauten Registry-Schluessel ({@code EmbeddedSigningKey#load()}) -- nie
 * gegen einen vom Angebot selbst mitgebrachten Schluessel (kein
 * Vertrauensanker, ein Angreifer koennte sich sonst selbst "gueltig"
 * signieren).</p>
 *
 * <p>{@code fileName} wird im Konstruktor gehaertet: kein Pfadtrenner, kein
 * {@code ..}, kein Laufwerksbuchstabe, nicht leer, muss auf {@code .jar} enden
 * (Zip-Slip-Analogie fuer den Jar-Namen, PLAN.md §6).</p>
 *
 * <p>ROADMAP J3: {@code dependencies} traegt die harten Mod-Ids, die vor dem
 * Kopieren bereits installiert, vorgemerkt oder das Angebot selbst sein
 * muessen ({@link InstalledModConflictCheck}). Eine leere Liste (Default)
 * bedeutet "keine harten Abhaengigkeiten" -- angelehnt an Fabrics
 * {@code depends}, aber ohne Versions-Range-Parser: dieses Angebot traegt
 * ohnehin keine Versionsnummer.</p>
 */
public record JavaModOffer(
		String id,
		String displayName,
		String author,
		String fileName,
		String expectedSha256Hex,
		byte[] signatureBytes,
		String sourceUrl,
		List<String> dependencies) {

	public JavaModOffer {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(author, "author");
		Objects.requireNonNull(fileName, "fileName");
		Objects.requireNonNull(expectedSha256Hex, "expectedSha256Hex");
		if (id.isBlank()) {
			throw new IllegalArgumentException("id darf nicht leer sein");
		}
		if (expectedSha256Hex.isBlank()) {
			throw new IllegalArgumentException("expectedSha256Hex darf nicht leer sein -- ein Jar ohne bekannte "
					+ "Pruefsumme wird nie gestaged (Charter-Regel 3)");
		}
		requireSafeJarFileName(fileName);
		// Defensive Kopie: das Array soll nach dem Bau nicht mehr von aussen
		// veraenderbar sein.
		signatureBytes = signatureBytes == null ? null : signatureBytes.clone();
		dependencies = normalizeDependencies(dependencies);
	}

	private static List<String> normalizeDependencies(List<String> dependencies) {
		if (dependencies == null || dependencies.isEmpty()) {
			return List.of();
		}
		List<String> cleaned = new ArrayList<>();
		for (String dependency : dependencies) {
			if (dependency == null) {
				continue;
			}
			String trimmed = dependency.strip();
			if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
				cleaned.add(trimmed);
			}
		}
		return List.copyOf(cleaned);
	}

	/** Auch von {@link StagedMod} genutzt (GATE61): jede Stelle, die einen Jar-Namen haelt, haertet sich selbst. */
	static void requireSafeJarFileName(String fileName) {
		String trimmed = fileName.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("fileName darf nicht leer sein");
		}
		if (trimmed.contains("/") || trimmed.contains("\\")) {
			throw new IllegalArgumentException("fileName darf kein Pfadtrenner enthalten: '" + fileName + "'");
		}
		if (trimmed.equals(".") || trimmed.equals("..") || trimmed.contains("..")) {
			throw new IllegalArgumentException("fileName darf kein '..' enthalten: '" + fileName + "'");
		}
		if (trimmed.length() >= 2 && trimmed.charAt(1) == ':') {
			throw new IllegalArgumentException("fileName darf keinen Laufwerksbuchstaben tragen: '" + fileName + "'");
		}
		if (trimmed.startsWith(".")) {
			throw new IllegalArgumentException("fileName darf nicht mit '.' beginnen: '" + fileName + "'");
		}
		if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			throw new IllegalArgumentException("fileName muss auf '.jar' enden: '" + fileName + "'");
		}
	}

	/**
	 * Bequemer Aufbau <b>ohne</b> Signatur/Quelle/Abhaengigkeiten -- fuer Tests
	 * anderer Belange (z. B. {@code InstalledModConflictCheck}), die nie durch
	 * {@link JavaModStager#stage} laufen. Seit GATE33 verweigert {@code stage}
	 * jedes unsignierte Angebot -- fuer echtes Staging {@link #signed} benutzen.
	 */
	public static JavaModOffer of(String id, String displayName, String author, String fileName,
			String expectedSha256Hex) {
		return new JavaModOffer(id, displayName, author, fileName, expectedSha256Hex, null, null, List.of());
	}

	/** Wie {@link #of(String, String, String, String, String)}, mit harten Abhaengigkeiten (ROADMAP J3). */
	public static JavaModOffer of(String id, String displayName, String author, String fileName,
			String expectedSha256Hex, List<String> dependencies) {
		return new JavaModOffer(id, displayName, author, fileName, expectedSha256Hex, null, null, dependencies);
	}

	/**
	 * Aufbau mit Ed25519-Signatur (GATE33: Pflicht fuer {@link
	 * JavaModStager#stage}) -- {@code signatureBytes} muss ueber genau {@code
	 * expectedSha256Hex} (als Hex-String-UTF-8-Bytes, siehe {@code
	 * VpkgIntegrityValidator}) mit dem privaten Gegenstueck des im Jar
	 * eingebetteten Registry-Schluessels erzeugt worden sein.
	 */
	public static JavaModOffer signed(String id, String displayName, String author, String fileName,
			String expectedSha256Hex, byte[] signatureBytes, List<String> dependencies) {
		return new JavaModOffer(id, displayName, author, fileName, expectedSha256Hex, signatureBytes, null,
				dependencies);
	}

	/** {@code true}, wenn das Angebot eine Ed25519-Signatur mitbringt. */
	public boolean hasSignature() {
		return signatureBytes != null && signatureBytes.length > 0;
	}

	@Override
	public byte[] signatureBytes() {
		return signatureBytes == null ? null : signatureBytes.clone();
	}
}
