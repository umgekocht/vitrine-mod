package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
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
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE115: deckt genau den im Befund beschriebenen Absturz-Zustand ab
 * -- ein Spielabbruch zwischen {@code PackUpdater}s "alte Version zur Seite
 * legen" ({@code moveInto(oldVpkg, backupVpkg)}) und "neue Version nach
 * {@code packs/} setzen" ({@code moveInto(stagedVpkg, newInPacks)}): {@code
 * state.json} fuehrt das Paket weiterhin als installiert, {@code packs/} ist
 * dafuer leer, die alte, gueltige Version liegt unberuehrt im Backup-Ordner
 * ({@link PackUpdater#backupDirFor}).
 */
public final class OrphanedUpdateBackupRecoveryTest {

	private static final String PROFILE_ID = "vitrine/beispiel.verwaist";

	private Path vitrineConfigDir;
	private Path packsDir;
	private Path backupDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-orphan-recovery-test-");
		packsDir = Files.createDirectories(vitrineConfigDir.resolve("packs"));
		backupDir = PackUpdater.backupDirFor(vitrineConfigDir);

		// GATE29: die Pruef-Kette in OrphanedUpdateBackupRecovery laeuft gegen
		// EmbeddedSigningKey.load() -- Fixtures muessen deshalb mit dem
		// Test-Override signieren.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (vitrineConfigDir == null || !Files.exists(vitrineConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(vitrineConfigDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
		}
	}

	/** Beweist die Selbstheilung: ein gefundenes, gueltiges Backup wandert zurueck nach {@code packs/}. */
	@Test
	public void recoversAnOrphanedBackupBackIntoPacks() throws IOException {
		byte[] bytes = VpkgFixtures.packageWithVersion("beispiel.verwaist", "Verwaistes Paket", "1.0.0",
				List.of("textures/a.png"));
		Files.createDirectories(backupDir);
		Path backupFile = backupDir.resolve("verwaist.vpkg");
		Files.write(backupFile, bytes);
		writeValidSidecar(backupFile, bytes);

		writeState(PROFILE_ID, true);
		assertTrue(VitrinePackProvider.forConfigDir(vitrineConfigDir).findPackFile(PROFILE_ID).isEmpty(),
				"Ausgangslage: packs/ ist fuer dieses Paket leer -- der Absturz-Zustand");

		int recovered = OrphanedUpdateBackupRecovery.recover(vitrineConfigDir);

		assertEquals(recovered, 1, "genau das eine verwaiste Paket muss wiederhergestellt werden");
		assertTrue(VitrinePackProvider.forConfigDir(vitrineConfigDir).findPackFile(PROFILE_ID).isPresent(),
				"nach der Heilung muss die Datei wieder in packs/ liegen, das Paket wieder normal funktionieren");
		assertFalse(Files.exists(backupFile), "die Backup-Datei wurde nach packs/ VERSCHOBEN, nicht kopiert");
	}

	/**
	 * Beweist den zweiten Fall: ohne passendes Backup bleibt das Paket ohne
	 * Datei -- genau das macht es fuer {@code LibraryModel}/{@code
	 * DiagnosticsModel} sichtbar als kaputt/fehlend statt es verschwinden zu
	 * lassen.
	 */
	@Test
	public void leavesAPackageWithoutAMatchingBackupUntouched() throws IOException {
		// Backup-Ordner existiert (von einem voellig ANDEREN frueheren Update),
		// enthaelt aber keine zum verwaisten Paket passende Datei.
		byte[] otherBytes = VpkgFixtures.packageWithVersion("beispiel.anderesbackup", "Anderes Backup", "1.0.0",
				List.of("textures/b.png"));
		Files.createDirectories(backupDir);
		Path otherBackupFile = backupDir.resolve("anderes.vpkg");
		Files.write(otherBackupFile, otherBytes);
		writeValidSidecar(otherBackupFile, otherBytes);

		writeState(PROFILE_ID, true);

		int recovered = OrphanedUpdateBackupRecovery.recover(vitrineConfigDir);

		assertEquals(recovered, 0, "ohne passendes Backup darf nichts als wiederhergestellt gezaehlt werden");
		assertTrue(VitrinePackProvider.forConfigDir(vitrineConfigDir).findPackFile(PROFILE_ID).isEmpty(),
				"das verwaiste Paket bleibt ohne Datei -- LibraryModel/DiagnosticsModel zeigen es jetzt sichtbar "
						+ "als kaputt/fehlend an, statt es zu verschlucken");
		assertTrue(Files.exists(otherBackupFile), "ein fremdes Backup darf nicht angefasst werden");
	}

	/** Ohne Backup-Ordner (der weit ueberwiegende Normalfall) passiert nichts -- kein Fehler, kein unnoetiger Scan. */
	@Test
	public void doesNothingWithoutABackupDirectoryAtAll() throws IOException {
		writeState(PROFILE_ID, true);
		assertFalse(Files.isDirectory(backupDir));

		int recovered = OrphanedUpdateBackupRecovery.recover(vitrineConfigDir);

		assertEquals(recovered, 0);
	}

	// --- Hilfsmittel ----------------------------------------------

	private void writeState(String profileId, boolean active) throws IOException {
		PackState state = PackState.createDefault();
		state.addOrUpdate(profileId, active);
		PackStateStore.save(vitrineConfigDir, state);
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(packageBytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded()));
		Files.writeString(VpkgIntegritySidecar.sidecarPathFor(vpkgFile), json.toString(), StandardCharsets.UTF_8);
	}
}
