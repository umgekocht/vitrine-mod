package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.hotapply.HotApplyService;

/**
 * Hält den einen {@link PackInstaller} für den echten Client, verdrahtet mit
 * {@link VitrinePackProvider#clientInstance()} und {@link HotApplyService}
 * (ROADMAP S4) -- dasselbe "initialization-on-demand holder"-Muster (JLS
 * 12.4.1) wie {@link PackUninstallService} und {@link HotApplyService}, aus
 * demselben Grund: kein {@code MinecraftClient#getInstance()} bei jeder
 * Klasseninitialisierung.
 *
 * <p>Öffentlicher Einstiegspunkt für die Store-Oberfläche (S4-Aktionsknopf,
 * Fähigkeiten-Dialog). Die eigentliche Sechs-Schritt-Logik ist vollständig in
 * {@link PackInstaller} getestet.</p>
 */
public final class PackInstallService {

	private static final class InstanceHolder {
		static final PackInstaller INSTANCE = new PackInstaller(
				VitrinePackProvider.clientInstance(),
				HotApplyService.instance(),
				ThemePackService.instance());
	}

	private PackInstallService() {
	}

	/** Der eine {@link PackInstaller} für den echten Client. */
	public static PackInstaller instance() {
		return InstanceHolder.INSTANCE;
	}
}
