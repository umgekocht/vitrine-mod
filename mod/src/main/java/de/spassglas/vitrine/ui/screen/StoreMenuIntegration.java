package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Vitrine-Knopf im Hauptmenue ({@link TitleScreen}) und Pausenmenue
 * ({@link GameMenuScreen}) (ROADMAP S0).
 *
 * <p>umgekocht (2026-10-06): quadratisch mit eigenem Vitrine-Symbol, und im
 * Hauptmenue an der Stelle des Barrierefreiheits-Knopfs (rechts neben
 * "Beenden"). Die Barrierefreiheit bleibt ueber Optionen erreichbar. Im
 * Pausenmenue gibt es diesen Knopf nicht -- dort sitzt das Symbol oben rechts.</p>
 *
 * <p>Der Klick geht ueber {@link StoreEntryPoint#open} (ROADMAP X4: beim ersten
 * Oeffnen zuerst der Erster-Start-Ablauf, genau wie ueber die Taste M).</p>
 */
public final class StoreMenuIntegration {

	private static final Identifier ICON = Identifier.of("vitrine", "textures/gui/icons/vitrine_button.png");
	private static final int SIZE = 20;

	private StoreMenuIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
				addOpenStoreButton(screen, scaledWidth);
			}
		});
	}

	private static void addOpenStoreButton(Screen screen, int scaledWidth) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		int x = scaledWidth - SIZE - VitrineTheme.EDGE_PADDING;
		int y = VitrineTheme.EDGE_PADDING;
		ClickableWidget accessibility = screen instanceof TitleScreen ? findAccessibility(buttons) : null;
		if (accessibility != null) {
			x = accessibility.getX();
			y = accessibility.getY();
			buttons.remove(accessibility);
		}
		buttons.add(new IconButton(x, y, screen));
	}

	private static ClickableWidget findAccessibility(List<ClickableWidget> buttons) {
		for (ClickableWidget w : buttons) {
			if (w.getMessage().getContent() instanceof TranslatableTextContent t
					&& "options.accessibility".equals(t.getKey())) {
				return w;
			}
		}
		return null;
	}

	/** Quadratischer Knopf, der nur das Vitrine-Symbol zeigt. */
	private static final class IconButton extends ButtonWidget {

		IconButton(int x, int y, Screen parent) {
			super(x, y, SIZE, SIZE, Text.translatable("vitrine.menu.button"),
					b -> StoreEntryPoint.open(MinecraftClient.getInstance(), parent), DEFAULT_NARRATION_SUPPLIER);
			setTooltip(Tooltip.of(Text.translatable("vitrine.menu.button")));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			// umgekocht (2026-10-07): Vanilla-Knopf als Hintergrund (sonst erkennt man ihn
			// nicht als Knopf), darin ein Einkaufswagen statt des V.
			super.renderWidget(context, mouseX, mouseY, delta);
			GuiBlend.on();
			int icon = 16;
			context.drawTexture(ICON, getX() + (SIZE - icon) / 2, getY() + (SIZE - icon) / 2, icon, icon,
					0, 0, 64, 64, 64, 64);
		}

		@Override
		public void drawMessage(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, int color) {
			// nur Symbol, kein Text
		}
	}
}
