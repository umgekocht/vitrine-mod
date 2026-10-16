package de.spassglas.vitrine.ui.downloads;

import de.spassglas.vitrine.catalog.download.CancellationToken;
import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Beweist die S8-Verhalten des client-freien {@link DownloadFlyoutModel}:
 * Trennung laufend / zuletzt abgeschlossen, Abbrechen verschiebt bzw. entfernt,
 * der Knopf-Zaehler zaehlt nur Laufende, noch unbestimmter Fortschritt ist als
 * solcher gekennzeichnet.
 */
public final class DownloadFlyoutModelTest {

	@Test
	public void freshModelIsEmptyAndCarriesTheUnknownProgressNote() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();

		assertTrue(model.isEmpty());
		assertEquals(model.runningCount(), 0);
		assertTrue(model.running().isEmpty());
		assertTrue(model.recent().isEmpty());
		assertFalse(model.realProgressAvailable(),
				"ohne gemeldete Bytes kein erfundener Fortschritt (Charter-Regel 2)");
		assertEquals(model.progressNoteKey(), "vitrine.downloads.progress.unknown_note");
	}

	/**
	 * ROADMAP-GATE12-Beweis: der Hinweis im Flyout nennt keine interne Kennung
	 * mehr (frueher stand woertlich „K3" darin) und behauptet nichts
	 * Ueberholtes -- er beschreibt genau das, was zutrifft: es sind noch keine
	 * Byte-Zahlen bekannt.
	 */
	@Test
	public void theProgressNoteIsTranslatedAndFreeOfInternalIdentifiers() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		String key = model.progressNoteKey();

		for (String locale : List.of("de_de", "en_us")) {
			String text = Translations.get(locale, key);
			assertNotEquals(text, key, locale + ": '" + key + "' fehlt in der Sprachdatei");
			assertFalse(text.contains("K3"), locale + ": interne Kennung im sichtbaren Text: " + text);
			assertFalse(text.toLowerCase().contains("katalog") || text.toLowerCase().contains("catalog"),
					locale + ": der Hinweis behauptet weiterhin, echter Fortschritt komme erst mit dem "
							+ "Katalog -- das stimmt seit GATE4 nicht mehr: " + text);
		}
	}

	/** Sobald echte Bytes gemeldet werden, verschwindet der Hinweis (GATE4/GATE12). */
	@Test
	public void theProgressNoteDisappearsOnceRealBytesAreReported() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1_000L);
		assertFalse(model.realProgressAvailable());

		model.updateProgress("vitrine/a", 500L, 1_000L);

		assertTrue(model.realProgressAvailable(),
				"nach echtem Fortschritt zeigt die Oberflaeche den Balken statt des Hinweises");
	}

	@Test
	public void runningAndFinishedDownloadsAreKeptInSeparateLists() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1_024L);
		model.startDownload("vitrine/b", "Paket B", 2_048L);

		assertEquals(model.running().size(), 2);
		assertTrue(model.recent().isEmpty());

		model.markDone("vitrine/a");

		assertEquals(model.running().size(), 1);
		assertEquals(model.running().get(0).packId(), "vitrine/b");
		assertEquals(model.recent().size(), 1);
		assertEquals(model.recent().get(0).packId(), "vitrine/a");
		assertEquals(model.recent().get(0).phase(), DownloadPhase.DONE);
		assertFalse(model.isEmpty());
	}

	@Test
	public void runningEntriesAreIndeterminateUntilK3() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 4_096L);

		DownloadEntry entry = model.running().get(0);
		assertTrue(entry.indeterminate(), "kein Fake-Fortschritt");
		assertTrue(entry.progress().isEmpty());
		assertTrue(entry.hasKnownSize());
		assertEquals(entry.phase(), DownloadPhase.RUNNING);
	}

	@Test
	public void cancelOnARunningDownloadMovesItToRecentAsCancelled() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 10L);

		assertTrue(model.cancel("vitrine/a"));

		assertTrue(model.running().isEmpty());
		assertEquals(model.recent().size(), 1);
		assertEquals(model.recent().get(0).phase(), DownloadPhase.CANCELLED);
	}

	@Test
	public void cancelOnAnAlreadyFinishedRowRemovesIt() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 10L);
		model.markFailed("vitrine/a");
		assertEquals(model.recent().size(), 1);
		assertEquals(model.recent().get(0).phase(), DownloadPhase.FAILED);

		assertTrue(model.cancel("vitrine/a"), "Klick auf Abbrechen einer fertigen Zeile entfernt sie");
		assertTrue(model.recent().isEmpty());
		assertTrue(model.isEmpty());

		assertFalse(model.cancel("vitrine/a"), "nichts mehr da -> nichts passiert");
	}

	@Test
	public void counterCountsOnlyRunningDownloads() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1L);
		model.startDownload("vitrine/b", "Paket B", 1L);
		model.startDownload("vitrine/c", "Paket C", 1L);
		assertEquals(model.runningCount(), 3);

		model.markDone("vitrine/a");
		model.cancel("vitrine/b");

		assertEquals(model.runningCount(), 1, "fertige und abgebrochene zaehlen nicht");
		assertEquals(model.recent().size(), 2);
	}

	@Test
	public void recentListKeepsNewestFirstAndIsCapped() {
		DownloadFlyoutModel model = new DownloadFlyoutModel(2);
		for (String id : List.of("a", "b", "c")) {
			model.startDownload("vitrine/" + id, "Paket " + id, 1L);
			model.markDone("vitrine/" + id);
		}

		List<DownloadEntry> recent = model.recent();
		assertEquals(recent.size(), 2, "auf die Kapazitaet begrenzt");
		assertEquals(recent.get(0).packId(), "vitrine/c", "neueste zuerst");
		assertEquals(recent.get(1).packId(), "vitrine/b");
	}

	@Test
	public void allRowsListsRunningBeforeRecent() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1L);
		model.markDone("vitrine/a");
		model.startDownload("vitrine/b", "Paket B", 1L);

		List<DownloadEntry> rows = model.allRows();
		assertEquals(rows.size(), 2);
		assertEquals(rows.get(0).packId(), "vitrine/b", "laufende zuerst");
		assertEquals(rows.get(1).packId(), "vitrine/a");
	}

	@Test
	public void restartingARunningDownloadReplacesTheEntry() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1L);
		model.startDownload("vitrine/a", "Paket A (retry)", 2L);

		assertEquals(model.running().size(), 1);
		assertEquals(model.running().get(0).displayName(), "Paket A (retry)");
		assertEquals(model.running().get(0).sizeBytes(), 2L);
	}

	// --- ROADMAP GATE4: echter Fortschritts-Rueckruf ---------------------

	@Test
	public void updateProgressFillsTheProgressFractionAndFlipsRealProgressAvailable() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 1_000L);
		assertFalse(model.realProgressAvailable());

		model.updateProgress("vitrine/a", 250L, 1_000L);

		assertTrue(model.realProgressAvailable(), "nach einem echten Fortschritts-Ruf bleibt es dauerhaft wahr");
		DownloadEntry entry = model.running().get(0);
		assertFalse(entry.indeterminate());
		assertEquals(entry.progress().getAsDouble(), 0.25, 0.0001);
	}

	@Test
	public void updateProgressWithoutKnownTotalStaysIndeterminateButMarksRealProgress() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", -1L);

		model.updateProgress("vitrine/a", 4_096L, -1L);

		assertTrue(model.realProgressAvailable(), "der Rueckruf selbst ist bereits echt, auch ohne bekannte Gesamtgroesse");
		assertTrue(model.running().get(0).indeterminate(), "ohne totalBytes bleibt der Anteil unbestimmt, kein Fake-Wert");
	}

	@Test
	public void updateProgressForAnUnknownPackIdIsANoopButStillFlipsRealProgressAvailable() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();

		model.updateProgress("vitrine/unbekannt", 10L, 100L);

		assertTrue(model.realProgressAvailable());
		assertTrue(model.running().isEmpty());
		assertTrue(model.isEmpty());
	}

	// --- ROADMAP GATE9: der Abbrechen-Knopf beendet den echten Download --

	@Test
	public void cancelInvokesTheRegisteredCancellationToken() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		CancellationToken token = new CancellationToken();
		model.startDownload("vitrine/a", "Paket A", 10L, token);

		assertFalse(token.isCancelled());
		assertTrue(model.cancel("vitrine/a"));

		assertTrue(token.isCancelled(), "Abbrechen im Flyout muss den echten Download-Abbruch anstossen");
		assertEquals(model.recent().get(0).phase(), DownloadPhase.CANCELLED);
	}

	@Test
	public void cancelWithoutARegisteredTokenStillMovesTheRowButTouchesNothingElse() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.startDownload("vitrine/a", "Paket A", 10L);

		assertTrue(model.cancel("vitrine/a"));
		assertEquals(model.recent().get(0).phase(), DownloadPhase.CANCELLED);
	}

	@Test
	public void markCancelledIsASafeNoopWithoutARunningEntry() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		model.markCancelled("vitrine/nichts-laeuft");
		assertTrue(model.isEmpty());
	}

	@Test
	public void discardRunningRemovesTheRowWithoutRecordingItAsFinished() {
		DownloadFlyoutModel model = new DownloadFlyoutModel();
		CancellationToken token = new CancellationToken();
		model.startDownload("vitrine/a", "Paket A", 10L, token);

		model.discardRunning("vitrine/a");

		assertTrue(model.isEmpty(), "verworfen heisst weder gelaufen noch abgeschlossen -- Zeile ist einfach weg");
		assertFalse(token.isCancelled(), "discardRunning ist kein Abbruch -- der Download selbst war schon fertig");
	}
}
