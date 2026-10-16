package de.spassglas.vitrine.addon.script.client;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
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
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das Fertig-Kriterium von ROADMAP AD2 woertlich: "Beispiel-Skript
 * läuft ohne Neustart" -- ein gueltiges Beispiel-Skript ({@code welt_betreten}
 * -&gt; Nachricht) wird beim Aktivieren in {@code state.json} ohne Neustart
 * geladen ({@link ScriptAddons#syncWith}) und beim Deaktivieren sofort wieder
 * entfernt. Analog zu {@code AddonRuleTest}/{@code AddonDataPacksTest} fuer
 * AD1 und {@code de.spassglas.vitrine.hud.client.HudActivationTest} fuer T5.
 */
public final class ScriptAddonsTest {

	private static final String MANIFEST_ID = "beispiel.skriptaddon";
	private static final String PROFILE_ID = "vitrine/beispiel.skriptaddon";

	private static final String EXAMPLE_SCRIPT = """
			{
			  "ereignis": "welt_betreten",
			  "aktionen": [ { "nachricht": { "op": "lit", "value": "Willkommen!" } } ]
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
		ScriptAddons.resetForTest();
		vitrineConfigDir = Files.createTempDirectory("vitrine-script-addons-test-");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(packsDir);
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);

		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		ScriptAddons.resetForTest();
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

	@Test
	public void activatingAddonHotLoadsScriptAndDeactivatingRemovesItImmediately() throws Exception {
		Path vpkgFile = writeScriptPackage();
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();
		installAndActivate();

		ScriptAddons.syncWith(provider, vitrineConfigDir);
		assertTrue(ScriptAddons.hasActiveScripts(PROFILE_ID),
				"ein aktiviertes, gueltiges Skript-Addon muss ohne Neustart geladen sein");

		deactivate();
		ScriptAddons.syncWith(provider, vitrineConfigDir);
		assertFalse(ScriptAddons.hasActiveScripts(PROFILE_ID),
				"ein deaktiviertes Addon muss sofort wieder ohne Skripte sein");
	}

	@Test
	public void neverActivatedAddonStaysWithoutScripts() throws Exception {
		Path vpkgFile = writeScriptPackage();
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();
		// Installiert, aber nicht aktiv.
		PackStateStore.update(vitrineConfigDir, state -> {
			state.addOrUpdate(PROFILE_ID, false);
			return null;
		});

		ScriptAddons.syncWith(provider, vitrineConfigDir);

		assertFalse(ScriptAddons.hasActiveScripts(PROFILE_ID));
	}

	// --- Hilfsfunktionen ------------------------------------------------

	private Path writeScriptPackage() throws IOException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
		entries.put("scripts/beispiel.vs", EXAMPLE_SCRIPT.getBytes(StandardCharsets.UTF_8));
		Path vpkgFile = packsDir.resolve("beispiel.vpkg");
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
			state.recordCapabilityConsent(MANIFEST_ID, List.of("skript"));
			return null;
		});
	}

	private void installAndActivate() throws IOException {
		PackStateStore.update(vitrineConfigDir, state -> {
			state.addOrUpdate(PROFILE_ID, true);
			return null;
		});
	}

	private void deactivate() throws IOException {
		PackStateStore.update(vitrineConfigDir, state -> {
			state.addOrUpdate(PROFILE_ID, false);
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
				+ "  \"id\": \"" + MANIFEST_ID + "\",\n"
				+ "  \"name\": \"Skript-Addon-Testpaket\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"addon\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"capabilities\": [\"skript\"]\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8);
	}
}
