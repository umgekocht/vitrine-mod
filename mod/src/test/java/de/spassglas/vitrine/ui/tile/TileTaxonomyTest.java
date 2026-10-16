package de.spassglas.vitrine.ui.tile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Deckt die Kachel-Taxonomie aus docs/UI-SPEC.md "4. Kachel" ab: jeder
 * Typ-Badge hat einen aufloesbaren Uebersetzungs-Key <em>und</em> ein
 * Icon-Asset; jedes sichtbare Statuszeichen hat Icon-Asset und
 * Uebersetzungs-Key (Barrierefreiheit: "Kein Zustand nur ueber Farbe"),
 * {@link TileStatus#NONE} traegt bewusst keines; und die Demo-Reihe fuer das
 * S1-Review enthaelt wirklich alle vier {@link TileStatus}-Werte.
 */
public final class TileTaxonomyTest {

	private static final Gson GSON = new Gson();
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
	}.getType();

	private static Map<String, String> lang(String locale) throws IOException {
		String resource = "/assets/vitrine/lang/" + locale + ".json";
		try (InputStream in = TileTaxonomyTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, "Sprachdatei nicht auf dem Testklassenpfad: " + resource);
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return GSON.fromJson(reader, MAP_TYPE);
			}
		}
	}

	private static boolean assetExists(String pathBelowAssets) {
		return TileTaxonomyTest.class.getResource("/assets/vitrine/" + pathBelowAssets) != null;
	}

	@Test
	public void everyPackageTypeHasResolvableKeyAndExistingIcon() throws IOException {
		Map<String, String> en = lang("en_us");
		Map<String, String> de = lang("de_de");

		for (TilePackageType type : TilePackageType.values()) {
			String key = type.translationKey();
			assertTrue(key.startsWith("vitrine.category."), "unerwarteter Key-Namensraum: " + key);
			assertTrue(en.containsKey(key) && de.containsKey(key),
					"Typ-Badge " + type + " ohne Uebersetzung in beiden Sprachen: " + key);
			assertTrue(assetExists(type.iconPath()),
					"Typ-Icon fehlt im Ressourcenbaum: " + type.iconPath());
		}
	}

	@Test
	public void packageTypeIdRoundTrips() {
		for (TilePackageType type : TilePackageType.values()) {
			assertEquals(TilePackageType.byId(type.id()).orElseThrow(), type);
		}
		assertTrue(TilePackageType.byId("gibt-es-nicht").isEmpty());
		assertTrue(TilePackageType.byId(null).isEmpty());
	}

	@Test
	public void visibleStatusesCarryIconAndTranslationNoneCarriesNeither() throws IOException {
		Map<String, String> en = lang("en_us");
		Map<String, String> de = lang("de_de");

		assertFalse(TileStatus.NONE.hasIndicator());
		assertTrue(TileStatus.NONE.iconPath().isEmpty());
		assertTrue(TileStatus.NONE.translationKey().isEmpty());

		for (TileStatus status : EnumSet.complementOf(EnumSet.of(TileStatus.NONE))) {
			assertTrue(status.hasIndicator(), status + " sollte ein Zeichen haben");
			String icon = status.iconPath().orElseThrow();
			assertTrue(assetExists(icon), status + ": Icon-Asset fehlt: " + icon);
			String key = status.translationKey().orElseThrow();
			assertTrue(en.containsKey(key) && de.containsKey(key),
					status + ": Klartext fehlt in einer Sprache: " + key);
		}
	}

	@Test
	public void statusShowcaseRowContainsAllFourStatuses() {
		List<TileData> row = DemoCatalog.statusShowcaseRow();

		Set<TileStatus> present = row.stream().map(TileData::status).collect(Collectors.toCollection(() -> EnumSet.noneOf(TileStatus.class)));
		assertEquals(present, EnumSet.allOf(TileStatus.class),
				"Die Review-Reihe muss je eine Kachel fuer alle vier Statuszeichen zeigen");
	}

	@Test
	public void everyTileInTheShowcaseRowIsFullyRenderable() throws IOException {
		Map<String, String> en = lang("en_us");
		Map<String, String> de = lang("de_de");

		for (TileData tile : DemoCatalog.statusShowcaseRow()) {
			// Typ-Badge: Icon + Klartext in beiden Sprachen.
			assertTrue(assetExists(tile.type().iconPath()), tile.id() + ": Typ-Icon fehlt");
			assertTrue(en.containsKey(tile.type().translationKey()) && de.containsKey(tile.type().translationKey()),
					tile.id() + ": Typ-Klartext fehlt");

			// Statuszeichen: sichtbare Zustaende brauchen Icon + Klartext, NONE zeichnet nichts.
			TileStatus status = tile.status();
			if (status.hasIndicator()) {
				assertTrue(assetExists(status.iconPath().orElseThrow()),
						tile.id() + ": Status-Icon fehlt fuer " + status);
				String key = status.translationKey().orElseThrow();
				assertTrue(en.containsKey(key) && de.containsKey(key),
						tile.id() + ": Status-Klartext fehlt fuer " + status);
			} else {
				assertTrue(status.iconPath().isEmpty() && status.translationKey().isEmpty());
			}
		}
	}

	@Test
	public void showcaseRowShowsAllThreeVisibleIndicatorsAtOncePlusThePlainState() {
		List<TileData> row = DemoCatalog.statusShowcaseRow();

		Set<TileStatus> withIndicator = row.stream()
				.map(TileData::status)
				.filter(TileStatus::hasIndicator)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(TileStatus.class)));
		assertEquals(withIndicator,
				EnumSet.of(TileStatus.INSTALLED, TileStatus.UPDATE_AVAILABLE, TileStatus.RESTART_REQUIRED),
				"eine einzige Reihe zeigt gleichzeitig alle drei sichtbaren Statuszeichen");

		assertTrue(row.stream().anyMatch(t -> t.status() == TileStatus.NONE),
				"und mindestens eine Kachel ohne jedes Zeichen (der vierte Zustand)");
	}

	@Test
	public void demoTilesAreWellFormed() {
		for (TileData tile : DemoCatalog.statusShowcaseRow()) {
			assertFalse(tile.id().isBlank());
			assertFalse(tile.name().isBlank());
			assertFalse(tile.author().isBlank());
			assertNotNull(tile.type());
			assertNotNull(tile.status());
			assertFalse(tile.hasImage(), "Demo-Kacheln nutzen den Platzhalter, kein eigenes Bild");
		}
	}
}
