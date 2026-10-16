package de.spassglas.vitrine.hud;

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
 * ROADMAP T6, Punkt 3 + 4: die client-freie Editor-Logik ({@link HudEditorModel})
 * verschiebt, richtet aus und schaltet Widgets an/aus -- und jede dieser
 * Aenderungen ueberlebt einen "Neustart" (frisches Modell aus derselben
 * {@code overrides.json}). Belegt damit das Fertig-Kriterium "Position
 * ueberlebt Neustart" auf dem headless pruefbaren Teil.
 */
public final class HudEditorModelTest {

	private static final String PACK = "example/hud";

	private Path configDir;

	@BeforeMethod
	public void createTempConfigDir() throws IOException {
		configDir = Files.createTempDirectory("vitrine-hud-editor-test-");
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

	private static HudWidget.Text textWidget(String id, HudAnchor anchor, HudOffset offset) {
		return new HudWidget.Text(id, anchor, offset, 1.0, HudVisibility.Always.INSTANCE,
				new HudTextContent.Static(id));
	}

	private List<HudWidgetRef> twoWidgets() {
		return List.of(
				new HudWidgetRef(PACK, textWidget("fps", HudAnchor.TOP_LEFT, new HudOffset(4, 4))),
				new HudWidgetRef(PACK, textWidget("coords", HudAnchor.BOTTOM_LEFT, new HudOffset(4, -4))));
	}

	private HudEditorModel freshModel() {
		return HudEditorModel.forConfigDir(twoWidgets(), configDir);
	}

	@Test
	public void draggingAWidgetAndSavingSurvivesRestart() {
		HudEditorModel editing = freshModel();
		editing.select(0);
		editing.moveBy(30, 10);   // Maus-Ziehen
		editing.moveBy(6, 0);     // eine Pfeiltasten-Feinverschiebung obendrauf
		editing.save();

		// "Neustart": frisches Modell, frisch aus der Datei geladene Overrides.
		HudEditorModel afterRestart = freshModel();
		HudWidget resolved = afterRestart.effectiveWidget(0);

		// Paket-Vorgabe war (4,4); jetzt (4+36, 4+10).
		assertEquals(resolved.offset(), new HudOffset(40, 14));
		assertEquals(resolved.anchor(), HudAnchor.TOP_LEFT, "nur der Versatz war angepasst");
		// Das zweite Widget blieb unangetastet.
		assertEquals(afterRestart.effectiveWidget(1).offset(), new HudOffset(4, -4));
	}

	@Test
	public void nudgingWithArrowKeysAccumulatesAndPersists() {
		HudEditorModel editing = freshModel();
		editing.select(1);
		editing.moveBy(0, -1);
		editing.moveBy(0, -1);
		editing.moveBy(-1, 0);
		editing.save();

		HudWidget resolved = freshModel().effectiveWidget(1);
		assertEquals(resolved.offset(), new HudOffset(3, -6));
	}

	@Test
	public void anchorCycleSurvivesRestart() {
		HudEditorModel editing = freshModel();
		editing.select(0);
		HudAnchor expected = HudAnchor.values()[(HudAnchor.TOP_LEFT.ordinal() + 1) % HudAnchor.values().length];
		editing.cycleAnchor();
		editing.save();

		assertEquals(freshModel().effectiveWidget(0).anchor(), expected);
	}

	@Test
	public void visibilityToggleCyclesDefaultOnOffAndPersists() {
		HudEditorModel editing = freshModel();
		editing.select(0);

		editing.cycleVisibility();   // default -> an
		assertEquals(editing.selectedVisibilityOverride().orElse(null), Boolean.TRUE);
		editing.cycleVisibility();   // an -> aus
		assertEquals(editing.selectedVisibilityOverride().orElse(null), Boolean.FALSE);
		editing.save();

		HudEditorModel afterRestart = freshModel();
		assertTrue(afterRestart.effectiveWidget(0).visibility() instanceof HudVisibility.Hidden);

		afterRestart.select(0);
		afterRestart.cycleVisibility();   // aus -> zurueck auf Paket-Vorgabe
		assertFalse(afterRestart.selectedVisibilityOverride().isPresent());
		afterRestart.save();

		HudWidget resolved = freshModel().effectiveWidget(0);
		assertTrue(resolved.visibility() instanceof HudVisibility.Always);
	}

	@Test
	public void resetSelectedDropsAllOverridesForThatWidget() {
		HudEditorModel editing = freshModel();
		editing.select(0);
		editing.moveBy(50, 50);
		editing.cycleAnchor();
		editing.cycleVisibility();
		editing.resetSelected();
		editing.save();

		HudEditorModel afterRestart = freshModel();
		HudWidget resolved = afterRestart.effectiveWidget(0);
		assertEquals(resolved.offset(), new HudOffset(4, 4));
		assertEquals(resolved.anchor(), HudAnchor.TOP_LEFT);
		assertTrue(resolved.visibility() instanceof HudVisibility.Always);
		assertTrue(afterRestart.overrides().isEmpty());
	}

	@Test
	public void unsavedEditsDoNotReachDiskAfterRestart() {
		HudEditorModel editing = freshModel();
		editing.select(0);
		editing.moveBy(99, 99);
		// kein save()

		HudWidget resolved = freshModel().effectiveWidget(0);
		assertEquals(resolved.offset(), new HudOffset(4, 4), "ohne Speichern darf nichts persistiert sein");
	}

	@Test
	public void movingBackToThePackageOffsetRemovesTheOverride() {
		HudEditorModel editing = freshModel();
		editing.select(0);
		editing.moveBy(10, 0);
		editing.moveBy(-10, 0);   // wieder auf (4,4)
		editing.save();

		assertTrue(freshModel().overrides().isEmpty(),
				"ein auf den Paket-Wert zurueckgeschobenes Widget braucht keinen Override");
	}

	@Test
	public void corruptOverridesFileLeavesTheEditorUsable() throws IOException {
		Files.createDirectories(configDir.resolve("hud"));
		Files.writeString(configDir.resolve("hud").resolve("overrides.json"), "totaler unsinn",
				StandardCharsets.UTF_8);

		HudEditorModel model = freshModel();   // darf nicht werfen
		assertTrue(model.hasWidgets());
		assertTrue(model.overrides().isEmpty());
		assertTrue(Files.exists(configDir.resolve("hud").resolve("overrides.json.bak")));
	}
}
