package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.config.SettingsModel;
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

import java.util.function.IntUnaryOperator;

/**
 * Vitrine-Einstellungen. Zwei Schalter, je ein Abschnitt (Titel, Schalter,
 * Klartext-Erklaerung):
 * <ol>
 *   <li>"Animationen reduzieren" (ROADMAP S9, docs/UI-SPEC.md "Bewegung":
 *       "alles sofort, ohne Uebergang").</li>
 *   <li>"Java-Mods anzeigen" (ROADMAP J1): Opt-in fuer Klasse-B-Angebote.
 *       Default aus -- ohne den Schalter taucht kein {@code .jar}-Angebot im
 *       Store auf. Die Erklaerung sagt ehrlich, dass diese Angebote einen
 *       Neustart brauchen und nicht sofort ("hot") aktiv werden
 *       (Charter-Regel 2).</li>
 * </ol>
 *
 * <p>Zusaetzlich ein Knopf "Diagnose" (ROADMAP X3), der den {@link
 * DiagnosticsScreen} oeffnet -- {@code docs/UI-SPEC.md} kennt fuer X3 keinen
 * eigenen Nav-Rail-Eintrag, deshalb haengt dieser Bildschirm am bereits ueber
 * Nav-Rail und Kopfleisten-Knopf erreichbaren "Einstellungen"-Ziel.</p>
 *
 * <p>Erreichbar aus dem {@code StoreScreen} sowohl ueber den Kopfleisten-Knopf
 * "Einstellungen" als auch ueber den Nav-Rail-Eintrag. Die Schalter-Logik und
 * die Persistenz liegen im client-freien {@link SettingsModel}; dieser Screen
 * bindet nur Zeichnen und Eingabe daran. Der Schreibvorgang geht ueber
 * {@code SettingsManager.save} (atomar) -- ein offener Store liest die Schalter
 * bei seinem naechsten Aufbau frisch und uebernimmt die Aenderung sofort.</p>
 */
public final class SettingsScreen extends Screen {

	/**
	 * Hoehe eines Abschnitts (Titel, Abstand, Schalter, Abstand, Erklaerung),
	 * ausschliesslich aus Theme-Tokens. Zwischen zwei Abschnitten liegt
	 * zusaetzlich {@link VitrineTheme#DETAIL_SECTION_GAP}.
	 *
	 * <p>ROADMAP GATE53: Methode statt einmalig beim Klassenladen gecachtem
	 * Feld -- {@link VitrineTheme#TYPE_SUBTITLE}/{@link
	 * VitrineTheme#DETAIL_HEADING_GAP}/{@link VitrineTheme#MENU_BUTTON_HEIGHT}/
	 * {@link VitrineTheme#TYPE_CAPTION} sind seit GATE21 zur Laufzeit
	 * veraenderlich ({@link VitrineTheme#applyFrom}); ein vorher gecachtes
	 * {@code static final int} wurde nach einem Theme-Wechsel nie neu
	 * berechnet und liess den Abschnittsabstand hinter dem neuen Theme
	 * zurueck.
	 */
	private static int sectionHeight(int descriptionHeight) {
		return VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.DETAIL_HEADING_GAP
				+ descriptionHeight;
	}

	/** Abschnitte in Anzeigereihenfolge: Titel- und Erklaerungs-Schluessel. */
	private static final String[][] SECTIONS = {
			{"vitrine.settings.reduce_motion", "vitrine.settings.reduce_motion.description"},
			{"vitrine.settings.show_java_mods", "vitrine.settings.show_java_mods.description"},
			{"vitrine.settings.diagnostics", "vitrine.settings.diagnostics.description"},
	};

	/**
	 * Wie weit der Inhalt nach oben gescrollt ist. Im Spiel gefunden
	 * (2026-10-06): bei hoher GUI-Skalierung passten nicht alle Abschnitte ins
	 * Fenster, und der Bildschirm liess sich nicht scrollen.
	 */
	private int scrollY;

	/** Kippschalter in umgekocht's Nenngroesse 52x32 (assets v2), bei GUI-Skalierung 2 halbiert. */
	private static final int TOGGLE_W = 26;
	private static final int TOGGLE_H = 16;

	private final Screen parentScreen;
	private final SettingsModel model;

	public SettingsScreen(Screen parentScreen) {
		this(parentScreen, SettingsModel.forConfigDir(
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID)));
	}

	/**
	 * ROADMAP T4: baut den Bildschirm bei einem Laufzeit-Theme-Wechsel neu auf
	 * (Muster wie {@code StoreScreen}). In {@link #removed()} wieder abgemeldet.
	 */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	/** Testbarer/entkoppelter Konstruktor. */
	public SettingsScreen(Screen parentScreen, SettingsModel model) {
		super(Text.translatable("vitrine.settings.title"));
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
	 * {@link ThemeService}-Rueckruf nach einem Laufzeit-Theme-Wechsel: nur den
	 * sichtbaren Bildschirm neu aufbauen.
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
		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		var backButton = de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(VitrineTheme.EDGE_PADDING, buttonY, VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), b -> this.close(), net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_back.png"), null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		this.addDrawableChild(backButton);

		clampScroll();
		int x = VitrineTheme.SPACE_PAGE;

		ButtonWidget reduceMotionToggle = ButtonWidget
				.builder(reduceMotionLabel(), b -> onToggleReduceMotion())
				.dimensions(x, sectionButtonY(0) + (VitrineTheme.MENU_BUTTON_HEIGHT - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H)
				.build();
		de.spassglas.vitrine.ui.theme.VitrineButtonStyle.toggle(reduceMotionToggle, model::reduceMotion);
		addSectionButton(reduceMotionToggle);

		ButtonWidget showJavaModsToggle = ButtonWidget
				.builder(showJavaModsLabel(), b -> onToggleShowJavaMods())
				.dimensions(x, sectionButtonY(1) + (VitrineTheme.MENU_BUTTON_HEIGHT - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H)
				.build();
		de.spassglas.vitrine.ui.theme.VitrineButtonStyle.toggle(showJavaModsToggle, model::showJavaModOffers);
		addSectionButton(showJavaModsToggle);

		ButtonWidget diagnosticsButton = ButtonWidget
				.builder(Text.translatable("vitrine.settings.diagnostics"), b -> openDiagnostics())
				.dimensions(x, sectionButtonY(2), VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		addSectionButton(diagnosticsButton);
	}

	/** Schalter, die unter die feste Kopfleiste gescrollt sind, werden weder gezeichnet noch angeklickt. */
	private void addSectionButton(ButtonWidget button) {
		button.visible = button.getY() >= VitrineTheme.HEADER_HEIGHT;
		this.addDrawableChild(button);
	}

	/** Breite, an der die Erklaerungen umbrechen. */
	private int textWidth() {
		return Math.max(1, this.width - 2 * VitrineTheme.SPACE_PAGE);
	}

	/** Hoehe der (umgebrochenen) Erklaerung des Abschnitts {@code index}. */
	private int descriptionHeight(int index) {
		if (this.textRenderer == null) {
			return VitrineTheme.TYPE_CAPTION;
		}
		return ScaledText.wrappedHeight(this.textRenderer, Text.translatable(SECTIONS[index][1]), textWidth(),
				VitrineTheme.TYPE_CAPTION);
	}

	/** Untere Kante des Inhalts ohne Scroll -- Grundlage fuer die Scrollgrenze. */
	private int contentBottom() {
		return layoutTop(SECTIONS.length, this::descriptionHeight) + VitrineTheme.SPACE_PAGE;
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, contentBottom() - this.height);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount == 0) {
			return false;
		}
		scrollY -= (int) Math.signum(verticalAmount) * VitrineTheme.DETAIL_SCROLL_STEP;
		rebuild();
		return true;
	}

	/** Obere Kante des {@code index}-ten Abschnitts ohne Scroll, bei gegebener Erklaerungshoehe je Abschnitt. */
	private static int layoutTop(int index, IntUnaryOperator descriptionHeight) {
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		for (int i = 0; i < index; i++) {
			y += sectionHeight(descriptionHeight.applyAsInt(i)) + VitrineTheme.DETAIL_SECTION_GAP;
		}
		return y;
	}

	/** Obere Kante des {@code index}-ten Einstellungs-Abschnitts (0-basiert), gescrollt. */
	private int sectionTop(int index) {
		return layoutTop(index, this::descriptionHeight) - scrollY;
	}

	/**
	 * Nur fuer Tests/Anbindung (ROADMAP GATE53): dieselbe obere Kante wie
	 * {@link #sectionTop(int)}, ohne einen vollen, clientfreien Bildschirm
	 * aufzubauen -- beweist, dass {@link #sectionHeight()} bei jedem Aufruf neu
	 * aus den aktuellen {@link VitrineTheme}-Feldern rechnet, statt einen
	 * Theme-Wechsel in einem beim Klassenladen gecachten Wert zu verpassen.
	 */
	static int sectionTopForTest(int index) {
		return layoutTop(index, i -> VitrineTheme.TYPE_CAPTION);
	}

	private int sectionButtonY(int index) {
		return sectionTop(index) + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
	}

	private int sectionDescriptionY(int index) {
		return sectionButtonY(index) + VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.DETAIL_HEADING_GAP;
	}

	private Text reduceMotionLabel() {
		String stateKey = model.reduceMotion() ? "vitrine.settings.on" : "vitrine.settings.off";
		return Text.translatable("vitrine.settings.reduce_motion.state", Text.translatable(stateKey));
	}

	private void onToggleReduceMotion() {
		boolean now = model.toggleReduceMotion();
		Vitrine.LOGGER.info("Einstellungen: 'Animationen reduzieren' ist jetzt {}.", now ? "an" : "aus");
		rebuild();
	}

	private Text showJavaModsLabel() {
		String stateKey = model.showJavaModOffers() ? "vitrine.settings.on" : "vitrine.settings.off";
		return Text.translatable("vitrine.settings.show_java_mods.state", Text.translatable(stateKey));
	}

	private void onToggleShowJavaMods() {
		boolean now = model.toggleShowJavaModOffers();
		Vitrine.LOGGER.info("Einstellungen: 'Java-Mods anzeigen' ist jetzt {}.", now ? "an" : "aus");
		rebuild();
	}

	/** ROADMAP X3: oeffnet den Diagnose-Bildschirm. */
	private void openDiagnostics() {
		if (this.client != null) {
			this.client.setScreen(new DiagnosticsScreen(this));
		}
	}

	private void rebuild() {
		this.clearChildren();
		this.init();
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
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
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

		int x = VitrineTheme.SPACE_PAGE;

		for (int i = 0; i < SECTIONS.length; i++) {
			drawSection(context, x, i, SECTIONS[i][0], SECTIONS[i][1]);
		}

		context.fill(0, 0, this.width, VitrineTheme.HEADER_HEIGHT, ActiveThemeColors.surfaceSolid());
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, this.width, VitrineTheme.HEADER_HEIGHT,
				ActiveThemeColors.borderTranslucent());
		ScaledText.draw(context, this.textRenderer, this.title,
				VitrineTheme.EDGE_PADDING * 2 + VitrineTheme.ICON_BUTTON_SIZE,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_TITLE) / 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);

		// Alle interaktiven Elemente dieses Bildschirms sind Vanilla-ButtonWidgets
		// -- die bringen ihren Fokusrahmen selbst mit (docs/UI-SPEC.md).
		super.render(context, mouseX, mouseY, delta);
	}

	/** Zeichnet Abschnitts-Titel und Erklaerung; der Schalter dazwischen ist ein {@link ButtonWidget} aus {@link #init()}. */
	private void drawSection(DrawContext context, int x, int index, String titleKey, String descriptionKey) {
		ScaledText.draw(context, this.textRenderer, Text.translatable(titleKey),
				x, sectionTop(index), ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		ScaledText.drawWrapped(context, this.textRenderer, Text.translatable(descriptionKey),
				x, sectionDescriptionY(index), textWidth(), ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
	}
}
