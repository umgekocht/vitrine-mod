package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.hotapply.HotApplyFailure;
import de.spassglas.vitrine.hotapply.PackActivation;
import de.spassglas.vitrine.hotapply.ResourceReloadTrigger;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ROADMAP H5 ("Deinstallieren und Deaktivieren"): deaktiviert ein Paket
 * (falls aktiv) und wartet den dadurch ausgeloesten Ressourcen-Reload ab,
 * bevor die zugehoerige {@code .vpkg}-Datei aus {@code config/vitrine/packs/}
 * nach {@code config/vitrine/trash/} verschoben und der Paket-Zustand ({@code
 * state.json}, H4) aktualisiert wird.
 *
 * <p><b>Reihenfolge ist der Kern des H5-Fertig-Kriteriums</b> ("ohne Neustart
 * wieder vanilla"): erst {@link PackActivation#deactivate}, dann {@link
 * ResourceReloadTrigger#reloadResources()} abwarten, DANN erst die Datei
 * verschieben und den Zustand aktualisieren. Wuerde die Datei zuerst
 * verschoben, koennte ein laufender Reload (etwa durch eine parallele
 * Aktivierung eines anderen Pakets, siehe {@code HotApplyCoordinator}s
 * gemeinsamer Reload-Stapel) kurzzeitig auf eine nicht mehr vorhandene Datei
 * treffen.</p>
 *
 * <p>Papierkorb-Prinzip statt Loeschen: die Datei landet unter {@code
 * config/vitrine/trash/}, wird nie tatsaechlich geloescht. Kollidiert der
 * Dateiname dort bereits mit einer frueher deinstallierten Datei, haengt
 * {@link #uniqueTrashPath} einen Millisekunden-Zeitstempel an, statt die
 * vorhandene Papierkorb-Datei stillschweigend zu ueberschreiben -- absichtlich
 * kein ausgefeilteres Nummerierungsschema wie bei {@code
 * PackStateStore#uniqueBackupPath}: Kollisionen im Papierkorb sind selten
 * (zwei verschieden deinstallierte Pakete mit exakt demselben Dateinamen),
 * ein Zeitstempel reicht dafuer bereits eindeutig aus.</p>
 *
 * <p>Kennt wie {@link PackActivation}/{@link ResourceReloadTrigger} keine
 * Minecraft-Typen direkt -- die echte Verdrahtung mit dem laufenden Client
 * steckt in {@code PackUninstallService}, austauschbar gegen Test-Doubles.</p>
 */
public final class PackUninstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Uninstall");
	private static final String TRASH_SUBDIR = "trash";

	private final VitrinePackProvider packProvider;
	private final PackActivation packActivation;
	private final ResourceReloadTrigger reloadTrigger;
	/**
	 * ROADMAP T4: fuer ein Klasse-A-Theme-Paket ({@code category: "theme"}) wird
	 * beim Deinstallieren <b>kein</b> Ressourcen-Reload ausgeloest -- nur der
	 * Token-Wechsel zurueck (naechstes aktives Theme-Paket bzw. Standard-Theme).
	 * {@code null}, wenn kein Theme-Pfad verdrahtet ist (reine Unit-Tests).
	 */
	private final ThemePackCoordinator themePacks;

	public PackUninstaller(VitrinePackProvider packProvider, PackActivation packActivation,
			ResourceReloadTrigger reloadTrigger) {
		this(packProvider, packActivation, reloadTrigger, null);
	}

	public PackUninstaller(VitrinePackProvider packProvider, PackActivation packActivation,
			ResourceReloadTrigger reloadTrigger, ThemePackCoordinator themePacks) {
		this.packProvider = Objects.requireNonNull(packProvider, "packProvider");
		this.packActivation = Objects.requireNonNull(packActivation, "packActivation");
		this.reloadTrigger = Objects.requireNonNull(reloadTrigger, "reloadTrigger");
		this.themePacks = themePacks;
	}

	/**
	 * Deinstalliert {@code packId} vollstaendig: deaktiviert es (falls aktiv),
	 * wartet den dadurch angestossenen Reload ab, verschiebt danach die Datei
	 * nach {@code trash/} und entfernt {@code packId} aus {@code state.json}.
	 *
	 * @return eine Future, die abschliesst, sobald alle drei Schritte
	 *         durchgelaufen sind. Schlaegt der Reload selbst fehl, wird die
	 *         Datei trotzdem verschoben (ein deinstalliertes Paket soll auf
	 *         keinen Fall aktivierbar bleiben, nur weil ein unabhaengiger
	 *         Reload-Fehler auftrat) -- die Future schliesst dann trotzdem
	 *         normal ab, der Fehlschlag wird nur geloggt (kein verschluckter
	 *         Fehler, aber auch kein Grund, das Deinstallieren selbst
	 *         scheitern zu lassen).
	 */
	public CompletableFuture<Void> uninstall(String packId) {
		Objects.requireNonNull(packId, "packId");

		boolean themePack = themePacks != null && isThemePack(packId);
		boolean wasActive = packActivation.deactivate(packId);
		LOGGER.info("Vitrine-Paket '{}' vor dem Deinstallieren deaktiviert (Zustand geaendert: {}).",
				packId, wasActive);

		if (themePack) {
			// ROADMAP T4: Klasse-A-Theme -> kein Ressourcen-Reload, nur Datei in
			// den Papierkorb, Zustand aktualisieren, dann Token-Wechsel zurueck.
			LOGGER.info("Theme-Paket '{}' wird deinstalliert: kein Ressourcen-Reload, nur Token-Wechsel zurueck "
					+ "(naechstes aktives Theme bzw. Standard).", packId);
			finishUninstall(packId);
			themePacks.onThemePackDeactivated(packId);
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> reloadFuture;
		try {
			reloadFuture = reloadTrigger.reloadResources();
		} catch (RuntimeException e) {
			LOGGER.warn("Reload vor dem Deinstallieren von '{}' liess sich nicht einmal anstossen ({}); "
					+ "die Datei wird trotzdem verschoben.", packId, e.toString());
			reloadFuture = CompletableFuture.completedFuture(null);
		}

		return reloadFuture
				.handle((ignored, throwable) -> {
					if (throwable != null) {
						LOGGER.warn("Reload beim Deinstallieren von '{}' ist fehlgeschlagen ({}); die Datei wird "
								+ "trotzdem in den Papierkorb verschoben, damit sie nicht aktivierbar bleibt.",
								// ROADMAP GATE11: derselbe geteilte Weg wie im
								// HotApplyCoordinator -- Klassenname und
								// Stapelspur nur hier im Log, nie im Toast.
								packId, HotApplyFailure.technicalDetail(throwable), throwable);
					}
					return null;
				})
				.thenRun(() -> finishUninstall(packId));
	}

	private void finishUninstall(String packId) {
		Path vitrineConfigDir = packProvider.packsDir().getParent();
		moveToTrash(packId, vitrineConfigDir);
		removeFromState(packId, vitrineConfigDir);
	}

	private void moveToTrash(String packId, Path vitrineConfigDir) {
		Optional<Path> found = packProvider.findPackFile(packId);
		if (found.isEmpty()) {
			LOGGER.warn("Vitrine-Paket '{}' hat beim Deinstallieren keine zugehoerige Datei mehr in '{}' "
					+ "gefunden -- schon anderweitig entfernt?", packId, packProvider.packsDir());
			return;
		}

		Path source = found.get();
		Path trashDir = vitrineConfigDir.resolve(TRASH_SUBDIR);
		try {
			Files.createDirectories(trashDir);
			Path target = uniqueTrashPath(trashDir, source.getFileName().toString());
			moveAtomicallyIfPossible(source, target);
			LOGGER.info("Vitrine-Paketdatei '{}' beim Deinstallieren von '{}' nach '{}' verschoben.",
					source, packId, target);
		} catch (IOException e) {
			LOGGER.error("Vitrine-Paketdatei '{}' liess sich beim Deinstallieren von '{}' nicht nach '{}' "
					+ "verschieben: {}", source, packId, trashDir, e.toString(), e);
		}
	}

	/**
	 * Verschiebt {@code source} nach {@code target}, wenn moeglich atomar
	 * (Charter-Regel: atomare Dateioperationen wie bei F2/H4). {@code target}
	 * ist ueber {@link #uniqueTrashPath} bereits als nicht existent
	 * garantiert, ein Fallback auf einen nicht-atomaren Move (nur noetig, wenn
	 * Papierkorb und Paketordner ausnahmsweise auf verschiedenen Dateisystemen
	 * liegen) ueberschreibt deshalb nichts.
	 */
	private static void moveAtomicallyIfPossible(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt (unterschiedliche "
					+ "Dateisysteme?), weiche auf einen gewoehnlichen Move aus: {}", source, target, e.toString());
			Files.move(source, target);
		}
	}

	private static Path uniqueTrashPath(Path trashDir, String fileName) {
		Path candidate = trashDir.resolve(fileName);
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
			stamped = trashDir.resolve(base + "-" + suffix + extension);
			attempt++;
		} while (Files.exists(stamped));
		return stamped;
	}

	private void removeFromState(String packId, Path vitrineConfigDir) {
		if (!PackStateStore.load(vitrineConfigDir).isInstalled(packId)) {
			LOGGER.debug("Vitrine-Paket '{}' war beim Deinstallieren nicht (mehr) im Zustand vermerkt.", packId);
			return;
		}
		try {
			// GATE41: entfernen unter der Ordner-Sperre, damit ein paralleles
			// Installieren/Deinstallieren diese Aenderung nicht ueberschreibt.
			PackStateStore.update(vitrineConfigDir, state -> state.remove(packId));
			LOGGER.info("Vitrine-Paket '{}' aus dem Zustand ({}) entfernt.", packId, vitrineConfigDir);
		} catch (IOException e) {
			LOGGER.error("Zustand nach dem Deinstallieren von '{}' liess sich nicht speichern: {}",
					packId, e.toString(), e);
			throw new UncheckedIOException("Konnte state.json nach dem Deinstallieren von '" + packId
					+ "' nicht speichern", e);
		}
	}

	/** ROADMAP T4: {@code true}, wenn die noch in {@code packs/} liegende Datei von {@code packId} ein Theme-Paket ist. */
	private boolean isThemePack(String packId) {
		return packProvider.findPackFile(packId)
				.map(file -> {
					try (VpkgArchive archive = VpkgArchive.open(file)) {
						return ThemePackCoordinator.isThemePackage(ManifestParser.parse(archive.readManifestBytes()));
					} catch (IOException | ManifestParseException | RuntimeException e) {
						LOGGER.debug("Kategorie von '{}' beim Deinstallieren nicht bestimmbar: {}", packId, e.toString());
						return false;
					}
				})
				.orElse(false);
	}
}
