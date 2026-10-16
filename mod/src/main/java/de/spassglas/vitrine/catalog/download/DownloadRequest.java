package de.spassglas.vitrine.catalog.download;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Eingabe-Wertobjekt fuer {@link PackageDownloader}: die vier Download-Angaben,
 * die im Registry-Package-Schema unter {@code versions[]} stehen
 * ({@code registry/schema/registry-package.schema.json}). K3 parst das
 * Package-Schema <em>nicht</em> selbst -- woher diese Werte kommen
 * (Detailseite/K5), ist nicht Sache dieser Zeile. Der {@code catalog}-Stil:
 * jeder Seam nimmt genau das entgegen, was er braucht.
 *
 * @param vpkgUrl         Download-URL des Pakets. Muss auf {@code .vpkg} enden.
 *                        Das Schema erlaubt hier nur {@code https}; die
 *                        HTTPS-/Host-Pruefung macht aber {@link DownloadHostWhitelist}
 *                        (bzw. der Test-Seam mit {@code file://}), nicht dieses
 *                        Wertobjekt.
 * @param sha256Hex       erwartete SHA-256-Pruefsumme ueber die fertige Datei,
 *                        genau 64 Hex-Zeichen, klein. Wird nach dem atomaren
 *                        Umbenennen von {@code VpkgIntegrityValidator} geprueft.
 * @param signatureBase64 Ed25519-Signatur (Base64) ueber den SHA-256-Digest der
 *                        Datei. Muss sich als Base64 dekodieren lassen und darf
 *                        nicht leer sein.
 * @param declaredSize    erwartete Gesamtgroesse in Bytes, {@code 1 ..}
 *                        {@link DownloadEndpoints#MAX_PACKAGE_BYTES}, oder
 *                        {@link #SIZE_UNKNOWN} ({@code -1}), wenn keine Groesse
 *                        bekannt ist -- dann greift nur die harte Obergrenze.
 */
public record DownloadRequest(URI vpkgUrl, String sha256Hex, String signatureBase64, long declaredSize) {

	/** Kennzeichnet eine Anfrage ohne bekannte Zielgroesse. */
	public static final long SIZE_UNKNOWN = -1L;

	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	public DownloadRequest {
		Objects.requireNonNull(vpkgUrl, "vpkgUrl");
		Objects.requireNonNull(sha256Hex, "sha256Hex");
		Objects.requireNonNull(signatureBase64, "signatureBase64");

		if (vpkgUrl.getPath() == null || !vpkgUrl.getPath().endsWith(".vpkg")) {
			throw new IllegalArgumentException("vpkgUrl muss auf '.vpkg' enden: " + vpkgUrl);
		}
		if (!SHA256_HEX.matcher(sha256Hex).matches()) {
			throw new IllegalArgumentException(
					"sha256Hex muss genau 64 kleingeschriebene Hex-Zeichen sein, war: '" + sha256Hex + "'");
		}
		byte[] decodedSignature;
		try {
			decodedSignature = Base64.getDecoder().decode(signatureBase64);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("signatureBase64 ist kein gueltiges Base64: " + e.getMessage(), e);
		}
		if (decodedSignature.length == 0) {
			throw new IllegalArgumentException("signatureBase64 dekodiert zu 0 Bytes -- keine Signatur");
		}
		if (declaredSize != SIZE_UNKNOWN) {
			if (declaredSize <= 0) {
				throw new IllegalArgumentException(
						"declaredSize muss > 0 sein (oder SIZE_UNKNOWN), war: " + declaredSize);
			}
			if (declaredSize > DownloadEndpoints.MAX_PACKAGE_BYTES) {
				throw new IllegalArgumentException("declaredSize " + declaredSize + " ueberschreitet die Obergrenze "
						+ DownloadEndpoints.MAX_PACKAGE_BYTES);
			}
		}
	}

	/** Anfrage mit bekannter Zielgroesse. */
	public static DownloadRequest of(URI vpkgUrl, String sha256Hex, String signatureBase64, long size) {
		return new DownloadRequest(vpkgUrl, sha256Hex, signatureBase64, size);
	}

	/** Anfrage ohne bekannte Zielgroesse -- nur die harte Obergrenze schuetzt dann. */
	public static DownloadRequest withoutDeclaredSize(URI vpkgUrl, String sha256Hex, String signatureBase64) {
		return new DownloadRequest(vpkgUrl, sha256Hex, signatureBase64, SIZE_UNKNOWN);
	}

	/** {@code true}, wenn eine erwartete Groesse vorliegt. */
	public boolean hasDeclaredSize() {
		return declaredSize != SIZE_UNKNOWN;
	}

	/** Die dekodierten Signatur-Bytes (frische Kopie je Aufruf). */
	public byte[] signatureBytes() {
		return Base64.getDecoder().decode(signatureBase64);
	}
}
