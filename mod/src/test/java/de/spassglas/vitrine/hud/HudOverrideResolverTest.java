package de.spassglas.vitrine.hud;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP T6, Punkt 2 ("Anwendung"): {@link HudOverrideResolver} legt eine
 * {@link HudWidgetOverride} ueber ein Paket-Widget, ohne das Paket-Widget zu
 * mutieren. Kein Override ⇒ exakt dasselbe Objekt zurueck (bisheriges
 * Verhalten).
 */
public final class HudOverrideResolverTest {

	private static HudWidget.Text widget(HudAnchor anchor, HudOffset offset, HudVisibility visibility) {
		return new HudWidget.Text("fps", anchor, offset, 1.0, visibility,
				new HudTextContent.Static("FPS"));
	}

	@Test
	public void noOverrideReturnsTheExactSameWidget() {
		HudWidget original = widget(HudAnchor.TOP_LEFT, new HudOffset(4, 4), HudVisibility.Always.INSTANCE);

		assertSame(HudOverrideResolver.resolve(original, null), original);
		assertSame(HudOverrideResolver.resolve(original, HudWidgetOverride.EMPTY), original);
	}

	@Test
	public void offsetOverrideMovesWithoutTouchingAnchorOrVisibility() {
		HudWidget original = widget(HudAnchor.TOP_RIGHT, new HudOffset(-4, 4), HudVisibility.Always.INSTANCE);
		HudWidgetOverride override = HudWidgetOverride.EMPTY.withOffset(new HudOffset(20, 60));

		HudWidget resolved = HudOverrideResolver.resolve(original, override);

		assertEquals(resolved.offset(), new HudOffset(20, 60));
		assertEquals(resolved.anchor(), HudAnchor.TOP_RIGHT);
		assertTrue(resolved.visibility() instanceof HudVisibility.Always);
		// Paket-Widget bleibt unberuehrt.
		assertEquals(original.offset(), new HudOffset(-4, 4));
	}

	@Test
	public void anchorOverrideRealignsWithoutTouchingOffset() {
		HudWidget original = widget(HudAnchor.TOP_LEFT, new HudOffset(4, 4), HudVisibility.Always.INSTANCE);
		HudWidget resolved = HudOverrideResolver.resolve(original,
				HudWidgetOverride.EMPTY.withAnchor(HudAnchor.BOTTOM_RIGHT));

		assertEquals(resolved.anchor(), HudAnchor.BOTTOM_RIGHT);
		assertEquals(resolved.offset(), new HudOffset(4, 4));
	}

	@Test
	public void visibleFalseForcesHiddenAndVisibleTrueForcesAlways() {
		HudWidget dimensionOnly = widget(HudAnchor.CENTER, HudOffset.ZERO,
				new HudVisibility.InDimension("minecraft:overworld"));

		HudWidget off = HudOverrideResolver.resolve(dimensionOnly, HudWidgetOverride.EMPTY.withVisible(false));
		assertTrue(off.visibility() instanceof HudVisibility.Hidden);
		assertFalse(off.visibility().isVisible(new FakeHudGameState()));

		HudWidget on = HudOverrideResolver.resolve(dimensionOnly, HudWidgetOverride.EMPTY.withVisible(true));
		assertTrue(on.visibility() instanceof HudVisibility.Always);
	}
}
