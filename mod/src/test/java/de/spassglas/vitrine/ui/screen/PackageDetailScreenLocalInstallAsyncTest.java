package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.detail.PackageInstallPhase;
import de.spassglas.vitrine.ui.downloads.DownloadEntry;
import de.spassglas.vitrine.ui.downloads.DownloadFlyoutService;
import de.spassglas.vitrine.ui.tile.TileStatus;
import net.minecraft.SharedConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE79-Beweis: {@code PackageDetailScreen#runInstall} (der lokale
 * Installationsweg, {@code fromCatalog == false}) rief vorher {@link
 * de.spassglas.vitrine.pack.PackInstallService#instance()} synchron auf dem
 * aufrufenden Thread auf -- ohne {@link PackageInstallPhase#WORKING}, ohne
 * Flyout-Zeile, und kehrte bei Erfolg wie bei Fehler gleich aus, nur mit
 * einem Log-Eintrag im Fehlerfall (kein sichtbarer Unterschied). Dieser Test
 * ersetzt den eigentlichen Installationsschritt durch {@link
 * PackageDetailScreen#setLocalInstallOperationForTest} -- ohne echten {@code
 * PackInstallService}-Singleton (haengt am {@code FabricLoader}-Konfigurations-
 * ordner, der im Testbetrieb nicht existiert) -- und beweist direkt aus dem
 * simulierten Installationsschritt heraus, dass zu genau diesem Zeitpunkt
 * schon {@link PackageInstallPhase#WORKING} gesetzt ist und eine laufende
 * Flyout-Zeile existiert, und dass ein simulierter Fehler einen sichtbaren
 * Fehlertext setzt statt stumm zurueckzukehren.
 */
public final class PackageDetailScreenLocalInstallAsyncTest {

	@BeforeClass
	public void bootstrapMinecraftGameData() {
		SharedConstants.createGameVersion();
	}

	@Test
	public void localInstallShowsWorkingPhaseAndFlyoutRowWhileRunningThenReturnsToIdle() {
		PackageDetailModel model = minimalModel();
		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		String flyoutId = VitrinePackProvider.profileId(model.manifestId());

		assertEquals(screen.installPhase(), PackageInstallPhase.IDLE,
				"vor einem Versuch darf der Knopf nicht 'arbeitet ...' zeigen");
		assertTrue(screen.installError().isEmpty(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");

		AtomicBoolean sawWorkingDuringWork = new AtomicBoolean(false);
		AtomicBoolean sawFlyoutRowDuringWork = new AtomicBoolean(false);
		screen.setLocalInstallOperationForTest((manifestId, capabilityIds) -> {
			sawWorkingDuringWork.set(screen.installPhase() == PackageInstallPhase.WORKING);
			sawFlyoutRowDuringWork.set(DownloadFlyoutService.instance().running().stream()
					.anyMatch(entry -> entry.packId().equals(flyoutId)));
			// simulierter Erfolg -- entspricht dem echten PackInstaller, der bei
			// Erfolg einfach zurueckkehrt.
		});

		screen.runInstall(List.of());

		assertTrue(sawWorkingDuringWork.get(),
				"der lokale Installationsschritt muss ausgefuehrt werden, waehrend phase auf WORKING steht "
						+ "(GATE79 -- vorher: synchron ohne jede sichtbare Rueckmeldung)");
		assertTrue(sawFlyoutRowDuringWork.get(),
				"der lokale Installationsweg muss waehrend der Arbeit eine Flyout-Fortschrittszeile zeigen, "
						+ "genau wie der Katalogweg (GATE79)");

		assertEquals(screen.installPhase(), PackageInstallPhase.IDLE,
				"nach einem erfolgreichen Versuch darf der Knopf nicht auf 'arbeitet ...' stehen bleiben");
		assertTrue(screen.installError().isEmpty(), "ein erfolgreicher Versuch darf keinen Fehlerzustand setzen");
		assertFalse(DownloadFlyoutService.instance().running().stream().anyMatch(e -> e.packId().equals(flyoutId)),
				"nach Abschluss darf die Zeile nicht mehr als laufend gelten");
		Optional<DownloadEntry> recent = DownloadFlyoutService.instance().recent().stream()
				.filter(e -> e.packId().equals(flyoutId)).findFirst();
		assertTrue(recent.isPresent(), "die abgeschlossene Zeile muss in 'Zuletzt abgeschlossen' auftauchen");
	}

	@Test
	public void localInstallSimulatedFailureProducesVisibleErrorInsteadOfSilentReturn() {
		PackageDetailModel model = minimalModel();
		PackageDetailScreen screen = new PackageDetailScreen(null, model);
		String flyoutId = VitrinePackProvider.profileId(model.manifestId());

		screen.setLocalInstallOperationForTest((manifestId, capabilityIds) -> {
			throw new PackInstallException("simulierter interner Fehler (Test)",
					"vitrine.error.install.offer_not_found", manifestId);
		});

		screen.runInstall(List.of());

		assertEquals(screen.installPhase(), PackageInstallPhase.IDLE,
				"nach einem gescheiterten Versuch darf der Knopf nicht auf 'arbeitet ...' stehen bleiben");
		assertTrue(screen.installError().isPresent(),
				"ein simulierter Installationsfehler muss einen sichtbaren Fehlerzustand setzen, statt stumm "
						+ "zurueckzukehren (vorher: nur Log + synchroner Rueckfall ohne jede Rueckmeldung)");
		assertFalse(screen.installError().get().isBlank());

		Optional<DownloadEntry> recent = DownloadFlyoutService.instance().recent().stream()
				.filter(e -> e.packId().equals(flyoutId)).findFirst();
		assertTrue(recent.isPresent(), "die fehlgeschlagene Zeile muss in 'Zuletzt abgeschlossen' auftauchen");
		assertEquals(recent.get().phase().name(), "FAILED");
	}

	private static PackageDetailModel minimalModel() {
		Manifest manifest = new Manifest(1, "solo.pack.local", "Solo Lokal", "1.0.0", "texturen", "solo",
				null, List.of(), null, List.of(), null, null);
		return PackageDetailModel.of(manifest, TileStatus.NONE);
	}
}
