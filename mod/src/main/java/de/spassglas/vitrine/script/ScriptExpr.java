package de.spassglas.vitrine.script;

/**
 * Der Ausdrucksbaum eines Sandbox-Skripts (ROADMAP X5). Geschlossene Menge
 * von sechs Knotentypen -- kein "eval", kein Funktionsaufruf per Name, kein
 * Java-Bezug. Reines Datenmodell, gebaut von {@link ScriptParser}, ausgewertet
 * von {@link ScriptInterpreter}.
 *
 * <p>Absichtlich <b>keine</b> Rekursion oder Selbstaufrufe ausser der festen
 * Schachtelung, die der JSON-Baum selbst mitbringt: es gibt keinen
 * "Funktionsdefinition"- oder "Funktionsaufruf"-Knoten. Endlosschleifen sind
 * daher nur ueber {@link Repeat} moeglich, dessen Anzahl ein Skript zwar frei
 * waehlen kann, deren tatsaechliche Ausfuehrung aber immer durch das
 * Schritt-Budget in {@link ScriptInterpreter} begrenzt wird.</p>
 */
public sealed interface ScriptExpr {

	/** Ein fester Wert, direkt aus dem Skript-JSON uebernommen. */
	record Literal(ScriptValue value) implements ScriptExpr {
	}

	/** Verweis auf einen Wert aus der geschlossenen {@link ScriptDataSource}-Liste. */
	record DataRef(ScriptDataSource source) implements ScriptExpr {
	}

	/** Zweistellige Operation ({@link ScriptOp.Arity#BINARY}). */
	record BinaryOp(ScriptOp op, ScriptExpr left, ScriptExpr right) implements ScriptExpr {
	}

	/** Einstellige Operation ({@link ScriptOp.Arity#UNARY}), derzeit nur {@link ScriptOp#NOT}. */
	record UnaryOp(ScriptOp op, ScriptExpr operand) implements ScriptExpr {
	}

	/** Bedingter Ausdruck -- wertet genau einen der beiden Zweige aus, nie beide. */
	record Conditional(ScriptExpr condition, ScriptExpr whenTrue, ScriptExpr whenFalse) implements ScriptExpr {
	}

	/**
	 * Wertet {@code body} genau {@code count}-mal aus und liefert den letzten
	 * Wert ({@code count == 0} wertet {@code body} nicht aus und liefert
	 * {@code ScriptValue.Number(0)} als neutralen Default, siehe
	 * {@link ScriptInterpreter}). {@code count} kommt unveraendert aus dem
	 * Skript-JSON, auch sehr grosse
	 * oder absichtlich boesartige Werte -- die eigentliche Bremse ist das
	 * Schritt-Budget in {@link ScriptInterpreter}, nicht dieser Knoten.
	 */
	record Repeat(long count, ScriptExpr body) implements ScriptExpr {
	}
}
