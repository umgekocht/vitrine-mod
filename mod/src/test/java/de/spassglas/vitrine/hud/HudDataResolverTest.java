package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Deckt T5s Fertig-Kriterium auf der Wert-Ebene ab: die geschlossene
 * {@link HudDataSource}-Whitelist wird aus einem {@link HudGameState} in
 * Anzeigetext umgesetzt -- FPS und Koordinaten (mit {@code decimals} und
 * {@code label}) sowie BIOME und TIME (Uhrzeit aus {@code dayTimeTicks}).
 * Reine Wertsubstitution, kein {@code eval}, keine Formel (Charter-Regel 3).
 */
public final class HudDataResolverTest {

	@Test
	public void dynamicFpsUsesLabelAndZeroDecimalsByDefault() {
		FakeHudGameState state = new FakeHudGameState();
		state.fps = 59.7;

		String line = HudDataResolver.render(
				new HudTextContent.Dynamic(HudDataSource.FPS, "FPS: ", null), state);

		assertEquals(line, "FPS: 60");
	}

	@Test
	public void dynamicCoordinatesHonourExplicitDecimals() {
		FakeHudGameState state = new FakeHudGameState();
		state.playerX = 123.456;
		state.playerY = 64.0;
		state.playerZ = -812.913;

		assertEquals(HudDataResolver.render(
				new HudTextContent.Dynamic(HudDataSource.COORDINATES_X, "X: ", 1), state), "X: 123.5");
		assertEquals(HudDataResolver.render(
				new HudTextContent.Dynamic(HudDataSource.COORDINATES_Y, "Y: ", 0), state), "Y: 64");
		assertEquals(HudDataResolver.render(
				new HudTextContent.Dynamic(HudDataSource.COORDINATES_Z, "Z: ", 2), state), "Z: -812.91");
	}

	@Test
	public void coordinatesFallBackToOneDecimalWhenUnspecified() {
		FakeHudGameState state = new FakeHudGameState();
		state.playerX = 100.44;

		assertEquals(HudDataResolver.render(
				new HudTextContent.Dynamic(HudDataSource.COORDINATES_X, "", null), state), "100.4");
	}

	@Test
	public void biomeIsRenderedAsRawIdText() {
		FakeHudGameState state = new FakeHudGameState();
		state.biomeId = "minecraft:snowy_taiga";

		assertEquals(HudDataResolver.renderValue(HudDataSource.BIOME, null, state), "minecraft:snowy_taiga");
	}

	@Test
	public void timeIsRenderedAsClockFromDayTimeTicks() {
		FakeHudGameState state = new FakeHudGameState();

		state.dayTimeTicks = 0L;
		assertEquals(HudDataResolver.renderValue(HudDataSource.TIME, null, state), "06:00");

		state.dayTimeTicks = 6000L;
		assertEquals(HudDataResolver.renderValue(HudDataSource.TIME, null, state), "12:00");

		state.dayTimeTicks = 18000L;
		assertEquals(HudDataResolver.renderValue(HudDataSource.TIME, null, state), "00:00");

		// Ueber einen ganzen Tag hinaus -- muss modulo 24000 rechnen, nie negativ.
		state.dayTimeTicks = 24000L + 6000L;
		assertEquals(HudDataResolver.renderValue(HudDataSource.TIME, null, state), "12:00");
	}

	@Test
	public void staticContentIsPassedThroughVerbatim() {
		assertEquals(HudDataResolver.render(new HudTextContent.Static("Koordinaten"), new FakeHudGameState()),
				"Koordinaten");
	}

	@Test
	public void numericValueFeedsTheBarFillForNumericSourcesOnly() {
		FakeHudGameState state = new FakeHudGameState();
		state.fps = 72.0;

		assertEquals(HudDataResolver.numericValue(HudDataSource.FPS, state), 72.0, 0.0001);
		assertThrows(IllegalArgumentException.class,
				() -> HudDataResolver.numericValue(HudDataSource.BIOME, state));
		assertThrows(IllegalArgumentException.class,
				() -> HudDataResolver.numericValue(HudDataSource.TIME, state));
	}
}
