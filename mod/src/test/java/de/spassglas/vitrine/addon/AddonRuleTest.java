package de.spassglas.vitrine.addon;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class AddonRuleTest {

	private static byte[] json(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	public void parsesRulesAndClampsAmounts() {
		List<AddonRule> rules = AddonRule.parse(json("{\"regeln\":[{\"wenn\":\"block_abgebaut\",\"ziel\":\"minecraft:stone\","
				+ "\"chance\":0.5,\"dann\":[{\"geben\":\"minecraft:dirt\",\"anzahl\":999},{\"effekt\":\"minecraft:speed\","
				+ "\"sekunden\":99999,\"stufe\":50}]}]}"));
		assertEquals(rules.size(), 1);
		assertEquals(rules.get(0).target(), "minecraft:stone");
		assertEquals(rules.get(0).actions().get(0).amount(), 64);
		assertEquals(rules.get(0).actions().get(1).amount(), 600);
		assertEquals(rules.get(0).actions().get(1).level(), 5);
	}

	@Test
	public void rejectsForbiddenCommand() {
		assertThrows(IllegalArgumentException.class, () -> AddonRule.parse(json(
				"{\"regeln\":[{\"wenn\":\"timer\",\"dann\":[{\"befehl\":\"/op @a\"}]}]}")));
	}

	@Test
	public void rejectsUnknownEvent() {
		assertThrows(IllegalArgumentException.class, () -> AddonRule.parse(json(
				"{\"regeln\":[{\"wenn\":\"system_exec\",\"dann\":[{\"xp\":1}]}]}")));
	}
}
