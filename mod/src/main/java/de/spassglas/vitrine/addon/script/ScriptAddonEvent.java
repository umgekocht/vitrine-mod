package de.spassglas.vitrine.addon.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Die vier Ereignisse, auf die ein Addon-Skript ({@code scripts/*.vs},
 * ROADMAP AD2) reagieren kann: "Tick, Welt betreten, Chat-Befehl {@code /v
 * <name>}, Taste" (woertlich aus dem Backlog). Eine geschlossene Aufzaehlung
 * wie {@link de.spassglas.vitrine.addon.AddonRule#EVENTS} -- ein {@code .vs}
 * mit einem unbekannten Ereignis wird von {@link ScriptAddonParser} abgelehnt,
 * nie als Sonderfall durchgelassen.
 */
public enum ScriptAddonEvent {

	/** Feuert jeden Client-Tick (ROADMAP AD2: "Budget je Tick" gilt dafuer besonders). */
	TICK("tick"),

	/** Feuert einmal, wenn der Spieler eine Welt betritt (Singleplayer oder LAN-Host beitritt). */
	WELT_BETRETEN("welt_betreten"),

	/** Feuert, wenn der Chat-Befehl {@code /v <name>} mit passendem {@code name} ausgefuehrt wird. */
	BEFEHL("befehl"),

	/** Feuert, wenn die eine Vitrine-Addon-Taste gedrueckt wird (siehe Klassendoku von {@code ScriptAddonKeyBinding}). */
	TASTE("taste");

	private static final Map<String, ScriptAddonEvent> BY_ID = buildIndex();

	private final String id;

	ScriptAddonEvent(String id) {
		this.id = id;
	}

	/** Der Bezeichner wie im {@code .vs}-JSON unter {@code "ereignis"}. */
	public String id() {
		return id;
	}

	public static Optional<ScriptAddonEvent> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, ScriptAddonEvent> buildIndex() {
		Map<String, ScriptAddonEvent> index = new LinkedHashMap<>();
		for (ScriptAddonEvent event : values()) {
			index.put(event.id, event);
		}
		return index;
	}
}
