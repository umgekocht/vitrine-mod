package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFW;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE74-Fertig-Kriterium (docs/UI-SPEC.md:112-114): Rechtsklick auf
 * eine Kachel oeffnet ein kleines Menue mit genau den vier erwarteten
 * Eintraegen in der vorgegebenen Reihenfolge, und ein Klick auf einen Eintrag
 * loest die passende Wirkung aus. {@link TileRowWidget} laesst sich -- wie
 * {@code TileRowWidgetSeeAllTest} -- ohne Rendering direkt anklicken:
 * {@code mouseClicked} braucht keinen {@code TextRenderer}.
 */
public final class TileRowWidgetContextMenuTest {

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	private static TileData tile(String id, TileStatus status) {
		return TileData.withoutImage(id, "Kachel " + id, "Autorin", TilePackageType.THEME, status);
	}

	/** Ruhe-Lage der ersten Kachel -- dieselbe wie in jedem anderen Trefferflaechen-Test dieser Klasse. */
	private static double firstTileCenterX() {
		return VitrineTheme.CARD_WIDTH / 2.0;
	}

	private static double firstTileCenterY() {
		int cardTop = VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP
				+ VitrineTheme.ELEVATION_HOVER - VitrineTheme.ELEVATION_REST;
		return cardTop + VitrineTheme.CARD_WIDTH * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH / 2.0;
	}

	@Test
	public void rightClickOnATileOpensTheMenuWithExactlyTheFourExpectedEntriesInOrder() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.NONE)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		boolean handled = row.mouseClicked(firstTileCenterX(), firstTileCenterY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);

		assertTrue(handled, "ein Rechtsklick auf eine Kachel muss behandelt werden");
		assertEquals(row.openContextMenuTile().id(), "a");
		List<TileContextMenuEntry> entries = row.openContextMenuEntries();
		assertEquals(entries.size(), 4);
		assertEquals(entries.get(0).action(), TileContextMenuAction.INSTALL);
		assertEquals(entries.get(1).action(), TileContextMenuAction.DETAILS);
		assertEquals(entries.get(2).action(), TileContextMenuAction.AUTHOR);
		assertEquals(entries.get(3).action(), TileContextMenuAction.REPORT);
	}

	@Test
	public void rightClickAwayFromAnyTileOpensNoMenu() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.NONE)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		boolean handled = row.mouseClicked(5, 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

		assertEquals(handled, false, "ein Rechtsklick abseits jeder Kachel darf nichts oeffnen");
		assertNull(row.openContextMenuTile());
	}

	@Test
	public void clickingTheInstallEntryOnAnUninstalledTileInvokesTheSameHolenCallback() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.NONE)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		AtomicReference<TileData> downloaded = new AtomicReference<>();
		row.setOnTileDownload(downloaded::set);

		row.mouseClicked(firstTileCenterX(), firstTileCenterY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		// Erster Eintrag ("Installieren"), oben im Menue direkt unterhalb des
		// Rechtsklick-Punkts (siehe TileRowWidget#openContextMenu/#renderContextMenu).
		boolean handled = row.mouseClicked(firstTileCenterX(), firstTileCenterY() + VitrineTheme.EDGE_PADDING
				+ VitrineTheme.MENU_BUTTON_HEIGHT / 2.0, 0);

		assertTrue(handled, "ein Klick auf einen Menuepunkt muss behandelt werden");
		assertEquals(downloaded.get().id(), "a", "\"Installieren\" muss denselben Rueckruf wie der \"Holen\"-Knopf ausloesen");
		assertNull(row.openContextMenuTile(), "nach der Auswahl muss das Menue wieder geschlossen sein");
	}

	@Test
	public void clickingTheDetailsEntryOpensTheDetailPage() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.INSTALLED)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		AtomicReference<TileData> activated = new AtomicReference<>();
		row.setOnTileActivated(activated::set);

		row.mouseClicked(firstTileCenterX(), firstTileCenterY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		// Zweiter Eintrag ("Details").
		row.mouseClicked(firstTileCenterX(),
				firstTileCenterY() + VitrineTheme.EDGE_PADDING + VitrineTheme.MENU_BUTTON_HEIGHT * 1.5, 0);

		assertEquals(activated.get().id(), "a", "\"Details\" muss die Detailseite oeffnen, wie ein normaler Klick");
	}

	@Test
	public void clickingAnywhereOutsideAnOpenMenuJustClosesIt() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.NONE), tile("b", TileStatus.NONE)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		AtomicReference<TileData> activated = new AtomicReference<>();
		row.setOnTileActivated(activated::set);

		row.mouseClicked(firstTileCenterX(), firstTileCenterY(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		assertTrue(row.openContextMenuTile() != null);

		// Weit entfernt vom Menue selbst, aber immer noch im Widget.
		row.mouseClicked(1, 1, 0);

		assertNull(row.openContextMenuTile(), "ein Klick ausserhalb des Menues muss es schliessen");
		assertNull(activated.get(), "das Schliessen allein darf keine Kachel aktivieren");
	}
}
