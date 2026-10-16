package de.spassglas.vitrine.catalog;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP K5: {@link UpdateStatus#evaluate} bildet aus installierter und
 * Katalog-Version den Zeilen-Marker -- ohne Wurf bei nicht parsebaren Versionen.
 */
public final class UpdateStatusTest {

	@Test
	public void updateAvailableWhenCatalogVersionIsStrictlyNewer() {
		UpdateStatus status = UpdateStatus.evaluate("vitrine/a", "1.0.0", "1.2.0");
		assertTrue(status.updateAvailable());
		assertEquals(status.installedVersion(), "1.0.0");
		assertEquals(status.latestVersion(), "1.2.0");
	}

	@Test
	public void noUpdateWhenVersionsEqualOrCatalogOlder() {
		assertFalse(UpdateStatus.evaluate("vitrine/a", "1.2.0", "1.2.0").updateAvailable());
		assertFalse(UpdateStatus.evaluate("vitrine/a", "2.0.0", "1.9.9").updateAvailable());
		assertFalse(UpdateStatus.evaluate("vitrine/a", "1.0.0", "1.0.0-rc.1").updateAvailable());
	}

	@Test
	public void noCatalogEntryMeansNoUpdate() {
		UpdateStatus status = UpdateStatus.evaluate("vitrine/a", "1.0.0", null);
		assertFalse(status.updateAvailable());
		assertEquals(status.latestVersion(), null);
	}

	@Test
	public void unparsableVersionOnEitherSideMeansNoUpdateAndNoThrow() {
		assertFalse(UpdateStatus.evaluate("vitrine/a", "not-semver", "1.2.0").updateAvailable());
		assertFalse(UpdateStatus.evaluate("vitrine/a", "1.0.0", "latest").updateAvailable());
		assertFalse(UpdateStatus.evaluate("vitrine/a", null, "1.2.0").updateAvailable());
	}
}
