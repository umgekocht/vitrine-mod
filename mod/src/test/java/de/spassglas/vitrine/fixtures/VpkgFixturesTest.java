package de.spassglas.vitrine.fixtures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt das Fertig-Kriterium von F4 ab: mindestens 5 Fixtures, davon 3
 * boesartige, werden hier tatsaechlich in Tests benutzt -- jeder Test
 * beweist, dass die jeweilige Fixture-Methode das behauptete ZIP-Archiv
 * liefert (Struktur der Fixture selbst, keine noch nicht existierende
 * Sicherheitspruefung aus P3).
 */
public final class VpkgFixturesTest {

	// --- gutartige Fixtures --------------------------------------------

	@Test
	public void minimalValidPackageOpensAndContainsManifestPlusOneTexture() throws IOException {
		byte[] vpkg = VpkgFixtures.minimalValidPackage();

		List<String> entryNames = readEntryNames(vpkg);

		assertEquals(entryNames, List.of(VpkgFixtures.MANIFEST_ENTRY_NAME, "textures/example.png"),
				"minimalValidPackage() muss genau Manifest + eine Textur enthalten");

		JsonObject manifest = readManifestJson(vpkg);
		assertEquals(manifest.get("id").getAsString(), "beispiel.minimalpaket");
		assertEquals(manifest.get("schemaVersion").getAsInt(), 1);
	}

	@Test
	public void multiResourcePackageContainsAllDeclaredResourceEntries() throws IOException {
		byte[] vpkg = VpkgFixtures.multiResourcePackage();

		List<String> entryNames = readEntryNames(vpkg);

		assertEquals(entryNames, List.of(
				VpkgFixtures.MANIFEST_ENTRY_NAME,
				"textures/gui/erste.png",
				"textures/gui/zweite.png",
				"sounds/klick.ogg",
				"lang/de_de.json"
		), "multiResourcePackage() muss Manifest + vier unterschiedliche Ressourcen enthalten");

		JsonObject manifest = readManifestJson(vpkg);
		assertEquals(manifest.get("category").getAsString(), "bundle");

		byte[] lang = readEntryBytes(vpkg, "lang/de_de.json");
		JsonObject langJson = JsonParser.parseString(new String(lang, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(langJson.get("vitrine.beispiel").getAsString(), "Hallo");
	}

	// --- boesartige Fixtures ---------------------------------------------

	@Test
	public void zipSlipPackageOpensAndContainsTraversalEntryName() throws IOException {
		byte[] vpkg = VpkgFixtures.zipSlipPackage();

		List<String> entryNames = readEntryNames(vpkg);

		assertTrue(entryNames.contains(VpkgFixtures.MANIFEST_ENTRY_NAME));
		boolean hasTraversalEntry = entryNames.stream().anyMatch(name -> name.contains(".."));
		assertTrue(hasTraversalEntry, "zipSlipPackage() muss einen Eintrag mit '..'-Traversal enthalten: " + entryNames);

		byte[] evilContent = readEntryBytes(vpkg, "../../../evil.txt");
		assertEquals(new String(evilContent, StandardCharsets.UTF_8), "diese Datei soll aus der Sandbox ausbrechen");
	}

	@Test
	public void absolutePathEntryPackageContainsWindowsAndUnixStyleAbsolutePaths() throws IOException {
		byte[] vpkg = VpkgFixtures.absolutePathEntryPackage();

		List<String> entryNames = readEntryNames(vpkg);

		boolean hasWindowsAbsolute = entryNames.stream().anyMatch(name -> name.matches("^[A-Za-z]:\\\\.*"));
		boolean hasUnixAbsolute = entryNames.stream().anyMatch(name -> name.startsWith("/"));
		assertTrue(hasWindowsAbsolute, "muss einen Windows-artigen absoluten Pfad enthalten: " + entryNames);
		assertTrue(hasUnixAbsolute, "muss einen Unix-artigen absoluten Pfad enthalten: " + entryNames);
	}

	@Test
	public void extremeCompressionRatioPackageHasSuspiciouslyHighRatio() throws IOException {
		byte[] vpkg = VpkgFixtures.extremeCompressionRatioPackage();

		ZipEntry bombEntry = findEntry(vpkg, "textures/bombe.png");
		assertTrue(bombEntry != null, "extremeCompressionRatioPackage() muss den Bomben-Eintrag enthalten");

		byte[] uncompressed = readEntryBytes(vpkg, "textures/bombe.png");
		assertEquals(uncompressed.length, VpkgFixtures.EXTREME_UNCOMPRESSED_SIZE,
				"unkomprimierter Inhalt muss die dokumentierte Groesse haben");

		// Das gesamte Archiv (inkl. Manifest) muss winzig gegenueber den 20 MiB
		// Nullbytes bleiben -- sonst waere es keine plausible Zip-Bomben-Andeutung.
		assertTrue(vpkg.length < VpkgFixtures.EXTREME_UNCOMPRESSED_SIZE / 100,
				"komprimiertes Archiv (" + vpkg.length + " Bytes) muss um Groessenordnungen kleiner sein als "
						+ VpkgFixtures.EXTREME_UNCOMPRESSED_SIZE + " Bytes unkomprimiert");
	}

	// --- generisches Werkzeug wird ebenfalls direkt geprueft -------------

	@Test
	public void buildZipRoundTripsArbitraryEntriesInInsertionOrder() throws IOException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("erste.txt", "eins".getBytes(StandardCharsets.UTF_8));
		entries.put("zweite.txt", "zwei".getBytes(StandardCharsets.UTF_8));

		byte[] built = VpkgFixtures.buildZip(entries);

		assertEquals(readEntryNames(built), List.of("erste.txt", "zweite.txt"));
		assertEquals(new String(readEntryBytes(built, "zweite.txt"), StandardCharsets.UTF_8), "zwei");
	}

	// --- Hilfsfunktionen fuer die Tests -----------------------------------

	private static List<String> readEntryNames(byte[] vpkg) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(vpkg), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				names.add(entry.getName());
				zip.closeEntry();
			}
		}
		return names;
	}

	private static ZipEntry findEntry(byte[] vpkg, String name) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(vpkg), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(name)) {
					return entry;
				}
				zip.closeEntry();
			}
		}
		return null;
	}

	private static byte[] readEntryBytes(byte[] vpkg, String name) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(vpkg), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(name)) {
					return zip.readAllBytes();
				}
				zip.closeEntry();
			}
		}
		fail("Eintrag nicht gefunden: " + name);
		throw new AssertionError("unreachable");
	}

	private static JsonObject readManifestJson(byte[] vpkg) throws IOException {
		byte[] raw = readEntryBytes(vpkg, VpkgFixtures.MANIFEST_ENTRY_NAME);
		return JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
