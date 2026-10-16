package de.spassglas.vitrine.ui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Registriert das Tastenkuerzel, mit dem der Store geoeffnet wird (ROADMAP
 * S0: "Tastenkuerzel M"). {@link #CATEGORY_TRANSLATION_KEY} und
 * {@link #OPEN_STORE_TRANSLATION_KEY} sind in beiden Sprachdateien
 * (lang/en_us.json, lang/de_de.json) hinterlegt, damit die Tastenbelegungs-
 * Oberflaeche von Minecraft selbst einen lesbaren Namen zeigt.
 */
public final class VitrineKeyBindings {

	public static final String CATEGORY_TRANSLATION_KEY = "key.categories.vitrine";
	public static final String OPEN_STORE_TRANSLATION_KEY = "key.vitrine.open_store";

	private static KeyBinding openStore;

	private VitrineKeyBindings() {
	}

	/** Registriert die Tastenbelegung bei Fabric; darf nur einmal beim Mod-Start laufen. */
	public static KeyBinding registerOpenStore() {
		if (openStore == null) {
			openStore = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					OPEN_STORE_TRANSLATION_KEY,
					InputUtil.Type.KEYSYM,
					GLFW.GLFW_KEY_M,
					CATEGORY_TRANSLATION_KEY));
		}
		return openStore;
	}

	/** Die registrierte Tastenbelegung, oder {@code null} vor {@link #registerOpenStore()}. */
	public static KeyBinding openStore() {
		return openStore;
	}
}
