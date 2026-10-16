package de.spassglas.vitrine.hud;

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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt T5s Parser-Haelfte ab: jedes gueltige Beispiel unter
 * {@code registry/schema/examples/hud/valid/} parst fehlerfrei, jedes
 * ungueltige unter {@code .../invalid/} wird mit einer je eigenen,
 * feldbezogenen Meldung abgewiesen, und eine doppelte Widget-Id fuehrt zu
 * einer klaren {@link HudParseException} (das kann das Schema selbst nicht).
 */
public final class HudLayoutParserTest {

	@Test
	public void everyValidExampleParsesWithoutError() throws IOException {
		List<Path> validFiles = listJsonFiles(resolveHudExamplesDir().resolve("valid"));
		assertFalse(validFiles.isEmpty(), "keine gueltigen HUD-Beispiele gefunden");

		for (Path file : validFiles) {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			try {
				HudLayout layout = HudLayoutParser.parse(json);
				assertEquals(layout.schemaVersion(), 1, file.getFileName() + ": schemaVersion");
				assertFalse(layout.widgets().isEmpty(), file.getFileName() + ": keine Widgets geparst");
			} catch (HudParseException e) {
				fail(file.getFileName() + " haette fehlerfrei parsen muessen: " + e.getMessage());
			}
		}
	}

	@Test
	public void everyInvalidExampleIsRejectedWithADistinctFieldMessage() throws IOException {
		List<Path> invalidFiles = listJsonFiles(resolveHudExamplesDir().resolve("invalid"));
		assertTrue(invalidFiles.size() >= 6, "erwarte mindestens 6 ungueltige HUD-Beispiele, gefunden: "
				+ invalidFiles.size());

		Set<String> seenMessages = new HashSet<>();
		List<String> allMessages = new ArrayList<>();
		for (Path file : invalidFiles) {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			try {
				HudLayout parsed = HudLayoutParser.parse(json);
				fail(file.getFileName() + " haette abgelehnt werden muessen, ergab aber: " + parsed);
			} catch (HudParseException e) {
				String message = e.getMessage();
				assertFalse(message == null || message.isBlank(), file.getFileName() + " ohne Meldung");
				assertTrue(seenMessages.add(message),
						file.getFileName() + " lieferte dieselbe Meldung wie eine andere Datei: " + message);
				allMessages.add(file.getFileName() + " -> " + message);
			}
		}
		assertEquals(seenMessages.size(), invalidFiles.size(),
				"jede kaputte Datei muss eine eigene Meldung ergeben:\n" + String.join("\n", allMessages));
	}

	@Test
	public void missingRequiredWidgetsArrayIsNamed() throws IOException {
		HudParseException e = expectRejection("01-missing-required-field.json");
		assertTrue(e.getMessage().contains("widgets"), "Meldung muss 'widgets' nennen: " + e.getMessage());
	}

	@Test
	public void unknownWidgetTypeIsNamed() throws IOException {
		HudParseException e = expectRejection("02-unknown-widget-type.json");
		assertTrue(e.getMessage().contains("type") && e.getMessage().contains("video"),
				"Meldung muss das Feld 'type' und den ungueltigen Wert 'video' nennen: " + e.getMessage());
	}

	@Test
	public void unknownDataSourceIsNamedWithFieldPath() throws IOException {
		HudParseException e = expectRejection("03-unknown-data-source.json");
		assertTrue(e.getMessage().contains("content.source"),
				"Meldung muss den Feldpfad 'content.source' nennen: " + e.getMessage());
	}

	@Test
	public void unknownAnchorIsNamed() throws IOException {
		HudParseException e = expectRejection("04-unknown-anchor.json");
		assertTrue(e.getMessage().contains("anchor") && e.getMessage().contains("middle-left"),
				"Meldung muss 'anchor' und den ungueltigen Wert nennen: " + e.getMessage());
	}

	@Test
	public void unknownVisibilityConditionIsNamedWithFieldPath() throws IOException {
		HudParseException e = expectRejection("05-unknown-visibility-condition.json");
		assertTrue(e.getMessage().contains("visibility.type"),
				"Meldung muss den Feldpfad 'visibility.type' nennen: " + e.getMessage());
	}

	@Test
	public void freeFormulaAsSourceIsRejectedAsAnUnknownDataSource() throws IOException {
		HudParseException e = expectRejection("06-free-formula-as-source.json");
		assertTrue(e.getMessage().contains("content.source"),
				"Meldung muss 'content.source' nennen (keine Formelauswertung, Charter 3): " + e.getMessage());
	}

	@Test
	public void duplicateWidgetIdIsRejectedWithAClearMessage() {
		String json = """
				{
				  "schemaVersion": 1,
				  "widgets": [
				    { "id": "same", "type": "text", "anchor": "top-left",
				      "content": { "kind": "dynamic", "source": "fps" } },
				    { "id": "same", "type": "text", "anchor": "top-right",
				      "content": { "kind": "dynamic", "source": "time" } }
				  ]
				}
				""";
		try {
			HudLayout parsed = HudLayoutParser.parse(json);
			fail("doppelte Widget-Id haette abgelehnt werden muessen, ergab: " + parsed);
		} catch (HudParseException e) {
			assertTrue(e.getMessage().contains("same")
							&& e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("mehrfach"),
					"Meldung muss die doppelte Id 'same' benennen: " + e.getMessage());
		}
	}

	private static HudParseException expectRejection(String fileName) throws IOException {
		String json = Files.readString(resolveHudExamplesDir().resolve("invalid").resolve(fileName),
				StandardCharsets.UTF_8);
		try {
			HudLayout parsed = HudLayoutParser.parse(json);
			fail(fileName + " haette abgelehnt werden muessen, ergab: " + parsed);
			throw new AssertionError("unreachable");
		} catch (HudParseException e) {
			return e;
		}
	}

	private static List<Path> listJsonFiles(Path dir) throws IOException {
		try (Stream<Path> paths = Files.list(dir)) {
			return paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
	}

	/** Gleiche Pfadaufloesung wie {@code ManifestParserTest#resolveExamplesDir()}. */
	static Path resolveHudExamplesDir() {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "examples", "hud");
		if (Files.exists(viaModuleWorkingDir)) {
			return viaModuleWorkingDir;
		}
		Path viaRepoRoot = Path.of("registry", "schema", "examples", "hud");
		if (Files.exists(viaRepoRoot)) {
			return viaRepoRoot;
		}
		throw new IllegalStateException(
				"Konnte registry/schema/examples/hud nicht finden, Arbeitsverzeichnis: "
						+ Path.of("").toAbsolutePath());
	}
}
