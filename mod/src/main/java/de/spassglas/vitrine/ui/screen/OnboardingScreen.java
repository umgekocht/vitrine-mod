package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.config.SettingsModel;
import de.spassglas.vitrine.ui.onboarding.OnboardingModel;
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

import java.util.List;

/**
 * Erster-Start-Ablauf (ROADMAP X4): erscheint genau einmal, bevor der Store
 * (Taste M bzw. Menue-Knopf, siehe {@code StoreEntryPoint}) sich zum ersten
 * Mal oeffnet, und erklaert in einfachen Saetzen Klasse A/B und das
 * Faehigkeiten-Prinzip -- die drei Abschnitte kommen wortgleich aus dem
 * client-freien {@link OnboardingModel}.
 *
 * <p>Der eine Knopf ("vitrine.onboarding.continue") markiert
 * {@code onboardingSeen=true} (persistiert ueber das client-freie
 * {@link SettingsModel}) und oeffnet danach den echten {@link StoreScreen}.
 * ESC bricht ab, ohne den Zustand zu aendern -- der Ablauf erscheint dann
 * beim naechsten Oeffnen erneut, es gibt keinen heimlichen Weg, ihn
 * ungesehen zu uebergehen (kein Fake-Fortschritt, Charter-Regel 2).</p>
 */
public final class OnboardingScreen extends Screen {

	private final Screen parentScreen;
	private final SettingsModel model;

	private int scrollY;
	private int contentHeight;

	/** ROADMAP T4: baut den Bildschirm bei einem Laufzeit-Theme-Wechsel neu auf (Muster wie andere Screens). */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	public OnboardingScreen(Screen parentScreen) {
		this(parentScreen, SettingsModel.forConfigDir(
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID)));
	}

	/** Testbarer/entkoppelter Konstruktor. */
	public OnboardingScreen(Screen parentScreen, SettingsModel model) {
		super(Text.translatable("vitrine.onboarding.title"));
		this.parentScreen = parentScreen;
		this.model = model;
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

	@Override
	protected void init() {
		int buttonWidth = VitrineTheme.MENU_BUTTON_WIDTH;
		int buttonX = this.width / 2 - buttonWidth / 2;
		// Fest am unteren Rand: der Knopf scrollt nicht mit dem Text. Vorher
		// stand er unter dem Text -- bei langem Text unterhalb des Fensters,
		// wo er (unverschoben gezeichnet) nie erreichbar war.
		int buttonY = this.height - VitrineTheme.SPACE_PAGE - VitrineTheme.MENU_BUTTON_HEIGHT;

		ButtonWidget continueButton = ButtonWidget
				.builder(Text.translatable("vitrine.onboarding.continue"), b -> onContinue())
				.dimensions(buttonX, buttonY, buttonWidth, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		this.addDrawableChild(continueButton);

		// Scrollbar so weit, dass der letzte Abschnitt oberhalb der Fusszeile steht.
		contentHeight = contentBottom() + footerHeight();
		clampScroll();
	}

	private void rebuild() {
		this.clearChildren();
		this.init();
	}

	/** y-Kante direkt unterhalb des letzten Erklaerabschnitts -- Grundlage fuer den Knopf und die Inhaltshoehe. */
	private int contentBottom() {
		int width = textWidth();
		int y = VitrineTheme.SPACE_PAGE
				+ ScaledText.wrappedHeight(this.textRenderer, this.title, width, VitrineTheme.TYPE_TITLE_LARGE)
				+ VitrineTheme.DETAIL_HEADING_GAP
				+ ScaledText.wrappedHeight(this.textRenderer, Text.translatable("vitrine.onboarding.intro"), width,
						VitrineTheme.TYPE_BODY)
				+ VitrineTheme.DETAIL_SECTION_GAP;
		for (OnboardingModel.Section section : OnboardingModel.sections()) {
			y += ScaledText.wrappedHeight(this.textRenderer, Text.translatable(section.titleKey()), width,
					VitrineTheme.TYPE_SUBTITLE)
					+ VitrineTheme.DETAIL_HEADING_GAP
					+ ScaledText.wrappedHeight(this.textRenderer, Text.translatable(section.bodyKey()), width,
							VitrineTheme.TYPE_BODY)
					+ VitrineTheme.DETAIL_SECTION_GAP;
		}
		return y - VitrineTheme.DETAIL_SECTION_GAP;
	}

	/** Hoehe der festen Fusszeile mit dem Weiter-Knopf. */
	private int footerHeight() {
		return VitrineTheme.MENU_BUTTON_HEIGHT + 2 * VitrineTheme.SPACE_PAGE;
	}

	/** Breite, an der die Texte umbrechen: Fensterbreite minus Seitenrand links und rechts. */
	private int textWidth() {
		return Math.max(1, this.width - 2 * VitrineTheme.SPACE_PAGE);
	}

	/** Bestaetigt den Ablauf: Zustand persistieren, danach den echten Store oeffnen. */
	private void onContinue() {
		model.setOnboardingSeen(true);
		Vitrine.LOGGER.info("Onboarding: Erster-Start-Ablauf bestaetigt.");
		if (this.client != null) {
			this.client.setScreen(new StoreScreen(parentScreen));
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			onContinue();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		// Bewusst kein setOnboardingSeen(true): ESC ist kein heimlicher
		// Weg, den Ablauf ungesehen zu uebergehen -- beim naechsten
		// Oeffnen erscheint er erneut.
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}
		if (verticalAmount == 0.0) {
			return false;
		}
		scrollY -= (int) Math.signum(verticalAmount) * VitrineTheme.DETAIL_SCROLL_STEP;
		clampScroll();
		return true;
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, contentHeight - this.height);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));
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
		context.fill(0, 0, this.width, this.height, ActiveThemeColors.bg());

		context.getMatrices().push();
		context.getMatrices().translate(0, -scrollY, 0);
		renderContent(context);
		context.getMatrices().pop();

		// Fusszeile deckt durchscrollenden Text ab, damit der Knopf frei steht.
		int footerTop = this.height - footerHeight();
		context.fill(0, footerTop, this.width, this.height, ActiveThemeColors.bg());
		context.fill(0, footerTop, this.width, footerTop + 1, ActiveThemeColors.borderTranslucent());

		super.render(context, mouseX, mouseY, delta);
	}

	private void renderContent(DrawContext context) {
		int x = VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.SPACE_PAGE;
		int width = textWidth();

		y += ScaledText.drawWrapped(context, this.textRenderer, this.title, x, y, width,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE_LARGE);
		y += VitrineTheme.DETAIL_HEADING_GAP;

		y += ScaledText.drawWrapped(context, this.textRenderer, Text.translatable("vitrine.onboarding.intro"), x, y,
				width, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		y += VitrineTheme.DETAIL_SECTION_GAP;

		List<OnboardingModel.Section> sections = OnboardingModel.sections();
		for (OnboardingModel.Section section : sections) {
			y += ScaledText.drawWrapped(context, this.textRenderer, Text.translatable(section.titleKey()), x, y,
					width, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
			y += VitrineTheme.DETAIL_HEADING_GAP;

			y += ScaledText.drawWrapped(context, this.textRenderer, Text.translatable(section.bodyKey()), x, y,
					width, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			y += VitrineTheme.DETAIL_SECTION_GAP;
		}
	}
}
