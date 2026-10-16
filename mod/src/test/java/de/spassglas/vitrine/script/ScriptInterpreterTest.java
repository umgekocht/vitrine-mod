package de.spassglas.vitrine.script;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Deckt die Auswertung von {@link ScriptInterpreter} ab (ROADMAP X5). */
public final class ScriptInterpreterTest {

	private final ScriptContext context = ScriptTestContext.DEFAULT;

	@Test
	public void evaluatesArithmetic() throws ScriptExecutionException {
		ScriptExpr expr = new ScriptExpr.BinaryOp(ScriptOp.ADD,
				new ScriptExpr.Literal(new ScriptValue.Number(2)),
				new ScriptExpr.BinaryOp(ScriptOp.MUL,
						new ScriptExpr.Literal(new ScriptValue.Number(3)),
						new ScriptExpr.Literal(new ScriptValue.Number(4))));
		assertEquals(ScriptInterpreter.execute(expr, context), new ScriptValue.Number(14));
	}

	@Test
	public void readsDataSourcesFromContext() throws ScriptExecutionException {
		assertEquals(
				ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.INSTALLED_PACKAGE_COUNT), context),
				new ScriptValue.Number(5));
		assertEquals(
				ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.ACTIVE_THEME_ID), context),
				new ScriptValue.Text("klarglas"));
	}

	@Test
	public void readsHudLikeDataSourcesFromContext() throws ScriptExecutionException {
		// ROADMAP AD2: Skripte lesen Spielwerte wie die HUD-Datenquellen.
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.FPS), context),
				new ScriptValue.Number(60));
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.COORDINATES_X), context),
				new ScriptValue.Number(10));
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.COORDINATES_Y), context),
				new ScriptValue.Number(64));
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.COORDINATES_Z), context),
				new ScriptValue.Number(-20));
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.BIOME), context),
				new ScriptValue.Text("minecraft:plains"));
		// dayTimeTicks=1000 -> 6:00 + (1000/1000*60)min = 07:00.
		assertEquals(ScriptInterpreter.execute(new ScriptExpr.DataRef(ScriptDataSource.TIME), context),
				new ScriptValue.Text("07:00"));
	}

	@Test
	public void evaluatesComparisonAndBooleanOps() throws ScriptExecutionException {
		ScriptExpr moreThanTwoPackages = new ScriptExpr.BinaryOp(ScriptOp.GT,
				new ScriptExpr.DataRef(ScriptDataSource.INSTALLED_PACKAGE_COUNT),
				new ScriptExpr.Literal(new ScriptValue.Number(2)));
		assertEquals(ScriptInterpreter.execute(moreThanTwoPackages, context), new ScriptValue.Bool(true));

		ScriptExpr negated = new ScriptExpr.UnaryOp(ScriptOp.NOT, moreThanTwoPackages);
		assertEquals(ScriptInterpreter.execute(negated, context), new ScriptValue.Bool(false));

		ScriptExpr both = new ScriptExpr.BinaryOp(ScriptOp.AND, moreThanTwoPackages,
				new ScriptExpr.Literal(new ScriptValue.Bool(true)));
		assertEquals(ScriptInterpreter.execute(both, context), new ScriptValue.Bool(true));
	}

	@Test
	public void conditionalEvaluatesOnlyTheChosenBranch() throws ScriptExecutionException {
		// 'else' referenziert eine unbekannte Datenquelle waere ein Parse-Fehler; stattdessen
		// pruefen wir mit einer Division durch null, die nur im nicht gewaehlten Zweig steht.
		ScriptExpr divisionByZero = new ScriptExpr.BinaryOp(ScriptOp.DIV,
				new ScriptExpr.Literal(new ScriptValue.Number(1)), new ScriptExpr.Literal(new ScriptValue.Number(0)));
		ScriptExpr conditional = new ScriptExpr.Conditional(
				new ScriptExpr.Literal(new ScriptValue.Bool(true)),
				new ScriptExpr.Literal(new ScriptValue.Number(42)),
				divisionByZero);
		assertEquals(ScriptInterpreter.execute(conditional, context), new ScriptValue.Number(42));
	}

	@Test
	public void repeatReturnsLastValueOfBody() throws ScriptExecutionException {
		ScriptExpr expr = new ScriptExpr.Repeat(3, new ScriptExpr.Literal(new ScriptValue.Number(7)));
		assertEquals(ScriptInterpreter.execute(expr, context), new ScriptValue.Number(7));
	}

	@Test
	public void repeatWithZeroCountReturnsNeutralDefault() throws ScriptExecutionException {
		ScriptExpr expr = new ScriptExpr.Repeat(0, new ScriptExpr.Literal(new ScriptValue.Number(99)));
		assertEquals(ScriptInterpreter.execute(expr, context), new ScriptValue.Number(0));
	}

	@Test
	public void divisionByZeroFailsCleanly() {
		ScriptExpr expr = new ScriptExpr.BinaryOp(ScriptOp.DIV,
				new ScriptExpr.Literal(new ScriptValue.Number(1)), new ScriptExpr.Literal(new ScriptValue.Number(0)));
		ScriptExecutionException e = expectThrows(ScriptExecutionException.class,
				() -> ScriptInterpreter.execute(expr, context));
		assertTrue(e.getMessage().contains("Division durch null"), e.getMessage());
	}

	@Test
	public void typeMismatchFailsCleanlyInsteadOfClassCastException() {
		ScriptExpr expr = new ScriptExpr.BinaryOp(ScriptOp.ADD,
				new ScriptExpr.Literal(new ScriptValue.Text("nicht-eine-zahl")),
				new ScriptExpr.Literal(new ScriptValue.Number(1)));
		ScriptExecutionException e = expectThrows(ScriptExecutionException.class,
				() -> ScriptInterpreter.execute(expr, context));
		assertTrue(e.getMessage().contains("erwartete eine Zahl"), e.getMessage());
	}
}
