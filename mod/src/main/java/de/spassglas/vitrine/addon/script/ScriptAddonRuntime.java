package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.script.ScriptValue;

/**
 * Die drei Seiteneffekte, die {@link ScriptAddonExecutor} fuer
 * {@link ScriptAddonAction} ausloesen darf (ROADMAP AD2, "Vitrine-API").
 * Getrennt von der echten Minecraft-Umsetzung
 * ({@code de.spassglas.vitrine.addon.script.client.MinecraftScriptAddonRuntime}),
 * damit der Executor selbst ohne laufenden Client/{@code MinecraftClient}
 * getestet werden kann -- dasselbe Muster wie
 * {@code de.spassglas.vitrine.hotapply.HotApplyToastPresenter}.
 */
public interface ScriptAddonRuntime {

	/** Zeigt {@code text} dem Spieler an (Nachricht/Toast, ROADMAP AD2). */
	void showMessage(String text);

	/** Spielt den Sound mit dieser Id ab, oder tut nichts (mit Log-Eintrag), wenn sie unbekannt ist. */
	void playSound(String soundId);

	/** Setzt den Addon-HUD-Wert unter {@code key} auf {@code value}. */
	void setHudValue(String key, ScriptValue value);
}
