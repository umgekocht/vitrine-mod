package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

/**
 * Deckt das Bedien-Modell einer Kachelreihe aus ROADMAP S1 ab
 * (docs/UI-SPEC.md "3. Start", Punkt 3): Chevrons erscheinen nur beim
 * Ueberfahren <em>und</em> nur, wenn es in die jeweilige Richtung etwas zu
 * sehen gibt; eine Ziehgeste zaehlt erst ab einer Schwelle von
 * {@link VitrineTheme#SPACE_UNIT} px als Scrollen statt als Kachel-Klick;
 * begin/drag/end laufen als saubere Sequenz ab.
 */
public final class TileRowControllerTest {

	/** 5 Kacheln a 100 px, 20 px Zwischenraum, Fenster 300 -> maxOffset 280. */
	private static TileRowController scrollableRow() {
		TileRowController controller = new TileRowController(5, 100, 20);
		controller.setViewportWidth(300);
		return controller;
	}

	@Test
	public void chevronsHiddenWhileNotHoveredEvenIfScrollable() {
		TileRowController controller = scrollableRow();
		controller.scrollByChevron(1); // irgendwo in der Mitte -> beide Richtungen moeglich

		controller.setHovered(false);
		assertFalse(controller.showLeftChevron());
		assertFalse(controller.showRightChevron());
	}

	@Test
	public void chevronsFollowHoverAndScrollPosition() {
		TileRowController controller = scrollableRow();
		controller.setHovered(true);

		assertFalse(controller.showLeftChevron(), "am linken Rand kein Links-Chevron");
		assertTrue(controller.showRightChevron());

		controller.scrollByChevron(1);
		assertTrue(controller.showLeftChevron());
		assertTrue(controller.showRightChevron());

		controller.scrollByWheel(-100.0); // ganz nach rechts
		assertTrue(controller.showLeftChevron());
		assertFalse(controller.showRightChevron(), "am rechten Rand kein Rechts-Chevron");
	}

	@Test
	public void nonScrollableRowNeverShowsChevronsEvenHovered() {
		TileRowController controller = new TileRowController(2, 100, 20);
		controller.setViewportWidth(500);
		controller.setHovered(true);

		assertFalse(controller.showLeftChevron());
		assertFalse(controller.showRightChevron());
	}

	@Test
	public void pageJumpAndHomeEndDriveOffsetAndChevronVisibility() {
		TileRowController controller = scrollableRow(); // maxOffset 280, stepSize 120
		controller.setHovered(true);

		assertFalse(controller.showLeftChevron(), "start: nichts links");
		assertTrue(controller.showRightChevron());

		controller.scrollByPage(1); // Seitensprung nach rechts, weg vom linken Rand
		assertTrue(controller.showLeftChevron());

		controller.scrollToEnd();
		assertEquals(controller.offset(), controller.maxOffset());
		assertTrue(controller.showLeftChevron());
		assertFalse(controller.showRightChevron(), "am Ende kein Rechts-Chevron");

		controller.scrollByPage(-1);
		assertTrue(controller.showLeftChevron());
		assertTrue(controller.showRightChevron());

		controller.scrollToStart();
		assertEquals(controller.offset(), 0);
		assertFalse(controller.showLeftChevron(), "wieder am linken Rand");
		assertTrue(controller.showRightChevron());
	}

	@Test
	public void tinyDragStaysAClickBelowThreshold() {
		TileRowController controller = scrollableRow();
		controller.beginDrag(10.0);
		assertFalse(controller.draggedPastClickThreshold(), "direkt nach beginDrag ist noch nichts bewegt");

		controller.dragTo(10.0 + VitrineTheme.SPACE_UNIT); // genau die Schwelle, noch nicht darueber
		assertFalse(controller.draggedPastClickThreshold());

		controller.endDrag();
	}

	@Test
	public void dragPastThresholdCountsAsScrollGesture() {
		TileRowController controller = scrollableRow();
		controller.beginDrag(0.0);
		controller.dragTo(VitrineTheme.SPACE_UNIT + 1.0);

		assertTrue(controller.draggedPastClickThreshold());
	}

	@Test
	public void beginDragResetsAccumulatedDistance() {
		TileRowController controller = scrollableRow();
		controller.beginDrag(0.0);
		controller.dragTo(100.0);
		assertTrue(controller.draggedPastClickThreshold());

		controller.endDrag();
		controller.beginDrag(0.0);
		assertFalse(controller.draggedPastClickThreshold(), "neue Geste faengt bei Null an");
	}

	@Test
	public void dragBeforeBeginIsIgnored() {
		TileRowController controller = scrollableRow();
		int before = controller.offset();
		controller.dragTo(500.0);
		assertEquals(controller.offset(), before, "ohne beginDrag bewegt dragTo nichts");
		assertFalse(controller.isDragging());
	}

	@Test
	public void beginDragThenDragMovesOffsetThenEndStopsDragging() {
		TileRowController controller = scrollableRow();
		controller.scrollByChevron(1);
		int start = controller.offset();

		controller.beginDrag(200.0);
		assertTrue(controller.isDragging());
		controller.dragTo(150.0); // 50 px nach links gezogen -> Offset steigt
		assertTrue(controller.offset() > start);

		controller.endDrag();
		assertFalse(controller.isDragging());
		int afterEnd = controller.offset();
		controller.dragTo(0.0);
		assertEquals(controller.offset(), afterEnd, "nach endDrag wirkt dragTo nicht mehr");
	}
}
