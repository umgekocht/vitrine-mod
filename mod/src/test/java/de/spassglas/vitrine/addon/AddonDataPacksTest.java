package de.spassglas.vitrine.addon;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

/** ROADMAP AD1: Abgleich der aktiven Datenpakete mit den gewollten Vitrine-Addons. */
public class AddonDataPacksTest {

	@Test
	public void addsWantedAndRemovesUnwantedAddonsKeepingOthers() {
		List<String> next = AddonDataPacks.plan(
				List.of("vanilla", "vitrine/alt", "file/eigenes"),
				List.of("vanilla", "vitrine/alt", "vitrine/neu", "file/eigenes"),
				Set.of("vitrine/neu"));
		assertEquals(next, List.of("vanilla", "file/eigenes", "vitrine/neu"));
	}

	@Test
	public void wantedButUnavailableAddonIsNotEnabled() {
		List<String> next = AddonDataPacks.plan(List.of("vanilla"), List.of("vanilla"), Set.of("vitrine/fehlt"));
		assertEquals(next, List.of("vanilla"));
	}

	@Test
	public void unchangedStateGivesSameList() {
		List<String> enabled = List.of("vanilla", "vitrine/a");
		assertEquals(AddonDataPacks.plan(enabled, enabled, Set.of("vitrine/a")), enabled);
	}
}
