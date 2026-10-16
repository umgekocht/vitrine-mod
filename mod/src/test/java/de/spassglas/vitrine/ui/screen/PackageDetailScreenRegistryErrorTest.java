package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.detail.PackageInstallPhase;
import de.spassglas.vitrine.ui.tile.TileStatus;
import net.minecraft.SharedConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE47-Beweis: vorher endeten ein nicht abrufbarer Registry-Eintrag
 * ({@code !result.isOnline()}) und ein {@code latestVersion} ohne passenden
 * {@code versions[]}-Eintrag in {@code PackageDetailScreen#beginCatalogInstall()}
 * beide nur in einem Log-Eintrag plus stillem {@code phase = IDLE;
 * returnToParent();} -- derselbe GATE34-Befund wie bei jedem anderen
 * Fehlerpfad der Detailseite, nur an diesen zwei Stellen nicht mitbehoben.
 * Dieser Test treibt {@link PackageDetailScreen#handleCatalogFetchResult}
 * (die aus {@code beginCatalogInstall()} ausgekoppelte, paket-sichtbare
 * Auswertung desselben Registry-Ergebnisses) direkt mit konstruierten {@link
 * CatalogResult}en an -- ohne echten Netzwerkzugriff -- und beweist, dass
 * beide Zweige jetzt {@link PackageDetailScreen#showInstallError} aufrufen,
 * also {@link PackageDetailScreen#installError()} sichtbar setzen, statt
 * stumm zu bleiben.
 */
public final class PackageDetailScreenRegistryErrorTest {

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@Test
	public void registryEntryNotOnlineProducesVisibleErrorInsteadOfSilentReturn() {
		PackageDetailScreen screen = PackageDetailScreen.forCatalog(null, minimalModel());
		assertTrue(screen.installError().isEmpty(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");

		CatalogResult<RegistryPackageEntry> offline = CatalogResult.noNetwork("kein Netz (Test)");
		screen.handleCatalogFetchResult(offline);

		assertEquals(screen.installPhase(), PackageInstallPhase.IDLE,
				"nach dem Fehlzustand darf der Knopf nicht auf 'arbeitet ...' stehen bleiben");
		assertTrue(screen.installError().isPresent(),
				"ein nicht abrufbarer Registry-Eintrag muss einen sichtbaren Fehlerzustand setzen, statt "
						+ "still zu bleiben (vorher: nur geloggt + returnToParent())");
		assertFalse(screen.installError().get().isBlank());
	}

	@Test
	public void latestVersionWithoutMatchingVersionsEntryProducesVisibleErrorInsteadOfSilentReturn() {
		PackageDetailScreen screen = PackageDetailScreen.forCatalog(null, minimalModel());
		assertTrue(screen.installError().isEmpty(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");

		RegistryPackageEntry entry = new RegistryPackageEntry(
				"solo.pack", "Solo", "texturen", "Autor", null, null, null, null, null,
				"https://example.invalid/repo",
				List.of(), // versions[]: absichtlich leer -- latestVersion passt zu keinem Eintrag
				"9.9.9", "2024-01-01T00:00:00Z", RegistryPackageEntry.CONTENT_CLASS_RESOURCE, List.of(), List.of());
		assertTrue(entry.latestVersionEntry().isEmpty(), "Testaufbau: latestVersion darf hier zu keinem Eintrag passen");

		CatalogResult<RegistryPackageEntry> online = CatalogResult.online(entry);
		screen.handleCatalogFetchResult(online);

		assertEquals(screen.installPhase(), PackageInstallPhase.IDLE,
				"nach dem Fehlzustand darf der Knopf nicht auf 'arbeitet ...' stehen bleiben");
		assertTrue(screen.installError().isPresent(),
				"ein latestVersion ohne passenden versions[]-Eintrag muss einen sichtbaren Fehlerzustand setzen, "
						+ "statt still zu bleiben (vorher: nur geloggt + returnToParent())");
		assertFalse(screen.installError().get().isBlank());
	}

	private static PackageDetailModel minimalModel() {
		Manifest manifest = new Manifest(1, "solo.pack", "Solo", "1.0.0", "texturen", "solo",
				null, List.of(), null, List.of(), null, null);
		return PackageDetailModel.of(manifest, TileStatus.NONE);
	}
}
