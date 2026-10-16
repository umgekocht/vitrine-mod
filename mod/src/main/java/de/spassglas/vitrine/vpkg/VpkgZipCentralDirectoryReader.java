package de.spassglas.vitrine.vpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Liest die "external file attributes" jedes Eintrags direkt aus dem
 * zentralen ZIP-Verzeichnis (rohe Bytes, PKZIP-APPNOTE-Format) -- das
 * einzige, was ueber Symlinks und andere Nicht-Datei-Eintraege Auskunft gibt.
 *
 * <p>{@link java.util.zip.ZipEntry}/{@link java.util.zip.ZipFile} aus dem
 * JDK bilden dieses Feld absichtlich NICHT ab (kein
 * {@code getExternalAttributes()} in der oeffentlichen API), obwohl genau
 * darin bei einem von einem Unix-Werkzeug geschriebenen ZIP der Unix-Dateityp
 * (regulaere Datei, Verzeichnis, Symlink, ...) steckt -- in den oberen 16 Bit
 * als {@code st_mode}, exakt wie {@code stat(2)} ihn liefert. Ein Symlink hat
 * dort {@code S_IFLNK = 0xA000}. Empirisch geprueft (Spike-Test): weder
 * {@code java.util.zip} noch das eingebaute {@code jdk.nio.zipfs} koennen
 * ueberhaupt einen Symlink-Eintrag *erzeugen* ({@code UnsupportedOperationException}) --
 * ein von Vitrine selbst erzeugtes {@code .vpkg} kann also nie einen echten
 * Symlink-Eintrag enthalten. Ein absichtlich boesartiges Archiv (von Hand
 * oder mit Info-ZIP/`zip -y` gebaut) kann es trotzdem, deshalb diese
 * eigenstaendige Pruefung.</p>
 *
 * <p>Liest dabei nur das zentrale Verzeichnis (End of Central Directory
 * rueckwaerts gesucht, dann die Eintraege ab dessen Offset) -- nicht die
 * komprimierten Eintragsdaten selbst. Die Groesse des zentralen Verzeichnisses
 * haengt nur von Eintragsanzahl und Namenslaenge ab, nicht von der
 * (unkomprimierten) Dateigroesse, bleibt also auch bei einer angedeuteten
 * Zip-Bombe klein.</p>
 */
final class VpkgZipCentralDirectoryReader {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgSecurity");

	private static final long EOCD_SIGNATURE = 0x06054b50L;
	private static final long CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L;
	private static final int MAX_EOCD_COMMENT_LENGTH = 0xFFFF;
	private static final int EOCD_FIXED_SIZE = 22;

	/** Host-System-Kennung "Unix" im oberen Byte von "version made by". */
	private static final int HOST_SYSTEM_UNIX = 3;

	private static final int UNIX_FILE_TYPE_MASK = 0xF000;
	private static final int UNIX_FILE_TYPE_REGULAR = 0x8000;
	private static final int UNIX_FILE_TYPE_DIRECTORY = 0x4000;

	private VpkgZipCentralDirectoryReader() {
	}

	/**
	 * Namen aller Eintraege, deren Unix-Dateityp (falls das Archiv ueberhaupt
	 * von einem Unix-Host geschrieben wurde) weder regulaere Datei noch
	 * Verzeichnis ist -- also Symlinks und alles andere Ungewoehnliche
	 * (Geraetedateien, FIFOs, Sockets). Liefert eine leere Menge, wenn das
	 * zentrale Verzeichnis nicht zuverlaessig gelesen werden konnte; die
	 * uebrigen P3-Pruefungen (Pfad, Groesse, Dateityp, PNG-Masse) laufen in
	 * diesem Fall trotzdem unveraendert weiter.
	 */
	static Set<String> findNonRegularEntryNames(Path zipFile) {
		try (RandomAccessFile raf = new RandomAccessFile(zipFile.toFile(), "r")) {
			long length = raf.length();
			long eocdOffset = findEndOfCentralDirectory(raf, length);
			if (eocdOffset < 0) {
				LOGGER.warn("VpkgSecurityValidator: End-of-Central-Directory in '{}' nicht gefunden -- "
						+ "Symlink-Pruefung uebersprungen, uebrige P3-Pruefungen laufen weiter.", zipFile);
				return Set.of();
			}
			raf.seek(eocdOffset + 16);
			long centralDirectoryOffset = readUnsignedInt32(raf);
			raf.seek(eocdOffset + 10);
			int totalEntries = readUnsignedInt16(raf);

			return readNonRegularEntryNames(raf, centralDirectoryOffset, totalEntries, zipFile);
		} catch (IOException e) {
			LOGGER.warn("VpkgSecurityValidator: zentrales ZIP-Verzeichnis von '{}' liess sich nicht lesen ({}) -- "
					+ "Symlink-Pruefung uebersprungen, uebrige P3-Pruefungen laufen weiter.", zipFile, e.toString());
			return Set.of();
		}
	}

	private static Set<String> readNonRegularEntryNames(RandomAccessFile raf, long centralDirectoryOffset,
			int totalEntries, Path zipFile) throws IOException {
		Set<String> nonRegular = new LinkedHashSet<>();
		raf.seek(centralDirectoryOffset);
		for (int i = 0; i < totalEntries; i++) {
			long signature = readUnsignedInt32(raf);
			if (signature != CENTRAL_DIRECTORY_SIGNATURE) {
				LOGGER.warn("VpkgSecurityValidator: unerwartete Signatur im zentralen Verzeichnis von '{}' "
						+ "bei Eintrag {} -- Symlink-Pruefung ab hier abgebrochen.", zipFile, i);
				break;
			}
			int versionMadeBy = readUnsignedInt16(raf);
			raf.skipBytes(4); // version needed, general purpose flag
			raf.skipBytes(4); // compression method, last mod time
			raf.skipBytes(2); // last mod date
			raf.skipBytes(4); // crc-32
			raf.skipBytes(4); // compressed size
			raf.skipBytes(4); // uncompressed size
			int nameLength = readUnsignedInt16(raf);
			int extraLength = readUnsignedInt16(raf);
			int commentLength = readUnsignedInt16(raf);
			raf.skipBytes(4); // disk number start, internal file attributes
			long externalAttributes = readUnsignedInt32(raf);
			raf.skipBytes(4); // relative offset of local header

			byte[] nameBytes = new byte[nameLength];
			raf.readFully(nameBytes);
			String name = new String(nameBytes, StandardCharsets.UTF_8);

			int hostSystem = (versionMadeBy >> 8) & 0xFF;
			if (hostSystem == HOST_SYSTEM_UNIX) {
				int fileTypeBits = (int) ((externalAttributes >> 16) & UNIX_FILE_TYPE_MASK);
				if (fileTypeBits != 0 && fileTypeBits != UNIX_FILE_TYPE_REGULAR
						&& fileTypeBits != UNIX_FILE_TYPE_DIRECTORY) {
					nonRegular.add(name);
				}
			}

			raf.skipBytes(extraLength + commentLength);
		}
		return nonRegular;
	}

	private static long findEndOfCentralDirectory(RandomAccessFile raf, long length) throws IOException {
		long maxSearch = Math.min(length, EOCD_FIXED_SIZE + MAX_EOCD_COMMENT_LENGTH);
		long searchStart = length - maxSearch;
		byte[] tail = new byte[(int) maxSearch];
		raf.seek(searchStart);
		raf.readFully(tail);
		for (int i = tail.length - EOCD_FIXED_SIZE; i >= 0; i--) {
			long candidate = unsignedInt32At(tail, i);
			if (candidate == EOCD_SIGNATURE) {
				return searchStart + i;
			}
		}
		return -1;
	}

	private static long unsignedInt32At(byte[] data, int offset) {
		return (data[offset] & 0xFFL)
				| ((data[offset + 1] & 0xFFL) << 8)
				| ((data[offset + 2] & 0xFFL) << 16)
				| ((data[offset + 3] & 0xFFL) << 24);
	}

	private static long readUnsignedInt32(RandomAccessFile raf) throws IOException {
		byte[] b = new byte[4];
		raf.readFully(b);
		return unsignedInt32At(b, 0);
	}

	private static int readUnsignedInt16(RandomAccessFile raf) throws IOException {
		byte[] b = new byte[2];
		raf.readFully(b);
		return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
	}
}
