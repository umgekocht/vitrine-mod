package de.spassglas.vitrine.vpkg;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Wird geworfen, wenn beim tatsaechlichen Lesen eines {@code .vpkg}-Eintrags
 * (GATE30/GATE31) mehr Bytes anfallen als erlaubt -- gezaehlt werden dabei
 * ausschliesslich die tatsaechlich vom Stream gelesenen Bytes, nie die im
 * ZIP-Verzeichnis deklarierte (und damit manipulierbare)
 * {@link VpkgEntry#size()}.
 *
 * <p>Eine {@link IOException}, ganz bewusst, damit sie in jedem bestehenden
 * {@code catch (IOException e)}-Zweig entlang der {@code .vpkg}-Lesepfade
 * (Paket-Scan, Theme-/HUD-Anwendung) automatisch abgefangen wird -- ein
 * bislang unbegrenztes {@code readAllBytes()} konnte hier stattdessen ein rohes
 * {@link OutOfMemoryError} ausloesen, das keiner dieser Zweige faengt.</p>
 */
public final class VpkgEntryTooLargeException extends IOException {

	public VpkgEntryTooLargeException(Path file, String entryName, long maxReadBytes) {
		super("Eintrag '" + entryName + "' in '" + file + "' liefert beim tatsaechlichen Lesen mehr als "
				+ maxReadBytes + " Bytes -- die im ZIP-Verzeichnis deklarierte Groesse ist nicht vertrauenswuerdig, "
				+ "Lesen abgebrochen (Zip-Bomben-Schutz)");
	}
}
