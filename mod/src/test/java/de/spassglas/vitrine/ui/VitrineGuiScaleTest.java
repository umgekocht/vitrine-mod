package de.spassglas.vitrine.ui;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/** Skalierungswahl fuer Vitrine-Bildschirme. */
public class VitrineGuiScaleTest {

	@Test
	public void fullHdWithAutoScaleGetsEnoughRoom() {
		// 1080p-Fenster, Minecraft "Auto" = 4 -> nur 480 Einheiten breit. Vitrine nimmt 2 (960).
		assertEquals(VitrineGuiScale.targetScale(1920, 1017, 4), 2);
	}

	@Test
	public void neverIncreasesThePlayersScale() {
		assertEquals(VitrineGuiScale.targetScale(3840, 2160, 1), 1);
		assertEquals(VitrineGuiScale.targetScale(3840, 2160, 3), 3);
	}

	@Test
	public void tinyWindowsStayAtOne() {
		assertEquals(VitrineGuiScale.targetScale(800, 600, 3), 1);
	}

	@Test
	public void heightLimitsToo() {
		// breit, aber flach: 2560 x 900 -> Hoehe erlaubt nur 1 (900/480).
		assertEquals(VitrineGuiScale.targetScale(2560, 900, 4), 1);
	}
}
