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
 * ROADMAP GATE20: die Rueckfrage vor dem Deinstallieren. Gebaut nach genau
 * demselben Muster wie der Fähigkeiten-Dialog (docs/UI-SPEC.md
 * "8. Fähigkeiten-Dialog") -- unten <b>zwei gleichwertige Knöpfe</b>, links
 * "Abbrechen", rechts "Deinstallieren", gleiche Größe
 * ({@link VitrineTheme#MENU_BUTTON_WIDTH}/{@link VitrineTheme#MENU_BUTTON_HEIGHT}),
 * gleicher Stil, keiner hervorgehoben, nichts vorausgewählt. Abbrechen geht
 * zusätzlich per ESC und ist damit mindestens so leicht erreichbar wie das
 * Bestätigen.
 *
 * <p>Jede Farbe kommt aus {@link ActiveThemeColors}, jedes Maß aus
 * {@link VitrineTheme} (Charter-Regel 7); jeder sichtbare Text läuft über
 * einen Übersetzungs-Key (F3).</p>
 *
 * <p>Die Entscheidung selbst liegt im client-freien
 * {@link UninstallConfirmModel} -- dieser Bildschirm zeichnet sie nur.</p>
 */
public final class ConfirmUninstallDialog extends Screen {

	private final Screen parentScreen;
	private final UninstallConfirmModel model;

	/** ROADMAP T4: baut den Dialog bei einem Laufzeit-Theme-Wechsel neu auf. */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	public ConfirmUninstallDialog(Screen parentScreen, UninstallConfirmModel model) {
		super(Text.translatable("vitrine.uninstall.dialog.title"));
		this.parentScreen = parentScreen;
		this.model = Objects.requireNonNull(model, "model");
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	@Override
	public void removed() {
		ThemeService.instance().removeRepaintListener(themeRepaintListener);
		super.removed();
	}

	/**
	 * {@link ThemeService}-Rueckruf nach einem Laufzeit-Theme-Wechsel.
	 *
	 * <p><b>GATE113 (Thread-Modell, siehe {@code StoreScreen#onThemeChanged}
	 * fuer die volle Begruendung):</b> dieser Rueckruf laeuft bei
	 * Theme-Paketen auf dem Installations-Thread, nicht dem Render-Thread --
	 * {@code clearChildren()}/{@code init()} duerfen aber nur auf dem
	 * Client-Thread laufen. Deshalb einplanen statt direkt ausfuehren; die
	 * {@code currentScreen}-Pruefung erneut innerhalb der eingeplanten
	 * Aufgabe, der Bildschirm kann bis dahin gewechselt haben.</p>
	 */
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

		ButtonWidget cancelButton = ButtonWidget
				.builder(Text.translatable("vitrine.uninstall.dialog.cancel"), b -> this.close())
				.dimensions(pairLeft, buttonsTop, VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		ButtonWidget confirmButton = ButtonWidget
				.builder(Text.translatable("vitrine.uninstall.dialog.confirm"), b -> confirm())
				.dimensions(pairLeft + VitrineTheme.MENU_BUTTON_WIDTH + VitrineTheme.DIALOG_BUTTON_GAP, buttonsTop,
						VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();

		this.addDrawableChild(cancelButton);
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

	/**
	 * Vanilla legt in {@code super.render(...)} die Menue-Unschaerfe ueber alles
	 * bis dahin Gezeichnete -- bei Vitrine also ueber den eigenen Inhalt, der vor
	 * {@code super.render} gezeichnet wird (im Spiel: verschwommener Text). Den
	 * Hintergrund zeichnet dieser Screen selbst, darum hier bewusst nichts.
	 */
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
		// Rahmen steckt im panel_frame

		int x = left + VitrineTheme.DIALOG_PADDING;
		int y = top + VitrineTheme.DIALOG_PADDING;

		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.uninstall.dialog.title_for", model.displayName()), x, y,
				ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;

		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.uninstall.dialog.body"), x, y,
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

	/** Nur für Tests/Anbindung: das client-freie Zustandsmodell dieses Dialogs. */
	public UninstallConfirmModel model() {
		return model;
	}
}
