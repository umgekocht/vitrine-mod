package de.spassglas.vitrine.ui.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Der eingebettete {@link ThemeSchemaValidator} muss dieselben Beispiele
 * annehmen/ablehnen wie der Node-Validator in
 * {@code registry/test/theme-schema.test.mjs} -- Charter-Regel 8, beide Seiten
 * lesen dieselbe Schema-Datei.
 */
public final class ThemeSchemaValidatorTest {

	@Test
	public void acceptsTheValidKlarglasExample() throws IOException {
		for (Path file : jsonFilesIn(exampleDir("valid"))) {
			List<String> violations = ThemeSchemaValidator.validate(
					ThemeSchemaValidator.embeddedSchema(), parse(file));
			assertTrue(violations.isEmpty(), file.getFileName() + " sollte gueltig sein: " + violations);
		}
	}

	@Test
	public void rejectsEveryInvalidExampleWithADistinctMessage() throws IOException {
		List<Path> invalid = jsonFilesIn(exampleDir("invalid"));
		assertTrue(invalid.size() >= 3, "erwarte mindestens drei ungueltige Beispiel-Themes");

		for (Path file : invalid) {
			List<String> violations = ThemeSchemaValidator.validate(
					ThemeSchemaValidator.embeddedSchema(), parse(file));
			assertFalse(violations.isEmpty(),
					file.getFileName() + " haette abgelehnt werden muessen, ergab aber keine Verstoesse");
		}
	}

	private static JsonObject parse(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static List<Path> jsonFilesIn(Path dir) throws IOException {
		try (Stream<Path> walk = Files.list(dir)) {
			return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static Path exampleDir(String kind) {
		Path viaModule = Path.of("..", "registry", "schema", "examples", "theme", kind);
		if (Files.exists(viaModule)) {
			return viaModule;
		}
		Path viaRoot = Path.of("registry", "schema", "examples", "theme", kind);
		if (Files.exists(viaRoot)) {
			return viaRoot;
		}
		throw new IllegalStateException("Beispielordner theme/" + kind + " nicht gefunden, Arbeitsverzeichnis: "
				+ Path.of("").toAbsolutePath());
	}
}
