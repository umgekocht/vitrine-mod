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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE81 ("Fehlgeschlagenes Update/Rollback bleibt unerklärt"):
 * vorher setzte {@code LibraryScreen#onUpdateAll} bei einem fehlgeschlagenen
 * Update nur {@code DownloadFlyoutService.markFailed(...)} -- sichtbar nur im
 * Download-Flyout, das erst geoeffnet werden musste. {@code
 * LibraryModel#failedUpdatePackIds()} und der Lang-Key {@code
 * vitrine.library.update_failed} existierten zwar schon, waren aber nirgends
 * verdrahtet.
 *
 * <p>Dieser Test installiert ein Paket, markiert es in {@code state.json} als
 * "Update fehlgeschlagen" (genau das, was {@code PackUpdater} nach einem
 * gescheiterten Update tut) und beweist: die Bibliothekszeile dieses Pakets
 * zeigt den Klartext-Marker direkt -- ohne jedes Flyout.</p>
 */
public final class LibraryScreenUpdateFailedMarkerTest {

	private static final String A_ID = "beispiel.paketa";
	private static final String B_ID = "beispiel.paketb";
	private static final String A_PROFILE = "vitrine/" + A_ID;
	private static final String B_PROFILE = "vitrine/" + B_ID;

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-library-update-failed-marker-test-");
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

	@Test
	public void rowOfTheFailedPackageShowsTheMarkerWithoutOpeningAnyFlyout() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", "1.0.0");
		installPack("b.vpkg", B_ID, "Paket B", "2.0.0");
		writeState(A_PROFILE, B_PROFILE);
		markUpdateFailed(A_PROFILE);

		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				affectedPackIds -> {
				}, (packId, active) -> {
				}, packId -> {
				});

		LibraryScreen screen = new LibraryScreen(null, model);
		screen.init();

		Set<String> failed = screen.failedUpdatePackIds();
		assertTrue(failed.contains(A_PROFILE), "A ist in state.json als 'Update fehlgeschlagen' markiert");
		assertFalse(failed.contains(B_PROFILE), "B ist nicht betroffen und darf keinen Marker vorgaukeln");

		LibraryEntry aEntry = findEntry(model, A_PROFILE);
		String aRowText = LibraryScreen.buildMetaText(aEntry, null, failed.contains(aEntry.packId()));
		assertTrue(aRowText.contains("fehlgeschlagen") || aRowText.contains("failed"),
				"die Zeile von A muss den Klartext-Marker zeigen, kein Flyout noetig: " + aRowText);

		LibraryEntry bEntry = findEntry(model, B_PROFILE);
		String bRowText = LibraryScreen.buildMetaText(bEntry, null, failed.contains(bEntry.packId()));
		assertFalse(bRowText.contains("fehlgeschlagen") || bRowText.contains("failed"),
				"die Zeile von B darf den Fehlschlag-Marker von A nicht uebernehmen: " + bRowText);
	}

	// --- Hilfsmittel ----------------------------------------------

	private static LibraryEntry findEntry(LibraryModel model, String packId) {
		return model.entries().stream().filter(e -> e.packId().equals(packId)).findFirst()
				.orElseThrow(() -> new AssertionError("Zeile '" + packId + "' nicht gefunden"));
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

	private void markUpdateFailed(String profileId) {
		try {
			PackStateStore.update(configDir, state -> {
				state.setUpdateFailed(profileId, true);
				return null;
			});
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
