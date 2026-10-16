package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptExpr;

import java.util.List;
import java.util.Objects;

/**
 * Ein geparstes Addon-Skript ({@code scripts/<name>.vs}, ROADMAP AD2): genau
 * ein Ereignis, eine optionale Bedingung (leer = immer wahr) und eine
 * geordnete Liste von Aktionen aus {@link ScriptAddonAction}. Gebaut von
 * {@link ScriptAddonParser}, nie direkt aus rohem Dateiinhalt.
 *
 * @param event       das Ereignis, bei dem dieses Skript feuert.
 * @param condition   die Bedingung aus der X5-Sandbox, oder {@code null} fuer
 *                    "immer wahr" ({@link ScriptAddonExecutor} wertet dann
 *                    gar nicht erst aus).
 * @param actions     die Aktionen, die bei erfuellter Bedingung in dieser
 *                    Reihenfolge laufen (nie leer, siehe {@link ScriptAddonParser#MAX_ACTIONS}).
 * @param commandName der zu {@code /v <name>} passende Name, nur gesetzt
 *                     (und dann nie leer) wenn {@code event == BEFEHL}, sonst
 *                     {@code null}.
 */
public record ScriptAddon(ScriptAddonEvent event, ScriptExpr condition, List<ScriptAddonAction> actions,
		String commandName) {

	public ScriptAddon {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(actions, "actions");
		actions = List.copyOf(actions);
		if (event == ScriptAddonEvent.BEFEHL && (commandName == null || commandName.isBlank())) {
			throw new IllegalArgumentException("Ereignis 'befehl' braucht einen nicht-leeren Befehlsnamen");
		}
		if (event != ScriptAddonEvent.BEFEHL && commandName != null) {
			throw new IllegalArgumentException("'befehl' ist nur fuer das Ereignis 'befehl' erlaubt");
		}
	}
}
