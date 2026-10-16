package de.spassglas.vitrine.manifest;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt P1s Fertig-Kriterium (Java-Teil) ab: "Parser weist 10 fehlerhafte
 * Manifeste mit je eigener Meldung ab." Getestet wird gegen alle zwoelf
 * Fixtures unter {@code registry/schema/examples/invalid/} (zwei mehr als
 * das Minimum) plus die beiden gueltigen unter {@code examples/valid/}.
 */
public final class ManifestParserTest {

	@Test
	public void allTwelveInvalidExamplesAreRejectedWithDistinctMessages() throws IOException {
		List<Path> invalidFiles = listJsonFiles(resolveExamplesDir().resolve("invalid"));
		assertTrue(invalidFiles.size() >= 10,
				"Erwarte mindestens 10 ungueltige Beispielmanifeste, gefunden: " + invalidFiles.size());

		Set<String> seenMessages = new HashSet<>();
		List<String> allMessages = new ArrayList<>();
		for (Path file : invalidFiles) {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			try {
				Manifest parsed = ManifestParser.parse(json);
				fail(file.getFileName() + " haette abgelehnt werden muessen, ergab aber: " + parsed);
			} catch (ManifestParseException e) {
				String message = e.getMessage();
				assertFalse(message == null || message.isBlank(),
						file.getFileName() + " lieferte keine Fehlermeldung");
				assertFalse(message.strip().equalsIgnoreCase("invalid") || message.strip().equalsIgnoreCase("ungueltig"),
						file.getFileName() + " lieferte nur eine pauschale Meldung: " + message);
				assertTrue(seenMessages.add(message),
						file.getFileName() + " lieferte dieselbe Meldung wie eine andere Datei: " + message);
				allMessages.add(file.getFileName() + " -> " + message);
			}
		}

		assertEquals(seenMessages.size(), invalidFiles.size(),
				"jede kaputte Datei muss eine eigene, unterscheidbare Meldung ergeben:\n"
						+ String.join("\n", allMessages));
	}

	@Test
	public void missingRequiredFieldIsNamedExplicitly() throws IOException {
		// "01-missing-required-field.json" fehlt urspruenglich nur 'author';
		// seit 'license' ebenfalls Pflichtfeld ist (siehe vitrine.schema.json),
		// fehlen dort zwei Felder gemeinsam -- die Meldung nennt sie deshalb
		// zusammen ("Pflichtfelder fehlen: ...") statt einzeln ("Pflichtfeld ... fehlt").
		ManifestParseException e = expectRejection("01-missing-required-field.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("author") && (v.contains("fehlt") || v.contains("fehlen"))),
				"Meldung muss das fehlende Feld 'author' benennen: " + e.getMessage());
	}

	@Test
	public void wrongTypeIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("02-wrong-type-schemaversion.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("schemaVersion") && v.contains("Typ")),
				"Meldung muss den falschen Typ von 'schemaVersion' benennen: " + e.getMessage());
	}

	@Test
	public void unknownAdditionalFieldIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("03-unknown-additional-field.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("preis") && v.contains("Unbekannt")),
				"Meldung muss das unbekannte Feld 'preis' benennen: " + e.getMessage());
	}

	@Test
	public void invalidCategoryEnumIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("04-invalid-category-enum.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("category") && v.contains("textures")),
				"Meldung muss den ungueltigen Kategorie-Wert benennen: " + e.getMessage());
	}

	@Test
	public void invalidSemverIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("05-invalid-semver.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("version") && v.contains("Muster")),
				"Meldung muss das SemVer-Musterproblem von 'version' benennen: " + e.getMessage());
	}

	@Test
	public void invalidIdPatternIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("06-invalid-id-pattern.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("'id'") && v.contains("Muster")),
				"Meldung muss das Musterproblem von 'id' benennen: " + e.getMessage());
	}

	@Test
	public void emptyNameIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("07-empty-name.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("name") && v.contains("leer")),
				"Meldung muss den leeren Namen benennen: " + e.getMessage());
	}

	@Test
	public void capabilitiesWrongTypeIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("08-capabilities-wrong-type.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("capabilities") && v.contains("Typ")),
				"Meldung muss den falschen Typ von 'capabilities' benennen: " + e.getMessage());
	}

	@Test
	public void capabilitiesItemNotWhitelistedIsNamedExplicitly() throws IOException {
		// Charter-Regel 8 / ROADMAP G2: capabilities-Eintraege sind ein geschlossenes
		// enum (dieselben sieben wie VpkgCapability). Jeder Nicht-Whitelist-Eintrag --
		// egal ob mit Sonderzeichen (09) oder punktgetrenntem Namespace (13) -- muss
		// mit derselben Wendung wie Designer/CLI abgelehnt werden: Feldname
		// "capabilities[0]" plus "erlaubt sind".
		for (String fixture : List.of(
				"09-capabilities-item-invalid-pattern.json",
				"13-capabilities-dotted-namespace.json")) {
			ManifestParseException e = expectRejection(fixture);
			assertTrue(e.violations().stream().anyMatch(v -> v.contains("capabilities[0]") && v.contains("erlaubt sind")),
					fixture + ": Meldung muss den konkreten Capability-Eintrag als 'capabilities[0]' benennen "
							+ "und die Wendung 'erlaubt sind' enthalten (Parität zu Designer/CLI): " + e.getMessage());
		}
	}

	@Test
	public void schemaVersionWrongValueIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("10-schemaversion-wrong-value.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("schemaVersion") && v.contains("2")),
				"Meldung muss den falschen schemaVersion-Wert benennen: " + e.getMessage());
	}

	@Test
	public void iconPathTraversalIsNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("11-icon-path-traversal.json");
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("icon") && v.contains("..")),
				"Meldung muss den Pfad-Ausbruch in 'icon' benennen: " + e.getMessage());
	}

	@Test
	public void multipleMissingFieldsAreAllNamedExplicitly() throws IOException {
		ManifestParseException e = expectRejection("12-multiple-missing-fields.json");
		assertTrue(e.violations().stream()
						.anyMatch(v -> v.contains("id") && v.contains("name") && v.contains("version") && v.contains("fehlen")),
				"Meldung muss alle fehlenden Pflichtfelder gemeinsam benennen: " + e.getMessage());
	}

	@Test
	public void minimalValidManifestParsesWithoutError() throws IOException, ManifestParseException {
		String json = Files.readString(resolveExamplesDir().resolve("valid").resolve("minimal.json"),
				StandardCharsets.UTF_8);

		Manifest manifest = ManifestParser.parse(json);

		assertEquals(manifest.schemaVersion(), 1);
		assertEquals(manifest.id(), "beispiel.minimalpaket");
		assertEquals(manifest.name(), "Minimalpaket");
		assertEquals(manifest.version(), "1.0.0");
		assertEquals(manifest.category(), "texturen");
		assertEquals(manifest.author(), "Vitrine-Testfixture");
		assertNull(manifest.description());
		assertTrue(manifest.capabilities().isEmpty());
		assertNull(manifest.icon());
	}

	@Test
	public void fullValidManifestParsesAllOptionalFields() throws IOException, ManifestParseException {
		String json = Files.readString(resolveExamplesDir().resolve("valid").resolve("full.json"),
				StandardCharsets.UTF_8);

		Manifest manifest = ManifestParser.parse(json);

		assertEquals(manifest.id(), "spassglas.klarglas-theme");
		assertEquals(manifest.version(), "2.1.0-beta.1+build.5");
		assertEquals(manifest.category(), "theme");
		assertEquals(manifest.capabilities(), List.of("theme", "hud"));
		assertEquals(manifest.icon(), "icon.png");
		assertEquals(manifest.screenshots(), List.of("screenshots/vorher.png", "screenshots/nachher.jpg"));
		assertEquals(manifest.homepage(), "https://github.com/spassglas/vitrine-theme-klarglas");
		assertEquals(manifest.license(), "CC-BY-4.0");
	}

	@Test
	public void malformedJsonSyntaxIsRejectedWithSyntaxMessage() {
		String brokenJson = "{ \"schemaVersion\": 1, ";

		try {
			ManifestParser.parse(brokenJson);
			fail("kaputtes JSON haette abgelehnt werden muessen");
		} catch (ManifestParseException e) {
			assertTrue(e.getMessage().contains("JSON"), "Meldung muss auf das JSON-Syntaxproblem hinweisen: " + e.getMessage());
		}
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private static ManifestParseException expectRejection(String fileName) throws IOException {
		String json = Files.readString(resolveExamplesDir().resolve("invalid").resolve(fileName), StandardCharsets.UTF_8);
		try {
			Manifest parsed = ManifestParser.parse(json);
			fail(fileName + " haette abgelehnt werden muessen, ergab aber: " + parsed);
			throw new AssertionError("unreachable");
		} catch (ManifestParseException e) {
			return e;
		}
	}

	private static List<Path> listJsonFiles(Path dir) throws IOException {
		try (Stream<Path> paths = Files.list(dir)) {
			return paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
	}

	/**
	 * Der Gradle-{@code test}-Task laeuft mit {@code mod/} als
	 * Arbeitsverzeichnis, ein direkter Aufruf aus dem Repo-Root braucht
	 * stattdessen den Umweg ueber {@code mod/../registry/...} -- siehe
	 * {@code UiTextScanTest#resolveMainJavaRoot()} als Vorbild.
	 */
	private static Path resolveExamplesDir() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "examples");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "examples");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/examples nicht finden, Arbeitsverzeichnis: " + Path.of("").toAbsolutePath());
	}
}
