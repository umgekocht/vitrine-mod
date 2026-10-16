package de.spassglas.vitrine.ui.downloads;

import de.spassglas.vitrine.catalog.download.CancellationToken;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Client-freies Modell des Download-Flyouts (ROADMAP S8, docs/UI-SPEC.md
 * "1. Kopfleiste": "Flyout mit laufenden und zuletzt abgeschlossenen
 * Installationen, je mit Fortschrittsbalken, Groesse und Abbrechen") -- analog
 * zu {@code LibraryModel}/{@code SearchModel}: keine einzige Minecraft-Klasse,
 * damit die Trenn-, Zaehl- und Abbruch-Logik headless pruefbar ist.
 *
 * <p>Zwei Listen: {@link #running()} (laufende Installationen, in Startreihenfolge)
 * und {@link #recent()} (zuletzt abgeschlossene, neueste zuerst, auf
 * {@code recentCapacity} begrenzt). Der Knopf-Zaehler
 * ({@code vitrine.header.downloads.count}) zeigt {@link #runningCount()} --
 * nur Laufende.</p>
 *
 * <p><b>ROADMAP GATE4/GATE9/GATE13:</b> {@link #startDownload(String, String, long, CancellationToken)}
 * haelt zusaetzlich das {@link CancellationToken} des laufenden Downloads --
 * {@link #cancel(String)} bricht damit den echten Download ab, nicht nur die
 * Anzeige-Zeile. {@link #updateProgress(String, long, long)} ist der
 * Fortschritts-Rueckruf aus {@code PackageDownloader}: sobald er einmal
 * aufgerufen wurde, meldet {@link #realProgressAvailable()} {@code true} und
 * die Oberflaeche zeigt den echten, gezaehlten Balken statt des Seam-Hinweises
 * (Charter-Regel 2 -- ein frisches, noch nie benutztes Modell bleibt ehrlich
 * bei {@code false}).</p>
 *
 * <p>Alle oeffentlichen Methoden sind {@code synchronized}: der Fortschritts-
 * Rueckruf kommt vom Download-Hintergrundthread, waehrend der Store ihn auf
 * dem Client-Thread jeden Frame liest (ROADMAP GATE9, "Installation laeuft
 * asynchron").</p>
 */
public final class DownloadFlyoutModel {

	/**
	 * ROADMAP GATE12: Lang-Key des Hinweises, der erscheint, solange noch
	 * keine echten Byte-Zahlen gemeldet wurden (lokale Installation, oder ein
	 * Download, der gerade erst angestossen ist). Der frueher hier genannte
	 * interne Bezeichner „K3" stand woertlich im sichtbaren Text und war seit
	 * dem echten Fortschritt (GATE4) auch sachlich falsch.
	 */
	public static final String UNKNOWN_PROGRESS_NOTE_KEY = "vitrine.downloads.progress.unknown_note";
	/** Lang-Key fuer den unbestimmten Fortschritt einer laufenden Zeile. */
	public static final String INDETERMINATE_KEY = "vitrine.downloads.progress.indeterminate";
	/** Lang-Key des Leer-Zustands. */
	public static final String EMPTY_KEY = "vitrine.downloads.flyout.empty";

	/** Standardanzahl der im "Zuletzt abgeschlossen"-Abschnitt behaltenen Eintraege. */
	public static final int DEFAULT_RECENT_CAPACITY = 5;

	private final int recentCapacity;
	private final Map<String, DownloadEntry> running = new LinkedHashMap<>();
	private final Deque<DownloadEntry> recent = new ArrayDeque<>();
	/** Laufende Downloads, deren Abbrechen-Knopf einen echten Netz-Transfer beenden soll (ROADMAP GATE9). */
	private final Map<String, CancellationToken> cancellationTokens = new LinkedHashMap<>();
	/** {@code true}, sobald {@link #updateProgress} mindestens einmal echte Bytes gemeldet hat. */
	private boolean hasRealProgress;

	public DownloadFlyoutModel() {
		this(DEFAULT_RECENT_CAPACITY);
	}

	public DownloadFlyoutModel(int recentCapacity) {
		if (recentCapacity < 1) {
			throw new IllegalArgumentException("recentCapacity muss mindestens 1 sein: " + recentCapacity);
		}
		this.recentCapacity = recentCapacity;
	}

	// --- Vorgaenge melden ------------------------------------------------

	/**
	 * Meldet den Start einer Installation. Ein bereits laufender Eintrag mit
	 * derselben {@code packId} wird ersetzt (erneuter Anlauf). Ohne
	 * {@link CancellationToken} -- der Abbrechen-Knopf entfernt dann nur die
	 * Anzeige-Zeile, siehe {@link #startDownload(String, String, long, CancellationToken)}.
	 */
	public synchronized void startDownload(String packId, String displayName, long sizeBytes) {
		startDownload(packId, displayName, sizeBytes, null);
	}

	/**
	 * Wie {@link #startDownload(String, String, long)}, aber mit dem echten
	 * {@link CancellationToken} des laufenden Downloads (ROADMAP GATE9): ein
	 * Klick auf "Abbrechen" in dieser Zeile ruft {@link CancellationToken#cancel()}
	 * auf, statt nur die Anzeige zu bewegen.
	 */
	public synchronized void startDownload(String packId, String displayName, long sizeBytes,
			CancellationToken cancellationToken) {
		Objects.requireNonNull(packId, "packId");
		running.put(packId, DownloadEntry.starting(packId, displayName, sizeBytes));
		if (cancellationToken != null) {
			cancellationTokens.put(packId, cancellationToken);
		} else {
			cancellationTokens.remove(packId);
		}
	}

	/** Verschiebt {@code packId} als {@link DownloadPhase#DONE} in "Zuletzt abgeschlossen". */
	public synchronized void markDone(String packId) {
		finish(packId, DownloadPhase.DONE);
	}

	/** Verschiebt {@code packId} als {@link DownloadPhase#FAILED} in "Zuletzt abgeschlossen". */
	public synchronized void markFailed(String packId) {
		finish(packId, DownloadPhase.FAILED);
	}

	/**
	 * Verschiebt {@code packId} als {@link DownloadPhase#CANCELLED} in "Zuletzt
	 * abgeschlossen" -- wie {@link #cancel(String)}, aber ohne das
	 * {@link CancellationToken} erneut anzustossen (der Abbruch ist bereits
	 * anderweitig geschehen, z. B. weil der Nutzer schon auf "Abbrechen"
	 * geklickt hatte, bevor der Installationsversuch selbst das erkannte).
	 * Ein Aufruf ohne laufenden Eintrag ist ein sicheres No-op.
	 */
	public synchronized void markCancelled(String packId) {
		finish(packId, DownloadPhase.CANCELLED);
	}

	/**
	 * Entfernt eine laufende Zeile stillschweigend, ohne sie als abgeschlossen,
	 * abgebrochen oder fehlgeschlagen zu vermerken -- fuer den Fall, dass ein
	 * Katalog-Download fertig und verifiziert ist, aber noch auf die
	 * Faehigkeiten-Zustimmung wartet ({@code CatalogConsentRequiredException}):
	 * kein Fehler, aber auch kein sichtbares "erledigt". Ein Aufruf ohne
	 * laufenden Eintrag ist ein sicheres No-op.
	 */
	public synchronized void discardRunning(String packId) {
		running.remove(packId);
		cancellationTokens.remove(packId);
	}

	/**
	 * Abbrechen-Aktion einer Flyout-Zeile (docs/UI-SPEC.md "1. Kopfleiste"):
	 * <ul>
	 *   <li>Laeuft {@code packId} gerade, wird ein evtl. hinterlegtes
	 *       {@link CancellationToken} sofort abgebrochen (ROADMAP GATE9 -- ein
	 *       echter Netz-Transfer wird beendet, nicht nur die Anzeige) und der
	 *       Eintrag wandert als {@link DownloadPhase#CANCELLED} in "Zuletzt
	 *       abgeschlossen".</li>
	 *   <li>Steht {@code packId} schon dort (abgeschlossen/abgebrochen/
	 *       fehlgeschlagen), entfernt der Klick die Zeile aus der Liste.</li>
	 * </ul>
	 *
	 * @return {@code true}, wenn eine Zeile bewegt oder entfernt wurde
	 */
	public synchronized boolean cancel(String packId) {
		if (running.containsKey(packId)) {
			CancellationToken token = cancellationTokens.get(packId);
			if (token != null) {
				token.cancel();
			}
			finish(packId, DownloadPhase.CANCELLED);
			return true;
		}
		return recent.removeIf(e -> e.packId().equals(packId));
	}

	/**
	 * Echter Fortschritts-Rueckruf (ROADMAP GATE4, {@code DownloadProgress}):
	 * aktualisiert den Anteil einer laufenden Zeile mit tatsaechlich gezaehlten
	 * Bytes. Ohne bekannte {@code totalBytes} (Katalog-Antwort ohne Groesse)
	 * bleibt der Anteil unbestimmt, aber {@link #realProgressAvailable()} wird
	 * trotzdem {@code true} -- der Rueckruf selbst ist bereits echt (Charter-
	 * Regel 2), nur die Prozentanzeige fehlt. Ein Aufruf ohne (mehr) laufenden
	 * Eintrag (z. B. nach einem Abbruch) ist ein sicheres No-op.
	 */
	public synchronized void updateProgress(String packId, long bytesRead, long totalBytes) {
		Objects.requireNonNull(packId, "packId");
		hasRealProgress = true;
		DownloadEntry entry = running.get(packId);
		if (entry == null) {
			return;
		}
		long sizeBytes = totalBytes >= 0 ? totalBytes : entry.sizeBytes();
		OptionalDouble progress = totalBytes > 0
				? OptionalDouble.of(Math.max(0.0, Math.min(1.0, bytesRead / (double) totalBytes)))
				: OptionalDouble.empty();
		running.put(packId, new DownloadEntry(packId, entry.displayName(), sizeBytes, entry.phase(), progress));
	}

	private void finish(String packId, DownloadPhase phase) {
		cancellationTokens.remove(packId);
		DownloadEntry entry = running.remove(packId);
		if (entry == null) {
			return;
		}
		recent.addFirst(entry.withPhase(phase));
		while (recent.size() > recentCapacity) {
			recent.removeLast();
		}
	}

	// --- Lesen ---------------------------------------------------------

	/** Laufende Installationen, in Startreihenfolge (aeltester Start zuerst). */
	public synchronized List<DownloadEntry> running() {
		return List.copyOf(running.values());
	}

	/** Zuletzt abgeschlossene Installationen, neueste zuerst, auf die Kapazitaet begrenzt. */
	public synchronized List<DownloadEntry> recent() {
		return List.copyOf(recent);
	}

	/** Alle Zeilen des Flyouts: erst laufende, dann zuletzt abgeschlossene. */
	public synchronized List<DownloadEntry> allRows() {
		List<DownloadEntry> rows = new ArrayList<>(running.size() + recent.size());
		rows.addAll(running.values());
		rows.addAll(recent);
		return List.copyOf(rows);
	}

	/** Zaehler am Kopfleisten-Knopf ({@code vitrine.header.downloads.count}) -- nur Laufende. */
	public synchronized int runningCount() {
		return running.size();
	}

	/** {@code true}, wenn weder etwas laeuft noch etwas kuerzlich abgeschlossen wurde -> Leer-Zustand. */
	public synchronized boolean isEmpty() {
		return running.isEmpty() && recent.isEmpty();
	}

	/**
	 * {@code false}, solange {@link #updateProgress} noch nie aufgerufen wurde
	 * -- ein frisches Modell erzeugt keinen erfundenen Fortschritt (Charter-
	 * Regel 2). Sobald der echte Downloader (ROADMAP K3/GATE4) einmal einen
	 * Fortschritts-Ruf gemacht hat, bleibt es dauerhaft {@code true}. Die
	 * Oberflaeche zeigt bis dahin {@link #UNKNOWN_PROGRESS_NOTE_KEY}.
	 */
	public synchronized boolean realProgressAvailable() {
		return hasRealProgress;
	}

	/** Lang-Key des Hinweises fuer noch unbestimmten Fortschritt (ROADMAP GATE12). */
	public String progressNoteKey() {
		return UNKNOWN_PROGRESS_NOTE_KEY;
	}
}
