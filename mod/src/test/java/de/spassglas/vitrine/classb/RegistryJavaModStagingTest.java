package de.spassglas.vitrine.classb;

import de.spassglas.vitrine.catalog.RegistryJavaModVersion;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * GATE6: der Produktionsweg Katalog-Eintrag -> gestagter Java-Mod. Echte
 * Jar-Bytes, echte Ed25519-Signatur, echter {@link JavaModStager} gegen einen
 * Wegwerf-Spielordner -- nur der Download ist durch eine feste Quelle ersetzt.
 */
public class RegistryJavaModStagingTest {

	private static final byte[] JAR = "PK pretend-this-is-a-jar".getBytes(StandardCharsets.UTF_8);

	private Path gameDir;
	private KeyPair trusted;

	@BeforeMethod
	public void setUp() throws IOException {
		gameDir = Files.createTempDirectory("vitrine-gate6-");
		trusted = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trusted.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		try (Stream<Path> paths = Files.walk(gameDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	public void optedInJavaModEntryIsStagedIntoModsAndAwaitsRestart() throws Exception {
		StagedMod staged = RegistryJavaModStaging.stage(javaModEntry(trusted), stager(), () -> true);

		assertTrue(staged.pendingRestart(), "erst nach dem Neustart wirksam -- und so vermerkt");
		assertTrue(Files.exists(gameDir.resolve("mods").resolve("cool-mod-1.0.0.jar")), "die Jar liegt in mods/");
	}

	@Test
	public void withoutOptInNothingIsDownloadedOrStaged() throws Exception {
		JavaModStagingException e = expectThrows(JavaModStagingException.class,
				() -> RegistryJavaModStaging.stage(javaModEntry(trusted), stager(), () -> false));
		assertTrue(e.getMessage().contains("ausgeschaltet"), e.getMessage());
		assertModsEmpty();
	}

	@Test
	public void aKlasseAEntryIsNeverStagedAsJar() throws Exception {
		RegistryPackageEntry klasseA = new RegistryPackageEntry("de.autor.theme", "Theme", "theme", "Autorin",
				null, null, null, null, "MIT", "https://github.com/autor/theme", List.of(), "1.0.0",
				"2026-10-03T00:00:00Z", null, List.of(), List.of());
		expectThrows(JavaModStagingException.class, () -> RegistryJavaModStaging.stage(klasseA, stager(), () -> true));
		assertModsEmpty();
	}

	@Test
	public void foreignSignatureIsRejectedBeforeAnythingReachesMods() throws Exception {
		KeyPair foreign = VpkgFixtures.generateEd25519KeyPair();
		expectThrows(JavaModStagingException.class,
				() -> RegistryJavaModStaging.stage(javaModEntry(foreign), stager(), () -> true));
		assertModsEmpty();
	}

	@Test
	public void missingLatestVersionIsReportedNotStaged() throws Exception {
		RegistryPackageEntry entry = javaModEntry(trusted);
		RegistryPackageEntry wrongLatest = new RegistryPackageEntry(entry.id(), entry.name(), entry.category(),
				entry.authorName(), null, null, null, null, entry.license(), entry.repository(), List.of(),
				"9.9.9", entry.updatedAt(), entry.contentClass(), entry.javaModVersions(), entry.minecraftVersions());
		JavaModStagingException e = expectThrows(JavaModStagingException.class,
				() -> RegistryJavaModStaging.stage(wrongLatest, stager(), () -> true));
		assertTrue(e.getMessage().contains("9.9.9"), e.getMessage());
		assertModsEmpty();
	}

	// --- Hilfen ---------------------------------------------------------

	private JavaModStager stager() {
		JarSource fixed = (offer, tempDir) -> {
			Path out = tempDir.resolve(offer.fileName());
			Files.write(out, JAR);
			return out;
		};
		return JavaModStager.forGameDir(gameDir, fixed, () -> {
		}, InstalledModConflictCheck.NONE);
	}

	private static RegistryPackageEntry javaModEntry(KeyPair signer) throws IOException {
		String sha = shaOf(JAR);
		String signature = Base64.getEncoder().encodeToString(VpkgFixtures.signSha256Digest(JAR, signer.getPrivate()));
		RegistryJavaModVersion version = new RegistryJavaModVersion("1.0.0",
				URI.create("https://github.com/autor/cool-mod/releases/download/v1.0.0/cool-mod-1.0.0.jar"),
				"cool-mod-1.0.0.jar", sha, signature, JAR.length, List.of(), "2026-10-03T00:00:00Z", null);
		return new RegistryPackageEntry("de.autor.coolmod", "Cool Mod", "hud", "Autorin", null, null, null, null,
				"MIT", "https://github.com/autor/cool-mod", List.of(), "1.0.0", "2026-10-03T00:00:00Z",
				RegistryPackageEntry.CONTENT_CLASS_JAVAMOD, List.of(version), List.of());
	}

	private void assertModsEmpty() throws IOException {
		Path mods = gameDir.resolve("mods");
		if (!Files.exists(mods)) {
			return;
		}
		try (Stream<Path> files = Files.list(mods)) {
			assertFalse(files.findAny().isPresent(), "in mods/ darf nichts gelandet sein");
		}
	}

	private static String shaOf(byte[] bytes) throws IOException {
		Path tmp = Files.createTempFile("vitrine-sha-", ".bin");
		try {
			Files.write(tmp, bytes);
			return VpkgIntegrityValidator.sha256Hex(tmp);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	@Test
	public void entryIdIsTheStagedId() throws Exception {
		StagedMod staged = RegistryJavaModStaging.stage(javaModEntry(trusted), stager(), () -> true);
		assertEquals(staged.id(), "de.autor.coolmod");
	}
}
