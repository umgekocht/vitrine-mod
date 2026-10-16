package de.spassglas.vitrine.ui.tile;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt die einzeilige Namenskuerzung aus docs/UI-SPEC.md "4. Kachel" ab
 * ("Name (einzeilig, mit Auslassungspunkten)"). Die Breitenmessung wird als
 * feste Ersatzfunktion hereingereicht (jedes Zeichen 10 px, das
 * Auslassungszeichen ebenfalls 10 px), damit die Kuerzungs-Logik ohne
 * laufenden {@code TextRenderer} exakt nachrechenbar ist.
 */
public final class TileLabelTest {

	private static final TileLabel.WidthFn TEN_PX_PER_CHAR = text -> text.length() * 10;

	@Test
	public void textThatFitsIsReturnedUnchanged() {
		assertEquals(TileLabel.truncateToWidth("Klarglas", 200, TEN_PX_PER_CHAR), "Klarglas");
	}

	@Test
	public void textExactlyAtWidthIsNotTruncated() {
		assertEquals(TileLabel.truncateToWidth("abcd", 40, TEN_PX_PER_CHAR), "abcd");
	}

	@Test
	public void tooWideTextGetsLongestPrefixPlusEllipsisThatStillFits() {
		String result = TileLabel.truncateToWidth("abcdefghij", 55, TEN_PX_PER_CHAR);

		// Ellipsis = 10 px, also passen 4 Zeichen (40) + Ellipsis (10) = 50 <= 55, 5 Zeichen (60) nicht.
		assertEquals(result, "abcd" + TileLabel.ELLIPSIS);
		assertTrue(TEN_PX_PER_CHAR.widthOf(result) <= 55, "Ergebnis muss in maxWidth passen");
	}

	@Test
	public void whenOnlyTheEllipsisFitsResultIsJustTheEllipsis() {
		String result = TileLabel.truncateToWidth("abcdef", 15, TEN_PX_PER_CHAR);

		assertEquals(result, TileLabel.ELLIPSIS);
		assertTrue(TEN_PX_PER_CHAR.widthOf(result) <= 15);
	}

	@Test
	public void nonPositiveWidthYieldsEmptyString() {
		assertEquals(TileLabel.truncateToWidth("abcdef", 0, TEN_PX_PER_CHAR), "");
		assertEquals(TileLabel.truncateToWidth("abcdef", -20, TEN_PX_PER_CHAR), "");
	}

	@Test
	public void widthTooSmallForEvenTheEllipsisYieldsEmptyString() {
		assertEquals(TileLabel.truncateToWidth("abcdef", 5, TEN_PX_PER_CHAR), "");
	}

	@Test
	public void trailingSpaceBeforeEllipsisIsTrimmed() {
		// Groesster Praefix ist "ab " (3 Zeichen, 30 px) + Ellipsis (10) = 40 <= 44;
		// das nachlaufende Leerzeichen wird vor dem Anhaengen der Ellipsis entfernt.
		String result = TileLabel.truncateToWidth("ab cdefgh", 44, TEN_PX_PER_CHAR);
		assertEquals(result, "ab" + TileLabel.ELLIPSIS);
	}

	@Test
	public void fitsHelperMatchesTruncationDecision() {
		assertTrue(TileLabel.fits("abc", 30, TEN_PX_PER_CHAR));
		assertTrue(!TileLabel.fits("abcd", 30, TEN_PX_PER_CHAR));
	}
}
