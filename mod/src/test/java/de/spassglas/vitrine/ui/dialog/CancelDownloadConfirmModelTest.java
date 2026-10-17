package de.spassglas.vitrine.ui.dialog;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE86-Beweis (zweiter Teil: "Klick zeigt eine Bestaetigungs-
 * Rueckfrage, erst Zustimmung bricht ab"). Vorher fuehrte ein Klick auf die
 * Download-Flyout-Zeile {@code DownloadFlyoutModel#cancel} sofort aus
 * ({@code StoreScreen#mouseClicked}); jetzt entsteht dort nur noch diese
 * offene Entscheidung, ausgefuehrt wird sie erst ueber
 * {@link ConfirmCancelDownloadDialog}.
 */
public final class CancelDownloadConfirmModelTest {

	private static final List<String> LOCALES = List.of("de_de", "en_us");

	@Test
	public void freshModelHasNotDecidedAndHasNotCancelledAnything() {
		AtomicInteger runs = new AtomicInteger();
		CancelDownloadConfirmModel model = new CancelDownloadConfirmModel("vitrine/beispiel", "Beispiel",
				runs::incrementAndGet);

		assertFalse(model.decided(), "frisch darf nichts entschieden sein");
		assertFalse(model.confirmed(), "nichts ist vorausgewaehlt");
		assertEquals(runs.get(), 0, "ohne Bestaetigen darf der Download nie abgebrochen werden");
	}

	@Test
	public void confirmRunsTheCancelExactlyOnce() {
		AtomicInteger runs = new AtomicInteger();
		CancelDownloadConfirmModel model = new CancelDownloadConfirmModel("vitrine/beispiel", "Beispiel",
				runs::incrementAndGet);

		model.confirm();
		model.confirm();

		assertTrue(model.decided());
		assertTrue(model.confirmed());
		assertEquals(runs.get(), 1, "Doppelklick/Enter+Klick darf nicht doppelt abbrechen");
	}

	@Test
	public void cancelNeverRunsTheDownloadCancelNotEvenLater() {
		AtomicInteger runs = new AtomicInteger();
		CancelDownloadConfirmModel model = new CancelDownloadConfirmModel("vitrine/beispiel", "Beispiel",
				runs::incrementAndGet);

		model.cancel();
		assertEquals(runs.get(), 0);
		assertTrue(model.decided());
		assertFalse(model.confirmed());

		model.confirm();
		assertEquals(runs.get(), 0, "Weiterlaufen-lassen muss endgueltig sein");
		assertFalse(model.confirmed());
	}

	@Test
	public void bothButtonsAndTheExplanationAreTranslatedInBothLocales() {
		List<String> keys = List.of(
				"vitrine.downloads.cancel_dialog.title",
				"vitrine.downloads.cancel_dialog.title_for",
				"vitrine.downloads.cancel_dialog.body",
				"vitrine.downloads.cancel_dialog.confirm",
				"vitrine.downloads.cancel_dialog.keep");

		for (String key : keys) {
			for (String locale : LOCALES) {
				String text = Translations.get(locale, key);
				assertNotEquals(text, key, locale + ": '" + key + "' fehlt in der Sprachdatei");
				assertFalse(text.isBlank(), locale + ": '" + key + "' ist leer");
			}
		}

		for (String locale : LOCALES) {
			assertNotEquals(Translations.get(locale, "vitrine.downloads.cancel_dialog.keep"),
					Translations.get(locale, "vitrine.downloads.cancel_dialog.confirm"),
					locale + ": die beiden Knoepfe muessen unterscheidbar beschriftet sein");
		}
	}
}
