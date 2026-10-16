package de.spassglas.vitrine.ui.image;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * GATE17: liest ein Vorschaubild ({@code icon} oder einen Screenshot) sicher
 * aus einem {@code .vpkg} -- client-frei und damit ohne laufendes Spiel
 * pruefbar. Die Umwandlung in eine Textur macht {@link PackageImageTextures}.
 *
 * <p>Reihenfolge der Riegel: das ganze Archiv besteht zuerst die
 * Sicherheitspruefung (P3: Zip-Slip, Zip-Bombe, PNG-Kopf je Eintrag); dann
 * nur PNG (der Spiel-Decoder kann nichts anderes); dann eine <b>engere</b>
 * Groessengrenze als P3. P3 erlaubt Texturen bis 4096x4096 -- als
 * Vorschaubild waeren das 64 MB Grafikspeicher je Kachel.</p>
 */
public final class PackageImageBytes {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Images");

	/** Groesste Breite/Hoehe eines Vorschaubilds in Pixeln. Die Spec nennt 512x288 fuer Kachelbilder. */
	public static final int MAX_PREVIEW_DIMENSION = 1024;

	/**
	 * GATE62: Deckel fuer die Bytes eines Vorschaubilds. Ein unkomprimiertes
	 * RGBA-Bild mit 1024x1024 hat 4 MiB; ein PNG darueber waere absichtlich
	 * aufgeblaeht.
	 */
	static final long MAX_PREVIEW_BYTES = 8L * 1024 * 1024;

	private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

	private PackageImageBytes() {
	}

	/** Bild-Bytes samt Maszen aus dem PNG-Kopf. */
	public record Image(byte[] png, int width, int height) {
	}

	/**
	 * Welches Bild eine Kachel zeigt: das Manifest-{@code icon}, sonst der
	 * erste Screenshot -- jeweils nur, wenn es ein PNG ist.
	 */
	public static Optional<String> tileImageEntry(Manifest manifest) {
		if (isPng(manifest.icon())) {
			return Optional.of(manifest.icon());
		}
		return manifest.screenshots().stream().filter(PackageImageBytes::isPng).findFirst();
	}

	/**
	 * Liest {@code entryPath} aus {@code vpkgFile}. Leer -- mit Grund im Log --,
	 * wenn das Archiv die Sicherheitspruefung nicht besteht, der Eintrag fehlt,
	 * kein PNG ist oder groesser als {@link #MAX_PREVIEW_DIMENSION} ist. Wirft nie.
	 */
	public static Optional<Image> read(Path vpkgFile, String entryPath) {
		if (!isPng(entryPath)) {
			return Optional.empty();
		}
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			if (!archive.hasEntry(entryPath)) {
				LOGGER.warn("Vorschaubild '{}' steht im Manifest, fehlt aber in '{}'.", entryPath, vpkgFile);
				return Optional.empty();
			}
			// GATE62: erst nur den PNG-Kopf lesen und die Masze pruefen, dann
			// (gedeckelt) den Rest -- nie erst ein riesiges Bild komplett laden.
			byte[] head;
			try (java.io.InputStream in = archive.openEntry(entryPath)) {
				head = in.readNBytes(24);
			}
			int[] size = pngSize(head);
			if (size == null) {
				LOGGER.warn("Vorschaubild '{}' in '{}' ist kein gueltiges PNG.", entryPath, vpkgFile);
				return Optional.empty();
			}
			if (size[0] > MAX_PREVIEW_DIMENSION || size[1] > MAX_PREVIEW_DIMENSION) {
				LOGGER.warn("Vorschaubild '{}' in '{}' ist {}x{} -- groesser als {} px, wird nicht geladen.",
						entryPath, vpkgFile, size[0], size[1], MAX_PREVIEW_DIMENSION);
				return Optional.empty();
			}
			byte[] bytes;
			try (java.io.InputStream in = archive.openEntry(entryPath, MAX_PREVIEW_BYTES)) {
				bytes = in.readAllBytes();
			}
			return Optional.of(new Image(bytes, size[0], size[1]));
		} catch (VpkgSecurityException e) {
			LOGGER.warn("'{}' hat die Sicherheitspruefung nicht bestanden -- kein Vorschaubild: {}",
					vpkgFile, e.getMessage());
			return Optional.empty();
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Vorschaubild '{}' aus '{}' nicht lesbar: {}", entryPath, vpkgFile, e.toString());
			return Optional.empty();
		}
	}

	/** Breite/Hoehe aus dem IHDR-Chunk, oder {@code null}, wenn es kein PNG ist. */
	static int[] pngSize(byte[] bytes) {
		if (bytes == null || bytes.length < 24) {
			return null;
		}
		for (int i = 0; i < PNG_SIGNATURE.length; i++) {
			if (bytes[i] != PNG_SIGNATURE[i]) {
				return null;
			}
		}
		if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
			return null;
		}
		int width = readInt(bytes, 16);
		int height = readInt(bytes, 20);
		if (width <= 0 || height <= 0) {
			return null;
		}
		return new int[] {width, height};
	}

	private static int readInt(byte[] b, int off) {
		return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
	}

	private static boolean isPng(String path) {
		return path != null && !path.isBlank() && path.toLowerCase(Locale.ROOT).endsWith(".png");
	}
}
