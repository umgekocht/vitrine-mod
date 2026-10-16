package de.spassglas.vitrine.script;

/**
 * Wird geworfen, wenn die Ausfuehrung eines bereits geparsten {@link ScriptExpr}
 * scheitert -- Typfehler (z. B. Text wo eine Zahl erwartet wird), Division
 * durch null, oder eine der beiden Sandbox-Grenzen aus {@link ScriptInterpreter}
 * ({@link ScriptBudgetExceededException}: Schritt-Budget oder Schachtelungstiefe).
 *
 * <p>Geprueft (checked), analog zu {@code ScriptParseException}: ein
 * fehlschlagendes Skript liefert eine Klartextmeldung an den Aufrufer statt
 * den Client mitzureissen (Charter-Regel 2, Aufgabe X5 Punkt 4). Der
 * Interpreter wirft niemals eine unkontrollierte {@code RuntimeException} nach
 * aussen -- jede interne Ausnahme wird hier eingepackt.</p>
 */
public class ScriptExecutionException extends Exception {

	public ScriptExecutionException(String message) {
		super(message);
	}

	public ScriptExecutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
