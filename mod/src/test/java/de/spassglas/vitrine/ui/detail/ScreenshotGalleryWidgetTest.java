package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.lwjgl.glfw.GLFW;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE82, woertliches Fertig-Kriterium: "Screenshot-Klick öffnet
 * Großansicht." Vorher hatte die Screenshot-Galerie (damals nur gezeichnete
 * Kacheln ohne jedes {@code ClickableWidget}) ueberhaupt keinen
 * Eingabe-Handler -- ein Klick tat nichts. Dieser Test treibt
 * {@link ScreenshotGalleryWidget} direkt (ohne laufenden Client/Textur-Laden
 * -- Klick-/Tastatur-Logik braucht keine GL-Textur), und beweist sowohl Maus-
 * als auch Tastaturbedienung (Charter S9: komplette Tastaturbedienung).
 */
public final class ScreenshotGalleryWidgetTest {

	private static final int TOP = VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
	private static final int STEP = VitrineTheme.DETAIL_SCREENSHOT_WIDTH + VitrineTheme.SPACE_GUTTER;

	@Test
	public void clickOnSecondThumbnailActivatesItsIndex() {
		List<Integer> activated = new ArrayList<>();
		ScreenshotGalleryWidget widget = new ScreenshotGalleryWidget(0, 0,
				List.of("a.png", "b.png", "c.png"), null, null, activated::add);

		widget.onClick(STEP + 5, TOP + 5);

		assertEquals(activated, List.of(1), "Klick auf die zweite Kachel muss Index 1 melden");
	}

	@Test
	public void clickOutsideAnyThumbnailDoesNotActivateAnything() {
		List<Integer> activated = new ArrayList<>();
		ScreenshotGalleryWidget widget = new ScreenshotGalleryWidget(0, 0,
				List.of("a.png"), null, null, activated::add);

		widget.onClick(-50, -50);

		assertTrue(activated.isEmpty(), "ein Klick ausserhalb jeder Kachel darf nichts aktivieren");
	}

	@Test
	public void enterAfterArrowRightActivatesTheKeyboardFocusedThumbnail() {
		List<Integer> activated = new ArrayList<>();
		ScreenshotGalleryWidget widget = new ScreenshotGalleryWidget(0, 0,
				List.of("a.png", "b.png"), null, null, activated::add);

		widget.setFocused(true);
		assertTrue(widget.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0), "Pfeil rechts muss behandelt werden");
		assertTrue(widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
				"Enter auf der fokussierten Galerie muss behandelt werden");

		assertEquals(activated, List.of(1),
				"Enter nach Pfeil-rechts muss die per Pfeiltaste fokussierte zweite Kachel oeffnen -- "
						+ "echte Tastaturbedienung ohne Maus (Charter S9)");
	}

	@Test
	public void spaceWithoutPriorArrowMoveActivatesTheFirstThumbnail() {
		List<Integer> activated = new ArrayList<>();
		ScreenshotGalleryWidget widget = new ScreenshotGalleryWidget(0, 0,
				List.of("a.png", "b.png"), null, null, activated::add);

		widget.setFocused(true);
		assertTrue(widget.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));

		assertEquals(activated, List.of(0), "ohne vorige Pfeilbewegung aktiviert die Leertaste die erste Kachel");
	}

	@Test
	public void losingFocusResetsKeyboardSelection() {
		List<Integer> activated = new ArrayList<>();
		ScreenshotGalleryWidget widget = new ScreenshotGalleryWidget(0, 0,
				List.of("a.png", "b.png"), null, null, activated::add);

		widget.setFocused(true);
		widget.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
		assertEquals(widget.focusedIndexForTest(), 1);

		widget.setFocused(false);
		assertEquals(widget.focusedIndexForTest(), -1,
				"ohne Tastaturfokus darf keine Kachel mehr als ausgewaehlt gelten");
	}
}
