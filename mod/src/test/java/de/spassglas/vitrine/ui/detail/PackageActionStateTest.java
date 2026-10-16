package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * ROADMAP S3 Fertig-Nachweis, Teil "Aktionsknopf-Zustand wird korrekt aus
 * {@code TileStatus}/Installationszustand abgeleitet" -- und alle in der
 * Backlog-Zeile geforderten Zustaende (Installieren / Installiert /
 * Aktualisieren / „Neustart nötig" / arbeitet… / Fehler) sind erreichbar.
 */
public final class PackageActionStateTest {

	@Test
	public void idlePhaseMapsEachTileStatusToItsButtonState() {
		assertEquals(PackageActionState.derive(TileStatus.NONE, PackageInstallPhase.IDLE),
				PackageActionState.INSTALL);
		assertEquals(PackageActionState.derive(TileStatus.INSTALLED, PackageInstallPhase.IDLE),
				PackageActionState.INSTALLED);
		assertEquals(PackageActionState.derive(TileStatus.UPDATE_AVAILABLE, PackageInstallPhase.IDLE),
				PackageActionState.UPDATE);
		assertEquals(PackageActionState.derive(TileStatus.RESTART_REQUIRED, PackageInstallPhase.IDLE),
				PackageActionState.RESTART_REQUIRED);
	}

	@Test
	public void runningVorgangWinsOverIdleInstallationState() {
		for (TileStatus status : TileStatus.values()) {
			assertEquals(PackageActionState.derive(status, PackageInstallPhase.WORKING),
					PackageActionState.WORKING, "arbeitet… gilt fuer jeden Installationszustand");
			assertEquals(PackageActionState.derive(status, PackageInstallPhase.FAILED),
					PackageActionState.ERROR, "Fehler gilt fuer jeden Installationszustand");
		}
	}

	@Test
	public void everyStateHasResolvableLabelInBothLocales() {
		for (PackageActionState state : PackageActionState.values()) {
			for (String locale : new String[] {"en_us", "de_de"}) {
				String label = state.describe(locale);
				org.testng.Assert.assertNotNull(label);
				org.testng.Assert.assertFalse(label.isBlank(),
						state + " hat in " + locale + " keine Beschriftung");
				org.testng.Assert.assertNotEquals(label, state.translationKey(),
						state + ": Key '" + state.translationKey() + "' ist in " + locale + " nicht uebersetzt");
			}
		}
	}
}
