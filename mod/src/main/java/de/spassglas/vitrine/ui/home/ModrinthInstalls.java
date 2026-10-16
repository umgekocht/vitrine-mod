package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.modrinth.ModrinthClient;
import de.spassglas.vitrine.modrinth.ModrinthInstaller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Installationszustand der Modrinth-Projekte, ueber Neuaufbauten des Stores hinweg. */
public final class ModrinthInstalls {

	public enum State { IDLE, WORKING, DONE, RESTART, FAILED }

	private static final Map<String, State> STATES = new ConcurrentHashMap<>();

	private ModrinthInstalls() {
	}

	public static State state(String projectId) {
		return STATES.getOrDefault(projectId, State.IDLE);
	}

	/** Startet die Installation im Hintergrund (Doppelklicks werden ignoriert). */
	public static void start(ModrinthClient.Hit hit) {
		State s = state(hit.id());
		if (s == State.WORKING || s == State.DONE || s == State.RESTART) {
			return;
		}
		STATES.put(hit.id(), State.WORKING);
		Thread t = new Thread(() -> {
			try {
				ModrinthInstaller.Result r = ModrinthInstaller.install(hit);
				STATES.put(hit.id(), r == ModrinthInstaller.Result.APPLIED ? State.DONE : State.RESTART);
			} catch (Exception e) {
				Vitrine.LOGGER.warn("Modrinth-Installation von '{}' fehlgeschlagen: {}", hit.title(), e.toString());
				STATES.put(hit.id(), State.FAILED);
			}
		}, "Vitrine-Modrinth-Install");
		t.setDaemon(true);
		t.start();
	}

	/** Uebersetzungs-Key der Pille fuer diesen Zustand. */
	public static String pillKey(String projectId) {
		return switch (state(projectId)) {
			case WORKING -> "vitrine.modrinth.installing";
			case DONE -> "vitrine.modrinth.installed";
			case RESTART -> "vitrine.home.pill.restart";
			case FAILED -> "vitrine.modrinth.failed_short";
			default -> "vitrine.modrinth.get";
		};
	}
}
