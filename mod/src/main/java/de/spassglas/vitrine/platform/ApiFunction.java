package de.spassglas.vitrine.platform;

import java.util.List;

/**
 * Eine Funktion der Vitrine-API, sprachunabhaengig. Argumente und Rueckgabe
 * nutzen nur das gemeinsame Werte-Modell: {@code null}, {@link Boolean},
 * {@link Double}, {@link String}, {@link List}, {@link java.util.Map},
 * {@link ApiFunction} und {@link Callback}. Jede Laufzeit (Lua, JavaScript, ...)
 * uebersetzt nur zwischen ihren eigenen Werten und diesem Modell -- deshalb
 * kann jedes Addon gleich viel, egal in welcher Sprache es geschrieben ist.
 */
@FunctionalInterface
public interface ApiFunction {

	Object call(List<Object> args);

	/** Hilfen zum Lesen von Argumenten. */
	static String str(List<Object> a, int i, String def) {
		return i < a.size() && a.get(i) != null ? String.valueOf(a.get(i)) : def;
	}

	static double num(List<Object> a, int i, double def) {
		return i < a.size() && a.get(i) instanceof Number n ? n.doubleValue() : def;
	}

	static boolean bool(List<Object> a, int i, boolean def) {
		return i < a.size() && a.get(i) instanceof Boolean b ? b : def;
	}

	static Callback fn(List<Object> a, int i) {
		return i < a.size() && a.get(i) instanceof Callback c ? c : null;
	}
}
