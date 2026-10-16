package de.spassglas.vitrine.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;

/**
 * Parst eine Skript-Zeichenkette der Sandbox (ROADMAP X5) in einen
 * {@link ScriptExpr}-Baum. Aufgebaut nach demselben Muster wie
 * {@code de.spassglas.vitrine.hud.HudLayoutParser}: kein freies Format,
 * sondern eine feste Grammatik, gegen die jedes Feld geprueft wird, mit einer
 * konkreten {@link ScriptParseException}-Meldung statt eines pauschalen
 * "ungueltig".
 *
 * <p><b>Format</b> (jeder Knoten ein JSON-Objekt mit Feld {@code "op"}):</p>
 * <ul>
 *   <li>{@code {"op":"lit","value": 3}} -- Zahl, Text oder Wahrheitswert als
 *       {@link ScriptExpr.Literal}.</li>
 *   <li>{@code {"op":"data","source":"installedPackageCount"}} -- Verweis auf
 *       {@link ScriptDataSource}.</li>
 *   <li>{@code {"op":"add","left":...,"right":...}} (und die uebrigen
 *       zweistelligen {@link ScriptOp}-Bezeichner) -- {@link ScriptExpr.BinaryOp}.</li>
 *   <li>{@code {"op":"not","operand":...}} -- {@link ScriptExpr.UnaryOp}.</li>
 *   <li>{@code {"op":"if","condition":...,"then":...,"else":...}} --
 *       {@link ScriptExpr.Conditional}.</li>
 *   <li>{@code {"op":"repeat","count": 5,"body":...}} --
 *       {@link ScriptExpr.Repeat}.</li>
 * </ul>
 *
 * <p><b>Sicherheit:</b> das Feld {@code "op"} (bzw. {@code "source"}) wird
 * ausschliesslich gegen {@link ScriptOp#byId(String)} bzw.
 * {@link ScriptDataSource#byId(String)} geprueft -- eine unbekannte
 * Zeichenkette (z. B. ein Dateipfad, eine URL oder ein Java-Klassen-/
 * Methodenname) ist ein Parse-Fehler, niemals eine dynamische Aufloesung.
 * Ausserdem wird die Schachtelungstiefe waehrend des Parsens gezaehlt: ab
 * {@link #MAX_NESTING_DEPTH} bricht der Parser mit einer klaren Meldung ab,
 * statt beliebig tief zu rekursieren (schuetzt sowohl den Parser selbst als
 * auch {@link ScriptInterpreter} vor einem {@code StackOverflowError} durch
 * absichtlich extrem verschachtelte Eingaben).</p>
 */
public final class ScriptParser {

	/**
	 * Maximale Schachtelungstiefe eines Ausdrucksbaums. Klein genug, dass
	 * weder Parsen noch Auswerten je in die Naehe des JVM-Stacklimits kommen,
	 * gross genug fuer jeden realistischen deklarativen Ausdruck.
	 */
	public static final int MAX_NESTING_DEPTH = 64;

	/**
	 * Obere Grenze fuer den im Skript deklarierten {@code repeat}-Zaehler.
	 * Reine Eingabe-Hygiene (verhindert z. B. negative oder auf Overflow
	 * angelegte Werte) -- der eigentliche Schutz vor einer Endlosschleife ist
	 * das Schritt-Budget in {@link ScriptInterpreter}, nicht dieser Wert.
	 */
	public static final long MAX_DECLARED_REPEAT_COUNT = 1_000_000_000L;

	private ScriptParser() {
	}

	/** Wie {@link #parse(String)}, mit UTF-8-Bytes eines Skripts. */
	public static ScriptExpr parse(byte[] json) throws ScriptParseException {
		return parse(new String(json, StandardCharsets.UTF_8));
	}

	/**
	 * Parst {@code json} in einen {@link ScriptExpr}-Baum.
	 *
	 * @throws ScriptParseException wenn {@code json} kein gueltiges JSON ist,
	 *                              die Grammatik verletzt, eine unbekannte
	 *                              Operation/Datenquelle referenziert oder die
	 *                              maximale Schachtelungstiefe ueberschreitet.
	 */
	public static ScriptExpr parse(String json) throws ScriptParseException {
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new ScriptParseException("Skript ist kein gueltiges JSON: " + e.getMessage());
		}
		return parseNode(root, 0);
	}

	private static ScriptExpr parseNode(JsonElement element, int depth) throws ScriptParseException {
		if (depth > MAX_NESTING_DEPTH) {
			throw new ScriptParseException(
					"Skript ist zu tief verschachtelt (Grenze " + MAX_NESTING_DEPTH + " Ebenen)");
		}
		if (element == null || !element.isJsonObject()) {
			throw new ScriptParseException("jeder Skript-Knoten muss ein JSON-Objekt sein, ist aber "
					+ (element == null || element.isJsonNull() ? "null" : element.getClass().getSimpleName()));
		}
		JsonObject node = element.getAsJsonObject();
		if (!node.has("op") || !node.get("op").isJsonPrimitive()) {
			throw new ScriptParseException("Skript-Knoten braucht ein String-Feld 'op'");
		}
		String opId = node.get("op").getAsString();

		switch (opId) {
			case "lit":
				return parseLiteral(node);
			case "data":
				return parseDataRef(node);
			case "if":
				return parseConditional(node, depth);
			case "repeat":
				return parseRepeat(node, depth);
			default:
				break;
		}

		ScriptOp op = ScriptOp.byId(opId).orElseThrow(() -> new ScriptParseException(
				"unbekannte Operation '" + opId + "' -- die Sandbox kennt keine solche Operation "
						+ "(erlaubt: lit, data, if, repeat, " + allOpIds() + ")"));
		return switch (op.arity()) {
			case UNARY -> new ScriptExpr.UnaryOp(op, requireChild(node, "operand", depth));
			case BINARY -> new ScriptExpr.BinaryOp(op, requireChild(node, "left", depth), requireChild(node, "right", depth));
		};
	}

	private static ScriptExpr.Literal parseLiteral(JsonObject node) throws ScriptParseException {
		if (!node.has("value") || !node.get("value").isJsonPrimitive()) {
			throw new ScriptParseException("'lit' braucht ein primitives Feld 'value' (Zahl, Text oder true/false)");
		}
		JsonPrimitive value = node.getAsJsonPrimitive("value");
		if (value.isBoolean()) {
			return new ScriptExpr.Literal(new ScriptValue.Bool(value.getAsBoolean()));
		}
		if (value.isNumber()) {
			return new ScriptExpr.Literal(new ScriptValue.Number(value.getAsDouble()));
		}
		return new ScriptExpr.Literal(new ScriptValue.Text(value.getAsString()));
	}

	private static ScriptExpr.DataRef parseDataRef(JsonObject node) throws ScriptParseException {
		if (!node.has("source") || !node.get("source").isJsonPrimitive()) {
			throw new ScriptParseException("'data' braucht ein String-Feld 'source'");
		}
		String sourceId = node.get("source").getAsString();
		ScriptDataSource source = ScriptDataSource.byId(sourceId).orElseThrow(() -> new ScriptParseException(
				"unbekannte Datenquelle '" + sourceId + "' -- die Sandbox kennt keinen solchen Wert "
						+ "(erlaubt: " + allDataSourceIds() + "); ein Java-Feld- oder Klassenname wird niemals "
						+ "aufgeloest"));
		return new ScriptExpr.DataRef(source);
	}

	private static ScriptExpr.Conditional parseConditional(JsonObject node, int depth) throws ScriptParseException {
		return new ScriptExpr.Conditional(
				requireChild(node, "condition", depth),
				requireChild(node, "then", depth),
				requireChild(node, "else", depth));
	}

	private static ScriptExpr.Repeat parseRepeat(JsonObject node, int depth) throws ScriptParseException {
		if (!node.has("count") || !node.get("count").isJsonPrimitive() || !node.get("count").getAsJsonPrimitive().isNumber()) {
			throw new ScriptParseException("'repeat' braucht ein numerisches Feld 'count'");
		}
		long count = node.get("count").getAsLong();
		if (count < 0) {
			throw new ScriptParseException("'repeat'-Feld 'count' darf nicht negativ sein, war " + count);
		}
		if (count > MAX_DECLARED_REPEAT_COUNT) {
			throw new ScriptParseException("'repeat'-Feld 'count' ueberschreitet die Obergrenze "
					+ MAX_DECLARED_REPEAT_COUNT + " (war " + count + ")");
		}
		ScriptExpr body = requireChild(node, "body", depth);
		return new ScriptExpr.Repeat(count, body);
	}

	private static ScriptExpr requireChild(JsonObject node, String field, int depth) throws ScriptParseException {
		if (!node.has(field)) {
			throw new ScriptParseException("Skript-Knoten '" + node.get("op").getAsString()
					+ "' braucht das Feld '" + field + "'");
		}
		return parseNode(node.get(field), depth + 1);
	}

	private static String allOpIds() {
		StringBuilder builder = new StringBuilder();
		for (ScriptOp op : ScriptOp.values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(op.id());
		}
		return builder.toString();
	}

	private static String allDataSourceIds() {
		StringBuilder builder = new StringBuilder();
		for (ScriptDataSource source : ScriptDataSource.values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(source.id());
		}
		return builder.toString();
	}
}
