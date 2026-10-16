package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptContext;
import de.spassglas.vitrine.script.ScriptExecutionException;
import de.spassglas.vitrine.script.ScriptInterpreter;
import de.spassglas.vitrine.script.ScriptValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fuehrt ein geladenes {@link ScriptAddon} einmal aus: prueft die Bedingung,
 * dann die Aktionen in Reihenfolge (ROADMAP AD2). Jede Auswertung laeuft
 * ueber {@link ScriptInterpreter#execute(de.spassglas.vitrine.script.ScriptExpr, ScriptContext, long)}
 * mit {@link #STEP_BUDGET} -- "Budget je Tick" woertlich, denn {@link
 * de.spassglas.vitrine.addon.script.client.ScriptAddons} ruft {@link #run}
 * fuer {@code tick}-Skripte jeden Client-Tick neu auf.
 *
 * <p><b>Nie ein Ausbruch nach aussen (Charter-Regel 2):</b> eine
 * {@link ScriptExecutionException} (Budget ueberschritten, Typfehler,
 * Division durch null, zu tiefe Verschachtelung) wird geloggt und bricht nur
 * die betroffene Bedingung/Aktion ab -- nie den Client. Eine unerwartete
 * {@link RuntimeException} aus {@link ScriptAddonRuntime} selbst (z. B. eine
 * kaputte Umsetzung) wird ebenso abgefangen, damit ein einzelnes Addon nie
 * alle anderen (oder den Tick-Takt selbst) mitreisst.</p>
 */
public final class ScriptAddonExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Script");

	/**
	 * Kleiner als {@link ScriptInterpreter#DEFAULT_STEP_BUDGET}: Addon-Skripte
	 * fuer {@code tick} laufen potenziell 20x pro Sekunde, eine Bedingung oder
	 * ein HUD-Wert braucht dafuer keine 100.000 Schritte. Klar benannt statt
	 * eine Magic Number an jeder Aufrufstelle (ROADMAP AD2, "Budget je Tick").
	 */
	public static final long STEP_BUDGET = 10_000L;

	private ScriptAddonExecutor() {
	}

	/** Prueft die Bedingung von {@code script} und fuehrt bei Erfuellung alle Aktionen aus. */
	public static void run(ScriptAddon script, ScriptContext context, ScriptAddonRuntime runtime) {
		if (script.condition() != null) {
			boolean conditionHolds;
			try {
				conditionHolds = ScriptInterpreter.execute(script.condition(), context, STEP_BUDGET).asBoolean();
			} catch (ScriptExecutionException e) {
				LOGGER.warn("Addon-Skript ({}): Bedingung abgebrochen, Aktionen bleiben aus: {}",
						script.event().id(), e.getMessage());
				return;
			}
			if (!conditionHolds) {
				return;
			}
		}
		for (ScriptAddonAction action : script.actions()) {
			runAction(script, action, context, runtime);
		}
	}

	private static void runAction(ScriptAddon script, ScriptAddonAction action, ScriptContext context,
			ScriptAddonRuntime runtime) {
		try {
			switch (action) {
				case ScriptAddonAction.ZeigeNachricht zeigeNachricht -> {
					ScriptValue text = ScriptInterpreter.execute(zeigeNachricht.text(), context, STEP_BUDGET);
					runtime.showMessage(text.display());
				}
				case ScriptAddonAction.SpieleTon spieleTon -> runtime.playSound(spieleTon.soundId());
				case ScriptAddonAction.SetzeHudWert setzeHudWert -> {
					ScriptValue value = ScriptInterpreter.execute(setzeHudWert.wert(), context, STEP_BUDGET);
					runtime.setHudValue(setzeHudWert.schluessel(), value);
				}
			}
		} catch (ScriptExecutionException e) {
			LOGGER.warn("Addon-Skript ({}): Aktion {} abgebrochen: {}", script.event().id(),
					action.getClass().getSimpleName(), e.getMessage());
		} catch (RuntimeException e) {
			// Verteidigung in der Tiefe: eine kaputte Runtime-Umsetzung darf das
			// Addon-System nie mitreissen (Charter-Regel 2), auch wenn das heute
			// kein Codepfad in den eigenen ScriptAddonRuntime-Implementierungen
			// ausloesen sollte.
			LOGGER.warn("Addon-Skript ({}): Aktion {} unerwartet fehlgeschlagen: {}", script.event().id(),
					action.getClass().getSimpleName(), e.toString());
		}
	}
}
