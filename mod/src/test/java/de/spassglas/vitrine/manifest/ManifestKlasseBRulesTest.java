package de.spassglas.vitrine.manifest;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * GATE6: die Klasse-A/B-Regel aus {@code vitrine.schema.json} muss auch auf
 * der Java-Seite greifen. Vor GATE6 kannte {@link ManifestSchemaValidator}
 * weder {@code allOf}/{@code if}/{@code then}/{@code else}/{@code not} noch
 * stieg er in verschachtelte Objekte ab -- ein Klasse-A-Paket mit
 * eingeschmuggeltem {@code javaMod}-Block waere still durchgegangen.
 *
 * <p>Jede Pruefung laeuft gegen die echten Beispieldateien unter
 * {@code registry/schema/examples/} -- dieselben, die auch die Node-Seite
 * prueft (ein Schema, zwei Seiten).</p>
 */
public class ManifestKlasseBRulesTest {

	@Test
	public void klasseBMitAllemNoetigenWirdAngenommen() throws IOException, ManifestParseException {
		Manifest manifest = ManifestParser.parse(read("valid", "klasse-b-javamod.json"));
		assertEquals(manifest.id().isBlank(), false, "gueltiges Klasse-B-Manifest muss eine ID tragen");
	}

	@Test
	public void bestehendeKlasseAManifesteBleibenGueltig() throws IOException, ManifestParseException {
		// Ohne contentClass gilt Klasse A -- jede vor GATE6 geschriebene vitrine.json
		// muss unveraendert gueltig bleiben.
		ManifestParser.parse(read("valid", "minimal.json"));
		ManifestParser.parse(read("valid", "full.json"));
	}

	@Test
	public void klasseAMitJarBlockWirdAbgelehnt() throws IOException {
		ManifestParseException e = expectThrows(ManifestParseException.class,
				() -> ManifestParser.parse(read("invalid", "14-klasse-a-mit-jar.json")));
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("javaMod") && v.contains("Klasse-A")),
				"die Meldung muss sagen, dass ein Klasse-A-Paket keinen javaMod-Block tragen darf: " + e.getMessage());
	}

	@Test
	public void klasseBOhnePruefsummeNenntDenVerschachteltenPfad() throws IOException {
		ManifestParseException e = expectThrows(ManifestParseException.class,
				() -> ManifestParser.parse(read("invalid", "15-klasse-b-ohne-sha256.json")));
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("javaMod.jar.sha256")),
				"verschachteltes Pflichtfeld muss mit vollem Pfad genannt werden: " + e.getMessage());
	}

	@Test
	public void klasseBDieHotApplyBehauptetWirdAbgelehnt() throws IOException {
		ManifestParseException e = expectThrows(ManifestParseException.class,
				() -> ManifestParser.parse(read("invalid", "16-klasse-b-hotapply-true.json")));
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("hotApply")),
				"eine .jar darf sich nicht als ohne Neustart anwendbar ausgeben: " + e.getMessage());
	}

	@Test
	public void klasseBOhneJavaModBlockWirdAbgelehnt() throws IOException {
		ManifestParseException e = expectThrows(ManifestParseException.class,
				() -> ManifestParser.parse(read("invalid", "17-klasse-b-ohne-javamod-block.json")));
		assertTrue(e.violations().stream().anyMatch(v -> v.contains("javaMod")),
				"ohne javaMod-Block kann JavaModStager nichts pruefen: " + e.getMessage());
	}

	private static String read(String folder, String name) throws IOException {
		Path viaModuleWorkingDir = Path.of("..", "registry", "schema", "examples", folder, name);
		Path viaRepoRoot = Path.of("registry", "schema", "examples", folder, name);
		Path file = Files.exists(viaModuleWorkingDir) ? viaModuleWorkingDir : viaRepoRoot;
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
