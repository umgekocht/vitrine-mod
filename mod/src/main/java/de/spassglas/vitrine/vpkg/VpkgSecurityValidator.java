package de.spassglas.vitrine.vpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;

/**
 * Sicherheitsschicht fuer {@code .vpkg}-Archive (ROADMAP P3, Charter-Regel 3:
 * "Sicherheit vor Bequemlichkeit"). Prueft ein bereits geoeffnetes
 * {@link VpkgArchive} gegen alle in PLAN.md Paragraph 6 genannten Angriffe --
 * rein lesend, ohne jemals einen Zielpfad ausserhalb des ZIP-Archivs zu
 * berechnen oder ein Byte auf die Platte zu schreiben. Das eigentliche
 * Entpacken/Anwenden (H1/H2, M2) passiert an anderer Stelle; diese Klasse
 * liefert nur das Urteil, BEVOR ein Aufrufer dort ueberhaupt anfaengt.
 *
 * <p><b>Absicht fuer spaetere Aufrufer:</b> Kein Code darf ein
 * {@link VpkgArchive} anwenden (Ressourcen kopieren, Pack registrieren, ...),
 * ohne vorher {@link #requireSafe(VpkgArchive)} aufgerufen zu haben. Diese
 * Methode ist bewusst die einzige, die eine {@link VpkgSecurityException}
 * wirft -- ein spaeterer Installer kann sie also nicht "aus Versehen"
 * umgehen, ohne den Aufruf aktiv wegzulassen.</p>
 */
public final class VpkgSecurityValidator {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgSecurity");

	// --- Zip-Bomben-Grenzen (Werte aus PLAN.md Paragraph 6, "Sicherheit") ---

	/**
	 * Summe der unkomprimierten Groessen aller Eintraege. 256 MiB deckt selbst
	 * hochaufloesende Texture-/Sound-Bundles grosszuegig ab (Klasse A enthaelt
	 * keinen Code, keine Modelle, keine Video-Assets) und begrenzt gleichzeitig,
	 * wie viel Speicher/Platte ein einzelnes Hot-Apply im schlimmsten Fall
	 * belegen kann, bevor auch nur ein Byte entpackt wurde.
	 */
	public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;

	/**
	 * Verhaeltnis unkomprimiert:komprimiert je einzelnem Eintrag. Echte
	 * Texturen (PNG), Sounds (OGG) und JSON-Dateien komprimieren realistisch
	 * nie staerker als grob 10:1-20:1 -- 100:1 laesst reichlich Spielraum,
	 * faengt aber die klassische Nullbyte-Zip-Bombe (Verhaeltnisse im drei-
	 * bis sechsstelligen Bereich) sicher ab.
	 */
	public static final int MAX_COMPRESSION_RATIO = 100;

	/**
	 * Maximale Anzahl Eintraege in einem Archiv. Selbst ein sehr grosses
	 * Bundle aus mehreren tausend Einzeltexturen bleibt weit darunter; die
	 * Grenze schuetzt vor Archiven, die allein ueber die EintragsMENGE (nicht
	 * deren Groesse) das zentrale Verzeichnis oder eine spaetere
	 * Dateisystem-Operation je Eintrag in die Laenge ziehen sollen.
	 */
	public static final int MAX_ENTRY_COUNT = 20_000;

	/** GATE59: hoechste Verschachtelungstiefe eines Eintragspfads (Vanilla-Ressourcen brauchen weit weniger). */
	public static final int MAX_PATH_DEPTH = 32;

	/** GATE59: laengster erlaubter Eintragsname in Zeichen. */
	public static final int MAX_ENTRY_NAME_LENGTH = 512;

	/**
	 * Maximale Bildbreite/-hoehe in Pixeln, geprueft ueber die jeweiligen
	 * Format-Kopfdaten (PNG: IHDR-Chunk; JPEG: SOFn-Segment), bevor irgendein
	 * Decoder das Bild vollstaendig entpackt. 4096x4096 deckt selbst sehr
	 * hochaufloesende Texture-Packs (Vanilla braucht 16x16 bis 512x512)
	 * grosszuegig ab und verhindert eine "Decompression Bomb" ueber
	 * vorgetaeuschte Bildmasse.
	 *
	 * <p>GATE111: dieselbe Grenze gilt fuer PNG ({@link #checkPngDimensions})
	 * UND {@code *.jpg}/{@code *.jpeg} ({@link #checkJpegDimensions}) -- beide
	 * Formate landen am Ende im selben {@code NativeImage}/stb-Decoder, eine
	 * Decompression-Bombe ueber vorgetaeuschte Masse ist fuer beide gleich
	 * moeglich. Deshalb eine gemeinsame Konstante statt zwei getrennter
	 * Grenzwerte, die sonst leicht auseinanderlaufen koennten. {@code .bmp}/
	 * {@code .tga} brauchen keine eigene Massenpruefung: GATE106s Allowlist
	 * (siehe {@link #ALLOWED_EXTENSIONS}) laesst beide Endungen gar nicht erst
	 * durch, sie erreichen also nie einen Decoder.</p>
	 */
	public static final int MAX_IMAGE_DIMENSION = 4096;

	/**
	 * Erlaubte Dateiendungen fuer Klasse A (Charter-Regel 3/4, PLAN.md §2:
	 * "In Klasse A verboten ... ausfuehrbare Skripte in irgendeiner Sprache").
	 * Allowlist statt Blockliste (GATE106): eine Blockliste muesste JEDE
	 * ausfuehrbare/gefaehrliche Endung einzeln kennen und ist per Definition
	 * nie vollstaendig ({@code .wasm}/{@code .jsp}/{@code .rb}/{@code .php}/
	 * {@code .jnilib}/{@code .scpt} fehlten in der alten Liste); eine
	 * Allowlist lehnt dagegen alles ab, was nicht explizit als harmloser
	 * Klasse-A-Ressourcentyp bekannt ist -- auch jede zukuenftige oder
	 * vergessene gefaehrliche Endung.
	 *
	 * <p>Die hier erlaubten Endungen sind genau die Ressourcentypen, die
	 * laut ROADMAP M5/T7 und dem bestehenden Code tatsaechlich in einem
	 * {@code .vpkg} vorkommen: {@code .png}/{@code .jpg}/{@code .jpeg}
	 * (Texturen, GUI, HUD-Icons), {@code .json} (Manifest {@code vitrine.json},
	 * {@code lang/*.json}, {@code sounds.json}, {@code hud/*.json},
	 * {@code theme.json}), {@code .ogg} (Sound, ROADMAP T7 -- ein reiner
	 * Audio-Container ohne ausfuehrbaren Inhalt), {@code .txt} (einfache
	 * Begleittexte), {@code .mcmeta} (Vanilla-Animationsmetadaten wie
	 * {@code foo.png.mcmeta}), {@code .ttf}/{@code .otf} (Fonts, falls
	 * ein Theme-/HUD-Paket eigene Schriften mitbringt) sowie {@code .vs}
	 * (ROADMAP AD2: {@code scripts/*.vs}, deklaratives JSON der X5-Sandbox,
	 * kein ausfuehrbarer Code). Gross-/Kleinschreibung
	 * wird beim Vergleich ignoriert; es wird NICHT getrimmt -- ein
	 * Leerzeichen, Steuerzeichen oder sonstiges Zeichen nach der eigentlichen
	 * Endung sorgt bewusst dafuer, dass der Suffix-Vergleich fehlschlaegt und
	 * das Archiv abgelehnt wird (siehe {@link #hasAllowedExtension(String)}).</p>
	 */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"png", "jpg", "jpeg", "json", "ogg", "txt", "mcmeta", "ttf", "otf", "md",
			// ROADMAP M10/AD1: Datenpaket-Inhalte von Addons (Funktionen, Strukturen) --
			// reine Daten, die Minecraft selbst liest, kein ausfuehrbarer Code.
			"mcfunction", "nbt",
			// Addon-Plattform: Lua- und JavaScript-Quelltext. Laeuft nur in der
			// Vitrine-Sandbox (kein Java-Zugriff, Zeitlimit, Faehigkeit "skript").
			"lua", "js",
			// ROADMAP AD2: scripts/*.vs -- deklaratives JSON (dieselbe X5-Sandbox-
			// Grammatik wie regeln/*.json, nur eine andere Dateiendung), kein
			// Lua/JS-Interpreter. Kein ausfuehrbarer Code, siehe
			// de.spassglas.vitrine.addon.script.ScriptAddonParser.
			"vs");

	/**
	 * Endungslose Text-Dateien, die an der Paketwurzel liegen duerfen (2026-10-06):
	 * GATE55 verlangt die LICENSE-Datei im Paket, GATE106 hatte sie mit der
	 * Allowlist versehentlich ausgesperrt -- jedes Katalogpaket war dadurch
	 * uninstallierbar ("Zip security check failed").
	 */
	private static final Set<String> ALLOWED_ROOT_FILES = Set.of("LICENSE", "LICENCE", "NOTICE", "COPYING", "README");

	private static final byte[] PNG_SIGNATURE = {
			(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
	};

	/** Bytes bis inklusive Breite+Hoehe im IHDR-Chunk: Signatur(8)+Laenge(4)+Typ(4)+Breite(4)+Hoehe(4). */
	private static final int PNG_HEADER_PREFIX_LENGTH = 24;

	private VpkgSecurityValidator() {
	}

	/**
	 * Prueft {@code archive} gegen alle P3-Angriffe und liefert die
	 * vollstaendige Liste der gefundenen Verstoesse (leer, wenn sauber). Liest
	 * dabei ausschliesslich Metadaten und, fuer {@code *.png}-Eintraege, die
	 * ersten Bytes des PNG-Kopfes -- nie mehr, nie eine volle Dekodierung, nie
	 * ein Schreibzugriff.
	 */
	public static VpkgSecurityReport validate(VpkgArchive archive) {
		return validate(archive, MAX_TOTAL_UNCOMPRESSED_BYTES);
	}

	/**
	 * Wie {@link #validate(VpkgArchive)}, aber mit einem explizit uebergebenen
	 * Gesamt-Budget statt {@link #MAX_TOTAL_UNCOMPRESSED_BYTES} -- package-
	 * private, ausschliesslich fuer Tests: ein kuenstlich kleines Budget deckt
	 * ein gefaelschtes zentrales Verzeichnis (klein deklariert, real darueber)
	 * auf, ohne dass ein Test hunderte Megabyte realen Inhalt bauen muss (siehe
	 * {@link #checkActualUncompressedSize}, GATE30).
	 */
	static VpkgSecurityReport validate(VpkgArchive archive, long maxTotalUncompressedBytes) {
		List<VpkgEntry> entries = archive.entries();
		List<VpkgSecurityViolation> violations = new ArrayList<>();

		checkEntryCount(entries, violations);
		if (!violations.isEmpty()) {
			// GATE65: zu viele Eintraege -- nicht noch jeden einzelnen lesen
			// (inklusive PNG-Koepfe), sondern sofort ablehnen.
			return reject(archive, violations);
		}
		checkTotalUncompressedSize(entries, maxTotalUncompressedBytes, violations);
		checkActualUncompressedSize(archive, entries, maxTotalUncompressedBytes, violations);

		Set<String> nonRegularEntryNames = VpkgZipCentralDirectoryReader.findNonRegularEntryNames(archive.path());

		for (VpkgEntry entry : entries) {
			checkPathTraversal(entry, violations);
			checkAbsolutePath(entry, violations);
			checkUnsafeEntryName(entry, violations);
			checkNonRegularEntry(entry, nonRegularEntryNames, violations);
			checkCompressionRatio(entry, violations);
			checkForbiddenFileType(entry, violations);
			checkPngDimensions(archive, entry, violations);
			checkJpegDimensions(archive, entry, violations);
		}
		return reject(archive, violations);
	}

	private static VpkgSecurityReport reject(VpkgArchive archive, List<VpkgSecurityViolation> violations) {
		VpkgSecurityReport report = new VpkgSecurityReport(violations);
		if (!report.isSafe()) {
			LOGGER.warn("VpkgSecurityValidator: Archiv '{}' abgelehnt, {} Verstoss/Verstoesse: {}",
					archive.path(), violations.size(), violations);
		}
		return report;
	}

	/**
	 * Wie {@link #validate(VpkgArchive)}, wirft aber statt ein Ergebnisobjekt
	 * zurueckzugeben eine {@link VpkgSecurityException}, sobald mindestens ein
	 * Verstoss vorliegt. Gedacht als der EINE Aufruf, den jeder spaetere
	 * Installer/Anwender eines {@code .vpkg} vor dem ersten
	 * {@link VpkgArchive#openEntry(String)} fuer eine anzuwendende Ressource
	 * machen muss.
	 */
	public static void requireSafe(VpkgArchive archive) throws VpkgSecurityException {
		VpkgSecurityReport report = validate(archive);
		if (!report.isSafe()) {
			throw new VpkgSecurityException(report.violations());
		}
	}

	// --- einzelne Pruefungen ---------------------------------------------

	private static void checkEntryCount(List<VpkgEntry> entries, List<VpkgSecurityViolation> violations) {
		if (entries.size() > MAX_ENTRY_COUNT) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.ENTRY_COUNT_EXCEEDED, null,
					"Archiv hat " + entries.size() + " Eintraege, erlaubt sind hoechstens " + MAX_ENTRY_COUNT));
		}
	}

	private static void checkTotalUncompressedSize(List<VpkgEntry> entries, long maxTotalBytes,
			List<VpkgSecurityViolation> violations) {
		long total = 0;
		for (VpkgEntry entry : entries) {
			total += Math.max(0, entry.size());
		}
		if (total > maxTotalBytes) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED, null,
					"Summe der DEKLARIERTEN unkomprimierten Eintragsgroessen betraegt " + total
							+ " Bytes, erlaubt sind hoechstens " + maxTotalBytes + " Bytes"));
		}
	}

	/**
	 * GATE30-Deckel: liest jeden Eintrag TATSAECHLICH (gestreamt, mit
	 * schrumpfendem Restbudget ueber alle Eintraege hinweg) und zaehlt die
	 * wirklich angefallenen Bytes -- unabhaengig von {@link #checkTotalUncompressedSize},
	 * das nur die im ZIP-Verzeichnis deklarierten Groessen summiert (und damit
	 * blind fuer ein gefaelschtes zentrales Verzeichnis ist: klein deklariert,
	 * riesig real). Bricht das Lesen des jeweiligen Eintrags sofort ab, sobald
	 * das Gesamtbudget ueberschritten wird ({@link VpkgArchive#openEntry(String, long)}
	 * wirft dafuer {@link VpkgEntryTooLargeException}) -- nie ein volles
	 * Einlesen eines echten riesigen Eintrags, genau wie der Deckel beim
	 * Download ({@code BoundedBody}).
	 *
	 * <p>GATE107: prueft JEDEN Eintrag, unabhaengig vom Namensmuster. Die
	 * fruehere Fassung hat Eintraege uebersprungen, deren Name auf {@code /}
	 * endete ({@link VpkgEntry#directory()}, das selbst nur auf {@link
	 * ZipEntry#isDirectory()} beruht -- einer reinen Namenskonvention ohne
	 * jede Grundlage im ZIP-Format). Ein handgebautes Archiv konnte so einen
	 * Eintrag namens {@code "evil/"} mit mehreren MiB echtem Inhalt am
	 * 256-MiB-Lesedeckel vorbeischleusen, weil er wie ein (immer leeres)
	 * Verzeichnis behandelt wurde. Eine echte leere Verzeichnis-Eintragung
	 * landet ohnehin bei 0 tatsaechlich gelesenen Bytes -- das Weglassen des
	 * Namens-basierten Kurzschlusses kostet also nichts, schliesst aber die
	 * Luecke.</p>
	 *
	 * <p>GATE109: fuer {@link ZipEntry#STORED} muss der Eintrag dafuer nicht
	 * einmal geoeffnet werden -- das ZIP-Format verlangt fuer STORED
	 * {@code compressedSize == size}, und {@link java.util.zip.ZipFile}
	 * liefert beim tatsaechlichen Lesen nachweislich nie mehr Bytes als die im
	 * zentralen Verzeichnis deklarierte Groesse (siehe {@code
	 * VpkgFixtures#forgedSmallDeclaredLargeRealSizePackage()}-Dokumentation:
	 * genau deshalb musste jene Fixture auf DEFLATE wechseln, um die GATE30-
	 * Luecke ueberhaupt nachzustellen). Die deklarierte Groesse ist fuer
	 * STORED also bereits die tatsaechlich maximal lieferbare Groesse -- ein
	 * teures vollstaendiges Lesen/Inflate bringt keine zusaetzliche
	 * Sicherheit. Nur bei jeder anderen Methode (v.a. DEFLATE) wird weiterhin
	 * tatsaechlich gelesen.</p>
	 */
	private static void checkActualUncompressedSize(VpkgArchive archive, List<VpkgEntry> entries, long maxTotalBytes,
			List<VpkgSecurityViolation> violations) {
		long total = 0;
		byte[] chunk = new byte[8192];
		for (VpkgEntry entry : entries) {
			if (entry.method() == ZipEntry.STORED) {
				total += Math.max(0, entry.size());
				if (total > maxTotalBytes) {
					violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED,
							entry.name(), "Eintrag '" + entry.name() + "' laesst die Summe der Groessen ueber die "
									+ "erlaubten " + maxTotalBytes + " Bytes steigen (STORED: deklarierte Groesse ist "
									+ "die tatsaechlich maximal lieferbare, kein Oeffnen noetig)"));
					return;
				}
				continue;
			}
			long remainingBudget = maxTotalBytes - total;
			long cap = Math.max(0, remainingBudget);
			try (InputStream in = archive.openEntry(entry.name(), cap)) {
				int read;
				while ((read = in.read(chunk)) != -1) {
					total += read;
				}
			} catch (VpkgEntryTooLargeException e) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.TOTAL_UNCOMPRESSED_SIZE_EXCEEDED,
						entry.name(), "Eintrag '" + entry.name() + "' laesst die Summe der TATSAECHLICH gelesenen "
								+ "unkomprimierten Bytes ueber die erlaubten " + maxTotalBytes + " Bytes steigen -- "
								+ "unabhaengig davon, was das ZIP-Verzeichnis fuer diesen Eintrag deklariert "
								+ "(Zip-Bomben-Schutz beim tatsaechlichen Lesen, nicht nur bei den Metadaten)"));
				return;
			} catch (IOException e) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.ENTRY_UNREADABLE, entry.name(),
						"Eintrag '" + entry.name() + "' liess sich beim tatsaechlichen Groessencheck nicht lesen: "
								+ e.getMessage()));
				return;
			}
		}
	}

	/**
	 * GATE108: lehnt nicht nur ein woertliches {@code ..}-Segment ab, sondern
	 * JEDES Segment, das nur aus zwei oder mehr Punkten besteht ({@code "...."},
	 * {@code "..."}, ...). Grund: {@code "...."} ist unter Unix nur ein
	 * ungewoehnlicher, aber harmloser Dateiname -- unter Windows behandeln
	 * jedoch sowohl der Explorer-Adressleiste als auch {@code cmd.exe} eine
	 * Folge von N Punkten historisch wie N-1 {@code ..}-Spruenge nach oben
	 * (eine lange bekannte DOS-Eigenheit). Ein Eintrag {@code "....//....//evil.txt"}
	 * besteht nur aus Segmenten ungleich dem woertlichen {@code ".."} und kam
	 * so am alten, rein woertlichen Vergleich vorbei.
	 */
	private static void checkPathTraversal(VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		String name = entry.name();
		for (String segment : name.split("[/\\\\]+")) {
			if (isDotsOnlyTraversalSegment(segment)) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PATH_TRAVERSAL, name,
						"Eintrag '" + name + "' enthaelt ein Traversal-Segment aus ausschliesslich Punkten ('"
								+ segment + "', Zip-Slip-Versuch)"));
				return;
			}
		}
	}

	private static boolean isDotsOnlyTraversalSegment(String segment) {
		return segment.length() >= 2 && segment.chars().allMatch(c -> c == '.');
	}

	private static void checkAbsolutePath(VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		String name = entry.name();
		if (isAbsolutePath(name)) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.ABSOLUTE_PATH, name,
					"Eintrag '" + name + "' ist ein absoluter Pfad statt relativ zur Paketwurzel"));
		}
	}

	/**
	 * GATE108: reservierte Windows-Geraetenamen (MS-DOS-Erbe, auch unter
	 * NTFS/Win32 noch aktiv) -- {@code CON}/{@code PRN}/{@code AUX}/{@code NUL}
	 * sowie {@code COM1}-{@code COM9}/{@code LPT1}-{@code LPT9}. Windows
	 * reserviert dabei nicht nur den exakten Namen, sondern auch jeden Namen,
	 * dessen Teil VOR dem ersten Punkt einem dieser Namen entspricht (z.B.
	 * {@code CON.txt}) -- ein Versuch, eine Datei/einen Ordner so zu benennen,
	 * schlaegt unter Windows fehl oder adressiert stattdessen das Geraet.
	 * Heute ungefaehrlich (kein Code loest einen Eintragsnamen gegen das
	 * Dateisystem auf), wird aber scharf, sobald ein Entpacker hinzukommt --
	 * deshalb schon jetzt eine Ablehnung, kein spaeteres Nachruesten.
	 */
	private static final Set<String> WINDOWS_RESERVED_DEVICE_NAMES = Set.of(
			"CON", "PRN", "AUX", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

	/**
	 * GATE59/GATE108: Namen, die kein Traversal sind, aber beim Entpacken oder
	 * Ablegen trotzdem Schaden anrichten koennten. Ein Doppelpunkt ist in
	 * keinem legitimen Ressourcenpfad noetig und waere unter NTFS ein
	 * Alternate Data Stream; Steuerzeichen und absurde Tiefe/Laenge dienen nur
	 * dazu, Dateisystem-Grenzen zu reizen; ein Segment, das (vor dem ersten
	 * Punkt) einem reservierten Windows-Geraetenamen entspricht, adressiert
	 * unter Windows kein normales Datei-/Verzeichnis-Ziel mehr.
	 */
	private static void checkUnsafeEntryName(VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		String name = entry.name();
		String reason = null;
		if (name.indexOf(':') >= 0 && !isAbsolutePath(name)) {
			reason = "enthaelt einen Doppelpunkt (unter Windows ein Alternate Data Stream)";
		} else if (name.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
			reason = "enthaelt ein Steuerzeichen";
		} else if (name.length() > MAX_ENTRY_NAME_LENGTH) {
			reason = "ist " + name.length() + " Zeichen lang, erlaubt sind hoechstens " + MAX_ENTRY_NAME_LENGTH;
		} else if (name.split("[/\\\\]+").length > MAX_PATH_DEPTH) {
			reason = "ist tiefer als " + MAX_PATH_DEPTH + " Ebenen verschachtelt";
		} else {
			String reservedSegment = findReservedWindowsDeviceNameSegment(name);
			if (reservedSegment != null) {
				reason = "enthaelt das Segment '" + reservedSegment + "', das (vor dem ersten Punkt) einem "
						+ "reservierten Windows-Geraetenamen entspricht";
			}
		}
		if (reason != null) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.UNSAFE_ENTRY_NAME, name,
					"Eintrag '" + name + "' " + reason));
		}
	}

	private static String findReservedWindowsDeviceNameSegment(String name) {
		for (String segment : name.split("[/\\\\]+")) {
			int dotIndex = segment.indexOf('.');
			String baseName = dotIndex >= 0 ? segment.substring(0, dotIndex) : segment;
			if (WINDOWS_RESERVED_DEVICE_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
				return segment;
			}
		}
		return null;
	}

	private static boolean isAbsolutePath(String name) {
		if (name.isEmpty()) {
			return false;
		}
		char first = name.charAt(0);
		if (first == '/' || first == '\\') {
			return true;
		}
		// Windows-Laufwerksbuchstabe, z.B. "C:\..." oder "C:/...".
		if (name.length() >= 3 && isAsciiLetter(name.charAt(0)) && name.charAt(1) == ':'
				&& (name.charAt(2) == '\\' || name.charAt(2) == '/')) {
			return true;
		}
		return false;
	}

	private static boolean isAsciiLetter(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	private static void checkNonRegularEntry(VpkgEntry entry, Set<String> nonRegularEntryNames,
			List<VpkgSecurityViolation> violations) {
		if (nonRegularEntryNames.contains(entry.name())) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.NON_REGULAR_ENTRY, entry.name(),
					"Eintrag '" + entry.name() + "' ist laut ZIP-Verzeichnis kein regulaerer Datei- oder "
							+ "Verzeichniseintrag (z.B. ein Symlink) und wird abgelehnt"));
		}
	}

	private static void checkCompressionRatio(VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		if (entry.directory() || entry.size() <= 0) {
			return;
		}
		long compressedSize = entry.compressedSize();
		if (compressedSize <= 0) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.COMPRESSION_RATIO_EXCEEDED,
					entry.name(), "Eintrag '" + entry.name() + "' hat eine komprimierte Groesse von " + compressedSize
							+ " Bytes bei " + entry.size() + " Bytes unkomprimiert -- unplausibles Verhaeltnis"));
			return;
		}
		double ratio = (double) entry.size() / (double) compressedSize;
		if (ratio > MAX_COMPRESSION_RATIO) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.COMPRESSION_RATIO_EXCEEDED,
					entry.name(), "Eintrag '" + entry.name() + "' hat ein Kompressionsverhaeltnis von "
							+ String.format(Locale.ROOT, "%.1f", ratio) + ":1, erlaubt sind hoechstens "
							+ MAX_COMPRESSION_RATIO + ":1"));
		}
	}

	private static void checkForbiddenFileType(VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		if (entry.directory()) {
			return;
		}
		if (!hasAllowedExtension(entry.name()) && !ALLOWED_ROOT_FILES.contains(entry.name())) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.FORBIDDEN_FILE_TYPE, entry.name(),
					"Eintrag '" + entry.name() + "' hat keine der erlaubten Dateiendungen ("
							+ String.join(", ", ALLOWED_EXTENSIONS) + ") -- Klasse A erlaubt nur bekannte, "
							+ "ungefaehrliche Ressourcentypen (Allowlist statt Blockliste, GATE106)"));
		}
	}

	/**
	 * GATE106: strikter, NICHT trimmender Suffix-Vergleich gegen
	 * {@link #ALLOWED_EXTENSIONS} -- bewusst kein {@code lastIndexOf('.')} plus
	 * Set-Lookup mehr (das war die alte, umgehbare Logik), sondern fuer jede
	 * erlaubte Endung ein direkter, case-insensitiver {@code endsWith(".ext")}
	 * auf dem vollen, UNVERAENDERTEN Eintragsnamen. Ein Leerzeichen
	 * ({@code "payload.jar "}), ein angehaengter Punkt/Semikolon/Schraegstrich
	 * ({@code "payload.jar."}, {@code "payload.jar;"}, {@code "payload.jar/"})
	 * oder ein eingebettetes Leerzeichen in der vermeintlichen Endung
	 * ({@code "payload.cl ass"}) aendern den Namen tatsaechlich -- der Name
	 * endet dann auf keine der erlaubten Endungen mehr und der Eintrag wird
	 * abgelehnt, statt dass eine "erratene" Endung faelschlich durchgeht.
	 */
	private static boolean hasAllowedExtension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String extension : ALLOWED_EXTENSIONS) {
			if (lower.endsWith("." + extension)) {
				return true;
			}
		}
		return false;
	}

	private static void checkPngDimensions(VpkgArchive archive, VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		if (entry.directory() || !entry.name().toLowerCase(Locale.ROOT).endsWith(".png")) {
			return;
		}
		byte[] header;
		try (InputStream in = archive.openEntry(entry.name())) {
			// readNBytes liest genau so viel wie fuer die Masspruefung noetig --
			// der Inflater entpackt nur diese wenigen Bytes, nie die ganze Datei.
			header = in.readNBytes(PNG_HEADER_PREFIX_LENGTH);
		} catch (IOException e) {
			LOGGER.warn("VpkgSecurityValidator: PNG-Kopf von '{}' liess sich nicht lesen: {}", entry.name(),
					e.toString());
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_HEADER_INVALID, entry.name(),
					"PNG-Kopf von '" + entry.name() + "' liess sich nicht lesen: " + e.getMessage()));
			return;
		}

		if (header.length < PNG_HEADER_PREFIX_LENGTH) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_HEADER_INVALID, entry.name(),
					"Eintrag '" + entry.name() + "' ist zu kurz fuer einen gueltigen PNG-Kopf ("
							+ header.length + " von " + PNG_HEADER_PREFIX_LENGTH + " Bytes)"));
			return;
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++) {
			if (header[i] != PNG_SIGNATURE[i]) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_HEADER_INVALID, entry.name(),
						"Eintrag '" + entry.name() + "' traegt die Endung .png, hat aber keine gueltige PNG-Signatur"));
				return;
			}
		}
		if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_HEADER_INVALID, entry.name(),
					"Eintrag '" + entry.name() + "' hat kein IHDR als ersten Chunk -- Breite/Hoehe nicht pruefbar"));
			return;
		}

		int width = readInt32BE(header, 16);
		int height = readInt32BE(header, 20);
		if (width <= 0 || height <= 0) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_HEADER_INVALID, entry.name(),
					"Eintrag '" + entry.name() + "' nennt im IHDR unplausible Masse " + width + "x" + height));
			return;
		}
		if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.PNG_DIMENSIONS_EXCEEDED, entry.name(),
					"Eintrag '" + entry.name() + "' hat die Masse " + width + "x" + height
							+ ", erlaubt sind hoechstens " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION));
		}
	}

	/**
	 * GATE111: Massenpruefung fuer {@code *.jpg}/{@code *.jpeg} -- dasselbe
	 * Prinzip wie {@link #checkPngDimensions}, nur gegen das JPEG-Format statt
	 * PNG. JPEG-Breite/-Hoehe stehen nicht an einer festen Stelle wie bei PNGs
	 * IHDR-Chunk, sondern im SOFn-Marker ("Start Of Frame", 0xFFC0-0xFFCF
	 * ausser den Nicht-Frame-Markern 0xFFC4/DHT, 0xFFC8/JPG-Erweiterung,
	 * 0xFFCC/DAC), der irgendwo nach dem SOI-Marker (0xFFD8) liegt -- davor
	 * koennen beliebig viele APPn/COM-Segmente stehen, jedes mit eigener
	 * 2-Byte-Laengenangabe (big-endian, schliesst die Laengenbytes selbst
	 * ein), die ueberbersprungen werden muss, bis ein SOFn-Marker gefunden
	 * wird. Im SOFn-Segment stehen direkt nach Marker(2)+Segmentlaenge(2)+
	 * Praezisionsbyte(1) zuerst die Hoehe, dann die Breite, je 2 Byte
	 * big-endian. Liest hoechstens {@link #JPEG_HEADER_SEARCH_LIMIT} Bytes --
	 * genug fuer mehrere grosse APPn/EXIF/ICC-Segmente vor dem SOFn, aber nie
	 * eine volle Dekodierung.
	 */
	private static void checkJpegDimensions(VpkgArchive archive, VpkgEntry entry, List<VpkgSecurityViolation> violations) {
		String lowerName = entry.name().toLowerCase(Locale.ROOT);
		if (entry.directory() || !(lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg"))) {
			return;
		}
		byte[] data;
		try (InputStream in = archive.openEntry(entry.name())) {
			data = in.readNBytes(JPEG_HEADER_SEARCH_LIMIT);
		} catch (IOException e) {
			LOGGER.warn("VpkgSecurityValidator: JPEG-Kopf von '{}' liess sich nicht lesen: {}", entry.name(),
					e.toString());
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
					"JPEG-Kopf von '" + entry.name() + "' liess sich nicht lesen: " + e.getMessage()));
			return;
		}

		if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
			violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
					"Eintrag '" + entry.name() + "' traegt die Endung .jpg/.jpeg, hat aber keine gueltige "
							+ "JPEG-SOI-Signatur"));
			return;
		}

		int pos = 2;
		while (true) {
			// Markerpraefix 0xFF (ggf. mehrfach durch Fuellbytes) ueberspringen.
			while (pos < data.length && (data[pos] & 0xFF) == 0xFF) {
				pos++;
			}
			if (pos >= data.length) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
						"Eintrag '" + entry.name() + "' hat innerhalb der ersten " + JPEG_HEADER_SEARCH_LIMIT
								+ " Bytes kein SOFn-Segment -- Masse nicht pruefbar"));
				return;
			}
			int marker = data[pos] & 0xFF;
			pos++;

			if (marker == 0xD9) { // EOI ohne vorheriges SOFn -- kein Bild-Frame im gelesenen Fenster
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
						"Eintrag '" + entry.name() + "' erreicht das Dateiende (EOI) ohne SOFn-Segment -- Masse "
								+ "nicht pruefbar"));
				return;
			}
			if (isStandaloneJpegMarker(marker)) {
				continue; // kein Laengenfeld, direkt zum naechsten Marker
			}
			if (pos + 1 >= data.length) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
						"Eintrag '" + entry.name() + "' bricht mitten im Laengenfeld eines Segments ab -- Masse "
								+ "nicht pruefbar"));
				return;
			}
			int segmentLength = readUint16BE(data, pos);
			if (segmentLength < 2) {
				violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
						"Eintrag '" + entry.name() + "' hat ein Segment mit unplausibler Laenge " + segmentLength));
				return;
			}

			if (isStartOfFrameMarker(marker)) {
				if (pos + 2 + 1 + 4 > data.length) {
					violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
							"Eintrag '" + entry.name() + "' hat ein SOFn-Segment, das vor Breite/Hoehe abbricht"));
					return;
				}
				int height = readUint16BE(data, pos + 3);
				int width = readUint16BE(data, pos + 5);
				if (width <= 0 || height <= 0) {
					violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_HEADER_INVALID, entry.name(),
							"Eintrag '" + entry.name() + "' nennt im SOFn-Segment unplausible Masse " + width + "x"
									+ height));
					return;
				}
				if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
					violations.add(new VpkgSecurityViolation(VpkgSecurityViolationType.JPEG_DIMENSIONS_EXCEEDED,
							entry.name(), "Eintrag '" + entry.name() + "' hat die Masse " + width + "x" + height
									+ ", erlaubt sind hoechstens " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION));
				}
				return;
			}

			pos += segmentLength; // Segment (APPn/COM/DQT/DHT/...) ueberspringen, naechster Marker folgt.
		}
	}

	/** Standalone-JPEG-Marker ohne Laengenfeld: TEM (0x01), RSTn (0xD0-0xD7). */
	private static boolean isStandaloneJpegMarker(int marker) {
		return marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7);
	}

	/**
	 * SOFn-Marker (0xC0-0xCF), AUSSER den drei Codes in diesem Bereich, die
	 * keinen Bild-Frame beschreiben: 0xC4 (DHT, Huffman-Tabelle), 0xC8
	 * (reserviert fuer JPG-Erweiterung, kein gueltiges Baseline/Progressive-
	 * SOF), 0xCC (DAC, arithmetische Kodierbedingungen).
	 */
	private static boolean isStartOfFrameMarker(int marker) {
		return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
	}

	/** Groesse des Suchfensters fuer {@link #checkJpegDimensions}, siehe dortige Klassendoku. */
	private static final int JPEG_HEADER_SEARCH_LIMIT = 128 * 1024;

	private static int readUint16BE(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
	}

	private static int readInt32BE(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 24)
				| ((data[offset + 1] & 0xFF) << 16)
				| ((data[offset + 2] & 0xFF) << 8)
				| (data[offset + 3] & 0xFF);
	}
}
