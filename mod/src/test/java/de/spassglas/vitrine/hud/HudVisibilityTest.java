package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt T5s Sichtbarkeitsbedingungen ab: {@code always} / {@code dimension} /
 * {@code debugScreen} / {@code outsideMenu} werten gegen einen
 * {@link FakeHudGameState} korrekt aus. Feste Menge, keine Ausdruckssprache.
 */
public final class HudVisibilityTest {

	@Test
	public void alwaysIsVisibleRegardlessOfState() {
		FakeHudGameState state = new FakeHudGameState();
		state.anyScreenOpen = true;
		state.debugScreenShown = true;
		assertTrue(HudVisibility.Always.INSTANCE.isVisible(state));
	}

	@Test
	public void dimensionMatchesExactIdOnly() {
		FakeHudGameState state = new FakeHudGameState();
		state.dimensionId = "minecraft:the_nether";

		assertTrue(new HudVisibility.InDimension("minecraft:the_nether").isVisible(state));
		assertFalse(new HudVisibility.InDimension("minecraft:overworld").isVisible(state));
	}

	@Test
	public void debugScreenConditionComparesTheRequestedState() {
		FakeHudGameState state = new FakeHudGameState();

		state.debugScreenShown = true;
		assertTrue(new HudVisibility.DebugScreen(true).isVisible(state));
		assertFalse(new HudVisibility.DebugScreen(false).isVisible(state));

		state.debugScreenShown = false;
		assertFalse(new HudVisibility.DebugScreen(true).isVisible(state));
		assertTrue(new HudVisibility.DebugScreen(false).isVisible(state));
	}

	@Test
	public void outsideMenuIsVisibleOnlyWhenNoScreenIsOpen() {
		FakeHudGameState state = new FakeHudGameState();

		state.anyScreenOpen = false;
		assertTrue(HudVisibility.OutsideMenu.INSTANCE.isVisible(state));

		state.anyScreenOpen = true;
		assertFalse(HudVisibility.OutsideMenu.INSTANCE.isVisible(state));
	}
}
