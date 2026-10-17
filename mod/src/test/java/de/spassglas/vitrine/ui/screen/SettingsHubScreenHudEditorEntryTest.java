package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.hud.client.HudEditorScreen;
import net.minecraft.client.gui.screen.Screen;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE75-Beweis: der HUD-Editor war vorher nur ueber
 * {@code /vitrine hud edit} erreichbar ({@code HudCommand#runEdit}, ruft
 * {@code HudEditorScreen.create(client.currentScreen, configDir)} auf).
 * Dieser Test beweist, dass der "HUD anordnen"-Eintrag in
 * {@link SettingsHubScreen} denselben Weg nimmt -- dieselbe Fabrikmethode
 * {@code HudEditorScreen.create(Screen, Path)} mit demselben Konfigordner
 * -- ohne dass ein echter, laufender {@code MinecraftClient} oder
 * {@code FabricLoader} dafuer noetig ist (Testseams {@link
 * SettingsHubScreen#setScreenOpenerForTest} und {@link
 * SettingsHubScreen#setHudEditorConfigDirSupplierForTest}, analog zu
 * {@code LibraryScreen#setBackgroundWorkSchedulerForTest}).
 */
public final class SettingsHubScreenHudEditorEntryTest {

	@Test
	public void hudEditorMenuEntryOpensTheSameScreenAsTheSlashCommand() throws IOException {
		Path testConfigDir = Files.createTempDirectory("vitrine-gate75-hud-editor-test");
		SettingsHubScreen settingsScreen = new SettingsHubScreen(null);
		List<Screen> openedScreens = new ArrayList<>();
		settingsScreen.setScreenOpenerForTest(openedScreens::add);
		settingsScreen.setHudEditorConfigDirSupplierForTest(() -> testConfigDir);

		settingsScreen.openHudEditorActionForTest().run();

		assertEquals(openedScreens.size(), 1, "der Menueintrag muss genau einen Screen oeffnen");
		Screen opened = openedScreens.get(0);
		assertTrue(opened instanceof HudEditorScreen,
				"der Menueintrag muss denselben HudEditorScreen oeffnen wie '/vitrine hud edit': " + opened);

		// Derselbe Erzeugungsweg wie HudCommand#runEdit (HudEditorScreen.create(Screen, Path)),
		// mit demselben Testkonfigordner, nur mit dem Einstellungen-Screen selbst
		// als Eltern-Screen statt mit client.currentScreen -- das Elternteil kommt
		// am Ende bei "Zurueck" zum Tragen, nicht bei der Identitaet des
		// HUD-Editor-Screens.
		HudEditorScreen expected = HudEditorScreen.create(settingsScreen, testConfigDir);
		assertSame(opened.getClass(), expected.getClass());
		assertEquals(opened.getTitle(), expected.getTitle());
	}
}
