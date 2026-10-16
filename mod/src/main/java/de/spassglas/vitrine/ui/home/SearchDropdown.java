package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.image.CatalogImageCache;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Vorschlagsliste unter der Suche wie im Microsoft Store: oben Suchbegriffe
 * (Lupe im dunklen Quadrat), darunter direkte Treffer mit Logo und Art.
 * Wird von {@code StoreScreen} ueber den Inhalt gezeichnet.
 */
public final class SearchDropdown {

	/** Eine Zeile: Suchbegriff ({@code item == null}) oder Treffer. */
	public record Row(String text, String caption, String imageUrl, Identifier fallback, Runnable action) {
	}

	private static final Identifier SEARCH_ICON = Identifier.of("vitrine", "textures/gui/icons/ui_search.png");
	/** Markiert eine Zeile als Suchverlauf (Uhr-Symbol im dunklen Quadrat). */
	public static final Identifier HISTORY = Identifier.of("vitrine", "textures/gui/icons/ui_restart.png");
	public static final int ROW_H = 36;

	private final List<Row> rows = new ArrayList<>();
	private int x;
	private int y;
	private int w;

	public void set(int x, int y, int w, List<Row> queries, List<Row> items) {
		this.x = x;
		this.y = y;
		this.w = w;
		rows.clear();
		rows.addAll(queries);
		rows.addAll(items);
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	public int height() {
		return rows.size() * ROW_H + 10;
	}

	public boolean contains(double mx, double my) {
		return !rows.isEmpty() && mx >= x && mx < x + w && my >= y && my < y + height();
	}

	public void render(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		if (rows.isEmpty()) {
			return;
		}
		int h = height();
		// weicher Schatten + Flaeche mit runden Ecken
		RoundedRect.fill(context, x - 2, y, w + 4, h + 4, 10, 0x40000000);
		RoundedRect.fill(context, x, y, w, h, 8, 0xFF272A31);
		int ry = y + 5;
		for (Row r : rows) {
			boolean over = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) {
				RoundedRect.fill(context, x + 4, ry, w - 8, ROW_H, 6, 0x22FFFFFF);
			}
			int s = ROW_H - 8;
			int ix = x + 10;
			int iy = ry + 4;
			if (r.imageUrl() == null && (r.fallback() == null || r.fallback() == HISTORY)) {
				RoundedRect.fill(context, ix, iy, s, s, 5, 0xFF111216);
				GuiBlend.on();
				int is = 10;
				context.drawTexture(r.fallback() == HISTORY ? HISTORY : SEARCH_ICON, ix + (s - is) / 2, iy + (s - is) / 2, 0, 0, is, is, is, is);
			} else {
				var tex = r.imageUrl() == null ? java.util.Optional.<PackageImageTextures.Texture>empty()
						: CatalogImageCache.get(r.imageUrl());
				if (tex.isPresent()) {
					GuiBlend.on();
					PackageImageTextures.drawFitted(context, tex.get(), ix, iy, s, s);
				} else {
					RoundedRect.fill(context, ix, iy, s, s, 5, 0xFF33363E);
					if (r.fallback() != null) {
						GuiBlend.on();
						context.drawTexture(r.fallback(), ix + 3, iy + 3, 0, 0, s - 6, s - 6, s - 6, s - 6);
					}
				}
				RoundedRect.maskCorners(context, ix, iy, s, s, 5, over ? 0xFF3A3D44 : 0xFF272A31);
			}
			int tx = ix + s + 10;
			if (r.caption() == null) {
				ScaledText.draw(context, tr, Text.literal(r.text()), tx, ry + (ROW_H - VitrineTheme.TYPE_BODY) / 2,
						ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			} else {
				ScaledText.draw(context, tr, Text.literal(r.text()), tx, ry + 4, ActiveThemeColors.text(),
						VitrineTheme.TYPE_BODY);
				ScaledText.draw(context, tr, Text.literal(r.caption()), tx, ry + 7 + VitrineTheme.TYPE_BODY,
						ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
			}
			ry += ROW_H;
		}
	}

	/** Klick auf eine Zeile ausfuehren; {@code true}, wenn getroffen. */
	public boolean click(double mx, double my) {
		if (!contains(mx, my)) {
			return false;
		}
		int idx = (int) ((my - y - 5) / ROW_H);
		if (idx >= 0 && idx < rows.size()) {
			rows.get(idx).action().run();
		}
		return true;
	}
}
