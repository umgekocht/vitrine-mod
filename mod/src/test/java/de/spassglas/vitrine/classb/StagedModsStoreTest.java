package de.spassglas.vitrine.classb;

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
 * ROADMAP J2: {@code staged-mods.json} haelt die gestageten Klasse-B-Jars fest
 * und ueberlebt einen Absturz mitten im Schreiben -- Muster wie
 * {@code PackStateStoreTest} (H4).
 */
public final class StagedModsStoreTest {

	private Path dir;

	@BeforeMethod
	public void setUp() throws IOException {
		dir = Files.createTempDirectory("vitrine-staged-store-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	@Test
	public void missingFileYieldsEmptyListWithoutWriting() {
		List<StagedMod> loaded = StagedModsStore.load(dir);

		assertTrue(loaded.isEmpty());
		assertFalse(Files.exists(dir.resolve("staged-mods.json")), "ohne Eintrag wird nichts geschrieben");
	}

	@Test
	public void allFieldsSurviveARoundTrip() throws IOException {
		StagedMod a = new StagedMod("de.a.one", "one-1.0.0.jar", "aa11", "Autorin A", "One", "2026-09-10T10:00:00Z", true);
		StagedMod b = new StagedMod("de.a.two", "two-2.0.0.jar", "bb22", "Autor B", "Two", "2026-09-10T11:00:00Z", false);

		StagedModsStore.save(dir, List.of(a, b));
		List<StagedMod> loaded = StagedModsStore.load(dir);

		assertEquals(loaded, List.of(a, b));
	}

	@Test
	public void saveWritesAtomicallyLeavingNoTempFileBehind() throws IOException {
		StagedModsStore.save(dir, List.of(
				new StagedMod("de.a.one", "one-1.0.0.jar", "aa11", "A", "One", "t", true)));

		try (Stream<Path> entries = Files.list(dir)) {
			assertEquals(entries.map(p -> p.getFileName().toString()).toList(), List.of("staged-mods.json"),
					"nur die Zieldatei, keine .tmp-Reste");
		}
	}

	// --- Fertig-Kriterium: Absturz mitten im Schreiben --------------------

	@Test
	public void stagedStateSurvivesAProcessAbort() throws IOException {
		Path file = dir.resolve("staged-mods.json");
		// Eine durch einen Absturz mitten im Schreiben abgeschnittene Datei.
		String truncated = "{\n  \"schemaVersion\": 1,\n  \"staged\": [\n    {\n      \"id\": \"de.a.ha";
		Files.writeString(file, truncated, StandardCharsets.UTF_8);

		List<StagedMod> loaded = StagedModsStore.load(dir);

		assertTrue(loaded.isEmpty(), "abgeschnittener Inhalt darf nicht teilweise uebernommen werden");
		Path backup = dir.resolve("staged-mods.json.bak");
		assertTrue(Files.exists(backup), "die abgeschnittene Originaldatei bleibt als Backup erhalten");
		assertEquals(Files.readString(backup, StandardCharsets.UTF_8), truncated);

		// Und danach ist der Store wieder normal benutzbar.
		StagedMod fresh = new StagedMod("de.a.fresh", "fresh-1.0.0.jar", "cc33", "A", "Fresh", "t", true);
		StagedModsStore.save(dir, List.of(fresh));
		assertEquals(StagedModsStore.load(dir), List.of(fresh));
	}

	@Test
	public void crashBetweenTempFileAndAtomicMoveLeavesPreviousValidStateIntact() throws IOException {
		StagedMod previous = new StagedMod("de.a.bestehend", "b-1.0.0.jar", "dd44", "A", "B", "t", true);
		StagedModsStore.save(dir, List.of(previous));

		// Verwaiste, halb geschriebene Temp-Datei neben der gueltigen Zieldatei.
		Files.writeString(dir.resolve("staged-mods.json987654.tmp"),
				"{\n  \"schemaVersion\": 1,\n  \"staged\": [ { \"id\": \"de.a.hal", StandardCharsets.UTF_8);

		assertEquals(StagedModsStore.load(dir), List.of(previous),
				"ein Absturz zwischen Temp-Datei und atomarem Verschieben tastet den gueltigen Zustand nicht an");
	}

	@Test
	public void duplicateIdsAreTreatedAsCorrupt() throws IOException {
		Path file = dir.resolve("staged-mods.json");
		String dup = "{ \"schemaVersion\": 1, \"staged\": ["
				+ "{ \"id\": \"de.a.dup\", \"fileName\": \"x-1.jar\", \"sha256\": \"aa\", \"pendingRestart\": true },"
				+ "{ \"id\": \"de.a.dup\", \"fileName\": \"y-1.jar\", \"sha256\": \"bb\", \"pendingRestart\": true }"
				+ "] }";
		Files.writeString(file, dup, StandardCharsets.UTF_8);

		assertTrue(StagedModsStore.load(dir).isEmpty(), "doppelte ids sind widerspruechlich");
		assertTrue(Files.exists(dir.resolve("staged-mods.json.bak")));
	}

	@Test
	public void unsafeFileNameIsTreatedAsCorrupt() throws IOException {
		Path file = dir.resolve("staged-mods.json");
		String evil = "{ \"schemaVersion\": 1, \"staged\": ["
				+ "{ \"id\": \"de.a.evil\", \"fileName\": \"../evil.jar\", \"sha256\": \"aa\", \"pendingRestart\": true }"
				+ "] }";
		Files.writeString(file, evil, StandardCharsets.UTF_8);

		assertTrue(StagedModsStore.load(dir).isEmpty(), "ein '..'-Dateiname wird nicht uebernommen");
		assertTrue(Files.exists(dir.resolve("staged-mods.json.bak")));
	}
}
