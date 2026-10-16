package de.spassglas.vitrine.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein geladenes Skript-Addon mit allem, was es registriert hat. Beim Entladen
 * wird genau diese Liste abgebaut -- nichts bleibt im Spiel zurueck.
 */
final class LoadedAddon {

	final String id;
	final String name;
	final String sourceKey;
	final Map<String, String> files;
	ScriptRuntime runtime;

	final Map<String, List<Callback>> handlers = new HashMap<>();
	final Map<Integer, Callback> keys = new HashMap<>();
	final Map<Integer, Boolean> keyDown = new HashMap<>();
	final List<Timer> timers = new ArrayList<>();
	boolean ownsShader;
	String restartReason;
	int errors;
	int nextTimerId = 1;

	static final class Timer {
		final int id;
		final long periodTicks;
		final boolean repeat;
		long remaining;
		final Callback fn;

		Timer(int id, long periodTicks, boolean repeat, Callback fn) {
			this.id = id;
			this.periodTicks = Math.max(1, periodTicks);
			this.repeat = repeat;
			this.remaining = this.periodTicks;
			this.fn = fn;
		}
	}

	LoadedAddon(String id, String name, String sourceKey, Map<String, String> files) {
		this.id = id;
		this.name = name;
		this.sourceKey = sourceKey;
		this.files = files;
	}

	void on(String event, Callback fn) {
		handlers.computeIfAbsent(event, k -> new ArrayList<>()).add(fn);
	}
}
