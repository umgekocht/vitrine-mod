package de.spassglas.vitrine.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Liest und schreibt {@code state.json} im Vitrine-Konfigurationsordner (im
 * echten Spiel {@code config/vitrine/state.json}) -- ROADMAP H4: haelt fest,
 * welche Paket-IDs installiert sind, welche davon aktiv sind, und in welcher
 * Reihenfolge.
 *
 * <p>Exakt dasselbe Muster wie {@code
 * de.spassglas.vitrine.config.SettingsManager} fuer {@code settings.json}:</p>
 * <ul>
 *   <li>Datei fehlt: leerer Default-Zustand wird angelegt und atomar
 *       geschrieben.</li>
 *   <li>Datei ist gueltig, aber in einer aelteren Schema-Version: ueber
 *       {@link PackStateSchemaMigrations} hochziehen, danach die migrierte
 *       Fassung zurueckschreiben.</li>
 *   <li>Datei ist kein gueltiges JSON, hat kein brauchbares
 *       {@code schemaVersion}-Feld, eine Version ohne Migrationsweg, oder
 *       inhaltlich widerspruechliche Paketeintraege (doppelte/fehlende
 *       Paket-IDs): Originaldatei nach {@code state.json.bak} (bzw. einer
 *       eindeutig durchnummerierten Variante) sichern, leeren Zustand
 *       zurueckgeben und schreiben. Es wird nie eine Exception aus dem Laden
 *       nach aussen geworfen -- das deckt insbesondere den Fall ab, dass das
 *       Spiel mitten im Schreiben von {@code state.json} abgestuerzt ist und
 *       nur eine halb geschriebene Datei zurueckblieb (ROADMAP-H4-Fertig-
 *       Kriterium).</li>
 * </ul>
 */
public final class PackStateStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/State");
	private static final String STATE_FILE_NAME = "state.json";
	private static final String BACKUP_SUFFIX = ".bak";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * GATE41: eine Sperre je Konfigurationsordner. Ohne sie lief jedes
	 * Fortschreiben als "laden, aendern, speichern" ohne Schutz -- bei 40
	 * gleichzeitigen Installationen ueberlebte genau eine, die anderen 39
	 * ueberschrieben einander mit ihrem jeweils veralteten Stand.
	 *
	 * <p>Gilt innerhalb eines Prozesses (ein laufendes Minecraft). Zwei Spiele,
	 * die gleichzeitig denselben {@code config/vitrine}-Ordner benutzen, sind
	 * damit nicht abgedeckt -- eine Dateisperre waere dafuer noetig, ist aber
	 * auf Netzlaufwerken unzuverlaessig und fuer den Normalfall (ein Spiel,
	 * ein Profil) nicht noetig.</p>
	 */
	private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

	private PackStateStore() {
	}

	private static ReentrantLock lockFor(Path vitrineConfigDir) {
		return LOCKS.computeIfAbsent(vitrineConfigDir.toAbsolutePath().normalize(), key -> new ReentrantLock());
	}

	/**
	 * Laedt den aktuellen Zustand, wendet {@code mutation} darauf an und
	 * schreibt ihn zurueck -- alles unter der Sperre des Ordners, damit kein
	 * paralleles Fortschreiben dazwischenfunken kann (GATE41). <b>Jede
	 * Aenderung an {@code state.json} soll hierueber laufen</b>, nicht ueber
	 * ein getrenntes {@link #load}/{@link #save}: zwischen den beiden liegt
	 * genau das Fenster, in dem Aenderungen verloren gingen.
	 *
	 * <p>{@code mutation} darf keine langen Arbeiten (Netz, Kopieren) tun -- sie
	 * haelt die Sperre. Pruefungen, die vorher auf einem frisch geladenen
	 * Zustand gemacht wurden, duerfen bleiben; nur das Schreiben muss hier
	 * passieren.</p>
	 *
	 * @return was {@code mutation} zurueckgibt
	 * @throws IOException wenn das Zurueckschreiben scheitert -- {@code state.json}
	 *                     bleibt dann unveraendert (atomarer Austausch)
	 */
	public static <R> R update(Path vitrineConfigDir, Function<PackState, R> mutation) throws IOException {
		ReentrantLock lock = lockFor(vitrineConfigDir);
		lock.lock();
		try {
			PackState state = load(vitrineConfigDir);
			R result = mutation.apply(state);
			save(vitrineConfigDir, state);
			return result;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * GATE120: fuehrt {@code action} unter genau derselben Ordner-Sperre aus
	 * wie {@link #update}/{@link #load}/{@link #save} -- fuer Aufrufer (z. B.
	 * {@code PackInstaller#install}), die einen Lese-Check ("ist diese
	 * Manifest-ID schon installiert?") und eine anschliessende Datei-Operation
	 * (Kopie nach {@code packs/}) als eine atomare Einheit mit der
	 * {@code state.json}-Schreibseite brauchen. Ohne das waere der Check
	 * "schon installiert?" ein check-then-act ohne Schutz: zwei gleichzeitige
	 * Installationsversuche derselben Manifest-ID koennten beide die Pruefung
	 * bestehen und zwei Dateien in {@code packs/} anlegen.
	 *
	 * <p>{@code action} darf selbst {@link #load}, {@link #update} oder
	 * {@link #save} aufrufen -- die Sperre ist ein {@link ReentrantLock}, also
	 * reentrant innerhalb desselben Threads.</p>
	 *
	 * @throws E was immer {@code action} wirft -- wird unveraendert
	 *           durchgereicht, die Sperre wird in jedem Fall freigegeben.
	 */
	public static <R, E extends Exception> R runLocked(Path vitrineConfigDir, LockedAction<R, E> action) throws E {
		ReentrantLock lock = lockFor(vitrineConfigDir);
		lock.lock();
		try {
			return action.run();
		} finally {
			lock.unlock();
		}
	}

	/** Siehe {@link #runLocked}. */
	@FunctionalInterface
	public interface LockedAction<R, E extends Exception> {
		R run() throws E;
	}

	/**
	 * Laedt den Zustand aus {@code vitrineConfigDir/state.json}. Wirft nie --
	 * jeder Fehlerfall faellt auf einen leeren, gueltigen Zustand zurueck,
	 * siehe Klassendokumentation.
	 *
	 * @param vitrineConfigDir der Vitrine-eigene Konfigurationsordner (z. B.
	 *                          {@code config/vitrine}), nicht die Datei selbst.
	 */
	public static PackState load(Path vitrineConfigDir) {
		// Auch das Laden haelt die Sperre: es kann selbst schreiben (Default
		// anlegen, Migration zurueckschreiben, kaputte Datei sichern) und darf
		// dabei keinem laufenden update() in die Quere kommen. ReentrantLock --
		// update() ruft load() unter derselben Sperre auf.
		ReentrantLock lock = lockFor(vitrineConfigDir);
		lock.lock();
		try {
			return loadLocked(vitrineConfigDir);
		} finally {
			lock.unlock();
		}
	}

	private static PackState loadLocked(Path vitrineConfigDir) {
		Path file = vitrineConfigDir.resolve(STATE_FILE_NAME);

		if (!Files.exists(file)) {
			LOGGER.info("Keine {} gefunden, lege leeren Zustand an.", file);
			PackState defaults = PackState.createDefault();
			trySave(vitrineConfigDir, defaults);
			return defaults;
		}

		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Konnte {} nicht lesen, verwende leeren Zustand: {}", file, e.toString(), e);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		JsonObject root;
		try {
			JsonElement parsed = com.google.gson.JsonParser.parseString(content);
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("Wurzelelement ist kein JSON-Objekt");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			// Deckt u. a. den abgeschnittenen JSON-Fall ab, der bei einem
			// Absturz mitten im Schreiben entstehen kann.
			LOGGER.warn("{} enthaelt kein gueltiges JSON-Objekt (moeglicherweise durch einen Absturz mitten im "
					+ "Schreiben abgeschnitten), sichere Datei und verwende leeren Zustand: {}", file, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		Integer storedVersion = readSchemaVersion(root);
		if (storedVersion == null) {
			LOGGER.warn("{} hat kein gueltiges 'schemaVersion'-Feld, sichere Datei und verwende leeren Zustand.", file);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		JsonObject migrated;
		boolean wasMigrated = storedVersion < PackState.CURRENT_SCHEMA_VERSION;
		try {
			migrated = PackStateSchemaMigrations.upgrade(root, storedVersion, PackState.CURRENT_SCHEMA_VERSION,
					PackStateSchemaMigrations.STEPS);
		} catch (IllegalArgumentException | IllegalStateException e) {
			LOGGER.warn("{} hat Schema-Version {}, die dieser Mod nicht lesen kann ({}); "
					+ "sichere Datei und verwende leeren Zustand.", file, storedVersion, e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}

		PackState state;
		try {
			state = GSON.fromJson(migrated, PackState.class);
		} catch (JsonSyntaxException e) {
			LOGGER.warn("{} liess sich nicht auf {} abbilden, sichere Datei und verwende leeren Zustand: {}",
					file, PackState.class.getSimpleName(), e.toString());
			return recoverWithBackup(vitrineConfigDir, file);
		}
		if (state == null) {
			LOGGER.warn("{} ergab keinen Zustand (leeres Dokument), sichere Datei und verwende leeren Zustand.", file);
			return recoverWithBackup(vitrineConfigDir, file);
		}
		if (state.capabilityConsents == null) {
			// Additiv eingeführtes Feld (ROADMAP S4): eine vor der Einführung
			// geschriebene state.json hat es nicht -- leere Liste statt null.
			state.capabilityConsents = new ArrayList<>();
		}
		if (state.packs == null || hasInvalidEntries(state) || state.hasDuplicatePackIds()) {
			LOGGER.warn("{} enthaelt widerspruechliche Paketeintraege (fehlende oder doppelte Paket-IDs), "
					+ "sichere Datei und verwende leeren Zustand.", file);
			return recoverWithBackup(vitrineConfigDir, file);
		}

		state.schemaVersion = PackState.CURRENT_SCHEMA_VERSION;

		if (wasMigrated) {
			LOGGER.info("{} von Schema-Version {} auf {} migriert.", file, storedVersion,
					PackState.CURRENT_SCHEMA_VERSION);
			trySave(vitrineConfigDir, state);
		}

		return state;
	}

	/**
	 * Schreibt {@code state} atomar (temporaere Datei im selben Ordner, dann
	 * {@code ATOMIC_MOVE}) nach {@code vitrineConfigDir/state.json}. Stuerzt
	 * der Prozess zwischen dem Anlegen der temporaeren Datei und dem
	 * abschliessenden {@code move} ab, bleibt hoechstens die temporaere Datei
	 * halb geschrieben liegen -- {@code state.json} selbst wird nie
	 * angefasst, bevor der neue Inhalt vollstaendig geschrieben ist.
	 */
	public static void save(Path vitrineConfigDir, PackState state) throws IOException {
		ReentrantLock lock = lockFor(vitrineConfigDir);
		lock.lock();
		try {
			saveLocked(vitrineConfigDir, state);
		} finally {
			lock.unlock();
		}
	}

	private static void saveLocked(Path vitrineConfigDir, PackState state) throws IOException {
		Files.createDirectories(vitrineConfigDir);
		Path target = vitrineConfigDir.resolve(STATE_FILE_NAME);
		String json = GSON.toJson(state);

		Path tmp = Files.createTempFile(vitrineConfigDir, STATE_FILE_NAME, ".tmp");
		try {
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException cleanupFailure) {
				LOGGER.warn("Konnte temporaere Datei {} nach fehlgeschlagenem Schreiben nicht entfernen: {}",
						tmp, cleanupFailure.toString());
			}
			throw e;
		}
	}

	private static void trySave(Path vitrineConfigDir, PackState state) {
		try {
			save(vitrineConfigDir, state);
		} catch (IOException e) {
			LOGGER.error("Konnte Zustand nicht nach {} schreiben: {}", vitrineConfigDir, e.toString(), e);
		}
	}

	private static boolean hasInvalidEntries(PackState state) {
		for (PackState.PackEntry entry : state.packs) {
			if (entry == null || entry.packId == null || entry.packId.isBlank()) {
				return true;
			}
		}
		return false;
	}

	private static Integer readSchemaVersion(JsonObject root) {
		if (!root.has("schemaVersion")) {
			return null;
		}
		JsonElement element = root.get("schemaVersion");
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		if (value != Math.floor(value) || Double.isInfinite(value) || value < 0) {
			return null;
		}
		return (int) value;
	}

	/**
	 * Sichert eine kaputte Datei unter einem eindeutigen Namen und liefert
	 * einen frischen, leeren Zustand zurueck (der zusaetzlich sofort als neue
	 * {@code state.json} geschrieben wird).
	 */
	private static PackState recoverWithBackup(Path vitrineConfigDir, Path brokenFile) {
		try {
			Path backup = uniqueBackupPath(brokenFile);
			Files.move(brokenFile, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Kaputte Zustandsdatei nach {} gesichert.", backup);
		} catch (IOException e) {
			LOGGER.error("Konnte kaputte Zustandsdatei {} nicht sichern: {}", brokenFile, e.toString(), e);
		}

		PackState defaults = PackState.createDefault();
		trySave(vitrineConfigDir, defaults);
		return defaults;
	}

	private static Path uniqueBackupPath(Path original) {
		Path parent = original.getParent();
		String fileName = original.getFileName().toString();
		Path candidate = parent.resolve(fileName + BACKUP_SUFFIX);
		int counter = 1;
		while (Files.exists(candidate)) {
			candidate = parent.resolve(fileName + BACKUP_SUFFIX + "." + counter);
			counter++;
		}
		return candidate;
	}
}
