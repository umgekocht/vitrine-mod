package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Der "vom Skript beschreibbare Wertspeicher" aus ROADMAP AD2 ("HUD-Wert
 * setzen"): ein Addon-Skript schreibt unter einem selbst gewaehlten
 * Schluessel (siehe {@link ScriptAddonAction.SetzeHudWert}), ein spaeteres
 * HUD-Widget (ROADMAP AD3/AD5) kann denselben Schluessel ueber {@link #get}
 * lesen. Bewusst die kleinste Erweiterung statt eines Umbaus von
 * {@code de.spassglas.vitrine.hud.HudDataSource}: diese geschlossene
 * Aufzaehlung kennt nur feste, in {@code hud.schema.json} benannte
 * Bezeichner, keine von einem Addon frei erfundenen Schluessel -- das HUD-
 * Rendering selbst bleibt deshalb unveraendert, bis ein eigener Widget-Typ
 * dafuer entsteht.
 *
 * <p>Threadsicher (einfache Synchronisierung reicht: wenige, kleine
 * Zugriffe, nie im Zeichen-Hotpath). Gedeckelt auf {@link #MAX_ENTRIES}, damit
 * kein Skript ueber Jahre hinweg unbegrenzt neue Schluessel anlegen kann.</p>
 */
public final class ScriptAddonHudValues {

	/** Obergrenze gleichzeitig gespeicherter Schluessel -- reine Eingabe-Hygiene. */
	public static final int MAX_ENTRIES = 64;

	private static final Map<String, ScriptValue> VALUES = new LinkedHashMap<>();

	private ScriptAddonHudValues() {
	}

	/** Setzt {@code key} auf {@code value}; verwirft die Schreibung, wenn {@link #MAX_ENTRIES} bereits erreicht ist. */
	public static synchronized void set(String key, ScriptValue value) {
		if (!VALUES.containsKey(key) && VALUES.size() >= MAX_ENTRIES) {
			return;
		}
		VALUES.put(key, value);
	}

	/** Liest den zuletzt gesetzten Wert unter {@code key}, oder {@link Optional#empty()}. */
	public static synchronized Optional<ScriptValue> get(String key) {
		return Optional.ofNullable(VALUES.get(key));
	}

	/** Entfernt alle Werte -- fuer Tests und einen moeglichen spaeteren Welt-Wechsel. */
	public static synchronized void clear() {
		VALUES.clear();
	}

	/** Anzahl aktuell gespeicherter Schluessel -- fuer Tests. */
	public static synchronized int size() {
		return VALUES.size();
	}
}
