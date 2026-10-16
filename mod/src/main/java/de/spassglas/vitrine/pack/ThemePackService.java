package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.ui.theme.ThemeService;

/**
 * Haelt den einen {@link ThemePackCoordinator} fuer den echten Client (ROADMAP
 * T4), verdrahtet mit dem einen {@link ThemeService#instance() ThemeManager} und
 * {@link VitrinePackProvider#clientInstance()} -- dasselbe
 * "initialization-on-demand holder"-Muster (JLS 12.4.1) wie
 * {@link PackInstallService}/{@link PackUninstallService}/{@link
 * de.spassglas.vitrine.hotapply.HotApplyService}: kein
 * {@code MinecraftClient#getInstance()} bei jeder Klasseninitialisierung.
 *
 * <p>Der {@link ThemePackCoordinator} selbst ist client-frei und in
 * {@code ThemePackCoordinatorTest} vollstaendig getestet.</p>
 */
public final class ThemePackService {

	private static final class InstanceHolder {
		static final ThemePackCoordinator INSTANCE = new ThemePackCoordinator(
				ThemeService.instance(),
				VitrinePackProvider.clientInstance().packsDir().getParent(),
				VitrinePackProvider.clientInstance()::findPackFile);
	}

	private ThemePackService() {
	}

	/** Der eine {@link ThemePackCoordinator} fuer den echten Client. */
	public static ThemePackCoordinator instance() {
		return InstanceHolder.INSTANCE;
	}
}
