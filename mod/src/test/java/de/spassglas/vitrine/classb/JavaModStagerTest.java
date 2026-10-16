package de.spassglas.vitrine.classb;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J2 -- headless-beweisbarer Kern: Staging nach {@code mods/}, Pruefsumme
 * <b>vor</b> dem Ablegen (Charter-Regel 3), Ruecknahme vor dem Neustart,
 * Nicht-Ueberschreiben, und der Neustart-Seam.
 *
 * <p><b>GATE33:</b> {@link JavaModStager#stage} verlangt seit GATE33 immer eine
 * Ed25519-Signatur, geprueft ausschliesslich gegen
 * {@code de.spassglas.vitrine.vpkg.EmbeddedSigningKey#load()} -- diese Testklasse ersetzt den eingebetteten
 * Schluessel deshalb ueber {@link SigningKeyTestSupport#install}
 * durch ein eigenes "vertrautes" Test-Schluesselpaar (dasselbe Muster wie
 * {@code ForeignSigningKeyRejectedTest} fuer GATE29) und signiert jedes
 * Angebot, das erfolgreich staged, damit.</p>
 */
public final class JavaModStagerTest {

	private Path gameDir;
	private Path modsDir;
	private Path vitrineDir;
	private Path trashDir;
	private CountingRestart restart;

	/** Der Schluessel, den {@link SigningKeyTestSupport#install} als "vertraut" setzt. */
	private KeyPair trustedKeyPair;
	/** Der Schluessel eines Angreifers -- signiert, ist aber niemals der vertraute Anker. */
	private KeyPair foreignKeyPair;

	@BeforeMethod
	public void setUp() throws IOException {
		gameDir = Files.createTempDirectory("vitrine-j2-");
		modsDir = gameDir.resolve("mods");
		vitrineDir = gameDir.resolve("config").resolve("vitrine");
		trashDir = vitrineDir.resolve("trash");
		restart = new CountingRestart();

		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		foreignKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (gameDir == null || !Files.exists(gameDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(gameDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	// --- Fertig-Kriterium: Staging nur nach passender Pruefsumme ------------

	@Test
	public void stagingCopiesJarToModsDirOnlyAfterChecksumMatches() throws Exception {
		byte[] jarBytes = "PK pretend-this-is-a-jar".getBytes(StandardCharsets.UTF_8);

		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		JavaModOffer good = signedOffer("de.autor.coolmod", "cool-mod-1.0.0.jar", jarBytes, trustedKeyPair);

		StagedMod entry = stager.stage(good);

		Path staged = modsDir.resolve("cool-mod-1.0.0.jar");
		assertTrue(Files.exists(staged), "nach passender Pruefsumme liegt die Jar in mods/");
		assertEquals(Files.readAllBytes(staged), jarBytes);
		assertTrue(entry.pendingRestart());
		assertEquals(stager.pendingRestarts().stream().map(StagedMod::id).toList(), List.of("de.autor.coolmod"));
		assertTrue(Files.exists(vitrineDir.resolve("staged-mods.json")), "staged-mods.json wurde geschrieben");
		assertEquals(restart.count.get(), 0, "Staging loest keinen Neustart aus");
	}

	@Test
	public void wrongChecksumAbortsWithNothingInModsDirAndNoStateEntry() throws Exception {
		byte[] jarBytes = "echte-jar-bytes".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		// Die Pruefsumme ist bereits falsch -- welcher Schluessel signiert,
		// spielt hier keine Rolle, die Pruefsummenpruefung (Schritt 2) greift
		// zuerst.
		JavaModOffer tampered = signedOfferWithExplicitSha("de.autor.boese", "boese-1.0.0.jar", jarBytes,
				"00".repeat(32), trustedKeyPair, List.of());

		assertThrows(JavaModStagingException.class, () -> stager.stage(tampered));

		assertFalse(Files.exists(modsDir.resolve("boese-1.0.0.jar")), "bei Pruefsummen-Abweichung nichts in mods/");
		if (Files.isDirectory(modsDir)) {
			try (Stream<Path> entries = Files.list(modsDir)) {
				assertEquals(entries.count(), 0, "mods/ bleibt leer");
			}
		}
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")), "kein staged-mods.json-Eintrag");
		assertTrue(stager.pendingRestarts().isEmpty());
		assertEquals(restart.count.get(), 0);
	}

	// --- GATE33-Fertig-Kriterium: kein Vertrauensanker aus dem Angebot ------

	@Test
	public void unsignedOfferIsRejectedBeforeAnythingIsStaged() throws Exception {
		byte[] jarBytes = "unsigniert".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		JavaModOffer unsigned = offer("de.autor.unsigniert", "unsigniert-1.0.0.jar", shaOf(jarBytes));

		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.stage(unsigned));
		assertTrue(ex.getMessage().contains("Signatur"), "Meldung muss die fehlende Signatur nennen");

		assertFalse(Files.exists(modsDir.resolve("unsigniert-1.0.0.jar")),
				"ein unsigniertes Angebot darf nie nach mods/ gelangen (GATE33)");
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")));
		assertTrue(stager.pendingRestarts().isEmpty());
	}

	@Test
	public void offerSignedWithAForeignKeyIsRejectedEvenWithACorrectChecksum() throws Exception {
		byte[] jarBytes = "fremd-signiert".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		// Ein Angreifer signiert mit seinem EIGENEN Schluesselpaar -- die
		// Pruefsumme selbst ist korrekt, nur der Signaturschluessel ist fremd.
		JavaModOffer foreignSigned =
				signedOffer("de.autor.angreifer", "angreifer-1.0.0.jar", jarBytes, foreignKeyPair);

		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.stage(foreignSigned));
		assertTrue(ex.getMessage().contains("Signaturpruefung"), "Meldung muss die fehlgeschlagene Signaturpruefung nennen");

		assertFalse(Files.exists(modsDir.resolve("angreifer-1.0.0.jar")),
				"ein mit einem fremden Schluessel signiertes Angebot darf nie nach mods/ gelangen (GATE33)");
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")));
		assertTrue(stager.pendingRestarts().isEmpty());
	}

	@Test
	public void offerSignedWithTheEmbeddedTrustedKeySucceeds() throws Exception {
		byte[] jarBytes = "vertrauensvoll-signiert".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		JavaModOffer trustedSigned =
				signedOffer("de.autor.vertraut", "vertraut-1.0.0.jar", jarBytes, trustedKeyPair);

		StagedMod entry = stager.stage(trustedSigned);

		assertTrue(Files.exists(modsDir.resolve("vertraut-1.0.0.jar")),
				"mit dem eingebetteten, vertrauten Schluessel signiert muss das Staging gelingen");
		assertEquals(entry.id(), "de.autor.vertraut");
	}

	// --- Fertig-Kriterium: Ruecknahme vor dem Neustart ---------------------

	@Test
	public void takeBackBeforeRestartRemovesTheStagedJarAndEntry() throws Exception {
		byte[] jarBytes = "jar-zum-zuruecknehmen".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		JavaModOffer o = signedOffer("de.autor.weg", "weg-2.0.0.jar", jarBytes, trustedKeyPair);
		stager.stage(o);
		assertTrue(Files.exists(modsDir.resolve("weg-2.0.0.jar")));

		stager.unstage("de.autor.weg");

		assertFalse(Files.exists(modsDir.resolve("weg-2.0.0.jar")), "Jar nicht mehr in mods/");
		assertTrue(Files.isDirectory(trashDir), "Papierkorb angelegt");
		try (Stream<Path> trash = Files.list(trashDir)) {
			assertEquals(trash.count(), 1, "die zurueckgenommene Jar liegt im Papierkorb");
		}
		assertTrue(stager.stagedMods().isEmpty(), "kein staged-mods.json-Eintrag mehr");
		assertEquals(restart.count.get(), 0, "Ruecknahme loest keinen Neustart aus");
	}

	@Test
	public void takeBackAfterRestartIsRefusedBecauseTheJarWouldAlreadyBeLoaded() throws Exception {
		byte[] jarBytes = "schon-geladen".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		stager.stage(signedOffer("de.autor.load", "load-1.0.0.jar", jarBytes, trustedKeyPair));

		stager.markAllPendingAsLoaded();

		assertThrows(JavaModStagingException.class, () -> stager.unstage("de.autor.load"));
		assertTrue(Files.exists(modsDir.resolve("load-1.0.0.jar")), "geladene Jar bleibt liegen");
		assertTrue(stager.pendingRestarts().isEmpty());
	}

	// --- Fertig-Kriterium: nie ueberschreiben -----------------------------

	@Test
	public void existingJarWithSameNameIsRejectedAndOriginalUntouched() throws Exception {
		Files.createDirectories(modsDir);
		byte[] original = "das-original-bleibt".getBytes(StandardCharsets.UTF_8);
		Path existing = modsDir.resolve("kollision-1.0.0.jar");
		Files.write(existing, original);

		byte[] incoming = "der-neuling".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(incoming));
		JavaModOffer o = signedOffer("de.autor.kollision", "kollision-1.0.0.jar", incoming, trustedKeyPair);

		assertThrows(JavaModStagingException.class, () -> stager.stage(o));

		assertEquals(Files.readAllBytes(existing), original, "die vorhandene Jar wurde nicht angetastet");
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")), "kein Eintrag fuer die abgelehnte Jar");
	}

	// --- Fertig-Kriterium: Sammelknopf -----------------------------------

	@Test
	public void restartNowInvokesTheSeamExactlyOnce() throws Exception {
		byte[] jarBytes = "irgendeine-jar".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes));
		stager.stage(signedOffer("de.autor.neu", "neu-1.0.0.jar", jarBytes, trustedKeyPair));

		stager.restartNow();

		assertEquals(restart.count.get(), 1, "der Neustart-Seam wird genau einmal gerufen");
	}

	// --- Fertig-Kriterium J3: doppelte Mod-ID vor dem Kopieren erkannt -----

	@Test
	public void duplicateModIdIsRejectedBeforeCopyingWithNothingInModsDir() throws Exception {
		byte[] jarBytes = "kollidierende-id".getBytes(StandardCharsets.UTF_8);
		InstalledModConflictCheck alreadyInstalled = new FabricInstalledModConflictCheck(
				() -> Set.of("de.autor.doppelt"), Set::of);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes), alreadyInstalled);
		JavaModOffer clash = signedOffer("de.autor.doppelt", "doppelt-1.0.0.jar", jarBytes, trustedKeyPair);

		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.stage(clash));
		assertTrue(ex.getMessage().contains("de.autor.doppelt"), "Meldung nennt die doppelte Mod-ID");

		assertFalse(Files.exists(modsDir.resolve("doppelt-1.0.0.jar")), "bei doppelter ID nichts nach mods/ kopiert");
		if (Files.isDirectory(modsDir)) {
			try (Stream<Path> entries = Files.list(modsDir)) {
				assertEquals(entries.count(), 0, "mods/ bleibt leer");
			}
		}
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")), "kein staged-mods.json-Eintrag");
		assertTrue(stager.pendingRestarts().isEmpty());
	}

	@Test
	public void modIdAlreadyPendingInStagedModsJsonIsRejectedBeforeCopying() throws Exception {
		byte[] jarBytes = "schon-vorgemerkt".getBytes(StandardCharsets.UTF_8);
		InstalledModConflictCheck alreadyPending = new FabricInstalledModConflictCheck(
				Set::of, () -> Set.of("de.autor.pending"));
		JavaModStager stager = newStager(new FixedJarSource(jarBytes), alreadyPending);
		JavaModOffer clash = signedOffer("de.autor.pending", "pending-1.0.0.jar", jarBytes, trustedKeyPair);

		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.stage(clash));
		assertTrue(ex.getMessage().contains("de.autor.pending"), "Meldung nennt die vorgemerkte Mod-ID");
		assertFalse(Files.exists(modsDir.resolve("pending-1.0.0.jar")));
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")));
	}

	@Test
	public void collisionFreeModIdStagesNormallyThroughTheConflictCheck() throws Exception {
		byte[] jarBytes = "freie-id".getBytes(StandardCharsets.UTF_8);
		InstalledModConflictCheck noClash = new FabricInstalledModConflictCheck(
				() -> Set.of("ganz.anderer.mod"), Set::of);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes), noClash);
		JavaModOffer clean = signedOffer("de.autor.frei", "frei-1.0.0.jar", jarBytes, trustedKeyPair);

		StagedMod entry = stager.stage(clean);

		assertTrue(Files.exists(modsDir.resolve("frei-1.0.0.jar")), "kollisionsfreie ID wird nach mods/ kopiert");
		assertEquals(entry.id(), "de.autor.frei");
		assertEquals(stager.pendingRestarts().stream().map(StagedMod::id).toList(), List.of("de.autor.frei"));
	}

	@Test
	public void missingHardDependencyIsRejectedBeforeCopying() throws Exception {
		byte[] jarBytes = "braucht-lib".getBytes(StandardCharsets.UTF_8);
		InstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				() -> Set.of("minecraft", "fabricloader"), Set::of);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes), check);
		JavaModOffer needsLib = signedOffer("de.autor.consumer", "consumer-1.0.0.jar", jarBytes,
				trustedKeyPair, List.of("de.autor.fehlende-lib"));

		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.stage(needsLib));
		assertTrue(ex.getMessage().contains("de.autor.fehlende-lib"), "Meldung nennt die fehlende Abhaengigkeit");
		assertFalse(Files.exists(modsDir.resolve("consumer-1.0.0.jar")));
		assertFalse(Files.exists(vitrineDir.resolve("staged-mods.json")));
	}

	@Test
	public void satisfiedHardDependencyLetsStagingThrough() throws Exception {
		byte[] jarBytes = "lib-da".getBytes(StandardCharsets.UTF_8);
		InstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				() -> Set.of("de.autor.lib"), Set::of);
		JavaModStager stager = newStager(new FixedJarSource(jarBytes), check);
		JavaModOffer needsLib = signedOffer("de.autor.consumer2", "consumer2-1.0.0.jar", jarBytes,
				trustedKeyPair, List.of("de.autor.lib"));

		StagedMod entry = stager.stage(needsLib);

		assertTrue(Files.exists(modsDir.resolve("consumer2-1.0.0.jar")), "erfuellte Abhaengigkeit laesst staging durch");
		assertEquals(entry.id(), "de.autor.consumer2");
	}

	// --- Hilfen ---------------------------------------------------------

	private JavaModStager newStager(JarSource source) {
		return JavaModStager.forGameDir(gameDir, source, restart, InstalledModConflictCheck.NONE);
	}

	private JavaModStager newStager(JarSource source, InstalledModConflictCheck conflictCheck) {
		return JavaModStager.forGameDir(gameDir, source, restart, conflictCheck);
	}

	/** Unsignierte Bequemlichkeit, NUR fuer den GATE33-Ablehnungstest -- {@link JavaModStager#stage} verweigert sie. */
	private static JavaModOffer offer(String id, String fileName, String sha) {
		return JavaModOffer.of(id, id, "Autorin", fileName, sha);
	}

	private static JavaModOffer signedOffer(String id, String fileName, byte[] jarBytes, KeyPair signer)
			throws IOException {
		return signedOffer(id, fileName, jarBytes, signer, List.of());
	}

	/**
	 * Signiert {@code jarBytes} (die tatsaechlichen Datei-Bytes, exakt das, was
	 * {@code VpkgIntegrityValidator#checkSignature} ueber {@code
	 * computeSha256Digest(vpkgFile)} prueft) mit {@code signer} und setzt
	 * {@code expectedSha256Hex} auf den SHA-256-Hexdigest derselben Bytes.
	 */
	private static JavaModOffer signedOffer(String id, String fileName, byte[] jarBytes, KeyPair signer,
			List<String> dependencies) throws IOException {
		return signedOfferWithExplicitSha(id, fileName, jarBytes, shaOf(jarBytes), signer, dependencies);
	}

	/**
	 * Wie {@link #signedOffer(String, String, byte[], KeyPair, List)}, mit
	 * einer explizit vorgegebenen {@code expectedSha256Hex} -- NUR fuer den
	 * Test, der eine absichtlich falsche Pruefsumme braucht (die Signatur
	 * spielt dort keine Rolle, die Pruefsummenpruefung greift zuerst).
	 */
	private static JavaModOffer signedOfferWithExplicitSha(String id, String fileName, byte[] jarBytes, String sha,
			KeyPair signer, List<String> dependencies) {
		byte[] signature = VpkgFixtures.signSha256Digest(jarBytes, signer.getPrivate());
		return JavaModOffer.signed(id, id, "Autorin", fileName, sha, signature, dependencies);
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

	private static final class FixedJarSource implements JarSource {
		private final byte[] content;

		FixedJarSource(byte[] content) {
			this.content = content.clone();
		}

		@Override
		public Path fetch(JavaModOffer offer, Path tempDir) throws IOException {
			Path out = tempDir.resolve(offer.fileName());
			Files.write(out, content);
			return out;
		}
	}

	private static final class CountingRestart implements RestartAction {
		private final AtomicInteger count = new AtomicInteger();

		@Override
		public void restart() {
			count.incrementAndGet();
		}
	}
}
