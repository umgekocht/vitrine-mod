package de.spassglas.vitrine.addon.script;

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
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP AD2 (Charter-Regel 3, "Sicherheit vor Bequemlichkeit"): {@link
 * ScriptAddonLoader#load(VitrinePackProvider, String)} darf Addon-Skripte nur
 * laden, nachdem Pruefsumme/Signatur (GATE29) <em>und</em> die angemeldete
 * <b>und</b> zugestimmte Faehigkeit {@code skript} (GATE60) denselben Weg
 * bestanden haben wie jedes andere Vitrine-Paket -- dasselbe Muster wie
 * {@code de.spassglas.vitrine.hud.client.HudActivationTest} fuer {@code hud}.
 */
public final class ScriptAddonLoaderTest {

	private static final String MANIFEST_ID = "beispiel.skriptpaket";
	private static final String PROFILE_ID = "vitrine/beispiel.skriptpaket";

	private static final String VALID_SCRIPT = """
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
		vitrineConfigDir = Files.createTempDirectory("vitrine-script-addon-loader-test-");
		packsDir = vitrineConfigDir.resolve("packs");
		Files.createDirectories(packsDir);
		provider = VitrinePackProvider.forConfigDir(vitrineConfigDir);

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
	public void unsignedScriptPackageIsRejected() throws Exception {
		writeScriptPackage("unsigniert.vpkg", List.of("skript"));
		// Bewusst KEINE .vpkg.integrity.json-Begleitdatei.
		grantCapabilityConsent();

		Optional<List<ScriptAddon>> loaded = ScriptAddonLoader.load(provider, PROFILE_ID);

		assertTrue(loaded.isEmpty(), "ein unsigniertes Skript-Paket darf nicht geladen werden");
	}

	@Test
	public void capabilityNotDeclaredIsRejected() throws Exception {
		Path vpkgFile = writeScriptPackage("ohne-faehigkeit.vpkg", List.of());
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();

		Optional<List<ScriptAddon>> loaded = ScriptAddonLoader.load(provider, PROFILE_ID);

		assertTrue(loaded.isEmpty(), "ohne angemeldete Faehigkeit 'skript' darf nichts laufen");
	}

	@Test
	public void capabilityDeclaredButNeverConsentedIsRejected() throws Exception {
		Path vpkgFile = writeScriptPackage("ohne-zustimmung.vpkg", List.of("skript"));
		signAndWriteSidecar(vpkgFile);
		// Keine Faehigkeiten-Zustimmung im state.json.

		Optional<List<ScriptAddon>> loaded = ScriptAddonLoader.load(provider, PROFILE_ID);

		assertTrue(loaded.isEmpty(), "ohne Zustimmung darf ein Skript nicht laufen (GATE60)");
	}

	@Test
	public void properlyVerifiedScriptPackageLoadsSuccessfully() throws Exception {
		Path vpkgFile = writeScriptPackage("ok.vpkg", List.of("skript"));
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();

		Optional<List<ScriptAddon>> loaded = ScriptAddonLoader.load(provider, PROFILE_ID);

		assertTrue(loaded.isPresent(), "ein vollstaendig geprueftes Skript-Paket muss laden");
		assertEquals(loaded.get().size(), 1);
		assertEquals(loaded.get().get(0).event(), ScriptAddonEvent.WELT_BETRETEN);
	}

	@Test
	public void brokenScriptInsideOtherwiseValidPackageIsRejectedAsAWhole() throws Exception {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes(List.of("skript")));
		entries.put("scripts/kaputt.vs", "{not json".getBytes(StandardCharsets.UTF_8));
		Path vpkgFile = packsDir.resolve("kaputt.vpkg");
		Files.write(vpkgFile, VpkgFixtures.buildZip(entries));
		signAndWriteSidecar(vpkgFile);
		grantCapabilityConsent();

		Optional<List<ScriptAddon>> loaded = ScriptAddonLoader.load(provider, PROFILE_ID);

		assertTrue(loaded.isEmpty(), "ein kaputtes .vs darf das ganze Paket nicht halb laden");
	}

	// --- Hilfsfunktionen ------------------------------------------------

	private Path writeScriptPackage(String fileName, List<String> capabilityIds) throws IOException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes(capabilityIds));
		entries.put("scripts/beispiel.vs", VALID_SCRIPT.getBytes(StandardCharsets.UTF_8));
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
			state.recordCapabilityConsent(MANIFEST_ID, List.of("skript"));
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

	private static byte[] manifestBytes(List<String> capabilityIds) {
		StringBuilder caps = new StringBuilder();
		for (int i = 0; i < capabilityIds.size(); i++) {
			if (i > 0) {
				caps.append(", ");
			}
			caps.append('"').append(capabilityIds.get(i)).append('"');
		}
		return ("{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"" + MANIFEST_ID + "\",\n"
				+ "  \"name\": \"Skript-Testpaket\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"addon\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"capabilities\": [" + caps + "]\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8);
	}
}
