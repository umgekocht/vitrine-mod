package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.config.SettingsModel;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J1 -- Kern des Fertig-Kriteriums "Ohne Opt-in taucht kein Jar auf",
 * auf der Ebene der Schleuse selbst: aus einer gemischten Angebotsliste
 * (Klasse A + Klasse B) entfernt {@link JavaModOfferGate} bei ausgeschaltetem
 * Opt-in jeden Klasse-B-Eintrag; bei eingeschaltetem Schalter bleiben sie und
 * tragen das "Neustart noetig"-Zeichen.
 */
public final class JavaModOfferGateTest {

	private Path tempDir;

	@AfterMethod
	public void cleanup() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private static TileData classA(String id) {
		return TileData.withoutImage(id, id, "autorin", TilePackageType.THEME, TileStatus.NONE);
	}

	private static TileData classB(String id) {
		return TileData.javaModOffer(id, id, "autorin", TilePackageType.HUD, TileStatus.RESTART_REQUIRED);
	}

	private static List<TileData> mixedOffers() {
		return List.of(classA("a1"), classB("b1"), classA("a2"), classB("b2"), classA("a3"));
	}

	@Test
	public void withoutOptInEveryJavaModOfferIsRemovedAndClassAKeptInOrder() {
		JavaModOfferGate gate = new JavaModOfferGate(() -> false);

		List<TileData> visible = gate.apply(mixedOffers());

		assertFalse(gate.showsJavaModOffers());
		assertTrue(visible.stream().noneMatch(TileData::javaMod), "kein einziges Klasse-B-Angebot bleibt uebrig");
		assertEquals(visible.stream().map(TileData::id).toList(), List.of("a1", "a2", "a3"),
				"Klasse-A-Angebote bleiben unveraendert und in Reihenfolge");
	}

	@Test
	public void withOptInJavaModOffersRemainAndCarryRestartRequiredBadge() {
		JavaModOfferGate gate = new JavaModOfferGate(() -> true);

		List<TileData> visible = gate.apply(mixedOffers());

		assertTrue(gate.showsJavaModOffers());
		assertEquals(visible.size(), 5, "mit Opt-in bleibt die Liste vollstaendig");
		List<TileData> jars = visible.stream().filter(TileData::javaMod).toList();
		assertEquals(jars.size(), 2);
		assertTrue(jars.stream().allMatch(t -> t.status() == TileStatus.RESTART_REQUIRED),
				"jedes sichtbare Jar-Angebot ist als 'Neustart noetig' gekennzeichnet");
	}

	@Test
	public void hiddenFactoryAlwaysHidesJavaModOffers() {
		List<TileData> visible = JavaModOfferGate.hidden().apply(mixedOffers());

		assertTrue(visible.stream().noneMatch(TileData::javaMod));
	}

	@Test
	public void freshSettingsDirDefaultsToHidingJavaModOffers() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-classb-gate-");
		JavaModOfferGate gate = JavaModOfferGate.forConfigDir(tempDir);

		// umgekocht 2026-10-07: Java-Mods sind ab Werk sichtbar
		assertTrue(gate.showsJavaModOffers(), "frische settings.json => Java-Mods an");
		assertTrue(gate.apply(mixedOffers()).stream().anyMatch(TileData::javaMod));
	}

	@Test
	public void forConfigDirReadsTheSwitchLiveOnEachCall() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-classb-gate-live-");
		JavaModOfferGate gate = JavaModOfferGate.forConfigDir(tempDir);
		SettingsModel.forConfigDir(tempDir).setShowJavaModOffers(false);
		assertTrue(gate.apply(mixedOffers()).stream().noneMatch(TileData::javaMod));

		SettingsModel.forConfigDir(tempDir).setShowJavaModOffers(true);

		assertTrue(gate.showsJavaModOffers(), "der umgelegte Schalter wirkt ohne neue Schleuse");
		assertEquals(gate.apply(mixedOffers()).size(), 5);
	}
}
