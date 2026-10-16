package de.spassglas.vitrine.ui.nav;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * GATE18: die Nav-Rail nutzt die 18 mitgelieferten {@code nav_*}-Icons.
 * Vorher lagen sie ungenutzt im Jar. Dieser Test stellt sicher, dass jeder
 * Eintrag auf zwei Dateien zeigt, die es wirklich gibt -- ein Tippfehler im
 * Namen wuerde sonst erst im Spiel als fehlende Textur auffallen.
 */
public class NavRailIconsTest {

	@Test
	public void everyEntryPointsToAnOutlineAndAFilledIconThatExist() throws IOException {
		for (NavRailEntry entry : NavRailEntry.values()) {
			assertResourceExists(entry.outlineIconPath(), entry);
			assertResourceExists(entry.filledIconPath(), entry);
		}
	}

	@Test
	public void allEighteenDeliveredNavIconsAreUsed() {
		Set<String> used = new HashSet<>();
		for (NavRailEntry entry : NavRailEntry.values()) {
			used.add(entry.outlineIconPath());
			used.add(entry.filledIconPath());
		}
		assertEquals(used.size(), NavRailEntry.values().length * 2,
				"je Eintrag Umriss + aktiv -- keines doppelt, keines vergessen (seit M10 mit Addons)");
	}

	private static void assertResourceExists(String path, NavRailEntry entry) throws IOException {
		try (InputStream in = NavRailIconsTest.class.getResourceAsStream("/assets/vitrine/" + path)) {
			assertNotNull(in, entry + " zeigt auf ein Icon, das nicht im Jar liegt: " + path);
			byte[] header = in.readNBytes(8);
			assertTrue(header.length == 8 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G',
					entry + ": " + path + " ist kein PNG");
		}
	}
}
