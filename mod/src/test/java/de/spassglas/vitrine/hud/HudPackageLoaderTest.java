package de.spassglas.vitrine.hud;

import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.fail;

/**
 * Deckt T5s Ladepfad ab (Charter-Regel 3/4, "Sicherheit vor Bequemlichkeit"):
 * {@link HudPackageLoader} liest ein {@code hud/*.json} erst, nachdem die
 * M1-Kette (Zip-Sicherheit, angemeldete {@code hud}-Faehigkeit) durchgesetzt
 * ist. Ein Paket ohne {@code hud}-Faehigkeit wird abgewiesen, ein
 * Zip-Slip-Paket ebenfalls -- und zwar bevor ein einziger HUD-Layout-Eintrag
 * geparst wird.
 */
public final class HudPackageLoaderTest {

	private static final String VALID_HUD_JSON = """
			{
			  "schemaVersion": 1,
			  "widgets": [
			    { "id": "fps-counter", "type": "text", "anchor": "top-left",
			      "content": { "kind": "dynamic", "source": "fps", "label": "FPS: ", "decimals": 0 } },
			    { "id": "coordinates-list", "type": "list", "anchor": "bottom-left",
			      "entries": [
			        { "kind": "dynamic", "source": "coordinates.x", "label": "X: ", "decimals": 1 },
			        { "kind": "dynamic", "source": "coordinates.y", "label": "Y: ", "decimals": 1 },
			        { "kind": "dynamic", "source": "coordinates.z", "label": "Z: ", "decimals": 1 }
			      ] }
			  ]
			}
			""";

	private Path tempDir;

	@BeforeMethod
	public void setUp() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-hud-loader-test-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	@Test
	public void packageWithHudLayoutAndGrantedCapabilityLoads() throws Exception {
		Path vpkg = writeVpkg("hud-ok.vpkg", entries -> {
			entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
			entries.put("hud/main.json", VALID_HUD_JSON.getBytes(StandardCharsets.UTF_8));
		});
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of("beispiel.hud", List.of("hud"));

		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			HudPackage loaded = HudPackageLoader.load(archive, grant);
			assertEquals(loaded.packageId(), "beispiel.hud");
			assertEquals(loaded.layouts().size(), 1);
			assertEquals(loaded.layouts().get(0).entryName(), "hud/main.json");
			assertEquals(loaded.allWidgets().size(), 2);
		}
	}

	@Test
	public void packageWithoutHudCapabilityIsRejected() throws IOException {
		Path vpkg = writeVpkg("hud-nocap.vpkg", entries -> {
			entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
			entries.put("hud/main.json", VALID_HUD_JSON.getBytes(StandardCharsets.UTF_8));
		});
		VpkgCapabilityGrant grant;
		try {
			grant = VpkgCapabilityGrant.of("beispiel.hud", List.of("texturen"));
		} catch (VpkgCapabilityException e) {
			throw new AssertionError("Grant-Aufbau haette hier nicht scheitern duerfen", e);
		}

		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			assertThrows(VpkgCapabilityException.class, () -> HudPackageLoader.load(archive, grant));
		}
	}

	@Test
	public void zipSlipPackageIsRejectedBeforeAnyHudLayoutIsParsed() throws Exception {
		// Das hud/*.json ist bewusst kaputt: wuerde es VOR der Zip-Pruefung
		// gelesen, kaeme eine HudParseException. Es muss aber die
		// VpkgSecurityException des ../-Eintrags sein.
		Path vpkg = writeVpkg("hud-zipslip.vpkg", entries -> {
			entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
			entries.put("hud/main.json", "{ das ist absichtlich kein gueltiges JSON".getBytes(StandardCharsets.UTF_8));
			entries.put("../../../evil.json", "ausbruch".getBytes(StandardCharsets.UTF_8));
		});
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of("beispiel.hud", List.of("hud"));

		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			try {
				HudPackage loaded = HudPackageLoader.load(archive, grant);
				fail("Zip-Slip-Paket haette abgelehnt werden muessen, ergab: " + loaded);
			} catch (VpkgSecurityException expected) {
				// richtig: die Sicherheitspruefung greift vor dem Lesen des Layouts.
			} catch (HudParseException wrongOrder) {
				fail("Das kaputte hud/main.json wurde geparst, bevor die Zip-Slip-Pruefung griff: "
						+ wrongOrder.getMessage());
			}
		}
	}

	@Test
	public void malformedHudLayoutInAnOtherwiseSafePackageYieldsHudParseException() throws Exception {
		Path vpkg = writeVpkg("hud-broken.vpkg", entries -> {
			entries.put(VpkgFixtures.MANIFEST_ENTRY_NAME, manifestBytes());
			entries.put("hud/main.json", "{ \"schemaVersion\": 1 }".getBytes(StandardCharsets.UTF_8));
		});
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of("beispiel.hud", List.of("hud"));

		try (VpkgArchive archive = VpkgArchive.open(vpkg)) {
			HudParseException e = expectHudParseException(archive, grant);
			// Meldung nennt Paket + Eintrag, damit der Autor die Datei findet.
			org.testng.Assert.assertTrue(e.getMessage().contains("hud/main.json"),
					"Meldung muss den Eintragsnamen nennen: " + e.getMessage());
		}
	}

	private static HudParseException expectHudParseException(VpkgArchive archive, VpkgCapabilityGrant grant)
			throws Exception {
		try {
			HudPackage loaded = HudPackageLoader.load(archive, grant);
			fail("kaputtes Layout haette abgelehnt werden muessen, ergab: " + loaded);
			throw new AssertionError("unreachable");
		} catch (HudParseException e) {
			return e;
		}
	}

	private Path writeVpkg(String name, java.util.function.Consumer<Map<String, byte[]>> fill) throws IOException {
		LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
		fill.accept(entries);
		Path file = tempDir.resolve(name);
		Files.write(file, VpkgFixtures.buildZip(entries));
		return file;
	}

	private static byte[] manifestBytes() {
		return ("{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"beispiel.hud\",\n"
				+ "  \"name\": \"HUD-Testpaket\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"hud\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\"\n"
				+ "}\n").getBytes(StandardCharsets.UTF_8);
	}
}
