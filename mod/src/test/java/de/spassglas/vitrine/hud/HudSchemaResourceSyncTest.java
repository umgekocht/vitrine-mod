package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/**
 * Charter-Regel 8 ("Ein Schema, zwei Seiten"): {@link HudLayoutParser}
 * validiert gegen eine im Jar eingebettete Kopie von
 * {@code registry/schema/hud.schema.json}, weil ein gepacktes Mod-Jar zur
 * Laufzeit nicht auf {@code registry/schema/} zugreifen kann. Dieser Test
 * haelt beide Kopien synchron -- gleiches Muster wie
 * {@code de.spassglas.vitrine.manifest.ManifestSchemaResourceSyncTest} (P1):
 * aendert jemand die eine Datei ohne die andere, bricht der Build sofort.
 */
public final class HudSchemaResourceSyncTest {

	@Test
	public void embeddedHudSchemaResourceIsByteIdenticalToSourceOfTruth() throws IOException {
		byte[] embedded = readEmbeddedResource();
		byte[] sourceOfTruth = Files.readAllBytes(resolveSourceOfTruth());

		assertEquals(embedded, sourceOfTruth,
				"mod/src/main/resources/de/spassglas/vitrine/hud/hud.schema.json ist nicht mehr "
						+ "byte-identisch zu registry/schema/hud.schema.json -- eine der beiden Kopien "
						+ "wurde geaendert, ohne die andere nachzuziehen.");
	}

	private static byte[] readEmbeddedResource() {
		try (InputStream in = HudLayoutParser.class.getResourceAsStream("hud.schema.json")) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource fehlt im Klassenpfad");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Konnte eingebettete Schema-Resource nicht lesen", e);
		}
	}

	/** Siehe {@code ManifestSchemaResourceSyncTest#resolveSourceOfTruth()} als Vorbild fuer die Pfadaufloesung. */
	private static Path resolveSourceOfTruth() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "hud.schema.json");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "hud.schema.json");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/hud.schema.json nicht finden, Arbeitsverzeichnis: "
						+ Path.of("").toAbsolutePath());
	}
}
