package de.spassglas.vitrine.addon.script.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Die eine Vitrine-Addon-Taste fuer das Ereignis {@code taste}
 * (ROADMAP AD2). <b>Entwurfsentscheidung:</b> ein einziger, fest verdrahteter
 * Tasten-Slot statt eines pro-Addon-Satzes oder mehrerer benannter Slots --
 * jedes {@code .vs} mit {@code "ereignis": "taste"} feuert bei genau dieser
 * einen Taste. Mehr Slots (ein "taste1".."tasteN"-Schema) waeren ein
 * Konfigurationsalbtraum, ohne dass umgekocht's Backlog-Zeile ("Taste") mehr als
 * eine physische Taste verlangt; mehrere Addons mit {@code taste}-Skripten
 * feuern bei Betaetigung einfach alle gleichzeitig (wie mehrere Regeln mit
 * demselben Ereignis in {@code AddonRule}). Unbeleget per Default
 * ({@link InputUtil#UNKNOWN_KEY}) -- der Spieler weist sie erst im
 * Tastenbelegungs-Menue zu, ohne Neustart.
 */
public final class ScriptAddonKeyBinding {

	public static final String CATEGORY_TRANSLATION_KEY = "key.categories.vitrine";
	public static final String TRANSLATION_KEY = "key.vitrine.addon_action";

	private static KeyBinding instance;

	private ScriptAddonKeyBinding() {
	}

	/** Registriert die Taste bei Fabric; darf nur einmal beim Mod-Start laufen. */
	public static KeyBinding register() {
		if (instance == null) {
			instance = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					TRANSLATION_KEY, InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY_TRANSLATION_KEY));
		}
		return instance;
	}

	/** Die registrierte Tastenbelegung, oder {@code null} vor {@link #register()}. */
	public static KeyBinding instance() {
		return instance;
	}
}
