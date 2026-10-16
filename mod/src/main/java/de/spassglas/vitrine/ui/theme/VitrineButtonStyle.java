package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Knoepfe in Vitrine-Bildschirmen mit umgekocht's Nine-Slice-Rahmen (assets v2):
 * normal {@code button_enabled}, Hauptaktion {@code button_accent} (blau),
 * deaktiviert {@code button_disabled}. Kippschalter zeigen
 * {@code toggle_on}/{@code toggle_off} statt Text. Beschriftung mittig in der
 * Vitrine-UI-Schrift, bei Platzmangel mit Auslassungspunkten gekuerzt.
 */
public final class VitrineButtonStyle {

	private static final Set<ClickableWidget> PRIMARY = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<ClickableWidget, BooleanSupplier> TOGGLES = new WeakHashMap<>();
	private static final Identifier TOGGLE_ON = Identifier.of("vitrine", "textures/gui/ui/toggle_on.png");
	private static final Identifier TOGGLE_OFF = Identifier.of("vitrine", "textures/gui/ui/toggle_off.png");

	private VitrineButtonStyle() {
	}

	/** Hauptaktion (z. B. "Installieren"): blauer Akzent-Rahmen. */
	public static <T extends ClickableWidget> T primary(T button) {
		PRIMARY.add(button);
		return button;
	}

	/** Kippschalter: zeigt den Schalter-Zustand aus {@code state} statt der Beschriftung. */
	public static <T extends ClickableWidget> T toggle(T button, BooleanSupplier state) {
		TOGGLES.put(button, state);
		return button;
	}

	/** Kippschalter wie in Windows 11 / iOS: runde Spur, weisser Knopf, glatt -- nicht "gedrueckt". */
	public static void drawSwitch(DrawContext context, int x, int y, int w, int h, boolean on, boolean hot) {
		int th = Math.min(h, Math.max(10, w * 10 / 18));
		int tw = Math.min(w, th * 18 / 10);
		int ty = y + (h - th) / 2;
		int track = on ? (hot ? 0xFF4A82EE : 0xFF3B74E8) : (hot ? 0xFF4A4F5B : 0xFF40454F);
		RoundedRect.fill(context, x, ty, tw, th, th / 2, track);
		if (!on) {
			RoundedRect.border(context, x, ty, tw, th, th / 2, 0xFF5A606E);
		}
		int k = th - 6;
		int kx = on ? x + tw - k - 3 : x + 3;
		RoundedRect.fill(context, kx, ty + 3, k, k, k / 2, on ? 0xFFFFFFFF : 0xFFC9CED8);
	}

	public static void render(DrawContext context, ClickableWidget button) {
		int x = button.getX();
		int y = button.getY();
		int w = button.getWidth();
		int h = button.getHeight();
		boolean hot = button.active && (button.isHovered() || button.isFocused());

		BooleanSupplier toggle = TOGGLES.get(button);
		if (toggle != null) {
			drawSwitch(context, x, y, w, h, toggle.getAsBoolean(), hot);
			return;
		}

		// umgekocht (2026-10-07): glatt gezeichnete Knoepfe statt Textur-Rahmen (die
		// hatten eine sichtbar dickere obere Haelfte). Flaeche + 1-px-Rand, rund.
		int radius = Math.min(h / 2, 6);
		boolean primary = PRIMARY.contains(button);
		int fill;
		int border;
		if (!button.active) {
			fill = 0xFF25272D;
			border = 0xFF30333B;
		} else if (primary) {
			fill = hot ? 0xFF4A82EE : 0xFF3B74E8;
			border = hot ? 0xFF6A9AF4 : 0xFF4F84EC;
		} else {
			fill = hot ? 0xFF353943 : 0xFF2C2F37;
			border = hot ? 0xFF4A505E : 0xFF3A3F4C;
		}
		RoundedRect.fill(context, x, y, w, h, radius, fill);
		RoundedRect.border(context, x, y, w, h, radius, border);

		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		Text label = ScaledText.styled(button.getMessage());
		int room = w - 2 * VitrineTheme.SPACE_UNIT;
		Text shown = label;
		if (font.getWidth(label) > room && room > 0) {
			StringVisitable cut = font.trimToWidth(label, Math.max(0, room - font.getWidth(ScaledText.styled("…"))));
			shown = ScaledText.styled(cut.getString() + "…");
		}
		int color = !button.active ? ActiveThemeColors.disabledText()
				: PRIMARY.contains(button) ? 0xFFFFFFFF : ActiveThemeColors.text();
		int textX = x + (w - font.getWidth(shown)) / 2;
		int textY = y + (h - font.fontHeight) / 2 + 1;
		context.drawText(font, shown, textX, textY, color, false);
	}
}
