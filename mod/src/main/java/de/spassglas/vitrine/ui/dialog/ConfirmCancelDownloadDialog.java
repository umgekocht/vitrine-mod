package de.spassglas.vitrine.ui.dialog;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * ROADMAP GATE86: die Rueckfrage, bevor ein Download-Flyout-Eintrag
 * tatsaechlich abgebrochen/entfernt wird -- gebaut nach demselben Muster wie
 * {@link ConfirmUninstallDialog}: unten zwei gleichwertige Knoepfe, keiner
 * hervorgehoben, nichts vorausgewaehlt, Abbrechen zusaetzlich per ESC.
 */
public final class ConfirmCancelDownloadDialog extends Screen {

	private final Screen parentScreen;
	private final CancelDownloadConfirmModel model;

	private final Runnable themeRepaintListener = this::onThemeChanged;

	public ConfirmCancelDownloadDialog(Screen parentScreen, CancelDownloadConfirmModel model) {
		super(Text.translatable("vitrine.downloads.cancel_dialog.title"));
		this.parentScreen = parentScreen;
		this.model = Objects.requireNonNull(model, "model");
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	@Override
	public void removed() {
		ThemeService.instance().removeRepaintListener(themeRepaintListener);
		super.removed();
	}

	private void onThemeChanged() {
		if (this.client == null) {
			return;
		}
		this.client.execute(() -> {
			if (this.client != null && this.client.currentScreen == this) {
				this.clearChildren();
				this.init();
			}
		});
	}

	private static int lineHeight() {
		return VitrineTheme.TYPE_BODY + VitrineTheme.DETAIL_LINE_GAP;
	}

	private int panelHeight() {
		return VitrineTheme.DIALOG_PADDING * 2
				+ VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ lineHeight()
				+ VitrineTheme.DETAIL_SECTION_GAP
				+ VitrineTheme.MENU_BUTTON_HEIGHT;
	}

	private int panelLeft() {
		return (this.width - VitrineTheme.DIALOG_WIDTH) / 2;
	}

	private int panelTop() {
		return (this.height - panelHeight()) / 2;
	}

	@Override
	protected void init() {
		int buttonsTop = panelTop() + panelHeight() - VitrineTheme.DIALOG_PADDING - VitrineTheme.MENU_BUTTON_HEIGHT;
		int pairWidth = VitrineTheme.MENU_BUTTON_WIDTH * 2 + VitrineTheme.DIALOG_BUTTON_GAP;
		int pairLeft = this.width / 2 - pairWidth / 2;

		ButtonWidget keepButton = ButtonWidget
				.builder(Text.translatable("vitrine.downloads.cancel_dialog.keep"), b -> this.close())
				.dimensions(pairLeft, buttonsTop, VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		ButtonWidget confirmButton = ButtonWidget
				.builder(Text.translatable("vitrine.downloads.cancel_dialog.confirm"), b -> confirm())
				.dimensions(pairLeft + VitrineTheme.MENU_BUTTON_WIDTH + VitrineTheme.DIALOG_BUTTON_GAP, buttonsTop,
						VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();

		this.addDrawableChild(keepButton);
		this.addDrawableChild(confirmButton);
	}

	private void confirm() {
		model.confirm();
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public void close() {
		model.cancel();
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(0, 0, this.width, this.height, ActiveThemeColors.overlayScrim());

		int left = panelLeft();
		int top = panelTop();
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, left, top, VitrineTheme.DIALOG_WIDTH, panelHeight(), 12, 0xFF272A31);
		de.spassglas.vitrine.ui.theme.RoundedRect.border(context, left, top, VitrineTheme.DIALOG_WIDTH, panelHeight(), 12, 0xFF3A3F4C);

		int x = left + VitrineTheme.DIALOG_PADDING;
		int y = top + VitrineTheme.DIALOG_PADDING;

		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.downloads.cancel_dialog.title_for", model.displayName()), x, y,
				ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;

		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.downloads.cancel_dialog.body"), x, y,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);

		super.render(context, mouseX, mouseY, delta);
		renderFocusRing(context);
	}

	private void renderFocusRing(DrawContext context) {
		if (!(this.getFocused() instanceof ClickableWidget widget)) {
			return;
		}
		de.spassglas.vitrine.ui.theme.FocusRing.drawFor(context, widget);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Nur fuer Tests/Anbindung: das client-freie Zustandsmodell dieses Dialogs. */
	public CancelDownloadConfirmModel model() {
		return model;
	}
}
