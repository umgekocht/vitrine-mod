package de.spassglas.vitrine.ui.theme;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Schaltet Alpha-Blending ein, bevor Vitrine eine Textur zeichnet.
 *
 * <p>{@code DrawContext#drawTexture} tut das in 1.21.1 nicht selbst. Ohne
 * Blending werden durchsichtige Pixel als Flaeche mitgezeichnet -- im Spiel
 * wirkten die Icons dadurch verrauscht bzw. wie Klumpen (gefunden 2026-10-06
 * per AutoShot).</p>
 */
public final class GuiBlend {

	private GuiBlend() {
	}

	public static void on() {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
	}
}
