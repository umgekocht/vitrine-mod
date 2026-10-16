package de.spassglas.vitrine.ui.widget;

import de.spassglas.vitrine.modrinth.ModrinthClient;
import de.spassglas.vitrine.ui.image.CatalogImageCache;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * Eine Zeile der Modrinth-Liste (umgekocht, 2026-10-06: "eine Liste nach unten,
 * mit den echten Bildern"): Projekt-Icon links, daneben Titel, Autor und
 * Downloads, darunter die Kurzbeschreibung; rechts der Hinweis zur Wirkung.
 * Ein Klick oeffnet den Installieren-Dialog.
 */
public final class ModrinthListEntryWidget extends ClickableWidget {

	public static final int HEIGHT = 84;
	private static final Identifier FALLBACK_MOD = Identifier.of("vitrine", "textures/gui/icons/category_mod.png");
	private static final Identifier FALLBACK_PACK = Identifier.of("vitrine", "textures/gui/icons/category_texturen.png");

	private final ModrinthClient.Hit hit;
	private final TextRenderer textRenderer;
	private final Consumer<ModrinthClient.Hit> onActivate;

	public ModrinthListEntryWidget(int x, int y, int width, ModrinthClient.Hit hit, TextRenderer textRenderer,
			Consumer<ModrinthClient.Hit> onActivate) {
		super(x, y, width, HEIGHT, Text.literal(hit.title()));
		this.hit = hit;
		this.textRenderer = textRenderer;
		this.onActivate = onActivate;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		// glatte, runde Zeile wie in der Bibliothek (keine Textur-Rahmen mehr)
		int card = isHovered() ? 0xFF2E323B : 0xFF2A2C33;
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, x, y, w, h, 10, card);
		int pad = 10;
		int icon = h - 2 * pad;
		int ix = x + pad;
		int iy = y + pad;
		var tex = hit.iconUrl() == null ? java.util.Optional.<PackageImageTextures.Texture>empty()
				: CatalogImageCache.get(hit.iconUrl());
		if (tex.isPresent()) {
			PackageImageTextures.drawCover(context, tex.get(), ix, iy, icon, icon);
			de.spassglas.vitrine.ui.theme.RoundedRect.maskCorners(context, ix, iy, icon, icon, 8, card);
		} else {
			context.fill(ix, iy, ix + icon, iy + icon, ActiveThemeColors.surfaceSolid());
			GuiBlend.on();
			int s = icon * 3 / 4;
			context.drawTexture(isMod() ? FALLBACK_MOD : FALLBACK_PACK, ix + (icon - s) / 2, iy + (icon - s) / 2,
					0, 0, s, s, s, s);
		}

		int tx = ix + icon + 14;
		int noteW = 130;
		int textW = Math.max(40, x + w - pad - noteW - tx);
		int ty = y + pad;
		ScaledText.draw(context, textRenderer, ScaledText.bold(Text.literal(fit(hit.title(), textW, VitrineTheme.TYPE_BODY + 4))), tx, ty,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY + 4);
		ty += VitrineTheme.TYPE_BODY + 8;
		ScaledText.draw(context, textRenderer, Text.translatable("vitrine.modrinth.byline", hit.author(),
				String.format("%,d", hit.downloads())), tx, ty, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		ty += VitrineTheme.TYPE_CAPTION + 6;
		ScaledText.draw(context, textRenderer, Text.literal(fit(hit.description(), textW, VitrineTheme.TYPE_CAPTION)),
				tx, ty, ActiveThemeColors.text(), VitrineTheme.TYPE_CAPTION);

		// umgekocht (2026-10-07): "Holen"-Knopf direkt in der Zeile, darunter klein die Wirkung.
		int[] b = getRect();
		boolean over = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
		Text label = switch (state) {
			case WORKING -> Text.translatable("vitrine.modrinth.installing");
			case DONE -> Text.translatable("vitrine.modrinth.installed");
			case FAILED -> Text.translatable("vitrine.modrinth.failed_short");
			default -> Text.translatable("vitrine.modrinth.get");
		};
		if (state == State.IDLE) {
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, b[0], b[1], b[2], b[3], b[3] / 2,
					over ? 0xFF474B55 : 0xFF3A3D45);
		} else {
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, b[0], b[1], b[2], b[3], b[3] / 2, 0x26FFFFFF);
		}
		int lw = ScaledText.width(textRenderer, label.getString(), VitrineTheme.TYPE_CAPTION);
		ScaledText.draw(context, textRenderer, label, b[0] + (b[2] - lw) / 2, b[1] + (b[3] - VitrineTheme.TYPE_CAPTION) / 2,
				state == State.IDLE ? ActiveThemeColors.text()
						: state == State.FAILED ? ActiveThemeColors.danger() : ActiveThemeColors.text(),
				VitrineTheme.TYPE_CAPTION);
		Text note = Text.translatable(isMod() ? "vitrine.modrinth.list.restart" : "vitrine.modrinth.list.instant");
		int nw = ScaledText.width(textRenderer, note.getString(), VitrineTheme.TYPE_CAPTION - 2);
		ScaledText.draw(context, textRenderer, note, b[0] + (b[2] - nw) / 2, b[1] + b[3] + 4,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION - 2);
	}

	private enum State { IDLE, WORKING, DONE, FAILED }

	private State state = State.IDLE;

	private int[] getRect() {
		int bw = 96;
		int bh = 22;
		return new int[] {getX() + getWidth() - 10 - bw, getY() + (getHeight() - bh) / 2 - 6, bw, bh};
	}

	private void startInstall() {
		state = State.WORKING;
		Thread t = new Thread(() -> {
			try {
				de.spassglas.vitrine.modrinth.ModrinthInstaller.install(hit);
				state = State.DONE;
			} catch (Exception e) {
				de.spassglas.vitrine.Vitrine.LOGGER.warn("Modrinth-Installation von '{}' fehlgeschlagen: {}",
						hit.title(), e.toString());
				state = State.FAILED;
			}
		}, "Vitrine-Modrinth-Install");
		t.setDaemon(true);
		t.start();
	}

	private boolean isMod() {
		return "mod".equals(hit.projectType());
	}

	private String fit(String s, int maxW, int size) {
		if (s == null) {
			return "";
		}
		if (ScaledText.width(textRenderer, s, size) <= maxW) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && ScaledText.width(textRenderer, t + "...", size) > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t.stripTrailing() + "...";
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		int[] b = getRect();
		if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
			if (state == State.IDLE || state == State.FAILED) {
				startInstall();
			}
			return;
		}
		onActivate.accept(hit);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal(hit.title() + ", " + hit.author()));
	}
}
