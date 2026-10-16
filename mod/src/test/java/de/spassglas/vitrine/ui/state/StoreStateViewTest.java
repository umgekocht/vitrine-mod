package de.spassglas.vitrine.ui.state;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Fertig-Nachweis fuer ROADMAP S7: alle sechs Zustaende aus der Spec-Tabelle
 * ({@code docs/UI-SPEC.md} "7. Zustaende") -- jeder mit genau einer
 * Ueberschrift, genau einem Erklaersatz und <em>genau einem</em> Knopf; der
 * Lade-Skeleton mit <em>keinem</em> Knopf und ohne Spinner. Client-frei ueber
 * die wiederverwendbare {@link StoreStateView}-Komponente.
 */
public final class StoreStateViewTest {

	private static final String[] LOCALES = {"en_us", "de_de"};

	/** Eine Auspraegung je Zeile der Spec-Tabelle. */
	private static List<StoreStateView> allStates() {
		return List.of(
				StoreStateView.libraryEmpty(),
				StoreStateView.searchNoResults("Crisp"),
				StoreStateView.offline(LocalDate.of(2026, 9, 1)),
				StoreStateView.catalogBroken(),
				StoreStateView.packageBroken("Crisp UI"),
				StoreStateView.loading());
	}

	@Test
	public void theSixSpecRowsAreDistinctAndComplete() {
		Set<StoreState> seen = EnumSet.noneOf(StoreState.class);
		for (StoreStateView view : allStates()) {
			seen.add(view.state());
		}
		assertEquals(seen.size(), 6, "sechs verschiedene Zustaende erwartet");
		assertEquals(seen.size(), StoreState.values().length, "jede StoreState-Konstante ist abgedeckt");
	}

	@Test
	public void everyStateCarriesExactlyOneSymbol() {
		for (StoreStateView view : allStates()) {
			assertNotNull(view.symbol(), view.state() + " braucht ein Symbol (Barrierefreiheit: nicht nur Farbe)");
		}
	}

	@Test
	public void everyStateNeverShowsASpinner() {
		for (StoreStateView view : allStates()) {
			assertFalse(view.showsSpinner(), view.state() + ": die Spec verbietet einen Spinner");
		}
	}

	@Test
	public void everyStateHasExactlyOneHeadingResolvedInBothLocales() {
		for (StoreStateView view : allStates()) {
			for (String locale : LOCALES) {
				assertTrue(Translations.has(locale, view.headingKey()),
						locale + " fuehrt den Ueberschrift-Key '" + view.headingKey() + "' nicht");
				String heading = view.heading(locale);
				assertFalse(heading.isBlank(), view.state() + ": leere Ueberschrift in " + locale);
				assertNotEquals(heading, view.headingKey(),
						view.state() + ": Ueberschrift-Key in " + locale + " nicht uebersetzt");
				assertFalse(heading.contains("%s"),
						view.state() + ": unaufgeloester Platzhalter in der Ueberschrift (" + locale + "): " + heading);
			}
		}
	}

	@Test
	public void headingPatternHasAPlaceholderExactlyWhenArgumentsArePresent() {
		for (StoreStateView view : allStates()) {
			for (String locale : LOCALES) {
				String pattern = Translations.get(locale, view.headingKey());
				if (view.headingArgs().isEmpty()) {
					assertFalse(pattern.contains("%s"),
							view.state() + ": Ueberschrift in " + locale + " hat einen Platzhalter ohne Argument");
				} else {
					assertTrue(pattern.contains("%s"),
							view.state() + ": Ueberschrift in " + locale + " muss einen %s-Platzhalter haben");
				}
			}
		}
	}

	@Test
	public void everyStateHasExactlyOneExplanationDistinctFromTheHeading() {
		for (StoreStateView view : allStates()) {
			assertNotEquals(view.explanationKey(), view.headingKey(),
					view.state() + ": Erklaersatz und Ueberschrift teilen sich einen Key");
			for (String locale : LOCALES) {
				assertTrue(Translations.has(locale, view.explanationKey()),
						locale + " fuehrt den Erklaersatz-Key '" + view.explanationKey() + "' nicht");
				String explanation = view.explanation(locale);
				assertFalse(explanation.isBlank(), view.state() + ": leerer Erklaersatz in " + locale);
				assertNotEquals(explanation, view.explanationKey(),
						view.state() + ": Erklaersatz-Key in " + locale + " nicht uebersetzt");
				assertNotEquals(explanation, view.heading(locale),
						view.state() + ": Erklaersatz gleicht der Ueberschrift in " + locale);
			}
		}
	}

	@Test
	public void invariantExactlyOneButtonExceptForTheSkeleton() {
		for (StoreStateView view : allStates()) {
			if (view.isSkeleton()) {
				assertFalse(view.hasAction(), "Lade-Skeleton darf keinen Knopf haben");
				assertTrue(view.action().isEmpty());
				continue;
			}
			assertTrue(view.hasAction(), view.state() + " braucht genau einen Knopf");
			StoreStateActionId action = view.action().orElseThrow();
			for (String locale : LOCALES) {
				String label = action.describe(locale);
				assertFalse(label.isBlank(), view.state() + ": Knopfbeschriftung leer in " + locale);
				assertNotEquals(label, action.labelKey(),
						view.state() + ": Knopf-Key in " + locale + " nicht uebersetzt");
			}
		}
	}

	@Test
	public void onlyTheOfflineStateCarriesACacheDate() {
		for (StoreStateView view : allStates()) {
			assertEquals(view.asOfDate().isPresent(), view.state() == StoreState.OFFLINE,
					view.state() + ": Standdatum nur beim Offline-Zustand");
		}
	}

	// --- je Zustand ein gezielter Nachweis gegen die Spec-Tabellenzeile ---

	@Test
	public void libraryEmptyMatchesItsSpecRow() {
		StoreStateView view = StoreStateView.libraryEmpty();
		assertEquals(view.state(), StoreState.LIBRARY_EMPTY);
		assertEquals(view.symbol(), StateSymbol.EMPTY);
		assertEquals(view.heading("de_de"), "Noch nichts installiert");
		assertEquals(view.action().orElseThrow(), StoreStateActionId.OPEN_STORE);
		assertEquals(view.action().orElseThrow().describe("de_de"), "Zum Store");
	}

	@Test
	public void searchNoResultsCarriesTheTrimmedTermInsideTheHeading() {
		StoreStateView view = StoreStateView.searchNoResults("  Shiny Blocks ");
		assertEquals(view.headingArgs(), List.of("Shiny Blocks"));
		assertTrue(view.heading("de_de").contains("Shiny Blocks"));
		assertTrue(view.heading("en_us").contains("Shiny Blocks"));
		assertEquals(view.action().orElseThrow(), StoreStateActionId.CLEAR_SEARCH);
		assertEquals(view.action().orElseThrow().describe("de_de"), "Suche löschen");
	}

	@Test
	public void offlineCarriesTheAsOfDateAsBothDateAndHeadingArgument() {
		LocalDate date = LocalDate.of(2026, 3, 4);
		StoreStateView view = StoreStateView.offline(date);
		assertEquals(view.asOfDate().orElseThrow(), date);
		assertEquals(view.headingArgs(), List.of("2026-03-04"));
		assertTrue(view.heading("de_de").contains("2026-03-04"));
		assertTrue(view.heading("en_us").contains("2026-03-04"));
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
	}

	@Test
	public void offlineRejectsAMissingDate() {
		assertThrows(NullPointerException.class, () -> StoreStateView.offline(null));
	}

	@Test
	public void offlineNoCacheReusesTheOfflineRowButCarriesNoDate() {
		StoreStateView view = StoreStateView.offlineNoCache();
		assertEquals(view.state(), StoreState.OFFLINE);
		assertTrue(view.asOfDate().isEmpty(), "ohne Cache gibt es kein ehrliches Standdatum (Charter-Regel 2)");
		assertEquals(view.symbol(), StateSymbol.WARNING);
		assertEquals(view.action().orElseThrow(), StoreStateActionId.RETRY);
		assertTrue(view.headingArgs().isEmpty());
		for (String locale : LOCALES) {
			assertTrue(Translations.has(locale, view.headingKey()),
					locale + " fuehrt den Ueberschrift-Key '" + view.headingKey() + "' nicht");
			assertFalse(view.heading(locale).isBlank(), "leere Ueberschrift in " + locale);
			assertFalse(view.heading(locale).contains("%s"), "kein Platzhalter ohne Argument (" + locale + ")");
			assertNotEquals(view.heading(locale), view.explanation(locale),
					"Ueberschrift und Erklaersatz teilen sich nichts in " + locale);
		}
	}

	@Test
	public void catalogBrokenAndOfflineShareTheRetryButton() {
		assertEquals(StoreStateView.catalogBroken().action().orElseThrow(), StoreStateActionId.RETRY);
		assertEquals(StoreStateView.offline(LocalDate.of(2026, 1, 1)).action().orElseThrow(),
				StoreStateActionId.RETRY);
		assertEquals(StoreStateView.catalogBroken().heading("de_de"), "Katalog konnte nicht gelesen werden");
	}

	@Test
	public void packageBrokenCarriesTheNameAndOffersTheDetailsButton() {
		StoreStateView view = StoreStateView.packageBroken("Neon HUD");
		assertEquals(view.state(), StoreState.PACKAGE_BROKEN);
		assertTrue(view.heading("de_de").contains("Neon HUD"));
		assertTrue(view.heading("en_us").contains("Neon HUD"));
		assertEquals(view.action().orElseThrow(), StoreStateActionId.SHOW_PACKAGE_DETAILS);
		assertEquals(view.action().orElseThrow().describe("de_de"), "Details");
	}

	@Test
	public void loadingIsASkeletonWithPlaceholderTilesAndNoButton() {
		StoreStateView view = StoreStateView.loading();
		assertTrue(view.isSkeleton());
		assertEquals(view.symbol(), StateSymbol.SKELETON);
		assertFalse(view.hasAction());
		assertFalse(view.showsSpinner());
		assertEquals(view.skeletonTileCount(), StoreStateView.DEFAULT_SKELETON_TILE_COUNT);
		assertEquals(StoreStateView.loading(3).skeletonTileCount(), 3);
	}

	@Test
	public void loadingRejectsANonPositiveTileCount() {
		assertThrows(IllegalArgumentException.class, () -> StoreStateView.loading(0));
		assertThrows(IllegalArgumentException.class, () -> StoreStateView.loading(-2));
	}

	@Test
	public void nonSkeletonStatesReportZeroSkeletonTiles() {
		for (StoreStateView view : allStates()) {
			if (!view.isSkeleton()) {
				assertEquals(view.skeletonTileCount(), 0, view.state() + " ist kein Skeleton");
			}
		}
	}
}
