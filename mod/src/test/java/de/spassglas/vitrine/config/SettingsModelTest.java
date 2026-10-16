package de.spassglas.vitrine.config;

import de.spassglas.vitrine.ui.motion.MotionSettings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP S9: der Schalter "Animationen reduzieren" schreibt {@code settings.json}
 * und wirkt sofort. {@link SettingsModel} traegt die Schalter-Logik client-frei;
 * dieser Test deckt beides ab -- den Persistenz-Seam mit einem Ersatz-Store und
 * den echten Weg ueber {@link SettingsManager} auf eine Datei.
 */
public final class SettingsModelTest {

	private Path tempDir;

	@AfterMethod
	public void cleanup() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	/** Merkt sich jeden gespeicherten Zustand -- so ist "schreibt sofort" pruefbar. */
	private static final class RecordingStore implements SettingsModel.Store {
		private VitrineSettings state = VitrineSettings.createDefault();
		private final List<Boolean> savedReduceMotion = new ArrayList<>();
		private int saveCount;

		@Override
		public VitrineSettings load() {
			return state;
		}

		@Override
		public void save(VitrineSettings settings) {
			this.state = settings;
			savedReduceMotion.add(settings.reduceMotion);
			saveCount++;
		}
	}

	@Test
	public void toggleWritesThroughEachTimeTheValueChanges() {
		RecordingStore store = new RecordingStore();
		SettingsModel model = new SettingsModel(store);

		assertFalse(model.reduceMotion(), "Default: Animationen an");

		assertTrue(model.toggleReduceMotion());
		assertEquals(store.savedReduceMotion, List.of(true), "erste Umlegung schreibt sofort");

		assertFalse(model.toggleReduceMotion());
		assertEquals(store.savedReduceMotion, List.of(true, false), "zweite Umlegung schreibt wieder");
	}

	@Test
	public void settingTheSameValueDoesNotWriteAgain() {
		RecordingStore store = new RecordingStore();
		SettingsModel model = new SettingsModel(store);

		model.setReduceMotion(false); // schon der Default
		assertTrue(store.savedReduceMotion.isEmpty(), "kein Schreiben ohne Aenderung");
	}

	@Test
	public void toggleReachesSettingsJsonOnDiskAndSurvivesAReload() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-model-");
		Path settingsFile = tempDir.resolve("settings.json");

		SettingsModel model = SettingsModel.forConfigDir(tempDir);
		assertFalse(model.reduceMotion());

		model.toggleReduceMotion();

		assertTrue(Files.exists(settingsFile), "der Schalter muss settings.json schreiben");
		String written = Files.readString(settingsFile).replace(" ", "");
		assertTrue(written.contains("\"reduceMotion\":true"), "settings.json: " + Files.readString(settingsFile));

		// Der offene Store liest genau so: frisch von der Platte bei jedem Aufbau.
		assertTrue(SettingsModel.reduceMotion(tempDir), "ein neuer Leser sieht den umgelegten Schalter");
		assertTrue(SettingsManager.load(tempDir).reduceMotion);
	}

	@Test
	public void javaModOffersOptInDefaultsToOff() {
		SettingsModel model = new SettingsModel(new RecordingStore());
		assertTrue(model.showJavaModOffers(), "umgekocht 2026-10-07: Java-Mods sind ab Werk an");
	}

	@Test
	public void javaModOffersToggleReachesSettingsJsonOnDiskAndSurvivesAReload() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-java-mods-");
		Path settingsFile = tempDir.resolve("settings.json");

		SettingsModel model = SettingsModel.forConfigDir(tempDir);
		assertTrue(model.showJavaModOffers(), "frische settings.json => Java-Mods an");
		model.setShowJavaModOffers(false);

		assertTrue(model.toggleShowJavaModOffers());

		assertTrue(Files.exists(settingsFile), "der Schalter muss settings.json schreiben");
		String written = Files.readString(settingsFile).replace(" ", "");
		assertTrue(written.contains("\"showJavaModOffers\":true"), "settings.json: " + Files.readString(settingsFile));

		// Ein neuer Leser (so liest die JavaModOfferGate) sieht den Schalter.
		assertTrue(SettingsModel.showJavaModOffers(tempDir));
		assertTrue(SettingsManager.load(tempDir).showJavaModOffers);
	}

	@Test
	public void oldSettingsJsonWithoutTheFieldReadsAsOptInOff() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-additive-");
		Path settingsFile = tempDir.resolve("settings.json");
		Files.writeString(settingsFile, "{\"schemaVersion\":1,\"reduceMotion\":true}");

		// Additiv: kein Migrationsschritt, Gson-Default greift.
		// umgekocht 2026-10-07: Java-Mods sind ab Werk an -- auch fuer alte Dateien ohne das Feld.
		assertTrue(SettingsModel.showJavaModOffers(tempDir));
		assertTrue(SettingsManager.load(tempDir).reduceMotion);
	}

	@Test
	public void onboardingSeenDefaultsToFalse() {
		SettingsModel model = new SettingsModel(new RecordingStore());
		assertFalse(model.onboardingSeen(),
				"ROADMAP X4: ein frischer Spielstand hat den Erster-Start-Ablauf noch nicht bestaetigt");
	}

	@Test
	public void settingOnboardingSeenWritesThroughOnlyOnceForTheSameValue() {
		RecordingStore store = new RecordingStore();
		SettingsModel model = new SettingsModel(store);

		model.setOnboardingSeen(true);
		assertTrue(model.onboardingSeen());
		assertEquals(store.saveCount, 1, "erste Bestaetigung schreibt sofort");

		model.setOnboardingSeen(true);
		assertEquals(store.saveCount, 1, "zweite Bestaetigung mit demselben Wert schreibt nicht erneut");
	}

	@Test
	public void onboardingSeenReachesSettingsJsonOnDiskAndSurvivesAReload() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-onboarding-");
		Path settingsFile = tempDir.resolve("settings.json");

		SettingsModel model = SettingsModel.forConfigDir(tempDir);
		assertFalse(model.onboardingSeen(), "frische settings.json => Onboarding noch nicht gesehen");

		model.setOnboardingSeen(true);

		assertTrue(Files.exists(settingsFile), "das Bestaetigen muss settings.json schreiben");
		String written = Files.readString(settingsFile).replace(" ", "");
		assertTrue(written.contains("\"onboardingSeen\":true"), "settings.json: " + Files.readString(settingsFile));

		// StoreEntryPoint liest genau so: frisch von der Platte bei jedem Oeffnen.
		assertTrue(SettingsModel.onboardingSeen(tempDir), "ein neuer Leser sieht die Bestaetigung");
	}

	@Test
	public void oldSettingsJsonWithoutOnboardingFieldReadsAsNotSeenYet() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-onboarding-additive-");
		Path settingsFile = tempDir.resolve("settings.json");
		Files.writeString(settingsFile, "{\"schemaVersion\":1,\"reduceMotion\":true}");

		// Additiv wie showJavaModOffers: kein Migrationsschritt, Gson-Default greift
		// -- ein bestehender Spielstand sieht den Ablauf beim naechsten Oeffnen einmal.
		assertFalse(SettingsModel.onboardingSeen(tempDir));
	}

	@Test
	public void switchTakesEffectOnMotionDurationsThatTheOpenStoreUses() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-settings-effect-");
		SettingsModel model = SettingsModel.forConfigDir(tempDir);

		boolean before = SettingsModel.reduceMotion(tempDir);
		assertEquals(MotionSettings.carouselMs(before), 400, "vorher laufen Uebergaenge in Spec-Zeit");

		model.toggleReduceMotion();

		boolean after = SettingsModel.reduceMotion(tempDir);
		assertTrue(after);
		assertEquals(MotionSettings.hoverMs(after), 0);
		assertEquals(MotionSettings.pageMs(after), 0);
		assertEquals(MotionSettings.carouselMs(after), 0);
		assertEquals(MotionSettings.liftPx(after), 0);
	}
}
