package de.spassglas.vitrine.catalog;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/**
 * Charter-Regel 8 ("Ein Schema, zwei Seiten"): der Katalog-Client validiert
 * gegen eine im Jar eingebettete Kopie von
 * {@code registry-featured.schema.json}, weil das gepackte Mod-Jar zur
 * Laufzeit nicht auf {@code registry/schema/} zugreifen kann. Dieser Test
 * haelt beide Kopien byte-genau synchron (gleiches Muster wie
 * {@link RegistryIndexPageSchemaResourceSyncTest}).
 */
public final class RegistryFeaturedSchemaResourceSyncTest {

	@Test
	public void embeddedSchemaResourceIsByteIdenticalToSourceOfTruth() throws IOException {
		byte[] embedded = readEmbeddedResource();
		byte[] sourceOfTruth = Files.readAllBytes(resolveSourceOfTruth());

		assertEquals(embedded, sourceOfTruth,
				"mod/src/main/resources/de/spassglas/vitrine/catalog/registry-featured.schema.json ist nicht "
						+ "mehr byte-identisch zu registry/schema/registry-featured.schema.json -- eine der "
						+ "beiden Kopien wurde geaendert, ohne die andere nachzuziehen.");
	}

	private static byte[] readEmbeddedResource() {
		try (InputStream in = FeaturedSchemaValidator.class.getResourceAsStream(
				FeaturedSchemaValidator.SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource fehlt im Klassenpfad");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Konnte eingebettete Schema-Resource nicht lesen", e);
		}
	}

	private static Path resolveSourceOfTruth() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "registry-featured.schema.json");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "registry-featured.schema.json");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/registry-featured.schema.json nicht finden, Arbeitsverzeichnis: "
						+ Path.of("").toAbsolutePath());
	}
}
