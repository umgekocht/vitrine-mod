package de.spassglas.vitrine.state;

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
 * Deckt das Fertig-Kriterium von H4 ab: {@code state.json} haelt installierte
 * Pakete, ihren aktiven Zustand und ihre Reihenfolge fest, und ein Absturz
 * mitten im Schreiben darf den Zustand nie ungueltig machen.
 */
public final class PackStateStoreTest {

	private Path tempConfigDir;

	@BeforeMethod
	public void createTempConfigDir() throws IOException {
		tempConfigDir = Files.createTempDirectory("vitrine-state-test-");
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

	// --- Grundfall: installiert/aktiv/Reihenfolge ueberleben einen Roundtrip ---

	@Test
	public void missingFileYieldsEmptyDefaultsAndCreatesFile() {
		Path stateFile = tempConfigDir.resolve("state.json");
		assertFalse(Files.exists(stateFile), "Testvoraussetzung: Datei darf vor dem Laden nicht existieren");

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(loaded.packs.isEmpty());
		assertTrue(Files.exists(stateFile), "load() muss beim Fehlen der Datei einen leeren Zustand anlegen");
	}

	@Test
	public void installedActiveAndOrderSurviveARoundTrip() throws IOException {
		PackState toSave = PackState.createDefault();
		toSave.addOrUpdate("vitrine/erstes", true);
		toSave.addOrUpdate("vitrine/zweites", false);
		toSave.addOrUpdate("vitrine/drittes", true);
		PackStateStore.save(tempConfigDir, toSave);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.installedPackIdsInOrder(), List.of("vitrine/erstes", "vitrine/zweites", "vitrine/drittes"),
				"Reihenfolge der Installation muss erhalten bleiben");
		assertEquals(loaded.activePackIdsInOrder(), List.of("vitrine/erstes", "vitrine/drittes"),
				"nur die als aktiv markierten Pakete duerfen als aktiv zurueckkommen");
		assertTrue(loaded.isInstalled("vitrine/zweites"));
		assertFalse(loaded.isActive("vitrine/zweites"));
	}

	@Test
	public void saveWritesAtomicallyLeavingNoTempFileBehind() throws IOException {
		PackStateStore.save(tempConfigDir, PackState.createDefault());

		try (Stream<Path> entries = Files.list(tempConfigDir)) {
			List<String> names = entries.map(p -> p.getFileName().toString()).toList();
			assertEquals(names, List.of("state.json"),
					"nach dem Schreiben darf nur die Zieldatei uebrig sein, keine .tmp-Reste");
		}
	}

	// --- H4-Fertig-Kriterium: Absturz mitten im Schreiben --------------------

	@Test
	public void truncatedStateFileFromCrashDuringWriteYieldsValidStateInstead() throws IOException {
		// Simuliert eine state.json, die durch einen Absturz mitten im
		// Schreiben mittendrin abgeschnitten wurde: kein Betriebssystem-Crash
		// noetig, das Resultat auf der Platte sieht identisch aus -- ein
		// abgeschnittenes, syntaktisch ungueltiges JSON-Dokument.
		Path stateFile = tempConfigDir.resolve("state.json");
		String truncated = "{\n  \"schemaVersion\": 1,\n  \"packs\": [\n    {\n      \"packId\": \"vitrine/hal";
		Files.writeString(stateFile, truncated, StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION,
				"abgeschnittene Datei darf nicht zum Absturz fuehren, muss auf einen gueltigen Zustand zurueckfallen");
		assertTrue(loaded.packs.isEmpty(), "der abgeschnittene, nicht auswertbare Inhalt darf nicht teilweise uebernommen werden");

		Path backup = tempConfigDir.resolve("state.json.bak");
		assertTrue(Files.exists(backup), "die abgeschnittene Originaldatei muss als Backup erhalten bleiben");
		assertEquals(Files.readString(backup, StandardCharsets.UTF_8), truncated);

		assertTrue(Files.exists(stateFile), "nach dem Laden muss wieder eine gueltige state.json existieren");
		String freshContent = Files.readString(stateFile, StandardCharsets.UTF_8);
		assertTrue(freshContent.contains("\"schemaVersion\": " + PackState.CURRENT_SCHEMA_VERSION));

		// Und das Ergebnis muss selbst wieder normal ladbar sein -- kein
		// Folgefehler durch den Wiederherstellungspfad.
		PackState reloaded = PackStateStore.load(tempConfigDir);
		assertEquals(reloaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(reloaded.packs.isEmpty());
	}

	@Test
	public void crashBetweenTempFileAndAtomicMoveLeavesPreviousValidStateIntact() throws IOException {
		// Ein vorher erfolgreich gespeicherter, gueltiger Zustand.
		PackState previous = PackState.createDefault();
		previous.addOrUpdate("vitrine/bestehend", true);
		PackStateStore.save(tempConfigDir, previous);

		// Simuliert genau den Absturz-Zeitpunkt, den save() per ATOMIC_MOVE
		// verhindert: der Prozess stirbt, nachdem die temporaere Datei
		// angelegt (und halb beschrieben) wurde, aber bevor sie ueber
		// state.json geschoben wurde. state.json selbst bleibt dabei
		// unangetastet -- genau das beweist dieser Test.
		Path halfWrittenTemp = tempConfigDir.resolve("state.json1234567890.tmp");
		Files.writeString(halfWrittenTemp, "{\n  \"schemaVersion\": 1,\n  \"packs\": [ { \"packId\": \"vitr",
				StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.installedPackIdsInOrder(), List.of("vitrine/bestehend"),
				"ein Absturz zwischen Temp-Datei und atomarem Verschieben darf den zuvor gueltigen Zustand nicht antasten");
		assertTrue(loaded.isActive("vitrine/bestehend"));

		// Die Datei mit der bereits geschriebenen state.json muss immer noch
		// vollstaendig und gueltig sein -- load() darf sie nicht angefasst
		// haben, obwohl die kaputte Leiche einer Temp-Datei danebenliegt.
		Path stateFile = tempConfigDir.resolve("state.json");
		assertTrue(Files.exists(stateFile));
		assertTrue(Files.exists(halfWrittenTemp), "die verwaiste Temp-Datei bleibt liegen, wird aber von load() ignoriert");
	}

	// --- Kaputte Datei -> leerer Default + Backup ---------------------------

	@Test
	public void invalidJsonIsBackedUpAndEmptyDefaultsAreReturned() throws IOException {
		Path stateFile = tempConfigDir.resolve("state.json");
		Files.writeString(stateFile, "{ das ist kein json", StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(loaded.packs.isEmpty());

		Path backup = tempConfigDir.resolve("state.json.bak");
		assertTrue(Files.exists(backup));
		assertEquals(Files.readString(backup, StandardCharsets.UTF_8), "{ das ist kein json");
	}

	@Test
	public void duplicatePackIdsAreTreatedAsCorrupt() throws IOException {
		Path stateFile = tempConfigDir.resolve("state.json");
		String duplicated = "{ \"schemaVersion\": 1, \"packs\": ["
				+ "{ \"packId\": \"vitrine/dup\", \"active\": true },"
				+ "{ \"packId\": \"vitrine/dup\", \"active\": false }"
				+ "] }";
		Files.writeString(stateFile, duplicated, StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(loaded.packs.isEmpty(), "widerspruechliche (doppelte) Paket-IDs duerfen nicht uebernommen werden");
		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak")));
	}

	@Test
	public void missingSchemaVersionFieldIsTreatedAsCorrupt() throws IOException {
		Path stateFile = tempConfigDir.resolve("state.json");
		Files.writeString(stateFile, "{ \"packs\": [] }", StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak")));
	}

	@Test
	public void futureSchemaVersionIsBackedUpInsteadOfCrashing() throws IOException {
		Path stateFile = tempConfigDir.resolve("state.json");
		int impossiblyFutureVersion = PackState.CURRENT_SCHEMA_VERSION + 1000;
		Files.writeString(stateFile, "{ \"schemaVersion\": " + impossiblyFutureVersion + " }", StandardCharsets.UTF_8);

		PackState loaded = PackStateStore.load(tempConfigDir);

		assertEquals(loaded.schemaVersion, PackState.CURRENT_SCHEMA_VERSION);
		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak")));
	}

	@Test
	public void repeatedCorruptionGetsUniquelyNumberedBackups() throws IOException {
		Path stateFile = tempConfigDir.resolve("state.json");

		Files.writeString(stateFile, "kaputt-1", StandardCharsets.UTF_8);
		PackStateStore.load(tempConfigDir);
		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak")));

		Files.writeString(stateFile, "kaputt-2", StandardCharsets.UTF_8);
		PackStateStore.load(tempConfigDir);

		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak")), "erstes Backup darf nicht verschwinden");
		assertTrue(Files.exists(tempConfigDir.resolve("state.json.bak.1")),
				"zweites Backup braucht einen eigenen, eindeutigen Namen");
	}

	// --- Deinstallieren (Vorarbeit fuer H5) ---------------------------------

	@Test
	public void removeDropsEntryFromInstalledAndActiveLists() {
		PackState state = PackState.createDefault();
		state.addOrUpdate("vitrine/bleibt", true);
		state.addOrUpdate("vitrine/geht", true);

		boolean changed = state.remove("vitrine/geht");

		assertTrue(changed);
		assertEquals(state.installedPackIdsInOrder(), List.of("vitrine/bleibt"));
		assertFalse(state.isInstalled("vitrine/geht"));
	}
}
