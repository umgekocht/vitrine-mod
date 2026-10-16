package de.spassglas.vitrine.platform;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineButtonStyle;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.spassglas.vitrine.platform.ApiFunction.bool;
import static de.spassglas.vitrine.platform.ApiFunction.fn;
import static de.spassglas.vitrine.platform.ApiFunction.str;

/**
 * Ein Menue, das ein Addon mit {@code vitrine.menu(titel, function(ui) ... end)}
 * baut: Ueberschriften, Texte, Knoepfe und Schalter in einer runden Karte im
 * Vitrine-Stil. Die Bau-Funktion laeuft bei jedem Aufbau neu, deshalb zeigt
 * {@code ui.refresh()} sofort geaenderte Werte.
 */
final class ScriptMenuScreen extends Screen {

	private sealed interface Item permits Label, Button, Toggle, Space {
	}

	private record Label(String text, boolean heading) implements Item {
	}

	private record Button(String label, Callback fn, boolean primary) implements Item {
	}

	private record Toggle(String label, boolean value, Callback fn) implements Item {
	}

	private record Space(int height) implements Item {
	}

	final LoadedAddon owner;
	private final Callback builder;
	private final List<Item> items = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	ScriptMenuScreen(LoadedAddon owner, String title, Callback builder) {
		super(Text.literal(title));
		this.owner = owner;
		this.builder = builder;
	}

	private Map<String, Object> ui() {
		Map<String, Object> ui = new LinkedHashMap<>();
		ui.put("heading", (ApiFunction) a -> add(new Label(str(a, 0, ""), true)));
		ui.put("text", (ApiFunction) a -> add(new Label(str(a, 0, ""), false)));
		ui.put("button", (ApiFunction) a -> add(new Button(str(a, 0, "OK"), fn(a, 1), bool(a, 2, false))));
		ui.put("toggle", (ApiFunction) a -> add(new Toggle(str(a, 0, ""), bool(a, 1, false), fn(a, 2))));
		ui.put("space", (ApiFunction) a -> add(new Space((int) ApiFunction.num(a, 0, 8))));
		ui.put("refresh", (ApiFunction) a -> {
			client.send(this::rebuild);
			return null;
		});
		ui.put("close", (ApiFunction) a -> {
			client.send(this::close);
			return null;
		});
		return ui;
	}

	private Object add(Item item) {
		if (items.size() < 64) {
			items.add(item);
		}
		return null;
	}

	private void rebuild() {
		clearChildren();
		init();
	}

	@Override
	protected void init() {
		items.clear();
		PlatformHost.safeCall(owner, builder, ui());
		panelW = Math.min(340, width - 40);
		int h = 46;
		for (Item it : items) {
			h += heightOf(it);
		}
		panelH = Math.min(height - 40, h + 14);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		int y = panelY + 46;
		int x = panelX + 18;
		int w = panelW - 36;
		for (Item it : items) {
			if (it instanceof Button b) {
				ButtonWidget bw = ButtonWidget.builder(Text.literal(b.label()), btn -> {
					if (b.fn() != null) {
						PlatformHost.safeCall(owner, b.fn());
					}
				}).dimensions(x, y, w, 22).build();
				if (b.primary()) {
					VitrineButtonStyle.primary(bw);
				}
				addDrawableChild(bw);
			} else if (it instanceof Toggle t) {
				boolean[] state = {t.value()};
				ButtonWidget bw = ButtonWidget.builder(Text.literal(t.label()), btn -> {
					state[0] = !state[0];
					if (t.fn() != null) {
						PlatformHost.safeCall(owner, t.fn(), state[0]);
					}
				}).dimensions(x + w - 40, y + 2, 40, 20).build();
				VitrineButtonStyle.toggle(bw, () -> state[0]);
				addDrawableChild(bw);
			}
			y += heightOf(it);
		}
	}

	private static int heightOf(Item it) {
		return switch (it) {
			case Label l -> l.heading() ? VitrineTheme.TYPE_TITLE + 10 : VitrineTheme.TYPE_BODY + 8;
			case Button b -> 28;
			case Toggle t -> 28;
			case Space s -> Math.max(0, Math.min(80, s.height()));
		};
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x88000000);
		RoundedRect.fill(context, panelX, panelY, panelW, panelH, 12, 0xFF272A31);
		RoundedRect.border(context, panelX, panelY, panelW, panelH, 12, 0xFF3A3F4C);
		ScaledText.draw(context, textRenderer, ScaledText.bold(getTitle()), panelX + 18, panelY + 16,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
		ScaledText.draw(context, textRenderer, Text.literal(owner.name), panelX + panelW - 18
						- ScaledText.width(textRenderer, owner.name, VitrineTheme.TYPE_CAPTION), panelY + 20,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		int y = panelY + 46;
		int x = panelX + 18;
		for (Item it : items) {
			if (it instanceof Label l) {
				Text t = l.heading() ? ScaledText.bold(Text.literal(l.text())) : Text.literal(l.text());
				ScaledText.draw(context, textRenderer, t, x, y + 2, l.heading() ? ActiveThemeColors.text()
						: ActiveThemeColors.textMuted(), l.heading() ? VitrineTheme.TYPE_BODY + 2 : VitrineTheme.TYPE_BODY);
			} else if (it instanceof Toggle t) {
				ScaledText.draw(context, textRenderer, Text.literal(t.label()), x, y + 7, ActiveThemeColors.text(),
						VitrineTheme.TYPE_BODY);
			}
			y += heightOf(it);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
