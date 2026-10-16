package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.config.VitrineConfig;
import de.spassglas.vitrine.config.VitrineSettings;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.GuiBlend;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineButtonStyle;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Einstellungen im Microsoft-Store-Stil (umgekocht, 2026-10-07: "soviel wie moeglich
 * einstellen"): Abschnitte als runde Karten, je Zeile Titel und Erklaerung
 * links, rechts ein Schalter oder Knopf. Jede Aenderung wird sofort gespeichert.
 */
public final class SettingsHubScreen extends Screen {

	private sealed interface Control permits Switch, Action {
	}

	private record Switch(Function<VitrineSettings, Boolean> get, BiConsumer<VitrineSettings, Boolean> set)
			implements Control {
	}

	private record Action(String labelKey, Runnable run) implements Control {
	}

	private record Row(String key, Control control) {
	}

	private record Section(String titleKey, List<Row> rows) {
	}

	private static final Identifier BACK = Identifier.of("vitrine", "textures/gui/icons/ui_back.png");
	private static final int ROW_H = 46;
	private static final int MAX_W = 760;

	private final Screen parent;
	private final List<Section> sections = new ArrayList<>();
	private int scrollY;
	private int contentH;
	private Text toast = null;
	private long toastUntil;

	/**
	 * Oeffnet einen Screen, standardmaessig ueber {@code client.setScreen}.
	 * GATE75-Testseam: {@link #setScreenOpenerForTest} ersetzt dies, damit
	 * ein Test ohne laufenden {@link net.minecraft.client.MinecraftClient}
	 * beweisen kann, welcher Screen geoeffnet wuerde -- derselbe Kniff wie
	 * {@code LibraryScreen#setBackgroundWorkSchedulerForTest} (GATE113).
	 */
	private Consumer<Screen> screenOpener = s -> {
		if (client != null) {
			client.setScreen(s);
		}
	};

	/**
	 * GATE75: derselbe HUD-Editor-Screen wie {@code /vitrine hud edit}
	 * ({@code HudCommand#runEdit}) -- beide rufen {@code
	 * HudEditorScreen.create(parent)} auf, hier mit diesem Settings-Screen
	 * als Eltern-Screen statt mit {@code client.currentScreen}.
	 */
	private final Runnable openHudEditorAction =
			() -> screenOpener.accept(de.spassglas.vitrine.hud.client.HudEditorScreen.create(this));

	public SettingsHubScreen(Screen parent) {
		super(Text.translatable("vitrine.settings.title"));
		this.parent = parent;
		VitrineConfig.invalidate();
		sections.add(new Section("vitrine.settings2.store", List.of(
				sw("modrinth", s -> s.showModrinth, (s, v) -> s.showModrinth = v),
				sw("java_mods", s -> s.showJavaModOffers, (s, v) -> s.showJavaModOffers = v),
				sw("dependencies", s -> s.installDependencies, (s, v) -> s.installDependencies = v),
				sw("confirm_install", s -> s.confirmModrinthInstall, (s, v) -> s.confirmModrinthInstall = v))));
		sections.add(new Section("vitrine.settings2.look", List.of(
				sw("reduce_motion", s -> s.reduceMotion, (s, v) -> s.reduceMotion = v),
				sw("banner_autoplay", s -> s.bannerAutoplay, (s, v) -> s.bannerAutoplay = v),
				sw("banner_glow", s -> s.bannerGlow, (s, v) -> s.bannerGlow = v),
				sw("badges", s -> s.showSourceBadges, (s, v) -> s.showSourceBadges = v))));
		sections.add(new Section("vitrine.settings2.search", List.of(
				sw("live_suggestions", s -> s.liveSuggestions, (s, v) -> s.liveSuggestions = v),
				sw("search_history", s -> s.saveSearchHistory, (s, v) -> s.saveSearchHistory = v),
				new Row("clear_history", new Action("vitrine.settings2.clear_history.button", () -> {
					VitrineConfig.update(s -> s.searchHistory.clear());
					showToast("vitrine.settings2.done");
				})))));
		sections.add(new Section("vitrine.settings2.tools", List.of(
				new Row("hud_editor", new Action("vitrine.settings2.hud_editor.button", openHudEditorAction)),
				new Row("open_packs", new Action("vitrine.settings2.open.button", () -> open(VitrineConfig.dir().resolve("packs")))),
				new Row("open_resourcepacks", new Action("vitrine.settings2.open.button",
						() -> open(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("resourcepacks")))),
				new Row("open_mods", new Action("vitrine.settings2.open.button",
						() -> open(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods")))),
				new Row("clear_cache", new Action("vitrine.settings2.clear_cache.button", this::clearImageCache)),
				new Row("onboarding", new Action("vitrine.settings2.onboarding.button", () -> {
					VitrineConfig.update(s -> s.onboardingSeen = false);
					showToast("vitrine.settings2.onboarding.done");
				})),
				new Row("addon_console", new Action("vitrine.settings2.open_short", () -> {
					if (client != null) {
						client.setScreen(new de.spassglas.vitrine.platform.AddonConsoleScreen(this));
					}
				})),
				sw("dev_addons", s -> s.devAddons, (s, v) -> s.devAddons = v),
				new Row("open_dev", new Action("vitrine.settings2.open.button",
						() -> open(de.spassglas.vitrine.platform.PlatformHost.devDir()))),
				new Row("diagnostics", new Action("vitrine.settings.diagnostics", () -> {
					if (client != null) {
						client.setScreen(new DiagnosticsScreen(this));
					}
				})))));
	}

	private static Row sw(String key, Function<VitrineSettings, Boolean> get, BiConsumer<VitrineSettings, Boolean> set) {
		return new Row(key, new Switch(get, set));
	}

	private void showToast(String key) {
		toast = Text.translatable(key);
		toastUntil = Util.getMeasuringTimeMs() + 2500;
	}

	private void open(Path path) {
		try {
			Files.createDirectories(path);
		} catch (java.io.IOException ignored) {
			// oeffnen klappt trotzdem, falls es den Ordner schon gibt
		}
		Util.getOperatingSystem().open(path.toFile());
	}

	/** Zwischengespeicherte Katalog- und Modrinth-Bilder entfernen (werden bei Bedarf neu geladen). */
	private void clearImageCache() {
		Path dir = VitrineConfig.dir().resolve("cache").resolve("images");
		int n = 0;
		if (Files.isDirectory(dir)) {
			try (var files = Files.list(dir)) {
				for (Path f : (Iterable<Path>) files::iterator) {
					if (Files.isRegularFile(f) && Files.deleteIfExists(f)) {
						n++;
					}
				}
			} catch (java.io.IOException ignored) {
				// halb geleert ist auch ok
			}
		}
		toast = Text.translatable("vitrine.settings2.clear_cache.done", n);
		toastUntil = Util.getMeasuringTimeMs() + 2500;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 2 * VitrineTheme.SPACE_PAGE);
	}

	private int panelX() {
		return (this.width - panelW()) / 2;
	}

	@Override
	protected void init() {
		int y = VitrineTheme.HEADER_HEIGHT + 24 - scrollY;
		int x = panelX();
		int w = panelW();
		for (Section sec : sections) {
			y += VitrineTheme.TYPE_TITLE + 12;
			for (Row row : sec.rows()) {
				int cy = y + (ROW_H - 20) / 2;
				if (row.control() instanceof Switch sw) {
					ButtonWidget b = ButtonWidget.builder(Text.translatable("vitrine.settings2." + row.key()), btn -> {
								VitrineConfig.update(s -> sw.set().accept(s, !sw.get().apply(s)));
							})
							.dimensions(x + w - 18 - 40, cy, 40, 20).build();
					VitrineButtonStyle.toggle(b, () -> sw.get().apply(VitrineConfig.get()));
					b.visible = cy > VitrineTheme.HEADER_HEIGHT;
					addDrawableChild(b);
				} else if (row.control() instanceof Action a) {
					int bw = 130;
					ButtonWidget b = ButtonWidget.builder(Text.translatable(a.labelKey()), btn -> a.run().run())
							.dimensions(x + w - 18 - bw, cy, bw, 20).build();
					b.visible = cy > VitrineTheme.HEADER_HEIGHT;
					addDrawableChild(b);
				}
				y += ROW_H;
			}
			y += 18;
		}
		contentH = y + scrollY - VitrineTheme.HEADER_HEIGHT;
		addDrawableChild(de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(VitrineTheme.EDGE_PADDING,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2, VitrineTheme.ICON_BUTTON_SIZE,
				VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), b -> close(), BACK, null,
				VitrineTheme.UI_ICON_SOURCE_SIZE));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		int max = Math.max(0, contentH - (this.height - VitrineTheme.HEADER_HEIGHT));
		int next = Math.max(0, Math.min(max, scrollY - (int) (v * 30)));
		if (next != scrollY) {
			scrollY = next;
			clearChildren();
			init();
		}
		return true;
	}

	@Override
	public void close() {
		VitrineConfig.invalidate();
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
		int x = panelX();
		int w = panelW();
		int y = VitrineTheme.HEADER_HEIGHT + 24 - scrollY;
		for (Section sec : sections) {
			ScaledText.draw(context, textRenderer, ScaledText.bold(Text.translatable(sec.titleKey())), x + 4, y,
					ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
			y += VitrineTheme.TYPE_TITLE + 12;
			int cardH = sec.rows().size() * ROW_H;
			RoundedRect.fill(context, x, y, w, cardH, 10, 0xFF2A2C33);
			for (int i = 0; i < sec.rows().size(); i++) {
				Row row = sec.rows().get(i);
				int ry = y + i * ROW_H;
				if (i > 0) {
					context.fill(x + 16, ry, x + w - 16, ry + 1, 0x18FFFFFF);
				}
				ScaledText.draw(context, textRenderer, Text.translatable("vitrine.settings2." + row.key()), x + 18,
						ry + 9, ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
				ScaledText.draw(context, textRenderer, Text.translatable("vitrine.settings2." + row.key() + ".desc"),
						x + 18, ry + 12 + VitrineTheme.TYPE_BODY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
			}
			y += cardH + 18;
		}
		// Kopfleiste ueber dem Inhalt
		context.fill(0, 0, width, VitrineTheme.HEADER_HEIGHT, 0xFF23252B);
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, width, VitrineTheme.HEADER_HEIGHT, 0x22FFFFFF);
		ScaledText.draw(context, textRenderer, ScaledText.bold(Text.translatable("vitrine.settings.title")),
				VitrineTheme.EDGE_PADDING + VitrineTheme.ICON_BUTTON_SIZE + 14,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_TITLE) / 2, ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
		super.render(context, mouseX, mouseY, delta);
		if (toast != null && Util.getMeasuringTimeMs() < toastUntil) {
			int tw = ScaledText.width(textRenderer, toast.getString(), VitrineTheme.TYPE_BODY) + 28;
			int tx = (width - tw) / 2;
			int ty = height - 44;
			RoundedRect.fill(context, tx, ty, tw, 26, 13, 0xFF3B74E8);
			ScaledText.draw(context, textRenderer, toast, tx + 14, ty + (26 - VitrineTheme.TYPE_BODY) / 2, 0xFFFFFFFF,
					VitrineTheme.TYPE_BODY);
			GuiBlend.on();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** GATE75-Testseam: ersetzt, wie {@link #openHudEditorAction} einen Screen oeffnet. */
	void setScreenOpenerForTest(Consumer<Screen> opener) {
		this.screenOpener = opener;
	}

	/** GATE75-Testseam: die Aktion, die der "HUD-Editor"-Knopf in den Einstellungen ausloest. */
	Runnable openHudEditorActionForTest() {
		return openHudEditorAction;
	}
}
