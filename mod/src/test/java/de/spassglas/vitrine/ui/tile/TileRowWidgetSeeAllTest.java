package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.SharedConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * ROADMAP GATE73-Fertig-Kriterium: der Link "Alle anzeigen ›" rechts im
 * Reihenkopf (docs/UI-SPEC.md "3. Start") ist anklickbar und navigiert.
 * {@link TileRowWidget} laesst sich -- wie {@code HeroCarouselWidget}
 * (siehe {@code HeroCarouselWidgetClickTest}) -- ohne Rendering direkt
 * anklicken: {@code onClick} braucht keinen {@code TextRenderer}, nur
 * {@link #renderWidget} (nie hier aufgerufen) tut das.
 */
public final class TileRowWidgetSeeAllTest {

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	private static TileData tile(String id) {
		return TileData.withoutImage(id, "Kachel " + id, "vitrine", TilePackageType.THEME, TileStatus.NONE);
	}

	@Test
	public void clickOnTheSeeAllLinkInvokesTheCallback() {
		int width = 900;
		TileRowWidget row = new TileRowWidget(0, 0, width, List.of(tile("a"), tile("b")), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		AtomicInteger calls = new AtomicInteger();
		row.setOnSeeAll(calls::incrementAndGet);

		// Trefferflaeche: rechter Rand des Reihenkopfs (siehe TileRowWidget#seeAllRect).
		double x = width - 10;
		double y = VitrineTheme.ROW_HEADER_HEIGHT / 2.0;
		row.onClick(x, y);

		assertEquals(calls.get(), 1, "ein Klick auf den Link muss den Rueckruf genau einmal ausloesen");
	}

	@Test
	public void clickOutsideTheLinkDoesNotInvokeTheCallback() {
		int width = 900;
		TileRowWidget row = new TileRowWidget(0, 0, width, List.of(tile("a"), tile("b")), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");
		AtomicInteger calls = new AtomicInteger();
		row.setOnSeeAll(calls::incrementAndGet);

		// Linker Rand des Kopfbereichs, weit weg von der rechtsbuendigen
		// Trefferflaeche des Links.
		row.onClick(5, 2);

		assertEquals(calls.get(), 0, "ein Klick ausserhalb des Links darf nicht navigieren");
	}

	@Test
	public void withoutACallbackTheLinkAreaDoesNothing() {
		int width = 900;
		TileRowWidget row = new TileRowWidget(0, 0, width, List.of(tile("a")), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		// Charter-Regel 2: ohne setOnSeeAll gibt es kein Ziel -- der Klick darf
		// nicht werfen und keine versteckte Wirkung haben.
		row.onClick(width - 10, VitrineTheme.ROW_HEADER_HEIGHT / 2.0);
	}
}
