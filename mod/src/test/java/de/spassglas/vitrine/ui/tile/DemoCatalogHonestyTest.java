package de.spassglas.vitrine.ui.tile;

import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * GATE16 (Charter-Regel 2): Demo-Daten duerfen nur Zustaende zeigen, die zur
 * Inhaltsklasse passen. Vorher trug die Demo-Reihe "Neustart noetig" auf
 * einem Sound-Paket -- Klasse A, also ohne Neustart wirksam.
 */
public class DemoCatalogHonestyTest {

	@Test
	public void restartRequiredAppearsOnlyOnJavaModOffers() {
		for (TileData tile : DemoCatalog.statusShowcaseRow()) {
			if (tile.status() == TileStatus.RESTART_REQUIRED) {
				assertTrue(tile.javaMod(), "'Neustart noetig' auf Klasse-A-Kachel: " + tile.id());
			}
			if (tile.javaMod()) {
				assertEquals(tile.status(), TileStatus.RESTART_REQUIRED,
						"ein Java-Mod muss ehrlich als 'Neustart noetig' erscheinen: " + tile.id());
			}
		}
	}

	@Test
	public void showcaseRowStillCoversAllFourStatusMarks() {
		// S1-Review-Kriterium: eine Reihe mit allen vier Statuszeichen.
		Set<TileStatus> seen = EnumSet.noneOf(TileStatus.class);
		for (TileData tile : DemoCatalog.statusShowcaseRow()) {
			seen.add(tile.status());
		}
		assertEquals(seen, EnumSet.allOf(TileStatus.class));
	}

	@Test
	public void aKlasseATileClaimingRestartCannotEvenBeCreated() {
		expectThrows(IllegalArgumentException.class, () -> TileData.withoutImage("x.y", "X", "a",
				TilePackageType.SOUND, TileStatus.RESTART_REQUIRED));
	}
}
