package de.spassglas.vitrine.hud.client;

import de.spassglas.vitrine.hud.ActiveHudRegistry;
import de.spassglas.vitrine.hud.HudDataResolver;
import de.spassglas.vitrine.hud.HudDataSource;
import de.spassglas.vitrine.hud.HudGameState;
import de.spassglas.vitrine.hud.HudPlacement;
import de.spassglas.vitrine.hud.HudTextContent;
import de.spassglas.vitrine.hud.HudWidget;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeichnet bei jedem HUD-Frame die Widgets aller aktiven HUD-Pakete aus
 * {@link ActiveHudRegistry} (ROADMAP T5). Registriert ueber Fabrics
 * {@link HudRenderCallback} -- den in dieser Fabric-1.21.1-Version
 * vorhandenen HUD-Zeichen-Haken (ein {@code HudLayerRegistrationCallback}
 * gibt es hier noch nicht).
 *
 * <p>Pro Widget: erst {@link HudWidget#visibility()} gegen den aktuellen
 * {@link MinecraftHudGameState} pruefen, dann per {@link HudPlacement} aus
 * Anker + Versatz + Skalierung die linke obere Ecke bestimmen und je nach Typ
 * zeichnen. Der Renderer traegt selbst keine nackte Farbe/Maszahl -- Masze aus
 * {@link VitrineTheme}, Farben aus {@link ActiveThemeColors} (liest frisch
 * aus {@code ThemeService.tokens()}, ein Theme-Wechsel faerbt den HUD-Overlay
 * also sofort um); nur die im HUD-JSON vom Paketautor gelieferte
 * Balkenfarbe ({@code color}) gewinnt, das ist Paketdatum.</p>
 */
public final class HudWidgetRenderer implements HudRenderCallback {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	private HudWidgetRenderer() {
	}

	/** Haengt den Renderer in Fabrics HUD-Zeichen-Ereignis ein (einmalig beim Client-Start aufrufen). */
	public static void register() {
		HudRenderCallback.EVENT.register(new HudWidgetRenderer());
		LOGGER.info("HUD-Renderer registriert (HudRenderCallback).");
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		ActiveHudRegistry registry = ActiveHudRegistry.instance();
		if (registry.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null) {
			return;
		}

		HudGameState state = new MinecraftHudGameState(client);
		Window window = client.getWindow();
		int screenWidth = window.getScaledWidth();
		int screenHeight = window.getScaledHeight();
		TextRenderer textRenderer = client.textRenderer;

		for (HudWidget widget : registry.activeWidgets()) {
			if (!widget.visibility().isVisible(state)) {
				continue;
			}
			try {
				drawWidget(context, widget, state, textRenderer, screenWidth, screenHeight);
			} catch (RuntimeException e) {
				// Ein einzelnes kaputtes Widget darf nicht den ganzen HUD-Frame
				// (und damit das Spiel-Overlay) mitreissen -- geloggt, nicht
				// verschluckt.
				LOGGER.warn("HUD-Widget '{}' ({}) liess sich nicht zeichnen: {}",
						widget.id(), widget.typeId(), e.toString());
			}
		}
	}

	private static void drawWidget(DrawContext context, HudWidget widget, HudGameState state,
			TextRenderer textRenderer, int screenWidth, int screenHeight) {
		switch (widget) {
			case HudWidget.Text text -> drawText(context, text, state, textRenderer, screenWidth, screenHeight);
			case HudWidget.List list -> drawList(context, list, state, textRenderer, screenWidth, screenHeight);
			case HudWidget.Icon icon -> drawIcon(context, icon, screenWidth, screenHeight);
			case HudWidget.Bar bar -> drawBar(context, bar, state, screenWidth, screenHeight);
		}
	}

	private static void drawText(DrawContext context, HudWidget.Text widget, HudGameState state,
			TextRenderer textRenderer, int screenWidth, int screenHeight) {
		String line = HudDataResolver.render(widget.content(), state);
		int width = textRenderer.getWidth(line);
		int height = textRenderer.fontHeight;
		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(), width, height,
				widget.scale(), screenWidth, screenHeight);

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(pos.x(), pos.y(), 0.0);
		matrices.scale((float) widget.scale(), (float) widget.scale(), 1.0f);
		context.drawText(textRenderer, line, 0, 0, ActiveThemeColors.hudText(), true);
		matrices.pop();
	}

	private static void drawList(DrawContext context, HudWidget.List widget, HudGameState state,
			TextRenderer textRenderer, int screenWidth, int screenHeight) {
		java.util.List<String> lines = new java.util.ArrayList<>(widget.entries().size());
		int width = 0;
		for (HudTextContent entry : widget.entries()) {
			String line = HudDataResolver.render(entry, state);
			lines.add(line);
			width = Math.max(width, textRenderer.getWidth(line));
		}
		int lineStep = textRenderer.fontHeight + VitrineTheme.HUD_LIST_LINE_GAP;
		int height = lines.isEmpty() ? 0 : lineStep * lines.size() - VitrineTheme.HUD_LIST_LINE_GAP;
		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(), width, height,
				widget.scale(), screenWidth, screenHeight);

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(pos.x(), pos.y(), 0.0);
		matrices.scale((float) widget.scale(), (float) widget.scale(), 1.0f);
		int y = 0;
		for (String line : lines) {
			context.drawText(textRenderer, line, 0, y, ActiveThemeColors.hudText(), true);
			y += lineStep;
		}
		matrices.pop();
	}

	private static void drawIcon(DrawContext context, HudWidget.Icon widget, int screenWidth, int screenHeight) {
		Identifier texture = Identifier.tryParse(widget.texture());
		if (texture == null) {
			// Der Pfad hat das Schema-Muster bestanden, ist aber keine gueltige
			// Resource-Location (z. B. Grossbuchstaben) -- nicht zeichenbar.
			LOGGER.debug("HUD-Icon '{}' hat keinen als Identifier aufloesbaren Texturpfad '{}'.",
					widget.id(), widget.texture());
			return;
		}
		int width = widget.width() != null ? widget.width() : VitrineTheme.HUD_ICON_DEFAULT_SIZE;
		int height = widget.height() != null ? widget.height() : VitrineTheme.HUD_ICON_DEFAULT_SIZE;
		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(), width, height,
				widget.scale(), screenWidth, screenHeight);

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(pos.x(), pos.y(), 0.0);
		matrices.scale((float) widget.scale(), (float) widget.scale(), 1.0f);
		context.drawTexture(texture, 0, 0, 0.0f, 0.0f, width, height, width, height);
		matrices.pop();
	}

	private static void drawBar(DrawContext context, HudWidget.Bar widget, HudGameState state,
			int screenWidth, int screenHeight) {
		if (widget.source().kind() != HudDataSource.Kind.NUMERIC) {
			LOGGER.debug("HUD-Balken '{}' bezieht sich auf die nicht-numerische Quelle '{}' -- nicht zeichenbar.",
					widget.id(), widget.source().id());
			return;
		}
		int width = widget.width() != null ? widget.width() : VitrineTheme.HUD_BAR_DEFAULT_WIDTH;
		int height = widget.height() != null ? widget.height() : VitrineTheme.HUD_BAR_DEFAULT_HEIGHT;
		double value = HudDataResolver.numericValue(widget.source(), state);
		int fillWidth = (int) Math.round(width * widget.fillFraction(value));
		int fillColor = widget.fillColorArgb() != null ? widget.fillColorArgb() : ActiveThemeColors.hudBarFill();

		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(), width, height,
				widget.scale(), screenWidth, screenHeight);

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(pos.x(), pos.y(), 0.0);
		matrices.scale((float) widget.scale(), (float) widget.scale(), 1.0f);
		context.fill(0, 0, width, height, ActiveThemeColors.hudBarTrack());
		if (fillWidth > 0) {
			context.fill(0, 0, fillWidth, height, fillColor);
		}
		matrices.pop();
	}
}
