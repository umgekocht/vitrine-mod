package de.spassglas.vitrine.catalog.download;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Ergebnis eines nicht-fehlgeschlagenen Downloads (ROADMAP K3). Ein Fehler wird
 * als {@link DownloadException} geworfen; hier bleiben die zwei geordneten
 * Ausgaenge:
 * <ul>
 *   <li>{@link Status#COMPLETED} -- Datei liegt verifiziert unter {@link #file()}.</li>
 *   <li>{@link Status#CANCELLED} -- der Nutzer hat abgebrochen; es liegt
 *       <strong>weder</strong> eine {@code .part} <strong>noch</strong> eine
 *       fertige Datei (Fertig-Kriterium der Zeile). {@link #file()} ist
 *       {@code null}.</li>
 * </ul>
 */
public final class DownloadOutcome {

	public enum Status {
		COMPLETED,
		CANCELLED
	}

	private final Status status;
	private final Path file;
	private final boolean alreadyPresent;

	private DownloadOutcome(Status status, Path file, boolean alreadyPresent) {
		this.status = Objects.requireNonNull(status, "status");
		this.file = file;
		this.alreadyPresent = alreadyPresent;
	}

	static DownloadOutcome completed(Path file, boolean alreadyPresent) {
		return new DownloadOutcome(Status.COMPLETED, Objects.requireNonNull(file, "file"), alreadyPresent);
	}

	static DownloadOutcome cancelled() {
		return new DownloadOutcome(Status.CANCELLED, null, false);
	}

	public Status status() {
		return status;
	}

	public boolean isCompleted() {
		return status == Status.COMPLETED;
	}

	public boolean isCancelled() {
		return status == Status.CANCELLED;
	}

	/** Pfad der verifizierten Datei bei {@link Status#COMPLETED}, sonst {@code null}. */
	public Path file() {
		return file;
	}

	public Optional<Path> fileIfPresent() {
		return Optional.ofNullable(file);
	}

	/**
	 * {@code true}, wenn die Datei schon verifiziert im Zielordner lag und kein
	 * Netz-Transfer noetig war.
	 */
	public boolean alreadyPresent() {
		return alreadyPresent;
	}

	@Override
	public String toString() {
		return "DownloadOutcome[" + status + (file != null ? ", file=" + file : "")
				+ (alreadyPresent ? ", alreadyPresent" : "") + "]";
	}
}
