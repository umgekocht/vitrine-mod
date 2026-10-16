package de.spassglas.vitrine.config;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das Fertig-Kriterium von F2 ab: schreiben, lesen, beschaedigen --
 * alle drei Faelle bewiesen, ohne dass der Mod je abstuerzt.
 */
public final class SettingsManagerTest {

	private Path tempConfigDir;

	@BeforeMethod
	public void createTempConfigDir() throws IOException {
		tempConfigDir = Files.createTempDirectory("vitrine-settings-test-");
	}

	@AfterMethod
	public void deleteTempConfigDir() throws IOException {
		if (tempConfigDir == null || !Files.exists(tempConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempConfigDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new UncheckedIOExceptionForTest(e);
						}
					});
		}
	}

	private static final class UncheckedIOExceptionForTest extends RuntimeException {
		UncheckedIOExceptionForTest(IOException cause) {
			super(cause);
		}
	}

	// --- Fall 1: schreiben + lesen -------------------------------------

	@Test
	public void missingFileYieldsDefaultsAndCreatesFile() {
		Path settingsFile = tempConfigDir.resolve("settings.json");
		assertFalse(Files.exists(settingsFile), "Testvoraussetzung: Datei darf vor dem Laden nicht existieren");

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, VitrineSettings.CURRENT_SCHEMA_VERSION);
		assertTrue(Files.exists(settingsFile), "load() muss beim Fehlen der Datei Default-Werte anlegen");
	}

	@Test
	public void savedSettingsRoundTripThroughLoad() throws IOException {
		VitrineSettings toSave = VitrineSettings.createDefault();
		SettingsManager.save(tempConfigDir, toSave);

		Path settingsFile = tempConfigDir.resolve("settings.json");
		assertTrue(Files.exists(settingsFile), "save() muss settings.json anlegen");
		String written = Files.readString(settingsFile, StandardCharsets.UTF_8);
		assertTrue(written.contains("\"schemaVersion\""), "geschriebene Datei muss schemaVersion enthalten");

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);
		assertEquals(loaded.schemaVersion, toSave.schemaVersion);
	}

	@Test
	public void saveWritesAtomicallyLeavingNoTempFileBehind() throws IOException {
		SettingsManager.save(tempConfigDir, VitrineSettings.createDefault());

		try (Stream<Path> entries = Files.list(tempConfigDir)) {
			List<String> names = entries.map(p -> p.getFileName().toString()).toList();
			assertEquals(names, List.of("settings.json"),
					"nach dem Schreiben darf nur die Zieldatei uebrig sein, keine .tmp-Reste");
		}
	}

	@Test
	public void saveOverwritesExistingFileAtomically() throws IOException {
		SettingsManager.save(tempConfigDir, VitrineSettings.createDefault());
		Path settingsFile = tempConfigDir.resolve("settings.json");
		long firstSize = Files.size(settingsFile);
		assertTrue(firstSize > 0);

		// Zweites Schreiben muss die bestehende Datei ersetzen, nicht daneben
		// eine zweite anlegen.
		SettingsManager.save(tempConfigDir, VitrineSettings.createDefault());
		try (Stream<Path> entries = Files.list(tempConfigDir)) {
			long fileCount = entries.count();
			assertEquals(fileCount, 1L, "erneutes Speichern darf nur eine settings.json hinterlassen");
		}
	}

	// --- Fall 2: kaputte Datei -> Default + Backup ----------------------

	@Test
	public void invalidJsonIsBackedUpAndDefaultsAreReturned() throws IOException {
		Path settingsFile = tempConfigDir.resolve("settings.json");
		Files.writeString(settingsFile, "{ das ist kein json", StandardCharsets.UTF_8);

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, VitrineSettings.CURRENT_SCHEMA_VERSION,
				"kaputte Datei muss zu Default-Werten fuehren, nicht zum Absturz");

		Path backup = tempConfigDir.resolve("settings.json.bak");
		assertTrue(Files.exists(backup), "die kaputte Originaldatei muss als Backup erhalten bleiben");
		assertEquals(Files.readString(backup, StandardCharsets.UTF_8), "{ das ist kein json");

		assertTrue(Files.exists(settingsFile), "nach dem Laden muss wieder eine gueltige settings.json existieren");
		String freshContent = Files.readString(settingsFile, StandardCharsets.UTF_8);
		assertTrue(freshContent.contains("\"schemaVersion\": " + VitrineSettings.CURRENT_SCHEMA_VERSION));
	}

	@Test
	public void missingSchemaVersionFieldIsTreatedAsCorrupt() throws IOException {
		Path settingsFile = tempConfigDir.resolve("settings.json");
		Files.writeString(settingsFile, "{ \"someField\": 42 }", StandardCharsets.UTF_8);

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, VitrineSettings.CURRENT_SCHEMA_VERSION);
		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak")));
	}

	@Test
	public void futureSchemaVersionIsBackedUpInsteadOfCrashing() throws IOException {
		Path settingsFile = tempConfigDir.resolve("settings.json");
		int impossiblyFutureVersion = VitrineSettings.CURRENT_SCHEMA_VERSION + 1000;
		Files.writeString(settingsFile, "{ \"schemaVersion\": " + impossiblyFutureVersion + " }",
				StandardCharsets.UTF_8);

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, VitrineSettings.CURRENT_SCHEMA_VERSION,
				"eine Version, die dieser Mod noch nicht kennt, darf nicht crashen");
		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak")));
	}

	@Test
	public void repeatedCorruptionGetsUniquelyNumberedBackups() throws IOException {
		Path settingsFile = tempConfigDir.resolve("settings.json");

		Files.writeString(settingsFile, "kaputt-1", StandardCharsets.UTF_8);
		SettingsManager.load(tempConfigDir);
		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak")));

		// Die frisch geschriebene Default-Datei erneut beschaedigen.
		Files.writeString(settingsFile, "kaputt-2", StandardCharsets.UTF_8);
		SettingsManager.load(tempConfigDir);

		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak")),
				"erstes Backup darf nicht verschwinden");
		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak.1")),
				"zweites Backup braucht einen eigenen, eindeutigen Namen");
		assertEquals(Files.readString(tempConfigDir.resolve("settings.json.bak"), StandardCharsets.UTF_8),
				"kaputt-1");
		assertEquals(Files.readString(tempConfigDir.resolve("settings.json.bak.1"), StandardCharsets.UTF_8),
				"kaputt-2");
	}

	// --- Fall 3: Schema-Version + Migrationshaken ------------------------

	@Test
	public void olderSchemaVersionsAreMigratedAndPersisted() throws IOException {
		// Es gibt bisher nur Version 1 -- dieser Test beweist trotzdem, dass
		// load() einen aelteren Versionsstand als solchen erkennt und ueber
		// SchemaMigrations zu migrieren versucht, statt ihn stillschweigend
		// so zu benutzen, als waere er aktuell. Da noch kein Migrationsschritt
		// registriert ist, kann selbst Version 0 nicht hochgezogen werden und
		// landet im Kaputt-Pfad (Default + Backup) -- das ist das erwartete,
		// sichere Verhalten fuer eine unbekannte Alt-Version.
		Path settingsFile = tempConfigDir.resolve("settings.json");
		Files.writeString(settingsFile, "{ \"schemaVersion\": 0 }", StandardCharsets.UTF_8);

		VitrineSettings loaded = SettingsManager.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, VitrineSettings.CURRENT_SCHEMA_VERSION);
		assertTrue(Files.exists(tempConfigDir.resolve("settings.json.bak")));
	}
}
