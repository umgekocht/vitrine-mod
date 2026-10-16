package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.detail.ScreenshotGalleryWidget;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileStatus;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFW;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE82, woertliches Fertig-Kriterium: "'Mehr anzeigen' erweitert
 * echt, Screenshot-Klick öffnet Großansicht." Vorher brach die Beschreibung
 * bei sechs Zeilen ohne jeden Aufrufer fuer die bereits vorhandenen
 * Uebersetzungs-Keys ab, und die Screenshot-Galerie hatte ueberhaupt kein
 * {@code mouseClicked}/Eingabe-Pfad.
 */
public final class PackageDetailScreenGate82Test {

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	private static PackageDetailModel longDescriptionAndScreenshotsModel() {
		Manifest manifest = new Manifest(1, "demo.pack", "Demo Pack", "1.0.0", "texturen", "demo",
				"l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8", List.of(), null,
				List.of("shot1.png", "shot2.png", "shot3.png"), null, null);
		return PackageDetailModel.of(manifest, TileStatus.NONE);
	}

	@Test
	public void moreButtonExpandsDescriptionBeyondTheSixLineCollapseLimit() {
		PackageDetailModel model = longDescriptionAndScreenshotsModel();
		assertTrue(model.descriptionNeedsCollapse(), "Testfall braucht eine Beschreibung laenger als 6 Zeilen");

		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		screen.rebuildForTest();

		assertFalse(screen.descriptionExpanded(), "im Ruhezustand ist die Beschreibung eingeklappt");
		assertNotNull(screen.descriptionToggleButton(),
				"\"Mehr anzeigen\" muss bei einer Beschreibung laenger als 6 Zeilen ein echter Knopf sein");

		// "Mehr anzeigen" druecken (derselbe Weg wie der Knopf-Rueckruf).
		screen.toggleDescription();
		screen.rebuildForTest();

		assertTrue(screen.descriptionExpanded(),
				"nach \"Mehr anzeigen\" muss die Beschreibung echt aufgeklappt sein, nicht nur der Knopftext");
		assertNotNull(screen.descriptionToggleButton(), "\"Weniger anzeigen\" bleibt ein echter Knopf");

		// "Weniger anzeigen" druecken -- klappt wieder zu.
		screen.toggleDescription();
		screen.rebuildForTest();

		assertFalse(screen.descriptionExpanded(), "\"Weniger anzeigen\" muss wieder einklappen");
	}

	@Test
	public void shortDescriptionGetsNoToggleButtonAtAll() {
		Manifest manifest = new Manifest(1, "demo.kurz", "Kurzbeschreibung", "1.0.0", "texturen", "demo",
				"nur eine Zeile", List.of(), null, List.of(), null, null);
		PackageDetailModel model = PackageDetailModel.of(manifest, TileStatus.NONE);
		assertFalse(model.descriptionNeedsCollapse());

		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		screen.rebuildForTest();

		assertEquals(screen.descriptionToggleButton(), null,
				"eine kurze Beschreibung darf keinen toten \"Mehr anzeigen\"-Knopf zeigen");
	}

	@Test
	public void clickingAScreenshotThumbnailRecordsItsIndexForTheLargeView() {
		PackageDetailModel model = longDescriptionAndScreenshotsModel();
		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		screen.rebuildForTest();

		ScreenshotGalleryWidget gallery = screen.screenshotGallery();
		assertNotNull(gallery, "bei echten Screenshots muss die Galerie ein echtes, anklickbares Widget sein");
		assertTrue(screen.openedScreenshotIndex().isEmpty(),
				"vor jedem Klick darf keine Grossansicht vorgemerkt sein");

		int step = VitrineTheme.DETAIL_SCREENSHOT_WIDTH + VitrineTheme.SPACE_GUTTER;
		int top = gallery.getY() + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		gallery.onClick(gallery.getX() + step + 5, top + 5);

		assertEquals(screen.openedScreenshotIndex(), Optional.of(1),
				"ein Klick auf die zweite Vorschau muss ihren Index als zu oeffnende Grossansicht vermerken "
						+ "(vorher hatte die Galerie ueberhaupt keinen mouseClicked-Pfad)");
	}

	@Test
	public void enterOnTheFocusedGalleryAlsoOpensTheLargeView() {
		PackageDetailModel model = longDescriptionAndScreenshotsModel();
		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		screen.rebuildForTest();

		ScreenshotGalleryWidget gallery = screen.screenshotGallery();
		gallery.setFocused(true);
		assertTrue(gallery.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));

		assertEquals(screen.openedScreenshotIndex(), Optional.of(0),
				"Enter bei fokussierter Galerie muss ebenfalls die Grossansicht oeffnen -- Tastaturbedienung "
						+ "ohne Maus (Charter S9)");
	}

	@Test
	public void packageWithoutScreenshotsGetsNoGalleryWidget() {
		Manifest manifest = new Manifest(1, "demo.ohne", "Ohne Bilder", "1.0.0", "texturen", "demo",
				"nur eine Zeile", List.of(), null, List.of(), null, null);
		PackageDetailModel model = PackageDetailModel.of(manifest, TileStatus.NONE);
		assertFalse(model.hasScreenshots());

		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		screen.rebuildForTest();

		assertEquals(screen.screenshotGallery(), null,
				"ohne echte Screenshots darf es kein anklickbares Galerie-Widget geben");
	}
}
