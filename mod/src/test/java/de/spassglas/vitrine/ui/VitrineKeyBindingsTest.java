package de.spassglas.vitrine.ui;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Deckt den Tastenkuerzel-Teil von ROADMAP S0 ab ("Tastenkuerzel M"), ohne
 * einen laufenden Client zu brauchen: {@link KeyBinding} ist ein reines
 * Datenobjekt, {@link VitrineKeyBindings#registerOpenStore()} selbst fasst
 * die Fabric-interne Registry an und bleibt deshalb ungetestet -- die
 * Konstanten und die Tastenzuordnung, die es benutzt, sind es nicht.
 */
public final class VitrineKeyBindingsTest {

	@Test
	public void translationKeysAreNonEmptyAndNamespaced() {
		assertEquals(VitrineKeyBindings.CATEGORY_TRANSLATION_KEY, "key.categories.vitrine");
		assertEquals(VitrineKeyBindings.OPEN_STORE_TRANSLATION_KEY, "key.vitrine.open_store");
	}

	@Test
	public void keyBindingBuiltFromTheSameConstantsExposesMAsDefaultKey() {
		KeyBinding binding = new KeyBinding(
				VitrineKeyBindings.OPEN_STORE_TRANSLATION_KEY,
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				VitrineKeyBindings.CATEGORY_TRANSLATION_KEY);

		assertEquals(binding.getTranslationKey(), VitrineKeyBindings.OPEN_STORE_TRANSLATION_KEY);
		assertEquals(binding.getCategory(), VitrineKeyBindings.CATEGORY_TRANSLATION_KEY);
		assertEquals(binding.getDefaultKey().getCode(), GLFW.GLFW_KEY_M);
	}
}
