package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.RegistryPackageClient;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;
import de.spassglas.vitrine.catalog.github.GitHubRepoStats;
import de.spassglas.vitrine.catalog.github.GitHubRepoStatsClient;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.classb.RestartRequiredBannerWidget;
import de.spassglas.vitrine.ui.classb.ClassBServices;
import de.spassglas.vitrine.config.SettingsModel;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.classb.StagedMod;
import de.spassglas.vitrine.classb.RegistryJavaModStaging;
import de.spassglas.vitrine.classb.JavaModStagingException;
import de.spassglas.vitrine.catalog.RegistryPackageVersion;
import de.spassglas.vitrine.catalog.download.CancellationToken;
import de.spassglas.vitrine.catalog.download.DownloadProgress;
import de.spassglas.vitrine.catalog.download.DownloadRequest;
import de.spassglas.vitrine.pack.CatalogConsentRequiredException;
import de.spassglas.vitrine.pack.CatalogPackInstallService;
import de.spassglas.vitrine.pack.PackInstallCancelledException;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.PackInstallService;
import de.spassglas.vitrine.pack.PackInstaller;
import de.spassglas.vitrine.pack.PackUninstallService;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.detail.PackageActionState;
import de.spassglas.vitrine.ui.detail.PackageDetailFact;
import de.spassglas.vitrine.ui.detail.PackageDetailHeader;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.detail.PackageInstallPhase;
import de.spassglas.vitrine.ui.detail.ScreenshotGalleryWidget;
import de.spassglas.vitrine.ui.dialog.CapabilityConsentDialog;
import de.spassglas.vitrine.ui.dialog.CapabilityConsentModel;
import de.spassglas.vitrine.ui.dialog.ConfirmUninstallDialog;
import de.spassglas.vitrine.ui.dialog.UninstallConfirmModel;
import de.spassglas.vitrine.ui.downloads.DownloadFlyoutService;
import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.FocusRing;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileRowWidget;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Store-Detailseite eines einzelnen Pakets (ROADMAP S3). Baut die Abschnitte
 * strikt in der Reihenfolge aus {@code docs/UI-SPEC.md} ("5. Detailseite")
 * auf: Kopfbereich, Aktionsknopf, Screenshot-Galerie, Beschreibung, "Das darf
 * dieses Paket", Details, Änderungen, weitere Pakete des Autors. Die Daten und
 * ihre Reihenfolge liefert das client-freie {@link PackageDetailModel}; dieser
 * Screen bindet nur das Zeichnen und Minecrafts Eingabe daran.
 *
 * <p>Jede Zahl, jede Farbe stammt aus {@link VitrineTheme}; alle Chrome-Texte
 * laufen ueber Uebersetzungs-Keys, nur die paket-eigenen Freitexte (Name,
 * Autor, Beschreibung, Changelog) werden als {@code Text.literal(<variable>)}
 * gesetzt.</p>
 *
 * <p>Der Aktionsknopf ruft (ROADMAP S4) die echten Dienste: {@code INSTALL}/
 * {@code UPDATE} starten -- nach dem Fähigkeiten-Dialog, falls den angemeldeten
 * Fähigkeiten noch nicht zugestimmt wurde -- {@link PackInstallService};
 * {@code INSTALLED} schaltet über {@code setActive} an/aus, daneben steht
 * "Deinstallieren" ({@link PackUninstallService}). {@code WORKING}/{@code
 * RESTART_REQUIRED} bleiben deaktiviert (kein Knopf, der nichts tut,
 * Charter-Regel 2).</p>
 */
public final class PackageDetailScreen extends Screen {

	private static final Identifier PLACEHOLDER_CARD =
			Identifier.of("vitrine", "textures/gui/placeholder_card.png");

	private final Screen parentScreen;
	/**
	 * GATE70: nicht mehr {@code final} -- {@link #maybeFetchRepoStats()} ersetzt
	 * das Modell nach einem beendeten GitHub-Abruf durch {@link
	 * PackageDetailModel#withRepoStats}, derselbe Kopf mit frisch bekannten
	 * Sternen/Downloads.
	 */
	private PackageDetailModel model;
	/** GATE6: Neustart-Banner, nur bei Klasse-B-Angeboten mit Vormerkungen; sonst {@code null}. */
	private RestartRequiredBannerWidget restartBanner;
	/**
	 * ROADMAP G3: {@code true}, wenn dieses Modell aus einem Katalog-Eintrag
	 * gebaut wurde ({@code CatalogPackageDetailSource}) statt aus einer
	 * lokalen {@code library/}-Datei -- der Aktionsknopf muss dann ueber
	 * {@link CatalogPackInstallService} laden+installieren (Download +
	 * Signaturpruefung gegen den eingebetteten Registry-Schluessel), nicht
	 * ueber {@link PackInstallService} (setzt eine bereits lokal vorliegende
	 * Datei voraus).
	 */
	private final boolean fromCatalog;

	/**
	 * ROADMAP S7: gesetzt, wenn das Paket nicht geladen werden konnte -- dann
	 * zeigt die Seite statt der Abschnitte den "Paket kaputt"-Zustand
	 * ("&lt;Name&gt; konnte nicht geladen werden", genau ein Knopf "Details").
	 * {@code null} im Normalbetrieb.
	 */
	private final StoreStateView brokenState;
	/** Ob der "Details"-Knopf des Paket-kaputt-Zustands den Hinweis auf das Spiel-Protokoll aufgedeckt hat. */
	private boolean brokenDetailsShown;

	private net.minecraft.client.gui.widget.ClickableWidget backButton;
	private ButtonWidget actionButton;
	private ButtonWidget uninstallButton;
	/** GATE69: Kopier-Knopf der Pruefsumme im "Details"-Abschnitt, oder {@code null} ohne bekannte Pruefsumme. */
	private ButtonWidget copyChecksumButton;
	/**
	 * ROADMAP GATE82: die Screenshot-Galerie, oder {@code null} ohne echte
	 * Screenshots -- ein echtes {@link ClickableWidget} statt nur gezeichneter
	 * Kacheln, damit Klick/Enter eine Grossansicht oeffnen koennen.
	 */
	private ScreenshotGalleryWidget screenshotGallery;
	/** ROADMAP GATE82: "Mehr/Weniger anzeigen" der Beschreibung, oder {@code null} bei kurzer Beschreibung. */
	private ButtonWidget descriptionToggleButton;
	/** ROADMAP GATE82: {@code true}, nachdem "Mehr anzeigen" gedrueckt wurde -- klappt die Beschreibung echt auf. */
	private boolean descriptionExpanded;
	/**
	 * ROADMAP GATE82: der zuletzt angeklickte/aktivierte Screenshot-Index, oder
	 * leer, solange keiner geoeffnet wurde -- clientfrei testbarer Beweis fuer
	 * "Klick/Enter oeffnet die Grossansicht" (siehe {@link #openScreenshot}).
	 */
	private Optional<Integer> openScreenshotIndex = Optional.empty();
	/** ROADMAP GATE20: die zuletzt angestossene Deinstallations-Rueckfrage, oder {@code null}. */
	private UninstallConfirmModel pendingUninstall;
	private TileRowWidget moreFromAuthorRow;

	/** Basis-Y der scrollenden Widgets (vor Abzug von {@link #scrollY}). */
	private final List<ScrollAnchor> scrollAnchors = new ArrayList<>();

	private int scrollY;
	private int contentHeight;

	/**
	 * ROADMAP GATE34: Klartext-Fehler des zuletzt gescheiterten Installations-/
	 * Umschalt-Versuchs, oder leer im Ruhezustand. Solange gesetzt, zeigt der
	 * Aktionsknopf {@link PackageActionState#ERROR} und die Seite den Satz
	 * darunter -- so sieht eine abgelehnte Installation (Zip-Slip, kaputte
	 * Signatur, fehlende Faehigkeit, ...) sichtbar anders aus als eine
	 * erfolgreiche, die weiterhin still zu {@link #returnToParent()} zurueckkehrt.
	 */
	private Optional<String> installError = Optional.empty();

	/**
	 * ROADMAP GATE9/GATE10: {@link PackageInstallPhase#WORKING}, waehrend ein
	 * Katalog-Download+Installationsversuch auf einem Hintergrund-Thread laeuft
	 * (siehe {@link #beginCatalogInstall()}/{@link #confirmCatalogInstall}) --
	 * echt erreichbar, nicht nur in Tests (vorher hing der Knopfzustand
	 * ausschliesslich am unveraenderlichen {@link #model}, das immer
	 * {@link PackageInstallPhase#IDLE} trug). {@link #buildActionButton} laesst
	 * dieses Feld immer gegen {@link #installError} und {@code model.actionState()}
	 * gewinnen, solange ein Vorgang laeuft.
	 */
	private PackageInstallPhase phase = PackageInstallPhase.IDLE;

	/**
	 * ROADMAP GATE70: {@code true}, sobald {@link #maybeFetchRepoStats()} den
	 * Hintergrund-Abruf der GitHub-Kennzahlen angestossen hat -- verhindert
	 * einen erneuten Abruf bei jedem {@link #rebuildIfOpen()} (z. B. nach
	 * einem Theme-Wechsel oder nachdem der Abruf selbst fertig ist und das
	 * Modell austauscht).
	 */
	private boolean repoStatsRequested;

	/**
	 * ROADMAP T4: baut die Seite bei einem Laufzeit-Theme-Wechsel neu auf, damit
	 * die im Aufbau festgehaltenen Farben (Knopf-/Zeilenfarben) sofort umfaerben
	 * -- Muster wie {@code StoreScreen}. In {@link #removed()} wieder abgemeldet.
	 */
	private final Runnable themeRepaintListener = this::onThemeChanged;

	public PackageDetailScreen(Screen parentScreen, PackageDetailModel model) {
		this(parentScreen, model, false);
	}

	private PackageDetailScreen(Screen parentScreen, PackageDetailModel model, boolean fromCatalog) {
		super(Text.translatable("vitrine.detail.title"));
		this.parentScreen = parentScreen;
		this.model = model;
		this.brokenState = null;
		this.fromCatalog = fromCatalog;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	private PackageDetailScreen(Screen parentScreen, StoreStateView brokenState) {
		super(Text.translatable("vitrine.detail.title"));
		this.parentScreen = parentScreen;
		this.model = null;
		this.brokenState = brokenState;
		this.fromCatalog = false;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	/**
	 * ROADMAP G3: Detailseite fuer ein Angebot, das nur als Katalog-Eintrag
	 * bekannt ist ({@code CatalogPackageDetailSource}). Der Aktionsknopf
	 * installiert dann ueber {@link CatalogPackInstallService} (Download +
	 * Pruefung gegen den eingebetteten Registry-Schluessel), nicht ueber eine
	 * lokal bereits vorliegende Datei.
	 */
	/**
	 * umgekocht (2026-10-07): "Holen" direkt auf der Kachel. Die Detailseite startet
	 * dann sofort die Installation -- mit genau demselben Ablauf wie ihr eigener
	 * Knopf (Pruefung, Faehigkeiten-Dialog, Download-Anzeige) -- und kehrt danach
	 * von selbst in den Store zurueck.
	 */
	private boolean autoInstall;

	public PackageDetailScreen withAutoInstall() {
		this.autoInstall = true;
		return this;
	}

	public static PackageDetailScreen forCatalog(Screen parentScreen, PackageDetailModel model) {
		return new PackageDetailScreen(parentScreen, model, true);
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

	/**
	 * ROADMAP S7: Detailseite fuer ein Paket, das sich nicht laden liess (kaputtes
	 * Archiv / schema-widriges Manifest / fehlende Datei -- der genaue Grund
	 * steht im Spiel-Protokoll). Zeigt "&lt;Name&gt; konnte nicht geladen werden"
	 * mit genau einem Knopf "Details".
	 */
	public static PackageDetailScreen broken(Screen parentScreen, StoreStateView brokenState) {
		return new PackageDetailScreen(parentScreen, brokenState);
	}

	private record ScrollAnchor(ClickableWidget widget, int baseY) {
	}

	@Override
	protected void init() {
		scrollAnchors.clear();
		if (autoInstall && phase == PackageInstallPhase.IDLE && this.client != null) {
			autoInstall = false;
			this.client.execute(this::beginInstall);
		}

		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		backButton = de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(VitrineTheme.EDGE_PADDING, buttonY, VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), b -> this.close(), net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_back.png"), null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		this.addDrawableChild(backButton);

		if (brokenState != null) {
			initBrokenState();
			return;
		}

		maybeFetchRepoStats();

		int x = VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;

		// --- Kopfbereich ---
		int headerBottom = y + VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT;

		// --- Aktionsknopf (direkt unter dem Kopfbereich, docs/UI-SPEC.md) ---
		int actionY = headerBottom + VitrineTheme.DETAIL_SECTION_GAP;
		actionButton = de.spassglas.vitrine.ui.theme.VitrineButtonStyle.primary(buildActionButton(x, actionY));
		this.addDrawableChild(actionButton);
		scrollAnchors.add(new ScrollAnchor(actionButton, actionY));

		// GATE6: liegt etwas zum Neustart bereit, sagt das Banner es -- unten
		// fest ueber dem Inhalt, mit "Jetzt neu starten" und "Zuruecknehmen".
		restartBanner = null;
		if (model.actionState() == PackageActionState.RESTART_REQUIRED) {
			RestartRequiredBannerWidget banner = new RestartRequiredBannerWidget(ClassBServices.stager());
			if (banner.isVisible()) {
				int bannerTop = this.height - banner.reservedHeight() - VitrineTheme.EDGE_PADDING;
				banner.build(x, bannerTop, this.width - 2 * x, this::addDrawableChild, this::rebuildIfOpen);
				restartBanner = banner;
			}
		}

		if (model.actionState() == PackageActionState.INSTALLED) {
			int uninstallX = x + VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH + VitrineTheme.CONTROL_GAP;
			uninstallButton = ButtonWidget
					.builder(Text.translatable("vitrine.action.uninstall"), b -> doUninstall())
					.dimensions(uninstallX, actionY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
							VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(uninstallButton);
			scrollAnchors.add(new ScrollAnchor(uninstallButton, actionY));

			// umgekocht (2026-10-06): nach dem Installieren fehlte ein sichtbarer Weg zum
			// An/Aus-Schalten -- direkt in die Bibliothek, wo jedes Paket einen
			// Schalter und seine Reihenfolge hat.
			int manageX = uninstallX + VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH + VitrineTheme.CONTROL_GAP;
			ButtonWidget manageButton = ButtonWidget
					.builder(Text.translatable("vitrine.detail.manage_in_library"), b -> {
						if (this.client != null) {
							this.client.setScreen(new LibraryScreen(this));
						}
					})
					.dimensions(manageX, actionY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
							VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(manageButton);
			scrollAnchors.add(new ScrollAnchor(manageButton, actionY));
		}

		int cursorY = actionY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.DETAIL_SECTION_GAP;

		// --- GATE34: Klartext-Fehler des letzten Versuchs, falls vorhanden ---
		cursorY += installErrorHeight();

		// --- Screenshot-Galerie (ROADMAP GATE82) ---
		screenshotGallery = null;
		if (model.hasScreenshots()) {
			// Ein echtes ClickableWidget -- zeichnet Ueberschrift und Kacheln
			// selbst und meldet Klick/Enter an openScreenshot (docs/UI-SPEC.md
			// "5. Detailseite": "Klick oeffnet gross").
			ScreenshotGalleryWidget gallery = new ScreenshotGalleryWidget(x, cursorY, model.screenshotPaths(),
					model.sourceFile().orElse(null), this.textRenderer, this::openScreenshot);
			this.addDrawableChild(gallery);
			scrollAnchors.add(new ScrollAnchor(gallery, cursorY));
			screenshotGallery = gallery;
			cursorY += ScreenshotGalleryWidget.preferredHeight() + VitrineTheme.DETAIL_SECTION_GAP;
		} else {
			cursorY = advanceHeading(cursorY);
			cursorY += lineHeight();
			cursorY += VitrineTheme.DETAIL_SECTION_GAP;
		}

		// --- Beschreibung (ROADMAP GATE82: "Mehr/Weniger anzeigen" klappt echt auf/zu) ---
		cursorY = advanceHeading(cursorY);
		cursorY += descriptionLinesToShow() * lineHeight();
		descriptionToggleButton = null;
		if (model.descriptionNeedsCollapse()) {
			String labelKey = descriptionExpanded
					? "vitrine.detail.description.less"
					: "vitrine.detail.description.more";
			ButtonWidget toggle = ButtonWidget.builder(Text.translatable(labelKey), b -> toggleDescription())
					.dimensions(x, cursorY, VitrineTheme.LIBRARY_TOGGLE_WIDTH, VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(toggle);
			scrollAnchors.add(new ScrollAnchor(toggle, cursorY));
			descriptionToggleButton = toggle;
			cursorY += VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.DETAIL_LINE_GAP;
		}
		cursorY += VitrineTheme.DETAIL_SECTION_GAP;

		// --- Das darf dieses Paket ---
		cursorY = advanceHeading(cursorY);
		int capLines = Math.max(1, model.capabilities().size()) + (model.runsNoProgramCode() ? 1 : 0);
		cursorY += capLines * lineHeight();
		cursorY += VitrineTheme.DETAIL_SECTION_GAP;

		// --- Details ---
		cursorY = advanceHeading(cursorY);
		copyChecksumButton = null;
		for (PackageDetailFact fact : model.details()) {
			if (fact.copyable() && fact.isKnown()) {
				// GATE69: "Prüfsumme (kopierbar)" (docs/UI-SPEC.md "Details") -- ein
				// echter Knopf statt nur Text, rechts neben der Werte-Spalte derselben
				// Zeile, damit Zeigen und Kopieren offensichtlich zusammengehoeren.
				int copyX = this.width - VitrineTheme.SPACE_PAGE - VitrineTheme.LIBRARY_TOGGLE_WIDTH;
				String value = fact.value().orElseThrow();
				ButtonWidget copyButton = ButtonWidget.builder(Text.translatable("vitrine.detail.action.copy"),
								b -> copyToClipboard(value))
						.dimensions(copyX, cursorY, VitrineTheme.LIBRARY_TOGGLE_WIDTH,
								VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
						.build();
				this.addDrawableChild(copyButton);
				scrollAnchors.add(new ScrollAnchor(copyButton, cursorY));
				copyChecksumButton = copyButton;
			}
			cursorY += lineHeight();
		}
		cursorY += VitrineTheme.DETAIL_SECTION_GAP;

		// --- Änderungen ---
		cursorY = advanceHeading(cursorY);
		int changelogLines = model.changelog().map(PackageDetailScreen::countLines).orElse(1);
		cursorY += changelogLines * lineHeight();
		cursorY += VitrineTheme.DETAIL_SECTION_GAP;

		// --- Weitere Pakete dieses Autors ---
		cursorY = advanceHeading(cursorY);
		if (model.hasMoreFromAuthor()) {
			int rowWidth = this.width - x - VitrineTheme.SPACE_PAGE;
			if (rowWidth >= VitrineTheme.CARD_WIDTH) {
				moreFromAuthorRow = new TileRowWidget(x, cursorY, rowWidth, model.moreFromAuthor(), this.textRenderer,
						"vitrine.detail.section.more_from_author", "vitrine.detail.section.more_from_author");
				this.addDrawableChild(moreFromAuthorRow);
				scrollAnchors.add(new ScrollAnchor(moreFromAuthorRow, cursorY));
				cursorY += TileRowWidget.preferredHeight();
			}
		} else {
			cursorY += lineHeight();
		}

		contentHeight = cursorY + VitrineTheme.SPACE_PAGE;
		clampScroll();
		applyScroll();
	}

	private int advanceHeading(int cursorY) {
		return cursorY + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
	}

	/** ROADMAP S7: der eine Knopf "Details" des Paket-kaputt-Zustands. */
	private void initBrokenState() {
		int topY = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		int btnY = topY + StoreStatePanel.contentAdvance();
		int btnX = this.width / 2 - VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH / 2;
		ButtonWidget details = ButtonWidget.builder(
						Text.translatable(brokenState.action().orElseThrow().labelKey()), b -> {
							brokenDetailsShown = !brokenDetailsShown;
						})
				.dimensions(btnX, btnY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
						VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.build();
		this.addDrawableChild(details);
		contentHeight = btnY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE;
	}

	private void renderBrokenState(DrawContext context) {
		int topY = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;
		StoreStatePanel.render(context, this.textRenderer, brokenState, this.width / 2, topY,
				this.width - VitrineTheme.SPACE_PAGE * 2);
		if (brokenDetailsShown) {
			int hintY = topY + StoreStatePanel.contentAdvance()
					+ VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.DETAIL_HEADING_GAP;
			Text hint = Text.translatable("vitrine.state.package_broken.details_hint");
			int width = ScaledText.width(this.textRenderer, hint.getString(), VitrineTheme.TYPE_CAPTION);
			ScaledText.draw(context, this.textRenderer, hint, this.width / 2 - width / 2, hintY,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		}
	}

	/**
	 * GATE69: legt {@code value} in die Systemzwischenablage, ueber Minecrafts
	 * eigenen {@link net.minecraft.client.Keyboard#setClipboard(String)} --
	 * dieselbe Stelle, ueber die auch Vanilla (z. B. "Koordinaten kopieren")
	 * die Zwischenablage setzt, kein eigener AWT-/JNI-Zugriff.
	 */
	private void copyToClipboard(String value) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		client.keyboard.setClipboard(value);
		Vitrine.LOGGER.debug("Detailseite: Wert in die Zwischenablage kopiert ('{}').", model.manifestId());
	}

	/** Nur fuer Tests: der Kopier-Knopf der Pruefsumme, oder {@code null} ohne bekannte Pruefsumme. */
	public ButtonWidget copyChecksumButton() {
		return copyChecksumButton;
	}

	private int lineHeight() {
		return VitrineTheme.TYPE_BODY + VitrineTheme.DETAIL_LINE_GAP;
	}

	/** ROADMAP GATE34: zusaetzlicher vertikaler Platz fuer die Fehlerzeile, oder 0 im Ruhezustand -- {@link #init()} und {@link #renderContent} nutzen denselben Wert. */
	private int installErrorHeight() {
		return installError.isPresent() ? lineHeight() + VitrineTheme.DETAIL_SECTION_GAP : 0;
	}

	/**
	 * ROADMAP GATE82: wie viele Zeilen der Beschreibung gerade sichtbar sind --
	 * solange nicht aufgeklappt, hoechstens {@link PackageDetailModel#DESCRIPTION_COLLAPSE_LINES},
	 * nach "Mehr anzeigen" alle. Eine eigene Methode, damit {@link #init()}
	 * (Platzberechnung) und {@link #renderDescription} (tatsaechliches
	 * Zeichnen) niemals auseinanderlaufen.
	 */
	private int descriptionLinesToShow() {
		return model.description().map(text -> {
			int total = countLines(text);
			return descriptionExpanded ? total : Math.min(total, PackageDetailModel.DESCRIPTION_COLLAPSE_LINES);
		}).orElse(1);
	}

	/**
	 * ROADMAP GATE82: "Mehr anzeigen"/"Weniger anzeigen" -- klappt die
	 * Beschreibung echt auf mehr als {@link PackageDetailModel#DESCRIPTION_COLLAPSE_LINES}
	 * Zeilen auf bzw. wieder zu, statt (wie vorher) nur zwei toten
	 * Uebersetzungs-Keys ohne jeden Aufrufer. Zustand liegt in
	 * {@link #descriptionExpanded}, nicht nur im Knopftext.
	 */
	void toggleDescription() {
		descriptionExpanded = !descriptionExpanded;
		rebuildIfOpen();
	}

	/** Nur fuer Tests/Anbindung: ob die Beschreibung aktuell aufgeklappt ist (ROADMAP GATE82). */
	public boolean descriptionExpanded() {
		return descriptionExpanded;
	}

	/** Nur fuer Tests: die "Mehr/Weniger anzeigen"-Schaltflaeche, oder {@code null} bei kurzer Beschreibung. */
	public ButtonWidget descriptionToggleButton() {
		return descriptionToggleButton;
	}

	/** Nur fuer Tests: die Screenshot-Galerie, oder {@code null} ohne echte Screenshots (ROADMAP GATE82). */
	public ScreenshotGalleryWidget screenshotGallery() {
		return screenshotGallery;
	}

	/**
	 * ROADMAP GATE82: Klick auf ein Vorschaubild oder Enter/Leertaste bei
	 * fokussierter Galerie ({@link ScreenshotGalleryWidget}) -- merkt sich den
	 * Index clientfrei testbar ({@link #openedScreenshotIndex()}) und oeffnet,
	 * mit laufendem Client, die Grossansicht ({@link ScreenshotViewerScreen}).
	 * Vorher tat ein solcher Klick ueberhaupt nichts (kein Eingabe-Handler).
	 */
	private void openScreenshot(int index) {
		openScreenshotIndex = Optional.of(index);
		if (this.client != null) {
			this.client.setScreen(new ScreenshotViewerScreen(this, model.sourceFile().orElse(null),
					model.screenshotPaths(), index));
		}
	}

	/** Nur fuer Tests/Anbindung: der zuletzt angeklickte/aktivierte Screenshot-Index (ROADMAP GATE82). */
	public Optional<Integer> openedScreenshotIndex() {
		return openScreenshotIndex;
	}

	/**
	 * Nur fuer Tests: baut die Seite neu auf wie {@link #rebuildIfOpen()},
	 * aber auch ohne laufenden Client -- {@link #toggleDescription()} und
	 * aehnliche Rueckrufe rufen nur {@link #rebuildIfOpen()} (no-op ohne
	 * Client), Tests brauchen aber einen echten zweiten {@link #init()}-Lauf,
	 * um die neu aufgebauten Widgets (z. B. den Knopftext) zu pruefen.
	 */
	void rebuildForTest() {
		this.clearChildren();
		this.init();
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
		applyScroll();
		return true;
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, contentHeight - this.height);
		scrollY = Math.max(0, Math.min(scrollY, maxScroll));
	}

	private void applyScroll() {
		for (ScrollAnchor anchor : scrollAnchors) {
			anchor.widget().setY(anchor.baseY() - scrollY);
		}
	}

	// --- ROADMAP GATE70: Sterne/Downloads von der GitHub-API, read-only ---

	/**
	 * Stoesst, hoechstens einmal pro Bildschirm-Leben, den Hintergrund-Abruf der
	 * GitHub-Kennzahlen an (docs/UI-SPEC.md:118-120: "Sterne/Downloads aus der
	 * GitHub-API (read-only)") -- genau dann, wenn {@link #model} ein Autoren-
	 * Repo nennt ({@link PackageDetailModel#repositoryUrl()}) <em>und</em> ein
	 * echter Client laeuft ({@link #client} != {@code null}, wie bei {@link
	 * #runInBackground}). Ohne Repo bleibt der Kopf dauerhaft bei {@link
	 * GitHubRepoStats#UNKNOWN} ("keine Angaben") -- kein Request, kein
	 * Rate-Limit-Verbrauch fuer ein Paket ohne erkennbare Quelle.
	 *
	 * <p>Laeuft auf einem eigenen Hintergrund-Thread (Muster wie {@link
	 * #beginCatalogInstall()}); {@link GitHubRepoStatsClient} selbst faengt
	 * jeden Fehler (Netz, 404, kaputtes JSON) bereits ab und wirft nie -- der
	 * Rueckruf auf den Client-Thread tauscht nur noch das Modell aus und baut
	 * den Bildschirm neu auf.</p>
	 */
	private void maybeFetchRepoStats() {
		if (repoStatsRequested || this.client == null || model.repositoryUrl().isEmpty()) {
			return;
		}
		repoStatsRequested = true;
		String repositoryUrl = model.repositoryUrl().get();
		Path configDir = configDir();
		runInBackground(() -> {
			GitHubRepoStats stats = GitHubRepoStatsClient.production(configDir).fetch(repositoryUrl);
			onClientThread(() -> {
				model = model.withRepoStats(stats);
				rebuildIfOpen();
			});
		});
	}

	/** Nur fuer Tests/Anbindung: ob der GitHub-Abruf schon angestossen wurde (ROADMAP GATE70). */
	public boolean repoStatsRequested() {
		return repoStatsRequested;
	}

	// --- Aktionsknopf-Verdrahtung (ROADMAP S4) ----------------------------

	private ButtonWidget buildActionButton(int x, int y) {
		// ROADMAP GATE9/GATE10/GATE34: ein laufender Vorgang (WORKING) gewinnt
		// immer, danach ein gesetzter installError (ERROR) -- beide gegen den aus
		// dem (unveraenderlichen) Modell abgeleiteten Zustand. Der Knopf muss
		// waehrend eines echten Katalog-Downloads "arbeitet …" zeigen und nach
		// einer gescheiterten Installation "Fehler", nicht wieder "Installieren",
		// als waere nichts gewesen.
		PackageActionState state = currentActionState();
		Text label = Text.translatable(state.translationKey());
		ButtonWidget.PressAction press = b -> {
		};
		boolean enabled = false;

		switch (state) {
			case INSTALL, UPDATE -> {
				press = b -> beginInstall();
				enabled = true;
			}
			case ERROR -> {
				// "Fehler" bleibt sichtbar, der Knopf startet einen erneuten Versuch.
				press = b -> beginInstall();
				enabled = true;
			}
			case INSTALLED -> {
				boolean active = isActiveNow();
				label = Text.translatable(active ? "vitrine.action.deactivate" : "vitrine.action.activate");
				boolean target = !active;
				press = b -> toggleActive(target);
				enabled = true;
			}
			case WORKING -> enabled = false;
			case RESTART_REQUIRED -> {
				// GATE6: "Neustart noetig" traegt nur ein Klasse-B-Angebot (seit
				// GATE16 erzwungen). Noch nicht gestagt -> Herunterladen anbieten
				// (UI-SPEC "Herunterladen (Neustart noetig)"); schon gestagt -> der
				// Knopf bleibt aus, das Banner unten sagt, was zu tun ist.
				if (!isStagedForRestart()) {
					label = Text.translatable("vitrine.classb.download_restart");
					press = b -> beginJavaModStaging();
					enabled = true;
				}
			}
		}

		ButtonWidget button = ButtonWidget.builder(label, press)
				.dimensions(x, y, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH, VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.build();
		button.active = enabled;
		return button;
	}

	/**
	 * ROADMAP GATE9/GATE10: der Knopfzustand, wie {@link #buildActionButton}
	 * ihn zeichnet -- als eigene Methode ausgekoppelt, damit sie ohne
	 * laufenden Client pruefbar ist (siehe {@link #installPhase()}/
	 * {@link #installError()}, demselben "Nur fuer Tests/Anbindung"-Muster).
	 */
	private PackageActionState currentActionState() {
		if (phase == PackageInstallPhase.WORKING) {
			return PackageActionState.WORKING;
		}
		if (installError.isPresent()) {
			return PackageActionState.ERROR;
		}
		return model.actionState();
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
	}

	private String profileId() {
		return VitrinePackProvider.profileId(model.manifestId());
	}

	private boolean isActiveNow() {
		return PackStateStore.load(configDir()).isActive(profileId());
	}

	/**
	 * ROADMAP GATE79: der eigentliche Installationsschritt hinter {@link
	 * #runInstall} -- ein eigenes Funktionsinterface statt eines direkten
	 * {@code PackInstallService.instance()}-Aufrufs, austauschbar fuer Tests.
	 * {@link PackInstallService#instance()} haengt am {@code FabricLoader}-
	 * Konfigurationsordner, der im Testbetrieb (kein laufender Loader) nicht
	 * zur Verfuegung steht; {@link PackageDetailScreenLocalInstallAsyncTest}
	 * ersetzt {@link #localInstallOperation} durch einen echten {@link
	 * PackInstaller} (wie {@code PackageDetailScreenInstallErrorTest}) bzw.
	 * durch einen simulierten Fehler.
	 */
	@FunctionalInterface
	interface LocalInstallOperation {
		void install(String manifestId, List<String> capabilityIds) throws PackInstallException;
	}

	private LocalInstallOperation localInstallOperation = (manifestId, capabilityIds) ->
			PackInstallService.instance().confirmCapabilitiesAndInstall(manifestId, capabilityIds);

	/** Nur fuer Tests: ersetzt den echten {@link PackInstallService}-Aufruf von {@link #runInstall} (ROADMAP GATE79). */
	void setLocalInstallOperationForTest(LocalInstallOperation operation) {
		this.localInstallOperation = operation;
	}

	private void beginInstall() {
		if (fromCatalog) {
			beginCatalogInstall();
			return;
		}
		PackState state = PackStateStore.load(configDir());
		CapabilityConsentModel consent =
				CapabilityConsentModel.of(model.manifestId(), model.capabilities(), state);
		if (consent.requiresConsent() && this.client != null) {
			this.client.setScreen(new CapabilityConsentDialog(this, consent, model.header().name(),
					() -> runInstall(consent.capabilityIds()), () -> {
					}));
			return;
		}
		runInstall(consent.capabilityIds());
	}

	/**
	 * ROADMAP GATE79: lief vorher synchron auf dem Client-Thread --
	 * {@link PackInstaller#install} macht SHA-256 + Ed25519 + eine Dateikopie,
	 * potentiell spuerbare I/O-/Krypto-Arbeit, die das Spiel ohne jede
	 * Rueckmeldung einfrieren liess (kein {@link PackageInstallPhase#WORKING},
	 * keine Flyout-Zeile, anders als der Katalogweg). Laeuft jetzt nach
	 * demselben Muster wie {@link #beginCatalogInstall()}: Hintergrund-Thread
	 * ({@link #runInBackground}), eine Flyout-Fortschrittszeile (ohne bekannte
	 * Bytegroesse -- {@link PackInstaller#install} meldet keinen laufenden
	 * Fortschritt, die Zeile zeigt den unbestimmten Balken aus GATE12), danach
	 * ueber {@link #onClientThread} zurueck auf den Render-Thread.
	 */
	void runInstall(List<String> capabilityIds) {
		phase = PackageInstallPhase.WORKING;
		installError = Optional.empty();
		rebuildIfOpen();
		String flyoutId = profileId();
		DownloadFlyoutService.instance().startDownload(flyoutId, model.header().name(), -1L);
		runInBackground(() -> {
			try {
				localInstallOperation.install(model.manifestId(), capabilityIds);
				Vitrine.LOGGER.info("Vitrine-Paket '{}' aus der Detailseite installiert.", model.manifestId());
				DownloadFlyoutService.instance().markDone(flyoutId);
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					returnToParent();
				});
			} catch (PackInstallException e) {
				Vitrine.LOGGER.error("Installieren von '{}' aus der Detailseite fehlgeschlagen: {}",
						model.manifestId(), e.getMessage(), e);
				DownloadFlyoutService.instance().markFailed(flyoutId);
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					showInstallError(e);
				});
			}
		});
	}

	// --- Klasse B: Java-Mod stagen (GATE6) --------------------------------

	/** Ob dieses Angebot schon in {@code mods/} vorgemerkt ist und auf den Neustart wartet. */
	private boolean isStagedForRestart() {
		try {
			return ClassBServices.stager().stagedMods().stream()
					.anyMatch(staged -> staged.id().equals(model.manifestId()));
		} catch (RuntimeException e) {
			Vitrine.LOGGER.warn("Klasse B: Vormerkungen nicht lesbar, biete Herunterladen an: {}", e.toString());
			return false;
		}
	}

	/**
	 * GATE6: Registry-Eintrag holen, dann {@link RegistryJavaModStaging} --
	 * Opt-in, Klasse-B-Pruefung, Pruefsumme und Signatur gegen den
	 * eingebetteten Schluessel, Konflikte; erst danach liegt etwas in
	 * {@code mods/}. Laeuft wie die Klasse-A-Installation auf einem
	 * Hintergrund-Thread (GATE9), der Knopf zeigt solange "arbeitet".
	 */
	private void beginJavaModStaging() {
		phase = PackageInstallPhase.WORKING;
		installError = Optional.empty();
		rebuildIfOpen();
		Path configDir = configDir();
		runInBackground(() -> {
			try {
				CatalogResult<RegistryPackageEntry> result =
						RegistryPackageClient.production(configDir).fetchPackage(model.manifestId());
				if (!result.isOnline()) {
					throw new JavaModStagingException("Katalog-Eintrag von '" + model.manifestId()
							+ "' gerade nicht abrufbar: " + result.message().orElse("keine Meldung"));
				}
				StagedMod staged = RegistryJavaModStaging.stage(result.value().orElseThrow(),
						ClassBServices.stager(), () -> SettingsModel.showJavaModOffers(configDir));
				Vitrine.LOGGER.info("Klasse B: '{}' als {} vorgemerkt -- wirksam nach dem Neustart.",
						staged.id(), staged.fileName());
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					rebuildIfOpen();
				});
			} catch (JavaModStagingException | RuntimeException e) {
				Vitrine.LOGGER.error("Klasse B: '{}' liess sich nicht vormerken: {}", model.manifestId(),
						e.toString(), e);
				PackInstallException shown = new PackInstallException(e.getMessage() == null
						? e.getClass().getSimpleName() : e.getMessage(), e,
						"vitrine.detail.error.staging_failed", friendlyFailureReason(e));
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					showInstallError(shown);
				});
			}
		});
	}

	// --- Katalog-Installation (ROADMAP G3) --------------------------------

	/**
	 * Holt den vollen Registry-Paketeintrag (erst hier bekannt: Download-URL,
	 * Pruefsumme, Signatur, Groesse -- {@code CatalogPackageDetailSource}
	 * traegt nur die Anzeige-Felder), dann laedt+installiert {@link
	 * CatalogPackInstallService}. Ein noch fehlender Faehigkeiten-Dialog wird
	 * erst hier ausgeloest, weil Faehigkeiten eines Katalog-Angebots erst nach
	 * dem Download bekannt sind (kein {@code capabilities}-Feld im
	 * Registry-Schema).
	 */
	/**
	 * ROADMAP GATE9: der Registry-Abruf und der eigentliche Download laufen auf
	 * einem Hintergrund-Thread (nur ein normaler {@code Thread} wie schon
	 * {@code StoreScreen#startCatalogRefresh} -- kein eigener Pool, kein
	 * {@code future.join()} auf dem Client-Thread, siehe GATE38). Waehrend
	 * dessen zeigt der Aktionsknopf {@link PackageInstallPhase#WORKING}, die
	 * Oberflaeche bleibt bedienbar. Ohne laufenden {@link #client} (Tests) laeuft
	 * dieselbe Logik synchron im aufrufenden Thread -- dasselbe Verhalten wie vor
	 * GATE9, nur ohne den Umweg ueber einen echten Thread.
	 */
	private void beginCatalogInstall() {
		phase = PackageInstallPhase.WORKING;
		installError = Optional.empty();
		rebuildIfOpen();
		Path configDir = configDir();
		runInBackground(() -> {
			try {
				CatalogResult<RegistryPackageEntry> result =
						RegistryPackageClient.production(configDir).fetchPackage(model.manifestId());
				handleCatalogFetchResult(result);
			} catch (RuntimeException e) {
				Vitrine.LOGGER.error("Katalog-Installation von '{}' unerwartet fehlgeschlagen: {}",
						model.manifestId(), e.toString(), e);
				String message = e.getMessage();
				PackInstallException wrapped = new PackInstallException(
						(message == null || message.isBlank()) ? e.getClass().getSimpleName() : message, e,
						"vitrine.detail.error.catalog_unexpected", friendlyFailureReason(e));
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					showInstallError(wrapped);
				});
			}
		});
	}

	/**
	 * ROADMAP GATE47: wertet das Ergebnis eines Registry-Abrufs genauso aus, wie
	 * es {@link #beginCatalogInstall()} auf dem Hintergrund-Thread tut -- als
	 * eigene, paket-sichtbare Methode ausgekoppelt, damit beide Fehlerpfade ohne
	 * echten Netzwerkzugriff pruefbar sind ({@code
	 * PackageDetailScreenRegistryErrorTest}). Vorher endeten ein nicht abrufbarer
	 * Registry-Eintrag und ein {@code latestVersion} ohne passenden {@code
	 * versions[]}-Eintrag beide nur in einem Log-Eintrag plus stillem {@code
	 * returnToParent()} -- derselbe GATE34-verletzende Befund wie bei jedem
	 * anderen Fehlerpfad, nur an diesen zwei Stellen nicht mitbehoben. Beide
	 * Zweige rufen jetzt {@link #showInstallError(PackInstallException)} wie die
	 * uebrigen Fehlerpfade.
	 */
	void handleCatalogFetchResult(CatalogResult<RegistryPackageEntry> result) {
		if (!result.isOnline()) {
			String reason = result.message().orElse("keine Meldung");
			Vitrine.LOGGER.error(
					"Katalog-Installation von '{}' abgebrochen: Registry-Eintrag nicht verfuegbar ({}).",
					model.manifestId(), reason);
			PackInstallException unreachable = new PackInstallException(
					"Registry-Eintrag nicht verfuegbar: " + reason,
					"vitrine.detail.error.registry_unreachable", reason);
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				showInstallError(unreachable);
			});
			return;
		}
		RegistryPackageEntry entry = result.value().orElseThrow();
		Optional<RegistryPackageVersion> latest = entry.latestVersionEntry();
		if (latest.isEmpty()) {
			Vitrine.LOGGER.error("Katalog-Installation von '{}' abgebrochen: latestVersion '{}' ohne "
					+ "passenden versions[]-Eintrag.", model.manifestId(), entry.latestVersion());
			PackInstallException noVersion = new PackInstallException(
					"latestVersion '" + entry.latestVersion() + "' ohne passenden versions[]-Eintrag.",
					"vitrine.detail.error.registry_version_missing", entry.latestVersion());
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				showInstallError(noVersion);
			});
			return;
		}
		DownloadRequest request = latest.get().toDownloadRequest();
		String sourceHost = request.vpkgUrl().getHost();
		performCatalogInstall(request, sourceHost);
	}

	/**
	 * Laedt+installiert ueber {@link CatalogPackInstallService} mit einem echten
	 * {@link CancellationToken} (der Abbrechen-Knopf im Download-Flyout, ROADMAP
	 * GATE9) und einem echten Fortschritts-Rueckruf ins geteilte
	 * {@link DownloadFlyoutService}-Modell (ROADMAP GATE4). Laeuft bereits auf
	 * dem Hintergrund-Thread aus {@link #beginCatalogInstall()}/
	 * {@link #confirmCatalogInstall} -- alles, was den Bildschirm oder
	 * Minecrafts Screen-Zustand anfasst, geht ueber {@link #onClientThread}.
	 */
	private void performCatalogInstall(DownloadRequest request, String sourceHost) {
		CancellationToken token = new CancellationToken();
		String flyoutId = profileId();
		DownloadFlyoutService.instance().startDownload(flyoutId, model.header().name(), request.declaredSize(), token);
		DownloadProgress progress = (bytesRead, totalBytes) ->
				DownloadFlyoutService.instance().updateProgress(flyoutId, bytesRead, totalBytes);
		try {
			CatalogPackInstallService.instance().install(model.manifestId(), request, sourceHost, token, progress);
			DownloadFlyoutService.instance().markDone(flyoutId);
			Vitrine.LOGGER.info("Katalog-Paket '{}' aus der Detailseite installiert (Quelle '{}').",
					model.manifestId(), sourceHost);
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				returnToParent();
			});
		} catch (CatalogConsentRequiredException e) {
			// Erfolgreich heruntergeladen und verifiziert, wartet aber noch auf die
			// Faehigkeiten-Zustimmung -- kein Fehler, kein "erledigt".
			DownloadFlyoutService.instance().discardRunning(flyoutId);
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				showCatalogConsentDialog(e.capabilityIds(), request, sourceHost);
			});
		} catch (PackInstallCancelledException e) {
			// Der Abbrechen-Knopf im Flyout hat den echten Download schon beendet
			// und die Zeile nach "Zuletzt abgeschlossen" verschoben -- dieser Aufruf
			// ist der sichere Nachtrag, falls das noch nicht passiert war.
			DownloadFlyoutService.instance().markCancelled(flyoutId);
			Vitrine.LOGGER.info("Katalog-Installation von '{}' vom Nutzer abgebrochen.", model.manifestId());
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				rebuildIfOpen();
			});
		} catch (PackInstallException e) {
			DownloadFlyoutService.instance().markFailed(flyoutId);
			Vitrine.LOGGER.error("Katalog-Installation von '{}' fehlgeschlagen: {}", model.manifestId(),
					e.getMessage(), e);
			onClientThread(() -> {
				phase = PackageInstallPhase.IDLE;
				showInstallError(e);
			});
		}
	}

	/** Faehigkeiten sind erst nach dem Download bekannt -- der Dialog kommt deshalb erst hier, nicht vorab. */
	private void showCatalogConsentDialog(List<String> capabilityIds, DownloadRequest request, String sourceHost) {
		List<VpkgCapability> capabilities = new ArrayList<>();
		for (String id : capabilityIds) {
			VpkgCapability.byId(id).ifPresent(capabilities::add);
		}
		CapabilityConsentModel consent = CapabilityConsentModel.of(model.manifestId(), capabilities,
				PackStateStore.load(configDir()));
		if (this.client == null) {
			return;
		}
		this.client.setScreen(new CapabilityConsentDialog(this, consent, model.header().name(),
				() -> confirmCatalogInstall(capabilityIds, request, sourceHost), () -> {
				}));
	}

	/** Wie {@link #beginCatalogInstall()}, aber nach einer bereits erteilten Faehigkeiten-Zustimmung. */
	private void confirmCatalogInstall(List<String> capabilityIds, DownloadRequest request, String sourceHost) {
		phase = PackageInstallPhase.WORKING;
		installError = Optional.empty();
		rebuildIfOpen();
		runInBackground(() -> {
			CancellationToken token = new CancellationToken();
			String flyoutId = profileId();
			DownloadFlyoutService.instance().startDownload(flyoutId, model.header().name(), request.declaredSize(),
					token);
			DownloadProgress progress = (bytesRead, totalBytes) ->
					DownloadFlyoutService.instance().updateProgress(flyoutId, bytesRead, totalBytes);
			try {
				CatalogPackInstallService.instance().confirmCapabilitiesAndInstall(model.manifestId(), capabilityIds,
						request, sourceHost, token, progress);
				DownloadFlyoutService.instance().markDone(flyoutId);
				Vitrine.LOGGER.info("Katalog-Paket '{}' nach Faehigkeiten-Zustimmung installiert.",
						model.manifestId());
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					returnToParent();
				});
			} catch (PackInstallCancelledException e) {
				DownloadFlyoutService.instance().markCancelled(flyoutId);
				Vitrine.LOGGER.info("Katalog-Installation von '{}' nach Faehigkeiten-Zustimmung vom Nutzer "
						+ "abgebrochen.", model.manifestId());
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					rebuildIfOpen();
				});
			} catch (PackInstallException e) {
				DownloadFlyoutService.instance().markFailed(flyoutId);
				Vitrine.LOGGER.error("Katalog-Installation von '{}' nach Faehigkeiten-Zustimmung fehlgeschlagen: {}",
						model.manifestId(), e.getMessage(), e);
				onClientThread(() -> {
					phase = PackageInstallPhase.IDLE;
					showInstallError(e);
				});
			}
		});
	}

	// --- ROADMAP GATE9: Hintergrund-Thread + Ruecksprung auf den Client-Thread ---

	/**
	 * Fuehrt {@code work} auf einem eigenen, daemon-Thread aus (Muster wie
	 * {@code StoreScreen#startCatalogRefresh} -- kein Pool, kein
	 * {@code future.join()} vom Client-Thread). Ohne laufenden {@link #client}
	 * (Tests, siehe {@code PackageDetailScreenInstallErrorTest}) gibt es keinen
	 * Client-Thread, auf den zurueckgesprungen werden muesste -- {@code work}
	 * laeuft dann synchron im aufrufenden Thread, mit identischem Ergebnis.
	 */
	private void runInBackground(Runnable work) {
		if (this.client == null) {
			work.run();
			return;
		}
		Thread worker = new Thread(work, "Vitrine-Catalog-Install");
		worker.setDaemon(true);
		worker.start();
	}

	/**
	 * Reicht {@code uiWork} an {@link MinecraftClient#execute(Runnable)} weiter
	 * (Muster wie {@code HudCommand}: der abschliessende Schritt, der Minecrafts
	 * Screen-Zustand anfasst -- {@code setScreen}, {@link #init()} -- gehoert auf
	 * den Client-Thread, nicht auf den Download-Hintergrund-Thread). Ist dieser
	 * Bildschirm inzwischen nicht mehr der sichtbare (Nutzer hat weitergeklickt),
	 * bleibt {@code uiWork} aus -- der Installationsversuch selbst (state.json,
	 * Aktivierung) ist zu diesem Zeitpunkt bereits abgeschlossen, nur die
	 * Bildschirm-Anzeige waere fuer niemanden mehr da.
	 */
	private void onClientThread(Runnable uiWork) {
		if (this.client == null) {
			uiWork.run();
			return;
		}
		MinecraftClient mc = this.client;
		mc.execute(() -> {
			if (mc.currentScreen == this) {
				uiWork.run();
			}
		});
	}

	private void rebuildIfOpen() {
		if (this.client != null) {
			this.clearChildren();
			this.init();
		}
	}

	/** Nur fuer Tests/Anbindung: der aktuelle Installationsfortschritt (ROADMAP GATE9/GATE10). */
	public PackageInstallPhase installPhase() {
		return phase;
	}

	private void toggleActive(boolean active) {
		installError = Optional.empty();
		try {
			PackInstallService.instance().setActive(profileId(), active);
		} catch (PackInstallException e) {
			Vitrine.LOGGER.error("An/Aus für '{}' aus der Detailseite fehlgeschlagen: {}",
					profileId(), e.getMessage(), e);
			showInstallError(e);
			return;
		}
		returnToParent();
	}

	/**
	 * ROADMAP GATE34: haelt den Bildschirm offen und zeigt einen Klartextfehler,
	 * statt (wie zuvor an dieser Stelle) nur zu loggen und still zu
	 * {@link #returnToParent()} zurueckzukehren -- eine abgelehnte Installation
	 * (Zip-Slip, kaputte Signatur, verbotene Faehigkeit, ...) muss sich sichtbar
	 * von einer erfolgreichen unterscheiden. Baut den Bildschirm neu auf, damit
	 * der Aktionsknopf sofort {@link PackageActionState#ERROR} zeigt; im
	 * Testbetrieb ohne {@link #client} bleibt nur das Feld gesetzt.
	 *
	 * <p>ROADMAP GATE48: zeigt nicht mehr {@code e.getMessage()} (rohe
	 * Protokoll-Fassung -- kann absolute Pfade und rohe {@link
	 * Throwable#toString()}-Fragmente enthalten, dazu in {@link PackInstaller}/
	 * {@link de.spassglas.vitrine.pack.CatalogPackInstaller} hartkodiertes
	 * Deutsch ohne Uebersetzungs-Key), sondern {@link PackInstallException#displayKey()}
	 * uebersetzt mit {@link PackInstallException#displayArgs()} -- beide von den
	 * Installern bewusst ohne Pfad/rohe Ursache befuellt. Faelle ohne
	 * {@code displayKey} (aeltere, nicht an der Oberflaeche sichtbare
	 * Konstruktoren, z. B. von {@link de.spassglas.vitrine.pack.BundleInstaller})
	 * fallen auf einen generischen, ebenso sicheren Schluessel zurueck, der nur
	 * den einfachen Klassennamen der Ausnahme nennt.</p>
	 *
	 * <p>GATE66: {@link Text#translatable} liefert nur dann echten Klartext,
	 * wenn Minecrafts {@code Language}-Singleton bereits mit den
	 * Sprachdateien des Mods geladen wurde (im laufenden Spiel immer der
	 * Fall, sobald der erste Ressourcen-Reload durchgelaufen ist) -- sonst
	 * (kopffreie Unit-Tests, o. ae.) kommt unveraendert der Schluessel
	 * zurueck. Fuer diesen Fall faellt {@link #resolveDisplayText} auf
	 * {@link Translations#format}, das direkt aus der gebuendelten
	 * {@code en_us.json} liest, statt den rohen Schluessel anzuzeigen.</p>
	 *
	 * <p>ROADMAP GATE78: der generische Zweig (kein {@code displayKey})
	 * zeigte vorher {@link Throwable#getClass()}{@code .getSimpleName()} als
	 * Platzhalter -- ein roher Java-Klassenname ("JavaModStagingException",
	 * "IOException", ...) in sichtbarem Spielertext. {@link
	 * #friendlyFailureReason} liefert jetzt stattdessen einen bereits
	 * uebersetzten, spielerverstaendlichen Grund ohne jeden Klassennamen.</p>
	 */
	void showInstallError(PackInstallException e) {
		String key = e.displayKey() != null ? e.displayKey() : "vitrine.detail.error.generic";
		Object[] args = e.displayKey() != null ? e.displayArgs() : new Object[] { friendlyFailureReason(e) };
		installError = Optional.of(resolveDisplayText(key, args));
		if (this.client != null) {
			this.clearChildren();
			this.init();
		}
	}

	/**
	 * ROADMAP GATE78: uebersetzt eine beliebige {@link Throwable} in einen
	 * sicheren, spielerverstaendlichen Grund -- niemals {@link
	 * Throwable#getClass()}{@code .getSimpleName()} und niemals {@link
	 * Throwable#getMessage()} (kann Pfade/rohe Ursachen enthalten, siehe
	 * {@link PackInstallException}). Bevorzugt den strukturierten {@code
	 * displayKey()} der bekannten Ausnahmetypen ({@link
	 * JavaModStagingException}, {@link PackInstallException}); ohne einen
	 * solchen wird die Ursachenkette nach bekannten Java-Standardtypen
	 * (Netzwerk/Datei, Sicherheit, Zeitueberschreitung) abgesucht. Bleibt
	 * auch das erfolglos, kommt ein generischer, ebenso uebersetzter
	 * Rueckfall-Satz zum Zug -- nie der Klassenname selbst.
	 */
	static String friendlyFailureReason(Throwable e) {
		if (e instanceof JavaModStagingException je && je.displayKey() != null) {
			return resolveDisplayText(je.displayKey(), je.displayArgs());
		}
		if (e instanceof PackInstallException pe && pe.displayKey() != null) {
			return resolveDisplayText(pe.displayKey(), pe.displayArgs());
		}
		Throwable cursor = e;
		for (int depth = 0; cursor != null && depth < 8; depth++) {
			if (cursor instanceof java.util.concurrent.TimeoutException) {
				return resolveDisplayText("vitrine.detail.error.reason.timeout");
			}
			if (cursor instanceof SecurityException) {
				return resolveDisplayText("vitrine.detail.error.reason.security");
			}
			if (cursor instanceof java.io.IOException) {
				return resolveDisplayText("vitrine.detail.error.reason.io");
			}
			Throwable next = cursor.getCause();
			cursor = (next == cursor) ? null : next;
		}
		return resolveDisplayText("vitrine.detail.error.reason.unknown");
	}

	/**
	 * Uebersetzt {@code key}/{@code args} ueber Minecrafts {@link Text#translatable};
	 * liefert dessen Ergebnis nur dann, wenn es sich tatsaechlich vom rohen
	 * Schluessel unterscheidet (sprich: eine Sprachdatei war geladen). Sonst
	 * {@link Translations#format} als Rueckfallebene (siehe {@link #showInstallError}).
	 */
	private static String resolveDisplayText(String key, Object... args) {
		String fromLanguage = Text.translatable(key, args).getString();
		if (!fromLanguage.equals(key)) {
			return fromLanguage;
		}
		return Translations.format(Translations.FALLBACK_LOCALE, key, args);
	}

	/** Nur fuer Tests/Anbindung: der zuletzt sichtbar gemachte Installationsfehler, oder leer im Ruhezustand. */
	public Optional<String> installError() {
		return installError;
	}

	/**
	 * ROADMAP GATE20: der Deinstallieren-Knopf der Detailseite fragt zuerst
	 * nach. Vorher rief der Klick direkt {@link
	 * PackUninstallService#requestUninstall(String)} -- ohne Rueckfrage und
	 * ohne Weg zurueck. Jetzt entsteht hier nur die offene Entscheidung; das
	 * Deinstallieren laeuft erst nach dem Bestaetigen im {@link
	 * ConfirmUninstallDialog} (Abbrechen per Knopf oder ESC ist genauso
	 * leicht, docs/UI-SPEC.md "8. Fähigkeiten-Dialog").
	 */
	private void doUninstall() {
		UninstallConfirmModel confirmation =
				new UninstallConfirmModel(profileId(), model.header().name(), this::performUninstall);
		pendingUninstall = confirmation;
		if (this.client != null) {
			this.client.setScreen(new ConfirmUninstallDialog(this, confirmation));
		}
	}

	/** Das tatsaechliche Deinstallieren -- laeuft nur nach dem Bestaetigen (ROADMAP GATE20). */
	private void performUninstall() {
		PackUninstallService.requestUninstall(profileId());
		Vitrine.LOGGER.info("Deinstallieren von '{}' aus der Detailseite nach Rueckfrage bestaetigt und "
				+ "angestoßen.", profileId());
		returnToParent();
	}

	/**
	 * Nur fuer Tests/Anbindung: die zuletzt angestossene, noch offene oder
	 * bereits entschiedene Deinstallations-Rueckfrage (ROADMAP GATE20).
	 */
	public Optional<UninstallConfirmModel> pendingUninstall() {
		return Optional.ofNullable(pendingUninstall);
	}

	private void returnToParent() {
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public void close() {
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
		context.fill(0, 0, this.width, this.height, ActiveThemeColors.bg());

		if (brokenState != null) {
			renderBrokenState(context);
		} else {
			context.getMatrices().push();
			context.getMatrices().translate(0, -scrollY, 0);
			renderContent(context);
			context.getMatrices().pop();
			if (restartBanner != null) {
				restartBanner.render(context, this.textRenderer);
			}
		}

		// Kopfleiste zuletzt -- deckt weggescrollten Inhalt ab.
		context.fill(0, 0, this.width, VitrineTheme.HEADER_HEIGHT, ActiveThemeColors.surfaceSolid());
		context.fill(0, VitrineTheme.HEADER_HEIGHT - 1, this.width, VitrineTheme.HEADER_HEIGHT,
				ActiveThemeColors.borderTranslucent());
		ScaledText.draw(context, this.textRenderer, this.title,
				VitrineTheme.EDGE_PADDING * 2 + VitrineTheme.ICON_BUTTON_SIZE,
				(VitrineTheme.HEADER_HEIGHT - VitrineTheme.TYPE_TITLE) / 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);

		super.render(context, mouseX, mouseY, delta);

		renderFocusRing(context);
	}

	private void renderContent(DrawContext context) {
		int x = VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;

		renderHeaderBlock(context, x, y);

		int cursorY = y + VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT + VitrineTheme.DETAIL_SECTION_GAP
				+ VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.DETAIL_SECTION_GAP;

		cursorY = renderInstallError(context, x, cursorY);
		cursorY = renderScreenshots(context, x, cursorY);
		cursorY = renderDescription(context, x, cursorY);
		cursorY = renderCapabilities(context, x, cursorY);
		cursorY = renderDetails(context, x, cursorY);
		cursorY = renderChangelog(context, x, cursorY);
		renderMoreFromAuthorHeading(context, x, cursorY);
	}

	private void renderHeaderBlock(DrawContext context, int x, int y) {
		PackageDetailHeader header = model.header();
		int imgRight = x + VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH;
		int imgBottom = y + VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT;

		// GATE17: der Icon-Pfad zeigt INS Paket. Vorher wurde daraus eine
		// Ressource des Mods gebaut (Identifier.of("vitrine", p)) -- im Spiel die
		// schwarz-lila "Textur fehlt"-Flaeche, sobald ein Paket ein Icon hatte.
		Optional<PackageImageTextures.Texture> icon = header.iconPath()
				.flatMap(p -> model.sourceFile().flatMap(file -> PackageImageTextures.get(file, p)));
		if (icon.isPresent()) {
			PackageImageTextures.drawFitted(context, icon.get(), x, y,
					VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH, VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT);
		} else {
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(PLACEHOLDER_CARD, x, y, VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH,
					VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT, 0.0F, 0.0F,
					VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT,
					VitrineTheme.PLACEHOLDER_CARD_SOURCE_WIDTH, VitrineTheme.PLACEHOLDER_CARD_SOURCE_HEIGHT);
			int drawSize = VitrineTheme.TYPE_ICON_PLACEHOLDER_DRAW_SIZE;
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(Identifier.of("vitrine", header.type().iconPath()),
					x + (VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH - drawSize) / 2,
					y + (VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT - drawSize) / 2, drawSize, drawSize, 0.0F, 0.0F,
					VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
					VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
		}
		// GATE54: Bilder tragen radius.card wie die Kacheln (UI-SPEC "Karten, Bilder").
		RoundedRect.maskCorners(context, x, y, VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH,
				VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT, VitrineTheme.RADIUS_CARD, ActiveThemeColors.bg());
		RoundedRect.border(context, x, y, VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH,
				VitrineTheme.DETAIL_HEADER_IMAGE_HEIGHT, VitrineTheme.RADIUS_CARD, ActiveThemeColors.borderTranslucent());

		int textX = imgRight + VitrineTheme.SPACE_GUTTER;
		int textY = y;
		ScaledText.draw(context, this.textRenderer, Text.literal(header.name()), textX, textY,
				ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE_LARGE);
		textY += VitrineTheme.TYPE_TITLE_LARGE + VitrineTheme.DETAIL_LINE_GAP;
		ScaledText.draw(context, this.textRenderer,
				Text.translatable("vitrine.detail.author_label", header.author()), textX, textY,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		textY += VitrineTheme.TYPE_BODY + VitrineTheme.DETAIL_LINE_GAP;

		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(Identifier.of("vitrine", header.type().iconPath()), textX, textY,
				VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE, VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE, 0.0F, 0.0F,
				VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
				VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
		ScaledText.draw(context, this.textRenderer, Text.translatable(header.type().translationKey()),
				textX + VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE + VitrineTheme.SPACE_UNIT, textY,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		textY += VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE + VitrineTheme.DETAIL_LINE_GAP;

		// GATE70, docs/UI-SPEC.md:118-120: "Sterne/Downloads aus der GitHub-API
		// (read-only), Größe, Lizenz, Version, MC-Versionen" -- je ein kurzer
		// Beschriftung-plus-Wert-Zeile, derselbe "Label: Wert"-Aufbau wie im
		// "Details"-Abschnitt weiter unten, nur ohne eigene Ueberschrift.
		textY = renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.version", Optional.of(header.version()));
		textY = renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.license", header.license());
		textY = renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.size", header.size());
		textY = renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.minecraft_versions", header.minecraftVersions());
		textY = renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.stars",
				header.repoStats().stargazerCount().map(String::valueOf));
		renderHeaderFactLine(context, textX, textY, "vitrine.detail.field.downloads",
				header.repoStats().downloadCount().map(String::valueOf));

		header.status().translationKey().ifPresent(key ->
				ScaledText.draw(context, this.textRenderer, Text.translatable(key), textX, imgBottom - VitrineTheme.TYPE_CAPTION,
						ActiveThemeColors.accent(), VitrineTheme.TYPE_CAPTION));
	}

	/**
	 * GATE70: zeichnet eine Kopfbereich-Zeile "Beschriftung: Wert" (oder
	 * "Beschriftung: keine Angaben", wenn {@code value} leer ist) und liefert
	 * die naechste freie Y-Position -- derselbe Leerzustand wie im "Details"-
	 * Abschnitt ({@link #renderDetails}), nur ohne Abschnitts-Ueberschrift.
	 */
	private int renderHeaderFactLine(DrawContext context, int x, int y, String labelKey, Optional<String> value) {
		Text text = Text.translatable(labelKey).copy().append(": ")
				.append(value.isPresent() ? Text.literal(value.get()) : Text.translatable("vitrine.detail.value.unknown"));
		ScaledText.draw(context, this.textRenderer, text, x, y, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		return y + VitrineTheme.TYPE_CAPTION + VitrineTheme.DETAIL_LINE_GAP;
	}

	/**
	 * ROADMAP GATE34: zeigt {@link #installError} als Klartext in
	 * {@link ActiveThemeColors#danger()} unter dem Aktionsknopf -- oder ruehrt
	 * {@code cursorY} gar nicht an, wenn kein Fehler vorliegt.
	 */
	private int renderInstallError(DrawContext context, int x, int cursorY) {
		if (installError.isEmpty()) {
			return cursorY;
		}
		ScaledText.draw(context, this.textRenderer, Text.literal(installError.get()), x, cursorY,
				ActiveThemeColors.danger(), VitrineTheme.TYPE_BODY);
		return cursorY + installErrorHeight();
	}

	private int renderHeading(DrawContext context, int x, int cursorY, String key) {
		ScaledText.draw(context, this.textRenderer, Text.translatable(key), x, cursorY,
				ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		return cursorY + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
	}

	/**
	 * ROADMAP GATE82: ein echtes {@link ScreenshotGalleryWidget} (siehe
	 * {@link #init()}) zeichnet sich samt Ueberschrift selbst -- hier wird nur
	 * derselbe Platz vorgerueckt, den {@link #init()} fuer dessen Y-Position
	 * reserviert hat, damit die folgenden Abschnitte (Beschreibung, ...)
	 * unveraendert an derselben Stelle weiterzeichnen. Ohne Screenshots gibt es
	 * kein Widget -- dann wird wie zuvor nur der Leerzustand gezeichnet.
	 */
	private int renderScreenshots(DrawContext context, int x, int cursorY) {
		if (model.hasScreenshots()) {
			return cursorY + ScreenshotGalleryWidget.preferredHeight() + VitrineTheme.DETAIL_SECTION_GAP;
		}
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.screenshots");
		return emptyLine(context, x, cursorY, "vitrine.detail.screenshots.none");
	}

	private int renderDescription(DrawContext context, int x, int cursorY) {
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.description");
		if (model.description().isEmpty()) {
			return emptyLine(context, x, cursorY, "vitrine.detail.description.none");
		}
		String[] lines = model.description().get().split("\n", -1);
		int shown = descriptionLinesToShow();
		for (int i = 0; i < shown; i++) {
			ScaledText.draw(context, this.textRenderer, Text.literal(lines[i]), x, cursorY,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			cursorY += lineHeight();
		}
		if (model.descriptionNeedsCollapse()) {
			// ROADMAP GATE82: "Mehr/Weniger anzeigen" ist ein echtes ButtonWidget
			// (siehe init()), das sich selbst zeichnet -- hier nur denselben Platz
			// vorruecken, den init() dafuer reserviert hat.
			cursorY += VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.DETAIL_LINE_GAP;
		}
		return cursorY + VitrineTheme.DETAIL_SECTION_GAP - VitrineTheme.DETAIL_LINE_GAP;
	}

	private int renderCapabilities(DrawContext context, int x, int cursorY) {
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.capabilities");
		if (!model.capabilitiesKnown()) {
			// GATE76: Katalog-Angebot ohne Capability-Daten im Registry-Schema --
			// ehrlich "unbekannt" statt der falschen Verneinung "fordert keine
			// Faehigkeiten an" (Charter-Regel 2).
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.detail.capabilities.unknown"), x,
					cursorY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			cursorY += lineHeight();
		} else if (model.declaresNoCapabilities()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.detail.capabilities.none"), x, cursorY,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			cursorY += lineHeight();
		} else {
			for (VpkgCapability capability : model.capabilities()) {
				// Die Faehigkeits-Bezeichner decken sich mit den Kategorie-Ids
				// (VpkgCapability/TilePackageType) -- dasselbe Icon-Asset.
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				context.drawTexture(Identifier.of("vitrine",
								"textures/gui/icons/category_" + capability.id() + ".png"),
						x, cursorY, VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE, VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE,
						0.0F, 0.0F, VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
						VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
				ScaledText.draw(context, this.textRenderer, Text.translatable(capability.translationKey()),
						x + VitrineTheme.DETAIL_CAPABILITY_ICON_SIZE + VitrineTheme.SPACE_UNIT, cursorY,
						ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
				cursorY += lineHeight();
			}
		}
		if (model.runsNoProgramCode()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.detail.capabilities.no_code"), x, cursorY,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
			cursorY += lineHeight();
		}
		return cursorY + VitrineTheme.DETAIL_SECTION_GAP - VitrineTheme.DETAIL_LINE_GAP;
	}

	private int renderDetails(DrawContext context, int x, int cursorY) {
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.details");
		for (PackageDetailFact fact : model.details()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable(fact.labelKey()), x, cursorY,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
			Text value = fact.isKnown()
					? Text.literal(fact.value().get())
					: Text.translatable("vitrine.detail.value.unknown");
			ScaledText.draw(context, this.textRenderer, value,
					x + VitrineTheme.DETAIL_HEADER_IMAGE_WIDTH / 2, cursorY,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			cursorY += lineHeight();
		}
		return cursorY + VitrineTheme.DETAIL_SECTION_GAP - VitrineTheme.DETAIL_LINE_GAP;
	}

	private int renderChangelog(DrawContext context, int x, int cursorY) {
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.changelog");
		if (model.changelog().isEmpty()) {
			return emptyLine(context, x, cursorY, "vitrine.detail.changelog.none");
		}
		for (String line : model.changelog().get().split("\n", -1)) {
			ScaledText.draw(context, this.textRenderer, Text.literal(line), x, cursorY,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			cursorY += lineHeight();
		}
		return cursorY + VitrineTheme.DETAIL_SECTION_GAP - VitrineTheme.DETAIL_LINE_GAP;
	}

	private void renderMoreFromAuthorHeading(DrawContext context, int x, int cursorY) {
		if (model.hasMoreFromAuthor()) {
			// Die Ueberschrift zeichnet das TileRowWidget selbst.
			return;
		}
		cursorY = renderHeading(context, x, cursorY, "vitrine.detail.section.more_from_author");
		emptyLine(context, x, cursorY, "vitrine.detail.more_from_author.none");
	}

	private int emptyLine(DrawContext context, int x, int cursorY, String key) {
		ScaledText.draw(context, this.textRenderer, Text.translatable(key), x, cursorY,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		return cursorY + lineHeight() + VitrineTheme.DETAIL_SECTION_GAP - VitrineTheme.DETAIL_LINE_GAP;
	}

	private void renderFocusRing(DrawContext context) {
		if (!(this.getFocused() instanceof ClickableWidget widget) || widget instanceof ButtonWidget) {
			return;
		}
		FocusRing.drawFor(context, widget);
	}

	private static int countLines(String text) {
		int lines = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Nur fuer Tests/Anbindung: das zugrunde liegende, client-freie Modell dieser Seite. */
	public PackageDetailModel model() {
		return model;
	}

	/** Nur fuer Tests/Anbindung: die Kacheln in der "weitere Pakete des Autors"-Reihe, oder {@code null}. */
	public List<TileData> moreFromAuthorTiles() {
		return moreFromAuthorRow == null ? null : model.moreFromAuthor();
	}
}
