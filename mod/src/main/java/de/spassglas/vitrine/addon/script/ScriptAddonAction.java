package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptExpr;

/**
 * Die feste Whitelist der Wirkungen, die ein Addon-Skript ausloesen darf
 * (ROADMAP AD2, "Vitrine-API: Nachricht/Toast zeigen, Ton spielen, HUD-Wert
 * setzen"). Genauso geschlossen wie {@link de.spassglas.vitrine.addon.AddonRule.Action.Kind}
 * -- {@link ScriptAddonParser} laesst keine vierte Art zu.
 *
 * <p>Jede Nutzlast, die einen Wert <em>berechnet</em> ({@code text}, {@code
 * wert}), ist ein {@link ScriptExpr}-Knoten aus der X5-Sandbox -- ausgewertet
 * von {@link ScriptAddonExecutor} ueber {@code ScriptInterpreter.execute}, nie
 * ein roher Java-Ausdruck. {@code soundId} ist dagegen bewusst ein einfacher
 * Text (keine Formel): die Aufloesung passiert ausschliesslich ueber die
 * Vanilla-Registry ({@code Registries.SOUND_EVENT}), nie per Reflection.</p>
 */
public sealed interface ScriptAddonAction {

	/** Zeigt {@code text} (ausgewertet, dann als Anzeigetext formatiert) als Nachricht an. */
	record ZeigeNachricht(ScriptExpr text) implements ScriptAddonAction {
	}

	/** Spielt den Sound mit dieser Id (z. B. {@code "minecraft:entity.experience_orb.pickup"}) ab. */
	record SpieleTon(String soundId) implements ScriptAddonAction {
	}

	/** Setzt den Addon-HUD-Wert unter {@code schluessel} auf den ausgewerteten {@code wert}. */
	record SetzeHudWert(String schluessel, ScriptExpr wert) implements ScriptAddonAction {
	}
}
