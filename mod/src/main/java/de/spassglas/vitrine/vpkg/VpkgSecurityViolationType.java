package de.spassglas.vitrine.vpkg;

/**
 * Die einzelnen Angriffsarten, gegen die {@link VpkgSecurityValidator} (P3)
 * prueft. Jeder Wert entspricht genau einem in ROADMAP.md/PLAN.md §6
 * genannten Angriff, damit ein Aufrufer (und ein Test) eindeutig sagen kann,
 * WAS an einem Paket abgelehnt wurde.
 */
public enum VpkgSecurityViolationType {

	/** Eintragsname enthaelt ein {@code ..}-Segment (Zip-Slip/Traversal). */
	PATH_TRAVERSAL,

	/** Eintragsname ist selbst ein absoluter Pfad (Windows-Laufwerk oder Unix-Wurzel). */
	ABSOLUTE_PATH,

	/**
	 * Eintrag ist laut dem zentralen ZIP-Verzeichnis kein regulaerer Datei-
	 * oder Verzeichniseintrag (z.B. ein Unix-Symlink, Geraetedatei o.ae.).
	 */
	NON_REGULAR_ENTRY,

	/** Summe der unkomprimierten Groessen aller Eintraege ueber dem Limit. */
	TOTAL_UNCOMPRESSED_SIZE_EXCEEDED,

	/** Kompressionsverhaeltnis (unkomprimiert:komprimiert) eines einzelnen Eintrags ueber dem Limit. */
	COMPRESSION_RATIO_EXCEEDED,

	/** Anzahl der Eintraege im Archiv ueber dem Limit. */
	ENTRY_COUNT_EXCEEDED,

	/** Dateiendung steht auf der Verbotsliste fuer Klasse A (kann Code enthalten). */
	FORBIDDEN_FILE_TYPE,

	/** Datei heisst {@code *.png}, aber der PNG-Kopf ist zu kurz/keine gueltige PNG-Signatur/kein IHDR-Chunk. */
	PNG_HEADER_INVALID,

	/** PNG-IHDR nennt eine Breite oder Hoehe ueber dem Limit. */
	PNG_DIMENSIONS_EXCEEDED,

	/**
	 * GATE111: Datei heisst {@code *.jpg}/{@code *.jpeg}, aber es liess sich
	 * kein gueltiges SOI/SOFn-Segment finden (keine gueltige JPEG-Signatur
	 * oder kein Start-Of-Frame-Segment innerhalb des Suchfensters).
	 */
	JPEG_HEADER_INVALID,

	/** GATE111: JPEG-SOFn-Segment nennt eine Breite oder Hoehe ueber dem Limit. */
	JPEG_DIMENSIONS_EXCEEDED,

	/**
	 * Eintrag liess sich beim tatsaechlichen Lesen (GATE30/GATE31-Groessencheck)
	 * nicht oeffnen/lesen -- ein ZIP-Formatfehler jenseits der deklarierten
	 * Groesse, z.B. weil die deklarierte Groesse im zentralen Verzeichnis
	 * nicht zum tatsaechlichen ZIP-Aufbau passt.
	 */
	ENTRY_UNREADABLE,

	/**
	 * GATE59/GATE108: Eintragsname ist fuer ein Dateisystem gefaehrlich, ohne
	 * Traversal zu sein -- ein Doppelpunkt (NTFS-Alternate-Data-Stream wie
	 * {@code datei.txt:zone}), ein Steuerzeichen, eine absurde Verschachtelungs-
	 * tiefe, ein ueberlanger Name, oder ein Pfadsegment, das (vor dem ersten
	 * Punkt) einem reservierten Windows-Geraetenamen entspricht ({@code CON},
	 * {@code PRN}, {@code AUX}, {@code NUL}, {@code COM1}-{@code COM9},
	 * {@code LPT1}-{@code LPT9}).
	 */
	UNSAFE_ENTRY_NAME
}
