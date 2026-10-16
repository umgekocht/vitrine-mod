package de.spassglas.vitrine.pack;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import net.minecraft.SharedConstants;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das Fertig-Kriterium von H1 ab: "Ein Paket im Ordner taucht in der
 * Vanilla-Pack-Liste auf." {@link VitrinePackProvider#register} meldet ein
 * Profil ueber genau denselben {@code Consumer<ResourcePackProfile>} an, den
 * Vanillas eigener {@link net.minecraft.resource.ResourcePackManager} beim
 * Aufbau seiner Pack-Liste benutzt -- ein sammelnder Test-{@code Consumer}
 * beweist deshalb ohne echten Client, dass ein Paket "in der Liste auftaucht".
 *
 * <p>{@link SharedConstants#createGameVersion()} laeuft einmal fuer die ganze
 * Testklasse: {@link VpkgResourcePack#parseMetadata} braucht {@link
 * SharedConstants#getGameVersion()}, genau wie es Vanillas eigener {@code
 * ResourcePackProfile.loadMetadata} beim Aufbau eines echten Profils auch
 * braucht. Bewusst NICHT {@code Bootstrap.initialize()} (registriert Bloecke/
 * Fluessigkeiten/Items) -- das scheitert ausserhalb von Fabric Looms
 * Entwicklungs-Klassenlader mit einem {@code IllegalAccessError} auf
 * feld-generierten Setter-Methoden und wird fuer die reine Metadaten-Frage
 * hier auch nicht gebraucht. Kein Rendering, kein Fenster, keine echte
 * Spielsitzung noetig -- reine Datenregistrierung.</p>
 */
public final class VitrinePackProviderTest {

	private Path tempDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-pack-provider-test-");

		// GATE29: die Sicherheitspruefung beim Scan (register()) laeuft gegen
		// EmbeddedSigningKey.load() -- Fixtures muessen deshalb mit dem
		// Test-Override signieren, siehe SigningKeyTestSupport#install.
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

	// --- Positivfall: gutartiges, vollstaendig geprueftes Paket -------------

	@Test
	public void validPackageInPacksDirIsRegisteredAsResourcePackProfile() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = tempDir.resolve("beispiel.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 1,
				"genau ein gueltiges .vpkg im Ordner muss genau ein Profil ergeben");
		ResourcePackProfile profile = profileAdder.profiles.get(0);
		assertEquals(profile.getId(), "vitrine/beispiel.minimalpaket",
				"Profil-ID muss aus dem Manifest stammen, mit 'vitrine/'-Praefix (siehe VitrinePackProvider)");
	}

	/**
	 * Beweist nebenbei, dass {@link VitrinePackProvider#register} die
	 * Begleitdatei ({@code *.vpkg.integrity.json}) selbst korrekt ignoriert
	 * (Endungsfilter auf {@code .vpkg}) -- trotz zweier Dateien im Ordner
	 * kommt genau ein Profil an, keines fuer die Begleitdatei.
	 */
	@Test
	public void integritySidecarFileItselfIsNeverTreatedAsAPackage() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = tempDir.resolve("beispiel.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);

		assertTrue(Files.exists(tempDir.resolve("beispiel.vpkg.integrity.json")),
				"Testaufbau fehlerhaft: Begleitdatei haette angelegt sein muessen");

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 1,
				"die .integrity.json-Begleitdatei darf nie selbst als Paket gezaehlt werden");
	}

	// --- Negativfall 1: Sicherheitspruefung (P3) schlaegt fehl --------------

	@Test
	public void packageFailingSecurityCheckIsNotRegistered() throws IOException {
		byte[] packageBytes = VpkgFixtures.zipSlipPackage();
		Path vpkgFile = tempDir.resolve("boesartig.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 0,
				"ein Paket mit Zip-Slip-Eintrag (P3) darf nie als Ressourcen-Pack-Profil angemeldet werden");
	}

	// --- Negativfall 2: Integritaetspruefung (P4) schlaegt fehl -------------

	@Test
	public void packageWithoutIntegritySidecarIsNotRegistered() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = tempDir.resolve("ohne-beleg.vpkg");
		Files.write(vpkgFile, packageBytes);
		// bewusst keine *.vpkg.integrity.json angelegt -- P4 muss das als
		// "nicht geprueft" werten und die Anmeldung verweigern.

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 0,
				"ein Paket ohne Integritaets-Beleg (P4) darf nie als Ressourcen-Pack-Profil angemeldet werden");
	}

	@Test
	public void packageWithTamperedSignatureIsNotRegistered() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = tempDir.resolve("manipuliert.vpkg");
		Files.write(vpkgFile, packageBytes);

		KeyPair keyPair = VpkgFixtures.generateEd25519KeyPair();
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, keyPair.getPrivate());
		signature[0] ^= 0xFF; // ein gekipptes Bit reicht, um Ed25519 scheitern zu lassen
		writeSidecar(vpkgFile, VpkgFixtures.sha256Hex(packageBytes), signature, keyPair.getPublic());

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(tempDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 0,
				"ein Paket mit manipulierter Signatur (P4) darf nie als Ressourcen-Pack-Profil angemeldet werden");
	}

	// --- Ordner ohne Pakete / nicht vorhanden --------------------------------

	@Test
	public void nonExistentPacksDirRegistersNothingWithoutThrowing() {
		Path missingDir = tempDir.resolve("existiert-nicht");

		CollectingProfileAdder profileAdder = new CollectingProfileAdder();
		VitrinePackProvider.forPacksDir(missingDir).register(profileAdder);

		assertEquals(profileAdder.profiles.size(), 0, "ein fehlender Paketordner darf nichts anmelden, aber auch nicht werfen");
	}

	// --- findPackFile (ROADMAP H5: Datei zu einer Paket-ID wiederfinden) ----

	@Test
	public void findPackFileLocatesTheFileMatchingTheManifestId() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		// bewusst ein Dateiname, der nichts mit der Manifest-ID zu tun hat --
		// die CLI laesst den Dateinamen beim "pack"-Befehl frei waehlen.
		Path vpkgFile = tempDir.resolve("voellig-anderer-dateiname.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);

		Optional<Path> found = VitrinePackProvider.forPacksDir(tempDir).findPackFile("vitrine/beispiel.minimalpaket");

		assertTrue(found.isPresent(), "die Datei muss ueber ihr Manifest gefunden werden, unabhaengig vom Dateinamen");
		assertEquals(found.get(), vpkgFile);
	}

	@Test
	public void findPackFileReturnsEmptyForUnknownPackId() throws IOException {
		byte[] packageBytes = VpkgFixtures.minimalValidPackage();
		Path vpkgFile = tempDir.resolve("beispiel.vpkg");
		Files.write(vpkgFile, packageBytes);
		writeValidSidecar(vpkgFile, packageBytes);

		Optional<Path> found = VitrinePackProvider.forPacksDir(tempDir).findPackFile("vitrine/nicht-vorhanden");

		assertTrue(found.isEmpty());
	}

	@Test
	public void findPackFileOnNonExistentDirReturnsEmptyWithoutThrowing() {
		Path missingDir = tempDir.resolve("existiert-nicht");

		Optional<Path> found = VitrinePackProvider.forPacksDir(missingDir).findPackFile("vitrine/irgendwas");

		assertTrue(found.isEmpty());
	}

	// --- Hilfsfunktionen -----------------------------------------------------

	// --- GATE39: unveraenderte Pakete werden nicht bei jedem Scan neu geprueft --

	@Test
	public void secondScanWithoutChangesRepeatsNoFullValidation() throws IOException {
		int packages = 25;
		for (int i = 0; i < packages; i++) {
			byte[] bytes = VpkgFixtures.minimalValidPackage();
			Path file = tempDir.resolve("paket" + i + ".vpkg");
			Files.write(file, bytes);
			writeValidSidecar(file, bytes);
		}
		VitrinePackProvider provider = VitrinePackProvider.forPacksDir(tempDir);

		long firstStart = System.nanoTime();
		provider.register(new CollectingProfileAdder());
		long firstNanos = System.nanoTime() - firstStart;
		int afterFirst = provider.fullValidationCount();

		long secondStart = System.nanoTime();
		CollectingProfileAdder second = new CollectingProfileAdder();
		provider.register(second);
		long secondNanos = System.nanoTime() - secondStart;

		assertEquals(afterFirst, packages, "der erste Scan prueft jede Datei voll");
		assertEquals(provider.fullValidationCount(), afterFirst,
				"der zweite Scan ohne Aenderung darf keine Datei erneut voll pruefen (Hash, Signatur, ZIP)");
		assertEquals(second.profiles.size(), packages,
				"angemeldet wird trotzdem jedes Paket -- nur eben aus dem gespeicherten Urteil");
		// Messwert fuers Log; die harte Aussage oben ist die Zaehlung, nicht die Uhr.
		System.out.printf("GATE39-Messung: erster Scan %.1f ms, zweiter %.1f ms (%d Pakete)%n",
				firstNanos / 1e6, secondNanos / 1e6, packages);
	}

	@Test
	public void changedPackageOrSidecarIsValidatedAgain() throws IOException {
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Path file = tempDir.resolve("aendert-sich.vpkg");
		Files.write(file, bytes);
		writeValidSidecar(file, bytes);
		VitrinePackProvider provider = VitrinePackProvider.forPacksDir(tempDir);
		provider.register(new CollectingProfileAdder());
		int before = provider.fullValidationCount();

		// Aenderungszeit verschieben -- wie nach einem Update der Datei.
		Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
				Files.getLastModifiedTime(file).toMillis() + 60_000));
		provider.register(new CollectingProfileAdder());

		assertEquals(provider.fullValidationCount(), before + 1, "eine geaenderte Datei wird erneut voll geprueft");
	}

	@Test
	public void tamperedSidecarIsNotTrustedFromCache() throws IOException {
		byte[] bytes = VpkgFixtures.minimalValidPackage();
		Path file = tempDir.resolve("beleg.vpkg");
		Files.write(file, bytes);
		writeValidSidecar(file, bytes);
		VitrinePackProvider provider = VitrinePackProvider.forPacksDir(tempDir);
		CollectingProfileAdder first = new CollectingProfileAdder();
		provider.register(first);
		assertEquals(first.profiles.size(), 1);

		// Beleg durch Muell ersetzen: das gespeicherte "gueltig" darf nicht greifen.
		Files.writeString(de.spassglas.vitrine.vpkg.VpkgIntegritySidecar.sidecarPathFor(file),
				"{\"kaputt\": true}", StandardCharsets.UTF_8);
		CollectingProfileAdder second = new CollectingProfileAdder();
		provider.register(second);

		assertTrue(second.profiles.isEmpty(), "ein veraenderter Integritaets-Beleg muss neu geprueft und abgelehnt werden");
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
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

	/** Sammelt jedes angemeldete Profil, genau wie Vanillas eigener {@code ResourcePackManager} es tut. */
	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}
	}
}
