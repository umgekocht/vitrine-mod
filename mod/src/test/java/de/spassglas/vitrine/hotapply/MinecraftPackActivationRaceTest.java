package de.spassglas.vitrine.hotapply;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourcePackManager;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE112, erster Teil des woertlichen Fertig-Kriteriums: "jeder
 * enable/disable-Aufruf laeuft ueber {@code client.execute(...)}".
 *
 * <p>Treibt den <em>echten</em> Fehlerpfad nach, den critic-adversary belegt
 * hatte: {@link ResourcePackManager#enable} liest sein {@code enabled}-Feld,
 * haengt das eigene Profil an eine <b>Kopie</b> der Liste und schreibt die
 * Kopie zurueck (Lese-Aendere-Schreibe, keine eigene Synchronisierung --
 * siehe Bytecode von {@code ResourcePackManager.enable(String)}: {@code
 * getfield enabled}, {@code Lists.newArrayList}, {@code add}, {@code putfield
 * enabled}, ohne jede Sperre). Rufen mehrere Threads das gleichzeitig auf,
 * lesen alle dieselbe alte Liste und nur der zuletzt zurueckschreibende
 * Thread "gewinnt" -- die uebrigen Aktivierungen verschwinden, obwohl
 * {@code enable(...)} jeweils {@code true} zurueckgab (vorgegaukelter
 * Erfolg, Charter-Regel 2).</p>
 *
 * <p>Dieser Test baut einen echten, aber ohne laufenden Client erzeugten
 * {@link ResourcePackManager} (Konstruktor {@code
 * ResourcePackManager(ResourcePackProvider...)}, dieselbe Datenstruktur, auf
 * die auch {@link VitrinePackProvider} seine Profile anmeldet) mit 40 echten,
 * ueber {@link VitrinePackProvider} als gueltig geprueften Paketen, haengt
 * {@link MinecraftPackActivation} an einen Fake-{@link ClientThreadExecutor}
 * ({@link SerializingClientThreadExecutor}), der mehrere gleichzeitig
 * aufrufende Threads ueber einen einzigen Hintergrund-Thread serialisiert --
 * genau das Muster, das {@link MinecraftClientThreadExecutor} mit dem echten
 * Client-Thread umsetzt --, und ruft {@code activate(...)} fuer alle 40
 * Pakete gleichzeitig auf (gemeinsames Startsignal, siehe {@code
 * PackStateStoreConcurrencyTest}, GATE41, fuer dasselbe Testmuster). Geprueft
 * wird der <em>Zustand am Manager danach</em> ({@code getEnabledIds()}),
 * nicht nur, dass jeder Aufruf {@code true} zurueckgegeben hat -- genau das
 * war beim critic-adversary-Befund der Unterschied zwischen "sieht aktiv
 * aus" und "ist tatsaechlich aktiv".</p>
 */
public final class MinecraftPackActivationRaceTest {

	private static final int PACKAGE_COUNT = 40;

	private Path tempDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-pack-activation-race-test-");
		packsDir = Files.createDirectories(tempDir.resolve("packs"));
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	@Test
	public void vierzigGleichzeitigeAktivierungenKommenAlleAmManagerAn() throws Exception {
		List<String> profileIds = new ArrayList<>();
		for (int i = 0; i < PACKAGE_COUNT; i++) {
			String manifestId = "race.paket" + i;
			installPack(manifestId, i);
			profileIds.add(VitrinePackProvider.profileId(manifestId));
		}

		ResourcePackManager manager = new ResourcePackManager(VitrinePackProvider.forPacksDir(packsDir));
		manager.scanPacks();
		assertEquals(manager.getIds().size(), PACKAGE_COUNT,
				"alle " + PACKAGE_COUNT + " Pakete muessen als Profil angemeldet sein, bevor ueberhaupt aktiviert wird");
		assertTrue(manager.getEnabledIds().isEmpty(), "vor der Aktivierung darf noch keines aktiv sein");

		SerializingClientThreadExecutor clientThread = new SerializingClientThreadExecutor();
		try {
			MinecraftPackActivation activation = new MinecraftPackActivation(clientThread, () -> manager);

			ExecutorService pool = Executors.newFixedThreadPool(PACKAGE_COUNT);
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<Boolean>> results = new ArrayList<>();
				for (String profileId : profileIds) {
					results.add(pool.submit(() -> {
						start.await();
						return activation.activate(profileId);
					}));
				}
				start.countDown();
				for (Future<Boolean> result : results) {
					assertTrue(result.get(30, TimeUnit.SECONDS),
							"jede Aktivierung muss den Zustand tatsaechlich geaendert haben (vorher inaktiv)");
				}
			} finally {
				pool.shutdownNow();
			}
		} finally {
			clientThread.shutdown();
		}

		assertEquals(manager.getEnabledIds().size(), PACKAGE_COUNT,
				"alle " + PACKAGE_COUNT + " parallelen Aktivierungen muessen am Manager ankommen, keine darf durch "
						+ "das read-modify-write von ResourcePackManager#enable verloren gehen (GATE112)");
		for (String profileId : profileIds) {
			assertTrue(manager.getEnabledIds().contains(profileId), "verloren gegangen: " + profileId);
		}
	}

	// --- Hilfsmittel (wie LibraryScreenRowLockTest/VitrinePackProviderTest) ----

	private void installPack(String manifestId, int index) throws IOException {
		byte[] bytes = VpkgFixtures.packageWithVersion(manifestId, "Rennpaket " + index, "1.0.0", List.of());
		Path vpkgFile = packsDir.resolve("paket" + index + ".vpkg");
		Files.write(vpkgFile, bytes);
		writeValidSidecar(vpkgFile, bytes);
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

	/**
	 * Fake fuer {@link ClientThreadExecutor}: serialisiert alle Aufrufe ueber
	 * einen einzigen, selbst gestarteten Hintergrund-Thread -- genau das
	 * Muster, das {@link MinecraftClientThreadExecutor} mit dem echten
	 * Client-Thread umsetzt (eine Aufgabenschlange, seriell abgearbeitet),
	 * nur ohne einen echten {@code MinecraftClient} zu brauchen. Beweist,
	 * dass die Serialisierung selbst (nicht irgendein Zufall bei der
	 * Thread-Planung) die Rennbedingung verhindert: alle 40 {@code
	 * runAndWait}-Aufrufe muessen sich diesen einen Thread teilen.
	 */
	private static final class SerializingClientThreadExecutor implements ClientThreadExecutor {

		private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
		private final Thread worker;
		private volatile boolean running = true;

		SerializingClientThreadExecutor() {
			worker = new Thread(this::drain, "vitrine-test-fake-client-thread");
			worker.setDaemon(true);
			worker.start();
		}

		private void drain() {
			while (running) {
				try {
					Runnable task = queue.poll(100, TimeUnit.MILLISECONDS);
					if (task != null) {
						task.run();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}

		@Override
		public <T> T runAndWait(Supplier<T> task) {
			CompletableFuture<T> result = new CompletableFuture<>();
			queue.add(() -> {
				try {
					result.complete(task.get());
				} catch (Throwable t) {
					result.completeExceptionally(t);
				}
			});
			return result.join();
		}

		void shutdown() {
			running = false;
			worker.interrupt();
		}
	}
}
