package de.spassglas.vitrine.platform;

import de.spassglas.vitrine.Vitrine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ausgaben und Fehler aller Addons, fuer die Addon-Konsole im Spiel und das Log. */
public final class AddonConsole {

	public enum Level { INFO, WARN, ERROR }

	public record Line(String time, String addon, Level level, String text) {
	}

	private static final int MAX = 500;
	private static final Deque<Line> LINES = new ArrayDeque<>();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private AddonConsole() {
	}

	public static synchronized void log(String addon, Level level, String text) {
		LINES.addLast(new Line(LocalTime.now().format(TIME), addon, level, text));
		while (LINES.size() > MAX) {
			LINES.removeFirst();
		}
		switch (level) {
			case ERROR -> Vitrine.LOGGER.warn("[Addon {}] {}", addon, text);
			case WARN -> Vitrine.LOGGER.info("[Addon {}] {}", addon, text);
			default -> Vitrine.LOGGER.info("[Addon {}] {}", addon, text);
		}
	}

	public static synchronized List<Line> lines() {
		return new ArrayList<>(LINES);
	}

	public static synchronized void clear() {
		LINES.clear();
	}
}
