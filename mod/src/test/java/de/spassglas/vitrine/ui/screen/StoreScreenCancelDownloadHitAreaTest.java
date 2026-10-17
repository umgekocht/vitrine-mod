package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE86-Beweis (erster Teil: "nur das Wort 'Abbrechen' ... ist
 * klickbar, nicht die ganze Zeile"). {@code StoreScreen} selbst laesst sich
 * ohne laufenden {@code MinecraftClient} nicht aufbauen (siehe {@code
 * StoreScreenLocalLibraryRowTest}), deshalb beweist dieser Test die reine,
 * client-freie Geometrie hinter {@link StoreScreen#cancelLabelRect} und
 * {@link StoreScreen#pointInsideRect}, mit denen {@code onCancelLabel} die
 * Trefferflaeche eines Klicks auf eine Download-Flyout-Zeile einschraenkt.
 */
public final class StoreScreenCancelDownloadHitAreaTest {

	private static final int ROW_TOP = 200;
	private static final int ROW_RIGHT = 500;
	private static final int CANCEL_TEXT_WIDTH = 60;

	@Test
	public void aPointOnTheCancelWordIsInsideTheHitArea() {
		int[] rect = StoreScreen.cancelLabelRect(ROW_TOP, ROW_RIGHT, CANCEL_TEXT_WIDTH);

		// Mittig im Wort "Abbrechen" selbst.
		double x = rect[0] + rect[2] / 2.0;
		double y = rect[1] + rect[3] / 2.0;

		assertTrue(StoreScreen.pointInsideRect(x, y, rect),
				"ein Klick mittig auf das Wort 'Abbrechen' muss als Treffer gelten");
	}

	@Test
	public void aPointAtTheLeftEdgeOfTheRowButNotOnTheWordIsOutsideTheHitArea() {
		int[] rect = StoreScreen.cancelLabelRect(ROW_TOP, ROW_RIGHT, CANCEL_TEXT_WIDTH);

		// Linker Rand der Zeile, weit links vom rechtsbuendigen Wort
		// "Abbrechen" -- vorher (ganze Zeile klickbar) waere das ein Treffer
		// gewesen, jetzt nicht mehr.
		double x = ROW_RIGHT - CANCEL_TEXT_WIDTH - VitrineTheme.SPACE_UNIT * 10;
		double y = rect[1] + rect[3] / 2.0;

		assertFalse(x >= rect[0], "Testvoraussetzung: der Punkt muss tatsaechlich links vom Wort liegen");
		assertFalse(StoreScreen.pointInsideRect(x, y, rect),
				"ein Klick links vom Wort 'Abbrechen' (aber noch in der Zeile) darf kein Treffer mehr sein");
	}

	@Test
	public void aPointJustBelowTheCancelWordLineIsOutsideTheHitArea() {
		int[] rect = StoreScreen.cancelLabelRect(ROW_TOP, ROW_RIGHT, CANCEL_TEXT_WIDTH);

		// Unterhalb der Zeile, in der "Abbrechen" steht (z.B. auf dem
		// Fortschrittsbalken weiter unten in derselben Flyout-Zeile).
		double x = rect[0] + rect[2] / 2.0;
		double y = rect[1] + rect[3] + 20;

		assertFalse(StoreScreen.pointInsideRect(x, y, rect),
				"ein Klick unterhalb der 'Abbrechen'-Zeile darf kein Treffer sein");
	}

	@Test
	public void theHitAreaAddsOnlyASmallThemeDerivedPadding() {
		int[] rect = StoreScreen.cancelLabelRect(ROW_TOP, ROW_RIGHT, CANCEL_TEXT_WIDTH);

		assertTrue(rect[2] <= CANCEL_TEXT_WIDTH + VitrineTheme.SPACE_UNIT * 2 + 1,
				"die Trefferflaeche darf nur das Wort plus einen kleinen Theme-Innenabstand umfassen, nicht mehr: "
						+ rect[2]);
	}
}
