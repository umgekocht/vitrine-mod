package de.spassglas.vitrine.ui.screen;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.library.LibraryEntry;
import de.spassglas.vitrine.ui.library.LibraryModel;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import net.minecraft.SharedConstants;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE119-Beweis: {@code LibraryScreen#onToggleActive} rief vorher
 * {@code LibraryModel#setActive} (dahinter {@code PackInstaller#setActive}:
 * {@code .vpkg} als ZIP oeffnen, Manifest parsen, Signatur pruefen,
 * {@code state.json} schreiben) direkt auf dem aufrufenden Thread auf --
 * beim echten Client der Render-Thread, Frame-Stocker beim Umschalten.
 *
 * <p>Kein Screen-Test im gesamten Projekt baut einen Bildschirm mit einem
 * echten, laufenden {@code MinecraftClient} auf (siehe {@code
 * LibraryScreenThemeChangeSchedulingTest}s Klassendoku) -- die Verzweigung
 * "mit Client ein eigener Daemon-Thread" liesse sich damit gar nicht
 * kopffrei durchspielen. {@link LibraryScreen} legt deshalb denselben
 * schmalen Test-Seitenkanal frei wie {@code
 * LibraryScreen#setClientThreadSchedulerForTest} (GATE113): {@link
 * LibraryScreen#setBackgroundWorkSchedulerForTest} ersetzt, wie die
 * eigentliche Arbeit vom aufrufenden Thread weggeschoben wird.</p>
 *
 * <p>Dieser Test ersetzt den Scheduler durch einen echten, zweiten Thread,
 * der die Arbeit erst nach einem expliziten Freigabe-Signal ({@code
 * releaseIo}) ausfuehrt. Kehrt {@code onToggleActive(...)} trotzdem sofort
 * zurueck (statt auf die Freigabe zu warten/zu haengen -- das
 * {@code @Test(timeOut=...)} faengt ein Haengenbleiben ab), ist damit
 * bewiesen, dass {@code model.setActive(...)} nicht auf dem aufrufenden
 * Thread laeuft. Nach der Freigabe beweist der Test zusaetzlich, dass das
 * Ergebnis korrekt auf den aufrufenden Thread zurueckkommt: der
 * Aktiv-Zustand stimmt (echtes installiertes Paket wie {@code
 * LibraryScreenRowLockTest}, damit {@code LibraryModel#entries()} die Zeile
 * nach der Hintergrund-Arbeit wirklich neu baut), und der Zeilen-Schalter
 * ist wieder bedienbar.</p>
 */
public final class LibraryScreenToggleActiveAsyncTest {

	private static final String PACK_ID = "beispiel.paket";
	private static final String PROFILE_ID = "vitrine/" + PACK_ID;

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-library-toggle-async-test-");
		packsDir = Files.createDirectories(configDir.resolve("packs"));
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (configDir == null || !Files.exists(configDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(configDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	@Test(timeOut = 10000)
	public void toggleActiveRunsIoOffCallingThreadAndReturnsCorrectResultAfterwards() throws Exception {
		installPack("a.vpkg", PACK_ID, "Beispielpaket", "1.0.0");
		writeState(PROFILE_ID);

		List<String> setActiveCalls = new ArrayList<>();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				affectedPackIds -> {
				},
				(packId, active) -> setActiveCalls.add(packId + "=" + active),
				packId -> {
				});
		LibraryScreen screen = new LibraryScreen(null, model);
		screen.init();
		LibraryEntry entry = findEntry(model);

		assertTrue(screen.isToggleActiveFor(PROFILE_ID), "im Ruhezustand muss der Zeilen-Schalter bedienbar sein");

		CountDownLatch releaseIo = new CountDownLatch(1);
		CountDownLatch ioDone = new CountDownLatch(1);
		AtomicReference<Thread> ioThread = new AtomicReference<>();
		Thread callingThread = Thread.currentThread();

		screen.setBackgroundWorkSchedulerForTest(work -> {
			Thread worker = new Thread(() -> {
				try {
					releaseIo.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				ioThread.set(Thread.currentThread());
				work.run();
				ioDone.countDown();
			}, "vitrine-toggle-active-async-test-worker");
			worker.setDaemon(true);
			worker.start();
		});

		// Beweis 1 (nicht blockierend): dieser Aufruf haengt hinter releaseIo auf
		// dem Hintergrund-Thread -- kehrt er trotzdem sofort zurueck (das
		// @Test(timeOut=...) oben faengt ein Haengenbleiben ab), lief die E/A
		// nicht auf diesem (dem "Render"-)Thread.
		screen.onToggleActive(entry, false);

		assertTrue(setActiveCalls.isEmpty(),
				"model.setActive() darf jetzt noch nicht gelaufen sein -- es wartet auf releaseIo auf dem "
						+ "Hintergrund-Thread; dass onToggleActive() trotzdem schon zurueck ist, beweist, dass die "
						+ "E/A nicht auf dem aufrufenden Thread lief");
		assertFalse(screen.isToggleActiveFor(PROFILE_ID),
				"der Zeilen-Schalter muss gesperrt sein, waehrend seine eigene E/A noch laeuft (Doppelklick-Schutz)");

		releaseIo.countDown();
		assertTrue(ioDone.await(5, TimeUnit.SECONDS), "die Hintergrund-E/A muss abschliessen");

		// Beweis 2 (anderer Thread): die E/A lief nachweislich nicht auf dem
		// aufrufenden Thread.
		assertNotEquals(ioThread.get(), callingThread,
				"model.setActive() (ZIP oeffnen, Manifest, Signatur, state.json) muss auf einem anderen Thread "
						+ "laufen als dem, der onToggleActive() aufgerufen hat");

		// Beweis 3 (Ergebnis kommt korrekt zurueck): genau ein Aufruf, richtiger
		// Aktiv-Zustand, Schalter wieder bedienbar.
		assertEquals(setActiveCalls, List.of(PROFILE_ID + "=false"),
				"nach Abschluss muss das Ergebnis korrekt zurueckgekommen sein -- genau ein Aufruf mit dem "
						+ "richtigen Aktiv-Zustand");
		assertTrue(screen.isToggleActiveFor(PROFILE_ID),
				"nach Abschluss der E/A muss der Zeilen-Schalter wieder bedienbar sein");
	}

	// --- Hilfsmittel (wie LibraryScreenRowLockTest) ----------------------

	private static LibraryEntry findEntry(LibraryModel model) {
		return model.entries().stream().filter(e -> e.packId().equals(PROFILE_ID)).findFirst()
				.orElseThrow(() -> new AssertionError("Zeile '" + PROFILE_ID + "' nicht gefunden"));
	}

	private void installPack(String fileName, String manifestId, String name, String version) throws IOException {
		byte[] bytes = VpkgFixtures.packageWithVersion(manifestId, name, version, List.of());
		Path vpkgFile = packsDir.resolve(fileName);
		Files.write(vpkgFile, bytes);
		writeValidSidecar(vpkgFile, bytes);
	}

	private void writeState(String... profileIds) {
		PackState state = PackState.createDefault();
		for (String profileId : profileIds) {
			state.addOrUpdate(profileId, true);
		}
		try {
			PackStateStore.save(configDir, state);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(packageBytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded()));
		Path sidecar = vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + ".integrity.json");
		Files.writeString(sidecar, json.toString(), StandardCharsets.UTF_8);
	}
}
