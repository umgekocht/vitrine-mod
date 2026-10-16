package de.spassglas.vitrine.vpkg;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Liest ein {@code .vpkg}-Paket (ROADMAP P2) direkt aus dem ZIP-Archiv --
 * ohne einen einzigen Eintrag jemals auf die Platte zu entpacken. Nutzt
 * {@link ZipFile}, das nur das zentrale Verzeichnis einliest und einzelne
 * Eintraege erst bei Bedarf ({@link #openEntry(String)}) dekomprimiert.
 *
 * <p>Liefert genau das, was P2 laut Roadmap braucht: Eintragsliste
 * ({@link #entries()}), Ressourcen-Streams fuer beliebige Pfade im Archiv
 * (Texturen, Sounds, Sprachdateien, ...) sowie Zugriff auf {@code
 * vitrine.json} (roh, damit {@code ManifestParser} sie parst) und auf
 * Vorschaubilder -- die im Manifest per {@code icon}/{@code screenshots}
 * benannten relativen Pfade sind ganz normale Archiveintraege und werden
 * ueber dieselbe {@link #openEntry(String)}/{@link #readEntryBytes(String)}
 * Methode gelesen wie jede andere Ressource.</p>
 *
 * <p><b>Sicherheit:</b> Diese Klasse selbst prueft NICHTS gegen Angriffe
 * (Zip-Slip, absolute Pfade, {@code ..}-Traversal, Zip-Bomben, Symlinks,
 * verbotene Dateitypen, PNG-Massgrenzen) -- das leistet
 * {@link VpkgSecurityValidator} (P3). {@link #entries()} liefert dafuer alle
 * Rohdaten (Name unveraendert, unkomprimierte und komprimierte Groesse), und
 * {@link #path()} den Dateipfad fuer die Rohbyte-Pruefung des zentralen
 * Verzeichnisses. Ein Aufrufer, der ein Archiv tatsaechlich anwenden will,
 * MUSS {@code VpkgSecurityValidator.requireSafe(archive)} aufrufen, bevor er
 * auch nur einen einzigen {@link #openEntry(String)}-Stream fuer eine
 * Ressource anfordert, die spaeter geschrieben/angewendet wird -- kein
 * zurueckgegebener Eintragsname dieser Klasse gilt von sich aus als
 * sicher.</p>
 */
public final class VpkgArchive implements AutoCloseable {

	/** Name des Manifest-Eintrags in jedem {@code .vpkg} (siehe registry/schema). */
	public static final String MANIFEST_ENTRY_NAME = "vitrine.json";

	/**
	 * Harte Obergrenze fuer die tatsaechlich beim Lesen eines einzelnen
	 * Eintrags gezaehlten Bytes -- unabhaengig von der (manipulierbaren) im
	 * ZIP-Verzeichnis deklarierten {@link VpkgEntry#size()} (GATE30/GATE31:
	 * ein gefaelschtes zentrales Verzeichnis kann eine winzige Groesse
	 * deklarieren, waehrend der tatsaechliche Inhalt riesig ist). Wirkt wie
	 * der Deckel beim Download ({@code BoundedBody} in
	 * {@code de.spassglas.vitrine.catalog}): gezaehlt werden die TATSAECHLICH
	 * gelesenen Bytes am Stream, das Lesen bricht sofort ab, sobald das Limit
	 * ueberschritten ist -- nie ein unbegrenztes {@code readAllBytes()}, nie
	 * ein {@link OutOfMemoryError}. Gleicher Wert wie
	 * {@link VpkgSecurityValidator#MAX_TOTAL_UNCOMPRESSED_BYTES}: kein
	 * einzelner Eintrag darf beim tatsaechlichen Lesen mehr liefern als das
	 * gesamte Archiv-Budget.
	 */
	public static final long DEFAULT_MAX_ENTRY_READ_BYTES = 256L * 1024 * 1024;

	private final Path file;
	private final ZipFile zipFile;

	private VpkgArchive(Path file, ZipFile zipFile) {
		this.file = file;
		this.zipFile = zipFile;
	}

	/**
	 * Der Pfad, unter dem dieses Archiv geoeffnet wurde -- gedacht fuer
	 * {@code VpkgSecurityValidator} (P3), der das zentrale ZIP-Verzeichnis auf
	 * Rohbyte-Ebene nach Eintraegen ausserhalb der regulaeren Datei-/
	 * Verzeichnistypen (z.B. Symlinks) durchsucht, was {@link java.util.zip.ZipEntry}
	 * nicht abbildet.
	 */
	public Path path() {
		return file;
	}

	/**
	 * Oeffnet {@code file} als {@code .vpkg}-Archiv. Liest dabei nur das
	 * zentrale ZIP-Verzeichnis ein -- kein Eintrag wird dekomprimiert oder
	 * auf die Platte geschrieben.
	 *
	 * @throws IOException wenn {@code file} kein lesbares ZIP-Archiv ist
	 */
	public static VpkgArchive open(Path file) throws IOException {
		return new VpkgArchive(file, new ZipFile(file.toFile()));
	}

	/**
	 * Alle Eintraege im Archiv, in der Reihenfolge des zentralen
	 * Verzeichnisses (das entspricht bei einem regulaer geschriebenen ZIP
	 * der Reihenfolge, in der die Eintraege hineingeschrieben wurden).
	 * Enthaelt auch Verzeichniseintraege.
	 */
	public List<VpkgEntry> entries() {
		List<VpkgEntry> result = new ArrayList<>();
		zipFile.stream().forEach(entry -> result.add(toVpkgEntry(entry)));
		return Collections.unmodifiableList(result);
	}

	/** Ob das Archiv einen Eintrag mit genau diesem Namen enthaelt. */
	public boolean hasEntry(String path) {
		return zipFile.getEntry(path) != null;
	}

	/**
	 * Liefert einen Stream fuer den Eintrag {@code path}, direkt aus dem
	 * ZIP-Archiv dekomprimiert -- es wird nichts auf die Platte entpackt.
	 * Der Aufrufer muss den Stream schliessen. Begrenzt auf
	 * {@link #DEFAULT_MAX_ENTRY_READ_BYTES} tatsaechlich gelesene Bytes
	 * (GATE30/GATE31) -- siehe {@link #openEntry(String, long)}.
	 *
	 * @throws FileNotFoundException wenn kein Eintrag mit diesem Namen im
	 *                                 Archiv existiert
	 * @throws IOException           bei einem I/O- oder ZIP-Formatfehler
	 *                                 beim Dekomprimieren
	 * @throws VpkgEntryTooLargeException wenn beim spaeteren Lesen des
	 *                                 zurueckgegebenen Streams mehr als
	 *                                 {@link #DEFAULT_MAX_ENTRY_READ_BYTES}
	 *                                 tatsaechliche Bytes anfallen
	 */
	public InputStream openEntry(String path) throws IOException {
		return openEntry(path, DEFAULT_MAX_ENTRY_READ_BYTES);
	}

	/**
	 * Wie {@link #openEntry(String)}, aber mit einem explizit uebergebenen
	 * Byte-Deckel statt {@link #DEFAULT_MAX_ENTRY_READ_BYTES} -- package-
	 * private, gedacht fuer {@link VpkgSecurityValidator} (dessen
	 * Gesamt-Budget ueber alle Eintraege eines Archivs hinweg mit jedem
	 * gelesenen Eintrag schrumpft) und fuer Tests (ein kuenstlich kleiner
	 * Deckel deckt den Angriff auf, ohne dass ein Test hunderte Megabyte
	 * realen Inhalt bauen muss). Der zurueckgegebene Stream zaehlt jedes
	 * tatsaechlich gelesene Byte selbst -- unabhaengig von der deklarierten
	 * {@link VpkgEntry#size()} -- und wirft {@link VpkgEntryTooLargeException}
	 * (eine {@link IOException}), sobald {@code maxReadBytes} ueberschritten
	 * wird, statt weiterzulesen.
	 */
	public InputStream openEntry(String path, long maxReadBytes) throws IOException {
		ZipEntry entry = zipFile.getEntry(path);
		if (entry == null) {
			throw new FileNotFoundException(
					"Eintrag '" + path + "' fehlt im .vpkg-Archiv '" + file + "'");
		}
		return new BoundedEntryInputStream(zipFile.getInputStream(entry), file, path, maxReadBytes);
	}

	/**
	 * Liest einen Eintrag vollstaendig in ein Byte-Array -- gedacht fuer
	 * kleine Dateien wie das Manifest oder ein Icon, nicht fuer grosse
	 * Ressourcen (dafuer {@link #openEntry(String)} als Stream nutzen).
	 * Begrenzt auf {@link #DEFAULT_MAX_ENTRY_READ_BYTES} (GATE31): kein
	 * unbegrenztes {@code readAllBytes()} mehr, ein zu grosser Eintrag wirft
	 * {@link VpkgEntryTooLargeException} statt ein {@link OutOfMemoryError}
	 * zu riskieren.
	 */
	public byte[] readEntryBytes(String path) throws IOException {
		return readEntryBytes(path, DEFAULT_MAX_ENTRY_READ_BYTES);
	}

	/** Wie {@link #readEntryBytes(String)}, aber mit explizitem Byte-Deckel -- siehe {@link #openEntry(String, long)}. */
	byte[] readEntryBytes(String path, long maxReadBytes) throws IOException {
		try (InputStream in = openEntry(path, maxReadBytes)) {
			return in.readAllBytes();
		}
	}

	/**
	 * Bequemlichkeitsmethode: die rohen Bytes von {@code vitrine.json},
	 * unveraendert, damit {@code ManifestParser.parse(byte[])} sie direkt
	 * validieren kann.
	 */
	public byte[] readManifestBytes() throws IOException {
		return readEntryBytes(MANIFEST_ENTRY_NAME);
	}

	private static VpkgEntry toVpkgEntry(ZipEntry entry) {
		return new VpkgEntry(entry.getName(), entry.isDirectory(), entry.getSize(), entry.getCompressedSize(),
				entry.getMethod());
	}

	@Override
	public void close() throws IOException {
		zipFile.close();
	}

	/**
	 * Zaehlt jedes tatsaechlich vom Delegat-Stream gelesene Byte und wirft
	 * {@link VpkgEntryTooLargeException}, sobald mehr als {@code maxReadBytes}
	 * gelesen wurden -- unabhaengig davon, was das ZIP-Verzeichnis fuer diesen
	 * Eintrag deklariert (GATE30/GATE31: genau diese Deklaration ist bei einem
	 * boesartigen Archiv nicht vertrauenswuerdig). Analog zu
	 * {@code BoundedBody} beim Download: der Deckel greift am Stream, nicht
	 * erst hinterher gegen eine Metadaten-Zahl.
	 */
	private static final class BoundedEntryInputStream extends FilterInputStream {

		private final Path file;
		private final String entryName;
		private final long maxReadBytes;
		private long readSoFar;

		BoundedEntryInputStream(InputStream delegate, Path file, String entryName, long maxReadBytes) {
			super(delegate);
			this.file = file;
			this.entryName = entryName;
			this.maxReadBytes = maxReadBytes;
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b != -1) {
				count(1);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = super.read(b, off, len);
			if (read > 0) {
				count(read);
			}
			return read;
		}

		private void count(int n) throws IOException {
			readSoFar += n;
			if (readSoFar > maxReadBytes) {
				throw new VpkgEntryTooLargeException(file, entryName, maxReadBytes);
			}
		}
	}
}
