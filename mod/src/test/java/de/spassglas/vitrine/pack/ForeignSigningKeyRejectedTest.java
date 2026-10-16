package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hotapply.DelayedTaskScheduler;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyToastPresenter;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourcePackProfile;
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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE29, woertliches Fertig-Kriterium: "ein Test mit fremdem
 * Ed25519-Schluessel + eigenem Sidecar schlaegt auf jedem Weg fehl."
 *
 * <p>Stellt genau das Angriffsszenario aus critic-security nach, das GATE29
 * ausgeloest hat: ein Angreifer erzeugt sein <b>eigenes</b> Ed25519-
 * Schluesselpaar ({@code foreignKeyPair}), signiert ein Paket damit und legt
 * seinen <b>eigenen</b> oeffentlichen Schluessel in die Begleitdatei
 * ({@code .vpkg.integrity.json}) -- exakt das Format, das eine gueltige
 * Sidecar auch sonst hat. Vor GATE29 pruefte sowohl {@link PackInstaller}
 * (lokaler Installationsweg) als auch {@link VitrinePackProvider#register}
 * (Paket-Scan) die Signatur gegen genau diesen mitgelieferten Schluessel --
 * die Pruefung bestand deshalb immer, unabhaengig davon, wer signiert hatte.
 * Seit GATE29 pruefen beide Wege stattdessen gegen
 * {@code de.spassglas.vitrine.vpkg.EmbeddedSigningKey#load()}, einen vertrauten, vom Angreifer nicht
 * beeinflussbaren Anker (Charter-Regel 3). Dieser Test ersetzt den
 * eingebetteten Schluessel ueber den dafuer vorgesehenen Test-Seam durch ein
 * eigenes "vertrautes" Test-Schluesselpaar und beweist, dass ein mit einem
 * DRITTEN, fremden Schluesselpaar signiertes Paket auf beiden Wegen
 * abgelehnt wird.</p>
 */
public final class ForeignSigningKeyRejectedTest {

	private Path vitrineConfigDir;
	private Path libraryDir;
	private Path packsDir;

	/** Der Schluessel, den {@link SigningKeyTestSupport#install} als "vertraut" setzt -- steht fuer den echten eingebetteten Registry-Schluessel. */
	private KeyPair trustedKeyPair;

	/** Der Schluessel eines Angreifers -- signiert das Paket UND liegt selbst in dessen Sidecar, ist aber niemals der vertraute Anker. */
	private KeyPair foreignKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-foreign-key-test-");
		libraryDir = vitrineConfigDir.resolve("library");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(libraryDir);
		Files.createDirectories(packsDir);

		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		foreignKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (vitrineConfigDir == null || !Files.exists(vitrineConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(vitrineConfigDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// --- Weg 1: PackInstaller (lokale Installation) -------------------------

	@Test
	public void packInstallerRejectsPackageSignedWithAForeignKeyAndLeavesPacksEmpty() throws Exception {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		writeLibraryPackageSignedByForeignKey("angreifer.vpkg", packageBytes);

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		RecordingPackActivation packActivation = new RecordingPackActivation();
		CountingReloadTrigger reloadTrigger = new CountingReloadTrigger();
		HotApplyCoordinator coordinator = new HotApplyCoordinator(
				packActivation, reloadTrigger, new SilentToastPresenter(), new ImmediateScheduler());
		PackInstaller installer = new PackInstaller(provider, coordinator);

		assertThrows(PackInstallException.class, () -> installer.install("beispiel.minimalpaket"));

		assertEquals(regularFileCount(packsDir), 0,
				"ein mit einem fremden Schluessel signiertes Paket darf niemals nach packs/ kopiert werden, "
						+ "auch wenn der Sidecar seinen eigenen (fremden) oeffentlichen Schluessel mitliefert");
		assertTrue(packActivation.activatedIds.isEmpty(), "ein abgelehntes Paket darf nie aktiviert werden");
		assertEquals(reloadTrigger.callCount, 0, "ein abgelehntes Paket darf keinen Reload ausloesen");
	}

	// --- Weg 2: VitrinePackProvider.register (Paket-Scan) -------------------

	@Test
	public void vitrinePackProviderNeverRegistersAPackageSignedWithAForeignKey() throws Exception {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = packsDir.resolve("angreifer.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeForeignSidecar(vpkgFile, packageBytes);

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(packsDir).register(profileAdder);

		assertFalse(profileAdder.profiles.stream().anyMatch(p -> p.getId().equals("vitrine/beispiel.minimalpaket")),
				"ein mit einem fremden Schluessel signiertes Paket darf nie in der profileAdder-Liste landen, "
						+ "auch wenn der Sidecar seinen eigenen (fremden) oeffentlichen Schluessel mitliefert");
		assertEquals(profileAdder.profiles.size(), 0);
	}

	// --- Kontrollbeweis: derselbe Aufbau, aber mit dem VERTRAUTEN Schluessel ---
	// --- signiert -- muss auf beiden Wegen gelingen, sonst waere der Test  ---
	// --- oben nicht aussagekraeftig (er koennte auch aus einem anderen    ---
	// --- Grund als der Schluesselpruefung scheitern).                    ---

	@Test
	public void sameSetupSucceedsOnBothWaysWhenSignedWithTheTrustedKeyInstead() throws Exception {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path libraryVpkg = libraryDir.resolve("legitim.vpkg");
		Files.write(libraryVpkg, packageBytes);
		writeSidecar(libraryVpkg, packageBytes, trustedKeyPair);

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);
		HotApplyCoordinator coordinator = new HotApplyCoordinator(
				new RecordingPackActivation(), new CountingReloadTrigger(), new SilentToastPresenter(),
				new ImmediateScheduler());
		PackInstaller installer = new PackInstaller(provider, coordinator);
		installer.install("beispiel.minimalpaket");
		assertTrue(Files.exists(packsDir.resolve("legitim.vpkg")),
				"mit dem vertrauten Schluessel signiert muss die Installation gelingen");

		Path scannedVpkg = packsDir.resolve("gescannt.vpkg");
		Files.write(scannedVpkg, packageBytes);
		writeSidecar(scannedVpkg, packageBytes, trustedKeyPair);
		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(packsDir).register(profileAdder);
		assertTrue(profileAdder.profiles.stream().anyMatch(p -> p.getId().equals("vitrine/beispiel.minimalpaket")),
				"mit dem vertrauten Schluessel signiert muss die Registrierung gelingen");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private void writeLibraryPackageSignedByForeignKey(String fileName, byte[] packageBytes) throws IOException {
		Path vpkgFile = libraryDir.resolve(fileName);
		Files.write(vpkgFile, packageBytes);
		writeForeignSidecar(vpkgFile, packageBytes);
	}

	/** Signiert mit {@link #foreignKeyPair} UND legt dessen oeffentlichen Teil in die Sidecar -- der Angriff aus critic-security. */
	private void writeForeignSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		writeSidecar(vpkgFile, packageBytes, foreignKeyPair);
	}

	private static void writeSidecar(Path vpkgFile, byte[] packageBytes, KeyPair signer) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, signer.getPrivate());
		writeSidecarRaw(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, signer.getPublic());
	}

	private static void writeSidecarRaw(Path vpkgFile, String sha256, byte[] signature, PublicKey publicKey)
			throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("sha256", sha256);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
		Files.writeString(VpkgIntegritySidecar.sidecarPathFor(vpkgFile), json.toString(), StandardCharsets.UTF_8);
	}

	private static long regularFileCount(Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile).count();
		}
	}

	// --- Test-Doubles ----------------------------------------------------

	private static final class RecordingPackActivation implements PackActivation {
		private final List<String> activatedIds = new ArrayList<>();

		@Override
		public boolean activate(String packId) {
			activatedIds.add(packId);
			return true;
		}

		@Override
		public boolean deactivate(String packId) {
			return true;
		}
	}

	private static final class CountingReloadTrigger implements ResourceReloadTrigger {
		private int callCount;

		@Override
		public CompletableFuture<Void> reloadResources() {
			callCount++;
			return CompletableFuture.completedFuture(null);
		}
	}

	private static final class SilentToastPresenter implements HotApplyToastPresenter {
		@Override
		public void showApplying(Set<String> packIds) {
		}

		@Override
		public void showApplied(Set<String> packIds) {
		}

		@Override
		public void showFailed(Set<String> packIds, String reason) {
		}

		@Override
		public void showRolledBack(Set<String> packIds, String reason) {
		}
	}

	private static final class ImmediateScheduler implements DelayedTaskScheduler {
		@Override
		public Handle scheduleOnce(Runnable task, long delayMillis) {
			task.run();
			return () -> {
			};
		}
	}

	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}
	}
}
