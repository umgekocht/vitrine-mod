package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE74-Fertig-Kriterium (docs/UI-SPEC.md:112-114): "Rechtsklick
 * oeffnet ein kleines Menue (Installieren, Details, Autor, Melden)." Reines
 * Datenmodell, ohne laufenden Client beweisbar -- {@link TileRowWidget}
 * bindet daran nur noch Minecrafts Eingabe-/Zeichen-Code (siehe
 * {@code TileRowWidgetContextMenuTest}).
 */
public final class TileContextMenuTest {

	private static TileData tile(String id, TileStatus status) {
		if (status == TileStatus.RESTART_REQUIRED) {
			return TileData.javaModOffer(id, "Kachel " + id, "Autorin Beispiel", TilePackageType.THEME, status);
		}
		return TileData.withoutImage(id, "Kachel " + id, "Autorin Beispiel", TilePackageType.THEME, status);
	}

	@Test
	public void entriesComeInExactlyTheFourSpecActionsInSpecOrder() {
		List<TileContextMenuEntry> entries = TileContextMenu.entriesFor(tile("a", TileStatus.NONE));

		assertEquals(entries.size(), 4, "das Menue hat genau vier Eintraege, nicht mehr und nicht weniger");
		assertEquals(entries.get(0).action(), TileContextMenuAction.INSTALL);
		assertEquals(entries.get(1).action(), TileContextMenuAction.DETAILS);
		assertEquals(entries.get(2).action(), TileContextMenuAction.AUTHOR);
		assertEquals(entries.get(3).action(), TileContextMenuAction.REPORT);
	}

	@Test
	public void installEntryLabelFollowsTheExistingTileStatusLogic() {
		assertEquals(TileContextMenu.entriesFor(tile("a", TileStatus.NONE)).get(0).translationKey(),
				"vitrine.action.install");
		assertEquals(TileContextMenu.entriesFor(tile("a", TileStatus.UPDATE_AVAILABLE)).get(0).translationKey(),
				"vitrine.action.update");
		assertEquals(TileContextMenu.entriesFor(tile("a", TileStatus.INSTALLED)).get(0).translationKey(),
				"vitrine.action.uninstall");
		assertEquals(TileContextMenu.entriesFor(tile("a", TileStatus.RESTART_REQUIRED)).get(0).translationKey(),
				"vitrine.action.uninstall");
	}

	@Test
	public void authorEntryCarriesTheTilesOwnAuthorNameAsArgument() {
		TileContextMenuEntry author = TileContextMenu.entriesFor(tile("a", TileStatus.NONE)).get(2);

		assertEquals(author.translationKey(), "vitrine.detail.author_label");
		assertTrue(author.argument().isPresent(), "der Autor-Eintrag braucht den Autorennamen als Platzhalter-Argument");
		assertEquals(author.argument().get(), "Autorin Beispiel");
	}

	@Test
	public void detailsAndReportEntriesCarryNoPlaceholderArgument() {
		List<TileContextMenuEntry> entries = TileContextMenu.entriesFor(tile("a", TileStatus.NONE));

		assertFalse(entries.get(1).argument().isPresent(), "\"Details\" braucht keinen Platzhalter");
		assertFalse(entries.get(3).argument().isPresent(), "\"Melden\" braucht keinen Platzhalter");
	}

	@Test
	public void everyEntryLabelResolvesToANonBlankTextInBothLocales() {
		for (String locale : List.of("en_us", "de_de")) {
			for (TileStatus status : TileStatus.values()) {
				TileData t = tile("a", status);
				for (TileContextMenuEntry entry : TileContextMenu.entriesFor(t)) {
					String resolved = entry.argument().isPresent()
							? Translations.format(locale, entry.translationKey(), entry.argument().get())
							: Translations.get(locale, entry.translationKey());
					assertFalse(resolved.isBlank(), locale + ": Eintrag '" + entry.translationKey() + "' liefert leeren Text");
					assertFalse(resolved.equals(entry.translationKey()),
							locale + ": Uebersetzungs-Key '" + entry.translationKey() + "' ist nicht aufgeloest");
				}
			}
		}
	}
}
