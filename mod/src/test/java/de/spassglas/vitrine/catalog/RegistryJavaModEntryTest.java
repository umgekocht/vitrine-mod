package de.spassglas.vitrine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.spassglas.vitrine.classb.JavaModOffer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * GATE6: Klasse-B-Eintraege aus der Registry. Vorher las
 * {@link RegistryPackageEntry#fromJson} bei jeder Version {@code vpkgUrl} --
 * ein echter Java-Mod-Eintrag (mit {@code jarUrl}) lief in eine
 * NullPointerException. Und der Schema-Auswerter uebersprang {@code if}/
 * {@code then}/{@code else}, also genau die Regel, die Klasse A und B trennt.
 *
 * <p>Gegen dieselben Beispieldateien, die auch die Node-Seite prueft.</p>
 */
public class RegistryJavaModEntryTest {

	@Test
	public void validJavaModEntryParsesAndBecomesAStageableOffer() throws IOException {
		JsonElement json = read("valid", "javamod-fixture.json");
		assertEquals(RegistryPackageSchemaValidator.validate(json), List.of(), "Beispiel muss gueltig sein");

		RegistryPackageEntry entry = RegistryPackageEntry.fromJson(json.getAsJsonObject());

		assertTrue(entry.isJavaMod());
		assertTrue(entry.versions().isEmpty(), "Klasse B hat keine .vpkg-Versionen");
		RegistryJavaModVersion latest = entry.latestJavaModVersion().orElseThrow();
		assertTrue(latest.jarUrl().toString().endsWith(".jar"));

		JavaModOffer offer = latest.toOffer(entry.id(), entry.name(), entry.authorName());
		assertEquals(offer.fileName(), latest.fileName());
		assertEquals(offer.expectedSha256Hex(), latest.sha256Hex());
		assertTrue(offer.signatureBytes().length > 0, "Signatur wird mitgegeben, nicht weggelassen");
	}

	@Test
	public void resourceEntryWithoutContentClassStaysKlasseA() {
		RegistryPackageEntry entry = RegistryPackageEntry.fromJson(
				JsonParser.parseString(CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON).getAsJsonObject());
		assertFalse(entry.isJavaMod(), "fehlendes contentClass heisst Klasse A");
		assertEquals(entry.versions().size(), 1);
		assertTrue(entry.javaModVersions().isEmpty());
	}

	@Test
	public void schemaRejectsTheThreeMixedUpEntries() throws IOException {
		for (String name : List.of("01-javamod-with-vpkgurl.json", "02-resource-with-jarurl.json",
				"03-javamod-unsafe-filename.json")) {
			assertFalse(RegistryPackageSchemaValidator.validate(read("invalid", name)).isEmpty(),
					name + " muss abgelehnt werden -- Klasse A und B duerfen sich nicht vermischen");
		}
	}

	private static JsonElement read(String folder, String name) throws IOException {
		Path viaModule = Path.of("..", "registry", "schema", "examples", "registry-package", folder, name);
		Path viaRoot = Path.of("registry", "schema", "examples", "registry-package", folder, name);
		Path file = Files.exists(viaModule) ? viaModule : viaRoot;
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
	}
}
