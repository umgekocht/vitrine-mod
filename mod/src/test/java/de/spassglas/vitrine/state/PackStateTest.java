package de.spassglas.vitrine.state;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Deckt {@link PackState#addOrUpdate(String, boolean, String)} ab (ROADMAP
 * X3, {@link PackState.PackEntry#source}): ein neuer Eintrag traegt die
 * angegebene Quelle, ein blosser An-/Aus-Wechsel ohne Quellangabe darf eine
 * bereits bekannte Quelle nicht auf "unbekannt" zuruecksetzen, und die
 * Zwei-Parameter-Form ({@link PackState#addOrUpdate(String, boolean)}, von
 * jedem Aufrufer vor X3 benutzt) bleibt unveraendert quellenlos.
 */
public final class PackStateTest {

	@Test
	public void newEntryWithSourceCarriesIt() {
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel", true, PackState.SOURCE_LOCAL_LIBRARY);

		assertEquals(state.packs.get(0).source, PackState.SOURCE_LOCAL_LIBRARY);
		assertTrue(state.isActive("vitrine/beispiel"));
	}

	@Test
	public void twoArgumentOverloadLeavesSourceNull() {
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel", true);

		assertNull(state.packs.get(0).source, "die alte Zwei-Parameter-Form darf keine Quelle erfinden");
	}

	@Test
	public void plainActiveToggleDoesNotClearAnAlreadyKnownSource() {
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel", true, PackState.SOURCE_CATALOG_PREFIX + "example.invalid");

		// Ein spaeterer reiner An-/Aus-Wechsel (wie PackInstaller#setActive ihn macht) ...
		state.addOrUpdate("vitrine/beispiel", false);

		assertEquals(state.packs.get(0).source, PackState.SOURCE_CATALOG_PREFIX + "example.invalid",
				"ein An-/Aus-Wechsel ohne neue Quellangabe darf die zuletzt bekannte Quelle nicht loeschen");
		assertEquals(state.packs.size(), 1);
	}

	@Test
	public void newSourceOverwritesAnExistingOne() {
		PackState state = new PackState();
		state.addOrUpdate("vitrine/beispiel", true, PackState.SOURCE_LOCAL_LIBRARY);

		state.addOrUpdate("vitrine/beispiel", true, PackState.SOURCE_CATALOG_PREFIX + "example.invalid");

		assertEquals(state.packs.get(0).source, PackState.SOURCE_CATALOG_PREFIX + "example.invalid",
				"eine spaeter uebergebene, echte Quelle (z. B. nach einem Katalog-Update) muss uebernommen werden");
	}
}
