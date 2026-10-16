package de.spassglas.vitrine.vpkg;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Im Spiel gefunden (2026-10-06): nach GATE106 (Allowlist) lehnte die
 * Sicherheitspruefung jedes Katalogpaket ab, weil die von GATE55 verlangte
 * LICENSE-Datei keine Endung hat. Ein echtes Paket aus dem Live-Katalog muss
 * die Pruefung bestehen -- sonst ist der ganze Store kaputt.
 */
public class LiveCatalogPackagePassesSecurityTest {

	@Test
	public void realCatalogPackageIsSafe() throws Exception {
		Path vpkg = Files.createTempFile("vitrine-live-sec-", ".vpkg");
		try (InputStream in = getClass().getResourceAsStream("live-catalog-sample.vpkg")) {
			assertNotNull(in);
			Files.write(vpkg, in.readAllBytes());
		}
		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive);
			assertTrue(report.isSafe(), "Live-Katalogpaket abgelehnt: " + report.violations());
		} finally {
			Files.deleteIfExists(vpkg);
		}
	}

	@Test
	public void licenseFileWithoutExtensionIsAllowedAtRoot() throws Exception {
		java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
		entries.put("vitrine.json", "{}".getBytes());
		entries.put("LICENSE", "CC-BY-4.0".getBytes());
		entries.put("README.md", "# x".getBytes());
		Path vpkg = Files.createTempFile("vitrine-license-", ".vpkg");
		Files.write(vpkg, de.spassglas.vitrine.fixtures.VpkgFixtures.buildZip(entries));
		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			VpkgSecurityReport report = VpkgSecurityValidator.validate(archive);
			assertTrue(report.isSafe(), report.violations().toString());
		} finally {
			Files.deleteIfExists(vpkg);
		}
	}
}
