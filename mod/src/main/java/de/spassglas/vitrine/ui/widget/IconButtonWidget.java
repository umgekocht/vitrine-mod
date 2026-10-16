package de.spassglas.vitrine.ui.widget;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * GATE18: Knopf mit einem der mitgelieferten Icons (docs/UI-SPEC.md "2.
 * Nav-Rail", "1. Kopfleiste"). Vorher waren Nav-Rail und Kopfleiste nackte
 * Vanilla-Knoepfe mit vollen Woertern -- in 40 px breiten Kopfleisten-Knoepfen
 * abgeschnitten, und die 18 {@code nav_*}-Icons lagen ungenutzt im Jar.
 *
 * <ul>
 *   <li>Mit {@link #withLabel}: Icon links, Beschriftung daneben (ausgeklappte
 *       Nav-Rail). Passt der Text nicht, wird er mit Auslassungspunkten
 *       gekuerzt -- nie abgeschnitten mitten im Buchstaben.</li>
 *   <li>Ohne Beschriftung: nur das Icon, mittig; der Text steht als Tooltip
 *       und als Bildschirmleser-Text zur Verfuegung (Barrierefreiheit).</li>
 * </ul>
 *
 * <p>Ausgewaehlt (aktive Nav-Rail-Seite) zeigt die gefuellte Icon-Variante
 * und faerbt sie mit dem Akzent; sonst die Umriss-Variante in Textfarbe. Die
 * Icons sind weiss gezeichnet und werden ueber die Shader-Farbe eingefaerbt --
 * Farben kommen ausschliesslich aus dem aktiven Theme.</p>
 */
public final class IconButtonWidget extends ButtonWidget {

	private final Identifier icon;
	private final Identifier selectedIcon;
	private final int iconSize;
	private final boolean showLabel;
	private boolean stackedLabel;
	private boolean selected;
	private String badge;

	private IconButtonWidget(int x, int y, int width, int height, Text label, PressAction onPress,
			Identifier icon, Identifier selectedIcon, int iconSize, boolean showLabel) {
		super(x, y, width, height, label, onPress, DEFAULT_NARRATION_SUPPLIER);
		this.icon = Objects.requireNonNull(icon, "icon");
		this.selectedIcon = selectedIcon != null ? selectedIcon : icon;
		this.iconSize = iconSize;
		this.showLabel = showLabel;
		// Ohne sichtbare Beschriftung traegt der Tooltip den Text.
		if (!showLabel) {
			setTooltip(Tooltip.of(label));
		}
	}

	/** Icon + Beschriftung (ausgeklappte Nav-Rail). */
	public static IconButtonWidget withLabel(int x, int y, int width, int height, Text label, PressAction onPress,
			Identifier icon, Identifier selectedIcon, int iconSize) {
		return new IconButtonWidget(x, y, width, height, label, onPress, icon, selectedIcon, iconSize, true);
	}

	/** Symbol oben, kleiner Name darunter -- Seitenleiste wie im Microsoft Store. */
	public static IconButtonWidget stacked(int x, int y, int width, int height, Text label, PressAction onPress,
			Identifier icon, Identifier selectedIcon, int iconSize) {
		IconButtonWidget b = new IconButtonWidget(x, y, width, height, label, onPress, icon, selectedIcon, iconSize, false);
		b.stackedLabel = true;
		b.setTooltip(null);
		return b;
	}

	/** Nur Icon, Text als Tooltip (Kopfleiste, eingeklappte Nav-Rail). */
	public static IconButtonWidget iconOnly(int x, int y, int width, int height, Text label, PressAction onPress,
			Identifier icon, Identifier selectedIcon, int iconSize) {
		return new IconButtonWidget(x, y, width, height, label, onPress, icon, selectedIcon, iconSize, false);
	}

	/**
	 * Kleines Abzeichen oben rechts, z. B. die Zahl laufender Downloads
	 * (docs/UI-SPEC.md "1. Kopfleiste": "Pfeil nach unten, mit Zaehler, wenn
	 * etwas laeuft"). {@code null} oder leer blendet es aus.
	 */
	public void setBadge(String badge) {
		this.badge = (badge == null || badge.isBlank()) ? null : badge;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public boolean isSelectedEntry() {
		return selected;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int left = getX();
		int top = getY();
		int right = left + getWidth();
		int bottom = top + getHeight();

		if (selected && !stackedLabel) {
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top, getWidth(), getHeight(), VitrineTheme.RADIUS_CONTROL,
					ActiveThemeColors.surfaceSolid());
		} else if (isHovered() && !stackedLabel) {
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top, getWidth(), getHeight(), VitrineTheme.RADIUS_CONTROL,
					ActiveThemeColors.surfaceTranslucent());
		}
		if (false && isFocused()) { // Fokusrahmen entfernt (umgekocht, 2026-10-06)
			de.spassglas.vitrine.ui.theme.RoundedRect.border(context, left, top, getWidth(), getHeight(), VitrineTheme.RADIUS_CONTROL,
					ActiveThemeColors.focusRing());
		}

		if (stackedLabel && (selected || isHovered())) {
			// ganze Knopfflaeche, glatt gerundet (Microsoft Store) -- Charter-Regel 7:
			// dieselben Theme-Token wie der nicht gestapelte Zweig oben, keine eigenen Literale.
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top, getWidth(), getHeight(), VitrineTheme.RADIUS_CONTROL,
					selected ? ActiveThemeColors.surfaceSolid() : ActiveThemeColors.surfaceTranslucent());
		}
		if (stackedLabel && selected) {
			// Microsoft Store: gewaehlter Eintrag mit kurzem Akzentstrich links
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top + getHeight() / 2 - 8, 3, 16, 1,
					ActiveThemeColors.accent());
		}
		int iconX = showLabel ? left + VitrineTheme.EDGE_PADDING * 2 : left + (getWidth() - iconSize) / 2;
		int iconY = stackedLabel ? top + 7 : top + (getHeight() - iconSize) / 2;
		int tint = !this.active ? ActiveThemeColors.disabledText()
				: selected ? 0xFFFFFFFF : ActiveThemeColors.text(); // active-Icon ist schon farbig
		context.setShaderColor(red(tint), green(tint), blue(tint), 1.0F);
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(selected ? selectedIcon : icon, iconX, iconY, 0.0F, 0.0F,
				iconSize, iconSize, iconSize, iconSize);
		context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		if (badge != null) {
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			int badgeWidth = font.getWidth(de.spassglas.vitrine.ui.theme.ScaledText.styled(badge)) + VitrineTheme.SPACE_UNIT * 2;
			int badgeHeight = font.fontHeight + VitrineTheme.SPACE_UNIT;
			int bx = right - badgeWidth;
			context.fill(bx, top, right, top + badgeHeight, ActiveThemeColors.accent());
			context.drawText(font, de.spassglas.vitrine.ui.theme.ScaledText.styled(badge), bx + VitrineTheme.SPACE_UNIT, top + VitrineTheme.SPACE_UNIT / 2 + 1,
					ActiveThemeColors.accentContrast(), false);
		}

		if (stackedLabel) {
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			int size = VitrineTheme.TYPE_CAPTION - 1;
			String text = getMessage().getString();
			int tw = de.spassglas.vitrine.ui.theme.ScaledText.width(font, text, size);
			de.spassglas.vitrine.ui.theme.ScaledText.draw(context, font, getMessage(), left + (getWidth() - tw) / 2,
					top + 7 + iconSize + 4, selected ? ActiveThemeColors.text() : ActiveThemeColors.textMuted(), size);
		}

		if (showLabel) {
			TextRenderer font = MinecraftClient.getInstance().textRenderer;
			int textX = iconX + iconSize + VitrineTheme.CONTROL_GAP;
			int maxWidth = right - VitrineTheme.EDGE_PADDING - textX;
			String label = fitWithEllipsis(font, getMessage().getString(), maxWidth);
			int textY = top + (getHeight() - font.fontHeight) / 2 + 1;
			int color = !this.active ? ActiveThemeColors.disabledText()
					: selected ? ActiveThemeColors.text() : ActiveThemeColors.textMuted();
			context.drawText(font, de.spassglas.vitrine.ui.theme.ScaledText.styled(label), textX, textY, color, false);
		}
	}

	/** Kuerzt {@code text} mit "…", damit er in {@code maxWidth} passt; leer, wenn gar nichts passt. */
	static String fitWithEllipsis(TextRenderer font, String text, int maxWidth) {
		if (maxWidth <= 0) {
			return "";
		}
		if (font.getWidth(de.spassglas.vitrine.ui.theme.ScaledText.styled(text)) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int room = maxWidth - font.getWidth(de.spassglas.vitrine.ui.theme.ScaledText.styled(ellipsis));
		if (room <= 0) {
			return "";
		}
		return font.trimToWidth(de.spassglas.vitrine.ui.theme.ScaledText.styled(text), room).getString() + ellipsis;
	}

	private static float red(int argb) {
		return ((argb >> 16) & 0xFF) / 255.0F;
	}

	private static float green(int argb) {
		return ((argb >> 8) & 0xFF) / 255.0F;
	}

	private static float blue(int argb) {
		return (argb & 0xFF) / 255.0F;
	}
}
