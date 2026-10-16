package de.spassglas.vitrine.script;

/**
 * Wird geworfen, wenn ein Skript-JSON kein gueltiges JSON ist, keine
 * Operation aus der geschlossenen {@link ScriptOp}/{@link ScriptDataSource}-
 * Whitelist referenziert oder sonst gegen die feste Grammatik der Sandbox
 * verstoesst (ROADMAP X5).
 *
 * <p>Das ist die erste Verteidigungslinie gegen Ausbruchsversuche: ein Skript,
 * das eine Datei-, Netz- oder Reflection-Operation referenziert, hat dafuer
 * gar keinen Bezeichner in der Whitelist -- es wird hier abgelehnt, bevor
 * ueberhaupt ein {@link ScriptExpr} entsteht, geschweige denn ausgefuehrt
 * wird. Geprueft (checked), analog zu
 * {@code de.spassglas.vitrine.hud.HudParseException}: ein einzelnes kaputtes
 * Skript soll abgewiesen werden koennen, ohne den Aufrufer abstuerzen zu
 * lassen (Charter-Regel 2).</p>
 */
public final class ScriptParseException extends Exception {

	public ScriptParseException(String message) {
		super(message);
	}
}
