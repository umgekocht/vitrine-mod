package de.spassglas.vitrine.ui.dialog;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Fähigkeiten-Dialog (ROADMAP S4, docs/UI-SPEC.md "8. Fähigkeiten-Dialog"):
 * erscheint vor der ersten Installation eines Pakets, listet die im Manifest
 * angemeldeten Fähigkeiten als Klartext und hat unten <b>zwei gleichwertige
 * Knöpfe</b> -- links "Abbrechen", rechts "Installieren", gleiche Größe,
 * gleicher Stil, kein hervorgehobenes "OK" (Charter-Regel 2/4). Nichts ist
 * vorausgewählt; Abbrechen (Knopf oder ESC) führt zu <em>keiner</em>
 * Zustandsänderung.
 *
 * <p>Reines Anzeige-/Eingabe-Geruest: die Entscheidung "muss der Dialog
 * überhaupt gezeigt werden" trifft das client-freie {@link
 * CapabilityConsentModel}, und was beim Bestätigen passiert (Zustimmung
 * speichern + installieren), steckt in {@code onConfirm}.</p>
 */
public final class CapabilityConsentDialog extends Screen {

	private final Screen parentScreen;
	private final CapabilityConsentModel model;
	private final String packDisplayName;
	private final Runnable onConfirm;
	private final Runnable onCancel;

	private boolean confirmed;

	/**
	 * ROADMAP T4: baut den Dialog bei einem Laufzeit-Theme-Wechsel neu auf
	 * (Muster wie {@code StoreScreen}). In {@link #removed()} wieder abgemeldet.
	 */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	public CapabilityConsentDialog(Screen parentScreen, CapabilityConsentModel model, String packDisplayName,
			Runnable onConfirm, Runnable onCancel) {
		super(Text.translatable("vitrine.capabilities.dialog.title"));
		this.parentScreen = parentScreen;
		this.model = Objects.requireNonNull(model, "model");
		this.packDisplayName = Objects.requireNonNull(packDisplayName, "packDisplayName");
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
	 * {@link ThemeService}-Rueckruf nach einem Laufzeit-Theme-Wechsel: nur den
	 * sichtbaren Dialog neu aufbauen.
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

	private int panelHeight() {
		int rows = Math.max(1, model.requestedCapabilities().size());
		return VitrineTheme.DIALOG_PADDING * 2
				+ VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ rows * lineHeight()
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
				.builder(Text.translatable("vitrine.capabilities.dialog.cancel"), b -> this.close())
				.dimensions(pairLeft, buttonsTop, VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		ButtonWidget confirmButton = ButtonWidget
				.builder(Text.translatable("vitrine.capabilities.dialog.confirm"), b -> confirm())
				.dimensions(pairLeft + VitrineTheme.MENU_BUTTON_WIDTH + VitrineTheme.DIALOG_BUTTON_GAP, buttonsTop,
						VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();

		this.addDrawableChild(cancelButton);
		this.addDrawableChild(confirmButton);
	}

	private void confirm() {
		confirmed = true;
		// Erst zurueck zur Detailseite (zeigt den Fortschritt), dann installieren.
		// Vorher blieb der Dialog nach "Installieren" einfach stehen -- im Spiel
		// klickte man deshalb mehrmals.
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
		onConfirm.run();
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
				Text.translatable("vitrine.capabilities.dialog.title_for", packDisplayName), x, y,
				ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;

		if (model.requestedCapabilities().isEmpty()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.capabilities.dialog.none"), x, y,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		} else {
			for (VpkgCapability capability : model.requestedCapabilities()) {
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(
						Identifier.of("vitrine", "textures/gui/icons/category_" + capability.id() + ".png"),
						x, y, VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE, VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE,
						0.0F, 0.0F, VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
						VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
				ScaledText.draw(context, this.textRenderer, Text.translatable(capability.translationKey()),
						x + VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE + VitrineTheme.SPACE_UNIT, y,
						ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
				y += lineHeight();
			}
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

	/** Nur für Tests/Anbindung: das client-freie Zustandsmodell dieses Dialogs. */
	public CapabilityConsentModel model() {
		return model;
	}
}
