package de.spassglas.vitrine.vpkg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.testng.Assert.assertNotNull;

/**
 * Bindet den im Jar eingebetteten Vertrauensanker an den echten Katalog.
 *
 * <p>Am 2026-10-04 fiel auf: der seit 2026-09-11 eingebettete Schluessel war
 * <b>nicht</b> der, mit dem die Katalog-Pakete signiert werden. Alle Tests
 * waren gruen, weil jeder Test einen eigenen Schluessel per
 * {@code SigningKeyTestSupport.install} einsetzt -- im echten Spiel waere jedoch kein
 * einziges Katalog-Paket installierbar gewesen.</p>
 *
 * <p>Dieser Test benutzt <b>keinen</b> Override: ein echtes Paket aus dem
 * Live-Katalog (mitsamt der Signatur aus seinem Katalog-Eintrag, siehe
 * {@code live-catalog-sample.json}) muss gegen genau den Schluessel bestehen,
 * der ausgeliefert wird. Wird der Signaturschluessel je getauscht, faellt das
 * hier auf -- nicht erst beim Nutzer.</p>
 */
public class EmbeddedKeyMatchesLiveCatalogTest {

	@Test
	public void aRealLiveCatalogPackagePassesAgainstTheShippedKey() throws Exception {
		SigningKeyTestSupport.reset(); // sicherstellen: kein Override aus anderen Tests
		JsonObject meta;
		try (InputStream in = getClass().getResourceAsStream("live-catalog-sample.json")) {
			assertNotNull(in, "Test-Ressource live-catalog-sample.json fehlt");
			meta = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Path vpkg = Files.createTempFile("vitrine-live-sample-", ".vpkg");
		try (InputStream in = getClass().getResourceAsStream("live-catalog-sample.vpkg")) {
			assertNotNull(in, "Test-Ressource live-catalog-sample.vpkg fehlt");
			Files.write(vpkg, in.readAllBytes());
		}
		try {
			// wirft VpkgIntegrityException, wenn Pruefsumme oder Signatur nicht passen
			VpkgIntegrityValidator.requireValid(vpkg, meta.get("sha256").getAsString(),
					Base64.getDecoder().decode(meta.get("signature").getAsString()), EmbeddedSigningKey.load());
		} finally {
			Files.deleteIfExists(vpkg);
		}
	}
}
