package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.classb.RestartAction;
import net.minecraft.client.MinecraftClient;

/**
 * ROADMAP J2 -- Produktions-{@link RestartAction}: der Sammelknopf "Jetzt neu
 * starten" beendet den Client geordnet ueber
 * {@link MinecraftClient#scheduleStop()} (nach dem laufenden Frame). J2 selbst
 * fuehrt keine Jar aus und stoesst keinen erzwungenen Neustart an -- der Knopf
 * ist die Absichts-Aktion; das eigentliche Wiederhochfahren macht der Launcher.
 */
public final class ClientRestartAction implements RestartAction {

	@Override
	public void restart() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) {
			Vitrine.LOGGER.warn("Klasse B: 'Jetzt neu starten' ohne laufenden Client -- nichts zu tun.");
			return;
		}
		Vitrine.LOGGER.info("Klasse B: 'Jetzt neu starten' -- Client wird beendet (scheduleStop).");
		mc.scheduleStop();
	}
}
