package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.catalog.download.CancellationToken;
import de.spassglas.vitrine.catalog.download.DownloadException;
import de.spassglas.vitrine.catalog.download.DownloadOutcome;
import de.spassglas.vitrine.catalog.download.DownloadProgress;
import de.spassglas.vitrine.catalog.download.DownloadRequest;
import de.spassglas.vitrine.catalog.download.PackageDownloader;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.hotapply.HotApplyService;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ROADMAP G3 ("Katalog-Pakete lassen sich in der Store-Oberflaeche nicht
 * installieren"): der Installationsweg fuer ein Angebot, das nur als
 * Katalog-Eintrag bekannt ist (nicht in {@code config/vitrine/library/}) --
 * Schwester von {@link PackInstaller}, die den lokalen Bibliotheksordner
 * voraussetzt.
 *
 * <ol>
 *   <li><b>a.</b> {@link PackageDownloader#download} -- laedt die {@code
 *       .vpkg} an einen temporaeren Ort. Prueft dabei bereits Pruefsumme UND
 *       Ed25519-Signatur gegen den mitgegebenen oeffentlichen Schluessel
 *       ({@link #production} verdrahtet {@link EmbeddedSigningKey#load()} --
 *       den im Jar eingebetteten Registry-Schluessel, keinen Sidecar aus
 *       dem Katalog selbst). Schlaegt das fehl, bleibt {@code packs/}
 *       unangetastet (Charter-Regel 3).</li>
 *   <li><b>b.</b> {@link PackInstaller#readManifest} -- Zip-Sicherheit +
 *       Manifest der heruntergeladenen Datei; die Manifest-Id muss zur
 *       angefragten Id passen.</li>
 *   <li><b>c.</b> {@link PackInstaller#capabilityIdsOf} + Zustimmungs-Pruefung
 *       im {@code state.json} (Charter-Regel 4: "Default ist Verbot"). Die
 *       angemeldeten Faehigkeiten eines Katalog-Angebots sind erst hier, nach
 *       dem Download, bekannt ({@code packages/&lt;id&gt;.json} kennt kein
 *       {@code capabilities}-Feld) -- fehlt die Zustimmung, wirft {@link
 *       #install} eine {@link CatalogConsentRequiredException}, OHNE die
 *       heruntergeladene Datei zu uebernehmen.</li>
 *   <li><b>d.</b> die {@code .vpkg} atomar nach {@code config/vitrine/packs/}
 *       kopieren, daneben eine frische {@link VpkgIntegritySidecar}-
 *       Begleitdatei mit genau der schon geprueften Pruefsumme/Signatur/
 *       Schluessel schreiben -- ohne die wuerde {@link VitrinePackProvider}
 *       das Paket beim naechsten Scan als "nicht geprueft" ablehnen.</li>
 *   <li><b>e.</b> {@link PackState#addOrUpdate} mit {@link
 *       PackState#SOURCE_CATALOG_PREFIX} + Host der Download-URL (ROADMAP
 *       X3: "Quelle" fuer den Diagnose-Bildschirm).</li>
 *   <li><b>f.</b> Hot-Apply wie {@link PackInstaller} -- Theme-Pakete ueber
 *       {@link ThemePackCoordinator}, alles andere ueber {@link
 *       HotApplyCoordinator#requestActivation}.</li>
 * </ol>
 */
public final class CatalogPackInstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/CatalogInstall");

	private static final String DOWNLOAD_STAGING_SUBDIR = "downloads";
	private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

	private final Path vitrineConfigDir;
	private final VitrinePackProvider packProvider;
	private final HotApplyCoordinator hotApply;
	private final ThemePackCoordinator themePacks;
	private final PackageDownloader downloader;
	private final PublicKey signingPublicKey;

	public CatalogPackInstaller(Path vitrineConfigDir, VitrinePackProvider packProvider, HotApplyCoordinator hotApply,
			ThemePackCoordinator themePacks, PackageDownloader downloader, PublicKey signingPublicKey) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.packProvider = Objects.requireNonNull(packProvider, "packProvider");
		this.hotApply = Objects.requireNonNull(hotApply, "hotApply");
		this.themePacks = themePacks;
		this.downloader = Objects.requireNonNull(downloader, "downloader");
		this.signingPublicKey = Objects.requireNonNull(signingPublicKey, "signingPublicKey");
	}

	/**
	 * Produktions-Verdrahtung: der oeffentliche Signaturschluessel kommt aus
	 * dem Jar ({@link EmbeddedSigningKey#load()}), NICHT aus einem lokalen
	 * Sidecar -- genau das Fertig-Kriterium von ROADMAP G3.
	 */
	public static CatalogPackInstaller production(Path vitrineConfigDir) {
		PublicKey key = EmbeddedSigningKey.load();
		return new CatalogPackInstaller(vitrineConfigDir, VitrinePackProvider.forConfigDir(vitrineConfigDir),
				HotApplyService.instance(), ThemePackService.instance(), PackageDownloader.production(key), key);
	}

	/**
	 * Laedt, prueft und installiert das Katalog-Angebot {@code expectedManifestId}.
	 * Erwartet, dass den (erst nach dem Download bekannten) angemeldeten
	 * Faehigkeiten bereits zugestimmt wurde -- siehe {@link
	 * #confirmCapabilitiesAndInstall}. Ohne angemeldete Faehigkeit (der
	 * Normalfall) ist kein vorheriger Aufruf noetig.
	 *
	 * @return die angemeldeten Faehigkeits-Bezeichner (evtl. leer)
	 * @throws CatalogConsentRequiredException wenn Faehigkeiten angemeldet
	 *                                          sind, denen noch nicht
	 *                                          zugestimmt wurde -- die
	 *                                          heruntergeladene Datei wird
	 *                                          dabei verworfen, {@code packs/}
	 *                                          bleibt unangetastet.
	 * @throws PackInstallException            bei jedem anderen Sicherheits-,
	 *                                          Integritaets- oder Netzfehler.
	 */
	public List<String> install(String expectedManifestId, DownloadRequest request, String sourceHost)
			throws PackInstallException {
		return install(expectedManifestId, request, sourceHost, new CancellationToken(), null);
	}

	/**
	 * Wie {@link #install(String, DownloadRequest, String)}, aber mit einem
	 * explizit uebergebenen {@link CancellationToken} (ROADMAP GATE9: der Nutzer
	 * kann den laufenden Download ueber das Download-Flyout abbrechen) und einem
	 * echten Fortschritts-Rueckruf (ROADMAP GATE4: {@code null} bedeutete bisher
	 * -- entgegen dem Fertig-Kriterium von K3 -- dass das Flyout nie befuellt
	 * wurde; {@code null} bleibt hier weiterhin erlaubt und wird wie
	 * {@link DownloadProgress#NOOP} behandelt, siehe {@link PackageDownloader#download}).
	 *
	 * @throws PackInstallCancelledException wenn {@code cancellationToken} den
	 *                                        Download vor dessen Abschluss
	 *                                        beendet hat -- {@code packs/}
	 *                                        bleibt dabei unangetastet.
	 */
	public List<String> install(String expectedManifestId, DownloadRequest request, String sourceHost,
			CancellationToken cancellationToken, DownloadProgress progress) throws PackInstallException {
		Objects.requireNonNull(expectedManifestId, "expectedManifestId");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(sourceHost, "sourceHost");
		Objects.requireNonNull(cancellationToken, "cancellationToken");

		Path stagingDir = vitrineConfigDir.resolve(DOWNLOAD_STAGING_SUBDIR);
		Path tempTarget = stagingDir.resolve(sanitizeFileName(expectedManifestId) + ".vpkg");
		try {
			Files.createDirectories(stagingDir);
		} catch (IOException e) {
			throw new PackInstallException("Zwischenordner '" + stagingDir + "' liess sich nicht anlegen: " + e, e,
					"vitrine.error.catalog.staging_dir_failed", e.getClass().getSimpleName());
		}

		DownloadOutcome outcome;
		try {
			outcome = downloader.download(request, tempTarget, cancellationToken, progress);
		} catch (DownloadException e) {
			throw new PackInstallException("Download von '" + expectedManifestId + "' fehlgeschlagen: "
					+ e.getMessage(), e, "vitrine.error.catalog.download_failed", expectedManifestId,
					e.getClass().getSimpleName());
		}
		if (!outcome.isCompleted()) {
			throw new PackInstallCancelledException("Download von '" + expectedManifestId + "' wurde abgebrochen.");
		}
		Path downloaded = outcome.file();

		// --- b. Zip-Sicherheit + Manifest -----------------------------------
		Manifest manifest;
		try {
			manifest = PackInstaller.readManifest(downloaded);
		} catch (PackInstallException e) {
			deleteQuietly(downloaded);
			throw e;
		}
		if (!expectedManifestId.equals(manifest.id())) {
			deleteQuietly(downloaded);
			throw new PackInstallException("Heruntergeladenes Katalog-Paket traegt eine andere Manifest-Id ('"
					+ manifest.id() + "') als erwartet ('" + expectedManifestId + "').",
					"vitrine.error.catalog.manifest_id_mismatch", manifest.id(), expectedManifestId);
		}

		// --- c. Faehigkeiten bestimmen + Zustimmung pruefen -----------------
		List<String> capabilityIds = PackInstaller.capabilityIdsOf(manifest);
		PackState state = PackStateStore.load(vitrineConfigDir);
		if (!state.hasCapabilityConsent(expectedManifestId, capabilityIds)) {
			deleteQuietly(downloaded);
			LOGGER.info("Katalog-Installation von '{}' angehalten: Faehigkeiten {} sind noch nicht zugestimmt "
					+ "(Charter-Regel 4).", expectedManifestId, capabilityIds);
			throw new CatalogConsentRequiredException(expectedManifestId, capabilityIds);
		}

		finishInstall(downloaded, manifest, request, state, sourceHost);
		return capabilityIds;
	}

	/**
	 * ROADMAP GATE90: wie {@link #install(String, DownloadRequest, String)},
	 * aber mit einer vom Aufrufer <em>vor</em> dem Download bereits bekannten
	 * Faehigkeitsmenge ({@code knownCapabilityIds}) -- fehlt dafuer die
	 * Zustimmung, wirft dieser Pfad {@link CatalogConsentRequiredException}
	 * <b>bevor</b> {@link #downloader} ueberhaupt angefasst wird, also bevor
	 * irgendein Byte uebers Netz geht (docs/UI-SPEC.md "8. Faehigkeiten-Dialog":
	 * "Erscheint vor der ersten Installation eines Pakets").
	 *
	 * <p>Heute liefert jeder Produktions-Aufrufer ({@code
	 * CatalogPackageDetailSource}) eine leere Liste, weil {@code
	 * registry-package.schema.json} kein {@code capabilities}-Feld fuehrt --
	 * die echten Faehigkeiten eines Katalog-Angebots sind dort erst nach dem
	 * Download bekannt (siehe {@link #install(String, DownloadRequest, String,
	 * CancellationToken, DownloadProgress)}, der danach weiterhin als
	 * zweites, bindendes Sicherheitsnetz laeuft -- Charter-Regel 4 gilt fuer
	 * die <em>tatsaechlich</em> im Manifest angemeldeten Faehigkeiten, nicht
	 * nur fuer die hier vorab bekannten). Eine leere Liste ueberspringt diese
	 * Vorab-Pruefung also folgenlos; sobald eine spaetere Registry-Erweiterung
	 * echte Vorab-Daten liefert, greift dieser Pfad ohne weitere Aenderung.</p>
	 */
	public List<String> install(String expectedManifestId, DownloadRequest request, String sourceHost,
			List<String> knownCapabilityIds, CancellationToken cancellationToken, DownloadProgress progress)
			throws PackInstallException {
		Objects.requireNonNull(knownCapabilityIds, "knownCapabilityIds");
		requireConsentForKnownCapabilities(expectedManifestId, knownCapabilityIds);
		return install(expectedManifestId, request, sourceHost, cancellationToken, progress);
	}

	/** Wie {@link #install(String, DownloadRequest, String, List, CancellationToken, DownloadProgress)}, mit Standard-Token/-Fortschritt. */
	public List<String> install(String expectedManifestId, DownloadRequest request, String sourceHost,
			List<String> knownCapabilityIds) throws PackInstallException {
		return install(expectedManifestId, request, sourceHost, knownCapabilityIds, new CancellationToken(), null);
	}

	/**
	 * GATE90: die eigentliche Vorab-Pruefung hinter den beiden Ueberladungen
	 * oben -- komplett ohne Download-Zugriff, rein gegen {@code state.json}.
	 */
	private void requireConsentForKnownCapabilities(String expectedManifestId, List<String> knownCapabilityIds)
			throws CatalogConsentRequiredException {
		if (knownCapabilityIds.isEmpty()) {
			return;
		}
		PackState state = PackStateStore.load(vitrineConfigDir);
		if (!state.hasCapabilityConsent(expectedManifestId, knownCapabilityIds)) {
			LOGGER.info("Katalog-Installation von '{}' angehalten, noch vor dem Download: bereits bekannte "
					+ "Faehigkeiten {} sind noch nicht zugestimmt (Charter-Regel 4).", expectedManifestId,
					knownCapabilityIds);
			throw new CatalogConsentRequiredException(expectedManifestId, knownCapabilityIds);
		}
	}

	/**
	 * Haelt die Zustimmung des Nutzers zu {@code capabilityIds} fest und
	 * installiert danach erneut (die {@code .vpkg} wird dafuer noch einmal
	 * geladen -- {@link PackageDownloader} erkennt eine bereits verifiziert
	 * vorliegende Zieldatei idempotent, ein zweiter voller Netz-Transfer ist
	 * damit der seltene Ausnahmefall, nicht der Normalfall).
	 */
	public void confirmCapabilitiesAndInstall(String expectedManifestId, Collection<String> capabilityIds,
			DownloadRequest request, String sourceHost) throws PackInstallException {
		confirmCapabilitiesAndInstall(expectedManifestId, capabilityIds, request, sourceHost,
				new CancellationToken(), null);
	}

	/**
	 * Wie {@link #confirmCapabilitiesAndInstall(String, Collection, DownloadRequest, String)},
	 * aber mit einem echten {@link CancellationToken}/{@link DownloadProgress}
	 * fuer den (seltenen) erneuten Download nach der Faehigkeiten-Zustimmung --
	 * siehe {@link #install(String, DownloadRequest, String, CancellationToken, DownloadProgress)}.
	 */
	public void confirmCapabilitiesAndInstall(String expectedManifestId, Collection<String> capabilityIds,
			DownloadRequest request, String sourceHost, CancellationToken cancellationToken, DownloadProgress progress)
			throws PackInstallException {
		Objects.requireNonNull(expectedManifestId, "expectedManifestId");
		Objects.requireNonNull(capabilityIds, "capabilityIds");
		try {
			PackStateStore.update(vitrineConfigDir, state -> {
				state.recordCapabilityConsent(expectedManifestId, capabilityIds);
				return null;
			});
		} catch (IOException e) {
			throw new PackInstallException("Faehigkeiten-Zustimmung liess sich nicht speichern: " + e, e,
					"vitrine.error.catalog.consent_save_failed", e.getClass().getSimpleName());
		}
		LOGGER.info("Faehigkeiten-Zustimmung fuer Katalog-Paket '{}' gespeichert: {}", expectedManifestId,
				capabilityIds);
		install(expectedManifestId, request, sourceHost, cancellationToken, progress);
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private void finishInstall(Path downloadedVpkg, Manifest manifest, DownloadRequest request, PackState state,
			String sourceHost) throws PackInstallException {
		Path packsDir = packProvider.packsDir();
		String profileId = VitrinePackProvider.profileId(manifest.id());

		// Schon installiert (z. B. Doppelklick auf "Installieren")? Dann keine zweite
		// Kopie anlegen -- im Spiel entstanden so sechs Kopien desselben Themes.
		java.util.Optional<Path> existing = packProvider.findPackFile(profileId);
		if (existing.isPresent()) {
			deleteQuietly(downloadedVpkg);
			LOGGER.info("Katalog-Paket '{}' ist schon installiert ('{}'), keine zweite Kopie.", profileId,
					existing.get());
			requestActivationFor(profileId, manifest);
			return;
		}

		Path targetVpkg;
		try {
			Files.createDirectories(packsDir);
			targetVpkg = uniqueTargetPath(packsDir, sanitizeFileName(manifest.id()) + ".vpkg");
			copyAtomically(downloadedVpkg, targetVpkg);
		} catch (IOException e) {
			throw new PackInstallException("Katalog-Paket liess sich nicht nach '" + packsDir + "' kopieren: " + e, e,
					"vitrine.error.catalog.copy_failed", e.getClass().getSimpleName());
		} finally {
			deleteQuietly(downloadedVpkg);
		}

		try {
			VpkgIntegritySidecar.write(targetVpkg, request.sha256Hex(), request.signatureBytes(), signingPublicKey);
		} catch (IOException e) {
			deleteQuietly(targetVpkg);
			throw new PackInstallException("Integritaets-Beleg fuer '" + targetVpkg + "' liess sich nicht schreiben: "
					+ e, e, "vitrine.error.catalog.sidecar_write_failed", e.getClass().getSimpleName());
		}

		try {
			PackStateStore.update(vitrineConfigDir, fresh -> {
				fresh.addOrUpdate(profileId, true, PackState.SOURCE_CATALOG_PREFIX + sourceHost);
				return null;
			});
		} catch (IOException e) {
			deleteQuietly(targetVpkg);
			deleteQuietly(VpkgIntegritySidecar.sidecarPathFor(targetVpkg));
			throw new PackInstallException("Zustand liess sich nach dem Kopieren nicht speichern: " + e, e,
					"vitrine.error.catalog.state_save_failed", e.getClass().getSimpleName());
		}

		LOGGER.info("Katalog-Paket '{}' installiert (Quelle '{}'), Aktivierung wird angestossen.",
				profileId, sourceHost);
		requestActivationFor(profileId, manifest);
	}

	private void requestActivationFor(String profileId, Manifest manifest) {
		if (themePacks != null && ThemePackCoordinator.isThemePackage(manifest)) {
			LOGGER.info("Theme-Katalog-Paket '{}' wird hot angewandt: Token-Wechsel ohne Ressourcen-Reload.",
					profileId);
			themePacks.onThemePackActivated(profileId);
			return;
		}
		hotApply.requestActivation(profileId);
	}

	private static String sanitizeFileName(String manifestId) {
		return UNSAFE_FILE_NAME_CHARS.matcher(manifestId.toLowerCase(Locale.ROOT)).replaceAll("-");
	}

	/** Zielpfad in {@code packsDir}, der garantiert noch nicht existiert -- wie {@code PackInstaller#uniqueTargetPath}. */
	private static Path uniqueTargetPath(Path packsDir, String fileName) {
		Path candidate = packsDir.resolve(fileName);
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
			stamped = packsDir.resolve(base + "-" + suffix + extension);
			attempt++;
		} while (Files.exists(stamped));
		return stamped;
	}

	/** Kopiert {@code source} nach {@code target} ueber eine Temp-Datei + {@code ATOMIC_MOVE} -- wie {@code PackInstaller#copyAtomically}. */
	private static void copyAtomically(Path source, Path target) throws IOException {
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt ({}), weiche auf einen "
						+ "gewoehnlichen Move aus.", tmp, target, e.toString());
				Files.move(tmp, target);
			}
		} catch (IOException e) {
			deleteQuietly(tmp);
			throw e;
		}
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("Aufraeumen von '{}' fehlgeschlagen: {}", path, e.toString());
		}
	}
}
