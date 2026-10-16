package de.spassglas.vitrine.vpkg;

/**
 * Rohe Metadaten eines einzelnen Eintrags im zentralen Verzeichnis eines
 * {@code .vpkg}-Archivs (P2) -- Name so, wie er im ZIP steht (kein
 * normalisierter/gepruefter Pfad), unkomprimierte und komprimierte Groesse.
 *
 * <p>Absichtlich ein reines Datenobjekt ohne jede Wertung: {@code name} kann
 * hier bereits ein Zip-Slip-Versuch ({@code ../../evil.txt}) oder ein
 * absoluter Pfad sein -- diese Klasse entscheidet nichts, sie beschreibt nur,
 * was im Archiv steht. Die Pruefung kommt mit P3 und liest genau diese Felder
 * (Name fuer Pfad-/Traversal-Pruefung, {@code size}/{@code compressedSize}
 * fuer Groessen- und Verhaeltnispruefung), ohne dass ein Eintrag dafuer erst
 * geoeffnet/dekomprimiert werden muss.</p>
 *
 * <p>{@code method} ist die rohe ZIP-Kompressionsmethode ({@link
 * java.util.zip.ZipEntry#STORED}/{@link java.util.zip.ZipEntry#DEFLATED}/...)
 * -- GATE109: nur fuer {@code STORED} kann die im zentralen Verzeichnis
 * deklarierte unkomprimierte Groesse NIE von der tatsaechlichen abweichen
 * (das ZIP-Format verlangt fuer STORED {@code compressedSize == size}, und
 * {@code java.util.zip.ZipFile} liefert beim Lesen nie mehr als die
 * deklarierte Groesse); bei jeder anderen Methode (insbesondere DEFLATE) MUSS
 * {@code VpkgSecurityValidator} den Eintrag tatsaechlich lesen, um die reale
 * Groesse zu kennen.</p>
 */
public record VpkgEntry(String name, boolean directory, long size, long compressedSize, int method) {
}
