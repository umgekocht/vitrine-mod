package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.classb.StagedMod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J2: das "Neustart noetig"-Band zaehlt nur die noch ausstehenden
 * Klasse-B-Jars und traegt keinen Anzeigetext (alles ueber {@code vitrine.classb.*}).
 */
public final class RestartRequiredBannerTest {

	private static StagedMod pending(String id) {
		return new StagedMod(id, id + "-1.0.0.jar", "aa", "Autorin", id, "t", true);
	}

	private static StagedMod loaded(String id) {
		return new StagedMod(id, id + "-1.0.0.jar", "aa", "Autorin", id, "t", false);
	}

	@Test
	public void bannerCountsOnlyPendingRestarts() {
		RestartRequiredBanner banner = new RestartRequiredBanner(List.of(
				pending("de.a.one"), loaded("de.a.done"), pending("de.a.two")));

		assertTrue(banner.isVisible());
		assertEquals(banner.pendingCount(), 2, "die bereits geladene Jar zaehlt nicht");
		assertEquals(banner.entries().stream().map(StagedMod::id).toList(), List.of("de.a.one", "de.a.two"));
	}

	@Test
	public void bannerIsHiddenWhenNothingIsPending() {
		assertFalse(new RestartRequiredBanner(List.of()).isVisible());
		assertFalse(new RestartRequiredBanner(List.of(loaded("de.a.done"))).isVisible());
	}

	@Test
	public void bannerCarriesOnlyLangKeysNoDisplayText() {
		assertTrue(RestartRequiredBanner.TITLE_KEY.startsWith("vitrine.classb."));
		assertTrue(RestartRequiredBanner.MESSAGE_KEY.startsWith("vitrine.classb."));
		assertTrue(RestartRequiredBanner.RESTART_ALL_KEY.startsWith("vitrine.classb."));
		assertTrue(RestartRequiredBanner.TAKE_BACK_KEY.startsWith("vitrine.classb."));
		assertTrue(RestartRequiredBanner.AUTHOR_KEY.startsWith("vitrine.classb."));
		assertTrue(RestartRequiredBanner.INSTALL_WARNING_KEY.startsWith("vitrine.classb."));
	}
}
