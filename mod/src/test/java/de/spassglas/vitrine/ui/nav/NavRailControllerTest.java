package de.spassglas.vitrine.ui.nav;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

/**
 * Deckt den Teil von ROADMAP S0 ab, der beim Umbau des Fensters (Resize)
 * beweisbar sein soll: der Controller wechselt das Layout bei den richtigen
 * Breiten und schliesst eine offene Ueberlagerung automatisch, sobald die
 * Nav-Rail wieder angedockt werden kann -- eine Ueberlagerung ergibt bei
 * ausgeklappter/kompakter Rail keinen Sinn mehr.
 */
public final class NavRailControllerTest {

	@Test
	public void defaultConstructorStartsExpanded() {
		NavRailController controller = new NavRailController();
		assertEquals(controller.layout(), NavRailLayout.EXPANDED);
		assertTrue(controller.isRailVisible());
		assertTrue(controller.isDocked());
	}

	@Test
	public void widthChangeSwitchesLayout() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(320);
		assertEquals(controller.layout(), NavRailLayout.OVERLAY);

		controller.onWidthChanged(800);
		assertEquals(controller.layout(), NavRailLayout.COMPACT);

		controller.onWidthChanged(1920);
		assertEquals(controller.layout(), NavRailLayout.EXPANDED);
	}

	@Test
	public void overlayClosedByDefaultAndHidesRail() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(320);
		assertFalse(controller.isOverlayOpen());
		assertFalse(controller.isRailVisible());
		assertFalse(controller.isDocked());
	}

	@Test
	public void toggleOverlayOpensAndClosesOnlyInOverlayLayout() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(320);

		controller.toggleOverlay();
		assertTrue(controller.isOverlayOpen());
		assertTrue(controller.isRailVisible());

		controller.toggleOverlay();
		assertFalse(controller.isOverlayOpen());
	}

	@Test
	public void toggleOverlayIsNoOpWhenDocked() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(1920);
		controller.toggleOverlay();
		assertFalse(controller.isOverlayOpen());
		assertTrue(controller.isRailVisible());
	}

	@Test
	public void leavingOverlayLayoutClosesOpenOverlay() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(320);
		controller.toggleOverlay();
		assertTrue(controller.isOverlayOpen());

		controller.onWidthChanged(1920);
		assertEquals(controller.layout(), NavRailLayout.EXPANDED);
		assertFalse(controller.isOverlayOpen());

		// Zurueck in den schmalen Zustand: die Ueberlagerung ist wieder zu,
		// nicht mehr im vorherigen offenen Zustand haengen geblieben.
		controller.onWidthChanged(320);
		assertFalse(controller.isOverlayOpen());
	}

	@Test
	public void closeOverlayIsIdempotent() {
		NavRailController controller = new NavRailController();
		controller.onWidthChanged(320);
		controller.closeOverlay();
		controller.closeOverlay();
		assertFalse(controller.isOverlayOpen());
	}
}
