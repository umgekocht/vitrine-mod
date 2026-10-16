package de.spassglas.vitrine.ui.nav;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Deckt die Breakpoint-Entscheidung aus ROADMAP S0 ab: "Umbruchpunkte" laut
 * docs/UI-SPEC.md, unter 1000px kompakt, unter 700px Ueberlagerung.
 */
public final class NavRailLayoutTest {

	@Test
	public void veryNarrowWidthIsOverlay() {
		assertEquals(NavRailLayout.forWidth(320), NavRailLayout.OVERLAY);
	}

	@Test
	public void zeroOrNegativeWidthIsOverlay() {
		assertEquals(NavRailLayout.forWidth(0), NavRailLayout.OVERLAY);
		assertEquals(NavRailLayout.forWidth(-100), NavRailLayout.OVERLAY);
	}

	@Test
	public void justBelowOverlayBreakpointIsOverlay() {
		assertEquals(NavRailLayout.forWidth(699), NavRailLayout.OVERLAY);
	}

	@Test
	public void exactlyOverlayBreakpointIsCompactNotOverlay() {
		// "unter 700px" ist strikt kleiner -- bei genau 700 gilt noch kompakt.
		assertEquals(NavRailLayout.forWidth(700), NavRailLayout.COMPACT);
	}

	@Test
	public void justBelowCompactBreakpointIsCompact() {
		assertEquals(NavRailLayout.forWidth(999), NavRailLayout.COMPACT);
	}

	@Test
	public void exactlyCompactBreakpointIsExpandedNotCompact() {
		// "unter 1000px" ist strikt kleiner -- bei genau 1000 gilt noch ausgeklappt.
		assertEquals(NavRailLayout.forWidth(1000), NavRailLayout.EXPANDED);
	}

	@Test
	public void wideWindowIsExpanded() {
		assertEquals(NavRailLayout.forWidth(1920), NavRailLayout.EXPANDED);
	}
}
