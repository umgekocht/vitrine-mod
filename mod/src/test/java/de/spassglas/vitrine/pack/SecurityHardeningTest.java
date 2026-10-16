package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.classb.StagedMod;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import de.spassglas.vitrine.vpkg.VpkgSecurityViolation;
import de.spassglas.vitrine.vpkg.VpkgSecurityViolationType;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourcePack;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Sicherheits-Nachschaerfungen aus dem zweiten Kritiker-Durchgang
 * (critic-security, GATE57-GATE61). Je Punkt ein Beweis, dass das Loch zu ist.
 */
public final class SecurityHardeningTest {

	private Path tempDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-security-hardening-test-");
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
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

	// --- GATE59: ADS-Namen und absurde Tiefe werden abgelehnt ---------------

	@Test
	public void alternateDataStreamNameIsRejected() throws IOException {
		assertRejectedAsUnsafeName("assets/minecraft/lang/de_de.json:zone");
	}

	@Test
	public void absurdlyDeepPathIsRejected() throws IOException {
		StringBuilder deep = new StringBuilder("assets");
		for (int i = 0; i < 2000; i++) {
			deep.append("/a");
		}
		assertRejectedAsUnsafeName(deep.append("/x.json").toString());
	}

	@Test
	public void newlyForbiddenScriptExtensionsAreRejected() throws IOException {
		for (String ext : List.of("mjs", "vbe", "jse", "wsf") /* .js/.lua seit der Addon-Plattform (2026-10-13) erlaubt, laufen nur in der Sandbox */) {
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("vitrine.json", "{}".getBytes(StandardCharsets.UTF_8));
			entries.put("assets/minecraft/skript." + ext, new byte[] {1});
			assertTrue(violationTypes(entries).contains(VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE),
					"Endung ." + ext + " muss abgelehnt werden (GATE64)");
		}
	}

	// --- GATE106: Endungs-Allowlist statt Blockliste -----------------------

	/**
	 * Die alte Blockliste pruefte eine per {@code lastIndexOf('.')} "erratene"
	 * Endung gegen eine feste verbotene Menge -- ein Leerzeichen, ein
	 * angehaengter Punkt/Semikolon oder ein eingebettetes Leerzeichen in der
	 * Endung liessen die "erratene" Endung nicht mehr auf {@code jar}/
	 * {@code class} passen und der Eintrag rutschte durch. Die neue Allowlist
	 * (exakter, nicht trimmender Suffix-Vergleich) lehnt alle diese Varianten
	 * ab, weil der volle Name auf KEINE erlaubte Endung mehr passt.
	 */
	@Test
	public void extensionAllowlistBypassAttemptsAreRejected() throws IOException {
		for (String bypassName : List.of(
				"payload.jar ", "payload.jar.", "payload.class.", "payload.jar;", "payload.cl ass")) {
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("vitrine.json", "{}".getBytes(StandardCharsets.UTF_8));
			entries.put("assets/minecraft/" + bypassName, new byte[] {1});
			assertTrue(violationTypes(entries).contains(VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE),
					"Umgehungsversuch '" + bypassName + "' muss abgelehnt werden (GATE106)");
		}
	}

	/**
	 * Endungen, die unter der alten Blockliste gar nicht erst aufgefuehrt
	 * waren (und deshalb klaglos durchkamen), obwohl sie Code ausfuehren
	 * koennen (Webassembly, Java-Server-Pages, Ruby-/PHP-/AppleScript- und
	 * JNI-Skripte/-Bibliotheken). Die Allowlist lehnt sie automatisch ab,
	 * weil keine von ihnen auf der erlaubten Liste steht.
	 */
	@Test
	public void additionalDangerousExtensionsAreRejected() throws IOException {
		for (String ext : List.of("wasm", "jsp", "rb", "php", "jnilib", "scpt")) {
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("vitrine.json", "{}".getBytes(StandardCharsets.UTF_8));
			entries.put("assets/minecraft/payload." + ext, new byte[] {1});
			assertTrue(violationTypes(entries).contains(VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE),
					"Endung ." + ext + " muss abgelehnt werden (GATE106)");
		}
	}

	/** Gegenprobe: die tatsaechlich genutzten Klasse-A-Ressourcentypen bleiben erlaubt. */
	@Test
	public void legitimateResourceExtensionsStillPassTheAllowlist() throws IOException {
		for (String ext : List.of("png", "jpg", "jpeg", "json", "ogg", "txt", "mcmeta", "ttf", "otf")) {
			Map<String, byte[]> entries = new LinkedHashMap<>();
			entries.put("vitrine.json", "{}".getBytes(StandardCharsets.UTF_8));
			entries.put("assets/minecraft/datei." + ext, new byte[] {1, 2, 3});
			assertFalse(violationTypes(entries).contains(VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE),
					"Endung ." + ext + " muss weiterhin erlaubt sein (GATE106)");
		}
	}

	private void assertRejectedAsUnsafeName(String entryName) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("vitrine.json", "{}".getBytes(StandardCharsets.UTF_8));
		entries.put(entryName, "{}".getBytes(StandardCharsets.UTF_8));
		assertTrue(violationTypes(entries).contains(VpkgSecurityViolationType.UNSAFE_ENTRY_NAME),
				"Eintrag muss als UNSAFE_ENTRY_NAME abgelehnt werden: " + entryName.substring(0, Math.min(40, entryName.length())));
	}

	private List<VpkgSecurityViolationType> violationTypes(Map<String, byte[]> entries) throws IOException {
		Path file = tempDir.resolve("probe-" + System.nanoTime() + ".vpkg");
		Files.write(file, VpkgFixtures.buildZip(entries));
		try (VpkgArchive archive = VpkgArchive.open(file)) {
			return VpkgSecurityValidator.validate(archive).violations().stream()
					.map(VpkgSecurityViolation::type).toList();
		}
	}

	// --- GATE61: StagedMod haertet sich selbst ------------------------------

	@Test
	public void stagedModRejectsTraversalFileNames() {
		for (String bad : List.of("../evil.jar", "..\\evil.jar", "sub/evil.jar", "C:evil.jar", ".hidden.jar", "x.exe")) {
			assertThrows(IllegalArgumentException.class,
					() -> new StagedMod("id", bad, "00", "a", "n", "t", true));
		}
		new StagedMod("id", "minimap-1.0.0.jar", "00", "a", "n", "t", true);
	}

	// --- GATE110: kein Laufzeit-Override des Vertrauensankers im Auslieferungscode ---

	/**
	 * Vor GATE110 reichte es, in derselben JVM
	 * {@code System.setProperty("vitrine.allowTestSigningKey", "true")} zu
	 * setzen, um danach {@code EmbeddedSigningKey.overrideForTesting(...)}
	 * -- eine Methode im Auslieferungscode -- aufzurufen und so den
	 * Vertrauensanker beliebig auszutauschen (critic-security hat das
	 * nachgestellt). Dieser Test beweist, dass {@link EmbeddedSigningKey}
	 * (die Klasse, die tatsaechlich im ausgelieferten Jar landet) ueberhaupt
	 * keine solche Methode mehr besitzt: keine Methode namens
	 * {@code overrideForTesting}/{@code resetForTesting}, und keine Methode,
	 * die ueberhaupt einen {@link PublicKey} als Parameter entgegennaehme.
	 */
	@Test
	public void embeddedSigningKeyExposesNoRuntimeOverrideMethod() {
		for (var method : EmbeddedSigningKey.class.getDeclaredMethods()) {
			assertFalse(method.getName().equals("overrideForTesting"),
					"EmbeddedSigningKey darf keine overrideForTesting-Methode mehr besitzen (GATE110)");
			assertFalse(method.getName().equals("resetForTesting"),
					"EmbeddedSigningKey darf keine resetForTesting-Methode mehr besitzen (GATE110)");
			assertFalse(List.of(method.getParameterTypes()).contains(PublicKey.class),
					"EmbeddedSigningKey darf keine Methode mehr mit einem PublicKey-Parameter besitzen -- "
							+ "das waere eine Override-Tuer (GATE110): " + method);
		}
	}

	/**
	 * Selbst wenn ein Angreifer die (jetzt bedeutungslose) alte
	 * Systemeigenschaft setzt, bevor er {@link EmbeddedSigningKey#load()}
	 * aufruft, bekommt er den echten eingebetteten Schluessel -- es gibt im
	 * Auslieferungscode schlicht keinen Code mehr, der diese Eigenschaft
	 * noch liest.
	 */
	@Test
	public void legacyOverridePropertyNoLongerInfluencesTheEmbeddedKey() {
		SigningKeyTestSupport.reset();
		System.setProperty("vitrine.allowTestSigningKey", "true");
		try {
			PublicKey key = EmbeddedSigningKey.load();
			assertFalse(Base64.getEncoder().encodeToString(key.getEncoded())
					.equals(Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded())),
					"die alte Systemeigenschaft darf den eingebetteten Schluessel nicht mehr durch den "
							+ "Test-Schluessel ersetzen");
		} finally {
			System.clearProperty("vitrine.allowTestSigningKey");
			SigningKeyTestSupport.install(trustedKeyPair.getPublic());
		}
	}

	// --- GATE58: openRoot liefert nur pack.png / pack.mcmeta ---------------

	@Test
	public void openRootServesOnlyTheKnownRootFiles() throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("vitrine.json", manifest("beispiel.wurzel", List.of()).getBytes(StandardCharsets.UTF_8));
		entries.put("pack.png", pngBytes());
		entries.put("geheim.txt", "nicht ausliefern".getBytes(StandardCharsets.UTF_8));
		byte[] bytes = VpkgFixtures.buildZip(entries);
		writeSigned(tempDir.resolve("wurzel.vpkg"), bytes);

		List<ResourcePackProfile> profiles = new ArrayList<>();
		VitrinePackProvider.forPacksDir(tempDir).register(profiles::add);
		assertEquals(profiles.size(), 1);
		try (ResourcePack pack = profiles.get(0).createResourcePack()) {
			assertNotNull(pack.openRoot("pack.png"), "pack.png bleibt erreichbar");
			assertNull(pack.openRoot("geheim.txt"), "beliebige Wurzel-Dateien duerfen nicht ausgeliefert werden");
			assertNull(pack.openRoot("vitrine.json"));
		}
	}

	// --- GATE60: Startup-Scan verlangt Faehigkeiten-Zustimmung -------------

	@Test
	public void startupScanSkipsPackagesWithoutCapabilityConsent() throws IOException {
		Path configDir = tempDir.resolve("config");
		Path packs = configDir.resolve("packs");
		Files.createDirectories(packs);
		byte[] bytes = VpkgFixtures.packageWithResourcePathsAndCapabilities("beispiel.ohnezustimmung",
				"Ohne Zustimmung", List.of("assets/minecraft/textures/block/stone.png"), List.of("texturen"));
		writeSigned(packs.resolve("direkt.vpkg"), bytes);

		List<ResourcePackProfile> withoutConsent = new ArrayList<>();
		VitrinePackProvider.forConfigDir(configDir).register(withoutConsent::add);
		assertEquals(withoutConsent.size(), 0,
				"ein direkt in packs/ gelegtes Paket ohne Zustimmung darf nicht angemeldet werden");

		PackStateStore.update(configDir, state -> {
			state.recordCapabilityConsent("beispiel.ohnezustimmung", List.of("texturen"));
			return null;
		});
		List<ResourcePackProfile> withConsent = new ArrayList<>();
		VitrinePackProvider.forConfigDir(configDir).register(withConsent::add);
		assertEquals(withConsent.size(), 1, "nach erteilter Zustimmung wird es angemeldet");
	}

	// --- Hilfen -------------------------------------------------------------

	private void writeSigned(Path vpkgFile, byte[] bytes) throws IOException {
		Files.write(vpkgFile, bytes);
		byte[] signature = VpkgFixtures.signSha256Digest(bytes, trustedKeyPair.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(bytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded()));
		Files.writeString(vpkgFile.resolveSibling(vpkgFile.getFileName() + ".integrity.json"), json.toString(),
				StandardCharsets.UTF_8);
	}

	private static String manifest(String id, List<String> capabilities) {
		StringBuilder caps = new StringBuilder();
		for (String c : capabilities) {
			caps.append(caps.length() == 0 ? "" : ",").append('"').append(c).append('"');
		}
		return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"name\":\"Wurzeltest\",\"version\":\"1.0.0\","
				+ "\"category\":\"texturen\",\"author\":\"Test\",\"license\":\"CC0-1.0\""
				+ (capabilities.isEmpty() ? "" : ",\"capabilities\":[" + caps + "]") + "}";
	}

	/** Kleinstes gueltiges PNG-Kopfstueck (Signatur + IHDR 1x1), reicht fuer die Kopfpruefung. */
	private static byte[] pngBytes() {
		return new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
				0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0, 0, 0, 0};
	}
}
