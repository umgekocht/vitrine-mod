package de.spassglas.vitrine.script;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das Fertig-Kriterium von ROADMAP X5 woertlich: "Ausbruchsversuche
 * in Tests (IO, Reflection, Endlosschleife) werden alle gestoppt." Jede
 * Methode hier ist ein eigener Ausbruchsversuch.
 */
public final class ScriptSandboxEscapeTest {

	private final ScriptContext context = ScriptTestContext.DEFAULT;

	/**
	 * IO-Ausbruch: ein Skript, das versucht eine Datei zu lesen/schreiben,
	 * hat dafuer keinen Bezeichner in der Grammatik -- der Parser lehnt es ab,
	 * bevor irgendetwas ausgefuehrt wird.
	 */
	@Test
	public void ioAttemptIsRejectedAtParseTimeNotExecuted() {
		String[] ioAttempts = {
				"{\"op\":\"readFile\",\"path\":\"config/vitrine/settings.json\"}",
				"{\"op\":\"writeFile\",\"path\":\"../../evil.txt\",\"content\":\"pwned\"}",
				"{\"op\":\"httpGet\",\"url\":\"https://example.invalid/exfiltrate\"}",
				"{\"op\":\"openSocket\",\"host\":\"example.invalid\",\"port\":9999}",
		};
		for (String attempt : ioAttempts) {
			ScriptParseException e = expectThrows(ScriptParseException.class, () -> ScriptParser.parse(attempt));
			assertTrue(e.getMessage().contains("unbekannte Operation"),
					"IO-Ausbruch haette als unbekannte Operation abgelehnt werden muessen: " + attempt);
		}
	}

	/**
	 * Reflection-Ausbruch: ein Skript, das per Zeichenkette eine Java-Klasse
	 * oder Methode referenziert, wird nicht dynamisch aufgeloest. Zwei Faelle:
	 * als "Datenquelle" wird die Zeichenkette gegen die geschlossene
	 * {@link ScriptDataSource}-Whitelist geprueft und abgelehnt; als
	 * {@code lit}-Text-Literal bleibt sie ein voellig harmloser Datenwert, der
	 * nie zu einer Klasse/Methode aufgeloest wird.
	 */
	@Test
	public void reflectionAttemptViaDataSourceIsRejectedAtParseTime() {
		String[] reflectionAttempts = {
				"{\"op\":\"data\",\"source\":\"java.lang.Runtime\"}",
				"{\"op\":\"data\",\"source\":\"java.lang.Class.forName\"}",
				"{\"op\":\"data\",\"source\":\"getClass\"}",
				"{\"op\":\"invoke\",\"class\":\"java.lang.Runtime\",\"method\":\"exec\"}",
		};
		for (String attempt : reflectionAttempts) {
			expectThrows(ScriptParseException.class, () -> ScriptParser.parse(attempt));
		}
	}

	@Test
	public void reflectionAttemptViaStringLiteralStaysInertData() throws ScriptParseException, ScriptExecutionException {
		// Ein Klassenname als reiner Text-Literal ist erlaubt -- er wird
		// niemals als Klassenreferenz interpretiert, nur als Text-Wert.
		ScriptExpr expr = ScriptParser.parse("{\"op\":\"lit\",\"value\":\"java.lang.Runtime\"}");
		ScriptValue result = ScriptInterpreter.execute(expr, context);
		assertEquals(result, new ScriptValue.Text("java.lang.Runtime"));
	}

	/**
	 * Endlosschleifen-Ausbruch: ein {@code repeat} mit einer riesigen Anzahl
	 * wird durch das Schritt-Budget nach endlich vielen Schritten gestoppt --
	 * gemessen daran, dass die Ausfuehrung in kurzer Zeit mit einer klaren
	 * Ausnahme abbricht statt zu haengen.
	 */
	@Test
	public void hugeRepeatIsStoppedByStepBudget() throws ScriptParseException {
		ScriptExpr expr = ScriptParser.parse(
				"{\"op\":\"repeat\",\"count\":" + ScriptParser.MAX_DECLARED_REPEAT_COUNT
						+ ",\"body\":{\"op\":\"add\",\"left\":{\"op\":\"lit\",\"value\":1},"
						+ "\"right\":{\"op\":\"lit\",\"value\":1}}}");

		long start = System.nanoTime();
		ScriptBudgetExceededException e = expectThrows(ScriptBudgetExceededException.class,
				() -> ScriptInterpreter.execute(expr, context, 10_000L));
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

		assertTrue(e.getMessage().contains("Budget"), e.getMessage());
		assertTrue(elapsedMillis < 2_000L,
				"Budget-Abbruch haette sofort kommen muessen, brauchte aber " + elapsedMillis + " ms");
	}

	/**
	 * Endlosschleifen-Ausbruch, Variante Rekursion: ein absichtlich sehr tief
	 * verschachtelter Baum (direkt ueber die Konstruktoren gebaut, nicht ueber
	 * {@link ScriptParser} -- der wuerde ihn schon frueher ablehnen) darf den
	 * Interpreter niemals per {@code StackOverflowError} zum Absturz bringen.
	 * Statt eines Stack-Overflows kommt eine kontrollierte
	 * {@link ScriptBudgetExceededException}.
	 */
	@Test
	public void deeplyNestedExpressionIsStoppedNotStackOverflow() {
		ScriptExpr expr = new ScriptExpr.Literal(new ScriptValue.Bool(true));
		int depth = ScriptInterpreter.MAX_CALL_DEPTH * 10;
		for (int i = 0; i < depth; i++) {
			expr = new ScriptExpr.UnaryOp(ScriptOp.NOT, expr);
		}
		ScriptExpr deeplyNested = expr;

		long start = System.nanoTime();
		ScriptExecutionException e = expectThrows(ScriptExecutionException.class,
				() -> ScriptInterpreter.execute(deeplyNested, context));
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

		assertTrue(e.getMessage().contains("verschachtelt") || e.getMessage().contains("Stack"), e.getMessage());
		assertTrue(elapsedMillis < 2_000L,
				"Tiefenabbruch haette sofort kommen muessen, brauchte aber " + elapsedMillis + " ms");
	}

	/**
	 * Dieselbe extreme Verschachtelung, aber schon als Skript-Text -- beweist,
	 * dass bereits {@link ScriptParser} solche Eingaben abweist und niemals
	 * versucht, sie erst zu bauen und dann auszuwerten.
	 */
	@Test
	public void deeplyNestedScriptTextIsRejectedByParser() {
		StringBuilder builder = new StringBuilder();
		int depth = ScriptParser.MAX_NESTING_DEPTH * 5;
		for (int i = 0; i < depth; i++) {
			builder.append("{\"op\":\"not\",\"operand\":");
		}
		builder.append("{\"op\":\"lit\",\"value\":true}");
		builder.append("}".repeat(depth));
		String script = builder.toString();

		expectThrows(ScriptParseException.class, () -> ScriptParser.parse(script));
	}
}
