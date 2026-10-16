package de.spassglas.vitrine.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geschlossene Menge arithmetischer, vergleichender und boolescher
 * Operationen der Skript-Sandbox (ROADMAP X5). Es gibt bewusst keine
 * Operation, die eine Datei, einen Netzwerk-Socket oder eine Java-Klasse
 * beruehrt -- {@link ScriptParser} kennt nur diese Liste, alles andere ist
 * ein Parse-Fehler, keine Ausfuehrung.
 */
public enum ScriptOp {

	ADD("add", Arity.BINARY),
	SUB("sub", Arity.BINARY),
	MUL("mul", Arity.BINARY),
	DIV("div", Arity.BINARY),
	MOD("mod", Arity.BINARY),
	EQ("eq", Arity.BINARY),
	NEQ("neq", Arity.BINARY),
	LT("lt", Arity.BINARY),
	LTE("lte", Arity.BINARY),
	GT("gt", Arity.BINARY),
	GTE("gte", Arity.BINARY),
	AND("and", Arity.BINARY),
	OR("or", Arity.BINARY),
	NOT("not", Arity.UNARY);

	/** Ob die Operation einen oder zwei Operanden nimmt. */
	public enum Arity { UNARY, BINARY }

	private static final Map<String, ScriptOp> BY_ID = buildIndex();

	private final String id;
	private final Arity arity;

	ScriptOp(String id, Arity arity) {
		this.id = id;
		this.arity = arity;
	}

	/** Der Bezeichner wie im Skript-JSON ({@code "add"}, {@code "not"} usw.). */
	public String id() {
		return id;
	}

	public Arity arity() {
		return arity;
	}

	/** Loest einen JSON-Bezeichner auf, oder {@link Optional#empty()} wenn unbekannt. */
	public static Optional<ScriptOp> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, ScriptOp> buildIndex() {
		Map<String, ScriptOp> index = new LinkedHashMap<>();
		for (ScriptOp op : values()) {
			index.put(op.id, op);
		}
		return index;
	}
}
