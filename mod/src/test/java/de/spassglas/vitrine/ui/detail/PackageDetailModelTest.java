package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.catalog.github.GitHubRepoStats;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP S3 Fertig-Nachweis:
 * <ul>
 *   <li>ein Manifest mit allen optionalen Feldern -&gt; {@link PackageDetailModel}
 *       enthaelt jeden Spec-Abschnitt in der richtigen Reihenfolge, die
 *       Kapabilitaets-Zeilen stimmen mit den {@link VpkgCapability}-Beschreibungen
 *       ueberein, der Aktionsknopf-Zustand wird aus {@link TileStatus} abgeleitet;</li>
 *   <li>Minimalmanifest (nur Pflichtfelder) -&gt; Leerzustaende greifen, kein NPE.</li>
 * </ul>
 */
public final class PackageDetailModelTest {

	private static final List<PackageDetailSection> SPEC_ORDER = List.of(
			PackageDetailSection.HEADER,
			PackageDetailSection.ACTION_BUTTON,
			PackageDetailSection.SCREENSHOTS,
			PackageDetailSection.DESCRIPTION,
			PackageDetailSection.CAPABILITIES,
			PackageDetailSection.DETAILS,
			PackageDetailSection.CHANGELOG,
			PackageDetailSection.MORE_FROM_AUTHOR);

	private static Manifest fullManifest() {
		return new Manifest(1, "mira.aurora-hud", "Aurora HUD", "2.1.0", "hud", "mira",
				"Zeile eins\nZeile zwei\nZeile drei\nZeile vier\nZeile fuenf\nZeile sechs\nZeile sieben",
				List.of("hud", "texturen"), "icon.png",
				List.of("shots/eins.png", "shots/zwei.png"),
				"https://example.invalid/mira/aurora-hud", "CC-BY-4.0");
	}

	private static Manifest minimalManifest() {
		return new Manifest(1, "solo.pack", "Solo", "1.0.0", "texturen", "solo",
				null, List.of(), null, List.of(), null, null);
	}

	private static List<TileData> catalog() {
		return List.of(
				TileData.withoutImage("mira.aurora-hud", "Aurora HUD", "mira", TilePackageType.HUD, TileStatus.INSTALLED),
				TileData.withoutImage("mira.compass-hud", "Compass HUD", "mira", TilePackageType.HUD, TileStatus.NONE),
				TileData.withoutImage("wolf.stone", "Stone", "wolf", TilePackageType.TEXTUREN, TileStatus.NONE));
	}

	@Test
	public void fullManifestExposesEverySectionInSpecOrder() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, "2.1.0 - Feinschliff\n2.0.0 - Neues HUD", catalog());

		assertEquals(model.sections(), SPEC_ORDER, "Abschnitte in exakt der Spec-Reihenfolge");
	}

	@Test
	public void fullManifestHeaderAndActionButton() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog());

		PackageDetailHeader header = model.header();
		assertEquals(header.name(), "Aurora HUD");
		assertEquals(header.author(), "mira");
		assertEquals(header.type(), TilePackageType.HUD);
		assertEquals(header.status(), TileStatus.INSTALLED);
		assertTrue(header.hasIcon());
		assertEquals(header.iconPath().orElseThrow(), "icon.png");

		assertEquals(model.actionState(), PackageActionState.INSTALLED,
				"INSTALLED + IDLE -> Knopf 'Installiert'");

		// GATE70: Version/Lizenz/Groesse sind schon beim Bauen des Modells
		// bekannt (aus dem Manifest bzw. der uebergebenen Dateigroesse);
		// MC-Versionen/GitHub-Kennzahlen bleiben ohne weitere Angabe ehrlich leer.
		assertEquals(header.version(), "2.1.0");
		assertEquals(header.license().orElseThrow(), "CC-BY-4.0");
		assertEquals(header.size().orElseThrow(), "1.5 MB");
		assertTrue(header.minecraftVersions().isEmpty(), "kein Aufrufer in diesem Test kennt Minecraft-Versionen");
		assertEquals(header.repoStats(), GitHubRepoStats.UNKNOWN);
		assertTrue(model.repositoryUrl().isEmpty(), "kein Aufrufer in diesem Test kennt ein Autoren-Repo");
	}

	@Test
	public void headerShowsMinecraftVersionsAndRepositoryUrlWhenKnown() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog(), null, null, List.of(), List.of(),
				"https://github.com/mira/aurora-hud", List.of("1.21.1", "1.21.4"));

		assertEquals(model.repositoryUrl().orElseThrow(), "https://github.com/mira/aurora-hud");
		assertEquals(model.header().minecraftVersions().orElseThrow(), "1.21.1, 1.21.4");
		// GATE70: der GitHub-Abruf selbst ist Sache der Detailseite (asynchron) --
		// bis dahin bleibt der Kopf ehrlich bei UNKNOWN, auch mit bekanntem Repo.
		assertEquals(model.header().repoStats(), GitHubRepoStats.UNKNOWN);
	}

	@Test
	public void withRepoStatsReplacesOnlyTheGitHubFieldsOfTheHeader() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog());
		GitHubRepoStats fetched = new GitHubRepoStats(java.util.Optional.of(42L), java.util.Optional.of(7L));

		PackageDetailModel updated = model.withRepoStats(fetched);

		assertEquals(updated.header().repoStats(), fetched);
		assertEquals(updated.header().repoStats().stargazerCount().orElseThrow(), Long.valueOf(42));
		assertEquals(updated.header().repoStats().downloadCount().orElseThrow(), Long.valueOf(7));
		// unveraendert gegenueber dem Ausgangsmodell:
		assertEquals(updated.header().name(), model.header().name());
		assertEquals(updated.header().version(), model.header().version());
		assertEquals(model.header().repoStats(), GitHubRepoStats.UNKNOWN,
				"das Ausgangsmodell bleibt unveraendert (keine Mutation)");
	}

	@Test
	public void fullManifestScreenshotsDescriptionAndCapabilityLines() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog());

		assertTrue(model.hasScreenshots());
		assertEquals(model.screenshotPaths(), List.of("shots/eins.png", "shots/zwei.png"));

		assertTrue(model.hasDescription());
		assertTrue(model.descriptionNeedsCollapse(), "7 Zeilen > 6 -> 'Mehr anzeigen'");

		assertEquals(model.capabilities(), List.of(VpkgCapability.HUD, VpkgCapability.TEXTUREN),
				"Faehigkeiten in Manifest-Reihenfolge");
		assertFalse(model.declaresNoCapabilities());
		assertTrue(model.capabilitiesKnown(), "ein echtes Manifest kennt seine Faehigkeiten sicher (GATE76)");
		assertTrue(model.runsNoProgramCode(), "Klasse A: 'Führt keinen Programmcode aus.'");

		// Die Klartextzeilen sind exakt die VpkgCapability-Beschreibungen.
		assertEquals(model.capabilities().get(0).describe("de_de"),
				Translations.get("de_de", "vitrine.capability.hud"));
		assertEquals(model.capabilities().get(1).describe("de_de"),
				Translations.get("de_de", "vitrine.capability.texturen"));
	}

	@Test
	public void fullManifestDetailsFactsInOrder() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog(),
				"2024-03-01T00:00:00Z", "a".repeat(64), List.of("wolf.stone"), List.of("wolf.conflict"));

		// GATE69: alle sieben UI-SPEC-Felder (docs/UI-SPEC.md:134-135), in dieser
		// Reihenfolge -- "Homepage" stand nicht in dieser Liste und ist entfernt.
		List<PackageDetailFact> details = model.details();
		assertEquals(details.size(), 7);
		assertEquals(details.get(0).labelKey(), "vitrine.detail.field.version");
		assertEquals(details.get(0).value().orElseThrow(), "2.1.0");
		assertEquals(details.get(1).labelKey(), "vitrine.detail.field.size");
		assertEquals(details.get(1).value().orElseThrow(), "1.5 MB");
		assertEquals(details.get(2).labelKey(), "vitrine.detail.field.published_at");
		assertEquals(details.get(2).value().orElseThrow(), "2024-03-01T00:00:00Z");
		assertEquals(details.get(3).labelKey(), "vitrine.detail.field.license");
		assertEquals(details.get(3).value().orElseThrow(), "CC-BY-4.0");
		assertEquals(details.get(4).labelKey(), "vitrine.detail.field.dependencies");
		assertEquals(details.get(4).value().orElseThrow(), "wolf.stone");
		assertEquals(details.get(5).labelKey(), "vitrine.detail.field.conflicts");
		assertEquals(details.get(5).value().orElseThrow(), "wolf.conflict");
		assertEquals(details.get(6).labelKey(), "vitrine.detail.field.checksum");
		assertEquals(details.get(6).value().orElseThrow(), "a".repeat(64));

		assertFalse(details.get(0).copyable(), "nur die Pruefsumme bekommt den Kopier-Knopf");
		assertTrue(details.get(6).copyable(), "Pruefsumme ist kopierbar (docs/UI-SPEC.md)");
		assertTrue(model.hasCopyableChecksum());
	}

	@Test
	public void hasCopyableChecksumIsFalseWithoutAKnownChecksum() {
		PackageDetailModel withoutChecksum = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog());

		assertFalse(withoutChecksum.hasCopyableChecksum(),
				"ohne bekannte Pruefsumme darf kein Kopier-Knopf erscheinen");
	}

	@Test
	public void fullManifestChangelogAndMoreFromAuthor() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, "2.1.0 - Feinschliff", catalog());

		assertTrue(model.hasChangelog());
		assertEquals(model.changelog().orElseThrow(), "2.1.0 - Feinschliff");

		assertTrue(model.hasMoreFromAuthor());
		List<String> ids = model.moreFromAuthor().stream().map(TileData::id).toList();
		assertEquals(ids, List.of("mira.compass-hud"),
				"nur andere Pakete desselben Autors, ohne das Paket selbst");
	}

	/**
	 * GATE76: eine Quelle ohne Capability-Daten (z. B. {@code
	 * CatalogPackageDetailSource}, deren Registry-Schema das Feld nicht
	 * fuehrt) uebergibt {@code capabilitiesKnown=false} -- auch mit leerer
	 * Liste darf das Modell dann NICHT "hat wirklich keine Faehigkeiten"
	 * melden, sonst zeigt die Seite die falsche Verneinung aus der
	 * ROADMAP-Zeile.
	 */
	@Test
	public void unknownCapabilitiesAreNeverReportedAsDeclaredNone() {
		PackageDetailModel model = PackageDetailModel.of(fullManifest(), TileStatus.NONE,
				PackageInstallPhase.IDLE, -1L, null, List.of(), null, null, List.of(), List.of(), null,
				List.of(), false);

		assertFalse(model.capabilitiesKnown(), "Katalog-Quelle ohne Capability-Daten kennt sie nicht");
		assertFalse(model.declaresNoCapabilities(),
				"unbekannt darf nicht als 'hat wirklich keine Faehigkeiten' durchgehen");
	}

	/**
	 * GATE77: ein Klasse-B-Angebot ({@code isJavaMod=true}) fuehrt
	 * tatsaechlich Programmcode aus -- {@code runsNoProgramCode()} muss
	 * {@code false} sein, sonst zeigt die Detailseite die falsche Verneinung
	 * neben der "Neustart nötig"-Warnung (Charter-Regel 2).
	 */
	@Test
	public void javaModOfferDoesNotClaimToRunNoProgramCode() {
		PackageDetailModel classB = PackageDetailModel.of(fullManifest(), TileStatus.RESTART_REQUIRED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog(), null, null, List.of(), List.of(), null,
				List.of(), true, true);

		assertFalse(classB.runsNoProgramCode(), "Klasse B (.jar) fuehrt Programmcode aus");
	}

	/** GATE77: der kuerzere Overload ohne {@code isJavaMod} bleibt beim konservativen Default Klasse A. */
	@Test
	public void defaultOverloadsStayClassAWithoutAnExplicitJavaModFlag() {
		PackageDetailModel classA = PackageDetailModel.of(fullManifest(), TileStatus.INSTALLED,
				PackageInstallPhase.IDLE, 1_500_000L, null, catalog());

		assertTrue(classA.runsNoProgramCode(), "ohne explizite Angabe bleibt ein Angebot Klasse A");
	}

	@Test
	public void actionStateReflectsRunningAndFailedVorgang() {
		assertEquals(PackageDetailModel.of(fullManifest(), TileStatus.NONE, PackageInstallPhase.WORKING,
				-1L, null, List.of()).actionState(), PackageActionState.WORKING);
		assertEquals(PackageDetailModel.of(fullManifest(), TileStatus.NONE, PackageInstallPhase.FAILED,
				-1L, null, List.of()).actionState(), PackageActionState.ERROR);
	}

	@Test
	public void minimalManifestEmptyStatesApplyWithoutNpe() {
		PackageDetailModel model = PackageDetailModel.of(minimalManifest(), TileStatus.NONE);

		assertEquals(model.sections(), SPEC_ORDER, "auch leer: alle Abschnitte, gleiche Reihenfolge");
		assertEquals(model.actionState(), PackageActionState.INSTALL);

		assertFalse(model.hasScreenshots());
		assertTrue(model.screenshotPaths().isEmpty());

		assertFalse(model.hasDescription());
		assertFalse(model.descriptionNeedsCollapse());

		assertTrue(model.declaresNoCapabilities());
		assertTrue(model.capabilities().isEmpty());
		// GATE76: ein echtes (wenn auch leeres) Manifest -- die Seite darf "hat
		// wirklich keine Faehigkeiten" zeigen, nicht "unbekannt".
		assertTrue(model.capabilitiesKnown());

		List<PackageDetailFact> details = model.details();
		assertEquals(details.size(), 7);
		assertEquals(details.get(0).value().orElseThrow(), "1.0.0", "Version ist Pflichtfeld");
		assertFalse(details.get(1).isKnown(), "keine Groesse -> 'keine Angaben'");
		assertFalse(details.get(2).isKnown(), "kein Veroeffentlichungsdatum -> 'keine Angaben'");
		assertFalse(details.get(3).isKnown(), "keine Lizenz -> 'keine Angaben'");
		assertFalse(details.get(4).isKnown(), "keine Abhaengigkeiten -> 'keine Angaben'");
		assertFalse(details.get(5).isKnown(), "keine Konflikte -> 'keine Angaben'");
		assertFalse(details.get(6).isKnown(), "keine Pruefsumme -> 'keine Angaben'");
		assertFalse(model.hasCopyableChecksum());

		assertFalse(model.hasChangelog());
		assertFalse(model.hasMoreFromAuthor());
		assertTrue(model.moreFromAuthor().isEmpty());

		// GATE70: ein Minimalmanifest ohne Lizenz/Groesse/Repo zeigt im Kopf
		// ehrlich "keine Angaben" statt NPE oder erfundener Werte.
		assertEquals(model.header().version(), "1.0.0");
		assertTrue(model.header().license().isEmpty());
		assertTrue(model.header().size().isEmpty());
		assertTrue(model.header().minecraftVersions().isEmpty());
		assertEquals(model.header().repoStats(), GitHubRepoStats.UNKNOWN);
		assertTrue(model.repositoryUrl().isEmpty());
	}
}
