package de.spassglas.vitrine.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Addon-Plattform: Bild-Filter (Post-Shader) zur Laufzeit laden, ohne Neustart. */
@Mixin(GameRenderer.class)
public interface GameRendererPostAccessor {

	@Invoker("loadPostProcessor")
	void vitrine$loadPostProcessor(Identifier id);
}
