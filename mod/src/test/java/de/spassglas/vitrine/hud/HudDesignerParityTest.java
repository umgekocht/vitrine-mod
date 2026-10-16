package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * D5&#8596;T5-Paritaet: belegt auf der Mod-Seite das Fertig-Kriterium von D5
 * ("Im Designer gebautes HUD laedt der Mod unveraendert"). Der byte-genaue
 * Designer-Export {@code registry/schema/examples/hud/valid/designer-canonical-export.json}
 * (aus D5, HUD-Editor) geht durch {@link HudLayoutParser} und wird Feld fuer
 * Feld gegen das erwartete Modell geprueft -- alle vier Widgets, mit den
 * richtigen Typen, Ankern, Datenquellen, Sichtbarkeiten und Masszahlen.
 * Aendert der Designer sein Ausgabeformat, ohne dass Schema und Mod-Parser
 * mitgezogen werden, schlaegt dieser Test fehl.
 */
public final class HudDesignerParityTest {

	@Test
	public void designerCanonicalExportLoadsUnchangedIntoTheModRuntime() throws IOException {
		Path exportFile = HudLayoutParserTest.resolveHudExamplesDir()
				.resolve("valid").resolve("designer-canonical-export.json");
		String json = Files.readString(exportFile, StandardCharsets.UTF_8);

		HudLayout layout;
		try {
			layout = HudLayoutParser.parse(json);
		} catch (HudParseException e) {
			throw new AssertionError("Designer-Export wurde von der Mod-Laufzeit abgelehnt: " + e.getMessage(), e);
		}

		assertEquals(layout.schemaVersion(), 1);
		List<HudWidget> widgets = layout.widgets();
		assertEquals(widgets.size(), 4, "Designer-Export hat genau vier Widgets");

		// Widget 0: dynamischer FPS-Text, oben links.
		HudWidget.Text fps = (HudWidget.Text) widgets.get(0);
		assertEquals(fps.id(), "fps-counter");
		assertEquals(fps.anchor(), HudAnchor.TOP_LEFT);
		assertEquals(fps.offset(), new HudOffset(4, 4));
		assertEquals(fps.scale(), 1.0, 0.0);
		assertTrue(fps.visibility() instanceof HudVisibility.Always);
		HudTextContent.Dynamic fpsContent = (HudTextContent.Dynamic) fps.content();
		assertEquals(fpsContent.source(), HudDataSource.FPS);
		assertEquals(fpsContent.label(), "FPS: ");
		assertEquals(fpsContent.decimals(), Integer.valueOf(0));

		// Widget 1: Icon, oben mittig, nur ausserhalb des F3-Debugbildschirms.
		HudWidget.Icon biome = (HudWidget.Icon) widgets.get(1);
		assertEquals(biome.id(), "biome-marker");
		assertEquals(biome.anchor(), HudAnchor.TOP_CENTER);
		assertEquals(biome.scale(), 1.5, 0.0);
		assertEquals(biome.texture(), "textures/gui/biome-marker.png");
		assertEquals(biome.width(), Integer.valueOf(16));
		assertEquals(biome.height(), Integer.valueOf(16));
		assertEquals(((HudVisibility.DebugScreen) biome.visibility()), new HudVisibility.DebugScreen(false));

		// Widget 2: Balken, unten mittig, nur im Overworld, mit Paket-Farbe.
		HudWidget.Bar bar = (HudWidget.Bar) widgets.get(2);
		assertEquals(bar.id(), "speed-bar");
		assertEquals(bar.anchor(), HudAnchor.BOTTOM_CENTER);
		assertEquals(bar.source(), HudDataSource.FPS);
		assertEquals(bar.min(), 0.0, 0.0);
		assertEquals(bar.max(), 144.0, 0.0);
		assertEquals(bar.width(), Integer.valueOf(120));
		assertEquals(bar.height(), Integer.valueOf(6));
		assertEquals(bar.fillColorArgb(), Integer.valueOf(0xFF3A6FF0));
		assertEquals(((HudVisibility.InDimension) bar.visibility()).dimensionId(), "minecraft:overworld");

		// Widget 3: Liste, unten links, nur ausserhalb eines Menues.
		HudWidget.List coords = (HudWidget.List) widgets.get(3);
		assertEquals(coords.id(), "coordinates-list");
		assertEquals(coords.anchor(), HudAnchor.BOTTOM_LEFT);
		assertTrue(coords.visibility() instanceof HudVisibility.OutsideMenu);
		assertEquals(coords.entries().size(), 4);
		assertEquals(((HudTextContent.Static) coords.entries().get(0)).value(), "Koordinaten");
		HudTextContent.Dynamic x = (HudTextContent.Dynamic) coords.entries().get(1);
		assertEquals(x.source(), HudDataSource.COORDINATES_X);
		assertEquals(x.label(), "X: ");
		assertEquals(x.decimals(), Integer.valueOf(1));
		assertEquals(((HudTextContent.Dynamic) coords.entries().get(2)).source(), HudDataSource.COORDINATES_Y);
		assertEquals(((HudTextContent.Dynamic) coords.entries().get(3)).source(), HudDataSource.COORDINATES_Z);
	}
}
