package de.spassglas.vitrine.mixin.client;

import de.spassglas.vitrine.ui.VitrineGuiScale;
import de.spassglas.vitrine.ui.theme.VitrineButtonStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vitrine-Bildschirme zeichnen ihre Knoepfe flach und abgerundet im
 * Store-Stil statt als graue Minecraft-Steinknoepfe (umgekocht, 2026-10-06:
 * "nicht alles so verpixelt"). Gilt nur fuer {@link ButtonWidget}s, waehrend
 * ein Vitrine-Bildschirm offen ist; jeder andere Bildschirm bleibt Vanilla.
 * Gezeichnet wird in {@link VitrineButtonStyle}.
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetStyleMixin {

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
	private void vitrine$storeStyle(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Object self = this;
		if (!(self instanceof ButtonWidget button)
				|| !VitrineGuiScale.isVitrineScreen(MinecraftClient.getInstance().currentScreen)) {
			return;
		}
		VitrineButtonStyle.render(context, button);
		ci.cancel();
	}
}
