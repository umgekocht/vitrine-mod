package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.modrinth.ModrinthCatalog;
import de.spassglas.vitrine.modrinth.ModrinthClient;
import de.spassglas.vitrine.ui.home.HomeWidget;
import de.spassglas.vitrine.ui.home.ModrinthInstalls;
import de.spassglas.vitrine.ui.image.CatalogImageCache;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Produktseite eines Modrinth-Projekts im Microsoft-Store-Stil (umgekocht,
 * 2026-10-07: "sieht leer und unverstaendlich aus wie eine Betonwand"): grosses
 * Galeriebild oben, darueber Logo, Titel, Autor, Zahlen, Kategorien und ein
 * grosser Installieren-Knopf; darunter Bilder, Beschreibung und "Mehr davon".
 * Der Name der Klasse bleibt (alle Aufrufer oeffnen weiter diese Seite).
 */
public final class ModrinthInstallScreen extends Screen {

	private final Screen parent;
	private final ModrinthClient.Hit hit;
	private volatile ModrinthClient.Project project;
	private int scrollY;
	private int heroImage;
	private Page page;

	public ModrinthInstallScreen(Screen parent, ModrinthClient.Hit hit) {
		super(Text.literal(hit.title()));
		this.parent = parent;
		this.hit = hit;
		Thread t = new Thread(() -> {
			try {
				project = ModrinthClient.project(hit.id());
			} catch (Exception e) {
				Vitrine.LOGGER.info("Modrinth-Details fuer '{}' nicht geladen: {}", hit.title(), e.toString());
			}
		}, "Vitrine-Modrinth-Project");
		t.setDaemon(true);
		t.start();
	}

	private boolean isMod() {
		return "mod".equals(hit.projectType());
	}

	@Override
	protected void init() {
		page = new Page(this.width, this.height, this.textRenderer);
		addDrawableChild(page);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scrollY = Math.max(0, Math.min(Math.max(0, page.contentHeight - this.height), scrollY - (int) (v * 30)));
		return true;
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	/** Markdown grob zu Fliesstext (Bilder, Links, Ueberschriften, HTML raus). */
	static String plain(String md) {
		if (md == null) {
			return "";
		}
		String s = md.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
				.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
				.replaceAll("<[^>]+>", " ")
				.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
				.replaceAll("[*_`>|]", "")
				.replaceAll("\\s+", " ").trim();
		return s.length() > 1200 ? s.substring(0, 1200).trim() + " ..." : s;
	}

	private static String fmt(long n) {
		if (n >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1f Mio.", n / 1_000_000.0);
		}
		if (n >= 1_000) {
			return String.format(Locale.ROOT, "%.1f Tsd.", n / 1_000.0);
		}
		return Long.toString(n);
	}

	/** Die ganze Seite als ein Widget mit Klickflaechen. */
	private final class Page extends HomeWidget {

		private static final Identifier BACK = Identifier.of("vitrine", "textures/gui/icons/ui_back.png");
		private static final Identifier PLACEHOLDER = Identifier.of("vitrine", "textures/gui/banner_placeholder.png");
		int contentHeight;

		Page(int w, int h, TextRenderer tr) {
			super(0, 0, w, h, tr, Text.literal(hit.title()));
		}

		@Override
		protected void draw(DrawContext context, float delta) {
			int w = getWidth();
			int bg = ActiveThemeColors.bg();
			context.fill(0, 0, w, getHeight(), bg);
			int top = -scrollY;
			int heroH = Math.min(320, getHeight() * 58 / 100);
			List<String> gallery = project != null ? project.gallery() : List.of();
			String heroUrl = !gallery.isEmpty() ? gallery.get(Math.min(heroImage, gallery.size() - 1)) : hit.galleryUrl();

			// --- Kopfbild ueber die ganze Breite, links und unten abgedunkelt ---
			if (heroUrl != null && CatalogImageCache.get(heroUrl).isPresent()) {
				image(context, heroUrl, null, 0, top, w, heroH, bg, bg, 0, true);
			} else {
				GuiBlend.on();
				int pw = Math.max(w, heroH * 8 / 3);
				context.drawTexture(PLACEHOLDER, (w - pw) / 2, top, 0, 0, pw, pw * 3 / 8, pw, pw * 3 / 8);
			}
			shadeLeftWide(context, 0, top, w * 3 / 4, heroH);
			context.fillGradient(0, top + heroH - 80, w, top + heroH, 0x00000000 | (bg & 0xFFFFFF), bg);

			// Zurueck
			int bx = 12;
			int by = 10;
			boolean overBack = hovered(bx, by, 26, 26);
			RoundedRect.fill(context, bx, by, 26, 26, 13, overBack ? 0x66000000 : 0x44000000);
			GuiBlend.on();
			context.drawTexture(BACK, bx + 7, by + 7, 0, 0, 12, 12, 12, 12);
			region(bx, by, 26, 26, ModrinthInstallScreen.this::close);

			// --- Kopfbereich: Logo, Titel, Autor, Zahlen, Kategorien ---
			int x = 48;
			int y = top + 44;
			int icon = 64;
			image(context, hit.iconUrl(), Identifier.of("vitrine", "textures/gui/icons/category_"
							+ (isMod() ? "mod" : "datapack".equals(hit.projectType()) ? "datapack"
							: "shader".equals(hit.projectType()) ? "shader" : "texturen") + ".png"),
					x, y, icon, icon, 0xFF2A2C33, 0x00000000, 10, false);
			int tx = x + icon + 18;
			int titleSize = VitrineTheme.TYPE_TITLE + 10;
			ScaledText.draw(context, textRenderer, ScaledText.bold(Text.literal(fit(hit.title(), w / 2, titleSize))), tx,
					y + 2, 0xFFFFFFFF, titleSize, false);
			ScaledText.draw(context, textRenderer, Text.literal(hit.author()), tx, y + titleSize + 8, 0xFF8FB4FF,
					VitrineTheme.TYPE_BODY);
			String stats = Text.translatable("vitrine.product.stats", fmt(hit.downloads()),
					project != null ? fmt(project.followers()) : "...").getString();
			ScaledText.draw(context, textRenderer, Text.literal(stats), tx, y + titleSize + 12 + VitrineTheme.TYPE_BODY,
					0xFFD6DBE5, VitrineTheme.TYPE_CAPTION);

			int cy = y + icon + 18;
			int cx = x;
			// Art + Kategorien als Pillen
			List<String> tags = new ArrayList<>();
			tags.add(Text.translatable("vitrine.modrinth.tab." + hit.projectType()).getString());
			if (project != null) {
				for (String c : project.categories()) {
					if (tags.size() < 5) {
						tags.add(c.substring(0, 1).toUpperCase(Locale.ROOT) + c.substring(1));
					}
				}
			}
			for (String tag : tags) {
				int tw = ScaledText.width(textRenderer, tag, VitrineTheme.TYPE_CAPTION) + 16;
				RoundedRect.fill(context, cx, cy, tw, 18, 9, 0x40000000);
				RoundedRect.border(context, cx, cy, tw, 18, 9, 0x33FFFFFF);
				ScaledText.draw(context, textRenderer, Text.literal(tag), cx + 8, cy + 5, 0xFFE8ECF4, VitrineTheme.TYPE_CAPTION);
				cx += tw + 6;
			}
			cy += 30;
			int textW = Math.min(w / 2, 460);
			cy += ScaledText.drawWrapped(context, textRenderer, Text.literal(hit.description()), x, cy, textW, 0xFFE8ECF4,
					VitrineTheme.TYPE_BODY) + 16;

			// Grosser Installieren-Knopf
			ModrinthInstalls.State st = ModrinthInstalls.state(hit.id());
			boolean action = st == ModrinthInstalls.State.IDLE || st == ModrinthInstalls.State.FAILED;
			int btnW = 190;
			int btnH = 30;
			boolean overBtn = action && hovered(x, cy, btnW, btnH);
			RoundedRect.fill(context, x, cy, btnW, btnH, 6, action ? (overBtn ? 0xFF4A82EE : 0xFF3B74E8) : 0xFF2E323B);
			String label = Text.translatable(ModrinthInstalls.pillKey(hit.id())).getString();
			int lw = ScaledText.width(textRenderer, label, VitrineTheme.TYPE_BODY);
			ScaledText.draw(context, textRenderer, Text.literal(label), x + (btnW - lw) / 2, cy + (btnH - VitrineTheme.TYPE_BODY) / 2,
					0xFFFFFFFF, VitrineTheme.TYPE_BODY);
			if (action) {
				region(x, cy, btnW, btnH, () -> ModrinthInstalls.start(hit));
			}
			ScaledText.drawWrapped(context, textRenderer, Text.translatable(isMod() ? "vitrine.modrinth.note_mod"
							: "datapack".equals(hit.projectType()) ? "vitrine.product.note_datapack" : "vitrine.modrinth.note_pack"),
					x, cy + btnH + 10, textW, 0xFFB8BFCC, VitrineTheme.TYPE_CAPTION);

			// --- Unterer Bereich: links Bilder + Beschreibung, rechts "Mehr davon" ---
			int below = Math.max(top + heroH + 10, cy + btnH + 50);
			int gap = 16;
			int rightW = Math.min(300, w / 3);
			int leftW = w - 2 * x + 16 - rightW - gap;
			int lx = x - 16;
			int ly = below;

			// Bilder
			int galH;
			int thumbs = Math.min(4, gallery.size());
			int tw4 = (leftW - 36 - 3 * 10) / 4;
			int th = tw4 * 9 / 16;
			galH = VitrineTheme.TYPE_TITLE + 30 + (thumbs > 0 ? th : VitrineTheme.TYPE_BODY) + 18;
			RoundedRect.fill(context, lx, ly, leftW, galH, RADIUS, PANEL_COLOR);
			heading(context, Text.translatable("vitrine.product.images"), lx + 18, ly + 16, null);
			if (thumbs == 0) {
				ScaledText.draw(context, textRenderer, Text.translatable(project == null ? "vitrine.home.loading"
						: "vitrine.product.no_images"), lx + 18, ly + VitrineTheme.TYPE_TITLE + 30, ActiveThemeColors.textMuted(),
						VitrineTheme.TYPE_BODY);
			}
			for (int i = 0; i < thumbs; i++) {
				int gx = lx + 18 + i * (tw4 + 10);
				int gy = ly + VitrineTheme.TYPE_TITLE + 30;
				image(context, gallery.get(i), null, gx, gy, tw4, th, 0xFF33363E, PANEL_COLOR, 6, true);
				if (i == heroImage) {
					RoundedRect.border(context, gx - 1, gy - 1, tw4 + 2, th + 2, 7, ActiveThemeColors.accent());
				}
				int idx = i;
				region(gx, gy, tw4, th, () -> heroImage = idx);
			}
			ly += galH + gap;

			// Beschreibung
			String body = project != null ? plain(project.body()) : "";
			Text bodyText = Text.literal(body.isEmpty() ? hit.description() : body);
			int bodyH = ScaledText.wrappedHeight(textRenderer, bodyText, leftW - 36, VitrineTheme.TYPE_BODY);
			int descH = VitrineTheme.TYPE_TITLE + 30 + bodyH + 18;
			RoundedRect.fill(context, lx, ly, leftW, descH, RADIUS, PANEL_COLOR);
			heading(context, Text.translatable("vitrine.product.description"), lx + 18, ly + 16, null);
			ScaledText.drawWrapped(context, textRenderer, bodyText, lx + 18, ly + VitrineTheme.TYPE_TITLE + 30, leftW - 36,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			if (project != null && !project.license().isEmpty()) {
				ly += descH + 8;
				ScaledText.draw(context, textRenderer, Text.translatable("vitrine.product.license", project.license()),
						lx + 4, ly, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
				ly += 20;
			} else {
				ly += descH + 16;
			}

			// Mehr davon
			int rx = lx + leftW + gap;
			int ry = below;
			heading(context, Text.translatable("vitrine.product.more"), rx, ry + 16, null);
			int iy = ry + VitrineTheme.TYPE_TITLE + 30;
			int count = 0;
			for (ModrinthClient.Hit other : ModrinthCatalog.get(hit.projectType(), () -> {
			})) {
				if (other.id().equals(hit.id()) || count >= 6) {
					continue;
				}
				int s = 44;
				boolean over = hovered(rx - 6, iy - 6, rightW + 12, s + 12);
				if (over) {
					RoundedRect.fill(context, rx - 6, iy - 6, rightW + 12, s + 12, 8, PANEL_HOVER);
				}
				image(context, other.iconUrl(), null, rx, iy, s, s, darken(other.color(), 0.5f), over ? PANEL_HOVER : bg, 8, false);
				ScaledText.draw(context, textRenderer, Text.literal(fit(other.title(), rightW - s - 12, VitrineTheme.TYPE_BODY)),
						rx + s + 12, iy + 4, ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
				ScaledText.draw(context, textRenderer, Text.literal(fit(other.author(), rightW - s - 12, VitrineTheme.TYPE_CAPTION)),
						rx + s + 12, iy + 8 + VitrineTheme.TYPE_BODY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
				region(rx - 6, iy - 6, rightW + 12, s + 12, () -> MinecraftClient.getInstance()
						.setScreen(new ModrinthInstallScreen(parent, other)));
				iy += s + 16;
				count++;
			}
			contentHeight = Math.max(ly, iy) + scrollY + 30;
		}

		private void shadeLeftWide(DrawContext context, int x, int y, int w, int h) {
			int steps = 48;
			for (int i = 0; i < steps; i++) {
				int a = 235 * (steps - i) / steps;
				context.fill(x + w * i / steps, y, x + w * (i + 1) / steps, y + h, a << 24 | (ActiveThemeColors.bg() & 0xFFFFFF));
			}
		}
	}
}
