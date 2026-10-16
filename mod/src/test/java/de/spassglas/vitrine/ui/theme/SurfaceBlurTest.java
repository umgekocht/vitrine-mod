package de.spassglas.vitrine.ui.theme;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** GATE21: {@code blur.surface} hinter Vitrine-Screens. */
public class SurfaceBlurTest {

	@Test
	public void vitrineScreensUseTheThemeValue() {
		assertEquals(SurfaceBlur.radius(5, true, 12), 12);
		assertEquals(SurfaceBlur.radius(5, true, 0), 0, "Theme darf Unschaerfe abschalten");
	}

	@Test
	public void otherScreensKeepVanilla() {
		assertEquals(SurfaceBlur.radius(5, false, 12), 5);
	}

	@Test
	public void playerWhoDisabledBlurNeverGetsIt() {
		assertEquals(SurfaceBlur.radius(0, true, 12), 0);
	}

	@Test
	public void recognisesScreensByPackage() {
		assertTrue(SurfaceBlur.isVitrineScreen(new RoundedRectTest()));
		assertFalse(SurfaceBlur.isVitrineScreen("kein Screen"));
		assertFalse(SurfaceBlur.isVitrineScreen(null));
	}
}
