package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.hotapply.MinecraftPackActivation;
import de.spassglas.vitrine.hotapply.MinecraftResourceReloadTrigger;

import java.util.concurrent.CompletableFuture;

/**
 * Haelt den einen {@link PackUninstaller} fuer den echten Client, verdrahtet
 * mit den echten, an {@code MinecraftClient} angebundenen Umsetzungen (ROADMAP
 * H5) -- dasselbe "initialization-on-demand holder"-Muster (JLS 12.4.1) wie
 * {@link de.spassglas.vitrine.hotapply.HotApplyService} und {@link
 * VitrinePackProvider}s eigener, verschachtelter Holder, aus demselben Grund:
 * ein statisches Feld wuerde {@code MinecraftClient#getInstance()} bereits bei
 * jeder Klasseninitialisierung auswerten, auch dort, wo kein laufender Client
 * existiert (z. B. in reinen Unit-Tests von {@link PackUninstaller} selbst).
 *
 * <p>Oeffentlicher Einstiegspunkt fuer spaetere UI-Aufrufe (S4, "Deinstallieren"-
 * Knopf) sowie den Test-/Debug-Befehl {@code /vitrine uninstall} -- die
 * eigentliche Deaktivieren/Warten/Verschieben/Zustand-aktualisieren-Logik ist
 * vollstaendig in {@link PackUninstaller} getestet.</p>
 */
public final class PackUninstallService {

	private static final class InstanceHolder {
		static final PackUninstaller INSTANCE = new PackUninstaller(
				VitrinePackProvider.clientInstance(),
				new MinecraftPackActivation(),
				new MinecraftResourceReloadTrigger(),
				ThemePackService.instance());
	}

	private PackUninstallService() {
	}

	/** Der eine {@link PackUninstaller} fuer den echten Client. */
	public static PackUninstaller instance() {
		return InstanceHolder.INSTANCE;
	}

	/** Kurzform fuer {@code instance().uninstall(packId)}. */
	public static CompletableFuture<Void> requestUninstall(String packId) {
		return instance().uninstall(packId);
	}
}
