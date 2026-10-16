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
 * ROADMAP-GATE20-Beweis (zweiter Teil: "Deinstallieren fragt vorher nach"):
 * das Deinstallieren laeuft nur nach einem ausdruecklichen Bestaetigen.
 * Vorher fuehrten {@code LibraryScreen#onUninstall} und
 * {@code PackageDetailScreen#doUninstall} die Aktion direkt beim Klick aus.
 */
public final class UninstallConfirmModelTest {

	private static final List<String> LOCALES = List.of("de_de", "en_us");

	@Test
	public void freshModelHasNotDecidedAndHasNotUninstalledAnything() {
		AtomicInteger runs = new AtomicInteger();
		UninstallConfirmModel model = new UninstallConfirmModel("vitrine/beispiel", "Beispiel", runs::incrementAndGet);

		assertFalse(model.decided(), "frisch darf nichts entschieden sein");
		assertFalse(model.confirmed(), "nichts ist vorausgewaehlt (docs/UI-SPEC.md, kein Dunkelmuster)");
		assertEquals(runs.get(), 0, "ohne Bestaetigen darf nie deinstalliert werden");
	}

	@Test
	public void confirmRunsTheUninstallExactlyOnce() {
		AtomicInteger runs = new AtomicInteger();
		UninstallConfirmModel model = new UninstallConfirmModel("vitrine/beispiel", "Beispiel", runs::incrementAndGet);

		model.confirm();
		model.confirm();

		assertTrue(model.decided());
		assertTrue(model.confirmed());
		assertEquals(runs.get(), 1, "Doppelklick/Enter+Klick darf nicht doppelt deinstallieren");
	}

	@Test
	public void cancelNeverRunsTheUninstallNotEvenLater() {
		AtomicInteger runs = new AtomicInteger();
		UninstallConfirmModel model = new UninstallConfirmModel("vitrine/beispiel", "Beispiel", runs::incrementAndGet);

		model.cancel();
		assertEquals(runs.get(), 0);
		assertTrue(model.decided());
		assertFalse(model.confirmed());

		// Ein nachtraeglich hereinkommendes confirm (z. B. aus dem
		// Schliessen-Pfad des Bildschirms) bleibt wirkungslos.
		model.confirm();
		assertEquals(runs.get(), 0, "Abbrechen muss endgueltig sein");
		assertFalse(model.confirmed());
	}

	@Test
	public void modelKeepsPackIdAndDisplayNameForTheDialogHeadline() {
		UninstallConfirmModel model = new UninstallConfirmModel("vitrine/beispiel.paket", "Klarglas", () -> {
		});

		assertEquals(model.packId(), "vitrine/beispiel.paket");
		assertEquals(model.displayName(), "Klarglas");
	}

	/**
	 * Beide Knoepfe sind uebersetzt und gleichwertig benannt -- kein
	 * "OK"/"Abbrechen"-Gefaelle, keine Drohtexte (docs/UI-SPEC.md
	 * "8. Fähigkeiten-Dialog").
	 */
	@Test
	public void bothButtonsAndTheExplanationAreTranslatedInBothLocales() {
		List<String> keys = List.of(
				"vitrine.uninstall.dialog.title",
				"vitrine.uninstall.dialog.title_for",
				"vitrine.uninstall.dialog.body",
				"vitrine.uninstall.dialog.confirm",
				"vitrine.uninstall.dialog.cancel");

		for (String key : keys) {
			for (String locale : LOCALES) {
				String text = Translations.get(locale, key);
				assertNotEquals(text, key, locale + ": '" + key + "' fehlt in der Sprachdatei");
				assertFalse(text.isBlank(), locale + ": '" + key + "' ist leer");
			}
		}

		for (String locale : LOCALES) {
			assertNotEquals(Translations.get(locale, "vitrine.uninstall.dialog.cancel"),
					Translations.get(locale, "vitrine.uninstall.dialog.confirm"),
					locale + ": die beiden Knoepfe muessen unterscheidbar beschriftet sein");
		}
	}
}
