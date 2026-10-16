package de.spassglas.vitrine.vpkg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;

/**
 * Liest die Pruefsummen-/Signatur-Begleitdatei eines {@code .vpkg}, die
 * {@link VpkgIntegrityValidator} (P4) fuer {@link
 * de.spassglas.vitrine.pack.VitrinePackProvider} (H1) mit den drei Werten
 * versorgt, die diese Klasse selbst bewusst nicht kennt: erwartete SHA-256,
 * Signatur und oeffentlicher Schluessel (siehe {@link VpkgIntegrityValidator}s
 * Klassendoku: "woher ... kommt, ist bewusst nicht Sache dieser Klasse").
 *
 * <p><b>Warum eine Begleitdatei und nicht der Registry-Index:</b> Der
 * Registry-Index (K1-K3) und ein herunterladender Installer, der die dortige
 * Pruefsumme/Signatur samt umgekocht's im Jar eingebettetem Schluessel gegen ein
 * frisch heruntergeladenes Paket prueft, bevor er es nach {@code
 * config/vitrine/packs/} legt, kommen laut Roadmap erst mit M4. H1 (M2) muss
 * schon vorher funktionieren -- deshalb liest diese Klasse dieselben drei
 * Werte aus einer neben dem {@code .vpkg} liegenden {@code
 * &lt;dateiname&gt;.vpkg.integrity.json}: {@code sha256} (Hex), {@code
 * signature} (Base64, Ed25519-Signatur ueber den SHA-256-Digest) und {@code
 * publicKey} (Base64, X.509-kodierter Ed25519-Schluessel). Wer diese Datei
 * schreibt -- lokal beim Testen, spaeter ein Installer (K3) mit den
 * Registry-Werten, oder die Designer-CLI (D6/D9) beim lokalen Signieren -- ist
 * nicht Sache dieser Klasse; sie liest nur, was dort steht.</p>
 *
 * <p><b>Kein zusaetzliches Vertrauen, nur strukturelle Pruefbarkeit (GATE29):</b>
 * Die Begleitdatei liegt neben dem Paket, im selben, vom Nutzer kontrollierten
 * Ordner -- ein Angreifer, der ein {@code .vpkg} mit einem selbst erzeugten
 * Ed25519-Schluesselpaar signiert, koennte den eigenen oeffentlichen
 * Schluessel als {@link Content#publicKey()} danebenlegen. Deshalb wird das
 * {@code publicKey}-Feld dieser Begleitdatei fuer die eigentliche
 * Vertrauensentscheidung NICHT mehr benutzt -- jeder Installationsweg
 * (lokal, Katalog, Update, Bundle) prueft die Ed25519-Signatur ausschliesslich
 * gegen den im Jar eingebetteten Schluessel ({@link EmbeddedSigningKey#load()}).
 * Nur die SHA-256-Pruefsumme aus dieser Begleitdatei bleibt relevant. Was sie
 * damit honest leistet: kein {@code .vpkg} wird als Ressourcen-Pack
 * angemeldet, ohne dass {@link VpkgIntegrityValidator} eine tatsaechlich zur
 * Datei passende Pruefsumme UND eine gegen den vertrauten Registry-Schluessel
 * kryptographisch gueltige Signatur gesehen hat -- Bitfaeule, abgebrochene
 * Kopien, ein vergessener Pruef-Aufruf und ein untergeschobener
 * Fremd-Schluessel fallen damit gleichermassen auf.</p>
 *
 * <p>Jeder Fehlerfall (Datei fehlt, kaputtes JSON, kaputter Schluessel) liefert
 * ein {@link Content} mit {@code null}-Feldern statt zu werfen -- {@link
 * VpkgIntegrityValidator#requireValid} wertet fehlende Werte ohnehin als
 * Verstoss (Default ist Verbot, Charter-Regel 3/4) und liefert dafuer bereits
 * die richtige, konkrete Meldung.</p>
 */
public final class VpkgIntegritySidecar {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgIntegritySidecar");

	/** Anhang an den vollstaendigen Dateinamen des {@code .vpkg}, z. B. {@code beispiel.vpkg.integrity.json}. */
	public static final String FILE_SUFFIX = ".integrity.json";

	private VpkgIntegritySidecar() {
	}

	/**
	 * Die drei Rohwerte, wie {@link VpkgIntegrityValidator#requireValid} sie
	 * erwartet -- jedes Feld darf {@code null} sein.
	 *
	 * <p><b>GATE29:</b> {@code publicKey} wird von keinem verdrahteten
	 * Installationsweg mehr fuer die Vertrauensentscheidung gelesen (siehe
	 * Klassendoku) -- die Ed25519-Pruefung laeuft dort ausschliesslich gegen
	 * {@link EmbeddedSigningKey#load()}. Das Feld bleibt fuer bestehende
	 * Begleitdateien und {@link #write} erhalten.</p>
	 */
	public record Content(String sha256, byte[] signature, PublicKey publicKey) {

		static final Content EMPTY = new Content(null, null, null);
	}

	/** Pfad der Begleitdatei zu {@code vpkgFile}, im selben Verzeichnis. */
	public static Path sidecarPathFor(Path vpkgFile) {
		return vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + FILE_SUFFIX);
	}

	/**
	 * Liest die Begleitdatei zu {@code vpkgFile}. Wirft nie -- jeder Fehlerfall
	 * (fehlende Datei, kaputtes JSON, kaputte Base64-Kodierung, kaputter
	 * Schluessel) wird geloggt und fuehrt zu {@code null}-Feldern im Ergebnis,
	 * die {@link VpkgIntegrityValidator#requireValid} anschliessend als
	 * Verstoss erkennt.
	 */
	public static Content read(Path vpkgFile) {
		Path sidecar = sidecarPathFor(vpkgFile);
		if (!Files.exists(sidecar)) {
			LOGGER.warn("Kein Integritaets-Beleg '{}' fuer '{}' gefunden -- Paket gilt als nicht geprueft.",
					sidecar, vpkgFile);
			return Content.EMPTY;
		}

		String json;
		try {
			json = Files.readString(sidecar, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Integritaets-Beleg '{}' liess sich nicht lesen: {}", sidecar, e.toString());
			return Content.EMPTY;
		}

		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("Wurzelelement ist kein JSON-Objekt");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			LOGGER.warn("Integritaets-Beleg '{}' enthaelt kein gueltiges JSON-Objekt: {}", sidecar, e.toString());
			return Content.EMPTY;
		}

		String sha256 = optionalString(root, "sha256");
		byte[] signature = decodeBase64(sidecar, optionalString(root, "signature"), "signature");
		byte[] publicKeyBytes = decodeBase64(sidecar, optionalString(root, "publicKey"), "publicKey");

		PublicKey publicKey = null;
		if (publicKeyBytes != null) {
			try {
				publicKey = VpkgIntegrityValidator.decodeEd25519PublicKey(publicKeyBytes);
			} catch (VpkgIntegrityException e) {
				LOGGER.warn("Integritaets-Beleg '{}' enthaelt einen nicht dekodierbaren oeffentlichen Schluessel: {}",
						sidecar, e.getMessage());
			}
		}

		return new Content(sha256, signature, publicKey);
	}

	/**
	 * Schreibt die Begleitdatei zu {@code vpkgFile} neu (ROADMAP G3/K5): fuer
	 * ein aus dem Katalog heruntergeladenes Paket, dessen Pruefsumme/Signatur
	 * schon von {@code PackageDownloader} gegen den eingebetteten Registry-
	 * Schluessel geprueft wurden -- ohne diese Datei wuerde
	 * {@code VitrinePackProvider} das Paket beim naechsten Scan als "nicht
	 * geprueft" ablehnen (siehe {@link #read}). Atomar (Temp-Datei +
	 * {@code ATOMIC_MOVE}, wie {@code PackInstaller.copyAtomically}).
	 */
	public static void write(Path vpkgFile, String sha256Hex, byte[] signature, PublicKey publicKey)
			throws IOException {
		Objects.requireNonNull(vpkgFile, "vpkgFile");
		Objects.requireNonNull(sha256Hex, "sha256Hex");
		Objects.requireNonNull(signature, "signature");
		Objects.requireNonNull(publicKey, "publicKey");

		JsonObject json = new JsonObject();
		json.addProperty("sha256", sha256Hex);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));

		Path sidecar = sidecarPathFor(vpkgFile);
		Path tmp = Files.createTempFile(sidecar.getParent(), sidecar.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, sidecar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt ({}), weiche auf einen "
						+ "gewoehnlichen Move aus.", tmp, sidecar, e.toString());
				Files.move(tmp, sidecar, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
	}

	private static String optionalString(JsonObject root, String field) {
		if (!root.has(field) || root.get(field).isJsonNull()) {
			return null;
		}
		JsonElement element = root.get(field);
		return element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private static byte[] decodeBase64(Path sidecar, String base64, String fieldName) {
		if (base64 == null || base64.isBlank()) {
			return null;
		}
		try {
			return Base64.getDecoder().decode(base64.strip());
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Integritaets-Beleg '{}': Feld '{}' ist keine gueltige Base64-Kodierung: {}",
					sidecar, fieldName, e.toString());
			return null;
		}
	}
}
