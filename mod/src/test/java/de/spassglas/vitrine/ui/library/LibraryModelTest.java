package de.spassglas.vitrine.ui.library;

import com.google.gson.JsonObject;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.PackUpdater;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.tile.TilePackageType;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das S5-Fertig-Kriterium ("Reihenfolge ändert sichtbar, welche Textur
 * gewinnt") sowie die uebrigen S5-Verhalten (Aktiv-Schalter, "Alle
 * aktualisieren", Konflikterkennung, Ueberlaufmenue-Deinstallieren) am
 * client-freien {@link LibraryModel}.
 *
 * <p>{@link SharedConstants#createGameVersion()} laeuft einmal fuer die
 * Testklasse, weil {@link VitrinePackProvider#register} beim Bau eines
 * {@link ResourcePackProfile} die Spielversion braucht (siehe
 * {@code VitrinePackProviderTest}).</p>
 */
public final class LibraryModelTest {

	private static final String A_ID = "beispiel.paketa";
	private static final String B_ID = "beispiel.paketb";
	private static final String C_ID = "beispiel.paketc";
	private static final String A_PROFILE = "vitrine/" + A_ID;
	private static final String B_PROFILE = "vitrine/" + B_ID;
	private static final String C_PROFILE = "vitrine/" + C_ID;

	private static final String SHARED_TEXTURE = "assets/minecraft/textures/block/stone.png";
	private static final String OTHER_TEXTURE = "assets/minecraft/textures/block/dirt.png";

	private Path configDir;
	private Path packsDir;
	private KeyPair trustedKeyPair;

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	@BeforeMethod
	public void setUp() throws IOException {
		configDir = Files.createTempDirectory("vitrine-library-model-test-");
		packsDir = Files.createDirectories(configDir.resolve("packs"));

		// GATE29: die Sicherheitspruefung beim Scan (register()) laeuft gegen
		// EmbeddedSigningKey.load() -- Fixtures muessen deshalb mit dem
		// Test-Override signieren, siehe SigningKeyTestSupport#install.
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() {
		SigningKeyTestSupport.reset();
	}

	// --- S5-Fertig-Kriterium ------------------------------------------------

	@Test
	public void reorderingChangesWhichTextureWins() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE), active(B_PROFILE));

		VitrinePackProvider provider = VitrinePackProvider.forConfigDir(configDir);
		RecordingReload reload = new RecordingReload();
		LibraryModel model = new LibraryModel(configDir, provider, reload,
				new RecordingActive(), new RecordingRemover());

		// Ausgangslage: A steht oben (state.json-Index 0) -> A wird als LETZTES
		// beim Provider angemeldet und gewinnt bei gleichem Ressourcenpfad.
		CollectingProfileAdder before = new CollectingProfileAdder();
		provider.register(before);
		assertEquals(before.ids(), List.of(B_PROFILE, A_PROFILE),
				"weiter oben in der Bibliothek = zuletzt angemeldet");
		assertEquals(model.conflicts().size(), 1);
		assertEquals(model.conflicts().get(0).winner().packId(), A_PROFILE);

		// Reihenfolge aendern: A nach unten. Schreibt state.json um, fordert
		// GENAU EINEN gesammelten Reload ueber alle aktiven IDs an.
		assertTrue(model.moveDown(A_PROFILE));
		assertEquals(reload.calls.size(), 1);
		assertEquals(new HashSet<>(reload.calls.get(0)), Set.of(A_PROFILE, B_PROFILE));

		// Erneuter Scan derselben Provider-Instanz (wie im echten Spiel bei
		// jedem Ressourcen-Reload): jetzt gewinnt B.
		CollectingProfileAdder after = new CollectingProfileAdder();
		provider.register(after);
		assertEquals(after.ids(), List.of(A_PROFILE, B_PROFILE),
				"nach dem Verschieben steht B oben und wird zuletzt angemeldet");
		assertEquals(model.conflicts().get(0).winner().packId(), B_PROFILE,
				"das Modell nennt jetzt B als Gewinner der aktuellen Reihenfolge");
	}

	// --- Aktiv-Schalter --------------------------------------------------

	@Test
	public void toggleActiveRequestsDeactivationThenActivation() throws IOException, PackInstallException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingActive active = new RecordingActive();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				new RecordingReload(), active, new RecordingRemover());

		model.setActive(A_PROFILE, false);
		model.setActive(A_ID, true); // auch die Roh-ID muss auf dieselbe Profil-ID abbilden

		assertEquals(active.calls, List.of(A_PROFILE + "=false", A_PROFILE + "=true"));
	}

	// --- Alle aktualisieren -------------------------------------------

	@Test
	public void updateAllRequestsExactlyOneReloadOverAllActiveIds() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(OTHER_TEXTURE));
		installPack("c.vpkg", C_ID, "Paket C", List.of("assets/minecraft/textures/block/sand.png"));
		writeState(active(A_PROFILE), inactive(B_PROFILE), active(C_PROFILE));

		RecordingReload reload = new RecordingReload();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				reload, new RecordingActive(), new RecordingRemover());

		List<PackUpdater.UpdateResult> results = model.updateAll();

		assertTrue(results.isEmpty(), "ohne UpdateRunner-Seam (Vor-K5-Anschluss) bleibt das Ergebnis leer");
		assertEquals(reload.calls.size(), 1, "genau ein gesammelter Reload");
		assertEquals(new HashSet<>(reload.calls.get(0)), Set.of(A_PROFILE, C_PROFILE),
				"nur die aktiven IDs, nicht das inaktive Paket B");
	}

	// --- ROADMAP GATE13: "Alle aktualisieren" ruft tatsaechlich den Updater auf ---

	@Test
	public void updateAllInvokesTheInjectedUpdateRunnerBeforeReloading() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingReload reload = new RecordingReload();
		RecordingUpdateRunner updateRunner = new RecordingUpdateRunner(
				List.of(new PackUpdater.UpdateResult(A_ID, PackUpdater.Outcome.UPDATED, "1.0.0", "1.1.0")));
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				reload, new RecordingActive(), new RecordingRemover(), LibraryModel.CatalogVersions.NONE,
				updateRunner);

		List<PackUpdater.UpdateResult> results = model.updateAll();

		assertEquals(updateRunner.callCount, 1, "'Alle aktualisieren' muss den echten PackUpdater-Seam aufrufen");
		assertEquals(results.size(), 1);
		assertTrue(results.get(0).updated());
		assertEquals(reload.calls.size(), 1, "der Sammel-Reload bleibt bestehen, auch mit echtem Updater");
	}

	@Test
	public void updateAllStillReloadsEvenWhenTheUpdateRunnerReportsAFailure() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingReload reload = new RecordingReload();
		RecordingUpdateRunner updateRunner = new RecordingUpdateRunner(
				List.of(new PackUpdater.UpdateResult(A_ID, PackUpdater.Outcome.FAILED_ROLLED_BACK, "1.0.0", "1.1.0")));
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				reload, new RecordingActive(), new RecordingRemover(), LibraryModel.CatalogVersions.NONE,
				updateRunner);

		List<PackUpdater.UpdateResult> results = model.updateAll();

		assertEquals(results.size(), 1);
		assertTrue(results.get(0).rolledBack(), "K5-Rueckfall: die alte Version laeuft weiter, kein Absturz");
		assertEquals(reload.calls.size(), 1, "die Bibliothek bleibt bedienbar, auch nach einem fehlgeschlagenen Update");
	}

	// --- ROADMAP GATE83: Ueberlaufmenue "Aktualisieren" je Zeile ---------

	@Test
	public void updateOneInvokesTheInjectedSingleUpdateRunnerAndReloads() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingReload reload = new RecordingReload();
		RecordingSingleUpdateRunner singleUpdateRunner = new RecordingSingleUpdateRunner(
				new PackUpdater.UpdateResult(A_ID, PackUpdater.Outcome.UPDATED, "1.0.0", "1.1.0"));
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				reload, new RecordingActive(), new RecordingRemover(), LibraryModel.CatalogVersions.NONE,
				LibraryModel.UpdateRunner.NONE, singleUpdateRunner);

		PackUpdater.UpdateResult result = model.updateOne(A_PROFILE);

		assertEquals(singleUpdateRunner.calls, List.of(A_ID),
				"'Aktualisieren' im Ueberlaufmenue muss den echten Einzelpaket-Seam mit der Roh-ID aufrufen");
		assertTrue(result.updated());
		assertEquals(reload.calls.size(), 1, "auch ein Einzel-Update loest den gesammelten Reload aus");
		assertEquals(new HashSet<>(reload.calls.get(0)), Set.of(A_PROFILE));
	}

	@Test
	public void updateOneAcceptsTheRawManifestIdToo() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingSingleUpdateRunner singleUpdateRunner = new RecordingSingleUpdateRunner(
				new PackUpdater.UpdateResult(A_ID, PackUpdater.Outcome.UPDATED, "1.0.0", "1.1.0"));
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				new RecordingReload(), new RecordingActive(), new RecordingRemover(), LibraryModel.CatalogVersions.NONE,
				LibraryModel.UpdateRunner.NONE, singleUpdateRunner);

		model.updateOne(A_ID);

		assertEquals(singleUpdateRunner.calls, List.of(A_ID));
	}

	// --- Konflikterkennung -------------------------------------------

	@Test
	public void sharedResourcePathBetweenTwoActivePacksIsReportedAsConflict() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE), active(B_PROFILE));

		LibraryModel model = newReadOnlyModel();

		List<LibraryConflict> conflicts = model.conflicts();
		assertEquals(conflicts.size(), 1);
		assertEquals(conflicts.get(0).resourcePath(), SHARED_TEXTURE);
		assertEquals(conflicts.get(0).contenders().size(), 2);
		assertEquals(conflicts.get(0).winner().packId(), A_PROFILE);
	}

	@Test
	public void disjointResourcePathsProduceNoConflict() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(OTHER_TEXTURE));
		writeState(active(A_PROFILE), active(B_PROFILE));

		assertTrue(newReadOnlyModel().conflicts().isEmpty());
	}

	@Test
	public void sharedPathWithAnInactivePackIsNoConflict() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE), inactive(B_PROFILE));

		assertTrue(newReadOnlyModel().conflicts().isEmpty(),
				"nur AKTIVE Pakete koennen sich eine Ressource streitig machen");
	}

	// --- Ueberlaufmenue: Deinstallieren --------------------------------

	@Test
	public void overflowMenuUninstallCallsUninstaller() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE));

		RecordingRemover remover = new RecordingRemover();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				new RecordingReload(), new RecordingActive(), remover);

		model.uninstall(A_PROFILE);

		assertEquals(remover.removed, List.of(A_PROFILE));
	}

	// --- entries() -------------------------------------------------

	@Test
	public void entriesReflectStateOrderAndManifestMetadata() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(OTHER_TEXTURE));
		writeState(active(A_PROFILE), inactive(B_PROFILE));

		List<LibraryEntry> entries = newReadOnlyModel().entries();

		assertEquals(entries.size(), 2);
		LibraryEntry a = entries.get(0);
		assertEquals(a.packId(), A_PROFILE);
		assertEquals(a.manifestId(), A_ID);
		assertEquals(a.name(), "Paket A");
		assertEquals(a.author(), "Vitrine-Testfixture");
		assertEquals(a.version(), "1.0.0");
		assertEquals(a.type(), TilePackageType.TEXTUREN);
		assertTrue(a.active());
		assertEquals(a.position(), 0);
		assertTrue(a.isResourcePack());
		assertFalse(entries.get(1).active());
		assertEquals(entries.get(1).position(), 1);
	}

	/**
	 * ROADMAP GATE115: vorher wurde ein solcher Eintrag nur geloggt und
	 * uebersprungen -- er verschwand lautlos aus der Bibliothek, blieb aber fuer
	 * immer in {@code state.json}. Jetzt bleibt er sichtbar als kaputt/fehlend
	 * stehen (z. B. nach einem zwischen den {@code PackUpdater}-Schritten
	 * abgebrochenen Update-Lauf, dessen Backup beim Start nicht wiederherstellbar
	 * war).
	 */
	@Test
	public void entriesShowAnInstalledPackageWithoutAnyFileAsMissingInsteadOfSkippingIt() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		// B ist in state.json installiert, aber es liegt (anders als A) gar keine
		// .vpkg-Datei mehr in packs/ -- der GATE115-Zustand.
		writeState(active(A_PROFILE), active(B_PROFILE));

		List<LibraryEntry> entries = newReadOnlyModel().entries();

		assertEquals(entries.size(), 2, "das verwaiste Paket darf nicht aus der Liste verschwinden");
		LibraryEntry missing = entries.get(1);
		assertEquals(missing.packId(), B_PROFILE);
		assertEquals(missing.manifestId(), B_ID);
		assertTrue(missing.isMissing(), "ohne .vpkg-Datei muss der Eintrag als kaputt/fehlend markiert sein");
		assertEquals(missing.type(), null, "ohne Manifest gibt es keinen Typ");
		assertTrue(missing.resourcePaths().isEmpty());
		assertFalse(missing.isResourcePack());
	}

	@Test
	public void moveToClampsAndReportsWhetherOrderChanged() throws IOException {
		installPack("a.vpkg", A_ID, "Paket A", List.of(SHARED_TEXTURE));
		installPack("b.vpkg", B_ID, "Paket B", List.of(SHARED_TEXTURE));
		writeState(active(A_PROFILE), active(B_PROFILE));

		RecordingReload reload = new RecordingReload();
		LibraryModel model = new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				reload, new RecordingActive(), new RecordingRemover());

		assertFalse(model.moveUp(A_PROFILE), "A steht schon ganz oben -- keine Aenderung, kein Reload");
		assertTrue(reload.calls.isEmpty());

		assertTrue(model.moveTo(A_PROFILE, 99), "auf die letzte Position geklemmt");
		assertEquals(model.entries().get(1).packId(), A_PROFILE);
		assertEquals(reload.calls.size(), 1);
	}

	// --- Hilfsmittel ----------------------------------------------

	private LibraryModel newReadOnlyModel() {
		return new LibraryModel(configDir, VitrinePackProvider.forConfigDir(configDir),
				new RecordingReload(), new RecordingActive(), new RecordingRemover());
	}

	private void installPack(String fileName, String manifestId, String name, List<String> resourcePaths)
			throws IOException {
		byte[] bytes = VpkgFixtures.packageWithResourcePaths(manifestId, name, resourcePaths);
		Path vpkgFile = packsDir.resolve(fileName);
		Files.write(vpkgFile, bytes);
		writeValidSidecar(vpkgFile, bytes);
	}

	private void writeState(PackState.PackEntry... packs) {
		PackState state = PackState.createDefault();
		for (PackState.PackEntry pack : packs) {
			state.addOrUpdate(pack.packId, pack.active);
		}
		try {
			PackStateStore.save(configDir, state);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static PackState.PackEntry active(String profileId) {
		return new PackState.PackEntry(profileId, true);
	}

	private static PackState.PackEntry inactive(String profileId) {
		return new PackState.PackEntry(profileId, false);
	}

	private void writeValidSidecar(Path vpkgFile, byte[] packageBytes) throws IOException {
		byte[] signature = VpkgFixtures.signSha256Digest(packageBytes, trustedKeyPair.getPrivate());
		JsonObject json = new JsonObject();
		json.addProperty("sha256", VpkgFixtures.sha256Hex(packageBytes));
		json.addProperty("signature", Base64.getEncoder().encodeToString(signature));
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded()));
		Path sidecar = vpkgFile.resolveSibling(vpkgFile.getFileName().toString() + ".integrity.json");
		Files.writeString(sidecar, json.toString(), StandardCharsets.UTF_8);
	}

	private static final class RecordingReload implements LibraryModel.ReloadRequester {
		private final List<List<String>> calls = new ArrayList<>();

		@Override
		public void requestReload(Collection<String> affectedPackIds) {
			calls.add(new ArrayList<>(affectedPackIds));
		}
	}

	private static final class RecordingActive implements LibraryModel.ActiveSetter {
		private final List<String> calls = new ArrayList<>();

		@Override
		public void setActive(String packId, boolean active) {
			calls.add(packId + "=" + active);
		}
	}

	private static final class RecordingRemover implements LibraryModel.PackRemover {
		private final List<String> removed = new ArrayList<>();

		@Override
		public void remove(String packId) {
			removed.add(packId);
		}
	}

	/** ROADMAP GATE13: beweist, dass {@code LibraryModel.updateAll()} den echten Update-Seam aufruft. */
	private static final class RecordingUpdateRunner implements LibraryModel.UpdateRunner {
		private final List<PackUpdater.UpdateResult> results;
		private int callCount;

		private RecordingUpdateRunner(List<PackUpdater.UpdateResult> results) {
			this.results = results;
		}

		@Override
		public List<PackUpdater.UpdateResult> updateAll() {
			callCount++;
			return results;
		}
	}

	/** ROADMAP GATE83: beweist, dass {@code LibraryModel.updateOne(...)} den echten Einzelpaket-Seam aufruft. */
	private static final class RecordingSingleUpdateRunner implements LibraryModel.SingleUpdateRunner {
		private final PackUpdater.UpdateResult result;
		private final List<String> calls = new ArrayList<>();

		private RecordingSingleUpdateRunner(PackUpdater.UpdateResult result) {
			this.result = result;
		}

		@Override
		public PackUpdater.UpdateResult updateOne(String manifestId) {
			calls.add(manifestId);
			return result;
		}
	}

	private static final class CollectingProfileAdder implements Consumer<ResourcePackProfile> {
		private final List<ResourcePackProfile> profiles = new ArrayList<>();

		@Override
		public void accept(ResourcePackProfile profile) {
			profiles.add(profile);
		}

		private List<String> ids() {
			return profiles.stream().map(ResourcePackProfile::getId).toList();
		}
	}
}
