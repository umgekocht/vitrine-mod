package de.spassglas.vitrine.ui.nav;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class NavRailOverlayStateTest {

	@Test
	public void startsClosed() {
		assertFalse(new NavRailOverlayState().isOpen());
	}

	@Test
	public void openSetsOpen() {
		NavRailOverlayState state = new NavRailOverlayState();
		state.open();
		assertTrue(state.isOpen());
	}

	@Test
	public void closeAfterOpenClearsOpen() {
		NavRailOverlayState state = new NavRailOverlayState();
		state.open();
		state.close();
		assertFalse(state.isOpen());
	}

	@Test
	public void toggleFlipsRepeatedly() {
		NavRailOverlayState state = new NavRailOverlayState();
		state.toggle();
		assertTrue(state.isOpen());
		state.toggle();
		assertFalse(state.isOpen());
		state.toggle();
		assertTrue(state.isOpen());
	}
}
