package de.spassglas.vitrine.hotapply;

import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * GATE36: der Rollback eines gescheiterten Reloads landet wirklich in
 * {@code state.json} -- echte Datei, kein Double.
 */
public class StateFileRollbackPersisterTest {

	@Test
	public void rolledBackPackIsInactiveInStateFileOthersUntouched() throws Exception {
		Path dir = Files.createTempDirectory("vitrine-gate36");
		PackStateStore.update(dir, state -> {
			state.addOrUpdate("vitrine/kaputt", true);
			state.addOrUpdate("vitrine/heil", true);
			return null;
		});

		StateFileRollbackPersister.forConfigDir(dir).markInactive(Set.of("vitrine/kaputt"));

		PackState result = PackStateStore.load(dir);
		assertTrue(result.isInstalled("vitrine/kaputt"), "zurueckgenommen heisst deaktiviert, nicht deinstalliert");
		assertFalse(result.isActive("vitrine/kaputt"), "nach dem Rollback darf state.json es nicht als aktiv fuehren");
		assertTrue(result.isActive("vitrine/heil"), "unbeteiligte Pakete bleiben aktiv");
	}

	@Test
	public void rollbackNeverCreatesEntriesForUnknownPacks() throws Exception {
		Path dir = Files.createTempDirectory("vitrine-gate36-unknown");
		PackStateStore.update(dir, state -> {
			state.addOrUpdate("vitrine/bekannt", true);
			return null;
		});

		StateFileRollbackPersister.forConfigDir(dir).markInactive(Set.of("vitrine/unbekannt"));

		PackState result = PackStateStore.load(dir);
		assertFalse(result.isInstalled("vitrine/unbekannt"), "ein Rollback legt keine neuen Eintraege an");
		assertTrue(result.isActive("vitrine/bekannt"));
	}
}
