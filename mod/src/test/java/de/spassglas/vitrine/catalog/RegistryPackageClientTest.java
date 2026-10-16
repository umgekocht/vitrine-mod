package de.spassglas.vitrine.catalog;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP G3 ("Vollstaendigen Registry-Paket-Eintrag holen"): {@link
 * RegistryPackageClient} folgt demselben Muster wie {@link CatalogClient}
 * (K2) -- Test mit lokalem {@code file://}-URL, Schema-Verstoss und falscher
 * Id fuehren zu einem typisierten {@code CATALOG_BROKEN}-Ergebnis, nie zu
 * einer Exception.
 */
public final class RegistryPackageClientTest {

	private static final Predicate<URI> ALLOW_ANY = uri -> true;

	private Path workDir;
	private Path cacheDir;

	@BeforeMethod
	public void setUp() throws IOException {
		workDir = Files.createTempDirectory("vitrine-registry-package-client-test-");
		cacheDir = workDir.resolve("cache");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (workDir == null || !Files.exists(workDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(workDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	private RegistryPackageClient fileClient(Path packagesDir) {
		HttpCatalogClient http = new HttpCatalogClient(new FileUriTransport(), new EtagCache(cacheDir), ALLOW_ANY);
		return new RegistryPackageClient(http, packagesDir.toUri());
	}

	@Test
	public void loadsAndParsesAValidRegistryPackageFromFileUri() throws IOException {
		Path packagesDir = workDir.resolve("packages");
		Files.createDirectories(packagesDir);
		Files.writeString(packagesDir.resolve("umgekocht.klarglas.json"), CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON,
				StandardCharsets.UTF_8);

		CatalogResult<RegistryPackageEntry> result = fileClient(packagesDir).fetchPackage("umgekocht.klarglas");

		assertEquals(result.availability(), CatalogResult.Availability.ONLINE);
		RegistryPackageEntry entry = result.value().orElseThrow();
		assertEquals(entry.id(), "umgekocht.klarglas");
		assertEquals(entry.name(), "Klarglas");
		assertEquals(entry.category(), "theme");
		assertEquals(entry.authorName(), "Umgekocht");
		assertEquals(entry.latestVersion(), "1.0.0");
		assertEquals(entry.versions().size(), 1);
		RegistryPackageVersion version = entry.latestVersionEntry().orElseThrow();
		assertEquals(version.version(), "1.0.0");
		assertEquals(version.vpkgUrl(),
				URI.create("https://github.com/umgekocht/vitrine-packs-starter/releases/download/klarglas-v1.0.0/klarglas.vpkg"));
		assertEquals(version.size(), 20480L);
	}

	@Test
	public void mismatchedIdInsideTheFileIsReportedAsBroken() throws IOException {
		Path packagesDir = workDir.resolve("packages");
		Files.createDirectories(packagesDir);
		Files.writeString(packagesDir.resolve("anderes-paket.json"), CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON,
				StandardCharsets.UTF_8);

		CatalogResult<RegistryPackageEntry> result = fileClient(packagesDir).fetchPackage("anderes-paket");

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertTrue(result.message().orElseThrow().contains("andere Id"));
	}

	@Test
	public void schemaViolationIsReportedAsBrokenNotAnException() throws IOException {
		Path packagesDir = workDir.resolve("packages");
		Files.createDirectories(packagesDir);
		// Kein 'versions'-Feld -- verletzt das Schema (required).
		Files.writeString(packagesDir.resolve("kaputt.json"), "{\"schemaVersion\":1,\"id\":\"kaputt\"}",
				StandardCharsets.UTF_8);

		CatalogResult<RegistryPackageEntry> result = fileClient(packagesDir).fetchPackage("kaputt");

		assertEquals(result.availability(), CatalogResult.Availability.CATALOG_BROKEN);
		assertFalse(result.value().isPresent());
	}

	@Test
	public void missingFileIsReportedAsNoNetworkNotAnException() throws IOException {
		Path packagesDir = workDir.resolve("packages");
		Files.createDirectories(packagesDir);

		CatalogResult<RegistryPackageEntry> result = fileClient(packagesDir).fetchPackage("nicht-vorhanden");

		assertEquals(result.availability(), CatalogResult.Availability.NO_NETWORK);
	}
}
