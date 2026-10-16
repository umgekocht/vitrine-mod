package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.catalog.SemanticVersion;
import de.spassglas.vitrine.catalog.download.CancellationToken;
import de.spassglas.vitrine.catalog.download.DownloadException;
import de.spassglas.vitrine.catalog.download.DownloadOutcome;
import de.spassglas.vitrine.catalog.download.DownloadRequest;
import de.spassglas.vitrine.catalog.download.PackageDownloader;
import de.spassglas.vitrine.hotapply.MinecraftPackActivation;
import de.spassglas.vitrine.hotapply.MinecraftResourceReloadTrigger;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * ROADMAP K5 ("Updates: Versionsvergleich (SemVer), Marker, „alles
 * aktualisieren", Rückfall auf die alte Version, wenn die neue nicht lädt") --
 * der client-freie Orchestrator dahinter.
 *
 * <p>Pro Paket streng transaktional:</p>
 * <ol>
 *   <li>Zielversion über {@link PackageVersionSource} bestimmen. Kein
 *       Katalog-Eintrag, nicht parsebare Version oder {@code latest} nicht echt
 *       neuer als installiert ⇒ übersprungen, nichts angefasst.</li>
 *   <li>Die neue {@code .vpkg} über den {@link VpkgFetcher} an einen
 *       <em>temporären</em> Ort laden -- <b>nie</b> direkt über die installierte
 *       Datei. Prüfsumme + Signatur macht der Fetcher (Produktion: {@code
 *       PackageDownloader}) bereits.</li>
 *   <li>Die neue Version über {@link PackInstaller#verifyDownloadedPackage}
 *       (Zip-Sicherheit → Prüfsumme/Signatur → Fähigkeiten-Zustimmung) prüfen.
 *       Diese Kette ist der einzige Weg, auf dem eine Version live geht.</li>
 *   <li>Die bisherige {@code packs/}-Datei zur Seite legen, die neue an ihre
 *       Stelle setzen, Reload anstoßen und dessen Ausgang <em>abwarten</em>.</li>
 *   <li><b>Rückfall:</b> scheitert Schritt 2, 3 oder der Reload aus 4 ⇒ die
 *       neue Datei wird entfernt, die alte wiederhergestellt und (falls sie es
 *       war) wieder aktiviert, ein bereinigender Reload angestoßen, und das
 *       Paket in {@code state.json} als „Update fehlgeschlagen, alte Version
 *       läuft weiter" markiert ({@link PackState#setUpdateFailed}). Kein halber
 *       Zustand in {@code packs/}.</li>
 * </ol>
 *
 * <p>{@link #updateAll()} führt Schritt 1–5 für jedes installierte Paket aus;
 * jedes Paket lädt seinen eigenen {@link PackState} frisch, ein gescheitertes
 * lässt die anderen (auch schon aktualisierte) unberührt.</p>
 *
 * <p>Kennt keine Minecraft-Typen direkt -- {@link PackActivation} und {@link
 * ResourceReloadTrigger} sind dieselben Schnittstellen, über die schon {@code
 * PackUninstaller} den H3-Rollback-Pfad fährt (Deaktivieren → Reload abwarten),
 * hier für den umgekehrten Fall: die alte Version wiederherstellen und wieder
 * aktivieren.</p>
 */
public final class PackUpdater {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Updates");

	private static final String PACK_ID_PREFIX = "vitrine/";
	private static final String STAGING_SUBDIR = "updates";
	private static final String BACKUP_SUBDIR = "previous";
	private static final String TRASH_SUBDIR = "trash";

	/** Wie ein Update-Versuch je Paket ausgegangen ist. */
	public enum Outcome {
		/** Die neue Version ist eingespielt und (falls die alte es war) aktiv. */
		UPDATED,
		/** Katalog-Version ist nicht echt neuer als die installierte -- nichts zu tun. */
		ALREADY_UP_TO_DATE,
		/** Der {@link PackageVersionSource} kennt dieses Paket nicht. */
		NO_CATALOG_ENTRY,
		/** Das Paket steht nicht (mehr) als installiert im {@code state.json}. */
		NOT_INSTALLED,
		/** Installierte oder Katalog-Version ist kein gültiges SemVer -- „kein Update" (geloggt). */
		VERSION_UNPARSABLE,
		/** Download/Prüfung/Reload gescheitert -- die alte Version läuft weiter, Paket ist markiert. */
		FAILED_ROLLED_BACK
	}

	/** Ergebnis eines Update-Versuchs für genau ein Paket. */
	public record UpdateResult(String manifestId, Outcome outcome, String installedVersion, String targetVersion) {

		public UpdateResult {
			Objects.requireNonNull(manifestId, "manifestId");
			Objects.requireNonNull(outcome, "outcome");
		}

		/** {@code true}, wenn das Update fehlschlug und die alte Version wiederhergestellt/behalten wurde. */
		public boolean rolledBack() {
			return outcome == Outcome.FAILED_ROLLED_BACK;
		}

		/** {@code true}, wenn dieses Paket tatsächlich auf eine neue Version gehoben wurde. */
		public boolean updated() {
			return outcome == Outcome.UPDATED;
		}
	}

	/** Die vier Download-Angaben aus {@code registry-package.schema.json} {@code versions[]} für eine Zielversion. */
	public record PackageVersion(String version, URI vpkgUrl, String sha256Hex, String signatureBase64, long size) {

		public PackageVersion {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(vpkgUrl, "vpkgUrl");
			Objects.requireNonNull(sha256Hex, "sha256Hex");
			Objects.requireNonNull(signatureBase64, "signatureBase64");
		}
	}

	/**
	 * Seam: liefert zu einer Manifest-ID die neueste im Katalog geführte
	 * Version samt Download-Koordinaten. Produktion holt {@code
	 * packages/&lt;id&gt;.json} und validiert es gegen eine eingebettete Kopie
	 * von {@code registry-package.schema.json}; im Test ein Fake mit zwei
	 * Versionen desselben Pakets.
	 */
	@FunctionalInterface
	public interface PackageVersionSource {
		/** Die Zielversion für {@code manifestId}, oder leer, wenn der Katalog das Paket nicht (mehr) führt. */
		Optional<PackageVersion> latestFor(String manifestId);
	}

	/**
	 * Seam: lädt die {@code .vpkg} zu {@code request} nach {@code targetVpkg}
	 * und legt eine passende {@link VpkgIntegritySidecar}-Begleitdatei ({@code
	 * .integrity.json}) daneben. Nach normalem Rücklauf sind beide vollständig
	 * geschrieben und Prüfsumme + Signatur bereits verifiziert. Bei jedem
	 * Netz-, Protokoll- oder Verifikationsfehler {@link DownloadException} --
	 * ohne etwas Anwendbares zu hinterlassen. Produktion wickelt {@code
	 * PackageDownloader.download(...)} ab.
	 */
	@FunctionalInterface
	public interface VpkgFetcher {
		void fetch(DownloadRequest request, Path targetVpkg) throws DownloadException;
	}

	private final Path vitrineConfigDir;
	private final VitrinePackProvider provider;
	private final PackageVersionSource versionSource;
	private final VpkgFetcher fetcher;
	private final PackActivation packActivation;
	private final ResourceReloadTrigger reloadTrigger;

	public PackUpdater(Path vitrineConfigDir, VitrinePackProvider provider, PackageVersionSource versionSource,
			VpkgFetcher fetcher, PackActivation packActivation, ResourceReloadTrigger reloadTrigger) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.versionSource = Objects.requireNonNull(versionSource, "versionSource");
		this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
		this.packActivation = Objects.requireNonNull(packActivation, "packActivation");
		this.reloadTrigger = Objects.requireNonNull(reloadTrigger, "reloadTrigger");
	}

	/**
	 * Produktions-Verdrahtung mit den echten Client-Diensten.
	 *
	 * <p>ROADMAP G3 hat den Abruf von {@code packages/&lt;id&gt;.json} gebaut
	 * ({@link de.spassglas.vitrine.catalog.RegistryPackageClient}, Pruefung
	 * gegen eine eingebettete Schema-Kopie) -- {@link RegistryPackageVersionSource}
	 * nutzt ihn jetzt fuer den SemVer-Vergleich; {@link #productionFetcher()}
	 * laedt eine neue Version ueber {@code PackageDownloader} und prueft dabei
	 * Pruefsumme + Ed25519-Signatur gegen den im Jar eingebetteten Registry-
	 * Schluessel ({@code EmbeddedSigningKey}).</p>
	 */
	public static PackUpdater forConfigDir(Path vitrineConfigDir) {
		return new PackUpdater(vitrineConfigDir,
				VitrinePackProvider.forConfigDir(vitrineConfigDir),
				RegistryPackageVersionSource.forConfigDir(vitrineConfigDir),
				productionFetcher(),
				new MinecraftPackActivation(),
				new MinecraftResourceReloadTrigger());
	}

	/**
	 * ROADMAP GATE115: der Ordner, in den Schritt 4 (siehe Klassendoku) die alte
	 * Version zur Seite legt, <em>bevor</em> die neue an ihre Stelle in {@code
	 * packs/} tritt -- ein zwischen diesen beiden Schritten abgebrochener Lauf
	 * (z. B. Spielabbruch) lässt genau hier eine noch vollständig geprüfte
	 * Kopie der alten Version liegen. {@link
	 * de.spassglas.vitrine.pack.OrphanedUpdateBackupRecovery} sucht beim Start
	 * genau in diesem Ordner, um das Paket zurückzustellen, statt es
	 * stillschweigend aus Bibliothek/Diagnose verschwinden zu lassen.
	 */
	public static Path backupDirFor(Path vitrineConfigDir) {
		return vitrineConfigDir.resolve(STAGING_SUBDIR).resolve(BACKUP_SUBDIR);
	}

	/**
	 * Produktions-{@link VpkgFetcher}: laedt ueber {@code PackageDownloader}
	 * (Pruefsumme + Signatur gegen {@code EmbeddedSigningKey.load()}, NICHT
	 * gegen einen lokalen Sidecar) und legt danach die {@link
	 * VpkgIntegritySidecar}-Begleitdatei mit genau diesen bereits geprueften
	 * Werten neben die heruntergeladene Datei -- ohne die wuerde {@link
	 * VitrinePackProvider} sie beim naechsten Scan als "nicht geprueft" ablehnen.
	 */
	static VpkgFetcher productionFetcher() {
		PublicKey key = EmbeddedSigningKey.load();
		PackageDownloader downloader = PackageDownloader.production(key);
		return (request, targetVpkg) -> {
			DownloadOutcome outcome = downloader.download(request, targetVpkg, new CancellationToken(), null);
			if (!outcome.isCompleted()) {
				throw new DownloadException(DownloadException.Kind.BROKEN,
						"Update-Download wurde unerwartet abgebrochen.");
			}
			try {
				VpkgIntegritySidecar.write(targetVpkg, request.sha256Hex(), request.signatureBytes(), key);
			} catch (IOException e) {
				throw new DownloadException(DownloadException.Kind.BROKEN,
						"Integritaets-Beleg fuer '" + targetVpkg + "' liess sich nicht schreiben: " + e.getMessage(), e);
			}
		};
	}

	// --- öffentliche API ------------------------------------------------

	/**
	 * „Alles aktualisieren" (ROADMAP K5 / docs/UI-SPEC.md „6. Bibliothek"):
	 * jedes installierte Paket der Reihe nach über {@link #update(String)}.
	 * Transaktional pro Paket -- ein gescheitertes lässt die übrigen und die
	 * bereits aktualisierten unberührt.
	 */
	public CompletableFuture<List<UpdateResult>> updateAll() {
		List<String> installed = PackStateStore.load(vitrineConfigDir).installedPackIdsInOrder();
		List<UpdateResult> results = new ArrayList<>(installed.size());
		LOGGER.info("„Alle aktualisieren“: prüfe {} installierte(s) Paket(e) gegen den Katalog.", installed.size());

		// GATE38: die Pakete laufen weiterhin streng nacheinander -- aber als
		// Kette von Fortsetzungen statt als Schleife mit join(). Jedes Glied
		// beginnt erst, wenn der Reload des vorherigen abgeschlossen ist.
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (String profileId : installed) {
			String manifestId = stripPrefix(profileId);
			chain = chain.thenCompose(ignored -> startUpdate(manifestId).handle((result, throwable) -> {
				if (throwable != null) {
					LOGGER.error("Update von '{}' brach unerwartet ab: {} -- die übrigen Pakete sind nicht betroffen.",
							manifestId, describe(throwable), throwable);
					markUpdateFailed(profileId);
					results.add(new UpdateResult(manifestId, Outcome.FAILED_ROLLED_BACK, null, null));
				} else {
					results.add(result);
				}
				return null;
			}));
		}
		return chain.thenApply(ignored -> List.copyOf(results));
	}

	/** {@link #update(String)}, aber eine bereits beim Anstossen geworfene Exception wird zur fehlgeschlagenen Future. */
	private CompletableFuture<UpdateResult> startUpdate(String manifestId) {
		try {
			return update(manifestId);
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Aktualisiert genau ein Paket. Siehe Klassendoku für den Ablauf und den
	 * Rückfall. Wirft nie wegen eines Update-Fehlers -- ein Fehlschlag kommt als
	 * {@link Outcome#FAILED_ROLLED_BACK} zurück, die alte Version läuft weiter.
	 */
	public CompletableFuture<UpdateResult> update(String manifestId) {
		Objects.requireNonNull(manifestId, "manifestId");
		String profileId = PACK_ID_PREFIX + manifestId;

		PackState state = PackStateStore.load(vitrineConfigDir);
		if (!state.isInstalled(profileId)) {
			LOGGER.info("Update übersprungen: '{}' steht nicht als installiert im state.json.", manifestId);
			return CompletableFuture.completedFuture(new UpdateResult(manifestId, Outcome.NOT_INSTALLED, null, null));
		}

		Optional<Path> currentFile = provider.findPackFile(profileId);
		if (currentFile.isEmpty()) {
			LOGGER.warn("Update übersprungen: für '{}' liegt keine .vpkg-Datei in packs/ -- nichts zu aktualisieren.",
					manifestId);
			return CompletableFuture.completedFuture(new UpdateResult(manifestId, Outcome.NOT_INSTALLED, null, null));
		}
		String installedVersion = readInstalledVersion(currentFile.get());

		Optional<PackageVersion> target = versionSource.latestFor(manifestId);
		if (target.isEmpty()) {
			LOGGER.info("Update übersprungen: der Katalog führt '{}' nicht.", manifestId);
			return CompletableFuture.completedFuture(
					new UpdateResult(manifestId, Outcome.NO_CATALOG_ENTRY, installedVersion, null));
		}
		String targetVersion = target.get().version();

		Optional<SemanticVersion> installedSem = SemanticVersion.tryParse(installedVersion);
		Optional<SemanticVersion> targetSem = SemanticVersion.tryParse(targetVersion);
		if (installedSem.isEmpty() || targetSem.isEmpty()) {
			LOGGER.info("Update übersprungen: Version von '{}' nicht als SemVer parsebar (installiert='{}', Ziel='{}').",
					manifestId, installedVersion, targetVersion);
			return CompletableFuture.completedFuture(
					new UpdateResult(manifestId, Outcome.VERSION_UNPARSABLE, installedVersion, targetVersion));
		}
		if (!targetSem.get().isNewerThan(installedSem.get())) {
			LOGGER.info("'{}' ist aktuell (installiert {}, Katalog {}).", manifestId, installedVersion, targetVersion);
			return CompletableFuture.completedFuture(
					new UpdateResult(manifestId, Outcome.ALREADY_UP_TO_DATE, installedVersion, targetVersion));
		}

		LOGGER.info("Update von '{}' {} → {} wird versucht.", manifestId, installedVersion, targetVersion);
		return applyUpdate(manifestId, profileId, currentFile.get(), installedVersion, target.get(),
				state.isActive(profileId));
	}

	// --- Kern: neue Version einspielen, alte Version schützen -----------

	private CompletableFuture<UpdateResult> applyUpdate(String manifestId, String profileId, Path oldVpkg, String installedVersion,
			PackageVersion target, boolean wasActive) {
		String targetVersion = target.version();

		Path stagingDir = vitrineConfigDir.resolve(STAGING_SUBDIR);
		Path stagedVpkg = stagingDir.resolve(safeFileName(manifestId + "@" + targetVersion) + ".vpkg");
		try {
			Files.createDirectories(stagingDir);
		} catch (IOException e) {
			LOGGER.error("Update von '{}': Arbeitsordner '{}' ließ sich nicht anlegen: {}", manifestId, stagingDir,
					e.toString(), e);
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}
		cleanupStaged(stagedVpkg);

		// --- 1. Download-Anfrage bauen ---------------------------------
		DownloadRequest request;
		try {
			long size = target.size() > 0 ? target.size() : DownloadRequest.SIZE_UNKNOWN;
			request = DownloadRequest.of(target.vpkgUrl(), target.sha256Hex(), target.signatureBase64(), size);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Update von '{}' abgebrochen: ungültige Download-Koordinaten der Zielversion: {}", manifestId,
					e.getMessage());
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}

		// --- 2. In den temporären Ort laden (nie über die installierte Datei) ---
		try {
			fetcher.fetch(request, stagedVpkg);
		} catch (DownloadException e) {
			LOGGER.warn("Update von '{}' auf {} fehlgeschlagen ({}: {}) -- alte Version {} läuft weiter.",
					manifestId, targetVersion, e.kind(), e.getMessage(), installedVersion);
			cleanupStaged(stagedVpkg);
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}

		// --- 3. Prüf-Kette: der einzige Anwendungsweg -----------------
		Manifest newManifest;
		try {
			newManifest = PackInstaller.verifyDownloadedPackage(vitrineConfigDir, stagedVpkg, manifestId);
		} catch (PackInstallException e) {
			LOGGER.warn("Update von '{}' auf {} fehlgeschlagen: {} -- alte Version {} läuft weiter.",
					manifestId, targetVersion, e.getMessage(), installedVersion);
			cleanupStaged(stagedVpkg);
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}
		if (!targetVersion.equals(newManifest.version())) {
			LOGGER.warn("Update von '{}' abgebrochen: heruntergeladene Datei meldet Version '{}', der Katalog "
					+ "kündigte '{}' an.", manifestId, newManifest.version(), targetVersion);
			cleanupStaged(stagedVpkg);
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}

		// --- 4. Datei-Tausch, alte Version zur Seite legen -----------
		Path backupDir = stagingDir.resolve(BACKUP_SUBDIR);
		Path backupVpkg = backupDir.resolve(oldVpkg.getFileName().toString());
		Path oldSidecar = VpkgIntegritySidecar.sidecarPathFor(oldVpkg);
		Path backupSidecar = VpkgIntegritySidecar.sidecarPathFor(backupVpkg);
		try {
			Files.createDirectories(backupDir);
			deleteQuietly(backupVpkg);
			deleteQuietly(backupSidecar);
			moveInto(oldVpkg, backupVpkg);
			if (Files.exists(oldSidecar)) {
				moveInto(oldSidecar, backupSidecar);
			}
		} catch (IOException e) {
			LOGGER.error("Update von '{}': alte Version ließ sich nicht zur Seite legen: {} -- Abbruch, alte Version "
					+ "bleibt unangetastet.", manifestId, e.toString(), e);
			restoreQuietly(backupVpkg, oldVpkg);
			restoreQuietly(backupSidecar, oldSidecar);
			cleanupStaged(stagedVpkg);
			return CompletableFuture.completedFuture(
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}

		Path packsDir = provider.packsDir();
		Path newInPacks = uniqueTarget(packsDir, oldVpkg.getFileName().toString());
		Path newInPacksSidecar = VpkgIntegritySidecar.sidecarPathFor(newInPacks);
		Path stagedSidecar = VpkgIntegritySidecar.sidecarPathFor(stagedVpkg);
		try {
			moveInto(stagedVpkg, newInPacks);
			if (Files.exists(stagedSidecar)) {
				moveInto(stagedSidecar, newInPacksSidecar);
			}
		} catch (IOException e) {
			LOGGER.error("Update von '{}': neue Version ließ sich nicht nach packs/ setzen: {} -- Rückfall.",
					manifestId, e.toString(), e);
			deleteQuietly(newInPacks);
			deleteQuietly(newInPacksSidecar);
			restoreQuietly(backupVpkg, oldVpkg);
			restoreQuietly(backupSidecar, oldSidecar);
			cleanupStaged(stagedVpkg);
			return reactivateAndReload(profileId, wasActive, manifestId).thenApply(ignored ->
					failAndMark(profileId, manifestId, installedVersion, targetVersion));
		}

		// --- 5. Reload anstoßen, Ausgang ANHÄNGEN (GATE38) -----------
		// Frueher stand hier ein join(): das haette den Client-Thread, auf dem
		// der Reload selbst laeuft, gegen sich selbst blockiert. Jetzt haengt
		// alles Weitere als Fortsetzung an der Reload-Future -- dasselbe
		// Muster wie in PackUninstaller.
		if (wasActive) {
			packActivation.activate(profileId);
		}
		return startReload(manifestId)
				.handle((ignored, throwable) -> throwable)
				.thenCompose(throwable -> {
					if (throwable == null) {
						moveToTrash(backupVpkg);
						moveToTrash(backupSidecar);
						clearUpdateFailedMark(profileId);
						recordCatalogSource(profileId, target.vpkgUrl());
						LOGGER.info("Update von '{}' {} → {} erfolgreich, neue Version {}.", manifestId,
								installedVersion, targetVersion, wasActive ? "aktiv" : "installiert (inaktiv)");
						return CompletableFuture.completedFuture(
								new UpdateResult(manifestId, Outcome.UPDATED, installedVersion, targetVersion));
					}

					// --- Rückfall: alte Version wiederherstellen + aktivieren ----
					LOGGER.warn("Reload nach dem Update von '{}' auf {} fehlgeschlagen ({}) -- Rückfall auf {}.",
							manifestId, targetVersion, describe(throwable), installedVersion);
					deleteQuietly(newInPacks);
					deleteQuietly(newInPacksSidecar);
					try {
						moveInto(backupVpkg, oldVpkg);
						if (Files.exists(backupSidecar)) {
							moveInto(backupSidecar, oldSidecar);
						}
					} catch (IOException e) {
						LOGGER.error("KRITISCH: alte Version von '{}' ließ sich nicht aus '{}' wiederherstellen: {}. "
								+ "Die gesicherte Datei bleibt dort liegen.", manifestId, backupVpkg, e.toString(), e);
					}
					return reactivateAndReload(profileId, wasActive, manifestId).thenApply(ignored ->
							failAndMark(profileId, manifestId, installedVersion, targetVersion));
				});
	}

	// --- Hilfsfunktionen ----------------------------------------------

	/**
	 * Stößt den Reload an, ohne zu warten (GATE38). Wirft {@link
	 * ResourceReloadTrigger#reloadResources()} schon beim Anstoßen, wird daraus
	 * eine fehlgeschlagene Future -- der Aufrufer hat genau einen Fehlerweg.
	 */
	private CompletableFuture<Void> startReload(String manifestId) {
		try {
			return reloadTrigger.reloadResources();
		} catch (RuntimeException e) {
			LOGGER.warn("Reload für das Update von '{}' ließ sich nicht einmal anstoßen: {}", manifestId,
					describe(e));
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Wiederherstellungs-Reload nach einem Rückfall. Schließt immer erfolgreich
	 * ab -- ein auch hier gescheiterter Reload wird nur geloggt, das Spiel
	 * bleibt im aktuellen Zustand nutzbar. Es wird nicht gewartet (GATE38).
	 */
	private CompletableFuture<Void> reactivateAndReload(String profileId, boolean wasActive, String manifestId) {
		if (wasActive) {
			packActivation.activate(profileId);
		}
		return startReload(manifestId).handle((ignored, throwable) -> {
			if (throwable != null) {
				LOGGER.error("Wiederherstellungs-Reload für '{}' schlug ebenfalls fehl: {} -- das Spiel bleibt im "
						+ "aktuellen Zustand nutzbar.", manifestId, describe(throwable), throwable);
			}
			return null;
		});
	}

	private UpdateResult failAndMark(String profileId, String manifestId, String installedVersion,
			String targetVersion) {
		markUpdateFailed(profileId);
		return new UpdateResult(manifestId, Outcome.FAILED_ROLLED_BACK, installedVersion, targetVersion);
	}

	private void markUpdateFailed(String profileId) {
		updateQuietly(fresh -> fresh.setUpdateFailed(profileId, true));
	}

	/**
	 * ROADMAP X3 (Diagnose-Bildschirm, "welche Datei kam von wo"): haelt den
	 * Host fest, von dem die soeben eingespielte Version tatsaechlich
	 * heruntergeladen wurde ({@link PackState#SOURCE_CATALOG_PREFIX} + Host).
	 * Ein {@code vpkgUrl} ohne ermittelbaren Host laesst die Quelle unangetastet
	 * (kein erfundener Wert) -- der bisherige Aktiv-Zustand bleibt unveraendert.
	 */
	private void recordCatalogSource(String profileId, URI vpkgUrl) {
		String host = vpkgUrl.getHost();
		if (host == null || host.isBlank()) {
			return;
		}
		updateQuietly(fresh -> fresh.addOrUpdate(profileId, fresh.isActive(profileId),
				PackState.SOURCE_CATALOG_PREFIX + host));
	}

	private void clearUpdateFailedMark(String profileId) {
		if (!PackStateStore.load(vitrineConfigDir).hasFailedUpdate(profileId)) {
			return; // nichts zu tun -- kein unnoetiges Schreiben
		}
		updateQuietly(fresh -> fresh.setUpdateFailed(profileId, false));
	}

	/** GATE41: fortschreiben unter der Ordner-Sperre; ein Schreibfehler wird geloggt, nicht geworfen. */
	private void updateQuietly(java.util.function.Consumer<PackState> mutation) {
		try {
			PackStateStore.update(vitrineConfigDir, state -> {
				mutation.accept(state);
				return null;
			});
		} catch (IOException e) {
			LOGGER.error("state.json ließ sich nach dem Update nicht schreiben: {}", e.toString(), e);
		}
	}

	private String readInstalledVersion(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			return ManifestParser.parse(archive.readManifestBytes()).version();
		} catch (IOException | ManifestParseException | RuntimeException e) {
			LOGGER.warn("Installierte Version aus '{}' nicht lesbar: {} -- gilt als nicht parsebar.", vpkgFile,
					e.toString());
			return null;
		}
	}

	private void cleanupStaged(Path stagedVpkg) {
		deleteQuietly(stagedVpkg);
		deleteQuietly(VpkgIntegritySidecar.sidecarPathFor(stagedVpkg));
	}

	private void moveToTrash(Path file) {
		if (file == null || !Files.exists(file)) {
			return;
		}
		Path trashDir = vitrineConfigDir.resolve(TRASH_SUBDIR);
		try {
			Files.createDirectories(trashDir);
			Path target = uniqueTarget(trashDir, file.getFileName().toString());
			moveInto(file, target);
			LOGGER.info("Vorgängerversion '{}' nach dem Update in den Papierkorb verschoben: '{}'.", file, target);
		} catch (IOException e) {
			LOGGER.warn("Vorgängerversion '{}' ließ sich nicht in den Papierkorb verschieben ({}), lösche sie.",
					file, e.toString());
			deleteQuietly(file);
		}
	}

	/** ROADMAP GATE115: package-private statt privat, {@link OrphanedUpdateBackupRecovery} kehrt dieselbe Bewegung um. */
	static void moveInto(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstützt ({}), gewöhnlicher Move.",
					source, target, e.toString());
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void restoreQuietly(Path backup, Path original) {
		if (backup == null || !Files.exists(backup)) {
			return;
		}
		try {
			moveInto(backup, original);
		} catch (IOException e) {
			LOGGER.error("Konnte '{}' nicht nach '{}' zurückstellen: {}", backup, original, e.toString(), e);
		}
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("Aufräumen von '{}' fehlgeschlagen: {}", path, e.toString());
		}
	}

	/**
	 * Zielpfad in {@code dir} für {@code fileName}, der garantiert noch nicht
	 * existiert -- eine Namenskollision wird nie überschrieben (Charter-Regel
	 * 3), stattdessen ein Millisekunden-Zeitstempel eingefügt (wie {@code
	 * PackInstaller#uniqueTargetPath}).
	 */
	/** ROADMAP GATE115: package-private statt privat, {@link OrphanedUpdateBackupRecovery} braucht denselben Kollisionsschutz. */
	static Path uniqueTarget(Path dir, String fileName) {
		Path candidate = dir.resolve(fileName);
		if (!Files.exists(candidate)) {
			return candidate;
		}
		int dot = fileName.lastIndexOf('.');
		String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
		String extension = dot >= 0 ? fileName.substring(dot) : "";
		Path stamped;
		int attempt = 0;
		do {
			String suffix = Long.toString(Instant.now().toEpochMilli()) + (attempt == 0 ? "" : "-" + attempt);
			stamped = dir.resolve(base + "-" + suffix + extension);
			attempt++;
		} while (Files.exists(stamped));
		return stamped;
	}

	/** Nur Zeichen, die auf jedem Dateisystem als Dateiname taugen -- Manifest-IDs sind bereits {@code [a-z0-9.-]}. */
	private static String safeFileName(String raw) {
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			sb.append((Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '@') ? c : '_');
		}
		return sb.toString();
	}

	private static String stripPrefix(String profileId) {
		return profileId.startsWith(PACK_ID_PREFIX) ? profileId.substring(PACK_ID_PREFIX.length()) : profileId;
	}

	private static String describe(Throwable throwable) {
		Throwable actual = (throwable instanceof CompletionException && throwable.getCause() != null)
				? throwable.getCause()
				: throwable;
		String message = actual.getMessage();
		return (message != null && !message.isBlank()) ? message : actual.getClass().getSimpleName();
	}
}
