package de.spassglas.vitrine.ui.library;

import de.spassglas.vitrine.catalog.CachedIndexStore;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.UpdateStatus;
import de.spassglas.vitrine.hotapply.HotApplyService;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.PackInstallService;
import de.spassglas.vitrine.pack.PackUninstallService;
import de.spassglas.vitrine.pack.PackUpdater;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freies Modell der Bibliotheks-Ansicht (ROADMAP S5, docs/UI-SPEC.md
 * "6. Bibliothek") -- analog zu {@code ui.detail.PackageDetailModel} und
 * {@code ui.dialog.CapabilityConsentModel}: baut aus {@code state.json} (H4)
 * plus einem rein lesenden {@code .vpkg}-Scan die Bibliotheksliste, ohne eine
 * einzige Minecraft-Klasse.
 *
 * <ul>
 *   <li>{@link #entries()} -- installierte Pakete in der massgeblichen
 *       Reihenfolge aus {@link PackState#packs} (Index 0 = ganz oben,
 *       gewinnt bei gleicher Ressource), je mit Name/Autor/Typ aus dem
 *       Manifest, Aktiv-Flag und Position.</li>
 *   <li>{@link #moveUp}/{@link #moveDown}/{@link #moveTo} -- schreiben
 *       {@link PackState} um (atomar ueber {@link PackStateStore#save}) und
 *       loesen genau <em>einen</em> gesammelten Reload aus
 *       ({@link HotApplyService#requestReload}), ohne die Aktiv-Menge zu
 *       aendern. Sichtbar wird die neue Reihenfolge beim naechsten
 *       {@code VitrinePackProvider}-Scan (H1): das weiter oben stehende Paket
 *       wird als letztes angemeldet und gewinnt bei gleichem Pfad.</li>
 *   <li>{@link #setActive} -- geht ueber den bestehenden
 *       {@code PackInstaller.setActive}-Weg (S4), der De-/Aktivierung beim
 *       {@code HotApplyCoordinator} anfordert.</li>
 *   <li>{@link #updateAll()} -- ein einziger {@code requestReload} ueber alle
 *       aktiven IDs. Ehrlich beschriftet: der echte Versionsabgleich gegen den
 *       Katalog ist K5, hier ist es der Sammel-Reload-Knopf, nichts
 *       Vorgegaukeltes (Charter-Regel 2).</li>
 *   <li>{@link #conflicts()} -- ueberschreiben zwei aktive Pakete denselben
 *       {@code assets/...}-Pfad, meldet das Modell den Konflikt und nennt den
 *       Gewinner bei aktueller Reihenfolge.</li>
 *   <li>{@link #uninstall} -- Ueberlaufmenue "Deinstallieren" ueber
 *       {@code PackUninstallService}.</li>
 * </ul>
 *
 * <p>Die drei Seiten-Effekte (Reload, Aktiv-Schalter, Deinstallieren) laufen
 * ueber austauschbare Schnittstellen, damit Tests sie ohne laufenden Client
 * beobachten koennen ({@link #forConfigDir} verdrahtet die echten Dienste).</p>
 */
public final class LibraryModel {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Library");

	private static final String PACK_ID_PREFIX = "vitrine/";
	private static final String ASSETS_PREFIX = "assets/";

	/** Loest einen gesammelten Ressourcen-Reload aus, ohne die Aktiv-Menge zu aendern (Reihenfolge / "Alle aktualisieren"). */
	@FunctionalInterface
	public interface ReloadRequester {
		void requestReload(Collection<String> affectedPackIds);
	}

	/**
	 * Setzt den Aktiv-Zustand eines installierten Pakets und stoesst den
	 * passenden De-/Aktivierungs-Reload an.
	 *
	 * <p>ROADMAP GATE49: wirft {@link PackInstallException} jetzt durch, statt
	 * sie (wie bis dahin {@code LibraryModel#setActiveViaInstallService}) in
	 * eine fuer die Oberflaeche nicht mehr unterscheidbare {@link
	 * RuntimeException} zu verpacken -- {@code LibraryScreen} braucht
	 * {@link PackInstallException#displayKey()} fuer einen sichtbaren
	 * Klartextfehler (Muster wie {@code PackageDetailScreen#showInstallError}).</p>
	 */
	@FunctionalInterface
	public interface ActiveSetter {
		void setActive(String packId, boolean active) throws PackInstallException;
	}

	/** Deinstalliert ein Paket (Datei in den Papierkorb, Zustand entfernen, Reload). */
	@FunctionalInterface
	public interface PackRemover {
		void remove(String packId);
	}

	/**
	 * ROADMAP GATE13: der echte Versionsabgleich hinter "Alle aktualisieren" --
	 * {@link PackUpdater#updateAll()} im Produktionsfall. Vor dieser Zeile rief
	 * {@link #updateAll()} nur einen Sammel-Reload aus, ohne {@link PackUpdater}
	 * je aufzurufen (deckte sich mit {@link CatalogVersions#NONE}: die Anzeige
	 * "wie viele Updates gibt es" war schon echt, das Update selbst nicht).
	 * {@link #NONE} ist der unveraenderte Vor-K5-Anschluss-Zustand -- kein
	 * Netzaufruf, leeres Ergebnis -- und bleibt der Default fuer die bestehenden
	 * Konstruktoren/Tests, die dieses Verhalten voraussetzen.
	 */
	@FunctionalInterface
	public interface UpdateRunner {
		List<PackUpdater.UpdateResult> updateAll();

		/** Kein echtes Update -- der Vor-K5-Anschluss-Zustand (siehe Klassendoku). */
		UpdateRunner NONE = List::of;
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Überlaufmenü-Eintrag
	 * "Aktualisieren" je Zeile) -- wie {@link UpdateRunner}, aber für genau ein
	 * Paket. Produktion: {@link PackUpdater#update(String)}; im Test ein Fake.
	 */
	@FunctionalInterface
	public interface SingleUpdateRunner {
		PackUpdater.UpdateResult updateOne(String manifestId);

		/** Kein echtes Update -- derselbe Vor-K5-Anschluss-Zustand wie {@link UpdateRunner#NONE}. */
		SingleUpdateRunner NONE = manifestId -> null;
	}

	/**
	 * Seam auf den Katalog-Zustand (ROADMAP K5): liefert je Manifest-ID die
	 * neueste im Katalog geführte Version ({@code IndexPackage.latestVersion()}),
	 * oder leer, wenn der Katalog das Paket nicht (mehr) führt. Im Test ein
	 * Fake, ohne Netz; produktiv aus dem Offline-Cache ({@link CachedIndexStore}).
	 */
	@FunctionalInterface
	public interface CatalogVersions {
		Optional<String> latestVersionFor(String manifestId);

		/** Kein Katalog bekannt -- keine Updates (z. B. vor dem ersten erfolgreichen Katalog-Abruf). */
		CatalogVersions NONE = manifestId -> Optional.empty();
	}

	private final Path vitrineConfigDir;
	private final VitrinePackProvider packProvider;
	private final ReloadRequester reloadRequester;
	private final ActiveSetter activeSetter;
	private final PackRemover packRemover;
	private final CatalogVersions catalogVersions;
	private final UpdateRunner updateRunner;
	private final SingleUpdateRunner singleUpdateRunner;

	public LibraryModel(Path vitrineConfigDir, VitrinePackProvider packProvider, ReloadRequester reloadRequester,
			ActiveSetter activeSetter, PackRemover packRemover) {
		this(vitrineConfigDir, packProvider, reloadRequester, activeSetter, packRemover, CatalogVersions.NONE);
	}

	public LibraryModel(Path vitrineConfigDir, VitrinePackProvider packProvider, ReloadRequester reloadRequester,
			ActiveSetter activeSetter, PackRemover packRemover, CatalogVersions catalogVersions) {
		this(vitrineConfigDir, packProvider, reloadRequester, activeSetter, packRemover, catalogVersions,
				UpdateRunner.NONE);
	}

	/** ROADMAP GATE13: voller Konstruktor mit dem echten Update-Seam ({@link UpdateRunner}). */
	public LibraryModel(Path vitrineConfigDir, VitrinePackProvider packProvider, ReloadRequester reloadRequester,
			ActiveSetter activeSetter, PackRemover packRemover, CatalogVersions catalogVersions,
			UpdateRunner updateRunner) {
		this(vitrineConfigDir, packProvider, reloadRequester, activeSetter, packRemover, catalogVersions,
				updateRunner, SingleUpdateRunner.NONE);
	}

	/** ROADMAP GATE83: voller Konstruktor, zusaetzlich mit dem Einzelpaket-Update-Seam ({@link SingleUpdateRunner}). */
	public LibraryModel(Path vitrineConfigDir, VitrinePackProvider packProvider, ReloadRequester reloadRequester,
			ActiveSetter activeSetter, PackRemover packRemover, CatalogVersions catalogVersions,
			UpdateRunner updateRunner, SingleUpdateRunner singleUpdateRunner) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.packProvider = Objects.requireNonNull(packProvider, "packProvider");
		this.reloadRequester = Objects.requireNonNull(reloadRequester, "reloadRequester");
		this.activeSetter = Objects.requireNonNull(activeSetter, "activeSetter");
		this.packRemover = Objects.requireNonNull(packRemover, "packRemover");
		this.catalogVersions = Objects.requireNonNull(catalogVersions, "catalogVersions");
		this.updateRunner = Objects.requireNonNull(updateRunner, "updateRunner");
		this.singleUpdateRunner = Objects.requireNonNull(singleUpdateRunner, "singleUpdateRunner");
	}

	/** Verdrahtet das Modell mit den echten Client-Diensten (HotApply, PackInstaller.setActive, PackUninstallService, PackUpdater). */
	public static LibraryModel forConfigDir(Path vitrineConfigDir) {
		CachedIndexStore catalogCache = CachedIndexStore.production(vitrineConfigDir);
		return new LibraryModel(vitrineConfigDir,
				VitrinePackProvider.forConfigDir(vitrineConfigDir),
				HotApplyService::requestReload,
				(packId, active) -> PackInstallService.instance().setActive(packId, active),
				PackUninstallService::requestUninstall,
				manifestId -> catalogCache.firstPage().flatMap(page -> page.packages().stream()
						.filter(p -> manifestId.equals(p.id()))
						.map(IndexPackage::latestVersion)
						.findFirst()),
				// GATE38: updateAll() ist asynchron. Dieses join() laeuft
				// ausschliesslich auf dem eigenen Daemon-Thread, den
				// LibraryScreen#onUpdateAll fuer den ganzen Lauf aufmacht (er
				// tut ohnehin blockierendes Netz-I/O) -- nie auf dem
				// Client-Thread, auf dem der Reload selbst stattfindet.
				() -> PackUpdater.forConfigDir(vitrineConfigDir).updateAll().join(),
				// ROADMAP GATE83: derselbe PackUpdater, aber fuer genau ein Paket
				// (Ueberlaufmenue "Aktualisieren"). Das join() laeuft wie oben nur
				// auf dem eigenen Daemon-Thread von LibraryScreen#onUpdateOne.
				manifestId -> PackUpdater.forConfigDir(vitrineConfigDir).update(manifestId).join());
	}

	/** Der Ordner, in dem die {@code .vpkg}-Dateien installierter Pakete liegen (Ueberlaufmenue "Ordner öffnen"). */
	public Path packsDir() {
		return packProvider.packsDir();
	}

	// --- Lesen -------------------------------------------------------------

	/**
	 * Die Bibliotheksliste, frisch aus {@code state.json} und einem rein
	 * lesenden {@code .vpkg}-Scan gebaut. Reihenfolge = {@link PackState#packs}.
	 * Ein in {@code state.json} gefuehrtes Paket, dessen Manifest unlesbar ist
	 * (z. B. Sicherheitspruefung scheitert, unbekannte Kategorie), wird
	 * uebersprungen (mit Warnung) -- der Rest der Liste erscheint trotzdem.
	 *
	 * <p>ROADMAP GATE115: fehlt die {@code .vpkg}-Datei komplett (z. B. ein
	 * zwischen den {@code PackUpdater}-Schritten abgebrochener Update-Lauf,
	 * dessen Backup beim Start nicht wiederherstellbar war, siehe {@code
	 * OrphanedUpdateBackupRecovery}), wird der Eintrag -- anders als bis
	 * GATE115 -- NICHT mehr still uebersprungen, sondern als {@link
	 * LibraryEntry#missing(String, String, int)} sichtbar in die Liste
	 * aufgenommen.</p>
	 */
	public List<LibraryEntry> entries() {
		PackState state = PackStateStore.load(vitrineConfigDir);
		List<LibraryEntry> result = new ArrayList<>(state.packs.size());
		for (PackState.PackEntry pack : state.packs) {
			String packId = withPrefix(pack.packId);
			Optional<Path> file = packProvider.findPackFile(packId);
			if (file.isEmpty()) {
				LOGGER.warn("Bibliothek: fuer '{}' aus state.json wurde keine .vpkg-Datei gefunden -- wird als "
						+ "kaputt/fehlend angezeigt (ROADMAP GATE115), nicht mehr stillschweigend uebersprungen.",
						packId);
				result.add(LibraryEntry.missing(packId, stripPrefix(packId), result.size()));
				continue;
			}
			LibraryEntry entry = readEntry(file.get(), packId, pack.active, result.size());
			if (entry != null) {
				result.add(entry);
			}
		}
		return List.copyOf(result);
	}

	/** Nur die Pakete, die im Abschnitt "Reihenfolge" sortierbar sind (bringen Ressourcen mit). */
	public List<LibraryEntry> orderableEntries() {
		return entries().stream().filter(LibraryEntry::isResourcePack).toList();
	}

	public boolean isEmpty() {
		return PackStateStore.load(vitrineConfigDir).packs.isEmpty();
	}

	/**
	 * Anzahl verfuegbarer Updates (ROADMAP K5): SemVer-Abgleich der installierten
	 * Version je Paket gegen {@code IndexPackage.latestVersion()} aus dem
	 * {@link CatalogVersions}-Seam. Ohne bekannten Katalog ({@link
	 * CatalogVersions#NONE}) ist das Ergebnis {@code 0} -- nichts vorgegaukelt
	 * (Charter-Regel 2).
	 */
	public int updatesAvailable() {
		return (int) updateStatuses().stream().filter(UpdateStatus::updateAvailable).count();
	}

	/**
	 * Der Update-Status je installierter Bibliothekszeile (ROADMAP K5), in der
	 * Reihenfolge von {@link #entries()}. {@link UpdateStatus#updateAvailable()}
	 * ist der Marker fuer die Zeile.
	 */
	public List<UpdateStatus> updateStatuses() {
		List<UpdateStatus> result = new ArrayList<>();
		for (LibraryEntry entry : entries()) {
			String latest = catalogVersions.latestVersionFor(entry.manifestId()).orElse(null);
			result.add(UpdateStatus.evaluate(entry.packId(), entry.version(), latest));
		}
		return List.copyOf(result);
	}

	/** {@code true}, wenn fuer die Zeile {@code packIdOrManifestId} ein Update verfuegbar ist (Zeilen-Marker). */
	public boolean hasUpdate(String packIdOrManifestId) {
		String profileId = withPrefix(packIdOrManifestId);
		return updateStatuses().stream()
				.anyMatch(status -> status.packId().equals(profileId) && status.updateAvailable());
	}

	/**
	 * Paket-IDs, fuer die zuletzt ein Update fehlgeschlagen ist und die alte
	 * Version weiterlaeuft (ROADMAP K5, {@link
	 * de.spassglas.vitrine.pack.PackUpdater}). Die Bibliothekszeile zeigt dafuer
	 * den Klartext-Marker {@code vitrine.library.update_failed}.
	 */
	public List<String> failedUpdatePackIds() {
		return PackStateStore.load(vitrineConfigDir).packIdsWithFailedUpdate();
	}

	/**
	 * Erkannte Ressourcen-Konflikte: teilen sich zwei oder mehr <em>aktive</em>
	 * Pakete einen {@code assets/...}-Pfad, entsteht ein {@link LibraryConflict}
	 * mit den Beteiligten in Prioritaetsreihenfolge (Gewinner zuerst).
	 */
	public List<LibraryConflict> conflicts() {
		List<LibraryEntry> active = entries().stream().filter(LibraryEntry::active).toList();
		Map<String, List<LibraryEntry>> byPath = new LinkedHashMap<>();
		for (LibraryEntry entry : active) {
			for (String path : entry.resourcePaths()) {
				byPath.computeIfAbsent(path, ignored -> new ArrayList<>()).add(entry);
			}
		}
		List<LibraryConflict> conflicts = new ArrayList<>();
		for (Map.Entry<String, List<LibraryEntry>> e : byPath.entrySet()) {
			if (e.getValue().size() >= 2) {
				conflicts.add(new LibraryConflict(e.getKey(), e.getValue()));
			}
		}
		return List.copyOf(conflicts);
	}

	// --- Reihenfolge aendern --------------------------------------------

	/** Schiebt {@code packId} eine Position nach oben (gewinnt eher bei gleicher Ressource). */
	public boolean moveUp(String packId) {
		return shift(packId, -1);
	}

	/** Schiebt {@code packId} eine Position nach unten. */
	public boolean moveDown(String packId) {
		return shift(packId, +1);
	}

	private boolean shift(String packId, int delta) {
		return move(packId, current -> current + delta);
	}

	/**
	 * Verschiebt {@code packId} an die 0-basierte Zielposition {@code targetIndex}
	 * (geklemmt auf die Liste). Schreibt {@code state.json} um und loest einen
	 * gesammelten Reload ueber alle aktiven IDs aus.
	 *
	 * @return {@code true}, wenn sich die Reihenfolge tatsaechlich geaendert hat
	 */
	public boolean moveTo(String packId, int targetIndex) {
		return move(packId, current -> targetIndex);
	}

	/**
	 * Verschiebt {@code packId} an die von {@code target} berechnete Position.
	 * GATE41: Lesen der aktuellen Position, Verschieben und Speichern laufen
	 * in einem Zug unter der Ordner-Sperre -- vorher konnte ein paralleles
	 * Installieren zwischen Laden und Speichern landen und ging verloren.
	 */
	private boolean move(String packId, java.util.function.IntUnaryOperator target) {
		record Outcome(boolean found, boolean moved, String movedId, int from, int to, List<String> activeIds) {
		}
		Outcome outcome;
		try {
			outcome = PackStateStore.update(vitrineConfigDir, state -> {
				int current = indexOf(state, packId);
				if (current < 0) {
					return new Outcome(false, false, packId, -1, -1, List.of());
				}
				int to = Math.max(0, Math.min(target.applyAsInt(current), state.packs.size() - 1));
				if (to == current) {
					return new Outcome(true, false, packId, current, to, List.of());
				}
				PackState.PackEntry moved = state.packs.remove(current);
				state.packs.add(to, moved);
				return new Outcome(true, true, moved.packId, current, to, state.activePackIdsInOrder());
			});
		} catch (IOException e) {
			throw new IllegalStateException("state.json liess sich nach der Reihenfolge-Aenderung nicht schreiben: "
					+ e.getMessage(), e);
		}
		if (!outcome.found()) {
			LOGGER.warn("Bibliothek: '{}' steht nicht in state.json, Reihenfolge unveraendert.", packId);
			return false;
		}
		if (!outcome.moved()) {
			return false;
		}
		// Kein Aktivierungswechsel -- nur die Reihenfolge. Der gesammelte Reload
		// laesst den naechsten Provider-Scan die Profile neu einsortieren.
		reloadRequester.requestReload(outcome.activeIds());
		LOGGER.info("Bibliothek: '{}' von Position {} nach {} verschoben, Reload angefordert.",
				outcome.movedId(), outcome.from(), outcome.to());
		return true;
	}

	// --- Aktiv-Schalter / Alle aktualisieren / Deinstallieren -----------

	/**
	 * Aktiv-Schalter je Zeile (docs/UI-SPEC.md "6. Bibliothek") -- ueber den
	 * bestehenden {@code PackInstaller.setActive}-Weg. Wirft {@link
	 * PackInstallException} unveraendert durch (ROADMAP GATE49): der Aufrufer
	 * ({@code LibraryScreen}) zeigt dann einen sichtbaren Klartextfehler, statt
	 * den Schalter stumm zurueckspringen zu lassen.
	 */
	public void setActive(String packId, boolean active) throws PackInstallException {
		activeSetter.setActive(withPrefix(packId), active);
	}

	/**
	 * "Alle aktualisieren" (docs/UI-SPEC.md "6. Bibliothek", ROADMAP GATE13):
	 * ruft zuerst den echten Versionsabgleich gegen den Katalog auf
	 * ({@link UpdateRunner#updateAll()} -- Produktion: {@link
	 * de.spassglas.vitrine.pack.PackUpdater#updateAll()}, das Download der
	 * neuen Version, Pruef-Kette und Rueckfall auf die alte Version bei
	 * Fehlschlag uebernimmt, ROADMAP K5), und loest danach genau <em>einen</em>
	 * gesammelten Reload ueber alle aktiven Paket-IDs aus, damit frisch in
	 * {@code packs/} liegende Dateien sofort greifen.
	 *
	 * <p>Diese Methode selbst bleibt blockierend (client-frei, wie der Rest
	 * dieses Modells) -- ein echter Update-Lauf tut Netz-I/O, das darf laut
	 * ROADMAP GATE9 nicht auf dem Render-Thread laufen. Den Hintergrund-Thread
	 * dafuer stellt der Aufrufer ({@code LibraryScreen}), wie schon
	 * {@code StoreScreen} es fuer den Katalog-Refresh tut.</p>
	 *
	 * @return die Ergebnisse je Paket (ROADMAP K5-Ausgang), leer ohne
	 *         {@link UpdateRunner} (Vor-K5-Anschluss-Zustand)
	 */
	public List<PackUpdater.UpdateResult> updateAll() {
		List<PackUpdater.UpdateResult> results = updateRunner.updateAll();
		List<String> activeIds = PackStateStore.load(vitrineConfigDir).activePackIdsInOrder();
		reloadRequester.requestReload(activeIds);
		LOGGER.info("Bibliothek: 'Alle aktualisieren' -- {} Ergebnis(se), gesammelter Reload ueber {} aktive(s) "
				+ "Paket(e).", results.size(), activeIds.size());
		return results;
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Überlaufmenü-Eintrag
	 * "Aktualisieren" je Zeile) -- derselbe echte Versionsabgleich wie
	 * {@link #updateAll()} (ROADMAP K5, {@link SingleUpdateRunner}), aber für
	 * genau ein Paket. Löst danach denselben gesammelten Reload über alle
	 * aktiven Paket-IDs aus wie {@link #updateAll()} -- kein Vorgaukeln
	 * (Charter-Regel 2), auch wenn der Reload fuer dieses eine Paket faellig ist.
	 *
	 * @param packIdOrManifestId Profil- oder Roh-ID der betroffenen Zeile
	 * @return das Ergebnis dieses einen Updates, oder {@code null} ohne
	 *         {@link SingleUpdateRunner} (Vor-GATE83-Anschluss-Zustand)
	 */
	public PackUpdater.UpdateResult updateOne(String packIdOrManifestId) {
		String manifestId = stripPrefix(packIdOrManifestId);
		PackUpdater.UpdateResult result = singleUpdateRunner.updateOne(manifestId);
		List<String> activeIds = PackStateStore.load(vitrineConfigDir).activePackIdsInOrder();
		reloadRequester.requestReload(activeIds);
		LOGGER.info("Bibliothek: Aktualisieren von '{}' -- Ergebnis {}, gesammelter Reload ueber {} aktive(s) "
				+ "Paket(e).", manifestId, result == null ? "keins" : result.outcome(), activeIds.size());
		return result;
	}

	/** Ueberlaufmenue "Deinstallieren" (docs/UI-SPEC.md "6. Bibliothek"). */
	public void uninstall(String packId) {
		packRemover.remove(withPrefix(packId));
	}

	// --- intern ----------------------------------------------------------

	private LibraryEntry readEntry(Path vpkgFile, String packId, boolean active, int position) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			Optional<TilePackageType> type = TilePackageType.byId(manifest.category());
			if (type.isEmpty()) {
				LOGGER.warn("Bibliothek: '{}' nennt eine unbekannte Kategorie '{}', wird uebersprungen.",
						packId, manifest.category());
				return null;
			}
			List<String> resourcePaths = archive.entries().stream()
					.filter(e -> !e.directory())
					.map(VpkgEntry::name)
					.filter(name -> name.startsWith(ASSETS_PREFIX))
					.sorted()
					.toList();
			return new LibraryEntry(packId, manifest.id(), manifest.name(), manifest.author(), manifest.version(),
					type.get(), active, position, resourcePaths, vpkgFile, manifest.icon(), fileSize(vpkgFile));
		} catch (IOException e) {
			LOGGER.warn("Bibliothek: '{}' liess sich nicht als .vpkg oeffnen: {}", vpkgFile, e.toString());
			return null;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Bibliothek: '{}' hat die Sicherheitspruefung nicht bestanden: {}", vpkgFile, e.getMessage());
			return null;
		} catch (ManifestParseException e) {
			LOGGER.warn("Bibliothek: '{}' hat ein ungueltiges Manifest: {}", vpkgFile, e.getMessage());
			return null;
		} catch (RuntimeException e) {
			LOGGER.warn("Bibliothek: '{}' liess sich unerwartet nicht auswerten: {}", vpkgFile, e.toString(), e);
			return null;
		}
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Zeile zeigt die Größe):
	 * Dateigröße der {@code .vpkg} in Bytes, oder {@code -1}, wenn sie nicht
	 * lesbar ist (gleiches Fallback-Muster wie {@code LocalPackageDetailSource#fileSize}).
	 */
	private static long fileSize(Path vpkgFile) {
		try {
			return java.nio.file.Files.size(vpkgFile);
		} catch (IOException e) {
			LOGGER.warn("Bibliothek: Groesse von '{}' nicht lesbar: {}", vpkgFile, e.toString());
			return -1L;
		}
	}

	private static int indexOf(PackState state, String packId) {
		String want = withPrefix(packId);
		for (int i = 0; i < state.packs.size(); i++) {
			if (withPrefix(state.packs.get(i).packId).equals(want)) {
				return i;
			}
		}
		return -1;
	}

	private static String withPrefix(String packId) {
		Objects.requireNonNull(packId, "packId");
		return packId.startsWith(PACK_ID_PREFIX) ? packId : PACK_ID_PREFIX + packId;
	}

	private static String stripPrefix(String packId) {
		Objects.requireNonNull(packId, "packId");
		return packId.startsWith(PACK_ID_PREFIX) ? packId.substring(PACK_ID_PREFIX.length()) : packId;
	}
}
