package de.spassglas.vitrine.platform;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Addon-Konsole: Ausgaben und Fehler aller Skript-Addons, neueste unten. */
public final class AddonConsoleScreen extends Screen {

	private final Screen parent;
	private int scroll;

	public AddonConsoleScreen(Screen parent) {
		super(Text.translatable("vitrine.platform.console"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.platform.console.clear"), b -> AddonConsole.clear())
				.dimensions(width - 24 - 220, 14, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.platform.console.close"), b -> close())
				.dimensions(width - 24 - 110, 14, 110, 20).build());
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, scroll + (int) Math.signum(v) * 3);
		return true;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, ActiveThemeColors.bg());
		ScaledText.draw(context, textRenderer, ScaledText.bold(getTitle()), 24, 18, ActiveThemeColors.text(),
				VitrineTheme.TYPE_TITLE);
		String loaded = String.join(", ", PlatformHost.loadedNames());
		ScaledText.draw(context, textRenderer, Text.translatable("vitrine.platform.console.loaded",
						loaded.isEmpty() ? "-" : loaded), 24, 18 + VitrineTheme.TYPE_TITLE + 6, ActiveThemeColors.textMuted(),
				VitrineTheme.TYPE_CAPTION);
		int top = 70;
		RoundedRect.fill(context, 16, top, width - 32, height - top - 16, 10, 0xFF17191E);
		List<AddonConsole.Line> lines = AddonConsole.lines();
		int lineH = VitrineTheme.TYPE_CAPTION + 5;
		int visible = (height - top - 32) / lineH;
		int end = Math.max(0, lines.size() - scroll);
		int start = Math.max(0, end - visible);
		int y = top + 10;
		for (int i = start; i < end; i++) {
			AddonConsole.Line l = lines.get(i);
			int color = switch (l.level()) {
				case ERROR -> 0xFFFF6B6B;
				case WARN -> 0xFFFFC857;
				default -> 0xFFD6DBE5;
			};
			ScaledText.draw(context, textRenderer, Text.literal(l.time() + "  [" + l.addon() + "]  " + l.text()), 28, y,
					color, VitrineTheme.TYPE_CAPTION);
			y += lineH;
		}
		if (lines.isEmpty()) {
			ScaledText.draw(context, textRenderer, Text.translatable("vitrine.platform.console.empty"), 28, y,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
