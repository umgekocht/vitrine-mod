package de.spassglas.vitrine.hud;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP T6, Punkt 1 + 4: Nutzer-Overrides ({@link HudLayoutOverrides}) werden
 * atomar nach {@code config/vitrine/hud/overrides.json} geschrieben, ueberleben
 * einen "Neustart" (frische Instanz aus derselben Datei) und eine kaputte
 * Datei fuehrt zu leeren Overrides + Backup statt Absturz.
 */
public final class HudLayoutOverridesTest {

	private static final String PACK = "example/hud";
	private static final String WIDGET = "fps";

	private Path configDir;

	@BeforeMethod
	public void createTempConfigDir() throws IOException {
		configDir = Files.createTempDirectory("vitrine-hud-overrides-test-");
	}

	@AfterMethod
	public void deleteTempConfigDir() throws IOException {
		if (configDir == null || !Files.exists(configDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(configDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private Path overridesFile() {
		return configDir.resolve("hud").resolve("overrides.json");
	}

	@Test
	public void missingFileYieldsEmptyOverridesAndWritesNothing() {
		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);

		assertTrue(overrides.isEmpty());
		assertFalse(Files.exists(overridesFile()), "ohne Anpassung darf keine Datei entstehen");
	}

	@Test
	public void movedWidgetSurvivesRestart() {
		HudLayoutOverrides before = HudLayoutOverrides.forConfigDir(configDir);
		before.setOffset(PACK, WIDGET, new HudOffset(120, 48));
		before.save();

		assertTrue(Files.exists(overridesFile()), "save() muss overrides.json anlegen");

		// "Neustart": frische Instanz aus derselben Datei.
		HudLayoutOverrides afterRestart = HudLayoutOverrides.forConfigDir(configDir);
		HudWidgetOverride restored = afterRestart.find(PACK, WIDGET).orElseThrow();
		assertEquals(restored.offset(), new HudOffset(120, 48));

		// Und die Aufloesung liefert die verschobene Position, nicht die Paket-Vorgabe.
		HudWidget packageWidget = new HudWidget.Text(WIDGET, HudAnchor.TOP_LEFT, new HudOffset(4, 4), 1.0,
				HudVisibility.Always.INSTANCE, new HudTextContent.Static("FPS"));
		HudWidget resolved = afterRestart.apply(PACK, packageWidget);
		assertEquals(resolved.offset(), new HudOffset(120, 48));
	}

	@Test
	public void anchorChangeSurvivesRestart() {
		HudLayoutOverrides before = HudLayoutOverrides.forConfigDir(configDir);
		before.setAnchor(PACK, WIDGET, HudAnchor.BOTTOM_RIGHT);
		before.save();

		HudLayoutOverrides afterRestart = HudLayoutOverrides.forConfigDir(configDir);
		assertEquals(afterRestart.find(PACK, WIDGET).orElseThrow().anchor(), HudAnchor.BOTTOM_RIGHT);
	}

	@Test
	public void visibilityOffSurvivesRestartAndResolvesToHidden() {
		HudLayoutOverrides before = HudLayoutOverrides.forConfigDir(configDir);
		before.setVisible(PACK, WIDGET, false);
		before.save();

		HudLayoutOverrides afterRestart = HudLayoutOverrides.forConfigDir(configDir);
		assertEquals(afterRestart.find(PACK, WIDGET).orElseThrow().visible(), Boolean.FALSE);

		HudWidget packageWidget = new HudWidget.Text(WIDGET, HudAnchor.TOP_LEFT, HudOffset.ZERO, 1.0,
				HudVisibility.Always.INSTANCE, new HudTextContent.Static("FPS"));
		HudWidget resolved = afterRestart.apply(PACK, packageWidget);
		assertTrue(resolved.visibility() instanceof HudVisibility.Hidden);
	}

	@Test
	public void reducingAnOverrideToNothingRemovesItEntirely() {
		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);
		overrides.setVisible(PACK, WIDGET, Boolean.TRUE);
		assertTrue(overrides.find(PACK, WIDGET).isPresent());

		// Zurueck auf die Paket-Sichtbarkeit -> der Eintrag hat keinen Inhalt mehr.
		overrides.setVisible(PACK, WIDGET, null);
		assertTrue(overrides.isEmpty());
	}

	@Test
	public void saveIsAtomicLeavingNoTempFileBehind() {
		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);
		overrides.setOffset(PACK, WIDGET, new HudOffset(1, 2));
		overrides.save();

		try (Stream<Path> entries = Files.list(configDir.resolve("hud"))) {
			assertEquals(entries.map(p -> p.getFileName().toString()).sorted().toList(),
					java.util.List.of("overrides.json"),
					"nach dem Schreiben darf nur overrides.json uebrig sein, keine .tmp-Reste");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void corruptFileYieldsEmptyOverridesAndBackupInsteadOfCrash() throws IOException {
		Files.createDirectories(configDir.resolve("hud"));
		Files.writeString(overridesFile(), "{ das ist kein json", StandardCharsets.UTF_8);

		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);

		assertTrue(overrides.isEmpty(), "kaputte Datei muss zu leeren Overrides fuehren, nicht zum Absturz");
		Path backup = configDir.resolve("hud").resolve("overrides.json.bak");
		assertTrue(Files.exists(backup), "die kaputte Originaldatei muss als Backup erhalten bleiben");
		assertEquals(Files.readString(backup, StandardCharsets.UTF_8), "{ das ist kein json");
	}

	@Test
	public void unknownAnchorInFileIsTreatedAsCorrupt() throws IOException {
		Files.createDirectories(configDir.resolve("hud"));
		Files.writeString(overridesFile(),
				"{\"schemaVersion\":1,\"overrides\":[{\"packId\":\"p\",\"widgetId\":\"w\",\"anchor\":\"nowhere\"}]}",
				StandardCharsets.UTF_8);

		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);

		assertTrue(overrides.isEmpty());
		assertTrue(Files.exists(configDir.resolve("hud").resolve("overrides.json.bak")));
	}

	@Test
	public void futureSchemaVersionIsBackedUpInsteadOfCrashing() throws IOException {
		Files.createDirectories(configDir.resolve("hud"));
		Files.writeString(overridesFile(),
				"{\"schemaVersion\":" + (HudOverridesStore.CURRENT_SCHEMA_VERSION + 1000) + ",\"overrides\":[]}",
				StandardCharsets.UTF_8);

		HudLayoutOverrides overrides = HudLayoutOverrides.forConfigDir(configDir);

		assertTrue(overrides.isEmpty());
		assertTrue(Files.exists(configDir.resolve("hud").resolve("overrides.json.bak")));
	}
}
