package de.spassglas.vitrine.ui.search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Reine In-Memory-Historie der letzten Suchen (ROADMAP S6, docs/UI-SPEC.md
 * „1. Kopfleiste": „letzte Suchen"). Beschraenkte Liste, juengste zuerst;
 * ein erneut abgeschickter Begriff wandert nach oben statt sich zu
 * verdoppeln; leere/blanke Eingaben werden nicht aufgenommen.
 *
 * <p>Kein Persistieren -- die Roadmap-Zeile verlangt es nicht ausdruecklich,
 * und ein eigener Dateipfad waere Charter-widrig. Soll spaeter persistiert
 * werden, laeuft das ueber den vorhandenen {@code SettingsManager}, nicht
 * ueber eine neue Datei.</p>
 */
public final class RecentSearches {

	/** Obergrenze der behaltenen Eintraege. */
	public static final int MAX_ENTRIES = 8;

	private final Deque<String> entries = new ArrayDeque<>();

	/**
	 * Nimmt {@code raw} (getrimmt) als juengsten Eintrag auf. Blanke Eingabe
	 * wird ignoriert. Ein bereits vorhandener Eintrag (Vergleich ohne
	 * Gross-/Kleinschreibung) wird entfernt und in der neuen Schreibweise
	 * vorn wieder eingefuegt. Ueberzaehlige aeltere Eintraege fallen hinten weg.
	 */
	public void record(String raw) {
		if (raw == null) {
			return;
		}
		String value = raw.trim();
		if (value.isEmpty()) {
			return;
		}
		entries.removeIf(existing -> existing.equalsIgnoreCase(value));
		entries.addFirst(value);
		while (entries.size() > MAX_ENTRIES) {
			entries.removeLast();
		}
	}

	/** Die Eintraege, juengster zuerst. */
	public List<String> entries() {
		return List.copyOf(entries);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public void clear() {
		entries.clear();
	}
}
