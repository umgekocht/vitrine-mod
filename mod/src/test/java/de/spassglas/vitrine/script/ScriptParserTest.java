package de.spassglas.vitrine.script;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/** Deckt die Grammatik-Pruefung von {@link ScriptParser} ab (ROADMAP X5). */
public final class ScriptParserTest {

	@Test
	public void parsesLiteralNumberTextAndBool() throws ScriptParseException {
		assertEquals(ScriptParser.parse("{\"op\":\"lit\",\"value\":3}"),
				new ScriptExpr.Literal(new ScriptValue.Number(3)));
		assertEquals(ScriptParser.parse("{\"op\":\"lit\",\"value\":\"hallo\"}"),
				new ScriptExpr.Literal(new ScriptValue.Text("hallo")));
		assertEquals(ScriptParser.parse("{\"op\":\"lit\",\"value\":true}"),
				new ScriptExpr.Literal(new ScriptValue.Bool(true)));
	}

	@Test
	public void parsesKnownDataSource() throws ScriptParseException {
		ScriptExpr parsed = ScriptParser.parse("{\"op\":\"data\",\"source\":\"installedPackageCount\"}");
		assertEquals(parsed, new ScriptExpr.DataRef(ScriptDataSource.INSTALLED_PACKAGE_COUNT));
	}

	@Test
	public void parsesHudLikeDataSource() throws ScriptParseException {
		// ROADMAP AD2: dieselben Bezeichner wie HudDataSource sind auch hier gueltig.
		assertEquals(ScriptParser.parse("{\"op\":\"data\",\"source\":\"coordinates.x\"}"),
				new ScriptExpr.DataRef(ScriptDataSource.COORDINATES_X));
		assertEquals(ScriptParser.parse("{\"op\":\"data\",\"source\":\"fps\"}"),
				new ScriptExpr.DataRef(ScriptDataSource.FPS));
	}

	@Test
	public void parsesBinaryAndUnaryAndConditionalAndRepeat() throws ScriptParseException {
		ScriptExpr add = ScriptParser.parse(
				"{\"op\":\"add\",\"left\":{\"op\":\"lit\",\"value\":1},\"right\":{\"op\":\"lit\",\"value\":2}}");
		assertEquals(add, new ScriptExpr.BinaryOp(ScriptOp.ADD,
				new ScriptExpr.Literal(new ScriptValue.Number(1)), new ScriptExpr.Literal(new ScriptValue.Number(2))));

		ScriptExpr not = ScriptParser.parse("{\"op\":\"not\",\"operand\":{\"op\":\"lit\",\"value\":true}}");
		assertEquals(not, new ScriptExpr.UnaryOp(ScriptOp.NOT, new ScriptExpr.Literal(new ScriptValue.Bool(true))));

		ScriptExpr conditional = ScriptParser.parse("{\"op\":\"if\",\"condition\":{\"op\":\"lit\",\"value\":true},"
				+ "\"then\":{\"op\":\"lit\",\"value\":1},\"else\":{\"op\":\"lit\",\"value\":0}}");
		assertEquals(conditional, new ScriptExpr.Conditional(
				new ScriptExpr.Literal(new ScriptValue.Bool(true)),
				new ScriptExpr.Literal(new ScriptValue.Number(1)),
				new ScriptExpr.Literal(new ScriptValue.Number(0))));

		ScriptExpr repeat = ScriptParser.parse("{\"op\":\"repeat\",\"count\":5,\"body\":{\"op\":\"lit\",\"value\":1}}");
		assertEquals(repeat, new ScriptExpr.Repeat(5, new ScriptExpr.Literal(new ScriptValue.Number(1))));
	}

	@Test
	public void rejectsMalformedJson() {
		ScriptParseException e = expectThrows(ScriptParseException.class, () -> ScriptParser.parse("{not json"));
		assertTrue(e.getMessage().contains("kein gueltiges JSON"), e.getMessage());
	}

	@Test
	public void rejectsMissingOpField() {
		expectThrows(ScriptParseException.class, () -> ScriptParser.parse("{}"));
	}

	@Test
	public void rejectsUnknownOperation() {
		ScriptParseException e = expectThrows(ScriptParseException.class,
				() -> ScriptParser.parse("{\"op\":\"readFile\",\"path\":\"config/vitrine/settings.json\"}"));
		assertTrue(e.getMessage().contains("unbekannte Operation 'readFile'"), e.getMessage());
	}

	@Test
	public void rejectsUnknownDataSource() {
		ScriptParseException e = expectThrows(ScriptParseException.class,
				() -> ScriptParser.parse("{\"op\":\"data\",\"source\":\"java.lang.Runtime\"}"));
		assertTrue(e.getMessage().contains("unbekannte Datenquelle"), e.getMessage());
	}

	@Test
	public void rejectsNegativeRepeatCount() {
		expectThrows(ScriptParseException.class,
				() -> ScriptParser.parse("{\"op\":\"repeat\",\"count\":-1,\"body\":{\"op\":\"lit\",\"value\":1}}"));
	}

	@Test
	public void rejectsRepeatCountAboveDeclaredCeiling() {
		long tooLarge = ScriptParser.MAX_DECLARED_REPEAT_COUNT + 1;
		expectThrows(ScriptParseException.class, () -> ScriptParser.parse(
				"{\"op\":\"repeat\",\"count\":" + tooLarge + ",\"body\":{\"op\":\"lit\",\"value\":1}}"));
	}

	@Test
	public void rejectsMissingChildField() {
		expectThrows(ScriptParseException.class,
				() -> ScriptParser.parse("{\"op\":\"add\",\"left\":{\"op\":\"lit\",\"value\":1}}"));
	}

	@Test
	public void rejectsExcessiveNestingDepth() {
		String script = buildNestedNot(ScriptParser.MAX_NESTING_DEPTH + 10);
		ScriptParseException e = expectThrows(ScriptParseException.class, () -> ScriptParser.parse(script));
		assertTrue(e.getMessage().contains("verschachtelt"), e.getMessage());
	}

	@Test
	public void acceptsNestingRightAtTheLimit() throws ScriptParseException {
		// Tiefe 0 ist der Wurzelknoten selbst, also MAX_NESTING_DEPTH weitere Ebenen sind noch erlaubt.
		String script = buildNestedNot(ScriptParser.MAX_NESTING_DEPTH);
		ScriptParser.parse(script);
	}

	private static String buildNestedNot(int depth) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			builder.append("{\"op\":\"not\",\"operand\":");
		}
		builder.append("{\"op\":\"lit\",\"value\":true}");
		builder.append("}".repeat(depth));
		return builder.toString();
	}
}
