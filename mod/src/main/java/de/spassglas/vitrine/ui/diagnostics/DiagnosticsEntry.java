package de.spassglas.vitrine.ui.diagnostics;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Zeile im Diagnose-Bildschirm (ROADMAP X3): ein <em>aktives</em>
 * Vitrine-Paket samt genau den drei woertlich im Fertig-Kriterium geforderten
 * Werten -- {@link #version()}, {@link #sha256()} und {@link #source()} --
 * plus Name/Autor fuer die Anzeige. Reines Datenobjekt, kein Minecraft-Typ:
 * {@link DiagnosticsModel} baut die Liste ohne laufenden Client.
 */
public record DiagnosticsEntry(String packId, String manifestId, String name, String author, String version,
		String sha256, Source source, boolean missing) {

	public DiagnosticsEntry {
		Objects.requireNonNull(packId, "packId");
		Objects.requireNonNull(manifestId, "manifestId");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(author, "author");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(source, "source");
	}

	/**
	 * ROADMAP GATE115 (Fertig-Kriterium: "zeigt den Zustand sichtbar in
	 * Bibliothek/Diagnose statt ihn zu verschlucken"): ein <em>aktives</em>
	 * Paket aus {@code state.json}, dessen {@code .vpkg} weder in {@code
	 * packs/} noch -- nach dem Wiederherstellungsversuch beim Start, siehe
	 * {@code OrphanedUpdateBackupRecovery} -- im Update-Backup gefunden wurde.
	 * Ohne lesbares Manifest gibt es ehrlich kein Name/Autor/Version/Pruefsumme
	 * (Charter-Regel 2: nichts vorgaukeln) -- {@link #name()} liefert deshalb
	 * die Roh-{@code manifestId}.
	 */
	public static DiagnosticsEntry missing(String packId, String manifestId) {
		return new DiagnosticsEntry(packId, manifestId, manifestId, "?", "?", null, Source.unknown(), true);
	}

	/**
	 * Die Quelle eines Pakets ("welche Datei kam von wo", X3): entweder der
	 * lokale Bibliotheksordner ({@link Kind#LOCAL_LIBRARY}), ein Katalog-Host,
	 * von dem eine Version tatsaechlich heruntergeladen wurde ({@link
	 * Kind#CATALOG}, {@link #detail()} traegt den Host), oder -- fuer einen vor
	 * X3 geschriebenen {@code state.json}-Eintrag -- {@link Kind#UNKNOWN}. Nie
	 * erfunden: siehe {@link DiagnosticsModel} fuer die Herleitung.
	 */
	public record Source(Kind kind, Optional<String> detail) {

		public Source {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(detail, "detail");
		}

		public static Source localLibrary() {
			return new Source(Kind.LOCAL_LIBRARY, Optional.empty());
		}

		public static Source catalog(String host) {
			return new Source(Kind.CATALOG, Optional.of(host));
		}

		public static Source unknown() {
			return new Source(Kind.UNKNOWN, Optional.empty());
		}

		public enum Kind {
			LOCAL_LIBRARY,
			CATALOG,
			UNKNOWN
		}
	}
}
