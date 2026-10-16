package de.spassglas.vitrine.hotapply;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.VitrinePackProvider;
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
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

/**
 * ROADMAP GATE81: {@link ManifestPackDisplayNameLookup} ist der Produktions-
 * Seam, der den Auto-Rollback-/Fehlschlag-Toasts ihren Klartext-Namen liefert
 * (statt der technischen Profil-ID). Beweist den echten Lese-Pfad ueber ein
 * echtes, signiertes {@code .vpkg} (derselbe Aufbau wie
 * {@code LibraryScreenUpdateMarkerTest}) sowie den sicheren Ruecksprung auf
 * die Paket-ID, wenn keine Datei (mehr) existiert.
 */
public final class ManifestPackDisplayNameLookupTest {

	private static final String MANIFEST_ID = "beispiel.paket";
	private static final String PROFILE_ID = "vitrine/" + MANIFEST_ID;

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-display-name-lookup-test-");
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
	public void returnsTheRealManifestNameForAnInstalledPackage() throws IOException {
		installPack("a.vpkg", MANIFEST_ID, "Mein echtes Paket", "1.0.0");
		ManifestPackDisplayNameLookup lookup = new ManifestPackDisplayNameLookup(
				VitrinePackProvider.forConfigDir(configDir));

		String name = lookup.displayNameFor(PROFILE_ID);

		assertEquals(name, "Mein echtes Paket",
				"die Umsetzung muss den Klartext-Namen aus dem Manifest liefern, nicht die Profil-ID");
	}

	@Test
	public void fallsBackToTheRawPackIdWhenNoFileIsFound() {
		ManifestPackDisplayNameLookup lookup = new ManifestPackDisplayNameLookup(
				VitrinePackProvider.forConfigDir(configDir));

		String name = lookup.displayNameFor("vitrine/nicht-installiert");

		assertEquals(name, "vitrine/nicht-installiert",
				"ohne gefundene Datei darf kein Fehler fliegen -- Ruecksprung auf die rohe Paket-ID");
	}

	// --- Hilfsmittel ----------------------------------------------

	private void installPack(String fileName, String manifestId, String name, String version) throws IOException {
		byte[] bytes = VpkgFixtures.packageWithVersion(manifestId, name, version, List.of());
		Path vpkgFile = packsDir.resolve(fileName);
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
}
