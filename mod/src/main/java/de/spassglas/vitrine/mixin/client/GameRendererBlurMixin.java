package de.spassglas.vitrine.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.spassglas.vitrine.ui.theme.SurfaceBlur;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GATE21: {@code blur.surface} des aktiven Themes steuert die Unschaerfe
 * hinter Vitrine-Screens. {@link GameRenderer#renderBlur(float)} liest die
 * Staerke aus {@link GameOptions#getMenuBackgroundBlurrinessValue()}; genau
 * dieser Wert wird hier ersetzt, wenn ein Vitrine-Screen offen ist. Die
 * Entscheidung selbst (inklusive "Spieler hat Unschaerfe abgeschaltet")
 * steht testbar in {@link SurfaceBlur}.
 *
 * <p>{@code ModifyExpressionValue} (MixinExtras, im Fabric Loader enthalten)
 * statt {@code Redirect}, damit andere Mods dieselbe Stelle weiter anfassen
 * koennen.</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {

	@ModifyExpressionValue(method = "renderBlur",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/option/GameOptions;getMenuBackgroundBlurrinessValue()I"))
	private int vitrine$themeBlur(int original) {
		return SurfaceBlur.radius(original,
				SurfaceBlur.isVitrineScreen(MinecraftClient.getInstance().currentScreen),
				VitrineTheme.BLUR_SURFACE);
	}
}
