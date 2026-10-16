package de.spassglas.vitrine.script;

/**
 * Wird geworfen, wenn ein Skript waehrend der Ausfuehrung eine der beiden
 * festen Sandbox-Grenzen aus {@link ScriptInterpreter} verletzt: das
 * Schritt-Budget je {@code execute(...)}-Aufruf (stoppt Endlosschleifen ueber
 * {@link ScriptExpr.Repeat}) oder die maximale Schachtelungstiefe (stoppt
 * uebermaessig tiefe Ausdrucksbaeume, bevor ein {@code StackOverflowError}
 * entstehen koennte). ROADMAP X5, Fertig-Kriterium: "Ausbruchsversuche ...
 * (Endlosschleife) werden gestoppt".
 */
public final class ScriptBudgetExceededException extends ScriptExecutionException {

	public ScriptBudgetExceededException(String message) {
		super(message);
	}
}
