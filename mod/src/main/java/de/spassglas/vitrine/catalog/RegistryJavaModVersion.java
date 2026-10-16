package de.spassglas.vitrine.catalog;

import de.spassglas.vitrine.classb.JavaModOffer;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * GATE6: ein Versions-Eintrag eines Klasse-B-Pakets ({@code contentClass}
 * {@code javamod}) aus {@code packages/&lt;id&gt;.json}, siehe
 * {@code $defs/javamodVersionItem} in {@code registry-package.schema.json}.
 *
 * <p>Klasse B hat kein {@code .vpkg}: die {@code .jar} haengt als
 * Release-Asset im Autoren-Repo ({@code jarUrl}). Vertrauenskette: die
 * Registry signiert den Eintrag, der Eintrag nennt SHA-256 und Signatur der
 * {@code .jar}, und {@code JavaModStager} prueft die heruntergeladene Datei
 * dagegen, bevor sie nach {@code mods/} wandert.</p>
 */
public record RegistryJavaModVersion(
		String version,
		URI jarUrl,
		String fileName,
		String sha256Hex,
		String signatureBase64,
		long size,
		List<String> dependencies,
		String publishedAt,
		String minVitrineVersion) {

	public RegistryJavaModVersion {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(jarUrl, "jarUrl");
		Objects.requireNonNull(fileName, "fileName");
		Objects.requireNonNull(sha256Hex, "sha256Hex");
		Objects.requireNonNull(signatureBase64, "signatureBase64");
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
	}

	/**
	 * Baut das Angebot fuer {@code JavaModStager.stage(...)} -- die eine Stelle,
	 * an der aus einem Registry-Eintrag ein stagebares Klasse-B-Angebot wird.
	 * {@code JavaModOffer} prueft dabei selbst Dateiname und Pruefsumme.
	 *
	 * @throws IllegalArgumentException wenn die Signatur kein gueltiges Base64 ist
	 */
	public JavaModOffer toOffer(String packageId, String displayName, String author) {
		byte[] signature = Base64.getDecoder().decode(signatureBase64);
		return new JavaModOffer(packageId, displayName, author, fileName, sha256Hex, signature,
				jarUrl.toString(), dependencies);
	}
}
