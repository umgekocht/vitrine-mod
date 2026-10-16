package de.spassglas.vitrine.classb;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ROADMAP J2 -- Seam fuer den Jar-Bezug: {@link JavaModStager} weiss nicht, ob
 * die Jar aus einem GitHub-Release-Asset (ueber
 * {@code de.spassglas.vitrine.catalog.download.PackageDownloader}) oder aus
 * einem lokalen Ordner kommt. Der Stager reicht einen frisch angelegten,
 * ausschliesslich ihm gehoerenden {@code tempDir}; die Implementierung legt die
 * Jar-Datei irgendwo darin ab und gibt deren Pfad zurueck.
 *
 * <p>Bewusst <b>vor</b> jeder Pruefung: die zurueckgegebene Datei liegt an
 * einem temporaeren Ort, nicht in {@code mods/}. Erst nachdem
 * {@link JavaModStager#stage} die Pruefsumme (und ggf. Signatur) verifiziert
 * hat, wandert sie per {@code ATOMIC_MOVE} an ihren Staging-Platz
 * (Charter-Regel 3: "Sicherheit vor Bequemlichkeit ... kein vorlaeufiges
 * Kopieren").</p>
 */
@FunctionalInterface
public interface JarSource {

	/**
	 * Besorgt die Jar von {@code offer} an einen Ort innerhalb von
	 * {@code tempDir} und liefert deren Pfad.
	 *
	 * @throws IOException wenn der Bezug fehlschlaegt -- {@link JavaModStager}
	 *                     bricht dann ab, raeumt {@code tempDir} weg und legt
	 *                     nichts in {@code mods/}.
	 */
	Path fetch(JavaModOffer offer, Path tempDir) throws IOException;
}
