package de.spassglas.vitrine.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das Fertig-Kriterium von ROADMAP T7 ("Je ein Beispielpaket wechselt
 * hot") fuer Sound- und Sprachpakete: beide kommen ueber <em>denselben</em>
 * generischen Weg wie ein Texturpaket an -- {@link VitrinePackProvider#register}
 * (nach {@code VpkgSecurityValidator.requireSafe} + Integritaets-Beleg) liefert
 * ein {@link ResourcePackProfile}, und die daraus erzeugte {@link VpkgResourcePack}
 * gibt die {@code assets/minecraft/lang/<locale>.json} bzw. {@code sounds.json} +
 * {@code .ogg} byte-identisch als Ressourcen-Streams zurueck. Kein Sonderpfad im
 * Mod-Code, kein {@code sounds.json}-Merge, kein {@code pack.mcmeta}.
 *
 * <p><b>Kein Neustart noetig:</b> {@link #sameProviderInstanceReappliesBothPacksOnEveryRescan()}
 * scannt dieselbe Provider-Instanz erneut und erzeugt aus demselben Profil ein
 * frisches Pack -- genau das, was Vanillas {@code ResourcePackManager} bei jedem
 * {@code MinecraftClient#reloadResources()} tut (analog {@code
 * LibraryModelTest.reorderingChangesWhichTextureWins}). Ein anschliessender
 * Reload wuerde die Ressourcen also zur Laufzeit greifen.</p>
 *
 * <p>{@link SharedConstants#createGameVersion()} laeuft einmal fuer die
 * Testklasse, weil der Profil-Aufbau die Spielversion braucht (siehe {@code
 * VitrinePackProviderTest}). Kein Client, kein Rendering, keine Wiedergabe --
 * geprueft wird die Ressourcen-Zustellung.</p>
 */
public final class SoundAndLanguagePackHotSwapTest {

	private Path tempDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-t7-hotswap-test-");

		// GATE29: Fixtures muessen gegen den Test-Override von
		// SigningKeyTestSupport signieren, nicht mehr gegen einen frei
		// erfundenen Sidecar-Schluessel.
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

	// --- Sprachpaket ---------------------------------------------------------

	@Test
	public void languagePackDeliversLangJsonHotThroughGenericProviderPath() throws IOException {
		Map<String, String> translations = new LinkedHashMap<>();
		translations.put("menu.singleplayer", "Solo World");
		translations.put("menu.multiplayer", "Shared World");
		translations.put("gui.done", "Alles gut");
		byte[] expectedLangBytes = flatJsonBytes(translations);

		byte[] packageBytes = VpkgFixtures.languagePackage("en_us", translations);
		ResourcePack pack = registerAndOpenSinglePack("sprachpaket.vpkg", packageBytes);
		try {
			assertTrue(pack.getNamespaces(ResourceType.CLIENT_RESOURCES).contains("minecraft"),
					"ein Sprachpaket muss den Namespace 'minecraft' beisteuern (assets/minecraft/lang/...)");

			Identifier langId = Identifier.of("minecraft", "lang/en_us.json");
			InputSupplier<InputStream> supplier = pack.open(ResourceType.CLIENT_RESOURCES, langId);
			assertNotNull(supplier, "assets/minecraft/lang/en_us.json muss ueber den generischen open(...)-Pfad ankommen");
			byte[] deliveredLangBytes = readAll(supplier);
			assertEquals(deliveredLangBytes, expectedLangBytes,
					"die ausgelieferten lang-Bytes muessen exakt die vom Fixture eingebetteten sein");

			JsonObject delivered = JsonParser.parseString(new String(deliveredLangBytes, StandardCharsets.UTF_8))
					.getAsJsonObject();
			assertEquals(delivered.keySet(), translations.keySet(),
					"exakt die uebergebenen Uebersetzungs-Keys, keine mehr, keiner weniger");
			for (Map.Entry<String, String> e : translations.entrySet()) {
				assertEquals(delivered.get(e.getKey()).getAsString(), e.getValue(),
						"Wert fuer '" + e.getKey() + "' muss unveraendert durchgereicht sein");
			}

			// findResources(...) muss denselben Eintrag ebenfalls auflisten.
			Map<Identifier, byte[]> found = collectResources(pack, "minecraft", "lang");
			assertTrue(found.containsKey(langId), "findResources(minecraft, lang, ...) muss en_us.json melden");
			assertEquals(found.get(langId), expectedLangBytes, "auch ueber findResources byte-identisch");
		} finally {
			pack.close();
		}
	}

	// --- Soundpaket --------------------------------------------------------

	@Test
	public void soundPackDeliversSoundsJsonAndOggHotThroughGenericProviderPath() throws IOException {
		byte[] oggBytes = VpkgFixtures.minimalOggBytes();
		byte[] expectedSoundsJson = expectedSoundsJsonBytes("ui.button.click");

		byte[] packageBytes = VpkgFixtures.soundPackage("ui.button.click", oggBytes);
		ResourcePack pack = registerAndOpenSinglePack("soundpaket.vpkg", packageBytes);
		try {
			Identifier soundsJsonId = Identifier.of("minecraft", "sounds.json");
			InputSupplier<InputStream> soundsJsonSupplier = pack.open(ResourceType.CLIENT_RESOURCES, soundsJsonId);
			assertNotNull(soundsJsonSupplier,
					"assets/minecraft/sounds.json muss ueber den generischen open(...)-Pfad ankommen");
			byte[] deliveredSoundsBytes = readAll(soundsJsonSupplier);
			assertEquals(deliveredSoundsBytes, expectedSoundsJson,
					"die ausgelieferte sounds.json muss byte-identisch zum Fixture-Inhalt sein");

			JsonObject deliveredSounds = JsonParser.parseString(
					new String(deliveredSoundsBytes, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray eventSounds = deliveredSounds.getAsJsonObject("ui.button.click").getAsJsonArray("sounds");
			assertEquals(eventSounds.get(0).getAsString(), "vitrine/hotswap_probe",
					"das Sound-Event muss auf die mitgelieferte OGG zeigen");

			String oggRelativePath =
					VpkgFixtures.SOUND_PACKAGE_OGG_ENTRY.substring("assets/minecraft/".length());
			Identifier oggId = Identifier.of("minecraft", oggRelativePath);
			InputSupplier<InputStream> oggSupplier = pack.open(ResourceType.CLIENT_RESOURCES, oggId);
			assertNotNull(oggSupplier, "die referenzierte .ogg muss ueber denselben generischen Pfad ankommen");
			assertEquals(readAll(oggSupplier), oggBytes,
					"die .ogg-Bytes muessen byte-identisch beim Ressourcen-Stream ankommen");
		} finally {
			pack.close();
		}
	}

	// --- Kein Neustart: erneuter Scan / erneute Pack-Erzeugung ------------

	@Test
	public void sameProviderInstanceReappliesBothPacksOnEveryRescan() throws IOException {
		Map<String, String> translations = new LinkedHashMap<>();
		translations.put("menu.singleplayer", "Solo World");
		byte[] expectedLangBytes = flatJsonBytes(translations);
		byte[] oggBytes = VpkgFixtures.minimalOggBytes();

		Path packsDir = tempDir.resolve("packs");
		Files.createDirectories(packsDir);
		writePackageWithSidecar(packsDir.resolve("sprachpaket.vpkg"),
				VpkgFixtures.languagePackage("en_us", translations));
		writePackageWithSidecar(packsDir.resolve("soundpaket.vpkg"),
				VpkgFixtures.soundPackage("ui.button.click", oggBytes));

		// Eine Provider-Instanz, mehrfach gescannt -- wie Vanillas
		// ResourcePackManager bei jedem reloadResources(): kein Neustart.
		VitrinePackProvider provider = VitrinePackProvider.forPacksDir(packsDir);

		for (int rescan = 1; rescan <= 3; rescan++) {
			CollectingProfileAdder adder = new CollectingProfileAdder();
			provider.register(adder);
			assertEquals(adder.profiles.size(), 2, "Scan " + rescan + ": beide Pakete muessen als Profil ankommen");

			for (ResourcePackProfile profile : adder.profiles) {
				// createResourcePack() ist genau das, was Vanilla bei jedem
				// Reload erneut aufruft -- pro Aufruf eine frische Instanz.
				try (ResourcePack pack = profile.createResourcePack()) {
					if ("vitrine/beispiel.sprachpaket".equals(profile.getId())) {
						InputSupplier<InputStream> s = pack.open(ResourceType.CLIENT_RESOURCES,
								Identifier.of("minecraft", "lang/en_us.json"));
						assertNotNull(s, "Scan " + rescan + ": lang/en_us.json muss weiterhin ankommen");
						assertEquals(readAll(s), expectedLangBytes, "Scan " + rescan + ": lang-Bytes unveraendert");
					} else if ("vitrine/beispiel.soundpaket".equals(profile.getId())) {
						String oggRel = VpkgFixtures.SOUND_PACKAGE_OGG_ENTRY.substring("assets/minecraft/".length());
						InputSupplier<InputStream> ogg = pack.open(ResourceType.CLIENT_RESOURCES,
								Identifier.of("minecraft", oggRel));
						assertNotNull(ogg, "Scan " + rescan + ": .ogg muss weiterhin ankommen");
						assertEquals(readAll(ogg), oggBytes, "Scan " + rescan + ": .ogg-Bytes unveraendert");
					} else {
						throw new AssertionError("unerwartetes Profil: " + profile.getId());
					}
				}
			}
		}
	}

	// --- Hilfsfunktionen -------------------------------------------------

	/**
	 * Schreibt das Paket samt gueltiger Begleitdatei in {@link #tempDir},
	 * fuehrt es durch {@link VitrinePackProvider#register} (P3-Sicherheit +
	 * P4-Integritaet) und oeffnet das eine erwartete Profil als {@link
	 * ResourcePack} -- exakt der Weg, den ein Texturpaket auch nimmt.
	 */
	private ResourcePack registerAndOpenSinglePack(String fileName, byte[] packageBytes) throws IOException {
		writePackageWithSidecar(tempDir.resolve(fileName), packageBytes);

		CollectingProfileAdder adder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(adder);

		assertEquals(adder.profiles.size(), 1,
				"genau ein gueltiges .vpkg muss genau ein Profil ergeben (P3+P4 bestanden)");
		return adder.profiles.get(0).createResourcePack();
	}

	private void writePackageWithSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		Files.write(vpkgFile, packageBytes);
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, trustedKeyPair.getPublic());
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

	/** Erzeugt exakt die Bytes, die {@code VpkgFixtures.languagePackage} als lang-Datei einbettet. */
	private static byte[] flatJsonBytes(Map<String, String> entries) {
		JsonObject json = new JsonObject();
		for (Map.Entry<String, String> e : entries.entrySet()) {
			json.addProperty(e.getKey(), e.getValue());
		}
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Erzeugt exakt die Bytes, die {@code VpkgFixtures.soundPackage} als sounds.json einbettet. */
	private static byte[] expectedSoundsJsonBytes(String eventName) {
		JsonObject soundsJson = new JsonObject();
		JsonObject event = new JsonObject();
		JsonArray sounds = new JsonArray();
		sounds.add("vitrine/hotswap_probe");
		event.add("sounds", sounds);
		soundsJson.add(eventName, event);
		return soundsJson.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] readAll(InputSupplier<InputStream> supplier) throws IOException {
		try (InputStream in = supplier.get()) {
			return in.readAllBytes();
		}
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
