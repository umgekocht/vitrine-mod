package de.spassglas.vitrine.catalog;

import de.spassglas.vitrine.catalog.download.DownloadRequest;

import java.net.URI;
import java.util.Objects;

/**
 * Ein einzelner Versions-Eintrag aus {@code packages/&lt;id&gt;.json}
 * ({@code versions[]}, siehe {@code registry-package.schema.json}) --
 * genau die vier Download-Angaben, die {@link DownloadRequest}/{@code
 * PackageDownloader} (K3) braucht, plus die beiden optionalen
 * Zusatzangaben {@code publishedAt}/{@code minVitrineVersion} (heute nur
 * fuer Diagnose/spaetere Kompatibilitaetspruefung gehalten, nicht
 * ausgewertet).
 */
public record RegistryPackageVersion(
		String version,
		URI vpkgUrl,
		String sha256Hex,
		String signatureBase64,
		long size,
		String publishedAt,
		String minVitrineVersion) {

	public RegistryPackageVersion {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(vpkgUrl, "vpkgUrl");
		Objects.requireNonNull(sha256Hex, "sha256Hex");
		Objects.requireNonNull(signatureBase64, "signatureBase64");
	}

	/**
	 * Baut daraus die {@link DownloadRequest} fuer {@code PackageDownloader}
	 * (K3/G3) -- eine Wahrheit fuer "wie wird aus einem Registry-Versions-
	 * Eintrag eine Download-Anfrage".
	 */
	public DownloadRequest toDownloadRequest() {
		return DownloadRequest.of(vpkgUrl, sha256Hex, signatureBase64, size);
	}
}
