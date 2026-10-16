package de.spassglas.vitrine.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Wertet einen von {@link ScriptParser} gebauten {@link ScriptExpr}-Baum
 * gegen einen {@link ScriptContext} aus (ROADMAP X5). Reine Wertsubstitution
 * und Arithmetik ueber die geschlossene {@link ScriptOp}/{@link ScriptDataSource}-
 * Menge -- kein {@code eval}, kein dynamischer Methodenaufruf, kein Zugriff
 * auf irgendetwas ausserhalb von {@link ScriptContext} (keine Reflection,
 * kein IO, kein Netz: es gibt in diesem Package schlicht keine Operation
 * dafuer).
 *
 * <p><b>Zwei harte Grenzen je {@code execute(...)}-Aufruf</b> (Aufgabe X5,
 * "Budget je Tick"):</p>
 * <ul>
 *   <li><b>Schritt-Budget</b> ({@link #DEFAULT_STEP_BUDGET}): jeder ausgewertete
 *       Knoten (auch jede Wiederholung eines {@link ScriptExpr.Repeat}-Rumpfs)
 *       zaehlt einen Schritt. Ist das Budget aufgebraucht, bricht
 *       {@link ScriptBudgetExceededException} sauber ab -- eine Endlosschleife
 *       ({@code repeat} mit riesigem/unbegrenztem Zaehler) laeuft nie laenger
 *       als das Budget erlaubt.</li>
 *   <li><b>Schachtelungstiefe</b> ({@link #MAX_CALL_DEPTH}): jede rekursive
 *       {@code eval(...)}-Ebene zaehlt. Ohne diese Grenze koennte ein absichtlich
 *       (z. B. direkt ueber die Konstruktoren von {@link ScriptExpr} statt
 *       ueber {@link ScriptParser}) sehr tief gebauter Baum einen
 *       {@code StackOverflowError} ausloesen; stattdessen kommt eine
 *       kontrollierte {@link ScriptBudgetExceededException}. {@link ScriptParser}
 *       verhindert das schon beim Bauen des Baums (siehe
 *       {@link ScriptParser#MAX_NESTING_DEPTH}) -- diese zweite Pruefung ist
 *       Verteidigung in der Tiefe fuer jeden {@link ScriptExpr}, der den Parser
 *       nicht durchlaufen hat.</li>
 * </ul>
 *
 * <p>Jede interne Ausnahme (Typfehler, Division durch null, ...) wird als
 * geprueftes {@link ScriptExecutionException} an den Aufrufer gereicht --
 * niemals eine unkontrollierte {@code RuntimeException} oder ein
 * {@code Error}. Ein kaputtes oder boesartiges Skript kann so nie den Client
 * mitreissen (Charter-Regel 2, Aufgabe X5 Punkt 4).</p>
 */
public final class ScriptInterpreter {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Script");

	/** Default-Obergrenze ausgewerteter Knoten je {@code execute(...)}-Aufruf. */
	public static final long DEFAULT_STEP_BUDGET = 100_000L;

	/** Maximale rekursive Auswertungstiefe -- weit unterhalb jedes JVM-Stacklimits. */
	public static final int MAX_CALL_DEPTH = 128;

	private ScriptInterpreter() {
	}

	/** Wie {@link #execute(ScriptExpr, ScriptContext, long)} mit {@link #DEFAULT_STEP_BUDGET}. */
	public static ScriptValue execute(ScriptExpr expr, ScriptContext context) throws ScriptExecutionException {
		return execute(expr, context, DEFAULT_STEP_BUDGET);
	}

	/**
	 * Wertet {@code expr} gegen {@code context} aus.
	 *
	 * @param stepBudget maximale Anzahl ausgewerteter Knoten, bevor
	 *                   {@link ScriptBudgetExceededException} geworfen wird.
	 * @throws ScriptExecutionException bei Typfehlern, Division durch null,
	 *                                  ueberschrittenem Schritt-Budget oder
	 *                                  ueberschrittener Schachtelungstiefe.
	 */
	public static ScriptValue execute(ScriptExpr expr, ScriptContext context, long stepBudget)
			throws ScriptExecutionException {
		Budget budget = new Budget(stepBudget);
		try {
			return eval(expr, context, budget, 0);
		} catch (ScriptExecutionException e) {
			LOGGER.debug("Skript-Ausfuehrung abgebrochen: {}", e.getMessage());
			throw e;
		} catch (StackOverflowError e) {
			// Letzte Verteidigungslinie, falls die Tiefenpruefung selbst einen
			// Fehler haette (siehe Klassendoku): nie den Client mitreissen.
			LOGGER.error("Skript-Interpreter hat trotz Tiefenpruefung den Stack ueberlaufen lassen", e);
			throw new ScriptExecutionException("Skript zu tief verschachtelt (Stack-Grenze erreicht)", e);
		}
	}

	private static ScriptValue eval(ScriptExpr expr, ScriptContext context, Budget budget, int depth)
			throws ScriptExecutionException {
		if (depth > MAX_CALL_DEPTH) {
			throw new ScriptBudgetExceededException(
					"Skript ist zu tief verschachtelt (Grenze " + MAX_CALL_DEPTH + " Ebenen)");
		}
		budget.consumeOne();

		return switch (expr) {
			case ScriptExpr.Literal literal -> literal.value();
			case ScriptExpr.DataRef ref -> readDataSource(ref.source(), context);
			case ScriptExpr.UnaryOp unary -> evalUnary(unary, context, budget, depth);
			case ScriptExpr.BinaryOp binary -> evalBinary(binary, context, budget, depth);
			case ScriptExpr.Conditional conditional -> eval(conditional.condition(), context, budget, depth + 1)
					.asBoolean()
					? eval(conditional.whenTrue(), context, budget, depth + 1)
					: eval(conditional.whenFalse(), context, budget, depth + 1);
			case ScriptExpr.Repeat repeat -> evalRepeat(repeat, context, budget, depth);
		};
	}

	private static ScriptValue evalUnary(ScriptExpr.UnaryOp unary, ScriptContext context, Budget budget, int depth)
			throws ScriptExecutionException {
		ScriptValue operand = eval(unary.operand(), context, budget, depth + 1);
		if (unary.op() != ScriptOp.NOT) {
			throw new ScriptExecutionException("unbekannte einstellige Operation '" + unary.op().id() + "'");
		}
		return new ScriptValue.Bool(!operand.asBoolean());
	}

	private static ScriptValue evalBinary(ScriptExpr.BinaryOp binary, ScriptContext context, Budget budget, int depth)
			throws ScriptExecutionException {
		ScriptOp op = binary.op();
		// Kurzschluss fuer 'and'/'or': der rechte Zweig wird nur ausgewertet,
		// wenn er das Ergebnis noch aendern kann -- reduziert nebenbei den
		// Schrittverbrauch bei ungenutzten Zweigen.
		if (op == ScriptOp.AND) {
			boolean left = eval(binary.left(), context, budget, depth + 1).asBoolean();
			return new ScriptValue.Bool(left && eval(binary.right(), context, budget, depth + 1).asBoolean());
		}
		if (op == ScriptOp.OR) {
			boolean left = eval(binary.left(), context, budget, depth + 1).asBoolean();
			return new ScriptValue.Bool(left || eval(binary.right(), context, budget, depth + 1).asBoolean());
		}

		ScriptValue left = eval(binary.left(), context, budget, depth + 1);
		ScriptValue right = eval(binary.right(), context, budget, depth + 1);
		return switch (op) {
			case ADD -> new ScriptValue.Number(left.asNumber() + right.asNumber());
			case SUB -> new ScriptValue.Number(left.asNumber() - right.asNumber());
			case MUL -> new ScriptValue.Number(left.asNumber() * right.asNumber());
			case DIV -> divide(left.asNumber(), right.asNumber());
			case MOD -> modulo(left.asNumber(), right.asNumber());
			case EQ -> new ScriptValue.Bool(valuesEqual(left, right));
			case NEQ -> new ScriptValue.Bool(!valuesEqual(left, right));
			case LT -> new ScriptValue.Bool(left.asNumber() < right.asNumber());
			case LTE -> new ScriptValue.Bool(left.asNumber() <= right.asNumber());
			case GT -> new ScriptValue.Bool(left.asNumber() > right.asNumber());
			case GTE -> new ScriptValue.Bool(left.asNumber() >= right.asNumber());
			case AND, OR -> throw new IllegalStateException("bereits oben mit Kurzschluss behandelt");
			case NOT -> throw new ScriptExecutionException("'not' ist einstellig, nicht zweistellig");
		};
	}

	private static ScriptValue evalRepeat(ScriptExpr.Repeat repeat, ScriptContext context, Budget budget, int depth)
			throws ScriptExecutionException {
		ScriptValue last = new ScriptValue.Number(0);
		for (long i = 0; i < repeat.count(); i++) {
			last = eval(repeat.body(), context, budget, depth + 1);
		}
		return last;
	}

	private static ScriptValue readDataSource(ScriptDataSource source, ScriptContext context) {
		return switch (source) {
			case INSTALLED_PACKAGE_COUNT -> new ScriptValue.Number(context.installedPackageCount());
			case ACTIVE_PACKAGE_COUNT -> new ScriptValue.Number(context.activePackageCount());
			case ACTIVE_THEME_ID -> new ScriptValue.Text(context.activeThemeId());
			case FPS -> new ScriptValue.Number(context.fps());
			case COORDINATES_X -> new ScriptValue.Number(context.playerX());
			case COORDINATES_Y -> new ScriptValue.Number(context.playerY());
			case COORDINATES_Z -> new ScriptValue.Number(context.playerZ());
			case BIOME -> new ScriptValue.Text(context.biomeId());
			case TIME -> new ScriptValue.Text(formatClock(context.dayTimeTicks()));
		};
	}

	/**
	 * Rechnet die Welt-Tageszeit (Ticks, 0 = Sonnenaufgang 06:00, 24000 Ticks
	 * pro Minecraft-Tag) in eine {@code HH:MM}-Uhrzeit um -- dieselbe
	 * Umrechnung wie {@code de.spassglas.vitrine.hud.HudDataResolver#formatClock}
	 * (ROADMAP T5), hier absichtlich dupliziert statt importiert: dieses
	 * Sandbox-Package bleibt damit frei von einer Abhaengigkeit auf das
	 * {@code hud}-Package (siehe {@link ScriptContext}-Klassendoku).
	 */
	private static String formatClock(long dayTimeTicks) {
		long normalized = ((dayTimeTicks % 24000L) + 24000L) % 24000L;
		long totalMinutes = (long) Math.floor(normalized / 1000.0 * 60.0) + 6L * 60L;
		totalMinutes %= 24L * 60L;
		long hours = totalMinutes / 60L;
		long minutes = totalMinutes % 60L;
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	private static ScriptValue divide(double left, double right) throws ScriptExecutionException {
		if (right == 0.0) {
			throw new ScriptExecutionException("Division durch null (" + left + " / " + right + ")");
		}
		return new ScriptValue.Number(left / right);
	}

	private static ScriptValue modulo(double left, double right) throws ScriptExecutionException {
		if (right == 0.0) {
			throw new ScriptExecutionException("Modulo durch null (" + left + " % " + right + ")");
		}
		return new ScriptValue.Number(left % right);
	}

	private static boolean valuesEqual(ScriptValue left, ScriptValue right) {
		return left.equals(right);
	}

	/** Zaehlt ausgewertete Knoten innerhalb eines {@code execute(...)}-Aufrufs. */
	private static final class Budget {
		private final long limit;
		private long consumed;

		Budget(long limit) {
			this.limit = limit;
		}

		void consumeOne() throws ScriptBudgetExceededException {
			consumed++;
			if (consumed > limit) {
				throw new ScriptBudgetExceededException(
						"Skript-Budget ueberschritten (" + limit + " Schritte) -- moegliche Endlosschleife gestoppt");
			}
		}
	}
}
