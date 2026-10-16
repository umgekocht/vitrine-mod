package de.spassglas.vitrine.ui.home;

import de.spassglas.vitrine.ui.image.CatalogImageCache;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Konto oben rechts wie im Microsoft Store (umgekocht, 2026-10-07): rundes
 * Profilbild -- der Kopf des eigenen Minecraft-Skins, oder spaeter das
 * Profilbild von der Vitrine-Website -- und ein Ausklappmenue mit genau zwei
 * Punkten: zur Website, Minecraft beenden.
 */
public final class AccountMenu {

	/**
	 * Platzhalter-Domain der kommenden Vitrine-Website. Hat ein Spieler dort ein
	 * Profilbild gesetzt, liefert {@code <WEBSITE>/api/avatar/<uuid>.png} es als
	 * PNG; sonst (404, offline) bleibt der Skin-Kopf.
	 */
	public static final String WEBSITE = "https://vitrine.example";

	private static final Identifier EXIT_ICON = Identifier.of("vitrine", "textures/gui/icons/ui_power.png");
	private static final Identifier WEB_ICON = Identifier.of("vitrine", "textures/gui/icons/ui_web.png");

	private boolean open;
	private int ax;
	private int ay;
	private int size;

	public static String avatarUrl() {
		var profile = MinecraftClient.getInstance().getGameProfile();
		String id = profile.getId() == null ? "unknown" : profile.getId().toString().replace("-", "");
		return WEBSITE + "/api/avatar/" + id + ".png";
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		open = false;
	}

	/** Zeichnet das runde Profilbild bei {@code (x, y)}. {@code under} = Farbe dahinter (fuer die Rundung). */
	public void renderAvatar(DrawContext context, int x, int y, int s, int under, int mouseX, int mouseY) {
		this.ax = x;
		this.ay = y;
		this.size = s;
		drawFace(context, x, y, s, under);
		boolean over = mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
		if (over || open) {
			RoundedRect.border(context, x - 1, y - 1, s + 2, s + 2, (s + 2) / 2, 0x88FFFFFF);
		}
	}

	private static void drawFace(DrawContext context, int x, int y, int s, int under) {
		var web = CatalogImageCache.get(avatarUrl());
		if (web.isPresent()) {
			GuiBlend.on();
			PackageImageTextures.drawCover(context, web.get(), x, y, s, s);
		} else {
			MinecraftClient mc = MinecraftClient.getInstance();
			SkinTextures skin = mc.getSkinProvider().getSkinTextures(mc.getGameProfile());
			PlayerSkinDrawer.draw(context, skin, x, y, s);
		}
		RoundedRect.maskCorners(context, x, y, s, s, s / 2, under);
	}

	/** Klick auf das Profilbild schaltet das Menue; Klick ins Menue fuehrt aus. */
	public boolean click(Screen parent, double mx, double my) {
		if (mx >= ax && mx < ax + size && my >= ay && my < ay + size) {
			open = !open;
			return true;
		}
		if (!open) {
			return false;
		}
		int[] p = panel();
		if (mx < p[0] || mx >= p[0] + p[2] || my < p[1] || my >= p[1] + p[3]) {
			open = false;
			return false;
		}
		int itemY = p[1] + 64;
		if (my >= itemY && my < itemY + 26) {
			open = false;
			ConfirmLinkScreen.open(parent, WEBSITE);
		} else if (my >= itemY + 26 && my < itemY + 52) {
			open = false;
			MinecraftClient.getInstance().scheduleStop();
		}
		return true;
	}

	private int[] panel() {
		int w = 230;
		int h = 64 + 52 + 8;
		return new int[] {ax + size - w, ay + size + 6, w, h};
	}

	public void renderMenu(DrawContext context, TextRenderer tr, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		int[] p = panel();
		int x = p[0];
		int y = p[1];
		int w = p[2];
		int h = p[3];
		RoundedRect.fill(context, x - 2, y, w + 4, h + 4, 12, 0x40000000);
		RoundedRect.fill(context, x, y, w, h, 10, 0xFF272A31);
		int face = 40;
		drawFace(context, x + 14, y + 12, face, 0xFF272A31);
		MinecraftClient mc = MinecraftClient.getInstance();
		ScaledText.draw(context, tr, Text.literal(mc.getSession().getUsername()), x + 14 + face + 12, y + 16,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
		ScaledText.draw(context, tr, Text.translatable("vitrine.account.minecraft"), x + 14 + face + 12,
				y + 18 + VitrineTheme.TYPE_BODY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		context.fill(x + 10, y + 60, x + w - 10, y + 61, 0x22FFFFFF);
		item(context, tr, x, y + 64, w, WEB_ICON, Text.translatable("vitrine.account.website"), mouseX, mouseY);
		item(context, tr, x, y + 90, w, EXIT_ICON, Text.translatable("vitrine.account.quit"), mouseX, mouseY);
	}

	private static void item(DrawContext context, TextRenderer tr, int x, int y, int w, Identifier icon, Text label,
			int mouseX, int mouseY) {
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 26;
		if (over) {
			RoundedRect.fill(context, x + 6, y + 1, w - 12, 24, 6, 0x22FFFFFF);
		}
		GuiBlend.on();
		context.drawTexture(icon, x + 18, y + 8, 0, 0, 10, 10, 10, 10);
		ScaledText.draw(context, tr, label, x + 40, y + (26 - VitrineTheme.TYPE_BODY) / 2, ActiveThemeColors.text(),
				VitrineTheme.TYPE_BODY);
	}
}
