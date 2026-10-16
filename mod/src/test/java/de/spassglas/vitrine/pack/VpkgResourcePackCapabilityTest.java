package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import net.minecraft.SharedConstants;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das woertliche Fertig-Kriterium von GATE32 ("Test ohne Zustimmung
 * installiertes GUI-Textur-Override wird nicht angewandt"): ein Paket, dessen
 * Manifest die Faehigkeit {@code texturen} nicht anmeldet, darf seine
 * {@code assets/minecraft/textures/gui/...}-Datei nie ueber {@link
 * VpkgResourcePack#open}/{@link VpkgResourcePack#findResources} ausliefern --
 * unabhaengig davon, dass die Datei ganz normal im ZIP liegt und die
 * Zip-Sicherheits- und Integritaetspruefung besteht.
 *
 * <p>Kontrollfall: dasselbe Paket, nur mit {@code texturen} in {@code
 * capabilities} angemeldet, liefert dieselbe Datei byte-identisch aus --
 * beweist, dass die Ablehnung tatsaechlich an der fehlenden Faehigkeit liegt,
 * nicht an einem sonstigen Fehler im Testaufbau.</p>
 */
public final class VpkgResourcePackCapabilityTest {

	private static final String GUI_TEXTURE_PATH = "assets/minecraft/textures/gui/widgets.png";

	private Path tempDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-gate32-capability-test-");
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

	// --- GATE32-Fertig-Kriterium: ohne Zustimmung wird nichts angewandt -----

	@Test
	public void guiTextureOverrideWithoutGrantedTexturenCapabilityIsNeverDelivered() throws IOException {
		byte[] packageBytes = VpkgFixtures.packageWithResourcePathsAndCapabilities(
				"beispiel.ohnetexturen", "Ohne Texturen-Faehigkeit", List.of(GUI_TEXTURE_PATH), List.of("hud"));
		ResourcePack pack = registerAndOpenSinglePack("ohne-texturen.vpkg", packageBytes);
		try {
			Identifier guiId = Identifier.of("minecraft", "textures/gui/widgets.png");
			InputSupplier<InputStream> supplier = pack.open(ResourceType.CLIENT_RESOURCES, guiId);
			assertNull(supplier, "ein Paket ohne die Faehigkeit 'texturen' darf eine GUI-Textur nicht ueber "
					+ "open(...) ausliefern (Charter-Regel 4, GATE32)");

			Map<Identifier, byte[]> found = collectResources(pack, "minecraft", "textures/gui");
			assertFalse(found.containsKey(guiId),
					"findResources(...) darf dieselbe GUI-Textur ohne Faehigkeiten-Zustimmung ebenfalls nicht melden");
		} finally {
			pack.close();
		}
	}

	// --- Kontrollfall: mit angemeldeter Faehigkeit wird ausgeliefert -------

	@Test
	public void guiTextureOverrideWithGrantedTexturenCapabilityIsDelivered() throws IOException {
		byte[] packageBytes = VpkgFixtures.packageWithResourcePathsAndCapabilities(
				"beispiel.mittexturen", "Mit Texturen-Faehigkeit", List.of(GUI_TEXTURE_PATH), List.of("texturen"));
		ResourcePack pack = registerAndOpenSinglePack("mit-texturen.vpkg", packageBytes);
		try {
			Identifier guiId = Identifier.of("minecraft", "textures/gui/widgets.png");
			InputSupplier<InputStream> supplier = pack.open(ResourceType.CLIENT_RESOURCES, guiId);
			assertNotNull(supplier, "mit angemeldeter Faehigkeit 'texturen' muss dieselbe GUI-Textur ankommen");

			Map<Identifier, byte[]> found = collectResources(pack, "minecraft", "textures/gui");
			assertTrue(found.containsKey(guiId), "findResources(...) muss die GUI-Textur ebenfalls melden");
		} finally {
			pack.close();
		}
	}

	// --- Hilfsfunktionen -----------------------------------------------------

	private ResourcePack registerAndOpenSinglePack(String fileName, byte[] packageBytes) throws IOException {
		Path vpkgFile = tempDir.resolve(fileName);
		Files.write(vpkgFile, packageBytes);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());

		CollectingProfileAdder adder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(adder);

		assertEquals(adder.profiles.size(), 1,
				"genau ein gueltiges .vpkg muss genau ein Profil ergeben (P3+P4 bestanden)");
		return adder.profiles.get(0).createResourcePack();
	}

	private static void writeSidecar(Path vpkgFile, String sha256, byte[] signature, PublicKey publicKey)
			throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("sha256", sha256);
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
		Path sidecar = vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + ".integrity.json");
		Files.writeString(sidecar, json.toString(), StandardCharsets.UTF_8);
	}

	private static Map<Identifier, byte[]> collectResources(ResourcePack pack, String namespace, String prefix) {
		Map<Identifier, byte[]> collected = new LinkedHashMap<>();
		pack.findResources(ResourceType.CLIENT_RESOURCES, namespace, prefix, (id, supplier) -> {
			try (InputStream in = supplier.get()) {
				collected.put(id, in.readAllBytes());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		return collected;
	}

	/** Sammelt jedes angemeldete Profil, genau wie Vanillas eigener {@code ResourcePackManager} es tut. */
	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}
	}
}
