package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.hotapply.MinecraftPackActivation;
import de.spassglas.vitrine.hotapply.MinecraftResourceReloadTrigger;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * ROADMAP X1 ("Bundles: mehrere Pakete atomar installieren, bei Fehler alles
 * zurück") -- installiert eine geordnete Liste von {@code .vpkg}-Dateien als
 * <b>eine</b> Einheit.
 *
 * <ol>
 *   <li><b>Phase 1, Pruefen (nichts wird angefasst):</b> jedes Bundle-Teil
 *       durchlaeuft, der Reihe nach, exakt dieselbe Pruef-Kette wie ein
 *       einzelnes Paket bei {@link PackInstaller} -- Zip-Sicherheit
 *       ({@link PackInstaller#readManifest}), danach Pruefsumme, Signatur
 *       <b>und</b> Faehigkeiten-Zustimmung ueber
 *       {@link PackInstaller#verifyDownloadedPackage}. Scheitert
 *       <em>irgendein</em> Teil -- egal an welcher Stelle in der Liste --,
 *       bricht die gesamte Installation hier ab: es wurde bis dahin noch
 *       nichts kopiert, also bleibt auch kein Teil des Bundles zurueck (auch
 *       nicht die fuer sich genommen gueltigen Teile davor).</li>
 *   <li><b>Phase 2, Kopieren:</b> erst wenn <em>alle</em> Teile Phase 1
 *       bestanden haben, wird jedes atomar (Temp-Datei + {@code ATOMIC_MOVE},
 *       wie {@link PackInstaller#install}) nach {@code config/vitrine/packs/}
 *       kopiert. Scheitert ein Kopiervorgang mittendrin, werden alle bereits
 *       kopierten Teile dieses Bundles sofort wieder entfernt.</li>
 *   <li><b>Phase 3, Zustand:</b> {@link PackStateStore} wird <b>einmal</b> fuer
 *       alle Teile zusammen fortgeschrieben (installiert + aktiv). Scheitert
 *       das Schreiben, werden ebenfalls alle Kopien zurueckgenommen.</li>
 *   <li><b>Phase 4, Aktivieren + EIN gesammelter Reload:</b> alle Profile
 *       werden ueber {@link PackActivation#activate} aktiviert, danach wird
 *       {@link ResourceReloadTrigger#reloadResources()} genau <b>einmal</b>
 *       fuer das gesamte Bundle angestossen -- kein Reload pro Paket.
 *       <b>GATE38:</b> auf diesen Reload wird nicht mehr blockierend
 *       gewartet; der Ausgang haengt als Fortsetzung ({@code handle} /
 *       {@code thenCompose}) an seiner Future, genau wie bei
 *       {@link PackUninstaller}. Der Client-Thread, auf dem der Reload
 *       selbst laeuft, blockiert dadurch nie gegen sich selbst.
 *       Schlaegt dieser eine Reload fehl, werden alle
 *       Bundle-Teile wieder deaktiviert, aus {@code state.json} entfernt, ihre
 *       Dateien aus {@code packs/} geloescht und ein bereinigender Reload
 *       angestossen, damit das Spiel in einem sauberen, benutzbaren Zustand
 *       bleibt (derselbe Rueckfall-Gedanke wie {@link PackUpdater}, hier fuer
 *       mehrere gleichzeitig neu installierte Pakete statt fuer ein Update
 *       eines einzelnen).</li>
 * </ol>
 *
 * <p><b>Faehigkeiten (Charter-Regel 4, "Default ist Verbot"):</b> meldet ein
 * Bundle-Teil Faehigkeiten an, denen im {@code state.json} noch nicht
 * zugestimmt wurde, scheitert Phase 1 fuer dieses Teil -- und damit das ganze
 * Bundle -- genau wie bei einem einzelnen Paket. Es gibt keinen Pfad, der
 * einem ganzen Bundle stillschweigend Faehigkeiten genehmigt: die Zustimmung
 * muss ueber {@link #confirmCapabilitiesAndInstall} vorher explizit erteilt
 * werden.</p>
 *
 * <p>Kennt -- wie {@link PackUpdater} -- keine Minecraft-Typen direkt;
 * {@link PackActivation} und {@link ResourceReloadTrigger} sind dieselben
 * Schnittstellen, ueber die auch {@code HotApplyCoordinator} und
 * {@code PackUninstaller} den echten Client ansteuern, hier direkt genutzt
 * statt ueber den Debounce-Koordinator, weil ein Bundle einen einzigen,
 * unmittelbar abzuwartenden Reload braucht (kein 200-ms-Debounce-Fenster, das
 * bei einem synchronen Test-Double mehrere Reloads statt eines einzigen
 * ausloesen wuerde).</p>
 */
public final class BundleInstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Bundle");

	private final Path vitrineConfigDir;
	private final VitrinePackProvider provider;
	private final PackActivation packActivation;
	private final ResourceReloadTrigger reloadTrigger;

	public BundleInstaller(Path vitrineConfigDir, VitrinePackProvider provider, PackActivation packActivation,
			ResourceReloadTrigger reloadTrigger) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.packActivation = Objects.requireNonNull(packActivation, "packActivation");
		this.reloadTrigger = Objects.requireNonNull(reloadTrigger, "reloadTrigger");
	}

	/** Produktions-Verdrahtung mit den echten Client-Diensten, wie {@code PackUpdater#forConfigDir}. */
	public static BundleInstaller forConfigDir(Path vitrineConfigDir) {
		return new BundleInstaller(vitrineConfigDir, VitrinePackProvider.forConfigDir(vitrineConfigDir),
				new MinecraftPackActivation(), new MinecraftResourceReloadTrigger());
	}

	/** Ergebnis einer erfolgreichen Bundle-Installation: die Ressourcen-Pack-Profil-IDs aller installierten Teile. */
	public record BundleResult(List<String> installedProfileIds) {

		public BundleResult {
			installedProfileIds = List.copyOf(installedProfileIds);
		}
	}

	/**
	 * Ermittelt fuer jedes Bundle-Teil (per Manifest-ID) die angemeldeten
	 * Faehigkeiten, ohne irgendetwas zu veraendern -- fuer einen
	 * Faehigkeiten-Dialog, der alle Teile eines Bundles auf einmal zeigt.
	 * Reihenfolge wie {@code vpkgFiles}.
	 *
	 * @throws PackInstallException wenn irgendein Teil nicht einmal die
	 *                               Zip-Sicherheits-/Manifest-Pruefung besteht.
	 */
	public Map<String, List<String>> requestedCapabilitiesByManifestId(List<Path> vpkgFiles)
			throws PackInstallException {
		Objects.requireNonNull(vpkgFiles, "vpkgFiles");
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (Path vpkgFile : vpkgFiles) {
			Manifest manifest = PackInstaller.readManifest(vpkgFile);
			result.put(manifest.id(), PackInstaller.capabilityIdsOf(manifest));
		}
		return Collections.unmodifiableMap(result);
	}

	/**
	 * Haelt die Zustimmung des Nutzers zu den angemeldeten Faehigkeiten
	 * <em>aller</em> im Bundle angemeldeten Teile im {@code state.json} fest
	 * (ein einziger Schreibvorgang) und installiert danach das ganze Bundle.
	 *
	 * @param capabilityGrantsByManifestId Manifest-ID -&gt; zugestimmte
	 *                                       Faehigkeits-Bezeichner, fuer jedes
	 *                                       Teil, das ueberhaupt welche anmeldet.
	 */
	public CompletableFuture<BundleResult> confirmCapabilitiesAndInstall(List<Path> vpkgFiles,
			Map<String, ? extends Collection<String>> capabilityGrantsByManifestId) throws PackInstallException {
		Objects.requireNonNull(vpkgFiles, "vpkgFiles");
		Objects.requireNonNull(capabilityGrantsByManifestId, "capabilityGrantsByManifestId");
		try {
			PackStateStore.update(vitrineConfigDir, state -> {
				for (Map.Entry<String, ? extends Collection<String>> entry : capabilityGrantsByManifestId.entrySet()) {
					state.recordCapabilityConsent(entry.getKey(), entry.getValue());
				}
				return null;
			});
		} catch (IOException e) {
			throw new PackInstallException("Faehigkeiten-Zustimmung fuer das Bundle liess sich nicht speichern: " + e,
					e);
		}
		LOGGER.info("Faehigkeiten-Zustimmung fuer Bundle-Pakete {} gespeichert.",
				capabilityGrantsByManifestId.keySet());
		return install(vpkgFiles);
	}

	/**
	 * Installiert {@code vpkgFiles} als eine atomare Einheit. Erwartet, dass
	 * den von jedem Teil angemeldeten Faehigkeiten bereits zugestimmt wurde
	 * (siehe {@link #confirmCapabilitiesAndInstall}).
	 *
	 * <p><b>GATE38:</b> die zurueckgegebene Future schliesst erst ab, wenn der
	 * eine gesammelte Reload durch ist -- erfolgreich mit dem
	 * {@link BundleResult}, sonst mit einer {@link PackInstallException},
	 * nachdem das ganze Bundle zurueckgenommen und ein bereinigender Reload
	 * angestossen wurde. Wer sie auf dem Client-Thread abwartet, blockiert
	 * den Reload gegen sich selbst -- also nicht warten, sondern anhaengen.</p>
	 *
	 * @throws PackInstallException synchron, wenn schon Phase 1--3 scheitert
	 *                               (Pruefung, Kopieren, Zustand) -- dann wurde
	 *                               nichts installiert bzw. alles wieder
	 *                               zurueckgenommen, und es gibt keinen Reload,
	 *                               auf den man warten koennte. Die Meldung
	 *                               nennt den Index und, wenn ermittelbar, die
	 *                               Manifest-ID des ausschlaggebenden Teils.
	 */
	public CompletableFuture<BundleResult> install(List<Path> vpkgFiles) throws PackInstallException {
		Objects.requireNonNull(vpkgFiles, "vpkgFiles");
		if (vpkgFiles.isEmpty()) {
			throw new PackInstallException("Ein Bundle ohne Pakete laesst sich nicht installieren.");
		}

		// --- Phase 1: JEDES Teil vollstaendig pruefen, bevor irgendetwas kopiert wird ---
		List<Manifest> manifests = new ArrayList<>(vpkgFiles.size());
		for (int i = 0; i < vpkgFiles.size(); i++) {
			Path vpkgFile = vpkgFiles.get(i);
			String position = "Bundle-Teil " + (i + 1) + " von " + vpkgFiles.size() + " ('" + vpkgFile + "')";
			Manifest manifest;
			try {
				manifest = PackInstaller.readManifest(vpkgFile);
			} catch (PackInstallException e) {
				throw new PackInstallException(position + " hat die Zip-Sicherheits-/Manifestpruefung nicht "
						+ "bestanden -- das ganze Bundle wird abgelehnt, nichts wurde installiert: " + e.getMessage(),
						e);
			}
			Manifest verified;
			try {
				verified = PackInstaller.verifyDownloadedPackage(vitrineConfigDir, vpkgFile, manifest.id());
			} catch (PackInstallException e) {
				throw new PackInstallException(position + " ('" + manifest.id() + "') hat die Pruefsummen-/"
						+ "Signatur- oder Faehigkeiten-Pruefung nicht bestanden -- das ganze Bundle wird abgelehnt, "
						+ "nichts wurde installiert: " + e.getMessage(), e);
			}
			manifests.add(verified);
		}

		// --- Phase 2: erst JETZT kopieren, alle Teile sind geprueft --------
		Path packsDir = provider.packsDir();
		try {
			Files.createDirectories(packsDir);
		} catch (IOException e) {
			throw new PackInstallException("Zielordner '" + packsDir + "' liess sich nicht anlegen: " + e, e);
		}

		List<CopiedPart> copied = new ArrayList<>(vpkgFiles.size());
		for (int i = 0; i < vpkgFiles.size(); i++) {
			Path source = vpkgFiles.get(i);
			Manifest manifest = manifests.get(i);
			Path target;
			try {
				target = uniqueTargetPath(packsDir, source.getFileName().toString());
				copyAtomically(source, target);
			} catch (IOException e) {
				rollbackCopies(copied);
				throw new PackInstallException("Bundle-Teil " + (i + 1) + " von " + vpkgFiles.size() + " ('"
						+ source + "') liess sich nicht nach '" + packsDir + "' kopieren -- das ganze Bundle wird "
						+ "zurueckgenommen: " + e, e);
			}
			Path sourceSidecar = VpkgIntegritySidecar.sidecarPathFor(source);
			Path targetSidecar = VpkgIntegritySidecar.sidecarPathFor(target);
			if (Files.exists(sourceSidecar)) {
				try {
					copyAtomically(sourceSidecar, targetSidecar);
				} catch (IOException e) {
					deleteQuietly(target);
					rollbackCopies(copied);
					throw new PackInstallException("Integritaets-Beleg von Bundle-Teil " + (i + 1) + " von "
							+ vpkgFiles.size() + " liess sich nicht mitkopieren -- das ganze Bundle wird "
							+ "zurueckgenommen: " + e, e);
				}
			}
			copied.add(new CopiedPart(VitrinePackProvider.profileId(manifest.id()), target, targetSidecar));
		}

		// --- Phase 3: Zustand EINMAL fuer das ganze Bundle fortschreiben ---
		try {
			PackStateStore.update(vitrineConfigDir, state -> {
				for (CopiedPart part : copied) {
					// ROADMAP X3: der Bibliotheksordner ist heute der einzige verdrahtete
					// Weg, aus dem Bundle-Teile kommen -- die Quelle ist damit ehrlich bekannt.
					state.addOrUpdate(part.profileId(), true, PackState.SOURCE_LOCAL_LIBRARY);
				}
				return null;
			});
		} catch (IOException e) {
			rollbackCopies(copied);
			throw new PackInstallException("Zustand liess sich nach dem Kopieren des Bundles nicht speichern -- "
					+ "alles wurde zurueckgenommen: " + e, e);
		}

		// --- Phase 4: aktivieren + EIN gesammelter Reload -------------------
		List<String> profileIds = new ArrayList<>(copied.size());
		for (CopiedPart part : copied) {
			profileIds.add(part.profileId());
		}
		for (String profileId : profileIds) {
			packActivation.activate(profileId);
		}

		// GATE38: ab hier wird NICHT mehr gewartet. Der Reload laeuft auf dem
		// Client-Thread; wer hier join() aufriefe, wuerde ihn im schlimmsten
		// Fall gegen sich selbst blockieren. Stattdessen genau das Muster aus
		// PackUninstaller: handle(...) -> thenCompose(...), der Rueckfall
		// haengt als Fortsetzung an der Reload-Future.
		return startReload(profileIds)
				.handle((ignored, throwable) -> throwable)
				.thenCompose(throwable -> {
					if (throwable == null) {
						LOGGER.info("Bundle mit {} Paket(en) erfolgreich installiert und aktiviert: {}",
								profileIds.size(), profileIds);
						return CompletableFuture.completedFuture(new BundleResult(profileIds));
					}
					return rollBackWholeBundle(profileIds, copied, throwable);
				});
	}

	// --- Hilfsfunktionen -------------------------------------------------

	/**
	 * Stoesst den einen gesammelten Reload an. Wirft {@link
	 * ResourceReloadTrigger#reloadResources()} schon beim Anstossen (statt erst
	 * in der Future), wird daraus eine fehlgeschlagene Future -- damit hat der
	 * Aufrufer genau einen Fehlerweg und keine zwei.
	 */
	private CompletableFuture<Void> startReload(List<String> profileIds) {
		try {
			return reloadTrigger.reloadResources();
		} catch (RuntimeException e) {
			LOGGER.warn("Bundle-Reload fuer {} liess sich nicht einmal anstossen: {}", profileIds, describe(e));
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Rueckfall nach einem fehlgeschlagenen Bundle-Reload: alles deaktivieren,
	 * aus {@code state.json} entfernen, Dateien zuruecknehmen und einen
	 * bereinigenden Reload anstossen -- ebenfalls ohne zu warten. Die
	 * zurueckgegebene Future schliesst erst ab, wenn auch dieser bereinigende
	 * Reload durch ist, und zwar immer mit einer {@link PackInstallException}:
	 * das Bundle ist und bleibt nicht installiert.
	 */
	private CompletableFuture<BundleResult> rollBackWholeBundle(List<String> profileIds, List<CopiedPart> copied,
			Throwable reloadFailure) {
		LOGGER.warn("Reload nach der Bundle-Installation von {} ist fehlgeschlagen ({}) -- das ganze Bundle wird "
				+ "zurueckgenommen.", profileIds, describe(reloadFailure));
		for (String profileId : profileIds) {
			packActivation.deactivate(profileId);
		}
		updateQuietly(freshState -> {
			for (String profileId : profileIds) {
				freshState.remove(profileId);
			}
		});
		rollbackCopies(copied);

		PackInstallException failure = new PackInstallException("Reload nach der Bundle-Installation von "
				+ profileIds + " ist fehlgeschlagen -- das ganze Bundle wurde zurueckgenommen (kein Paket bleibt "
				+ "installiert).");
		return startReload(profileIds)
				.handle((ignored, cleanupFailure) -> {
					if (cleanupFailure != null) {
						LOGGER.error("Bereinigender Reload nach dem Bundle-Rueckfall von {} ist ebenfalls "
								+ "fehlgeschlagen: {} -- das Spiel bleibt im aktuellen Zustand nutzbar.",
								profileIds, describe(cleanupFailure), cleanupFailure);
					}
					return null;
				})
				.thenCompose(ignored -> CompletableFuture.failedFuture(failure));
	}

	/** GATE41: fortschreiben unter der Ordner-Sperre; ein Schreibfehler wird geloggt, nicht geworfen. */
	private void updateQuietly(java.util.function.Consumer<PackState> mutation) {
		try {
			PackStateStore.update(vitrineConfigDir, state -> {
				mutation.accept(state);
				return null;
			});
		} catch (IOException e) {
			LOGGER.error("state.json liess sich nach dem Bundle-Rueckfall nicht schreiben: {}", e.toString(), e);
		}
	}

	private static void rollbackCopies(List<CopiedPart> copied) {
		for (CopiedPart part : copied) {
			deleteQuietly(part.vpkgPath());
			deleteQuietly(part.sidecarPath());
		}
	}

	/** Ein bereits nach {@code packs/} kopiertes Bundle-Teil -- fuer den Rueckfall braucht es Profil-ID und beide Pfade. */
	private record CopiedPart(String profileId, Path vpkgPath, Path sidecarPath) {
	}

	/**
	 * Zielpfad in {@code packsDir} fuer {@code fileName}, der garantiert noch
	 * nicht existiert -- wie {@code PackInstaller#uniqueTargetPath}.
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

	/** Kopiert {@code source} nach {@code target} ueber eine Temp-Datei und {@code ATOMIC_MOVE}, wie {@code PackInstaller#copyAtomically}. */
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
			LOGGER.warn("Aufraeumen von '{}' nach abgebrochener/zurueckgenommener Bundle-Installation fehlgeschlagen: {}",
					path, e.toString());
		}
	}

	private static String describe(Throwable throwable) {
		Throwable actual = (throwable instanceof CompletionException && throwable.getCause() != null)
				? throwable.getCause()
				: throwable;
		String message = actual.getMessage();
		return (message != null && !message.isBlank()) ? message : actual.getClass().getSimpleName();
	}
}
