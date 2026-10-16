package de.spassglas.vitrine.addon.script;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Deckt {@link ScriptAddonParser} ab (ROADMAP AD2) -- u.a. das Fertig-Kriterium
 * "Ausbruchsversuche gestoppt" fuer den Parse-Schritt: ein unbekanntes
 * Ereignis/eine unbekannte Aktion/kaputtes JSON wird mit klarer Meldung
 * abgelehnt, nie ausgefuehrt.
 */
public final class ScriptAddonParserTest {

	private static byte[] json(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	public void parsesMinimalWeltBetretenScriptWithoutCondition() throws ScriptAddonParseException {
		ScriptAddon script = ScriptAddonParser.parse(json("""
				{
				  "ereignis": "welt_betreten",
				  "aktionen": [ { "nachricht": { "op": "lit", "value": "Willkommen!" } } ]
				}
				"""));
		assertEquals(script.event(), ScriptAddonEvent.WELT_BETRETEN);
		assertNull(script.condition());
		assertEquals(script.actions().size(), 1);
		assertTrue(script.actions().get(0) instanceof ScriptAddonAction.ZeigeNachricht);
		assertNull(script.commandName());
	}

	@Test
	public void parsesFullScriptWithConditionSoundAndHudValue() throws ScriptAddonParseException {
		ScriptAddon script = ScriptAddonParser.parse(json("""
				{
				  "ereignis": "befehl",
				  "befehl": "gruss",
				  "bedingung": { "op": "gt", "left": { "op": "data", "source": "installedPackageCount" }, \
"right": { "op": "lit", "value": 0 } },
				  "aktionen": [
				    { "ton": "minecraft:entity.experience_orb.pickup" },
				    { "hud_wert": { "schluessel": "punkte", "wert": { "op": "lit", "value": 1 } } }
				  ]
				}
				"""));
		assertEquals(script.event(), ScriptAddonEvent.BEFEHL);
		assertEquals(script.commandName(), "gruss");
		assertEquals(script.actions().size(), 2);
		assertTrue(script.actions().get(0) instanceof ScriptAddonAction.SpieleTon);
		assertTrue(script.actions().get(1) instanceof ScriptAddonAction.SetzeHudWert);
	}

	@Test
	public void rejectsMalformedJson() {
		ScriptAddonParseException e = expectThrows(ScriptAddonParseException.class,
				() -> ScriptAddonParser.parse(json("{not json")));
		assertTrue(e.getMessage().contains("kein gueltiges JSON"), e.getMessage());
	}

	@Test
	public void rejectsUnknownEvent() {
		ScriptAddonParseException e = expectThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"system_exec\",\"aktionen\":[{\"ton\":\"minecraft:entity.experience_orb.pickup\"}]}")));
		assertTrue(e.getMessage().contains("unbekanntes Ereignis"), e.getMessage());
	}

	@Test
	public void rejectsUnknownAction() {
		ScriptAddonParseException e = expectThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"tick\",\"aktionen\":[{\"readFile\":\"config/vitrine/settings.json\"}]}")));
		assertTrue(e.getMessage().contains("unbekannte Aktion"), e.getMessage());
	}

	@Test
	public void rejectsUnknownDataSourceInsideCondition() {
		ScriptAddonParseException e = expectThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"tick\",\"bedingung\":{\"op\":\"data\",\"source\":\"java.lang.Runtime\"},"
						+ "\"aktionen\":[{\"ton\":\"minecraft:entity.experience_orb.pickup\"}]}")));
		assertTrue(e.getMessage().contains("unbekannte Datenquelle"), e.getMessage());
	}

	@Test
	public void rejectsCommandEventWithoutCommandName() {
		assertThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"befehl\",\"aktionen\":[{\"ton\":\"minecraft:entity.experience_orb.pickup\"}]}")));
	}

	@Test
	public void rejectsCommandNameOnNonCommandEvent() {
		assertThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"tick\",\"befehl\":\"gruss\",\"aktionen\":"
						+ "[{\"ton\":\"minecraft:entity.experience_orb.pickup\"}]}")));
	}

	@Test
	public void rejectsEmptyActionsList() {
		assertThrows(ScriptAddonParseException.class,
				() -> ScriptAddonParser.parse(json("{\"ereignis\":\"tick\",\"aktionen\":[]}")));
	}

	@Test
	public void rejectsTooManyActions() {
		StringBuilder actions = new StringBuilder("[");
		for (int i = 0; i <= ScriptAddonParser.MAX_ACTIONS; i++) {
			if (i > 0) {
				actions.append(',');
			}
			actions.append("{\"ton\":\"minecraft:entity.experience_orb.pickup\"}");
		}
		actions.append(']');
		assertThrows(ScriptAddonParseException.class,
				() -> ScriptAddonParser.parse(json("{\"ereignis\":\"tick\",\"aktionen\":" + actions + "}")));
	}

	@Test
	public void rejectsInvalidHudValueKey() {
		assertThrows(ScriptAddonParseException.class, () -> ScriptAddonParser.parse(json(
				"{\"ereignis\":\"tick\",\"aktionen\":[{\"hud_wert\":{\"schluessel\":\"MIT GROSS/Sonderzeichen\","
						+ "\"wert\":{\"op\":\"lit\",\"value\":1}}}]}")));
	}
}
