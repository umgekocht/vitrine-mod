package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.hotapply.HotApplyCoordinator;
import de.spassglas.vitrine.ui.diagnostics.DiagnosticsEntry;
import de.spassglas.vitrine.ui.diagnostics.DiagnosticsModel;
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
import java.util.Optional;

/**
 * Diagnose-Bildschirm (ROADMAP X3): "was ist installiert, was hat den Reload
 * gekostet, welche Datei kam von wo". Woertliches Fertig-Kriterium: "Zeigt
 * fuer jedes aktive Paket Quelle, Version und Pruefsumme."
 *
 * <p>Erreichbar aus dem Nav-Rail-Eintrag "Einstellungen" ueber einen eigenen
 * Knopf im {@link SettingsScreen} -- {@code docs/UI-SPEC.md} kennt keinen
 * eigenen Nav-Rail-Eintrag dafuer (verbindliches Layout, ROADMAP-Zeile bringt
 * keinen eigenen Spec-Abschnitt mit), das bestehende "Einstellungen"-Nav-Ziel
 * ist bereits ueber Nav-Rail und Kopfleisten-Knopf erreichbar.</p>
 *
 * <p>Alle Daten liefert das client-freie {@link DiagnosticsModel}; dieser
 * Screen bindet nur Zeichnen und Scrollen daran. Jede Zahl/Farbe stammt aus
 * {@link VitrineTheme} (dieselben Bibliotheks-Zeilen-Tokens wie {@link
 * LibraryScreen}), jeder feste Anzeigetext aus einem Uebersetzungs-Key -- die
 * paket-eigenen Freitexte (Name/Autor/Version/Pruefsumme/Host) werden als
 * {@code Text.literal(<variable>)} gesetzt, wie in {@link LibraryScreen}.</p>
 */
public final class DiagnosticsScreen extends Screen {

	private final Screen parentScreen;
	private final DiagnosticsModel model;

	private List<DiagnosticsEntry> entries = List.of();
	private Optional<HotApplyCoordinator.ReloadStats> lastReload = Optional.empty();

	private int scrollY;
	private int contentHeight;

	public DiagnosticsScreen(Screen parentScreen) {
		this(parentScreen, DiagnosticsModel.forConfigDir(
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID)));
	}

	/** Testbarer/entkoppelter Konstruktor. */
	public DiagnosticsScreen(Screen parentScreen, DiagnosticsModel model) {
		super(Text.translatable("vitrine.diagnostics.title"));
		this.parentScreen = parentScreen;
		this.model = model;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	/** ROADMAP T4: baut den Bildschirm bei einem Laufzeit-Theme-Wechsel neu auf (Muster wie {@code LibraryScreen}). */
	private final Runnable themeRepaintListener = this::onThemeChanged;

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
		entries = model.activePackages();
		lastReload = model.lastReload();

		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		var backButton = de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(VitrineTheme.EDGE_PADDING, buttonY, VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), b -> this.close(), net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_back.png"), null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		this.addDrawableChild(backButton);

		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		// Reload-Abschnitt: Ueberschrift + eine Zeile.
		int cursorY = y + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP
				+ VitrineTheme.TYPE_BODY + VitrineTheme.LIBRARY_SECTION_GAP;

		// Pakete-Abschnitt: Ueberschrift, dann eine Zeile je aktivem Paket
		// (Name/Meta + Quelle/Pruefsumme je zwei Textzeilen).
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		if (entries.isEmpty()) {
			cursorY += VitrineTheme.TYPE_BODY;
		} else {
			cursorY += entries.size() * (VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP);
		}

		contentHeight = cursorY + VitrineTheme.SPACE_PAGE;
		clampScroll();
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

	// --- Scrollen ------------------------------------------------------

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return true;
		}
		if (verticalAmount == 0.0) {
			return false;
		}
		scrollY -= (int) Math.signum(verticalAmount) * VitrineTheme.LIBRARY_SCROLL_STEP;
		clampScroll();
		return true;
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, contentHeight - this.height);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));
	}

	// --- Zeichnen --------------------------------------------------------

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

		context.fill(0, 0, this.width, VitrineTheme.HEADER_HEIGHT, ActiveThemeColors.surfaceSolid());
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, this.width, VitrineTheme.HEADER_HEIGHT,
				ActiveThemeColors.borderTranslucent());
		ScaledText.draw(context, this.textRenderer, this.title,
				VitrineTheme.EDGE_PADDING * 2 + VitrineTheme.ICON_BUTTON_SIZE,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_TITLE) / 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);

		super.render(context, mouseX, mouseY, delta);
	}

	private void renderContent(DrawContext context) {
		int x = VitrineTheme.SPACE_PAGE;
		int cursorY = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;

		// --- Abschnitt: Reload ---
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.diagnostics.section.reload"),
				x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		ScaledText.draw(context, this.textRenderer, reloadSummary(),
				x, cursorY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		cursorY += VitrineTheme.TYPE_BODY + VitrineTheme.LIBRARY_SECTION_GAP;

		// --- Abschnitt: aktive Pakete ---
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.diagnostics.section.packages"),
				x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;

		if (entries.isEmpty()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.diagnostics.empty"),
					x, cursorY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			return;
		}

		for (DiagnosticsEntry entry : entries) {
			cursorY = renderEntry(context, entry, x, cursorY);
		}
	}

	private int renderEntry(DrawContext context, DiagnosticsEntry entry, int x, int y) {
		int nameColor = entry.missing() ? ActiveThemeColors.danger() : ActiveThemeColors.text();
		ScaledText.draw(context, this.textRenderer, Text.literal(entry.name()), x, y, nameColor, VitrineTheme.TYPE_BODY);
		int lineY = y + VitrineTheme.TYPE_BODY + VitrineTheme.CARD_TEXT_LINE_GAP;

		// ROADMAP GATE115: ein aktiv gefuehrtes, aber verwaistes Paket (keine
		// .vpkg in packs/ gefunden, kein Backup wiederherstellbar) zeigt hier
		// den Klartext-Hinweis statt Version/Autor/Quelle/Pruefsumme vorzugaukeln.
		if (entry.missing()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.diagnostics.missing"),
					x, lineY, ActiveThemeColors.danger(), VitrineTheme.TYPE_CAPTION);
			return y + VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
		}

		String meta = "v" + entry.version() + "  ·  " + entry.author();
		ScaledText.draw(context, this.textRenderer, Text.literal(meta),
				x, lineY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		lineY += VitrineTheme.TYPE_CAPTION + VitrineTheme.CARD_TEXT_LINE_GAP;

		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.diagnostics.label.source", sourceText(entry.source())),
				x, lineY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		lineY += VitrineTheme.TYPE_CAPTION + VitrineTheme.CARD_TEXT_LINE_GAP;

		String checksum = entry.sha256() != null ? entry.sha256() : "?";
		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.diagnostics.label.checksum", Text.literal(checksum)),
				x, lineY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);

		return y + VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
	}

	private Text sourceText(DiagnosticsEntry.Source source) {
		return switch (source.kind()) {
			case LOCAL_LIBRARY -> Text.translatable("vitrine.diagnostics.source.library");
			case CATALOG -> Text.translatable("vitrine.diagnostics.source.catalog",
					Text.literal(source.detail().orElse("")));
			case UNKNOWN -> Text.translatable("vitrine.diagnostics.source.unknown");
		};
	}

	private Text reloadSummary() {
		if (lastReload.isEmpty()) {
			return Text.translatable("vitrine.diagnostics.reload.none");
		}
		HotApplyCoordinator.ReloadStats stats = lastReload.get();
		String key = stats.success() ? "vitrine.diagnostics.reload.success" : "vitrine.diagnostics.reload.failure";
		return Text.translatable(key, stats.packageCount(), stats.durationMillis());
	}
}
