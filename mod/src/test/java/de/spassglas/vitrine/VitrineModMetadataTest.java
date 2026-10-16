package de.spassglas.vitrine;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public final class VitrineModMetadataTest {

	// 44 Grafiken aus umgekocht's Asset-Paket (F1) plus 8 Kategorie-Vorschaubilder
	// (scripts/make_category_placeholders.py, 2026-10-06) plus Addon-Kategorie-Icon und
	// -Vorschaubild (ROADMAP M10), plus umgekocht's assets v2 (active-Icons, ui_filter,
	// Nine-Slice-Rahmen, Kippschalter, logo_text_only; 2026-10-06).
	private static final int EXPECTED_GRAPHIC_COUNT = 109; // + vitrine_button (Titelmenue-Symbol)

	@Test
	public void modIdMatchesFabricModJson() throws IOException {
		assertEquals(Vitrine.MOD_ID, "vitrine");

		String json;
		try (InputStream in = VitrineModMetadataTest.class.getResourceAsStream("/fabric.mod.json")) {
			assertTrue(in != null, "fabric.mod.json fehlt im Klassenpfad");
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertTrue(json.contains("\"id\": \"" + Vitrine.MOD_ID + "\""),
				"fabric.mod.json muss die Mod-ID '" + Vitrine.MOD_ID + "' enthalten");
		assertTrue(json.contains("\"name\": \"Vitrine\""),
				"fabric.mod.json muss den Anzeigenamen 'Vitrine' enthalten");
	}

	@Test
	public void resourcesContainAllFortyFourGraphics() throws IOException, URISyntaxException {
		URL guiTexturesUrl = VitrineModMetadataTest.class.getResource("/assets/vitrine/textures/gui");
		assertTrue(guiTexturesUrl != null, "assets/vitrine/textures/gui fehlt auf dem Klassenpfad");

		Path guiTexturesDir = Paths.get(guiTexturesUrl.toURI());
		long pngCount;
		try (Stream<Path> paths = Files.walk(guiTexturesDir)) {
			pngCount = paths.filter(p -> p.toString().endsWith(".png")).count();
		}

		assertEquals(pngCount, EXPECTED_GRAPHIC_COUNT,
				"Erwartet genau " + EXPECTED_GRAPHIC_COUNT
						+ " PNG-Grafiken unter assets/vitrine/textures/gui/** (F1-Fertig-Kriterium)");
	}
}
