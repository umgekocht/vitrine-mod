package de.spassglas.vitrine.ui.classb;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP J2 -- Klasse-B-Bestaetigungsdialog vor dem Staging (Muster wie
 * {@code CapabilityConsentDialog}, docs/UI-SPEC.md "8. Faehigkeiten-Dialog"):
 * Titel "&lt;Name&gt; installieren?", Autorenanzeige "von &lt;Autor&gt;", der
 * <b>woertliche</b> Klartext-Warntext ("Diese Datei ist ein Java-Mod. ..."),
 * unten <b>zwei gleichwertige Knoepfe</b> -- links "Abbrechen", rechts
 * "Installieren", gleiche Groesse, gleicher Stil, nichts vorausgewaehlt.
 * Abbrechen (Knopf oder ESC) fuehrt zu keiner Aenderung.
 *
 * <p>Reines Anzeige-/Eingabe-Geruest: was beim Bestaetigen passiert (Staging
 * anstossen) steckt im {@code onConfirm}-Rueckruf des Aufrufers.</p>
 */
public final class JavaModConfirmDialog extends Screen {

	private final Screen parentScreen;
	private final String displayName;
	private final String author;
	private final Runnable onConfirm;
	private final Runnable onCancel;

	private boolean confirmed;

	private final Runnable themeRepaintListener = this::onThemeChanged;

	public JavaModConfirmDialog(Screen parentScreen, String displayName, String author,
			Runnable onConfirm, Runnable onCancel) {
		super(Text.translatable("vitrine.classb.confirm.title", Objects.requireNonNull(displayName, "displayName")));
		this.parentScreen = parentScreen;
		this.displayName = displayName;
		this.author = Objects.requireNonNull(author, "author");
		this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
		this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
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

	private int innerWidth() {
		return VitrineTheme.DIALOG_WIDTH - VitrineTheme.DIALOG_PADDING * 2;
	}

	private List<String> warningLines() {
		return wrap(Text.translatable("vitrine.classb.install_warning").getString(), innerWidth());
	}

	private int panelHeight() {
		return VitrineTheme.DIALOG_PADDING * 2
				+ VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ VitrineTheme.TYPE_CAPTION + VitrineTheme.DETAIL_SECTION_GAP
				+ warningLines().size() * lineHeight()
				+ VitrineTheme.DETAIL_SECTION_GAP
				+ VitrineTheme.MENU_BUTTON_HEIGHT;
	}

	private static int lineHeight() {
		return VitrineTheme.TYPE_BODY + VitrineTheme.DETAIL_LINE_GAP;
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
				.builder(Text.translatable("vitrine.classb.confirm.cancel"), b -> this.close())
				.dimensions(pairLeft, buttonsTop, VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		ButtonWidget confirmButton = ButtonWidget
				.builder(Text.translatable("vitrine.classb.confirm.install"), b -> confirm())
				.dimensions(pairLeft + VitrineTheme.MENU_BUTTON_WIDTH + VitrineTheme.DIALOG_BUTTON_GAP, buttonsTop,
						VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();

		this.addDrawableChild(cancelButton);
		this.addDrawableChild(confirmButton);
	}

	private void confirm() {
		confirmed = true;
		onConfirm.run();
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public void close() {
		if (!confirmed) {
			onCancel.run();
		}
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
				Text.translatable("vitrine.classb.confirm.title", displayName), x, y,
				ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;

		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.classb.author", author), x, y,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		y += VitrineTheme.TYPE_CAPTION + VitrineTheme.DETAIL_SECTION_GAP;

		for (String line : warningLines()) {
			ScaledText.draw(context, this.textRenderer, Text.literal(line), x, y,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			y += lineHeight();
		}

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

	/** Wort-Umbruch bei {@link VitrineTheme#TYPE_BODY}-Groesse auf {@code maxWidth} Pixel. */
	private List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (!current.isEmpty()
					&& ScaledText.width(this.textRenderer, candidate, VitrineTheme.TYPE_BODY) > maxWidth) {
				lines.add(current.toString());
				current = new StringBuilder(word);
			} else {
				current = new StringBuilder(candidate);
			}
		}
		if (!current.isEmpty()) {
			lines.add(current.toString());
		}
		return lines;
	}
}
