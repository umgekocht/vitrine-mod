package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.state.PackStateStore;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP M10/AD1: ein Verhaltens-Addon ({@code category: addon},
 * Faehigkeit {@code verhalten}) wird als Datenpaket angemeldet und liefert
 * seine {@code data/**}-Inhalte aus -- auf der Client-Ressourcenseite nicht.
 */
public final class AddonDataPackTest {

	private static final String RECIPE = "data/vitrine_test/recipe/diamond_from_dirt.json";
	private static final String RECIPE_JSON = "{\"type\":\"minecraft:crafting_shapeless\","
			+ "\"ingredients\":[{\"item\":\"minecraft:dirt\"}],\"result\":{\"id\":\"minecraft:diamond\"}}";

	private Path configDir;
	private KeyPair keys;

	@BeforeClass
	public void bootstrap() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-addon-test-");
		Files.createDirectories(configDir.resolve("packs"));
		keys = VpkgFixtures.generateEd25519KeyPair();
		de.spassglas.vitrine.vpkg.SigningKeyTestSupport.install(keys.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		de.spassglas.vitrine.vpkg.SigningKeyTestSupport.reset();
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
	public void behaviourAddonIsServedAsDataPackOnly() throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("vitrine.json", ("{\"schemaVersion\":1,\"id\":\"beispiel.mehrrezepte\",\"name\":\"Mehr Rezepte\","
				+ "\"version\":\"1.0.0\",\"category\":\"addon\",\"author\":\"Test\",\"license\":\"CC0-1.0\","
				+ "\"capabilities\":[\"verhalten\"]}").getBytes(StandardCharsets.UTF_8));
		entries.put(RECIPE, RECIPE_JSON.getBytes(StandardCharsets.UTF_8));
		writeSigned(configDir.resolve("packs").resolve("addon.vpkg"), VpkgFixtures.buildZip(entries));
		PackStateStore.update(configDir, state -> {
			state.recordCapabilityConsent("beispiel.mehrrezepte", List.of("verhalten"));
			return null;
		});

		List<ResourcePackProfile> data = new ArrayList<>();
		VitrinePackProvider.dataForConfigDir(configDir).register(data::add);
		assertEquals(data.size(), 1, "das Addon muss als Datenpaket angemeldet werden");
		assertEquals(data.get(0).getId(), "vitrine/beispiel.mehrrezepte");

		try (ResourcePack pack = data.get(0).createResourcePack()) {
			assertTrue(pack.getNamespaces(ResourceType.SERVER_DATA).contains("vitrine_test"));
			InputSupplier<InputStream> recipe = pack.open(ResourceType.SERVER_DATA,
					Identifier.of("vitrine_test", "recipe/diamond_from_dirt.json"));
			assertNotNull(recipe, "das Rezept muss ausgeliefert werden");
			try (InputStream in = recipe.get()) {
				assertEquals(new String(in.readAllBytes(), StandardCharsets.UTF_8), RECIPE_JSON);
			}
			assertNull(pack.open(ResourceType.CLIENT_RESOURCES,
					Identifier.of("vitrine_test", "recipe/diamond_from_dirt.json")));
		}
	}

	@Test
	public void ordinaryResourcePackIsNotADataPack() throws IOException {
		byte[] bytes = VpkgFixtures.packageWithResourcePathsAndCapabilities("beispiel.textur", "Textur",
				List.of("assets/minecraft/textures/block/stone.png"), List.of("texturen"));
		writeSigned(configDir.resolve("packs").resolve("textur.vpkg"), bytes);
		PackStateStore.update(configDir, state -> {
			state.recordCapabilityConsent("beispiel.textur", List.of("texturen"));
			return null;
		});
		List<ResourcePackProfile> data = new ArrayList<>();
		VitrinePackProvider.dataForConfigDir(configDir).register(data::add);
		assertEquals(data.size(), 0, "ohne Faehigkeit 'verhalten' kein Datenpaket");
	}

	private void writeSigned(Path vpkgFile, byte[] bytes) throws IOException {
		Files.write(vpkgFile, bytes);
		byte[] signature = VpkgFixtures.signSha256Digest(bytes, keys.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(bytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
		Files.writeString(vpkgFile.resolveSibling(vpkgFile.getFileName() + ".integrity.json"), json.toString(),
				StandardCharsets.UTF_8);
	}
}
