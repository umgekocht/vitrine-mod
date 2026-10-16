package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.ui.library.LibraryModel;
import net.minecraft.SharedConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * ROADMAP GATE113: "{@code onThemeChanged()} plant ueber {@code
 * client.execute(...)} statt direkt auszufuehren."
 *
 * <p>Kein Screen-Test im gesamten Projekt baut einen Bildschirm mit einem
 * echten, laufenden {@code MinecraftClient} auf (siehe {@code
 * StoreScreenLocalLibraryRowTest}s Klassendoku) -- {@code onThemeChanged()}
 * selbst liesse sich also mit einem echten Client gar nicht kopffrei
 * durchspielen. {@link LibraryScreen} legt deshalb einen schmalen, nur fuer
 * Tests gedachten Einplanungs-Seitenkanal frei ({@link
 * LibraryScreen#setClientThreadSchedulerForTest}, wiederverwendetes {@code
 * java.util.function.Consumer}, kein neues Interface): die Produktion reicht
 * jede Aufgabe an {@code this.client.execute(...)} weiter ({@link
 * LibraryScreen#scheduleOnClientThread}); dieser Test ersetzt das durch eine
 * aufzeichnende Variante, die die Aufgabe absichtlich <em>nicht</em>
 * ausfuehrt. Ruft dieser Test danach {@code onThemeChanged()} direkt auf --
 * genau das, was der {@code ThemeService}-Rueckruf von einem fremden Thread
 * aus tut --, beweist das Ausbleiben von {@code rebuild()} (kein Anstieg von
 * {@link LibraryScreen#rebuildCountForTest}), dass {@code onThemeChanged()}
 * den Neuaufbau nur einplant, statt ihn selbst auf dem aufrufenden Thread
 * auszufuehren.</p>
 */
public final class LibraryScreenThemeChangeSchedulingTest {

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@Test
	public void onThemeChangedPlantEinStattSofortNeuAufzubauen() throws Exception {
		Path configDir = Files.createTempDirectory("vitrine-library-theme-scheduling-test-");
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				affectedPackIds -> {
				},
				(packId, active) -> {
				},
				packId -> {
				});
		LibraryScreen screen = new LibraryScreen(null, model);
		screen.init();
		int rebuildCountAfterInit = screen.rebuildCountForTest();

		List<Runnable> scheduledTasks = new ArrayList<>();
		Consumer<Runnable> recordingScheduler = scheduledTasks::add;
		screen.setClientThreadSchedulerForTest(recordingScheduler);

		screen.onThemeChanged();

		assertEquals(scheduledTasks.size(), 1,
				"onThemeChanged() muss genau eine Aufgabe einplanen, statt selbst etwas zu tun");
		assertEquals(screen.rebuildCountForTest(), rebuildCountAfterInit,
				"onThemeChanged() darf rebuild() nicht direkt auf dem aufrufenden Thread ausfuehren -- "
						+ "nur die eingeplante Aufgabe darf das spaeter tun");

		// Die eingeplante Aufgabe selbst bleibt korrekt: ohne echten Client
		// (this.client == null, wie in jedem kopffreien Screen-Test) baut sie
		// absichtlich nichts neu auf -- auch das darf kein NPE werfen.
		scheduledTasks.get(0).run();
		assertFalse(scheduledTasks.isEmpty(), "die aufgezeichnete Aufgabe muss abrufbar bleiben");
		assertEquals(screen.rebuildCountForTest(), rebuildCountAfterInit,
				"ohne sichtbaren Client (this.client == null) darf die eingeplante Aufgabe trotzdem nicht neu aufbauen");
	}
}
