package de.spassglas.vitrine.fixtures;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Baut {@code .vpkg}-Testdateien komplett im Speicher (kein Zugriff auf die
 * Platte) -- fuer F4: "Hilfsfunktionen, die .vpkg-Testdateien im Speicher
 * bauen (auch boesartige)".
 *
 * <p>Ein {@code .vpkg} ist laut Roadmap ein ganz normales ZIP-Archiv mit
 * einer {@code vitrine.json} als Manifest, das der Mod niemals entpackt,
 * sondern nur ueber ZIP-Eintraege liest. Diese Klasse baut deshalb rohe
 * ZIP-Bytes ({@code byte[]}) -- keinerlei Manifest-Parsing, kein Sicherheits-
 * check. Das kommt erst mit P1 (Schema/Parser), P2 (.vpkg-Leser) und P3
 * (Sicherheitsschicht). Diese Klasse liefert nur das rohe Material, damit
 * P1/P2/P3 spaeter direkt gegen dieselben Fixtures testen koennen, statt
 * jeweils eigene ZIP-Bau-Logik zu duplizieren.</p>
 *
 * <p>Fuenf Fixtures, davon drei boesartige (siehe P3-Charter: Zip-Slip,
 * absolute Pfade, Zip-Bomben):</p>
 * <ul>
 *   <li>{@link #minimalValidPackage()} -- gutartig, ein Manifest + eine Textur.</li>
 *   <li>{@link #multiResourcePackage()} -- gutartig, mehrere Ressourcen-Eintraege.</li>
 *   <li>{@link #zipSlipPackage()} -- boesartig, Eintragsname mit {@code ../}-Traversal.</li>
 *   <li>{@link #absolutePathEntryPackage()} -- boesartig, Eintragsnamen mit
 *       absoluten Windows- und Unix-Pfaden.</li>
 *   <li>{@link #extremeCompressionRatioPackage()} -- boesartig, ein Eintrag mit
 *       einem Kompressionsverhaeltnis weit jenseits jeder plausiblen Textur/
 *       eines plausiblen Sounds (Zip-Bomben-Andeutung).</li>
 * </ul>
 *
 * <p>Fuer P3 (Sicherheitsschicht) ergaenzt um weitere boesartige Faelle:
 * {@link #totalUncompressedSizeExceededPackage()}, {@link #entryCountExceededPackage()},
 * {@link #forbiddenFileTypePackage()}, {@link #oversizedPngDimensionsPackage()},
 * {@link #invalidPngSignaturePackage()} und {@link #symlinkEntryPackage()}.</p>
 *
 * <p>Fuer GATE30/GATE31 (Endgate-Runde 1) ergaenzt um
 * {@link #forgedSmallDeclaredLargeRealSizePackage()}: ein gefaelschtes
 * zentrales Verzeichnis, das eine winzige Groesse deklariert, waehrend der
 * tatsaechliche Inhalt darueber liegt.</p>
 */
public final class VpkgFixtures {

	/** Name des Manifest-Eintrags, den ein echtes {@code .vpkg} laut Roadmap traegt. */
	public static final String MANIFEST_ENTRY_NAME = "vitrine.json";

	private VpkgFixtures() {
	}

	// --- gutartige Fixtures --------------------------------------------

	/**
	 * Minimales, in sich plausibles Paket: ein Manifest plus genau eine
	 * Textur-Ressource. Der reale Manifest-Aufbau folgt erst mit P1 (Schema);
	 * hier reicht ein plausibles, von Hand geschriebenes JSON-Dokument.
	 */
	public static byte[] minimalValidPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.minimalpaket", "Minimalpaket", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/example.png", fakePngBytes());
		return buildZip(entries);
	}

	/**
	 * Paket mit mehreren Ressourcen unterschiedlicher Art (zwei Texturen,
	 * ein Sound, eine zusaetzliche Sprachdatei) -- deckt ab, dass ein
	 * spaeterer .vpkg-Leser (P2) mit mehr als einem Eintrag umgehen muss.
	 */
	public static byte[] multiResourcePackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.mehrfachpaket", "Mehrfachpaket", "bundle").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/gui/erste.png", fakePngBytes());
		entries.put("textures/gui/zweite.png", fakePngBytes());
		entries.put("sounds/klick.ogg", fakeOggBytes());
		entries.put("lang/de_de.json", "{\"vitrine.beispiel\":\"Hallo\"}".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	// --- boesartige Fixtures ---------------------------------------------

	/**
	 * Boesartig: ein Eintrag, dessen Name per {@code ../}-Traversal aus dem
	 * vorgesehenen Installationsordner ausbrechen will (klassisches
	 * Zip-Slip). Ein spaeterer .vpkg-Leser/Installer (P2/P3) darf diesen
	 * Eintrag niemals ausserhalb der Sandbox schreiben.
	 */
	public static byte[] zipSlipPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.zipslip", "Zip-Slip-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("../../../evil.txt", "diese Datei soll aus der Sandbox ausbrechen".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig (GATE108): ein Eintrag, dessen Pfadsegmente nur aus Punkten
	 * bestehen ({@code "...."}), statt woertlich {@code ".."} zu heissen.
	 * Unter Windows behandeln Explorer-Adressleiste und {@code cmd.exe} eine
	 * Folge von N Punkten historisch wie N-1 {@code ..}-Spruenge nach oben --
	 * ein rein woertlicher Vergleich gegen {@code ".."} haette diesen Eintrag
	 * durchgelassen.
	 */
	public static byte[] dotsOnlyTraversalPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.punkttraversal", "Punkt-Traversal-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("..../..../evil.txt", "Punkte statt woertlichem '..'".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig (GATE108): ein Eintrag, dessen Name (vor der Endung) exakt
	 * einem reservierten Windows-Geraetenamen entspricht. Heute ungefaehrlich
	 * (kein Code loest einen Eintragsnamen gegen das Dateisystem auf), wuerde
	 * aber bei einem spaeteren Entpacker unter Windows kein normales Ziel mehr
	 * adressieren.
	 */
	public static byte[] reservedWindowsDeviceNamePackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.geraetename", "Reservierter-Geraetename-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("assets/vitrine/CON", "reservierter Windows-Geraetename".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig: Eintragsnamen, die selbst absolute Pfade sind (Windows-
	 * Laufwerksbuchstabe und Unix-Wurzelpfad), statt relativ zum
	 * Paket-Wurzelverzeichnis zu bleiben.
	 */
	public static byte[] absolutePathEntryPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.absoluterpfad", "Absoluter-Pfad-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("C:\\Windows\\System32\\evil.dll", "windows-artiger absoluter Pfad".getBytes(StandardCharsets.UTF_8));
		entries.put("/etc/passwd", "unix-artiger absoluter Pfad".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig: ein Eintrag mit einem Kompressionsverhaeltnis weit jenseits
	 * dessen, was eine echte Textur/ein echter Sound je erreicht (Andeutung
	 * einer Zip-Bombe -- die eigentliche Verhaeltnis-/Groessenpruefung
	 * kommt erst mit P3). Der unkomprimierte Inhalt ist stark komprimierbar
	 * (Nullbytes), der komprimierte Eintrag bleibt winzig.
	 */
	public static byte[] extremeCompressionRatioPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.zipbombe", "Zip-Bomben-Andeutung", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/bombe.png", new byte[EXTREME_UNCOMPRESSED_SIZE]);
		return buildZip(entries);
	}

	/** Unkomprimierte Groesse des Zip-Bomben-Eintrags: 20 MiB Nullbytes. */
	public static final int EXTREME_UNCOMPRESSED_SIZE = 20 * 1024 * 1024;

	/**
	 * Boesartig (P3): ein Eintrag, dessen im zentralen Verzeichnis deklarierte
	 * unkomprimierte Groesse ueber der Grenze
	 * {@code VpkgSecurityValidator.MAX_TOTAL_UNCOMPRESSED_BYTES} (256 MiB)
	 * liegt. {@code VpkgSecurityValidator} prueft diese Groesse rein ueber die
	 * ZIP-Metadaten ({@code VpkgEntry#size()}), ohne den Eintrag jemals zu
	 * entpacken -- deshalb reicht es, die deklarierte Groesse im zentralen
	 * Verzeichnis von Hand auf einen Wert jenseits der Grenze zu setzen,
	 * waehrend der tatsaechlich im Archiv liegende Inhalt winzig bleibt (kein
	 * mehrere hundert Megabyte grosses Test-Fixture noetig). Deklarierte
	 * unkomprimierte und komprimierte Groesse sind gleich gesetzt, damit
	 * ausschliesslich die Gesamtgroessen-, nicht die Verhaeltnispruefung
	 * anschlaegt.
	 */
	public static byte[] totalUncompressedSizeExceededPackage() {
		byte[] content = "winziger tatsaechlicher Inhalt".getBytes(StandardCharsets.UTF_8);
		return buildRawSingleEntryZip("textures/riesig-deklariert.txt", content,
				TOTAL_SIZE_OVER_LIMIT_BYTES, TOTAL_SIZE_OVER_LIMIT_BYTES, null);
	}

	/** Bewusst ueber der P3-Grenze von 256 MiB, siehe {@link #totalUncompressedSizeExceededPackage()}. */
	public static final long TOTAL_SIZE_OVER_LIMIT_BYTES = 257L * 1024 * 1024;

	/**
	 * Boesartig (GATE30): das genaue Gegenteil von
	 * {@link #totalUncompressedSizeExceededPackage()} -- die im zentralen
	 * Verzeichnis deklarierte unkomprimierte Groesse ist winzig
	 * ({@link #FORGED_DECLARED_SIZE_BYTES}), waehrend der Eintrag beim
	 * tatsaechlichen Entpacken {@link #FORGED_REAL_CONTENT_SIZE} Bytes liefert --
	 * ein gefaelschtes zentrales Verzeichnis genau wie im GATE30-Befund
	 * ("klein deklariert, riesig real").
	 *
	 * <p><b>Warum DEFLATE, nicht STORED:</b> Ein erster Versuch dieser Fixture
	 * nutzte STORED (Kompressionsmethode 0) mit einem echten Inhalt von
	 * {@code FORGED_REAL_CONTENT_SIZE} Bytes im lokalen Dateikopf, aber einer
	 * winzigen deklarierten Groesse im zentralen Verzeichnis. Das deckt die
	 * Schwachstelle NICHT auf: {@code java.util.zip.ZipFile} begrenzt den
	 * zurueckgegebenen Stream eines STORED-Eintrags auf die im zentralen
	 * Verzeichnis deklarierte (komprimierte = unkomprimierte) Groesse -- die
	 * ueberschuessigen Bytes im physischen Archiv werden beim Lesen schlicht
	 * ignoriert, der Deckel wird nie ueberschritten.</p>
	 *
	 * <p>Bei DEFLATE (Kompressionsmethode 8) gilt das nicht: {@code ZipFile}
	 * entpackt ueber einen {@code Inflater}, bis der komprimierte Bytestrom
	 * (dessen Laenge ueber die -- hier korrekte -- deklarierte komprimierte
	 * Groesse begrenzt wird) vollstaendig verarbeitet ist, VOELLIG unabhaengig
	 * davon, was als unkomprimierte Groesse deklariert ist. Diese Fixture
	 * dekomprimiert echten, stark komprimierbaren Inhalt ({@code 'A'}-Bytes,
	 * {@link #FORGED_REAL_CONTENT_SIZE} Bytes) mit {@link Deflater} von Hand zu
	 * einem winzigen, aber gueltigen DEFLATE-Strom, schreibt dessen tatsaechliche
	 * Laenge korrekt als komprimierte Groesse, deklariert aber im lokalen
	 * Dateikopf UND im zentralen Verzeichnis nur
	 * {@link #FORGED_DECLARED_SIZE_BYTES} Bytes unkomprimiert -- die Luege.
	 * {@code ZipFile}/{@code VpkgArchive#openEntry} liefern beim tatsaechlichen
	 * Lesen trotzdem alle {@link #FORGED_REAL_CONTENT_SIZE} Bytes, weil der
	 * Inflater den kompletten gueltigen komprimierten Strom verarbeitet.</p>
	 */
	public static byte[] forgedSmallDeclaredLargeRealSizePackage() {
		byte[] realContent = new byte[FORGED_REAL_CONTENT_SIZE];
		java.util.Arrays.fill(realContent, (byte) 'A');
		return buildRawSingleDeflateEntryZip("textures/klein-deklariert-real-riesig.txt", realContent,
				FORGED_DECLARED_SIZE_BYTES);
	}

	/** Deklarierte unkomprimierte Groesse in {@link #forgedSmallDeclaredLargeRealSizePackage()} -- absichtlich winzig, die Luege. */
	public static final long FORGED_DECLARED_SIZE_BYTES = 10L;

	/**
	 * Boesartig (GATE107): ein Eintrag namens {@code "evil/"} -- der Name
	 * endet auf {@code /}, also meldet {@link java.util.zip.ZipEntry#isDirectory()}
	 * (und damit {@link VpkgEntry#directory()}) "Verzeichnis", obwohl der
	 * Eintrag {@link #FORGED_REAL_CONTENT_SIZE} Bytes echten, tatsaechlich
	 * dekomprimierbaren Inhalt traegt. Die DEKLARIERTE unkomprimierte Groesse
	 * ist 0 -- genau das, was ein echtes (immer leeres) Verzeichnis deklarieren
	 * wuerde. Ein Validator, der Eintraege allein anhand dieses Namensmusters
	 * von der tatsaechlichen Groessenpruefung ausnimmt, zaehlt diesen Eintrag
	 * nie mit und laesst beliebig viel echten Inhalt am Lesedeckel vorbei.
	 */
	public static byte[] directoryNamedEntryWithRealPayloadPackage() {
		byte[] realContent = new byte[FORGED_REAL_CONTENT_SIZE];
		java.util.Arrays.fill(realContent, (byte) 'B');
		return buildRawSingleDeflateEntryZip("evil/", realContent, 0L);
	}

	/**
	 * Tatsaechliche Anzahl Bytes, die {@link #forgedSmallDeclaredLargeRealSizePackage()}
	 * beim Entpacken (Inflate) wirklich liefert -- weit ueber
	 * {@link #FORGED_DECLARED_SIZE_BYTES}.
	 */
	public static final int FORGED_REAL_CONTENT_SIZE = 64 * 1024;

	/** Eintragsname in {@link #storedEntryDeclaringMoreThanPhysicallyPresentPackage()}. */
	public static final String STORED_TRUST_ENTRY_NAME = "textures/stored-trust-ohne-echtes-lesen.txt";

	/**
	 * Boesartig/Grenzfall (GATE109): ein {@code STORED}-Eintrag (Kompressions-
	 * methode 0), dessen zentrales Verzeichnis
	 * {@link #STORED_TRUST_DECLARED_SIZE_BYTES} Bytes deklariert UND dessen
	 * "relative offset of local header" auf {@link #STORED_TRUST_BOGUS_LOCAL_HEADER_OFFSET}
	 * gefaelscht ist -- weit ausserhalb der tatsaechlichen, nur
	 * {@link #STORED_TRUST_REAL_CONTENT_SIZE} Bytes grossen physischen Datei.
	 * Eine blosse Groessenluege (korrekter Offset) reicht dafuer NICHT: nach
	 * echtem JDK-Verhalten (siehe {@code VpkgSecurityValidatorTest}) liest
	 * {@code java.util.zip.ZipFile} einen solchen Eintrag klaglos bis zum
	 * wahren physischen Dateiende durch und liefert nur weniger Bytes als
	 * deklariert -- ohne jede {@link java.io.IOException}, weil ein
	 * STORED-Eintrag durch die physische Dateigroesse bereits hart begrenzt
	 * ist. Der zusaetzlich gefaelschte Offset bildet dagegen ein echtes,
	 * korruptes zentrales Verzeichnis nach (lesbar/parsebar, aber ohne
	 * referenzierbaren lokalen Dateikopf): tatsaechliches Oeffnen/Lesen dieses
	 * Eintrags (z.B. ueber {@link de.spassglas.vitrine.vpkg.VpkgArchive#openEntry(String, long)})
	 * scheitert deshalb zuverlaessig mit einer {@link java.io.EOFException}.
	 * Fuer GATE109 beweist das: {@code VpkgSecurityValidator} darf einen
	 * STORED-Eintrag NICHT oeffnen, um die deklarierte Groesse zu vertrauen
	 * (ZIP-Garantie: fuer STORED gilt {@code compressedSize == size}) -- tut
	 * er es doch, schlaegt die Pruefung an genau dieser Datei fehl, statt ihn
	 * (zu Recht) als innerhalb des Budgets durchzulassen.
	 */
	public static byte[] storedEntryDeclaringMoreThanPhysicallyPresentPackage() {
		byte[] realContent = new byte[STORED_TRUST_REAL_CONTENT_SIZE];
		java.util.Arrays.fill(realContent, (byte) 'C');
		return buildRawSingleEntryZip(STORED_TRUST_ENTRY_NAME, realContent, STORED_TRUST_DECLARED_SIZE_BYTES,
				STORED_TRUST_DECLARED_SIZE_BYTES, null, STORED_TRUST_BOGUS_LOCAL_HEADER_OFFSET);
	}

	/**
	 * Gefaelschter "relative offset of local header" in
	 * {@link #storedEntryDeclaringMoreThanPhysicallyPresentPackage()} -- weit
	 * ausserhalb der tatsaechlichen (winzigen) physischen Datei, siehe dortige
	 * Klassendoku.
	 */
	private static final long STORED_TRUST_BOGUS_LOCAL_HEADER_OFFSET = 1_000_000L;

	/** Deklarierte Groesse in {@link #storedEntryDeclaringMoreThanPhysicallyPresentPackage()} -- weit mehr, als physisch vorhanden ist. */
	public static final long STORED_TRUST_DECLARED_SIZE_BYTES = 5_000_000L;

	/** Tatsaechlich im Archiv vorhandene Bytes in {@link #storedEntryDeclaringMoreThanPhysicallyPresentPackage()}. */
	public static final int STORED_TRUST_REAL_CONTENT_SIZE = 16;

	/**
	 * Boesartig (P3): mehr Eintraege als die Grenze
	 * {@code VpkgSecurityValidator.MAX_ENTRY_COUNT} (20000) erlaubt. Jeder
	 * zusaetzliche Eintrag ist leer, damit der Test trotz der hohen Anzahl
	 * schnell bleibt.
	 */
	public static byte[] entryCountExceededPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.zipbombeanzahl", "Zip-Bomben-Eintragsanzahl", "texturen").getBytes(StandardCharsets.UTF_8));
		for (int i = 0; i < ENTRY_COUNT_OVER_LIMIT; i++) {
			entries.put("filler/entry-" + i + ".txt", EMPTY_BYTES);
		}
		return buildZip(entries);
	}

	/** Anzahl zusaetzlicher (leerer) Eintraege in {@link #entryCountExceededPackage()}, ueber der P3-Grenze von 20000. */
	public static final int ENTRY_COUNT_OVER_LIMIT = 20_001;

	private static final byte[] EMPTY_BYTES = new byte[0];

	/**
	 * Boesartig (P3): Eintraege mit auf Klasse A verbotenen Dateiendungen
	 * (ausfuehrbarer Code/Skripte).
	 */
	public static byte[] forbiddenFileTypePackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.verbotenertyp", "Verbotener-Dateityp-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("scripts/payload.sh", "#!/bin/sh\necho boese\n".getBytes(StandardCharsets.UTF_8));
		entries.put("bin/install.exe", new byte[] { 'M', 'Z' });
		return buildZip(entries);
	}

	/**
	 * Boesartig (P3): ein {@code *.png}-Eintrag, dessen IHDR-Chunk Breite/Hoehe
	 * ueber der Grenze {@code VpkgSecurityValidator.MAX_PNG_DIMENSION} (4096)
	 * nennt -- ohne dass irgendein Decoder das Bild dafuer entpacken muesste.
	 */
	public static byte[] oversizedPngDimensionsPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.pngmasse", "PNG-Massgrenzen-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/riesig.png", pngHeaderWithDimensions(8192, 8192));
		return buildZip(entries);
	}

	/**
	 * Boesartig (P3, GATE111): ein {@code *.jpg}-Eintrag, dessen SOF0-Segment
	 * Breite/Hoehe ueber der Grenze {@code VpkgSecurityValidator.MAX_IMAGE_DIMENSION}
	 * (4096) nennt -- ohne dass irgendein Decoder das Bild dafuer entpacken
	 * muesste. Beweist, dass die Massenpruefung (vorher nur fuer {@code .png})
	 * auch fuer JPEG gilt.
	 */
	public static byte[] oversizedJpegDimensionsPackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.jpgmasse", "JPEG-Massgrenzen-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/riesig.jpg", jpegHeaderWithDimensions(8192, 8192));
		return buildZip(entries);
	}

	/**
	 * Boesartig (P3, GATE111): ein Eintrag mit der Endung {@code .jpg}, dessen
	 * Inhalt gar kein JPEG ist (keine gueltige SOI-Signatur).
	 */
	public static byte[] invalidJpegSignaturePackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.jpgsignatur", "JPEG-Signatur-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/gefaelscht.jpg", "das ist kein JPEG, nur Text mit .jpg-Endung".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig (P3): ein Eintrag mit der Endung {@code .png}, dessen Inhalt
	 * gar kein PNG ist (keine gueltige PNG-Signatur).
	 */
	public static byte[] invalidPngSignaturePackage() {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(
				"beispiel.pngsignatur", "PNG-Signatur-Angriff", "texturen").getBytes(StandardCharsets.UTF_8));
		entries.put("textures/gefaelscht.png", "das ist kein PNG, nur Text mit .png-Endung".getBytes(StandardCharsets.UTF_8));
		return buildZip(entries);
	}

	/**
	 * Boesartig (P3): ein ZIP-Eintrag, dessen "external file attributes" im
	 * zentralen Verzeichnis einen Unix-Symlink markieren ({@code S_IFLNK}).
	 * {@code java.util.zip.ZipOutputStream}/{@code jdk.nio.zipfs} koennen
	 * selbst keinen solchen Eintrag *erzeugen* (empirisch geprueft --
	 * {@code UnsupportedOperationException}), deshalb baut diese Fixture die
	 * ZIP-Bytes von Hand nach PKZIP-APPNOTE-Format (lokaler Dateikopf,
	 * zentraler Verzeichniseintrag mit Host-System "Unix" und passenden
	 * external attributes, End-of-Central-Directory). {@code ZipFile} liest
	 * den Eintrag dabei trotzdem ganz normal als Datei -- die external
	 * attributes werden von der JDK-API beim Lesen nicht ausgewertet. Genau
	 * deshalb braucht {@code VpkgZipCentralDirectoryReader} eine eigene
	 * Rohbyte-Pruefung, gegen die diese Fixture testet.
	 */
	public static byte[] symlinkEntryPackage() {
		byte[] content = "../../../etc/passwd".getBytes(StandardCharsets.UTF_8);
		return buildRawSingleEntryZip("link-to-secret.txt", content, content.length, content.length,
				UNIX_MODE_SYMLINK);
	}

	// --- Rohbyte-ZIP-Aufbau fuer Faelle, die java.util.zip nicht bauen kann ---

	/** Unix-Modus-Bits fuer einen Symlink: {@code S_IFLNK (0xA000) | 0777}. */
	private static final int UNIX_MODE_SYMLINK = 0xA1FF;

	/** Host-System-Kennung "Unix" im oberen Byte von "version made by" (siehe VpkgZipCentralDirectoryReader). */
	private static final int ZIP_HOST_SYSTEM_UNIX = 3;

	/**
	 * Baut ein einzelnes ZIP-Archiv mit genau einem Eintrag komplett von Hand
	 * (STORED, unkomprimiert) -- lokaler Dateikopf, zentraler Verzeichnis-
	 * eintrag, End-of-Central-Directory, alles selbst geschrieben. Erlaubt
	 * zwei Dinge, die {@link ZipOutputStream} nicht kann:
	 *
	 * <ol>
	 *   <li>{@code declaredUncompressedSize}/{@code declaredCompressedSize} im
	 *       zentralen Verzeichnis unabhaengig von der tatsaechlichen Laenge
	 *       von {@code content} setzen -- damit ein Zip-Bomben-Groessentest
	 *       (siehe {@link #totalUncompressedSizeExceededPackage()}) keine
	 *       hunderte Megabyte grosse Testdatei braucht, obwohl
	 *       {@code VpkgSecurityValidator} nur die deklarierte Groesse liest,
	 *       nie den Inhalt.</li>
	 *   <li>{@code unixMode} (z.B. {@code S_IFLNK} fuer einen Symlink) direkt
	 *       in die "external file attributes" schreiben -- {@code
	 *       ZipOutputStream}/{@code jdk.nio.zipfs} koennen das nicht (siehe
	 *       {@link #symlinkEntryPackage()}).</li>
	 * </ol>
	 *
	 * @param unixMode Unix-{@code st_mode}-Bits fuer die external file
	 *                  attributes, oder {@code null} fuer einen gewoehnlichen
	 *                  Eintrag ohne Unix-Host-Kennung (dann ueberspringt
	 *                  {@code VpkgZipCentralDirectoryReader} die
	 *                  Symlink-Pruefung fuer diesen Eintrag, wie bei jedem
	 *                  ueber {@link #buildZip(Map)} gebauten Archiv auch).
	 */
	private static byte[] buildRawSingleEntryZip(String entryName, byte[] content, long declaredUncompressedSize,
			long declaredCompressedSize, Integer unixMode) {
		return buildRawSingleEntryZip(entryName, content, declaredUncompressedSize, declaredCompressedSize, unixMode,
				0L);
	}

	/**
	 * Wie {@link #buildRawSingleEntryZip(String, byte[], long, long, Integer)},
	 * aber mit einem explizit uebergebenen "relative offset of local header" im
	 * zentralen Verzeichnis statt des wahren Wertes {@code 0} -- gedacht fuer
	 * {@link #storedEntryDeclaringMoreThanPhysicallyPresentPackage()} (GATE109):
	 * eine blosse Groessenluege (korrekter Offset, nur die Groessenfelder
	 * gefaelscht) liest {@code java.util.zip.ZipFile} beim tatsaechlichen
	 * Lesen klaglos bis zum wahren physischen Dateiende durch und liefert dann
	 * schlicht WENIGER Bytes als deklariert zurueck -- OHNE jede
	 * {@link IOException} (nachgewiesen gegen das echte JDK-Verhalten: ein
	 * STORED-Eintrag ist nie eine Dekompressions-Bombe, seine gelieferten
	 * Bytes sind durch die physische Dateigroesse hart begrenzt). Ein
	 * zusaetzlich auf einen ausserhalb der Datei liegenden Offset gefaelschter
	 * Eintrag bildet dagegen ein wirklich korruptes zentrales Verzeichnis nach
	 * (das zentrale Verzeichnis selbst bleibt lesbar/parsebar, nur der
	 * referenzierte lokale Dateikopf existiert nicht) und lAesst
	 * {@code ZipFile.getInputStream(...).read(...)} zuverlaessig mit einer
	 * {@link java.io.EOFException} (einer {@link IOException}) scheitern,
	 * sobald tatsaechlich gelesen wird -- genau der Beweis, den GATE109
	 * braucht: {@code VpkgSecurityValidator} darf diesen Eintrag fuer STORED
	 * NIE oeffnen.
	 */
	private static byte[] buildRawSingleEntryZip(String entryName, byte[] content, long declaredUncompressedSize,
			long declaredCompressedSize, Integer unixMode, long relativeOffsetOfLocalHeader) {
		byte[] nameBytes = entryName.getBytes(StandardCharsets.UTF_8);
		CRC32 crc32 = new CRC32();
		crc32.update(content);
		long crc = crc32.getValue();

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// lokaler Dateikopf -- traegt die tatsaechliche Laenge von "content",
		// die central-directory-Groessen (unten) koennen davon abweichen.
		writeInt32LE(out, 0x04034b50L);
		writeInt16LE(out, 20); // version needed to extract
		writeInt16LE(out, 0); // general purpose bit flag
		writeInt16LE(out, 0); // compression method: stored
		writeInt16LE(out, 0); // last mod file time
		writeInt16LE(out, 0x21); // last mod file date (1.1.1980)
		writeInt32LE(out, crc);
		writeInt32LE(out, content.length); // compressed size (lokal)
		writeInt32LE(out, content.length); // uncompressed size (lokal)
		writeInt16LE(out, nameBytes.length);
		writeInt16LE(out, 0); // extra field length
		out.write(nameBytes, 0, nameBytes.length);
		out.write(content, 0, content.length);

		int centralDirectoryOffset = out.size();

		int versionMadeBy = unixMode != null ? ((ZIP_HOST_SYSTEM_UNIX << 8) | 20) : 20;
		long externalAttributes = unixMode != null ? (((long) unixMode) << 16) : 0L;

		writeInt32LE(out, 0x02014b50L);
		writeInt16LE(out, versionMadeBy);
		writeInt16LE(out, 20); // version needed to extract
		writeInt16LE(out, 0); // general purpose bit flag
		writeInt16LE(out, 0); // compression method
		writeInt16LE(out, 0); // last mod file time
		writeInt16LE(out, 0x21); // last mod file date
		writeInt32LE(out, crc);
		writeInt32LE(out, declaredCompressedSize);
		writeInt32LE(out, declaredUncompressedSize);
		writeInt16LE(out, nameBytes.length);
		writeInt16LE(out, 0); // extra field length
		writeInt16LE(out, 0); // file comment length
		writeInt16LE(out, 0); // disk number start
		writeInt16LE(out, 0); // internal file attributes
		writeInt32LE(out, externalAttributes);
		writeInt32LE(out, relativeOffsetOfLocalHeader); // relative offset of local header
		out.write(nameBytes, 0, nameBytes.length);

		int centralDirectorySize = out.size() - centralDirectoryOffset;

		writeInt32LE(out, 0x06054b50L);
		writeInt16LE(out, 0); // number of this disk
		writeInt16LE(out, 0); // disk where central directory starts
		writeInt16LE(out, 1); // central directory records on this disk
		writeInt16LE(out, 1); // total central directory records
		writeInt32LE(out, centralDirectorySize);
		writeInt32LE(out, centralDirectoryOffset);
		writeInt16LE(out, 0); // comment length

		return out.toByteArray();
	}

	/**
	 * Baut ein einzelnes ZIP-Archiv mit genau einem DEFLATE-komprimierten
	 * Eintrag von Hand -- fuer {@link #forgedSmallDeclaredLargeRealSizePackage()}
	 * (GATE30/GATE31): {@code realUncompressedContent} wird mit {@link Deflater}
	 * zu einem echten, gueltigen komprimierten Bytestrom verdichtet; dessen
	 * TATSAECHLICHE Laenge steht korrekt als komprimierte Groesse im lokalen
	 * Dateikopf UND im zentralen Verzeichnis (sonst koennte {@code ZipFile} den
	 * Eintrag gar nicht mehr lesen), waehrend als unkomprimierte Groesse an
	 * beiden Stellen die Luege {@code declaredUncompressedSize} steht -- der
	 * CRC-32 wird ueber den echten unkomprimierten Inhalt berechnet, damit
	 * {@code ZipFile} beim vollstaendigen Lesen keinen (unabhaengig davon
	 * zusaetzlich zu pruefenden) Prueffehler wirft.
	 */
	private static byte[] buildRawSingleDeflateEntryZip(String entryName, byte[] realUncompressedContent,
			long declaredUncompressedSize) {
		byte[] nameBytes = entryName.getBytes(StandardCharsets.UTF_8);
		CRC32 crc32 = new CRC32();
		crc32.update(realUncompressedContent);
		long crc = crc32.getValue();

		byte[] compressed = deflateRaw(realUncompressedContent);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// lokaler Dateikopf -- komprimierte Groesse korrekt (sonst nicht lesbar),
		// unkomprimierte Groesse die Luege.
		writeInt32LE(out, 0x04034b50L);
		writeInt16LE(out, 20); // version needed to extract
		writeInt16LE(out, 0); // general purpose bit flag
		writeInt16LE(out, 8); // compression method: deflate
		writeInt16LE(out, 0); // last mod file time
		writeInt16LE(out, 0x21); // last mod file date (1.1.1980)
		writeInt32LE(out, crc);
		writeInt32LE(out, compressed.length); // compressed size (lokal, echt)
		writeInt32LE(out, declaredUncompressedSize); // uncompressed size (lokal) -- die Luege
		writeInt16LE(out, nameBytes.length);
		writeInt16LE(out, 0); // extra field length
		out.write(nameBytes, 0, nameBytes.length);
		out.write(compressed, 0, compressed.length);

		int centralDirectoryOffset = out.size();

		writeInt32LE(out, 0x02014b50L);
		writeInt16LE(out, 20); // version made by
		writeInt16LE(out, 20); // version needed to extract
		writeInt16LE(out, 0); // general purpose bit flag
		writeInt16LE(out, 8); // compression method: deflate
		writeInt16LE(out, 0); // last mod file time
		writeInt16LE(out, 0x21); // last mod file date
		writeInt32LE(out, crc);
		writeInt32LE(out, compressed.length); // compressed size (zentral, echt)
		writeInt32LE(out, declaredUncompressedSize); // uncompressed size (zentral) -- die Luege
		writeInt16LE(out, nameBytes.length);
		writeInt16LE(out, 0); // extra field length
		writeInt16LE(out, 0); // file comment length
		writeInt16LE(out, 0); // disk number start
		writeInt16LE(out, 0); // internal file attributes
		writeInt32LE(out, 0); // external file attributes
		writeInt32LE(out, 0); // relative offset of local header
		out.write(nameBytes, 0, nameBytes.length);

		int centralDirectorySize = out.size() - centralDirectoryOffset;

		writeInt32LE(out, 0x06054b50L);
		writeInt16LE(out, 0); // number of this disk
		writeInt16LE(out, 0); // disk where central directory starts
		writeInt16LE(out, 1); // central directory records on this disk
		writeInt16LE(out, 1); // total central directory records
		writeInt32LE(out, centralDirectorySize);
		writeInt32LE(out, centralDirectoryOffset);
		writeInt16LE(out, 0); // comment length

		return out.toByteArray();
	}

	/**
	 * Komprimiert {@code data} als rohen DEFLATE-Bytestrom (kein Zlib-Kopf/
	 * -Truemmer, {@code nowrap=true}) -- exakt das Format, das das ZIP-Format
	 * fuer Kompressionsmethode 8 erwartet.
	 */
	private static byte[] deflateRaw(byte[] data) {
		Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		deflater.setInput(data);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		while (!deflater.finished()) {
			int n = deflater.deflate(buffer);
			out.write(buffer, 0, n);
		}
		deflater.end();
		return out.toByteArray();
	}

	/** Baut die ersten 24 Bytes eines PNG-Kopfes (Signatur + IHDR-Praefix bis Breite/Hoehe) von Hand. */
	private static byte[] pngHeaderWithDimensions(int width, int height) {
		byte[] header = new byte[24];
		byte[] signature = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		System.arraycopy(signature, 0, header, 0, signature.length);
		header[8] = 0;
		header[9] = 0;
		header[10] = 0;
		header[11] = 13; // Chunk-Laenge der IHDR-Nutzdaten (nicht vom Validator geprueft)
		header[12] = 'I';
		header[13] = 'H';
		header[14] = 'D';
		header[15] = 'R';
		writeInt32BE(header, 16, width);
		writeInt32BE(header, 20, height);
		return header;
	}

	/**
	 * Baut ein minimales, aber fuer {@code checkJpegDimensions} vollstaendig
	 * gueltiges JPEG: SOI (0xFFD8) gefolgt von einem einzigen SOF0-Segment
	 * (0xFFC0) mit Praezisionsbyte, Hoehe, Breite und ein paar Fuell-Bytes fuer
	 * eine plausible Segmentlaenge. Kein Entropie-kodierter Bilddateninhalt --
	 * die Massenpruefung liest nie mehr als das SOF0-Segment, braucht also kein
	 * vollstaendiges, dekodierbares Bild.
	 */
	private static byte[] jpegHeaderWithDimensions(int width, int height) {
		byte[] header = new byte[21];
		header[0] = (byte) 0xFF;
		header[1] = (byte) 0xD8; // SOI
		header[2] = (byte) 0xFF;
		header[3] = (byte) 0xC0; // SOF0
		header[4] = 0x00;
		header[5] = 0x11; // Segmentlaenge = 17 (inkl. der beiden Laengenbytes)
		header[6] = 0x08; // Praezision
		header[7] = (byte) ((height >>> 8) & 0xFF);
		header[8] = (byte) (height & 0xFF);
		header[9] = (byte) ((width >>> 8) & 0xFF);
		header[10] = (byte) (width & 0xFF);
		// Reihe/Komponenten-Fuellbytes bis zur deklarierten Segmentlaenge (hier nicht geprueft).
		return header;
	}

	private static void writeInt32BE(byte[] target, int offset, int value) {
		target[offset] = (byte) ((value >>> 24) & 0xFF);
		target[offset + 1] = (byte) ((value >>> 16) & 0xFF);
		target[offset + 2] = (byte) ((value >>> 8) & 0xFF);
		target[offset + 3] = (byte) (value & 0xFF);
	}

	private static void writeInt16LE(ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
		out.write((value >>> 8) & 0xFF);
	}

	private static void writeInt32LE(ByteArrayOutputStream out, long value) {
		out.write((int) (value & 0xFF));
		out.write((int) ((value >>> 8) & 0xFF));
		out.write((int) ((value >>> 16) & 0xFF));
		out.write((int) ((value >>> 24) & 0xFF));
	}

	// --- generisches Werkzeug, fuer spaetere Fixtures (P1/P2/P3) nutzbar ---

	/**
	 * Baut ein ZIP-Archiv im Speicher aus den gegebenen Eintraegen
	 * (Reihenfolge bleibt erhalten). Schreibt absichtlich keine Datei auf
	 * die Platte -- reine {@link ByteArrayOutputStream}/{@link ZipOutputStream}
	 * -Verkettung, damit Tests auch fuer boesartige Namen nichts anlegen,
	 * das aufgeraeumt werden muesste.
	 */
	public static byte[] buildZip(Map<String, byte[]> entries) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				ZipEntry zipEntry = new ZipEntry(entry.getKey());
				zip.putNextEntry(zipEntry);
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		} catch (IOException e) {
			// Ein ByteArrayOutputStream kann beim Schreiben nicht scheitern
			// (kein echtes I/O beteiligt) -- wenn doch, ist das ein
			// Programmierfehler in dieser Fixture-Klasse, kein normaler
			// Laufzeitfall. Trotzdem geloggt statt verschluckt.
			throw new UncheckedIOException("Aufbau eines .vpkg-Test-Fixtures fehlgeschlagen", e);
		}
		return buffer.toByteArray();
	}

	private static String plausibleManifestJson(String id, String name, String category) {
		return "{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"" + id + "\",\n"
				+ "  \"name\": \"" + name + "\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"" + category + "\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\"\n"
				+ "}\n";
	}

	/**
	 * Gutartiges Paket wie {@link #minimalValidPackage()}, dessen Manifest
	 * zusaetzlich {@code capabilityIds} unter {@code capabilities} anmeldet --
	 * fuer S4-Tests, die den Faehigkeiten-Dialog/die Zustimmungspruefung vor der
	 * Installation abdecken (ohne angemeldete Faehigkeit gibt es nichts
	 * zuzustimmen). {@code manifestId} muss dem Schema-Muster genuegen (z. B.
	 * {@code "beispiel.faehigkeitspaket"}), {@code capabilityIds} den sieben in
	 * {@code vitrine.schema.json} erlaubten Bezeichnern.
	 */
	public static byte[] minimalValidPackageWithCapabilities(String manifestId, java.util.List<String> capabilityIds) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, manifestJsonWithCapabilities(manifestId, capabilityIds)
				.getBytes(StandardCharsets.UTF_8));
		entries.put("textures/example.png", fakePngBytes());
		return buildZip(entries);
	}

	/**
	 * Gutartiges Textur-Paket mit frei waehlbarer Manifest-ID/Name und einer
	 * beliebigen Menge an Ressourcen-Eintragspfaden (jeder mit einem plausiblen
	 * Fake-PNG-Inhalt, 16x16, weit unter der P3-Grenze) -- fuer S5-Tests, die
	 * zwei aktive Pakete mit einem <em>gemeinsamen</em> {@code assets/...}-Pfad
	 * (dieselbe ueberschriebene Textur) bzw. mit disjunkten Pfaden brauchen.
	 * Die {@code resourcePaths} werden woertlich als ZIP-Eintragsnamen benutzt.
	 */
	public static byte[] packageWithResourcePaths(String manifestId, String name,
			java.util.List<String> resourcePaths) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, plausibleManifestJson(manifestId, name, "texturen")
				.getBytes(StandardCharsets.UTF_8));
		for (String path : resourcePaths) {
			entries.put(path, fakePngBytes());
		}
		return buildZip(entries);
	}

	/**
	 * Wie {@link #packageWithResourcePaths(String, String, java.util.List)}, meldet
	 * aber zusaetzlich {@code capabilityIds} unter {@code capabilities} an --
	 * fuer GATE32-Tests, die einen konkreten Ressourcen-Pfad (z. B. eine
	 * GUI-Textur unter {@code assets/minecraft/textures/gui/...}) UND eine
	 * bestimmte (oder bewusst fehlende) Faehigkeit gleichzeitig brauchen.
	 */
	public static byte[] packageWithResourcePathsAndCapabilities(String manifestId, String name,
			java.util.List<String> resourcePaths, java.util.List<String> capabilityIds) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, manifestJsonWithCapabilities(manifestId, name, "texturen", capabilityIds)
				.getBytes(StandardCharsets.UTF_8));
		for (String path : resourcePaths) {
			entries.put(path, fakePngBytes());
		}
		return buildZip(entries);
	}

	/**
	 * Gutartiges Textur-Paket mit frei waehlbarer Manifest-ID/Name, expliziter
	 * SemVer-{@code version} und einer beliebigen Menge an Ressourcen-Pfaden --
	 * fuer K5-Tests, die <em>zwei Versionen desselben Pakets</em> brauchen (z. B.
	 * {@code 1.0.0} installiert, {@code 2.0.0} aus dem Fake-Katalog). {@code
	 * version} muss dem SemVer-Muster aus {@code vitrine.schema.json} genuegen.
	 */
	public static byte[] packageWithVersion(String manifestId, String name, String version,
			java.util.List<String> resourcePaths) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, ("{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"" + manifestId + "\",\n"
				+ "  \"name\": \"" + name + "\",\n"
				+ "  \"version\": \"" + version + "\",\n"
				+ "  \"category\": \"texturen\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\"\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8));
		for (String path : resourcePaths) {
			entries.put(path, fakePngBytes());
		}
		return buildZip(entries);
	}

	/**
	 * Gutartiges <em>Theme</em>-Paket (ROADMAP T4): Manifest mit
	 * {@code category: "theme"} plus eine {@code theme.json} im Wurzelverzeichnis
	 * (fester Pfad laut {@code ThemePackCoordinator}). {@code themeJson} wird
	 * woertlich als Eintrag {@code theme.json} abgelegt -- fuer den Kaputt-Fall
	 * einfach ungueltiges JSON uebergeben. Meldet {@code theme} als Faehigkeit an
	 * (GATE32: {@code ThemePackCoordinator} wendet ein Theme sonst gar nicht mehr
	 * an) -- Aufrufer muessen dieser Faehigkeit vor der Installation zustimmen,
	 * z. B. ueber {@code PackInstaller#confirmCapabilitiesAndInstall}.
	 */
	public static byte[] themePackage(String manifestId, String name, byte[] themeJson) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME,
				manifestJsonWithCapabilities(manifestId, name, "theme", java.util.List.of("theme"))
						.getBytes(StandardCharsets.UTF_8));
		entries.put("theme.json", themeJson);
		return buildZip(entries);
	}

	/** ZIP-Eintragspfad der {@code sounds.json}, die {@link #soundPackage(String, byte[])} ablegt (Vanilla-Layout). */
	public static final String SOUND_PACKAGE_SOUNDS_JSON_ENTRY = "assets/minecraft/sounds.json";

	/** ZIP-Eintragspfad der OGG-Datei, die {@link #soundPackage(String, byte[])} ablegt und die {@code sounds.json} referenziert. */
	public static final String SOUND_PACKAGE_OGG_ENTRY = "assets/minecraft/sounds/vitrine/hotswap_probe.ogg";

	/**
	 * Gutartiges <em>Sprachpaket</em> (ROADMAP T7): Manifest mit
	 * {@code category: "sprache"} plus eine Uebersetzungsdatei im
	 * Vanilla-Ressourcen-Layout {@code assets/minecraft/lang/<locale>.json}
	 * (PLAN.md Paragraph 2: ein {@code .vpkg} legt seine Ressourcen bereits unter
	 * {@code assets/<namespace>/...} ab). {@code entries} wird woertlich als
	 * flaches JSON-Objekt (Uebersetzungs-Key -&gt; Wert) geschrieben und
	 * ueberschreibt beim Anwenden die gleichnamigen Vanilla-Keys der Locale --
	 * das Zusammenfuehren mehrerer Pack-Ebenen erledigt Vanillas
	 * {@code NamespaceResourceManager} selbst. Ein {@code pack.mcmeta}-{@code
	 * language}-Block ist nur fuer eine voellig neue Locale noetig; das
	 * Ueberschreiben einer bereits vorhandenen Locale (T7-Fertig-Kriterium)
	 * kommt ohne aus. Meldet {@code sprache} als Faehigkeit an (GATE32: {@code
	 * VpkgResourcePack} liefert {@code lang/*.json} sonst gar nicht mehr aus).
	 */
	public static byte[] languagePackage(String locale, Map<String, String> entries) {
		LinkedHashMap<String, byte[]> zipEntries = new LinkedHashMap<>();
		zipEntries.put(MANIFEST_ENTRY_NAME, manifestJsonWithCapabilities(
				"beispiel.sprachpaket", "Sprachpaket", "sprache", java.util.List.of("sprache"))
				.getBytes(StandardCharsets.UTF_8));
		JsonObject langJson = new JsonObject();
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			langJson.addProperty(entry.getKey(), entry.getValue());
		}
		zipEntries.put("assets/minecraft/lang/" + locale + ".json",
				langJson.toString().getBytes(StandardCharsets.UTF_8));
		return buildZip(zipEntries);
	}

	/**
	 * Gutartiges <em>Soundpaket</em> (ROADMAP T7): Manifest mit
	 * {@code category: "sound"} plus {@code assets/minecraft/sounds.json}, das
	 * das Sound-Event {@code eventName} auf eine mitgelieferte OGG-Datei
	 * ({@link #SOUND_PACKAGE_OGG_ENTRY}) umbiegt -- beides gewoehnlicher
	 * {@code assets/<namespace>/...}-Inhalt, den der generische
	 * {@code VitrinePackProvider}-Pfad ohne Sonderbehandlung ausliefert; das
	 * Zusammenfuehren der {@code sounds.json} aller Packs erledigt Vanillas
	 * {@code SoundManager}. {@code oggBytes} ist der Inhalt der OGG-Datei -- fuer
	 * den Regelfall {@link #minimalOggBytes()} uebergeben (nur Container-Header,
	 * nicht abspielbar; der Test prueft Ressourcen-Zustellung, keine Wiedergabe).
	 * Meldet {@code sound} als Faehigkeit an (GATE32: {@code VpkgResourcePack}
	 * liefert {@code sounds.json}/{@code sounds/**} sonst gar nicht mehr aus).
	 */
	public static byte[] soundPackage(String eventName, byte[] oggBytes) {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put(MANIFEST_ENTRY_NAME, manifestJsonWithCapabilities(
				"beispiel.soundpaket", "Soundpaket", "sound", java.util.List.of("sound"))
				.getBytes(StandardCharsets.UTF_8));
		JsonObject soundsJson = new JsonObject();
		JsonObject event = new JsonObject();
		JsonArray sounds = new JsonArray();
		sounds.add("vitrine/hotswap_probe");
		event.add("sounds", sounds);
		soundsJson.add(eventName, event);
		entries.put(SOUND_PACKAGE_SOUNDS_JSON_ENTRY, soundsJson.toString().getBytes(StandardCharsets.UTF_8));
		entries.put(SOUND_PACKAGE_OGG_ENTRY, oggBytes.clone());
		return buildZip(entries);
	}

	private static String manifestJsonWithCapabilities(String id, java.util.List<String> capabilityIds) {
		return manifestJsonWithCapabilities(id, "Faehigkeitspaket", "texturen", capabilityIds);
	}

	/**
	 * Wie {@link #manifestJsonWithCapabilities(String, java.util.List)}, mit frei
	 * waehlbarem Namen und {@code category} -- fuer Fixtures wie {@link
	 * #themePackage(String, String, byte[])}, deren Kategorie nicht "texturen"
	 * ist, deren Faehigkeit aber trotzdem explizit angemeldet sein muss (GATE32).
	 */
	private static String manifestJsonWithCapabilities(String id, String name, String category,
			java.util.List<String> capabilityIds) {
		StringBuilder caps = new StringBuilder();
		for (int i = 0; i < capabilityIds.size(); i++) {
			if (i > 0) {
				caps.append(", ");
			}
			caps.append('"').append(capabilityIds.get(i)).append('"');
		}
		return "{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"" + id + "\",\n"
				+ "  \"name\": \"" + name + "\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"" + category + "\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"capabilities\": [" + caps + "]\n"
				+ "}\n";
	}

	/**
	 * Kein echter PNG-Decoder ist beteiligt -- nur ein plausibler Kopf mit
	 * gueltiger Signatur und IHDR-Chunk (16x16, weit unter der P3-Grenze von
	 * 4096) reicht, damit Tests die Datei als "Textur" erkennen UND
	 * {@code VpkgSecurityValidator} (P3) diese gutartigen Fixtures nicht
	 * faelschlich als ungueltiges PNG ablehnt.
	 */
	private static byte[] fakePngBytes() {
		return pngHeaderWithDimensions(16, 16);
	}

	/** Kein echter OGG-Decoder ist beteiligt -- {@link #minimalOggBytes()} reicht als Attrappe. */
	private static byte[] fakeOggBytes() {
		return minimalOggBytes();
	}

	/**
	 * Minimale, in sich plausible OGG-Container-Bytes: ein einzelner
	 * Ogg-Seitenkopf nach RFC 3533 Paragraph 6 -- Capture-Pattern {@code "OggS"},
	 * Strukturversion 0, Header-Typ "beginning of stream", danach Granule-
	 * Position (8), Bitstream-Serial (4), Seitenzahl (4), CRC (4), Anzahl
	 * Segmente (1) und eine Segmenttabelle mit einem 0-Byte-Segment. Kein
	 * Audio-Payload -- die Attrappe muss nicht abspielbar sein, T7-Tests pruefen
	 * die Ressourcen-Zustellung, nicht die Wiedergabe. {@code VpkgSecurityValidator}
	 * (P3) laesst {@code .ogg} als Klasse-A-Audioressource ohnehin durch (nicht
	 * auf der Endungs-Blockliste).
	 */
	public static byte[] minimalOggBytes() {
		byte[] page = new byte[28];
		page[0] = 'O';
		page[1] = 'g';
		page[2] = 'g';
		page[3] = 'S';
		page[4] = 0; // Strukturversion
		page[5] = 0x02; // Header-Typ: beginning of stream
		// page[6..25] bleiben 0: Granule-Position, Serial, Seitenzahl, CRC
		page[26] = 1; // eine Zeile in der Segmenttabelle
		page[27] = 0; // Laenge dieses Segments: 0 Bytes
		return page;
	}

	// --- Kryptografie-Hilfsmittel fuer P4 (Pruefsumme + Signatur) -----------

	/**
	 * Erzeugt ein frisches Ed25519-Schluesselpaar fuer P4-Tests
	 * (Signaturpruefung). Jeder Aufruf liefert ein neues, zufaelliges Paar --
	 * fuer Tests reicht das, ein echter Signaturschluessel liegt nie im Repo
	 * (Charter: "der private nie im Repo").
	 */
	public static KeyPair generateEd25519KeyPair() {
		try {
			return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			// Ed25519 ist seit JDK 15 nativ in java.security verfuegbar (siehe
			// VpkgIntegrityValidator) -- ein Fehlen ist eine kaputte
			// Laufzeitumgebung, kein normaler Testfall.
			throw new IllegalStateException("Ed25519 ist in dieser JDK-Laufzeitumgebung nicht verfuegbar", e);
		}
	}

	/**
	 * Signiert den SHA-256-Digest von {@code fileBytes} mit {@code privateKey}
	 * (Ed25519) -- exakt das, was {@code VpkgIntegrityValidator} spaeter gegen
	 * den oeffentlichen Schluessel prueft (Signatur ueber den Digest, nicht
	 * ueber die Rohbytes, siehe dortige Klassendoku).
	 */
	public static byte[] signSha256Digest(byte[] fileBytes, PrivateKey privateKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(fileBytes);
			Signature signer = Signature.getInstance("Ed25519");
			signer.initSign(privateKey);
			signer.update(digest);
			return signer.sign();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Test-Signatur (Ed25519) liess sich nicht erzeugen", e);
		}
	}

	/** SHA-256-Pruefsumme von {@code fileBytes} als Hex-Zeichenkette, fuer P4-Tests. */
	public static String sha256Hex(byte[] fileBytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(fileBytes);
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 ist in dieser JDK-Laufzeitumgebung nicht verfuegbar", e);
		}
	}
}
