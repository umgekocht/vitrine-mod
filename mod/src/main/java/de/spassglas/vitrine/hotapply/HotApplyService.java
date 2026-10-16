package de.spassglas.vitrine.hotapply;

/**
 * Haelt den einen {@link HotApplyCoordinator} fuer den echten Client,
 * verdrahtet mit den echten, an {@code MinecraftClient} angebundenen
 * Umsetzungen (ROADMAP H2). Absichtlich eine eigene, verschachtelte Klasse
 * ("initialization-on-demand holder", JLS 12.4.1) statt eines direkten
 * statischen Feldes auf {@link HotApplyService} selbst -- dieselbe
 * Begruendung wie bei {@code
 * de.spassglas.vitrine.pack.VitrinePackProvider.ClientInstanceHolder}: ein
 * statisches Feld wuerde {@link net.minecraft.client.MinecraftClient#getInstance()}
 * bereits bei jeder Klasseninitialisierung auswerten, auch dort, wo kein
 * laufender Client existiert.
 *
 * <p>Dies ist der oeffentliche Einstiegspunkt, ueber den spaetere UI-Aufrufe
 * (S4, "Installieren/Aktivieren aus der Oberflaeche") sowie der
 * Test-/Debug-Befehl {@link HotApplyCommand} eine Paket-Aktivierung anstossen
 * -- die eigentliche Sammel-/Debounce-/Toast-Logik ist vollstaendig in
 * {@link HotApplyCoordinator} getestet.</p>
 */
public final class HotApplyService {

	private static final class InstanceHolder {
		static final HotApplyCoordinator INSTANCE;

		static {
			java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
					.resolve(de.spassglas.vitrine.Vitrine.MOD_ID);
			INSTANCE = new HotApplyCoordinator(
					new MinecraftPackActivation(),
					new MinecraftResourceReloadTrigger(),
					new SystemToastHotApplyPresenter(),
					ScheduledExecutorDelayedTaskScheduler.daemon(),
					StateFileRollbackPersister.forConfigDir(configDir),
					// ROADMAP GATE81: Rollback-/Fehlschlag-Toasts zeigen den
					// echten Manifest-Namen, keine technische Profil-ID.
					new ManifestPackDisplayNameLookup(
							de.spassglas.vitrine.pack.VitrinePackProvider.forConfigDir(configDir)));
		}
	}

	private HotApplyService() {
	}

	/** Der eine {@link HotApplyCoordinator} fuer den echten Client. */
	public static HotApplyCoordinator instance() {
		return InstanceHolder.INSTANCE;
	}

	/** Kurzform fuer {@code instance().requestActivation(packId)}. */
	public static void requestActivation(String packId) {
		instance().requestActivation(packId);
	}

	/** Kurzform fuer {@code instance().requestDeactivation(packId)} (ROADMAP H5, "nur deaktivieren"). */
	public static void requestDeactivation(String packId) {
		instance().requestDeactivation(packId);
	}

	/** Kurzform fuer {@code instance().requestReload(affectedPackIds)} (ROADMAP S5, "Reihenfolge"/"Alle aktualisieren"). */
	public static void requestReload(java.util.Collection<String> affectedPackIds) {
		instance().requestReload(affectedPackIds);
	}

	/** Kurzform fuer {@code instance().lastReloadStats()} (ROADMAP X3, Diagnose-Bildschirm). */
	public static java.util.Optional<HotApplyCoordinator.ReloadStats> lastReloadStats() {
		return instance().lastReloadStats();
	}
}
