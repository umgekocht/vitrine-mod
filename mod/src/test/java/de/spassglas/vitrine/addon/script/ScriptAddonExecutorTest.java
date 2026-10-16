package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptContext;
import de.spassglas.vitrine.script.ScriptExpr;
import de.spassglas.vitrine.script.ScriptOp;
import de.spassglas.vitrine.script.ScriptParser;
import de.spassglas.vitrine.script.ScriptValue;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt {@link ScriptAddonExecutor} ab (ROADMAP AD2): beweist, dass die drei
 * Vitrine-API-Aktionen (Nachricht, Ton, HUD-Wert) wirklich ausgefuehrt
 * werden, eine nicht erfuellte Bedingung sie ueberspringt, und ein absichtlich
 * boesartiges Skript (riesiges {@code repeat}, sehr tiefe Verschachtelung) den
 * Lauf kontrolliert abbricht statt den Client mitzureissen.
 */
public final class ScriptAddonExecutorTest {

	/** Fester {@link ScriptContext} fuer diesen Test -- keine Abhaengigkeit vom Client. */
	private record FixedScriptContext(double installedPackageCount, double activePackageCount, String activeThemeId,
			double fps, double playerX, double playerY, double playerZ, String biomeId, long dayTimeTicks)
			implements ScriptContext {
	}

	private static final ScriptContext CONTEXT =
			new FixedScriptContext(5, 3, "klarglas", 60, 10, 64, -20, "minecraft:plains", 1000);

	/** Zeichnet jeden Aufruf auf, statt tatsaechlich etwas anzuzeigen/abzuspielen. */
	private static final class RecordingRuntime implements ScriptAddonRuntime {
		final List<String> messages = new ArrayList<>();
		final List<String> sounds = new ArrayList<>();
		final List<String> hudKeys = new ArrayList<>();
		final List<ScriptValue> hudValues = new ArrayList<>();

		@Override
		public void showMessage(String text) {
			messages.add(text);
		}

		@Override
		public void playSound(String soundId) {
			sounds.add(soundId);
		}

		@Override
		public void setHudValue(String key, ScriptValue value) {
			hudKeys.add(key);
			hudValues.add(value);
		}
	}

	private static ScriptAddon script(ScriptAddonEvent event, ScriptExpr condition, List<ScriptAddonAction> actions) {
		String commandName = event == ScriptAddonEvent.BEFEHL ? "gruss" : null;
		return new ScriptAddon(event, condition, actions, commandName);
	}

	@Test
	public void runsAllThreeActionsInOrder() {
		RecordingRuntime runtime = new RecordingRuntime();
		ScriptAddon s = script(ScriptAddonEvent.WELT_BETRETEN, null, List.of(
				new ScriptAddonAction.ZeigeNachricht(new ScriptExpr.Literal(new ScriptValue.Text("Willkommen!"))),
				new ScriptAddonAction.SpieleTon("minecraft:entity.experience_orb.pickup"),
				new ScriptAddonAction.SetzeHudWert("punkte", new ScriptExpr.Literal(new ScriptValue.Number(42)))));

		ScriptAddonExecutor.run(s, CONTEXT, runtime);

		assertEquals(runtime.messages, List.of("Willkommen!"));
		assertEquals(runtime.sounds, List.of("minecraft:entity.experience_orb.pickup"));
		assertEquals(runtime.hudKeys, List.of("punkte"));
		assertEquals(runtime.hudValues, List.of(new ScriptValue.Number(42)));
	}

	@Test
	public void falseConditionSkipsAllActions() {
		RecordingRuntime runtime = new RecordingRuntime();
		ScriptAddon s = script(ScriptAddonEvent.TICK, new ScriptExpr.Literal(new ScriptValue.Bool(false)),
				List.of(new ScriptAddonAction.ZeigeNachricht(new ScriptExpr.Literal(new ScriptValue.Text("nie")))));

		ScriptAddonExecutor.run(s, CONTEXT, runtime);

		assertTrue(runtime.messages.isEmpty());
	}

	@Test
	public void trueConditionRunsActions() {
		RecordingRuntime runtime = new RecordingRuntime();
		ScriptAddon s = script(ScriptAddonEvent.TICK, new ScriptExpr.Literal(new ScriptValue.Bool(true)),
				List.of(new ScriptAddonAction.ZeigeNachricht(new ScriptExpr.Literal(new ScriptValue.Text("ja")))));

		ScriptAddonExecutor.run(s, CONTEXT, runtime);

		assertEquals(runtime.messages, List.of("ja"));
	}

	/**
	 * Ausbruchsversuch: eine riesige {@code repeat}-Wiederholung in der HUD-Wert-
	 * Formel wird durch das Schritt-Budget gestoppt -- schnell, ohne
	 * Ausnahme nach aussen, ohne dass der HUD-Wert gesetzt wird.
	 */
	@Test
	public void hugeRepeatInActionIsStoppedByStepBudget() {
		ScriptExpr hugeRepeat = new ScriptExpr.Repeat(ScriptParser.MAX_DECLARED_REPEAT_COUNT,
				new ScriptExpr.BinaryOp(ScriptOp.ADD, new ScriptExpr.Literal(new ScriptValue.Number(1)),
						new ScriptExpr.Literal(new ScriptValue.Number(1))));
		RecordingRuntime runtime = new RecordingRuntime();
		ScriptAddon s = script(ScriptAddonEvent.TICK, null,
				List.of(new ScriptAddonAction.SetzeHudWert("boese", hugeRepeat)));

		long start = System.nanoTime();
		ScriptAddonExecutor.run(s, CONTEXT, runtime);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

		assertTrue(runtime.hudKeys.isEmpty(), "Budget-Abbruch darf den HUD-Wert nicht setzen");
		assertTrue(elapsedMillis < 2_000L,
				"Budget-Abbruch haette sofort kommen muessen, brauchte aber " + elapsedMillis + " ms");
	}

	/**
	 * Ausbruchsversuch, Variante Rekursion: ein direkt ueber die Konstruktoren
	 * (nicht ueber den Parser) absichtlich sehr tief verschachtelter Ausdruck
	 * in der Bedingung darf den Executor nie per StackOverflowError abstuerzen
	 * lassen -- nur die Aktionen bleiben aus.
	 */
	@Test
	public void deeplyNestedConditionIsStoppedNotStackOverflow() {
		ScriptExpr expr = new ScriptExpr.Literal(new ScriptValue.Bool(true));
		int depth = de.spassglas.vitrine.script.ScriptInterpreter.MAX_CALL_DEPTH * 10;
		for (int i = 0; i < depth; i++) {
			expr = new ScriptExpr.UnaryOp(ScriptOp.NOT, expr);
		}
		RecordingRuntime runtime = new RecordingRuntime();
		ScriptAddon s = script(ScriptAddonEvent.TICK, expr,
				List.of(new ScriptAddonAction.ZeigeNachricht(new ScriptExpr.Literal(new ScriptValue.Text("nie")))));

		long start = System.nanoTime();
		ScriptAddonExecutor.run(s, CONTEXT, runtime);
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

		assertTrue(runtime.messages.isEmpty(), "Tiefenabbruch darf die Aktion nicht ausfuehren");
		assertTrue(elapsedMillis < 2_000L,
				"Tiefenabbruch haette sofort kommen muessen, brauchte aber " + elapsedMillis + " ms");
	}
}
