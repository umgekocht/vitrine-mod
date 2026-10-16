package de.spassglas.vitrine.ui.search;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das Verhalten des In-Memory-Verlaufs fuer das Vorschlagsfeld
 * (ROADMAP S6, docs/UI-SPEC.md „1. Kopfleiste": „letzte Suchen"): juengste
 * zuerst, keine Dublette, blanke Eingabe ignoriert, Obergrenze eingehalten.
 */
public final class RecentSearchesTest {

	@Test
	public void newestEntryComesFirst() {
		RecentSearches recent = new RecentSearches();
		recent.record("stone");
		recent.record("glass");
		recent.record("wood");

		assertEquals(recent.entries(), List.of("wood", "glass", "stone"));
	}

	@Test
	public void repeatedTermMovesToFrontWithoutDuplicating() {
		RecentSearches recent = new RecentSearches();
		recent.record("stone");
		recent.record("glass");
		recent.record("STONE");

		assertEquals(recent.entries(), List.of("STONE", "glass"),
				"erneuter Begriff (case-insensitiv) wandert nach vorn statt sich zu verdoppeln");
	}

	@Test
	public void blankInputIsIgnored() {
		RecentSearches recent = new RecentSearches();
		recent.record(null);
		recent.record("");
		recent.record("   ");

		assertTrue(recent.isEmpty());
		assertEquals(recent.entries(), List.of());
	}

	@Test
	public void entryIsTrimmedBeforeStoring() {
		RecentSearches recent = new RecentSearches();
		recent.record("  glass  ");

		assertEquals(recent.entries(), List.of("glass"));
	}

	@Test
	public void capIsEnforcedAndOldestFallsOff() {
		RecentSearches recent = new RecentSearches();
		for (int i = 1; i <= RecentSearches.MAX_ENTRIES + 3; i++) {
			recent.record("term" + i);
		}

		List<String> entries = recent.entries();
		assertEquals(entries.size(), RecentSearches.MAX_ENTRIES);
		assertEquals(entries.get(0), "term" + (RecentSearches.MAX_ENTRIES + 3), "juengster erhalten");
		assertFalse(entries.contains("term1"), "aeltester weggefallen");
		assertFalse(entries.contains("term3"));
	}

	@Test
	public void clearEmptiesTheHistory() {
		RecentSearches recent = new RecentSearches();
		recent.record("stone");
		recent.clear();

		assertTrue(recent.isEmpty());
	}

	@Test
	public void entriesViewIsImmutable() {
		RecentSearches recent = new RecentSearches();
		recent.record("stone");
		try {
			recent.entries().add("glass");
			assertFalse(true, "entries() muss unveraenderlich sein");
		} catch (UnsupportedOperationException expected) {
			// gewuenscht
		}
	}
}
