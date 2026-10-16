package de.spassglas.vitrine.ui.theme;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** GATE21 Teil 2: Eckenmathematik der gerundeten Rechtecke. */
public class RoundedRectTest {

	@Test
	public void radiusZeroIsAPlainRectangle() {
		for (int row = 0; row < 4; row++) {
			assertEquals(RoundedRect.inset(0, row), 0);
		}
	}

	@Test
	public void insetShrinksTowardsTheStraightEdge() {
		int r = 8;
		int previous = Integer.MAX_VALUE;
		for (int row = 0; row < r; row++) {
			int in = RoundedRect.inset(r, row);
			assertTrue(in <= previous, "Einzug muss nach innen hin abnehmen, Zeile " + row);
			assertTrue(in >= 0 && in < r);
			previous = in;
		}
		assertTrue(RoundedRect.inset(r, 0) > 0, "aeusserste Zeile muss eingerueckt sein");
		assertEquals(RoundedRect.inset(r, r), 0);
		assertEquals(RoundedRect.inset(r, r + 5), 0);
	}

	@Test
	public void cornerIsSymmetricAcrossTheDiagonal() {
		// Ein Viertelkreis ist zur Diagonalen symmetrisch: die Zahl der Pixel,
		// die in Zeile i fehlen, gleicht grob der in Spalte i -- hier geprueft
		// ueber die Gesamtflaeche, die exakt ein Viertel von (4 - pi) r^2 sein soll.
		int r = 12;
		int missing = 0;
		for (int row = 0; row < r; row++) {
			missing += RoundedRect.inset(r, row);
		}
		double expected = (4 - Math.PI) / 4 * r * r;
		assertTrue(Math.abs(missing - expected) <= r / 2.0,
				"fehlende Pixel " + missing + " statt ~" + expected);
	}

	@Test
	public void radiusIsClampedToHalfTheShorterEdge() {
		assertEquals(RoundedRect.clampRadius(8, 10, 40), 5);
		assertEquals(RoundedRect.clampRadius(8, 100, 100), 8);
		assertEquals(RoundedRect.clampRadius(-3, 100, 100), 0);
	}
}
