package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.IndexPage;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.state.CatalogAvailability;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J1 -- Fertig-Kriterium "Ohne Opt-in taucht kein Jar auf" fuer die
 * <b>Katalogreihe</b>. Der Katalog liefert eine gemischte Seite (Klasse A +
 * ein Klasse-B-Paket, {@code javaMod == true}); der {@link StoreCatalogController}
 * spiegelt das ohne Opt-in-Schalter ohne jedes Jar in seine Kacheln, mit
 * Schalter erscheint das Jar und traegt das "Neustart noetig"-Zeichen.
 */
public final class StoreCatalogControllerJavaModOfferTest {

	@BeforeMethod
	public void setUp() {
		CatalogAvailability.reset();
	}

	@AfterMethod
	public void tearDown() {
		CatalogAvailability.reset();
	}

	private static IndexPage mixedPage() {
		List<IndexPackage> packages = List.of(
				new IndexPackage("kat.thema", "Thema", "autorin", "theme", "1.0.0", null, null,
						"2026-09-01T00:00:00Z", false),
				new IndexPackage("kat.hud", "HUD", "autorin", "hud", "1.0.0", null, null,
						"2026-09-01T00:00:00Z", false),
				new IndexPackage("kat.jarmod", "Jar-Mod", "autorin", "hud", "2.0.0", null, null,
						"2026-09-01T00:00:00Z", true));
		return new IndexPage(1, "2026-09-10T00:00:00Z", 1, 50, 1, packages.size(), null, null, packages);
	}

	private static StoreCatalogController controllerWith(boolean optIn) {
		IndexSource source = pageNumber -> CatalogResult.online(mixedPage());
		CatalogCache emptyCache = () -> Optional.empty();
		return new StoreCatalogController(source, emptyCache, new JavaModOfferGate(() -> optIn));
	}

	@Test
	public void withoutOptInNoJarTileAppearsInTheCatalogRow() {
		StoreCatalogController controller = controllerWith(false);

		controller.refresh();

		List<TileData> tiles = controller.catalogTiles();
		assertEquals(tiles.size(), 2, "nur die beiden Klasse-A-Pakete werden zu Kacheln");
		assertTrue(tiles.stream().noneMatch(TileData::javaMod), "kein Jar in der Katalogreihe");
		assertFalse(tiles.stream().anyMatch(t -> t.id().equals("kat.jarmod")));
	}

	@Test
	public void withOptInTheJarTileAppearsMarkedRestartRequired() {
		StoreCatalogController controller = controllerWith(true);

		controller.refresh();

		List<TileData> tiles = controller.catalogTiles();
		assertEquals(tiles.size(), 3);
		TileData jar = tiles.stream().filter(TileData::javaMod).findFirst().orElseThrow();
		assertEquals(jar.id(), "kat.jarmod");
		assertEquals(jar.status(), TileStatus.RESTART_REQUIRED,
				"ein sichtbares Jar-Angebot ist ehrlich als 'Neustart noetig' beschriftet");
	}
}
