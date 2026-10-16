package de.spassglas.vitrine.script;

import java.util.Locale;

/**
 * Der Laufzeitwert eines Ausdrucks der Skript-Sandbox (ROADMAP X5). Geschlossene
 * Menge von drei Sorten -- keine Objektreferenz, kein Array, kein Funktionswert,
 * also nichts, worueber sich ein Skript jemals an eine Java-Instanz oder
 * Klasse haengen koennte (Charter-Regel 3/4: kein Ausbruch aus der Sandbox).
 */
public sealed interface ScriptValue {

	/** Zeigt den Wert so an, wie ihn ein Diagnose-Text oder Log erhalten wuerde. */
	String display();

	record Number(double value) implements ScriptValue {
		@Override
		public String display() {
			if (value == Math.rint(value) && !java.lang.Double.isInfinite(value)) {
				return java.lang.Long.toString((long) value);
			}
			return String.format(Locale.ROOT, "%s", value);
		}
	}

	record Text(String value) implements ScriptValue {
		@Override
		public String display() {
			return value;
		}
	}

	record Bool(boolean value) implements ScriptValue {
		@Override
		public String display() {
			return java.lang.Boolean.toString(value);
		}
	}

	/**
	 * Liest diesen Wert als Zahl, oder wirft eine {@link ScriptExecutionException}
	 * mit Klartext, wenn er es nicht ist -- kein {@code ClassCastException}
	 * verlaesst jemals den Interpreter (Charter-Regel 2/Punkt 4 der Aufgabe:
	 * ein kaputtes Skript darf den Client nie mitreissen).
	 */
	default double asNumber() throws ScriptExecutionException {
		if (this instanceof Number number) {
			return number.value();
		}
		throw new ScriptExecutionException(
				"erwartete eine Zahl, bekam " + kindName() + " (" + display() + ")");
	}

	default boolean asBoolean() throws ScriptExecutionException {
		if (this instanceof Bool bool) {
			return bool.value();
		}
		throw new ScriptExecutionException(
				"erwartete einen Wahrheitswert, bekam " + kindName() + " (" + display() + ")");
	}

	private String kindName() {
		return switch (this) {
			case Number ignored -> "eine Zahl";
			case Text ignored -> "einen Text";
			case Bool ignored -> "einen Wahrheitswert";
		};
	}
}
