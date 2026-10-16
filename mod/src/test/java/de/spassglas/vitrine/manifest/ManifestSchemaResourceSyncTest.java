package de.spassglas.vitrine.manifest;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/**
 * Charter-Regel 8 ("Ein Schema, zwei Seiten"): der Java-Parser validiert
 * gegen eine im Jar eingebettete Kopie von {@code vitrine.schema.json}
 * (siehe {@link ManifestParser}), weil ein gepacktes Mod-Jar zur Laufzeit
 * nicht auf {@code registry/schema/} im Repo zugreifen kann. Dieser Test
 * haelt beide Kopien synchron: sobald jemand die eine Datei aendert, ohne
 * die andere nachzuziehen, schlaegt der Build sofort fehl statt heimlich
 * zu driften.
 */
public final class ManifestSchemaResourceSyncTest {

	@Test
	public void embeddedSchemaResourceIsByteIdenticalToSourceOfTruth() throws IOException {
		byte[] embedded = readEmbeddedResource();
		byte[] sourceOfTruth = Files.readAllBytes(resolveSourceOfTruth());

		assertEquals(embedded, sourceOfTruth,
				"mod/src/main/resources/de/spassglas/vitrine/manifest/vitrine.schema.json ist nicht mehr "
						+ "byte-identisch zu registry/schema/vitrine.schema.json -- eine der beiden Kopien "
						+ "wurde geaendert, ohne die andere nachzuziehen.");
	}

	private static byte[] readEmbeddedResource() {
		try (InputStream in = ManifestParser.class.getResourceAsStream("vitrine.schema.json")) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource fehlt im Klassenpfad");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Konnte eingebettete Schema-Resource nicht lesen", e);
		}
	}

	/**
	 * Siehe {@code UiTextScanTest#resolveMainJavaRoot()} als Vorbild fuer
	 * die Pfadaufloesung relativ zum jeweiligen Arbeitsverzeichnis.
	 */
	private static Path resolveSourceOfTruth() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "vitrine.schema.json");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "vitrine.schema.json");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/vitrine.schema.json nicht finden, Arbeitsverzeichnis: "
						+ Path.of("").toAbsolutePath());
	}
}
