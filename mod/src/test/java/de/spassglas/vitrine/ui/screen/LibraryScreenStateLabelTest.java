package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;

/**
 * ROADMAP GATE89-Beweis: eine inaktive Bibliothekszeile beschriftete ihren
 * Zustand bisher mit der Aktion "Aktivieren" statt mit einer eigenen
 * Zustandsanzeige -- das las sich wie ein zweiter Knopf. {@link
 * LibraryScreen#stateLabelKey} (Zustand) und {@link
 * LibraryScreen#toggleActionLabelKey} (Aktion des Schalter-Knopfs) muessen
 * auf unterscheidbare, nicht verwechselbare Texte aufloesen -- fuer beide
 * Zustaende und beide Sprachen.
 */
public final class LibraryScreenStateLabelTest {

	private static final List<String> LOCALES = List.of("de_de", "en_us");

	@Test
	public void stateLabelNamesTheStateNotTheAction() {
		assertEquals(LibraryScreen.stateLabelKey(true), "vitrine.library.state.on");
		assertEquals(LibraryScreen.stateLabelKey(false), "vitrine.library.state.off");
	}

	@Test
	public void toggleActionLabelNamesTheNextActionNotTheState() {
		assertEquals(LibraryScreen.toggleActionLabelKey(true), "vitrine.action.deactivate");
		assertEquals(LibraryScreen.toggleActionLabelKey(false), "vitrine.action.activate");
	}

	@Test
	public void stateTextAndActionTextAreDistinctStringsInBothLocalesForBothStates() {
		for (String locale : LOCALES) {
			for (boolean active : new boolean[] {true, false}) {
				String stateText = Translations.get(locale, LibraryScreen.stateLabelKey(active));
				String actionText = Translations.get(locale, LibraryScreen.toggleActionLabelKey(active));

				assertNotEquals(stateText, actionText,
						locale + " (aktiv=" + active + "): Zustandstext und Aktionstext duerfen sich nicht gleich lesen: '"
								+ stateText + "' vs. '" + actionText + "'");
				assertFalse(stateText.isBlank(), "Zustandstext darf nicht leer sein");
			}
		}
	}

	/**
	 * Fuer eine inaktive Zeile ist "Aktivieren" die Aktion, nicht der
	 * Zustandstext -- die urspruengliche Verwechslungsgefahr des GATE89-
	 * Berichts.
	 */
	@Test
	public void theWordUsedAsTheActivateActionNeverAppearsAsTheInactiveStateLabel() {
		for (String locale : LOCALES) {
			String activateAction = Translations.get(locale, LibraryScreen.toggleActionLabelKey(false));
			String inactiveState = Translations.get(locale, LibraryScreen.stateLabelKey(false));

			assertNotEquals(inactiveState, activateAction,
					locale + ": der Zustandstext einer inaktiven Zeile darf nicht wortgleich mit der Aktion "
							+ "'" + activateAction + "' sein");
		}
	}
}
