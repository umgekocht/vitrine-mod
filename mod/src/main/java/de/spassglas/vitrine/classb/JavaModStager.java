package de.spassglas.vitrine.classb;

import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.VpkgIntegrityException;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * ROADMAP J2: client-freier Kern des Klasse-B-Ablaufs -- Staging einer
 * {@code .jar} nach {@code mods/}, Pruefsumme, absturzsichere
 * {@code staged-mods.json}, Ruecknahme <em>vor</em> dem Neustart, und der
 * Sammelknopf "Jetzt neu starten" hinter einem Seam.
 *
 * <p><b>{@link #stage} -- die Reihenfolge ist Charter-Regel 3
 * ("Sicherheit vor Bequemlichkeit"):</b></p>
 * <ol>
 *   <li>{@link JarSource#fetch} holt die Jar an einen frisch angelegten,
 *       ausschliesslich diesem Aufruf gehoerenden temporaeren Ort -- nicht nach
 *       {@code mods/}.</li>
 *   <li><b>SHA-256 der temporaeren Datei gegen die erwartete Pruefsumme aus dem
 *       Angebot.</b> Weicht sie ab: {@link JavaModStagingException}, die
 *       temporaere Datei ist weg, in {@code mods/} liegt nichts, kein Eintrag in
 *       {@code staged-mods.json}. Kein "vorlaeufiges" Kopieren.</li>
 *   <li><b>Ed25519-Signatur ist Pflicht (GATE33):</b> ein Angebot ohne
 *       Signatur wird abgelehnt; eine vorhandene Signatur wird ausschliesslich
 *       gegen den im Jar eingebetteten, vertrauten Registry-Schluessel
 *       ({@link EmbeddedSigningKey#load()}) geprueft -- <b>nie</b> gegen einen
 *       vom Angebot selbst mitgebrachten Schluessel (kein Vertrauensanker,
 *       ein Angreifer koennte sich sonst mit seinem eigenen Schluessel selbst
 *       "gueltig" signieren).</li>
 *   <li>{@link InstalledModConflictCheck#check} -- Andock-Seam fuer J3, hier
 *       {@link InstalledModConflictCheck#NONE}.</li>
 *   <li>{@code ATOMIC_MOVE} nach {@code <modsDir>/<datei>.jar}. Existiert dort
 *       schon eine gleichnamige Datei: {@link JavaModStagingException}, das
 *       Original bleibt unangetastet (nie ueberschreiben).</li>
 *   <li>Eintrag in {@code staged-mods.json} ({@code pendingRestart=true}).
 *       Scheitert das Schreiben, wird die eben gestagete Jar wieder aus
 *       {@code mods/} in den Papierkorb geholt -- keine untracked Jar.</li>
 * </ol>
 *
 * <p><b>{@link #unstage} -- Ruecknahme vor dem Neustart:</b> die gestagete Jar
 * wird aus {@code mods/} nach {@code config/vitrine/trash/} verschoben (Muster
 * wie H5) und der {@code staged-mods.json}-Eintrag entfernt. Nur erlaubt,
 * solange {@link StagedMod#pendingRestart()} -- nach einem echten Neustart
 * (siehe {@link #markAllPendingAsLoaded()}) waere die Jar schon geladen und
 * eine Ruecknahme gelogen.</p>
 *
 * <p>J2 fuehrt <b>keine</b> Jar aus und stoesst keinen echten Neustart an --
 * {@link #restartNow()} ruft nur den {@link RestartAction}-Seam.</p>
 */
public final class JavaModStager {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/ClassB");

	private static final String MODS_SUBDIR = "mods";
	private static final String CONFIG_SUBDIR = "config";
	private static final String VITRINE_SUBDIR = "vitrine";
	private static final String TRASH_SUBDIR = "trash";
	private static final String STAGING_TMP_PREFIX = "javamod-stage-";

	private final Path modsDir;
	private final Path stagingStateDir;
	private final Path trashDir;
	private final JarSource jarSource;
	private final RestartAction restartAction;
	private final InstalledModConflictCheck conflictCheck;

	public JavaModStager(Path modsDir, Path stagingStateDir, Path trashDir, JarSource jarSource,
			RestartAction restartAction, InstalledModConflictCheck conflictCheck) {
		this.modsDir = Objects.requireNonNull(modsDir, "modsDir");
		this.stagingStateDir = Objects.requireNonNull(stagingStateDir, "stagingStateDir");
		this.trashDir = Objects.requireNonNull(trashDir, "trashDir");
		this.jarSource = Objects.requireNonNull(jarSource, "jarSource");
		this.restartAction = Objects.requireNonNull(restartAction, "restartAction");
		this.conflictCheck = Objects.requireNonNull(conflictCheck, "conflictCheck");
	}

	/**
	 * Produktions-naher Aufbau ueber ein Spielverzeichnis: {@code <gameDir>/mods}
	 * als Ziel, {@code <gameDir>/config/vitrine} fuer {@code staged-mods.json},
	 * {@code <gameDir>/config/vitrine/trash} als Papierkorb. Im echten Client
	 * kommt {@code gameDir} aus
	 * {@code FabricLoader.getInstance().getGameDir()}; in Tests injizierbar.
	 *
	 * <p>GATE33: es gibt bewusst keinen Signaturschluessel-Parameter mehr --
	 * {@link #stage} prueft ausschliesslich gegen {@link
	 * EmbeddedSigningKey#load()}, es gibt keinen zweiten, injizierbaren
	 * Vertrauensanker, der versehentlich {@code null} oder ein fremder
	 * Schluessel sein koennte.</p>
	 */
	public static JavaModStager forGameDir(Path gameDir, JarSource jarSource, RestartAction restartAction,
			InstalledModConflictCheck conflictCheck) {
		Objects.requireNonNull(gameDir, "gameDir");
		Path vitrineDir = gameDir.resolve(CONFIG_SUBDIR).resolve(VITRINE_SUBDIR);
		return new JavaModStager(gameDir.resolve(MODS_SUBDIR), vitrineDir, vitrineDir.resolve(TRASH_SUBDIR),
				jarSource, restartAction, conflictCheck);
	}

	// --- stage ---------------------------------------------------------------

	/**
	 * Bezieht die Jar von {@code offer}, prueft die Pruefsumme (und ggf.
	 * Signatur) <b>vor</b> dem Staging, verschiebt sie dann atomar nach
	 * {@code mods/} und traegt sie in {@code staged-mods.json} ein.
	 *
	 * @return der geschriebene {@link StagedMod}-Eintrag ({@code pendingRestart=true}).
	 * @throws JavaModStagingException bei jedem Fehlschlag -- bei einer
	 *                                 Pruefsummen-Abweichung liegt garantiert
	 *                                 nichts in {@code mods/} und kein Eintrag in
	 *                                 {@code staged-mods.json}.
	 */
	public StagedMod stage(JavaModOffer offer) throws JavaModStagingException {
		Objects.requireNonNull(offer, "offer");

		List<StagedMod> staged = StagedModsStore.load(stagingStateDir);
		if (staged.stream().anyMatch(m -> m.id().equals(offer.id()))) {
			throw new JavaModStagingException("'" + offer.id() + "' ist bereits gestaged -- erst zuruecknehmen "
					+ "oder neu starten.", "vitrine.classb.error.already_staged", offer.id());
		}
		Path target = modsDir.resolve(offer.fileName());
		if (Files.exists(target)) {
			throw new JavaModStagingException("In '" + modsDir + "' liegt bereits eine Datei namens '"
					+ offer.fileName() + "' -- sie wird nicht ueberschrieben (Charter-Regel 3).",
					"vitrine.classb.error.target_exists", offer.fileName());
		}

		Path tempDir;
		try {
			Files.createDirectories(stagingStateDir);
			tempDir = Files.createTempDirectory(stagingStateDir, STAGING_TMP_PREFIX);
		} catch (IOException e) {
			throw new JavaModStagingException("Temporaerer Staging-Ordner liess sich nicht anlegen: " + e.getMessage(),
					e, "vitrine.classb.error.staging_dir_failed");
		}

		try {
			// --- 1. Jar an den temporaeren Ort holen ----------------------
			Path fetched;
			try {
				fetched = jarSource.fetch(offer, tempDir);
			} catch (IOException e) {
				throw new JavaModStagingException("Jar fuer '" + offer.id() + "' liess sich nicht beziehen: "
						+ e.getMessage(), e, "vitrine.classb.error.fetch_failed", offer.id());
			}
			Objects.requireNonNull(fetched, "JarSource.fetch lieferte null");
			Path normalized = fetched.toAbsolutePath().normalize();
			if (!Files.isRegularFile(normalized) || !normalized.startsWith(tempDir.toAbsolutePath().normalize())) {
				throw new JavaModStagingException("JarSource lieferte '" + fetched + "' -- keine regulaere Datei "
						+ "innerhalb des Staging-Ordners.", "vitrine.classb.error.fetch_invalid_location", offer.id());
			}

			// --- 2. SHA-256 VOR dem Staging -------------------------------
			String actualSha;
			try {
				actualSha = VpkgIntegrityValidator.sha256Hex(normalized);
			} catch (IOException e) {
				throw new JavaModStagingException("Pruefsumme der bezogenen Jar liess sich nicht berechnen: "
						+ e.getMessage(), e, "vitrine.classb.error.checksum_calc_failed");
			}
			String expectedSha = offer.expectedSha256Hex().strip();
			if (!actualSha.equalsIgnoreCase(expectedSha)) {
				throw new JavaModStagingException("SHA-256 der Jar fuer '" + offer.id() + "' stimmt nicht: erwartet '"
						+ expectedSha + "', tatsaechlich '" + actualSha + "'. Nichts wird nach '" + modsDir
						+ "' gelegt.", "vitrine.classb.error.checksum_mismatch", offer.id());
			}

			// --- 3. Signatur ist Pflicht (GATE33, Charter-Regel 3/4) -----
			// Kein Vertrauensanker aus dem Angebot selbst: ausschliesslich der
			// im Jar eingebettete Registry-Schluessel gilt, nie ein vom Angebot
			// mitgebrachter Schluessel.
			if (!offer.hasSignature()) {
				throw new JavaModStagingException("Angebot '" + offer.id() + "' bringt keine Ed25519-Signatur mit -- "
						+ "Klasse-B-Jars werden nur mit gueltiger Signatur gegen den eingebetteten "
						+ "Registry-Schluessel gestaged (Charter-Regel 3/4).",
						"vitrine.classb.error.missing_signature", offer.id());
			}
			try {
				VpkgIntegrityValidator.requireValid(normalized, expectedSha, offer.signatureBytes(),
						EmbeddedSigningKey.load());
			} catch (VpkgIntegrityException e) {
				throw new JavaModStagingException("Signaturpruefung der Jar fuer '" + offer.id()
						+ "' fehlgeschlagen: " + e.getMessage(), e, "vitrine.classb.error.signature_invalid", offer.id());
			}

			// --- 4. J3-Andock-Seam (hier NONE) ------------------------
			conflictCheck.check(offer);

			// --- 5. ATOMIC_MOVE nach mods/, nie ueberschreiben ---------
			if (Files.exists(target)) {
				throw new JavaModStagingException("In '" + modsDir + "' ist zwischenzeitlich eine Datei namens '"
						+ offer.fileName() + "' aufgetaucht -- sie wird nicht ueberschrieben.",
						"vitrine.classb.error.target_exists", offer.fileName());
			}
			try {
				Files.createDirectories(modsDir);
				moveNoOverwrite(normalized, target);
			} catch (IOException e) {
				deleteQuietly(target);
				throw new JavaModStagingException("Jar liess sich nicht nach '" + modsDir + "' verschieben: "
						+ e.getMessage(), e, "vitrine.classb.error.move_failed", offer.fileName());
			}

			// --- 6. staged-mods.json fortschreiben --------------------
			StagedMod entry = new StagedMod(offer.id(), offer.fileName(), actualSha.toLowerCase(Locale.ROOT),
					offer.author(), offer.displayName(), Instant.now().toString(), true);
			staged.add(entry);
			try {
				StagedModsStore.save(stagingStateDir, staged);
			} catch (IOException e) {
				// Kein untracked Jar zuruecklassen: die eben gestagete Datei in
				// den Papierkorb holen, dann Klartextfehler.
				try {
					moveToTrash(target);
				} catch (IOException rollbackFailure) {
					LOGGER.error("staged-mods.json-Schreiben fuer '{}' fehlgeschlagen UND die Jar liess sich nicht "
							+ "aus '{}' zuruecknehmen: {}", offer.id(), modsDir, rollbackFailure.toString());
				}
				throw new JavaModStagingException("Staging-Zustand liess sich nicht speichern, die Jar wurde wieder "
						+ "aus '" + modsDir + "' entfernt: " + e.getMessage(), e,
						"vitrine.classb.error.save_state_failed");
			}

			LOGGER.info("Klasse-B-Jar '{}' ({}) nach '{}' gestaget -- Neustart noetig.", offer.id(), offer.fileName(),
					target);
			return entry;
		} finally {
			deleteTreeQuietly(tempDir);
		}
	}

	// --- unstage ----------------------------------------------------------

	/**
	 * Ruecknahme vor dem Neustart: verschiebt die gestagete Jar von {@code id}
	 * aus {@code mods/} nach {@code config/vitrine/trash/} und entfernt den
	 * {@code staged-mods.json}-Eintrag. Loest <b>keinen</b> Neustart aus.
	 *
	 * @throws JavaModStagingException wenn {@code id} nicht gestaget ist oder
	 *                                 bereits als "nach dem Neustart geladen"
	 *                                 markiert wurde (dann waere die Ruecknahme
	 *                                 gelogen -- die Jar ist schon in der JVM).
	 */
	public void unstage(String id) throws JavaModStagingException {
		Objects.requireNonNull(id, "id");
		List<StagedMod> staged = StagedModsStore.load(stagingStateDir);
		StagedMod entry = staged.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
		if (entry == null) {
			throw new JavaModStagingException("Kein gestagetes Klasse-B-Jar mit id '" + id + "'.",
					"vitrine.classb.error.not_staged", id);
		}
		if (!entry.pendingRestart()) {
			throw new JavaModStagingException("'" + id + "' wurde nach einem Neustart bereits geladen -- eine "
					+ "Ruecknahme ist jetzt nicht mehr moeglich (die Jar laeuft schon).",
					"vitrine.classb.error.already_loaded", id);
		}

		Path jar = modsDir.resolve(entry.fileName());
		try {
			if (Files.exists(jar)) {
				moveToTrash(jar);
			} else {
				LOGGER.warn("Gestagete Jar '{}' fuer '{}' war beim Zuruecknehmen nicht mehr in '{}' -- "
						+ "Eintrag wird trotzdem entfernt.", entry.fileName(), id, modsDir);
			}
		} catch (IOException e) {
			throw new JavaModStagingException("Gestagete Jar '" + entry.fileName() + "' liess sich nicht in den "
					+ "Papierkorb verschieben: " + e.getMessage(), e, "vitrine.classb.error.trash_move_failed",
					entry.fileName());
		}

		staged.removeIf(m -> m.id().equals(id));
		try {
			StagedModsStore.save(stagingStateDir, staged);
		} catch (IOException e) {
			throw new JavaModStagingException("Staging-Zustand liess sich nach der Ruecknahme nicht speichern: "
					+ e.getMessage(), e, "vitrine.classb.error.save_state_failed");
		}
		LOGGER.info("Klasse-B-Jar '{}' vor dem Neustart zurueckgenommen -- '{}' in den Papierkorb verschoben.",
				id, entry.fileName());
	}

	// --- Abfragen / Neustart -------------------------------------------

	/** Alle gestageten Jars (auch bereits geladene), in Eintragsreihenfolge -- unveraenderlich. */
	public List<StagedMod> stagedMods() {
		return List.copyOf(StagedModsStore.load(stagingStateDir));
	}

	/**
	 * Die Jars, die noch auf einen Neustart warten -- Grundlage fuer das
	 * "Neustart noetig"-Band und den Sammelknopf.
	 */
	public List<StagedMod> pendingRestarts() {
		return StagedModsStore.load(stagingStateDir).stream().filter(StagedMod::pendingRestart).toList();
	}

	/**
	 * Client-Einstieg meldet nach einem echten Neustart: alle bislang
	 * ausstehenden Jars sind jetzt geladen. Danach lehnt {@link #unstage} sie
	 * ehrlich ab. Idempotent; schreibt nur, wenn sich etwas aendert.
	 */
	public void markAllPendingAsLoaded() {
		List<StagedMod> staged = StagedModsStore.load(stagingStateDir);
		boolean changed = false;
		List<StagedMod> updated = new ArrayList<>(staged.size());
		for (StagedMod m : staged) {
			if (m.pendingRestart()) {
				updated.add(m.asLoaded());
				changed = true;
			} else {
				updated.add(m);
			}
		}
		if (!changed) {
			return;
		}
		try {
			StagedModsStore.save(stagingStateDir, updated);
			LOGGER.info("{} gestagete Klasse-B-Jar(s) nach dem Neustart als geladen markiert.",
					updated.stream().filter(m -> !m.pendingRestart()).count());
		} catch (IOException e) {
			LOGGER.error("Konnte staged-mods.json nach dem Neustart nicht aktualisieren: {}", e.toString(), e);
		}
	}

	/** Sammelknopf "Jetzt neu starten": ruft den {@link RestartAction}-Seam genau einmal. */
	public void restartNow() {
		LOGGER.info("Sammelknopf 'Jetzt neu starten' -- {} ausstehende Klasse-B-Jar(s).", pendingRestarts().size());
		restartAction.restart();
	}

	// --- Hilfsfunktionen ---------------------------------------------

	private void moveToTrash(Path source) throws IOException {
		Files.createDirectories(trashDir);
		Path target = uniqueTrashPath(trashDir, source.getFileName().toString());
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt ({}), gewoehnlicher Move.",
					source, target, e.toString());
			Files.move(source, target);
		}
		LOGGER.info("'{}' nach '{}' verschoben (Papierkorb).", source, target);
	}

	private static void moveNoOverwrite(Path source, Path target) throws IOException {
		// target ist zuvor als nicht existent geprueft -- kein REPLACE_EXISTING.
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt ({}), gewoehnlicher Move.",
					source, target, e.toString());
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

	private static void deleteTreeQuietly(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException | UncheckedIOException e) {
			LOGGER.warn("Aufraeumen des Staging-Ordners '{}' fehlgeschlagen: {}", dir, e.toString());
		}
	}

	/** Nur fuer Tests sichtbar: der Zielordner {@code mods/}. */
	Path modsDirForTest() {
		return modsDir;
	}

	/** Nur fuer Tests sichtbar: der Papierkorb. */
	Path trashDirForTest() {
		return trashDir;
	}
}
