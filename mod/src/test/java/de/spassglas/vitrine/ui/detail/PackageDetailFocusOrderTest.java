package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * ROADMAP S3, Punkt 3: "Tastatur/Fokus nach {@code StoreFocusOrder}-Muster."
 * Prueft, dass die Tab-Reihenfolge der Detailseite in Leserichtung steht und
 * nur tatsaechlich bedienbare Elemente enthaelt.
 */
public final class PackageDetailFocusOrderTest {

	@Test
	public void minimalPackageHasOnlyBackAndActionButton() {
		Manifest minimal = new Manifest(1, "solo.pack", "Solo", "1.0.0", "texturen", "solo",
				null, List.of(), null, List.of(), null, null);

		List<PackageDetailFocusTarget> order =
				PackageDetailFocusOrder.compute(PackageDetailModel.of(minimal, TileStatus.NONE));

		assertEquals(order, List.of(
				PackageDetailFocusTarget.BACK,
				PackageDetailFocusTarget.ACTION_BUTTON));
	}

	@Test
	public void fullPackageAddsGalleryToggleChangelogAndAuthorRowInReadingOrder() {
		Manifest full = new Manifest(1, "mira.aurora-hud", "Aurora HUD", "2.1.0", "hud", "mira",
				"l1\nl2\nl3\nl4\nl5\nl6\nl7", List.of("hud"), "icon.png",
				List.of("a.png"), "https://example.invalid/x", "MIT");
		List<TileData> catalog = List.of(
				TileData.withoutImage("mira.aurora-hud", "Aurora HUD", "mira", TilePackageType.HUD, TileStatus.NONE),
				TileData.withoutImage("mira.compass", "Compass", "mira", TilePackageType.HUD, TileStatus.NONE));

		PackageDetailModel model = PackageDetailModel.of(full, TileStatus.NONE,
				PackageInstallPhase.IDLE, 2048L, "1.0.0 first", catalog);

		assertEquals(PackageDetailFocusOrder.compute(model), List.of(
				PackageDetailFocusTarget.BACK,
				PackageDetailFocusTarget.ACTION_BUTTON,
				PackageDetailFocusTarget.SCREENSHOT_GALLERY,
				PackageDetailFocusTarget.DESCRIPTION_TOGGLE,
				PackageDetailFocusTarget.CHANGELOG_TOGGLE,
				PackageDetailFocusTarget.MORE_FROM_AUTHOR));
	}

	@Test
	public void knownChecksumAddsCopyChecksumBeforeChangelogToggle() {
		Manifest full = new Manifest(1, "mira.aurora-hud", "Aurora HUD", "2.1.0", "hud", "mira",
				"l1\nl2\nl3\nl4\nl5\nl6\nl7", List.of("hud"), "icon.png",
				List.of("a.png"), "https://example.invalid/x", "MIT");
		List<TileData> catalog = List.of(
				TileData.withoutImage("mira.aurora-hud", "Aurora HUD", "mira", TilePackageType.HUD, TileStatus.NONE));

		PackageDetailModel model = PackageDetailModel.of(full, TileStatus.NONE,
				PackageInstallPhase.IDLE, 2048L, "1.0.0 first", catalog, null, "b".repeat(64), List.of(), List.of());

		assertEquals(PackageDetailFocusOrder.compute(model), List.of(
				PackageDetailFocusTarget.BACK,
				PackageDetailFocusTarget.ACTION_BUTTON,
				PackageDetailFocusTarget.SCREENSHOT_GALLERY,
				PackageDetailFocusTarget.DESCRIPTION_TOGGLE,
				PackageDetailFocusTarget.COPY_CHECKSUM,
				PackageDetailFocusTarget.CHANGELOG_TOGGLE));
	}
}
