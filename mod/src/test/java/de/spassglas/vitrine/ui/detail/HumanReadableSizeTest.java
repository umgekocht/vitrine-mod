package de.spassglas.vitrine.ui.detail;

import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Deckt die "Größe"-Zeile des Details-Abschnitts ab (docs/UI-SPEC.md "5. Detailseite"). */
public final class HumanReadableSizeTest {

	@Test
	public void negativeSizeMeansUnknown() {
		assertTrue(HumanReadableSize.format(-1L).isEmpty());
	}

	@Test
	public void bytesBelowOneKiloAreShownAsBytes() {
		assertEquals(HumanReadableSize.format(0L), Optional.of("0 B"));
		assertEquals(HumanReadableSize.format(742L), Optional.of("742 B"));
		assertEquals(HumanReadableSize.format(999L), Optional.of("999 B"));
	}

	@Test
	public void kiloAndMegaRangesUseOneDecimalAndRootLocale() {
		assertEquals(HumanReadableSize.format(1000L), Optional.of("1.0 KB"));
		assertEquals(HumanReadableSize.format(12_345L), Optional.of("12.3 KB"));
		assertEquals(HumanReadableSize.format(4_000_000L), Optional.of("4.0 MB"));
		assertEquals(HumanReadableSize.format(1_500_000L), Optional.of("1.5 MB"));
	}
}
