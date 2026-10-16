package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Deckt T5s "Anker + Pixel-Offset + Skalierung anwenden" auf der
 * Geometrie-Ebene ab: {@link HudPlacement#resolve} rechnet Anker, Versatz und
 * Skalierung ohne Minecraft-Bezug in die linke obere Bildschirmecke des
 * Widget-Inhalts um.
 */
public final class HudPlacementTest {

	private static final int SCREEN_W = 400;
	private static final int SCREEN_H = 220;

	@Test
	public void topLeftIsThePlainOffset() {
		HudPlacement.Position p = HudPlacement.resolve(
				HudAnchor.TOP_LEFT, new HudOffset(4, 6), 40, 10, 1.0, SCREEN_W, SCREEN_H);
		assertEquals(p, new HudPlacement.Position(4, 6));
	}

	@Test
	public void topRightMeasuresTheScaledContentFromTheRightEdge() {
		HudPlacement.Position p = HudPlacement.resolve(
				HudAnchor.TOP_RIGHT, new HudOffset(-4, 4), 50, 10, 1.0, SCREEN_W, SCREEN_H);
		// 400 - 50 - 4 = 346
		assertEquals(p, new HudPlacement.Position(346, 4));
	}

	@Test
	public void bottomLeftMeasuresTheScaledContentFromTheBottomEdge() {
		HudPlacement.Position p = HudPlacement.resolve(
				HudAnchor.BOTTOM_LEFT, new HudOffset(4, -4), 50, 10, 1.0, SCREEN_W, SCREEN_H);
		// y: 220 - 10 - 4 = 206
		assertEquals(p, new HudPlacement.Position(4, 206));
	}

	@Test
	public void centerCentersTheScaledContent() {
		HudPlacement.Position p = HudPlacement.resolve(
				HudAnchor.CENTER, new HudOffset(0, 0), 100, 20, 1.0, SCREEN_W, SCREEN_H);
		// x: (400 - 100) / 2 = 150 ; y: (220 - 20) / 2 = 100
		assertEquals(p, new HudPlacement.Position(150, 100));
	}

	@Test
	public void scaleEnlargesTheContentFootprintBeforeAnchoring() {
		HudPlacement.Position p = HudPlacement.resolve(
				HudAnchor.TOP_RIGHT, new HudOffset(0, 0), 50, 10, 2.0, SCREEN_W, SCREEN_H);
		// scaled width 100 -> x: 400 - 100 = 300
		assertEquals(p, new HudPlacement.Position(300, 0));
	}
}
