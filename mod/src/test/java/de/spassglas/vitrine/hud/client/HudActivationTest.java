package de.spassglas.vitrine.hud.client;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.hud.ActiveHudRegistry;
import de.spassglas.vitrine.pack.VitrinePackProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * GATE104 (Charter-Regel 3, "Sicherheit vor Bequemlichkeit"): {@link
 * HudActivation#activate(VitrinePackProvider, String)} darf ein HUD-Paket nur
 * aktivieren, nachdem Pruefsumme, Ed25519-Signatur (GATE29) <em>und</em> die
 * Faehigkeiten-Zustimmung (GATE60) denselben Weg bestanden haben wie ein
 * Resource-Pack -- kein zweiter, schwaecherer Weg nur fuer HUD-Pakete.
 */
public final class HudActivationTest {

	private static final String HUD_MANIFEST_ID = "beispiel.hudpaket";
	private static final String HUD_PROFILE_ID = "vitrine/beispiel.hudpaket";

	private static final String VALID_HUD_JSON = """
			{
			  "schemaVersion": 1,
			  "widgets": [
			    { "id": "fps-counter", "type": "text", "anchor": "top-left",
			      "content": { "kind": "dynamic", "source": "fps", "label": "FPS: ", "decimals": 0 } }
			  ]
			}
			""";

	private Path vitrineConfigDir;
	private Path packsDir;
	private VitrinePackProvider provider;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws Exception {
		vitrineConfigDir = Files.createTempDirectory("vitrine-hud-activation-test-");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(packsDir);
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);

		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		ActiveHudRegistry.instance().deactivate(HUD_MANIFEST_ID);
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

	// --- GATE104a: unsigniertes HUD-Paket direkt in packs/ wird nicht aktiviert ---

	@Test
	public void activatingUnsignedHudPackageIsRejected() throws Exception {
		Path vpkgFile = writeHudPackage("unsigniert.vpkg");
		// Bewusst KEINE .vpkg.integrity.json-Begleitdatei.
		grantCapabilityConsent();

		HudActivation.activate(provider, HUD_PROFILE_ID);

		assertTrue(ActiveHudRegistry.instance().isEmpty(),
				"ein unsigniertes HUD-Paket darf nicht aktiviert werden (GATE104)");
	}

	// --- GATE104b: signiertes HUD-Paket ohne erteilte Zustimmung wird nicht aktiviert ---

	@Test
	public void activatingHudPackageWithoutCapabilityConsentIsRejected() throws Exception {
		Path vpkgFile = writeHudPackage("ohne-zustimmung.vpkg");
		signAndWriteSidecar(vpkgFile);
		// Keine Faehigkeiten-Zustimmung im state.json (GATE104, wie GATE60).

		HudActivation.activate(provider, HUD_PROFILE_ID);

		assertTrue(ActiveHudRegistry.instance().isEmpty(),
				"ein HUD-Paket ohne Faehigkeiten-Zustimmung darf nicht aktiviert werden (GATE104)");
	}

	// --- Positivfall: signiert UND zugestimmt -> aktiviert sich wie erwartet ---

	@Test
	public void activatingProperlyVerifiedHudPackageSucceeds() throws Exception {
		Path vpkgFile = writeHudPackage("ok.vpkg");
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();

		HudActivation.activate(provider, HUD_PROFILE_ID);

		assertEquals(ActiveHudRegistry.instance().activeWidgets().size(), 1,
				"ein vollstaendig geprueftes HUD-Paket muss aktiviert werden");
	}

	// --- Hilfsfunktionen ------------------------------------------------

	private Path writeHudPackage(String fileName) throws IOException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
		entries.put("hud/main.json", VALID_HUD_JSON.getBytes(StandardCharsets.UTF_8));
		Path vpkgFile = packsDir.resolve(fileName);
		Files.write(vpkgFile, VpkgFixtures.buildZip(entries));
		return vpkgFile;
	}

	private void signAndWriteSidecar(Path vpkgFile) throws IOException {
		byte[] packageBytes = Files.readAllBytes(vpkgFile);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());
	}

	private void grantCapabilityConsent() throws IOException {
		PackStateStore.update(vitrineConfigDir, state -> {
			state.recordCapabilityConsent(HUD_MANIFEST_ID, List.of("hud"));
			return null;
		});
	}

	private static void writeSidecar(Path vpkgFile, String sha256, byte[] signature, PublicKey publicKey)
			throws IOException {
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		json.addProperty("sha256", sha256);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
		Files.writeString(VpkgIntegritySidecar.sidecarPathFor(vpkgFile), json.toString(), StandardCharsets.UTF_8);
	}

	private static byte[] manifestBytes() {
		return ("{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"" + HUD_MANIFEST_ID + "\",\n"
				+ "  \"name\": \"HUD-Testpaket\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"hud\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"capabilities\": [\"hud\"]\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8);
	}
}
