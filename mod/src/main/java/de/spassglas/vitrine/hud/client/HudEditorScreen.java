package de.spassglas.vitrine.hud.client;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.hud.ActiveHudRegistry;
import de.spassglas.vitrine.hud.HudAnchor;
import de.spassglas.vitrine.hud.HudEditorModel;
import de.spassglas.vitrine.hud.HudPlacement;
import de.spassglas.vitrine.hud.HudVisibility;
import de.spassglas.vitrine.hud.HudWidget;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * In-Game-HUD-Editor (ROADMAP T6): ein Widget auswaehlen, mit der Maus ziehen
 * oder mit den Pfeiltasten fein verschieben, Anker/Ausrichtung umschalten,
 * an-/abschalten, speichern/schliessen. Erreichbar ueber
 * {@code /vitrine hud edit} ({@link HudCommand}).
 *
 * <p>Reine Ansicht: die gesamte Logik sitzt im client-freien
 * {@link HudEditorModel}, das jede Aenderung in eine {@code HudLayoutOverrides}
 * schreibt -- getrennt vom schreibgeschuetzten Paket-Layout. "Speichern"
 * persistiert atomar nach {@code config/vitrine/hud/overrides.json} und legt
 * die Overrides in der {@link ActiveHudRegistry} live ueber die aktiven
 * Pakete. Nur Vanilla-Widgets, alle Texte ueber {@code vitrine.hud.editor.*},
 * keine hartkodierten Farben/Masze (alles aus {@link VitrineTheme} bzw.
 * {@link ActiveThemeColors}).</p>
 */
public final class HudEditorScreen extends Screen {

	private final Screen parent;
	private final HudEditorModel model;
	private final Runnable themeRepaintListener = this::onThemeChanged;

	private int draggingIndex = -1;

	HudEditorScreen(Screen parent, HudEditorModel model) {
		super(Text.translatable("vitrine.hud.editor.title"));
		this.parent = parent;
		this.model = model;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	/** Baut den Editor ueber den gerade aktiven HUD-Widgets und dem echten Konfigordner. */
	public static HudEditorScreen create(Screen parent) {
		return create(parent, FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID));
	}

	/**
	 * Wie {@link #create(Screen)}, aber mit einem frei waehlbaren
	 * Konfigordner -- erlaubt {@code SettingsHubScreen}, denselben
	 * Erzeugungsweg testbar zu verdrahten (Testseam
	 * {@code SettingsHubScreen#setHudEditorConfigDirSupplierForTest}), ohne
	 * dass dafuer ein echter, laufender {@code FabricLoader} noetig ist.
	 */
	public static HudEditorScreen create(Screen parent, Path configDir) {
		return new HudEditorScreen(parent,
				HudEditorModel.forConfigDir(ActiveHudRegistry.instance().activeWidgetRefs(), configDir));
	}

	@Override
	protected void init() {
		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.header.back"), b -> close())
				.dimensions(VitrineTheme.EDGE_PADDING, buttonY,
						VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.ICON_BUTTON_SIZE)
				.build());

		int x = VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		int w = VitrineTheme.MENU_BUTTON_WIDTH;
		int h = VitrineTheme.MENU_BUTTON_HEIGHT;
		int step = h + VitrineTheme.CONTROL_GAP;

		if (model.hasWidgets()) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.hud.editor.select_next"),
					b -> mutate(model::selectNext)).dimensions(x, y, w, h).build());
			y += step;
			addDrawableChild(ButtonWidget.builder(
					Text.translatable("vitrine.hud.editor.anchor", anchorLabel()),
					b -> mutate(model::cycleAnchor)).dimensions(x, y, w, h).build());
			y += step;
			addDrawableChild(ButtonWidget.builder(
					Text.translatable("vitrine.hud.editor.visibility", visibilityLabel()),
					b -> mutate(model::cycleVisibility)).dimensions(x, y, w, h).build());
			y += step;
			addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.hud.editor.reset"),
					b -> mutate(model::resetSelected)).dimensions(x, y, w, h).build());
			y += step;
			addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.hud.editor.save"),
					b -> onSave()).dimensions(x, y, w, h).build());
			y += step;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("vitrine.hud.editor.close"),
				b -> close()).dimensions(x, y, w, h).build());
	}

	private void mutate(Runnable action) {
		action.run();
		rebuild();
	}

	private void rebuild() {
		clearChildren();
		init();
	}

	/**
	 * {@link ThemeService}-Rueckruf nach einem Laufzeit-Theme-Wechsel.
	 *
	 * <p><b>GATE113 (Thread-Modell, siehe {@code StoreScreen#onThemeChanged}
	 * fuer die volle Begruendung):</b> dieser Rueckruf laeuft bei
	 * Theme-Paketen auf dem Installations-Thread, nicht dem Render-Thread --
	 * {@code rebuild()} darf aber nur auf dem Client-Thread laufen. Deshalb
	 * einplanen statt direkt ausfuehren; die {@code currentScreen}-Pruefung
	 * erneut innerhalb der eingeplanten Aufgabe, der Bildschirm kann bis
	 * dahin gewechselt haben.</p>
	 */
	private void onThemeChanged() {
		if (this.client == null) {
			return;
		}
		this.client.execute(() -> {
			if (this.client != null && this.client.currentScreen == this) {
				rebuild();
			}
		});
	}

	private void onSave() {
		model.save();
		ActiveHudRegistry.instance().setOverrides(model.overrides());
		Vitrine.LOGGER.info("HUD-Editor: {} Override-Eintrag/Eintraege gespeichert.",
				model.overrides().asMap().size());
		close();
	}

	@Override
	public void removed() {
		ThemeService.instance().removeRepaintListener(themeRepaintListener);
		super.removed();
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

	// --- Eingabe ------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && model.hasWidgets()) {
			for (int i = model.widgetCount() - 1; i >= 0; i--) {
				if (hitTest(i, mouseX, mouseY)) {
					model.select(i);
					draggingIndex = i;
					rebuild();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && draggingIndex >= 0 && draggingIndex == model.selectedIndex()) {
			model.moveBy((int) Math.round(deltaX), (int) Math.round(deltaY));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			draggingIndex = -1;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		int nudge = shift ? VitrineTheme.SPACE_UNIT : 1;
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				close();
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				model.moveBy(-nudge, 0);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				model.moveBy(nudge, 0);
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				model.moveBy(0, -nudge);
				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				model.moveBy(0, nudge);
				return true;
			}
			case GLFW.GLFW_KEY_TAB -> {
				mutate(model::selectNext);
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}

	// --- Zeichnen ---------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, ActiveThemeColors.overlayScrim());

		for (int i = 0; i < model.widgetCount(); i++) {
			drawWidgetBox(context, i);
		}

		context.fill(0, 0, this.width, VitrineTheme.HEADER_HEIGHT, ActiveThemeColors.surfaceSolid());
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, this.width, VitrineTheme.HEADER_HEIGHT,
				ActiveThemeColors.borderTranslucent());
		ScaledText.draw(context, this.textRenderer, this.title,
				VitrineTheme.EDGE_PADDING * 2 + VitrineTheme.ICON_BUTTON_SIZE,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_TITLE) / 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);

		int textX = VitrineTheme.SPACE_PAGE * 2 + VitrineTheme.MENU_BUTTON_WIDTH;
		int textY = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		if (model.hasWidgets()) {
			Text selected = Text.translatable("vitrine.hud.editor.selected",
					Text.literal(model.selected().map(ref -> ref.widget().id()).orElse("")));
			ScaledText.draw(context, this.textRenderer, selected, textX, textY,
					ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.hud.editor.hint"),
					textX, textY + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		} else {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.hud.editor.no_widgets"),
					textX, textY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_SUBTITLE);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawWidgetBox(DrawContext context, int index) {
		HudWidget widget = model.effectiveWidget(index);
		int[] size = boxSize(widget);
		int boxW = (int) Math.round(size[0] * widget.scale());
		int boxH = (int) Math.round(size[1] * widget.scale());
		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(),
				size[0], size[1], widget.scale(), this.width, this.height);

		boolean hidden = widget.visibility() instanceof HudVisibility.Hidden;
		boolean selected = index == model.selectedIndex();

		context.fill(pos.x(), pos.y(), pos.x() + boxW, pos.y() + boxH,
				hidden ? ActiveThemeColors.overlayScrim() : ActiveThemeColors.surfaceTranslucent());
		int borderColor = selected ? ActiveThemeColors.accent() : ActiveThemeColors.borderTranslucent();
		int borderWidth = selected ? VitrineTheme.FOCUS_RING_WIDTH : 1;
		drawOutline(context, pos.x(), pos.y(), boxW, boxH, borderColor, borderWidth);

		ScaledText.draw(context, this.textRenderer, Text.literal(widget.id()),
				pos.x() + VitrineTheme.SPACE_UNIT, pos.y() + VitrineTheme.SPACE_UNIT,
				hidden ? ActiveThemeColors.disabledText() : ActiveThemeColors.text(),
				VitrineTheme.TYPE_CAPTION);
	}

	private static void drawOutline(DrawContext context, int x, int y, int w, int h, int color, int thickness) {
		context.fill(x, y, x + w, y + thickness, color);
		context.fill(x, y + h - thickness, x + w, y + h, color);
		context.fill(x, y, x + thickness, y + h, color);
		context.fill(x + w - thickness, y, x + w, y + h, color);
	}

	private int[] boxSize(HudWidget widget) {
		return switch (widget) {
			case HudWidget.Icon icon -> new int[] {
					icon.width() != null ? icon.width() : VitrineTheme.HUD_ICON_DEFAULT_SIZE,
					icon.height() != null ? icon.height() : VitrineTheme.HUD_ICON_DEFAULT_SIZE };
			case HudWidget.Bar bar -> new int[] {
					bar.width() != null ? bar.width() : VitrineTheme.HUD_BAR_DEFAULT_WIDTH,
					bar.height() != null ? bar.height() : VitrineTheme.HUD_BAR_DEFAULT_HEIGHT };
			case HudWidget.List list -> new int[] {
					Math.max(this.textRenderer.getWidth(list.id()) + VitrineTheme.SPACE_UNIT * 2,
							VitrineTheme.HUD_BAR_DEFAULT_WIDTH),
					(this.textRenderer.fontHeight + VitrineTheme.HUD_LIST_LINE_GAP)
							* Math.max(1, list.entries().size()) + VitrineTheme.SPACE_UNIT * 2 };
			case HudWidget.Text text -> new int[] {
					Math.max(this.textRenderer.getWidth(text.id()) + VitrineTheme.SPACE_UNIT * 2,
							VitrineTheme.HUD_BAR_DEFAULT_WIDTH),
					this.textRenderer.fontHeight + VitrineTheme.SPACE_UNIT * 2 };
		};
	}

	private boolean hitTest(int index, double mouseX, double mouseY) {
		HudWidget widget = model.effectiveWidget(index);
		int[] size = boxSize(widget);
		int boxW = (int) Math.round(size[0] * widget.scale());
		int boxH = (int) Math.round(size[1] * widget.scale());
		HudPlacement.Position pos = HudPlacement.resolve(widget.anchor(), widget.offset(),
				size[0], size[1], widget.scale(), this.width, this.height);
		return mouseX >= pos.x() && mouseX <= pos.x() + boxW
				&& mouseY >= pos.y() && mouseY <= pos.y() + boxH;
	}

	private Text anchorLabel() {
		HudAnchor anchor = model.effectiveSelected().map(HudWidget::anchor).orElse(HudAnchor.TOP_LEFT);
		return Text.translatable("vitrine.hud.editor.anchor." + anchor.id().replace('-', '_'));
	}

	private Text visibilityLabel() {
		String state = model.selectedVisibilityOverride()
				.map(visible -> visible ? "on" : "off")
				.orElse("default");
		return Text.translatable("vitrine.hud.editor.visibility." + state);
	}
}
