package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE87-Beweis: das "Neustart noetig"-Statuszeichen
 * (docs/UI-SPEC.md "4. Kachel") trug vorher kein Tooltip -- ein Icon ohne
 * Klartext-Erklaerung. {@link TileRowWidget#restartTooltipAt} ist die reine
 * Geometrie/Text-Entscheidung hinter {@code renderWidget}s {@code
 * setTooltip(...)}-Aufruf (kein {@code DrawContext} noetig, Muster wie
 * {@code TileRowWidgetSeeAllTest}).
 */
public final class TileRowWidgetRestartTooltipTest {

	@BeforeClass
	public void bootstrapGameData() {
		SharedConstants.createGameVersion();
	}

	private static TileData tile(String id, TileStatus status) {
		// TileData erlaubt RESTART_REQUIRED nur fuer Klasse-B-Angebote
		// (javaMod=true, siehe TileData#TileData/Charter-Regel 2) --
		// withoutImage() setzt javaMod stets auf false und wuerde dafuer
		// eine IllegalArgumentException werfen.
		if (status == TileStatus.RESTART_REQUIRED) {
			return TileData.javaModOffer(id, "Kachel " + id, "autorin", TilePackageType.THEME, status);
		}
		return TileData.withoutImage(id, "Kachel " + id, "autorin", TilePackageType.THEME, status);
	}

	@Test
	public void hoveringTheRestartRequiredIconYieldsATooltipText() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.RESTART_REQUIRED)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		// Mittig im Statuszeichen-Icon unten rechts im Bildbereich der ersten
		// Kachel -- dieselbe Ruhe-Lage (baseCardTop() - ELEVATION_REST), die
		// auch jeder andere Trefferflaechen-Test dieser Klasse benutzt
		// (TileRowWidget#restartTooltipAt, #onGet, #tileAt).
		int cardTop = VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP
				+ VitrineTheme.ELEVATION_HOVER - VitrineTheme.ELEVATION_REST;
		int imageBottom = cardTop + VitrineTheme.CARD_WIDTH * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH;
		int size = VitrineTheme.STATUS_ICON_DRAW_SIZE;
		double x = VitrineTheme.CARD_WIDTH - VitrineTheme.CARD_BADGE_INSET - size / 2.0;
		double y = imageBottom - VitrineTheme.CARD_BADGE_INSET - size / 2.0;

		Optional<Text> tooltip = row.restartTooltipAt(x, y);

		assertTrue(tooltip.isPresent(), "ein Hover ueber das Icon muss einen Tooltip liefern");
		assertEquals(tooltip.get().getString(), Text.translatable("vitrine.tile.status.restart_required.tooltip").getString());
	}

	@Test
	public void theTooltipTextIsNotBlankInBothLocales() {
		for (String locale : List.of("de_de", "en_us")) {
			String text = Translations.get(locale, "vitrine.tile.status.restart_required.tooltip");
			assertFalse(text.isBlank(), locale + ": der Tooltip-Text darf nicht leer sein");
			assertFalse(text.equals("vitrine.tile.status.restart_required.tooltip"),
					locale + ": der Uebersetzungs-Key muss aufgeloest sein");
		}
	}

	@Test
	public void hoveringAwayFromTheIconYieldsNoTooltip() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.RESTART_REQUIRED)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		Optional<Text> tooltip = row.restartTooltipAt(5, 5);

		assertTrue(tooltip.isEmpty(), "weit weg vom Icon darf kein Tooltip erscheinen");
	}

	@Test
	public void aTileWithoutRestartRequiredNeverYieldsThisTooltip() {
		TileRowWidget row = new TileRowWidget(0, 0, 900,
				List.of(tile("a", TileStatus.INSTALLED)), null,
				"vitrine.row.library.title", "vitrine.row.library.subtitle");

		int cardTop = VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP
				+ VitrineTheme.ELEVATION_HOVER - VitrineTheme.ELEVATION_REST;
		int imageBottom = cardTop + VitrineTheme.CARD_WIDTH * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH;
		int size = VitrineTheme.STATUS_ICON_DRAW_SIZE;
		double x = VitrineTheme.CARD_WIDTH - VitrineTheme.CARD_BADGE_INSET - size / 2.0;
		double y = imageBottom - VitrineTheme.CARD_BADGE_INSET - size / 2.0;

		assertTrue(row.restartTooltipAt(x, y).isEmpty(),
				"ein INSTALLED-Status darf nicht den 'Neustart noetig'-Tooltip zeigen");
	}
}
