package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgIntegrityException;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
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
import java.util.stream.Stream;

/**
 * ROADMAP S4 ("Installieren aus der Oberfläche"): installiert ein Angebot aus
 * {@code config/vitrine/library/} in exakt der von der Backlog-Zeile
 * vorgegebenen Reihenfolge -- und keinen Schritt vorher.
 *
 * <ol>
 *   <li><b>a.</b> {@link VpkgSecurityValidator#requireSafe} -- Zip-Slip,
 *       Zip-Bombe, verbotene Dateitypen, PNG-Maße.</li>
 *   <li><b>b.</b> {@link VpkgIntegrityValidator#requireValid} -- SHA-256 aus
 *       dem Begleit-Beleg ({@link VpkgIntegritySidecar}), Ed25519 aber
 *       ausschliesslich gegen den im Jar eingebetteten, vertrauten Schluessel
 *       ({@link de.spassglas.vitrine.vpkg.EmbeddedSigningKey#load()}) --
 *       <b>nie</b> gegen einen vom Paket/Sidecar mitgelieferten Schluessel
 *       (GATE29).</li>
 *   <li><b>c.</b> {@link VpkgCapabilityGrant#of(Manifest)} -- die im Manifest
 *       angemeldeten Fähigkeiten bestimmen; ist ihnen nicht bereits im
 *       {@code state.json} zugestimmt worden (Fähigkeiten-Dialog, Charter-Regel
 *       4: "Default ist Verbot"), bricht die Installation hier ab, <em>bevor</em>
 *       irgendetwas kopiert wird.</li>
 *   <li><b>d.</b> die {@code .vpkg} (samt Integritäts-Beleg) atomar nach
 *       {@code config/vitrine/packs/} kopieren (Temp-Datei + {@code
 *       ATOMIC_MOVE}); ein bereits vorhandener gleicher Dateiname wird nie
 *       überschrieben.</li>
 *   <li><b>e.</b> {@link PackStateStore} fortschreiben (installiert, aktiv).</li>
 *   <li><b>f.</b> {@link HotApplyCoordinator#requestActivation} -- Debounce +
 *       Ressourcen-Reload ohne Neustart.</li>
 * </ol>
 *
 * <p>Scheitert ein Schritt, wird sauber abgebrochen: nichts Halbes bleibt in
 * {@code packs/} liegen, der Zustand bleibt unangetastet, die Meldung ist
 * Klartext ({@link PackInstallException}).</p>
 *
 * <p>Kennt -- wie {@link PackUninstaller} -- keine Minecraft-Typen direkt; die
 * Verdrahtung mit dem laufenden Client steckt in {@link PackInstallService},
 * austauschbar gegen Test-Doubles.</p>
 */
public final class PackInstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Install");

	private static final String LIBRARY_SUBDIR = "library";
	private static final String VPKG_EXTENSION = ".vpkg";

	private final VitrinePackProvider packProvider;
	private final HotApplyCoordinator hotApply;
	/**
	 * ROADMAP T4: fuer Klasse-A-Theme-Pakete ({@code category: "theme"}) uebernimmt
	 * dieser Koordinator die Aktivierung (Token-Wechsel, <b>kein</b>
	 * Ressourcen-Reload). {@code null}, wenn kein Theme-Pfad verdrahtet ist -- dann
	 * laeuft auch ein Theme-Paket ueber den gewoehnlichen {@link #hotApply}-Weg
	 * (Verhalten vor T4, fuer reine Installer-Unit-Tests ohne Theme-Anbindung).
	 */
	private final ThemePackCoordinator themePacks;

	public PackInstaller(VitrinePackProvider packProvider, HotApplyCoordinator hotApply) {
		this(packProvider, hotApply, null);
	}

	public PackInstaller(VitrinePackProvider packProvider, HotApplyCoordinator hotApply,
			ThemePackCoordinator themePacks) {
		this.packProvider = Objects.requireNonNull(packProvider, "packProvider");
		this.hotApply = Objects.requireNonNull(hotApply, "hotApply");
		this.themePacks = themePacks;
	}

	/**
	 * Ermittelt die im Manifest von {@code manifestId} angemeldeten Fähigkeiten
	 * (als Bezeichner-Liste), ohne irgendetwas zu verändern -- für den
	 * Fähigkeiten-Dialog.
	 *
	 * @throws PackInstallException wenn das Angebot fehlt oder sein Manifest
	 *                              ungültige Fähigkeiten anmeldet
	 */
	public List<String> requestedCapabilityIds(String manifestId) throws PackInstallException {
		Objects.requireNonNull(manifestId, "manifestId");
		Manifest manifest = readManifest(locateLibraryFile(manifestId));
		return capabilityIdsOf(manifest);
	}

	/**
	 * Hält die Zustimmung des Nutzers zu den angemeldeten Fähigkeiten von
	 * {@code manifestId} im {@code state.json} fest und installiert danach.
	 * Genau der Aufruf, den der Fähigkeiten-Dialog beim Bestätigen macht.
	 */
	public void confirmCapabilitiesAndInstall(String manifestId, Collection<String> capabilityIds)
			throws PackInstallException {
		Objects.requireNonNull(manifestId, "manifestId");
		Objects.requireNonNull(capabilityIds, "capabilityIds");
		Path configDir = configDir();
		try {
			PackStateStore.update(configDir, state -> {
				state.recordCapabilityConsent(manifestId, capabilityIds);
				return null;
			});
		} catch (IOException e) {
			throw new PackInstallException("Fähigkeiten-Zustimmung ließ sich nicht speichern: " + e, e,
					"vitrine.error.install.consent_save_failed", e.getClass().getSimpleName());
		}
		LOGGER.info("Fähigkeiten-Zustimmung für '{}' gespeichert: {}", manifestId, capabilityIds);
		install(manifestId);
	}

	/**
	 * ROADMAP K5 ("Updates ... Rückfall auf die alte Version"): prüft eine
	 * bereits an einen temporären Ort heruntergeladene {@code .vpkg} einer
	 * <em>neuen</em> Version mit exakt den Schritten a–c dieser Installations-
	 * Kette -- <b>ohne</b> irgendetwas zu kopieren oder am Zustand zu ändern:
	 *
	 * <ol>
	 *   <li><b>a.</b> {@link VpkgSecurityValidator#requireSafe} + Manifest
	 *       parsen; die Manifest-ID muss zu {@code expectedManifestId} passen.</li>
	 *   <li><b>b.</b> {@link VpkgIntegrityValidator#requireValid} über die
	 *       daneben liegende {@link VpkgIntegritySidecar}-Begleitdatei --
	 *       SHA-256 + Ed25519.</li>
	 *   <li><b>c.</b> die im (neuen) Manifest angemeldeten Fähigkeiten müssen im
	 *       {@code state.json} bereits zugestimmt sein (Charter-Regel 4: Default
	 *       ist Verbot). Meldet die neue Version zusätzliche Fähigkeiten an,
	 *       schlägt die Prüfung hier fehl und die alte Version bleibt.</li>
	 * </ol>
	 *
	 * <p>So bleibt die Prüf-Kette (Zip-Sicherheit, Prüfsumme, Signatur,
	 * Fähigkeiten) der einzige Weg, auf dem eine Version live gehen kann,
	 * während der eigentliche Datei-Tausch samt Rückfall beim Aufrufer
	 * ({@link PackUpdater}) liegt -- der den Ausgang des Ressourcen-Reloads
	 * abwarten muss, um die alte Version bei Bedarf wiederherzustellen.</p>
	 *
	 * @return das geparste, geprüfte Manifest der neuen Version (für den
	 *         SemVer-Abgleich durch den Aufrufer).
	 * @throws PackInstallException bei jedem Sicherheits-, Integritäts- oder
	 *                              Zustimmungsverstoß -- der Aufrufer fasst dann
	 *                              nichts an.
	 */
	public static Manifest verifyDownloadedPackage(Path vitrineConfigDir, Path vpkgFile, String expectedManifestId)
			throws PackInstallException {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		Objects.requireNonNull(vpkgFile, "vpkgFile");
		Objects.requireNonNull(expectedManifestId, "expectedManifestId");

		// --- a. Zip-Sicherheit + Manifest --------------------------------
		Manifest manifest;
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			manifest = ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			throw new PackInstallException("Heruntergeladene Version '" + vpkgFile + "' ließ sich nicht lesen: " + e, e,
					"vitrine.error.install.offer_unreadable", vpkgFile.getFileName().toString(),
					e.getClass().getSimpleName());
		} catch (VpkgSecurityException e) {
			throw new PackInstallException("Zip-Sicherheitsprüfung der neuen Version fehlgeschlagen: " + e.getMessage(), e,
					"vitrine.error.install.security_check_failed", vpkgFile.getFileName().toString());
		} catch (ManifestParseException e) {
			throw new PackInstallException("Manifest der neuen Version ungültig: " + e.getMessage(), e,
					"vitrine.error.install.manifest_invalid", vpkgFile.getFileName().toString());
		}
		if (!expectedManifestId.equals(manifest.id())) {
			throw new PackInstallException("Heruntergeladene Version trägt eine andere Manifest-ID ('" + manifest.id()
					+ "') als erwartet ('" + expectedManifestId + "').",
					"vitrine.error.install.manifest_id_mismatch", manifest.id(), expectedManifestId);
		}

		// --- b. Prüfsumme + Signatur -----------------------------------
		// GATE29: der oeffentliche Schluessel kommt ausschliesslich aus dem im
		// Jar eingebetteten, vertrauten Registry-Schluessel -- NICHT aus dem
		// vom Paket/Sidecar mitgelieferten Beleg (integrity.publicKey() waere
		// von einem Angreifer frei waehlbar, siehe EmbeddedSigningKey-Doku).
		VpkgIntegritySidecar.Content integrity = VpkgIntegritySidecar.read(vpkgFile);
		PublicKey trustedKey = EmbeddedSigningKey.load();
		try {
			VpkgIntegrityValidator.requireValid(vpkgFile, integrity.sha256(), integrity.signature(), trustedKey);
		} catch (VpkgIntegrityException e) {
			throw new PackInstallException("Prüfsummen-/Signaturprüfung der neuen Version fehlgeschlagen: "
					+ e.getMessage(), e, "vitrine.error.install.integrity_check_failed", vpkgFile.getFileName().toString());
		}

		// --- c. Fähigkeiten-Zustimmung prüfen ------------------------
		List<String> capabilityIds = capabilityIdsOf(manifest);
		PackState state = PackStateStore.load(vitrineConfigDir);
		if (!state.hasCapabilityConsent(expectedManifestId, capabilityIds)) {
			throw new PackInstallException("Die neue Version von '" + expectedManifestId + "' meldet Fähigkeiten an, "
					+ "denen noch nicht zugestimmt wurde " + capabilityIds
					+ " -- der Fähigkeiten-Dialog muss zuerst erneut bestätigt werden (Charter-Regel 4).",
					"vitrine.error.install.capabilities_not_confirmed", expectedManifestId);
		}

		LOGGER.info("Neue Version von '{}' (Version {}) hat Zip-Sicherheit, Prüfsumme, Signatur und "
				+ "Fähigkeiten-Zustimmung bestanden.", expectedManifestId, manifest.version());
		return manifest;
	}

	/**
	 * Installiert das Angebot mit Manifest-ID {@code manifestId} aus dem
	 * Bibliotheksordner. Erwartet, dass den angemeldeten Fähigkeiten bereits
	 * zugestimmt wurde (siehe {@link #confirmCapabilitiesAndInstall}).
	 *
	 * <p>Ist das Paket bereits installiert und liegt seine Datei in
	 * {@code packs/}, wird nichts erneut kopiert -- nur der Aktiv-Zustand
	 * sichergestellt und ein Reload angestoßen (idempotent).</p>
	 */
	public void install(String manifestId) throws PackInstallException {
		Objects.requireNonNull(manifestId, "manifestId");
		Path libraryFile = locateLibraryFile(manifestId);
		Path configDir = configDir();
		Path packsDir = packProvider.packsDir();
		String profileId = VitrinePackProvider.profileId(manifestId);

		// --- a. Zip-Sicherheit + Manifest -----------------------------------
		Manifest manifest;
		try (VpkgArchive archive = VpkgArchive.open(libraryFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			manifest = ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			throw new PackInstallException("Angebot '" + libraryFile + "' ließ sich nicht lesen: " + e, e,
					"vitrine.error.install.offer_unreadable", libraryFile.getFileName().toString(),
					e.getClass().getSimpleName());
		} catch (VpkgSecurityException e) {
			throw new PackInstallException("Zip-Sicherheitsprüfung fehlgeschlagen: " + e.getMessage(), e,
					"vitrine.error.install.security_check_failed", libraryFile.getFileName().toString());
		} catch (ManifestParseException e) {
			throw new PackInstallException("Manifest ungültig: " + e.getMessage(), e,
					"vitrine.error.install.manifest_invalid", libraryFile.getFileName().toString());
		}
		if (!manifestId.equals(manifest.id())) {
			throw new PackInstallException("Angebot '" + libraryFile + "' trägt eine andere Manifest-ID ('"
					+ manifest.id() + "') als erwartet ('" + manifestId + "').",
					"vitrine.error.install.manifest_id_mismatch", manifest.id(), manifestId);
		}

		// --- b. Prüfsumme + Signatur --------------------------------------
		// GATE29: siehe verifyDownloadedPackage -- ausschliesslich gegen den
		// eingebetteten Registry-Schluessel, nie gegen den Sidecar-Schluessel.
		VpkgIntegritySidecar.Content integrity = VpkgIntegritySidecar.read(libraryFile);
		PublicKey trustedKey = EmbeddedSigningKey.load();
		try {
			VpkgIntegrityValidator.requireValid(libraryFile, integrity.sha256(), integrity.signature(), trustedKey);
		} catch (VpkgIntegrityException e) {
			throw new PackInstallException("Prüfsummen-/Signaturprüfung fehlgeschlagen: " + e.getMessage(), e,
					"vitrine.error.install.integrity_check_failed", libraryFile.getFileName().toString());
		}

		// --- c. Fähigkeiten bestimmen + Zustimmung prüfen -----------------
		List<String> capabilityIds = capabilityIdsOf(manifest);
		PackState state = PackStateStore.load(configDir);
		if (!state.hasCapabilityConsent(manifestId, capabilityIds)) {
			throw new PackInstallException("Den angemeldeten Fähigkeiten von '" + manifestId
					+ "' wurde noch nicht zugestimmt -- der Fähigkeiten-Dialog muss zuerst bestätigt werden "
					+ "(Charter-Regel 4: Default ist Verbot).",
					"vitrine.error.install.capabilities_not_confirmed", manifestId);
		}

		// --- Idempotenz + d./e. unter derselben Sperre wie die state.json-
		// Schreibseite (GATE120): ohne das koennten zwei gleichzeitige
		// install()-Aufrufe fuer dieselbe Manifest-ID beide den "schon
		// installiert?"-Check unbeschadet passieren und zwei Dateien in
		// packs/ anlegen -- check-then-act ohne Schutz. PackStateStore.runLocked
		// nimmt exakt die Ordner-Sperre, die PackStateStore.update/load/save
		// sowieso schon benutzen (ReentrantLock, daher reentrant-sicher fuer
		// die update(...)-Aufrufe darin).
		PackStateStore.runLocked(configDir, () -> {
			PackState lockedState = PackStateStore.load(configDir);
			if (lockedState.isInstalled(profileId) && packProvider.findPackFile(profileId).isPresent()) {
				LOGGER.info("Vitrine-Paket '{}' ist bereits installiert -- keine erneute Kopie, nur Aktivierung.",
						profileId);
				updateState(configDir, fresh -> fresh.addOrUpdate(profileId, true, PackState.SOURCE_LOCAL_LIBRARY));
				return null;
			}

			// --- d. .vpkg (+ Beleg) atomar nach packs/ kopieren --------------
			Path targetVpkg;
			try {
				Files.createDirectories(packsDir);
				targetVpkg = uniqueTargetPath(packsDir, libraryFile.getFileName().toString());
				copyAtomically(libraryFile, targetVpkg);
			} catch (IOException e) {
				throw new PackInstallException("Paketdatei ließ sich nicht nach '" + packsDir + "' kopieren: " + e, e,
						"vitrine.error.install.copy_failed", e.getClass().getSimpleName());
			}

			Path libSidecar = VpkgIntegritySidecar.sidecarPathFor(libraryFile);
			Path targetSidecar = VpkgIntegritySidecar.sidecarPathFor(targetVpkg);
			if (Files.exists(libSidecar)) {
				try {
					copyAtomically(libSidecar, targetSidecar);
				} catch (IOException e) {
					deleteQuietly(targetVpkg);
					throw new PackInstallException("Integritäts-Beleg ließ sich nicht mitkopieren: " + e, e,
							"vitrine.error.install.sidecar_copy_failed", e.getClass().getSimpleName());
				}
			}

			// --- e. Zustand fortschreiben (installiert + aktiv) --------------
			// ROADMAP X3: der Bibliotheksordner ist heute der einzige verdrahtete
			// Installationsweg -- die Quelle ist damit ehrlich bekannt.
			try {
				PackStateStore.update(configDir, fresh -> {
					fresh.addOrUpdate(profileId, true, PackState.SOURCE_LOCAL_LIBRARY);
					return null;
				});
			} catch (IOException e) {
				deleteQuietly(targetVpkg);
				deleteQuietly(targetSidecar);
				throw new PackInstallException("Zustand ließ sich nach dem Kopieren nicht speichern: " + e, e,
						"vitrine.error.install.state_save_failed", e.getClass().getSimpleName());
			}

			LOGGER.info("Vitrine-Paket '{}' installiert (Datei '{}' -> '{}'), Aktivierung wird angestoßen.",
					profileId, libraryFile.getFileName(), targetVpkg);
			return null;
		});

		// --- f. Hot-Apply (Debounce + Reload, kein Neustart) ------------
		// Klasse-A-Theme: Token-Wechsel ohne Ressourcen-Reload (ROADMAP T4).
		requestActivationFor(profileId, manifest);
	}

	/**
	 * ROADMAP T4: leitet die Aktivierung fuer ein Klasse-A-Theme-Paket
	 * ({@code category: "theme"}) an den {@link ThemePackCoordinator} (Token-Wechsel,
	 * <b>kein</b> {@code reloadResources()}); jedes andere Paket geht den
	 * gewoehnlichen {@link HotApplyCoordinator}-Weg (Debounce + Ressourcen-Reload).
	 */
	private void requestActivationFor(String profileId, Manifest manifest) {
		if (themePacks != null && ThemePackCoordinator.isThemePackage(manifest)) {
			LOGGER.info("Theme-Paket '{}' wird hot angewandt: Token-Wechsel ohne Ressourcen-Reload (Klasse A, T4).",
					profileId);
			themePacks.onThemePackActivated(profileId);
			return;
		}
		hotApply.requestActivation(profileId);
	}

	/** ROADMAP T4: {@code true}, wenn das in {@code packs/} liegende Paket {@code profileId} ein Theme-Paket ist. */
	private boolean isInstalledThemePack(String profileId) {
		return packProvider.findPackFile(profileId)
				.map(file -> {
					try (VpkgArchive archive = VpkgArchive.open(file)) {
						return ThemePackCoordinator.isThemePackage(ManifestParser.parse(archive.readManifestBytes()));
					} catch (IOException | ManifestParseException | RuntimeException e) {
						LOGGER.debug("Kategorie von '{}' fuer setActive nicht bestimmbar: {}", profileId, e.toString());
						return false;
					}
				})
				.orElse(false);
	}

	/**
	 * An/Aus-Umschalter aus der Oberfläche (ROADMAP S4): setzt den Aktiv-Zustand
	 * eines bereits installierten Pakets und stößt den passenden Reload an --
	 * ohne die Datei anzufassen, ohne Neu-Installation.
	 */
	public void setActive(String profileId, boolean active) throws PackInstallException {
		Objects.requireNonNull(profileId, "profileId");
		Path configDir = configDir();
		PackState state = PackStateStore.load(configDir);
		if (!state.isInstalled(profileId)) {
			throw new PackInstallException("Paket '" + profileId + "' ist nicht installiert -- kein An/Aus möglich.",
					"vitrine.error.install.not_installed", profileId);
		}
		updateState(configDir, fresh -> fresh.addOrUpdate(profileId, active));
		if (themePacks != null && isInstalledThemePack(profileId)) {
			// Klasse-A-Theme (ROADMAP T4): Token-Wechsel ohne Ressourcen-Reload.
			if (active) {
				themePacks.onThemePackActivated(profileId);
			} else {
				themePacks.onThemePackDeactivated(profileId);
			}
		} else if (active) {
			hotApply.requestActivation(profileId);
		} else {
			hotApply.requestDeactivation(profileId);
		}
		LOGGER.info("Vitrine-Paket '{}' aus der Oberfläche {}.", profileId, active ? "aktiviert" : "deaktiviert");
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private Path configDir() {
		return packProvider.packsDir().getParent();
	}

	private Path libraryDir() {
		return configDir().resolve(LIBRARY_SUBDIR);
	}

	/** GATE41: schreibt {@code state.json} unter der Ordner-Sperre fort (laden, aendern, speichern in einem Zug). */
	private void updateState(Path configDir, java.util.function.Consumer<PackState> mutation)
			throws PackInstallException {
		try {
			PackStateStore.update(configDir, fresh -> {
				mutation.accept(fresh);
				return null;
			});
		} catch (IOException e) {
			throw new PackInstallException("Zustand ließ sich nicht speichern: " + e, e,
					"vitrine.error.install.state_save_failed", e.getClass().getSimpleName());
		}
	}

	/** Package-private (statt {@code private}), damit {@link BundleInstaller} (ROADMAP X1) dieselbe Faehigkeiten-Logik wiederverwendet, statt sie zu duplizieren. */
	static List<String> capabilityIdsOf(Manifest manifest) throws PackInstallException {
		try {
			VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(manifest);
			return grant.granted().stream().map(VpkgCapability::id).sorted().toList();
		} catch (VpkgCapabilityException e) {
			throw new PackInstallException("Angemeldete Fähigkeiten ungültig: " + e.getMessage(), e,
					"vitrine.error.install.capabilities_invalid", manifest.id());
		}
	}

	private Path locateLibraryFile(String manifestId) throws PackInstallException {
		Path libraryDir = libraryDir();
		if (!Files.isDirectory(libraryDir)) {
			throw new PackInstallException("Kein Bibliotheksordner '" + libraryDir + "' -- nichts zu installieren.",
					"vitrine.error.install.library_dir_missing");
		}
		List<Path> candidates;
		try (Stream<Path> stream = Files.list(libraryDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new PackInstallException("Bibliotheksordner '" + libraryDir + "' ließ sich nicht lesen: " + e, e,
					"vitrine.error.install.library_dir_unreadable", e.getClass().getSimpleName());
		}
		for (Path candidate : candidates) {
			// Nur das Manifest lesen, um das vom Nutzer gewählte Angebot
			// wiederzufinden -- die eigentliche Zip-Sicherheitsprüfung (Schritt
			// a) und jede Anwendung passieren erst danach in install(...).
			if (manifestId.equals(peekManifestId(candidate))) {
				return candidate;
			}
		}
		throw new PackInstallException("Kein Angebot mit Id '" + manifestId + "' im Bibliotheksordner '"
				+ libraryDir + "'.", "vitrine.error.install.offer_not_found", manifestId);
	}

	/** Package-private (statt {@code private}), damit {@link BundleInstaller} (ROADMAP X1) dieselbe Pruefung (Zip-Sicherheit + Manifest) wiederverwendet, statt sie zu duplizieren. */
	static Manifest readManifest(Path vpkgFile) throws PackInstallException {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			return ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			throw new PackInstallException("Angebot '" + vpkgFile + "' ließ sich nicht lesen: " + e, e,
					"vitrine.error.install.offer_unreadable", vpkgFile.getFileName().toString(),
					e.getClass().getSimpleName());
		} catch (VpkgSecurityException e) {
			throw new PackInstallException("Zip-Sicherheitsprüfung fehlgeschlagen: " + e.getMessage(), e,
					"vitrine.error.install.security_check_failed", vpkgFile.getFileName().toString());
		} catch (ManifestParseException e) {
			throw new PackInstallException("Manifest ungültig: " + e.getMessage(), e,
					"vitrine.error.install.manifest_invalid", vpkgFile.getFileName().toString());
		}
	}

	private static String peekManifestId(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			return ManifestParser.parse(archive.readManifestBytes()).id();
		} catch (IOException | ManifestParseException | RuntimeException e) {
			LOGGER.debug("Bibliotheks-Datei '{}' beim Suchen übersprungen: {}", vpkgFile, e.toString());
			return null;
		}
	}

	/**
	 * Zielpfad in {@code packsDir} für {@code fileName}, der garantiert noch
	 * nicht existiert -- eine Namenskollision wird nie überschrieben (Charter-
	 * Regel 3), stattdessen wird ein Millisekunden-Zeitstempel eingefügt (wie
	 * {@code PackUninstaller#uniqueTrashPath}).
	 */
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

	/**
	 * Kopiert {@code source} nach {@code target} über eine Temp-Datei im
	 * Zielordner und {@code ATOMIC_MOVE} -- {@code target} wird nie
	 * halbgeschrieben sichtbar. {@code target} ist über {@link #uniqueTargetPath}
	 * bereits als nicht existent garantiert.
	 */
	private static void copyAtomically(Path source, Path target) throws IOException {
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstützt ({}), weiche auf einen "
						+ "gewöhnlichen Move aus.", tmp, target, e.toString());
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
			LOGGER.warn("Aufräumen von '{}' nach abgebrochener Installation fehlgeschlagen: {}", path, e.toString());
		}
	}
}
