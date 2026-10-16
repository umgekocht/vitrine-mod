package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J1 -- Fertig-Kriterium "Ohne Opt-in taucht kein Jar auf" fuer die
 * <b>Suchergebnisse</b> und das Vorschlagsfeld. Der Katalog der Suche enthaelt
 * ein Klasse-B-Angebot ({@code javaMod == true}); ohne den Opt-in-Schalter
 * reicht {@link StoreSearchController} es weder in {@code results()} noch in
 * die Live-Treffer der {@code suggestions()}, mit Schalter schon.
 */
public final class StoreSearchControllerJavaModOfferTest {

	private static List<TileData> catalog() {
		return List.of(
				TileData.withoutImage("th1", "Klarglas", "spassglas", TilePackageType.THEME, TileStatus.NONE),
				TileData.withoutImage("hd1", "Aurora HUD", "mira", TilePackageType.HUD, TileStatus.NONE),
				TileData.javaModOffer("jr1", "Aurora Tweaks", "dev", TilePackageType.HUD,
						TileStatus.RESTART_REQUIRED));
	}

	private static List<String> ids(List<TileData> tiles) {
		return tiles.stream().map(TileData::id).collect(Collectors.toList());
	}

	private static StoreSearchController controller(boolean optIn) {
		return new StoreSearchController(catalog(), new JavaModOfferGate(() -> optIn));
	}

	@Test
	public void withoutOptInSearchResultsContainNoJarOffer() {
		StoreSearchController c = controller(false);

		c.submit("aurora");

		assertEquals(ids(c.results()), List.of("hd1"), "das Jar-Angebot 'jr1' faellt heraus");
		assertTrue(c.results().stream().noneMatch(TileData::javaMod));
	}

	@Test
	public void withoutOptInLiveSuggestionsContainNoJarOffer() {
		StoreSearchController c = controller(false);

		c.setLiveText("aurora");

		assertEquals(ids(c.suggestions().liveMatches()), List.of("hd1"));
		assertTrue(c.suggestions().liveMatches().stream().noneMatch(TileData::javaMod));
	}

	@Test
	public void defaultConstructorHidesJarOffersToo() {
		StoreSearchController c = new StoreSearchController(catalog());

		c.submit("aurora");

		assertTrue(c.results().stream().noneMatch(TileData::javaMod),
				"ohne eigene Schleuse gilt der sichere Default: kein Jar");
	}

	@Test
	public void withOptInJarOfferShowsInResultsAndSuggestions() {
		StoreSearchController c = controller(true);

		c.submit("aurora");
		assertEquals(ids(c.results()), List.of("hd1", "jr1"));

		StoreSearchController live = controller(true);
		live.setLiveText("aurora");
		assertTrue(ids(live.suggestions().liveMatches()).contains("jr1"));
		assertTrue(live.suggestions().liveMatches().stream()
				.filter(TileData::javaMod)
				.allMatch(t -> t.status() == TileStatus.RESTART_REQUIRED));
	}
}
