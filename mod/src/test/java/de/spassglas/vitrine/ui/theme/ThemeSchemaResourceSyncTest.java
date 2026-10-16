package de.spassglas.vitrine.ui.theme;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/**
 * Charter-Regel 8 ("Ein Schema, zwei Seiten"): {@link ThemeManager} validiert
 * geladene Themes gegen eine im Jar eingebettete Kopie von
 * {@code registry/schema/theme.schema.json}, weil ein gepacktes Mod-Jar zur
 * Laufzeit nicht auf {@code registry/schema/} zugreifen kann. Dieser Test
 * haelt beide Kopien synchron -- gleiches Muster wie
 * {@code de.spassglas.vitrine.manifest.ManifestSchemaResourceSyncTest} (P1)
 * und {@code de.spassglas.vitrine.hud.HudSchemaResourceSyncTest} (T5): aendert
 * jemand die eine Datei ohne die andere, bricht der Build sofort.
 */
public final class ThemeSchemaResourceSyncTest {

	@Test
	public void embeddedThemeSchemaResourceIsByteIdenticalToSourceOfTruth() throws IOException {
		byte[] embedded = readEmbeddedResource();
		byte[] sourceOfTruth = Files.readAllBytes(resolveSourceOfTruth());

		assertEquals(embedded, sourceOfTruth,
				"mod/src/main/resources/de/spassglas/vitrine/ui/theme/theme.schema.json ist nicht mehr "
						+ "byte-identisch zu registry/schema/theme.schema.json -- eine der beiden Kopien "
						+ "wurde geaendert, ohne die andere nachzuziehen.");
	}

	private static byte[] readEmbeddedResource() {
		try (InputStream in = ThemeSchemaValidator.class.getResourceAsStream(
				ThemeSchemaValidator.SCHEMA_RESOURCE_NAME)) {
			if (in == null) {
				throw new IllegalStateException("Eingebettete Schema-Resource fehlt im Klassenpfad");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Konnte eingebettete Schema-Resource nicht lesen", e);
		}
	}

	/** Siehe {@code HudSchemaResourceSyncTest#resolveSourceOfTruth()} als Vorbild fuer die Pfadaufloesung. */
	private static Path resolveSourceOfTruth() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "theme.schema.json");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "theme.schema.json");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/theme.schema.json nicht finden, Arbeitsverzeichnis: "
						+ Path.of("").toAbsolutePath());
	}
}
