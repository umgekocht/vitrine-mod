package de.spassglas.vitrine.platform;

/** Eine Funktion aus einem Addon (z. B. ein Lua- oder JavaScript-Handler), aufrufbar aus Java. */
@FunctionalInterface
public interface Callback {

	/** Ruft die Addon-Funktion auf; Fehler und Zeitlimit behandelt die Laufzeit. */
	Object call(Object... args);
}
