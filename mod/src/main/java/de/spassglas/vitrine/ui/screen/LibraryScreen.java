package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.catalog.UpdateStatus;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.PackUpdater;
import de.spassglas.vitrine.ui.detail.HumanReadableSize;
import de.spassglas.vitrine.ui.detail.LocalPackageDetailSource;
import de.spassglas.vitrine.ui.detail.PackageDetailModel;
import de.spassglas.vitrine.ui.dialog.ConfirmUninstallDialog;
import de.spassglas.vitrine.ui.dialog.UninstallConfirmModel;
import de.spassglas.vitrine.ui.downloads.DownloadFlyoutService;
import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.library.LibraryConflict;
import de.spassglas.vitrine.ui.library.LibraryEntry;
import de.spassglas.vitrine.ui.library.LibraryModel;
import de.spassglas.vitrine.ui.state.StoreStateView;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.FocusRing;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import de.spassglas.vitrine.ui.tile.TileStatus;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bibliotheks-Ansicht (ROADMAP S5, docs/UI-SPEC.md "6. Bibliothek"): Liste der
 * installierten Pakete mit Aktiv-Schalter und Ueberlaufmenue je Zeile, ein
 * Abschnitt "Reihenfolge" (per Ziehen sortierbar, Tastatur-Alternative
 * ueber die sichtbaren "Nach oben"/"Nach unten"-Schaltflaechen bzw.
 * Alt+Pfeil-oben/-unten auf einer fokussierten Zeile -- S9), eine
 * Konflikthinweiszeile und der Knopf "Alle aktualisieren".
 *
 * <p>Alle Daten, die Reihenfolge-Logik und die Konflikterkennung liefert das
 * client-freie {@link LibraryModel}; dieser Screen bindet nur Zeichnen und
 * Eingabe daran. Jede Zahl/Farbe stammt aus {@link VitrineTheme}, jeder
 * Anzeigetext aus einem Uebersetzungs-Key (die paket-eigenen Freitexte Name/
 * Autor/Version werden als {@code Text.literal(<variable>)} gesetzt).</p>
 */
public final class LibraryScreen extends Screen {

	/** ROADMAP GATE13: Flyout-Zeilen-Id fuer den Sammelvorgang "Alle aktualisieren" -- kein echtes Paket. */
	private static final String UPDATE_ALL_FLYOUT_ID = "vitrine/_library_update_all";
	/** ROADMAP GATE83: Vorsilbe der Flyout-Zeilen-Id fuer ein Einzelpaket-Update aus dem Ueberlaufmenue. */
	private static final String UPDATE_ONE_FLYOUT_PREFIX = "vitrine/_library_update_one_";
	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek"): die vier Eintraege im
	 * Ueberlaufmenue -- Aktualisieren, Deinstallieren, Ordner oeffnen, Details
	 * -- bestimmen, wie viel zusaetzliche Zeilenhoehe ein offenes Menue braucht
	 * ({@link #renderContent}).
	 */
	private static final int OVERFLOW_MENU_ENTRY_COUNT = 4;

	private final Screen parentScreen;
	private final LibraryModel model;

	/**
	 * ROADMAP GATE9/GATE13: {@code true}, waehrend {@link LibraryModel#updateAll()}
	 * (echter Katalog-Versionsabgleich, Netz-I/O) auf einem Hintergrund-Thread
	 * laeuft -- der Knopf "Alle aktualisieren" bleibt bis dahin deaktiviert,
	 * statt einen zweiten, ueberlappenden Lauf anzustossen.
	 */
	private boolean updatingAll;

	/**
	 * ROADMAP GATE113: wie der echte Client eine eingeplante Aufgabe
	 * abarbeitet -- Produktion ({@link #scheduleOnClientThread}) reicht sie an
	 * {@code this.client.execute(...)} weiter. {@code
	 * LibraryScreenThemeChangeSchedulingTest} ersetzt das durch eine
	 * aufzeichnende Variante, die die Aufgabe absichtlich <em>nicht</em>
	 * ausfuehrt, und beweist damit, dass {@link #onThemeChanged} den
	 * eigentlichen Neuaufbau nur einplant statt ihn auf dem aufrufenden
	 * Fremd-Thread direkt auszufuehren.
	 */
	private Consumer<Runnable> clientThreadScheduler = this::scheduleOnClientThread;

	/** ROADMAP GATE113: wie viele Male {@link #rebuild} seit der Konstruktion tatsaechlich lief -- nur fuer Tests. */
	private int rebuildCountForTest;

	/** ROADMAP S7: der Leer-Zustand kommt aus der wiederverwendbaren Zustands-Komponente (eine Wahrheit). */
	private final StoreStateView emptyStateView = StoreStateView.libraryEmpty();

	private final List<ScrollAnchor> scrollAnchors = new ArrayList<>();
	private final List<OrderRow> orderRows = new ArrayList<>();

	private List<LibraryEntry> entries = List.of();
	private List<LibraryConflict> conflicts = List.of();
	/**
	 * ROADMAP GATE80: der Update-Status je Zeile ({@link LibraryModel#updateStatuses()},
	 * echter Katalog-Versionsabgleich K5), einmal je {@link #init()} als Momentaufnahme
	 * geholt (nicht jeden Frame -- das waere ein .vpkg-Scan samt Sicherheitspruefung
	 * je installiertem Paket, 60-mal pro Sekunde). Schluessel = {@link LibraryEntry#packId()}.
	 */
	private Map<String, UpdateStatus> updateStatusByPackId = Map.of();
	/**
	 * ROADMAP GATE81 ("Fehlgeschlagenes Update/Rollback bleibt unerklärt"):
	 * Paket-IDs, fuer die {@link LibraryModel#failedUpdatePackIds()} ein
	 * zuletzt gescheitertes Update meldet -- sichtbar direkt auf der eigenen
	 * Zeile ({@link #renderListRowText}), ohne dass ein Flyout geoeffnet
	 * werden muss. Vorher war der Lang-Key {@code vitrine.library.update_failed}
	 * zwar uebersetzt, aber nirgends verdrahtet.
	 */
	private Set<String> failedUpdatePackIds = Set.of();

	/**
	 * ROADMAP GATE83: Paket-IDs, fuer die gerade ein Einzel-Update
	 * (Ueberlaufmenue "Aktualisieren") auf einem Hintergrund-Thread laeuft --
	 * gleiches Doppellauf-Schutzmuster wie {@link #updatingAll}, nur je Zeile.
	 */
	private final Set<String> updatingPackIds = new HashSet<>();

	/**
	 * ROADMAP GATE119: Paket-IDs, fuer die der An/Aus-Schalter gerade auf einem
	 * Hintergrund-Thread laeuft ({@link #onToggleActive}, ZIP oeffnen,
	 * Manifest parsen, Signatur pruefen, {@code state.json} schreiben) --
	 * gleiches Doppellauf-Schutzmuster wie {@link #updatingPackIds} fuer
	 * "Aktualisieren". Vorher lief dieses I/O synchron auf dem Render-Thread
	 * (Frame-Stocker beim Umschalten); der Knopf der Zeile bleibt jetzt bis
	 * zum Abschluss gesperrt und zeigt "Arbeitet …" statt still einzufrieren.
	 */
	private final Set<String> togglingPackIds = new HashSet<>();

	/**
	 * Nur fuer Tests (ROADMAP GATE119): ersetzt, wie {@link #onToggleActive}
	 * die eigentliche E/A vom aufrufenden Thread wegschiebt. {@code null}
	 * (Produktion) heisst: genau das Muster von {@link #onUpdateAll}/
	 * {@link #onUpdateOne} -- mit laufendem {@link #client} ein eigener
	 * Daemon-Thread, ohne Client (kopffreie Tests) synchron im aufrufenden
	 * Thread. Ein Test kann das ersetzen, um den aufrufenden Thread der E/A
	 * mitzuschreiben und zu beweisen, dass er nicht der Thread ist, der
	 * {@link #onToggleActive} aufgerufen hat.
	 */
	private Consumer<Runnable> backgroundWorkSchedulerForTest;

	/**
	 * ROADMAP GATE112: die je Zeile gebauten Aktiv-Schalter, nach {@link
	 * LibraryEntry#packId()} indiziert -- damit {@link #updatingAll} auch sie
	 * sperren kann ({@link #buildListRow}) und Tests ({@code
	 * LibraryScreenRowLockTest}) das ohne laufenden Client pruefen koennen.
	 * Vorher sperrte "Alle aktualisieren" nur seinen eigenen Knopf
	 * ({@link #updatingAll}, Zeile 209) und die Ueberlaufmenue-Aktualisieren-
	 * Eintraege ({@link #updatingPackIds}) -- der An/Aus-Schalter einer
	 * einzelnen Zeile blieb waehrend des Sammel-Laufs weiter bedienbar und
	 * konnte {@link LibraryModel#setActive} parallel zum laufenden
	 * {@link LibraryModel#updateAll()} auf demselben Paket ausloesen.
	 */
	private final Map<String, ButtonWidget> toggleButtonsByPackId = new LinkedHashMap<>();

	/** Paket-ID, deren Ueberlaufmenue gerade offen ist, oder {@code null}. */
	private String openMenuPackId;
	/**
	 * ROADMAP GATE49: Klartext-Fehler des zuletzt gescheiterten Aktiv-Schalter-
	 * Versuchs, oder leer im Ruhezustand. Vorher fing {@link #onToggleActive}
	 * jeden Fehlschlag nur mit einem Log-Eintrag ab und baute die Liste neu auf
	 * -- der Schalter sprang optisch kommentarlos zurueck. Jetzt zeigt die
	 * Liste denselben Satz, wie {@code PackageDetailScreen#installError} es
	 * fuer die Installation tut (GATE34/48).
	 */
	private Optional<String> libraryError = Optional.empty();
	/** ROADMAP GATE20: die zuletzt angestossene Deinstallations-Rueckfrage, oder {@code null}. */
	private UninstallConfirmModel pendingUninstall;
	/** Paket-ID der zuletzt im Abschnitt "Reihenfolge" angefassten Zeile (fuer Alt+Pfeil). */
	private String activeOrderPackId;
	/** Paket-ID, die gerade gezogen wird, oder {@code null}. */
	private String draggingPackId;
	private int dragPointerY;

	private int scrollY;
	private int contentHeight;

	public LibraryScreen(Screen parentScreen) {
		this(parentScreen, LibraryModel.forConfigDir(
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID)));
	}

	/** Testbarer/entkoppelter Konstruktor. */
	public LibraryScreen(Screen parentScreen, LibraryModel model) {
		super(Text.translatable("vitrine.library.title"));
		this.parentScreen = parentScreen;
		this.model = model;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	/**
	 * ROADMAP T4: baut die Liste bei einem Laufzeit-Theme-Wechsel neu auf, damit
	 * die im Aufbau festgehaltenen Farben sofort umfaerben (Muster wie
	 * {@code StoreScreen}). In {@link #removed()} wieder abgemeldet.
	 */
	private final Runnable themeRepaintListener = this::onThemeChanged;

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
	 * {@code rebuild()} darf aber nur auf dem Client-Thread laufen (der
	 * Render-Thread kann parallel ueber {@code Screen.children()} iterieren).
	 * Deshalb nur ueber {@link #clientThreadScheduler} einplanen, nie direkt
	 * ausfuehren; die {@code currentScreen}-Pruefung laeuft dabei absichtlich
	 * erst bei der tatsaechlichen Ausfuehrung erneut, der sichtbare
	 * Bildschirm kann bis dahin gewechselt haben.</p>
	 *
	 * <p>Paketsichtbar (kein {@code private}), damit {@code
	 * LibraryScreenThemeChangeSchedulingTest} ihn wie den echten {@code
	 * ThemeService}-Rueckruf direkt aufrufen kann -- gleiches Muster wie
	 * {@link #onToggleActive}.</p>
	 */
	void onThemeChanged() {
		clientThreadScheduler.accept(() -> {
			if (this.client != null && this.client.currentScreen == this) {
				rebuild();
			}
		});
	}

	/** ROADMAP GATE113: Produktionsweg des {@link #clientThreadScheduler} -- reicht nur an den echten Client-Thread weiter, ohne Client gibt es nichts einzuplanen. */
	private void scheduleOnClientThread(Runnable task) {
		if (this.client != null) {
			this.client.execute(task);
		}
	}

	/** Nur fuer Tests (ROADMAP GATE113): ersetzt die Einplanung auf dem Client-Thread, um zu beweisen, dass {@link #onThemeChanged} einplant statt direkt auszufuehren. */
	void setClientThreadSchedulerForTest(Consumer<Runnable> scheduler) {
		this.clientThreadScheduler = Objects.requireNonNull(scheduler, "scheduler");
	}

	/** Nur fuer Tests (ROADMAP GATE113): wie oft {@link #rebuild} seit der Konstruktion tatsaechlich lief. */
	int rebuildCountForTest() {
		return rebuildCountForTest;
	}

	private record ScrollAnchor(ClickableWidget widget, int baseY) {
	}

	private record OrderRow(String packId, int baseY, int height) {
	}

	@Override
	protected void init() {
		scrollAnchors.clear();
		orderRows.clear();
		toggleButtonsByPackId.clear();
		entries = model.entries();
		conflicts = model.conflicts();
		Map<String, UpdateStatus> statuses = new LinkedHashMap<>();
		for (UpdateStatus status : model.updateStatuses()) {
			statuses.put(status.packId(), status);
		}
		updateStatusByPackId = Map.copyOf(statuses);
		failedUpdatePackIds = Set.copyOf(model.failedUpdatePackIds());

		int buttonY = (VitrineTheme.HEADER_HEIGHT - VitrineTheme.ICON_BUTTON_SIZE) / 2;
		var backButton = de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(VitrineTheme.EDGE_PADDING, buttonY, VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.ICON_BUTTON_SIZE, Text.translatable("vitrine.header.back"), b -> this.close(), net.minecraft.util.Identifier.of("vitrine", "textures/gui/icons/ui_back.png"), null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		this.addDrawableChild(backButton);

		int updateAllX = this.width - VitrineTheme.SPACE_PAGE - VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH;
		ButtonWidget updateAllButton = ButtonWidget
				.builder(Text.translatable("vitrine.action.update_all"), b -> onUpdateAll())
				// mittig in der Kopfleiste (vorher an der Oberkante des Zurueck-Knopfs ausgerichtet)
				.dimensions(updateAllX, (VitrineTheme.HEADER_HEIGHT - VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT) / 2,
						VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
						VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.build();
		// ROADMAP GATE9: waehrend des echten Update-Laufs (Netz-I/O auf dem
		// Hintergrund-Thread) bleibt der Knopf deaktiviert -- kein zweiter,
		// ueberlappender Lauf per Doppelklick.
		updateAllButton.active = !updatingAll;
		this.addDrawableChild(updateAllButton);

		int x = VitrineTheme.SPACE_PAGE;
		int y = VitrineTheme.HEADER_HEIGHT + VitrineTheme.SPACE_PAGE;

		if (entries.isEmpty()) {
			int emptyButtonY = y + StoreStatePanel.contentAdvance();
			int emptyButtonX = this.width / 2 - VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH / 2;
			ButtonWidget toStore = ButtonWidget
					.builder(Text.translatable(emptyStateView.action().orElseThrow().labelKey()), b -> this.close())
					.dimensions(emptyButtonX, emptyButtonY, VitrineTheme.DETAIL_ACTION_BUTTON_WIDTH,
							VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
					.build();
			this.addDrawableChild(toStore);
			contentHeight = emptyButtonY + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.SPACE_PAGE;
			clampScroll();
			applyScroll();
			return;
		}

		int rowWidth = this.width - x - VitrineTheme.SPACE_PAGE;
		int cursorY = y;

		// --- Abschnitt: Updates-Kopfzeile (nur wenn es welche gibt) ---
		if (updateCount() > 0) {
			cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		}

		// --- GATE49: Klartext-Fehler des letzten Aktiv-Schalter-Versuchs ---
		cursorY += libraryErrorHeight();

		// --- Abschnitt: installierte Pakete (Liste) ---
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		for (LibraryEntry entry : entries) {
			cursorY = buildListRow(entry, x, cursorY, rowWidth);
		}

		cursorY += VitrineTheme.LIBRARY_SECTION_GAP;

		// --- Abschnitt: Reihenfolge (nur Ressourcen-Pakete, erst ab zwei sinnvoll) ---
		List<LibraryEntry> orderable = entries.stream().filter(LibraryEntry::isResourcePack).toList();
		if (orderable.size() > 1) {
			cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
			cursorY += VitrineTheme.TYPE_CAPTION + VitrineTheme.DETAIL_HEADING_GAP; // Hinweis-Zeile
			for (LibraryEntry entry : orderable) {
				cursorY = buildOrderRow(entry, x, cursorY, rowWidth, orderable);
			}
			cursorY += VitrineTheme.LIBRARY_SECTION_GAP;
		}

		// --- Abschnitt: Konflikthinweis (nur wenn es Konflikte gibt) ---
		if (!conflicts.isEmpty()) {
			cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
			cursorY += conflicts.size() * lineHeight();
		}

		contentHeight = cursorY + VitrineTheme.SPACE_PAGE;
		clampScroll();
		applyScroll();
	}

	/** Innenabstand der Karten-Zeilen. */
	private static final int ROW_PAD = 8;

	private long updateCount() {
		return updateStatusByPackId.values().stream().filter(UpdateStatus::updateAvailable).count();
	}

	private int buildListRow(LibraryEntry entry, int x, int cursorY, int rowWidth) {
		boolean active = entry.active();
		// ROADMAP GATE119: waehrend die E/A dieses Schalters auf dem
		// Hintergrund-Thread laeuft, zeigt der Knopf "Arbeitet …" (dieselbe
		// Beschriftung wie PackageDetailScreens WORKING-Zustand) statt
		// Aktivieren/Deaktivieren -- sichtbare Rueckmeldung statt eines stillen
		// Frame-Stockers.
		boolean toggling = togglingPackIds.contains(entry.packId());
		Text toggleLabel = toggling
				? Text.translatable("vitrine.detail.action.working")
				: Text.translatable(active ? "vitrine.action.deactivate" : "vitrine.action.activate");
		int toggleX = x + rowWidth - VitrineTheme.LIBRARY_TOGGLE_WIDTH;
		int buttonY = cursorY + (VitrineTheme.LIBRARY_ROW_HEIGHT - VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT) / 2;
		ButtonWidget toggle = ButtonWidget.builder(toggleLabel, b -> onToggleActive(entry, !active))
				.dimensions(toggleX - ROW_PAD, buttonY, VitrineTheme.LIBRARY_TOGGLE_WIDTH,
						VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.build();
		if (!active) {
			de.spassglas.vitrine.ui.theme.VitrineButtonStyle.primary(toggle);
		}
		// ROADMAP GATE112: waehrend "Alle aktualisieren" laeuft, bleibt auch der
		// einzelne Zeilen-Schalter gesperrt -- sonst koennte ein Klick hier
		// mitten im Sammel-Update desselben Pakets ansetzen.
		// ROADMAP GATE115: ein kaputter/fehlender Eintrag hat keine Datei zum
		// Akivieren -- der Schalter bleibt gesperrt statt gegen eine fehlende
		// .vpkg zu laufen.
		// ROADMAP GATE119: und gesperrt, waehrend die eigene E/A dieses Schalters
		// noch laeuft (Doppelklick-Schutz, zweite Verteidigungslinie in
		// onToggleActive).
		toggle.active = !updatingAll && !entry.isMissing() && !toggling;
		this.addDrawableChild(toggle);
		toggleButtonsByPackId.put(entry.packId(), toggle);
		scrollAnchors.add(new ScrollAnchor(toggle, buttonY));

		int overflowX = toggleX - ROW_PAD - VitrineTheme.CONTROL_GAP - VitrineTheme.ICON_BUTTON_SIZE;
		// umgekocht (2026-10-07): echtes "Mehr"-Symbol statt eines Schriftzeichens im Knopf
		ButtonWidget overflow = de.spassglas.vitrine.ui.widget.IconButtonWidget.iconOnly(overflowX, buttonY,
				VitrineTheme.ICON_BUTTON_SIZE, VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT,
				Text.translatable("vitrine.library.overflow"), b -> onOverflowClicked(entry),
				Identifier.of("vitrine", "textures/gui/icons/ui_overflow.png"), null, VitrineTheme.UI_ICON_SOURCE_SIZE);
		this.addDrawableChild(overflow);
		scrollAnchors.add(new ScrollAnchor(overflow, buttonY));

		int nextY = cursorY + VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;

		if (entry.packId().equals(openMenuPackId)) {
			// ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek"): Aktualisieren,
			// Deinstallieren, Ordner oeffnen, Details -- in genau dieser
			// Reihenfolge. "Nach oben"/"Nach unten" gehoeren NICHT hierher, das
			// sind die Ziehen-Tastatur-Alternativen im Abschnitt "Reihenfolge"
			// (buildOrderRow) und wuerden hier nur verdoppelt.
			int menuX = overflowX - VitrineTheme.CONTROL_GAP - VitrineTheme.LIBRARY_TOGGLE_WIDTH;
			// ROADMAP GATE115: ein kaputter/fehlender Eintrag hat keine .vpkg --
			// "Aktualisieren" und "Details" brauchen beide die Datei (Download-
			// Ziel bzw. LocalPackageDetailSource#loadInstalledFile) und bleiben
			// deshalb gesperrt; "Deinstallieren" (den verwaisten state.json-Eintrag
			// entfernen) und "Ordner oeffnen" bleiben die ehrlichen Auswege.
			nextY = addMenuButton(menuX, nextY, "vitrine.action.update", b -> onUpdateOne(entry),
					!entry.isMissing() && !updatingPackIds.contains(entry.packId()));
			nextY = addMenuButton(menuX, nextY, "vitrine.action.uninstall", b -> onUninstall(entry), true);
			nextY = addMenuButton(menuX, nextY, "vitrine.library.open_folder", b -> onOpenFolder(), true);
			nextY = addMenuButton(menuX, nextY, "vitrine.library.details", b -> onOpenDetails(entry), !entry.isMissing());
		}
		return nextY;
	}

	private int addMenuButton(int x, int y, String key, ButtonWidget.PressAction action, boolean active) {
		ButtonWidget button = ButtonWidget.builder(Text.translatable(key), action)
				.dimensions(x, y, VitrineTheme.LIBRARY_TOGGLE_WIDTH, VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.build();
		button.active = active;
		this.addDrawableChild(button);
		scrollAnchors.add(new ScrollAnchor(button, y));
		return y + VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
	}

	private int buildOrderRow(LibraryEntry entry, int x, int cursorY, int rowWidth, List<LibraryEntry> orderable) {
		int upX = x + rowWidth - VitrineTheme.LIBRARY_REORDER_BUTTON_WIDTH * 2 - VitrineTheme.CONTROL_GAP;
		int downX = x + rowWidth - VitrineTheme.LIBRARY_REORDER_BUTTON_WIDTH;
		boolean isFirst = orderable.get(0).packId().equals(entry.packId());
		boolean isLast = orderable.get(orderable.size() - 1).packId().equals(entry.packId());

		ButtonWidget up = ButtonWidget.builder(Text.literal("↑"), b -> onMove(entry, -1))
				.dimensions(upX, cursorY, VitrineTheme.LIBRARY_REORDER_BUTTON_WIDTH,
						VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.tooltip(Tooltip.of(Text.translatable("vitrine.library.move_up")))
				.build();
		up.active = !isFirst;
		this.addDrawableChild(up);
		scrollAnchors.add(new ScrollAnchor(up, cursorY));

		ButtonWidget down = ButtonWidget.builder(Text.literal("↓"), b -> onMove(entry, +1))
				.dimensions(downX, cursorY, VitrineTheme.LIBRARY_REORDER_BUTTON_WIDTH,
						VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT)
				.tooltip(Tooltip.of(Text.translatable("vitrine.library.move_down")))
				.build();
		down.active = !isLast;
		this.addDrawableChild(down);
		scrollAnchors.add(new ScrollAnchor(down, cursorY));

		orderRows.add(new OrderRow(entry.packId(), cursorY, VitrineTheme.LIBRARY_ROW_HEIGHT));
		return cursorY + VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
	}

	// --- Aktionen --------------------------------------------------------

	/**
	 * ROADMAP GATE9/GATE13: "Alle aktualisieren" ruft jetzt wirklich
	 * {@link LibraryModel#updateAll()} (echter Katalog-Versionsabgleich samt
	 * Download und K5-Rueckfall) auf -- vorher loeste der Knopf nur einen
	 * Ressourcen-Reload aus, ohne den Updater je zu erreichen. Der Lauf tut
	 * Netz-I/O und darf deshalb nicht den Render-Thread blockieren (GATE9):
	 * ein eigener, daemon-Thread wie bei {@code StoreScreen#startCatalogRefresh},
	 * die Rueckmeldung erscheint als Sammel-Zeile im Download-Flyout (GATE4/13).
	 */
	private void onUpdateAll() {
		if (updatingAll) {
			return;
		}
		updatingAll = true;
		DownloadFlyoutService.instance().startDownload(UPDATE_ALL_FLYOUT_ID,
				Text.translatable("vitrine.action.update_all").getString(), -1L);
		rebuild();

		Runnable work = () -> {
			List<PackUpdater.UpdateResult> results = model.updateAll();
			long failed = results.stream().filter(PackUpdater.UpdateResult::rolledBack).count();
			if (failed > 0) {
				DownloadFlyoutService.instance().markFailed(UPDATE_ALL_FLYOUT_ID);
			} else {
				DownloadFlyoutService.instance().markDone(UPDATE_ALL_FLYOUT_ID);
			}
			Vitrine.LOGGER.info("Bibliothek: 'Alle aktualisieren' abgeschlossen -- {} Ergebnis(se), {} davon "
					+ "fehlgeschlagen (alte Version laeuft weiter).", results.size(), failed);
			runOnClientThread(() -> {
				updatingAll = false;
				rebuild();
			});
		};

		if (this.client != null) {
			Thread worker = new Thread(work, "Vitrine-Update-All");
			worker.setDaemon(true);
			worker.start();
		} else {
			work.run();
		}
	}

	/** Wie {@code PackageDetailScreen#onClientThread} -- der abschliessende Bildschirm-Zustand gehoert auf den Client-Thread. */
	private void runOnClientThread(Runnable uiWork) {
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

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Ueberlaufmenue-Eintrag
	 * "Aktualisieren" je Zeile) -- wie {@link #onUpdateAll()}, aber fuer genau
	 * dieses eine Paket ({@link LibraryModel#updateOne(String)}, echter
	 * Katalog-Versionsabgleich samt Download und K5-Rueckfall). Laeuft aus
	 * demselben GATE9-Grund (Netz-I/O) auf einem eigenen Daemon-Thread, nie auf
	 * dem Render-Thread.
	 */
	private void onUpdateOne(LibraryEntry entry) {
		openMenuPackId = null;
		if (updatingPackIds.contains(entry.packId())) {
			return;
		}
		updatingPackIds.add(entry.packId());
		String flyoutId = UPDATE_ONE_FLYOUT_PREFIX + entry.packId();
		DownloadFlyoutService.instance().startDownload(flyoutId, entry.name(), -1L);
		rebuild();

		Runnable work = () -> {
			PackUpdater.UpdateResult result = model.updateOne(entry.manifestId());
			boolean failed = result != null && result.rolledBack();
			if (failed) {
				DownloadFlyoutService.instance().markFailed(flyoutId);
			} else {
				DownloadFlyoutService.instance().markDone(flyoutId);
			}
			Vitrine.LOGGER.info("Bibliothek: Aktualisieren von '{}' abgeschlossen -- Ergebnis {}.", entry.packId(),
					result == null ? "keins" : result.outcome());
			runOnClientThread(() -> {
				updatingPackIds.remove(entry.packId());
				rebuild();
			});
		};

		if (this.client != null) {
			Thread worker = new Thread(work, "Vitrine-Update-" + entry.manifestId());
			worker.setDaemon(true);
			worker.start();
		} else {
			work.run();
		}
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Ueberlaufmenue-Eintrag
	 * "Ordner oeffnen") -- oeffnet {@code packs/} (dort liegen alle
	 * installierten {@code .vpkg}-Dateien) im System-Dateimanager, gleiches
	 * Muster wie Vanillas "Open Resource Pack Folder".
	 */
	private void onOpenFolder() {
		openMenuPackId = null;
		Path packsDir = model.packsDir();
		try {
			Files.createDirectories(packsDir);
		} catch (IOException e) {
			Vitrine.LOGGER.warn("Bibliothek: Paketordner '{}' liess sich nicht anlegen: {}", packsDir, e.toString());
		}
		Util.getOperatingSystem().open(packsDir);
		rebuild();
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Ueberlaufmenue-Eintrag
	 * "Details" -- "kein Weg von der Bibliothek zur Detailseite eines
	 * installierten Pakets") -- baut das Detailmodell direkt aus der
	 * installierten {@code .vpkg}-Datei ({@link LibraryEntry#file()}) und
	 * oeffnet dieselbe {@link PackageDetailScreen}, die auch der Store fuer ein
	 * installiertes Angebot zeigt (gleiches Muster wie {@code
	 * StoreScreen#openDetail}).
	 */
	private void onOpenDetails(LibraryEntry entry) {
		openMenuPackId = null;
		if (this.client == null) {
			return;
		}
		UpdateStatus status = updateStatusByPackId.get(entry.packId());
		TileStatus tileStatus = status != null && status.updateAvailable()
				? TileStatus.UPDATE_AVAILABLE
				: TileStatus.INSTALLED;
		Optional<PackageDetailModel> loaded =
				LocalPackageDetailSource.loadInstalledFile(entry.file(), tileStatus, List.of());
		if (loaded.isPresent()) {
			this.client.setScreen(new PackageDetailScreen(this, loaded.get()));
		} else {
			Vitrine.LOGGER.warn("Bibliothek: '{}' liess sich nicht als Detailseite laden -- Paket-kaputt-Zustand.",
					entry.packId());
			this.client.setScreen(PackageDetailScreen.broken(this, StoreStateView.packageBroken(entry.name())));
		}
	}

	/**
	 * ROADMAP GATE49: ein Fehlschlag haelt jetzt {@link #libraryError} sichtbar
	 * (gleiches Muster wie {@code PackageDetailScreen#showInstallError},
	 * GATE34/48) statt den Schalter stumm zurueckspringen zu lassen -- nur
	 * geloggt wurde vorher nichts an der Oberflaeche sichtbar gemacht.
	 *
	 * <p>ROADMAP GATE119: {@link LibraryModel#setActive} tut echtes Platten-I/O
	 * ({@code .vpkg} als ZIP oeffnen, Manifest parsen, Signatur pruefen,
	 * {@code state.json} schreiben -- siehe {@code PackInstaller#setActive}).
	 * Lief das vorher direkt hier, blockierte ein Klick auf den Schalter den
	 * Render-Thread fuer die Dauer dieses I/O. Jetzt laeuft es -- genau wie
	 * {@link #onUpdateAll()}/{@link #onUpdateOne(LibraryEntry)} (GATE9/GATE13)
	 * -- auf einem eigenen Daemon-Thread; nur der abschliessende Bildschirm-
	 * Zustand (Sperre loesen, Fehler setzen, neu aufbauen) geht ueber
	 * {@link #runOnClientThread} zurueck auf den Client-Thread.</p>
	 *
	 * <p>Paketsichtbar (kein {@code private}), damit {@code LibraryScreenToggleErrorTest}
	 * den Fehlerpfad ohne laufenden Client treiben kann -- gleiches Muster wie
	 * {@code PackageDetailScreen#showInstallError}.</p>
	 */
	void onToggleActive(LibraryEntry entry, boolean active) {
		// ROADMAP GATE112: zweite Verteidigungslinie neben dem gesperrten Knopf
		// (buildListRow) -- ein bereits in Bearbeitung befindlicher Klick (z.B.
		// per Tastatur/Controller statt Maus-Klick auf den deaktivierten Knopf)
		// darf waehrend "Alle aktualisieren" trotzdem nicht durchgehen.
		if (updatingAll) {
			return;
		}
		// ROADMAP GATE119: zweite Verteidigungslinie fuer den eigenen Schalter --
		// waehrend seine eigene E/A noch auf dem Hintergrund-Thread laeuft, darf
		// kein zweiter Klick denselben Vorgang doppelt anstossen.
		if (togglingPackIds.contains(entry.packId())) {
			return;
		}
		togglingPackIds.add(entry.packId());
		openMenuPackId = null;
		rebuild();

		Runnable work = () -> {
			Optional<String> toggleError = Optional.empty();
			try {
				model.setActive(entry.packId(), active);
			} catch (PackInstallException e) {
				Vitrine.LOGGER.error("Bibliothek: An/Aus fuer '{}' fehlgeschlagen: {}", entry.packId(), e.getMessage(), e);
				toggleError = Optional.of(resolveToggleErrorText(entry.name(), e));
			}
			Optional<String> finalError = toggleError;
			runOnClientThread(() -> {
				togglingPackIds.remove(entry.packId());
				libraryError = finalError;
				rebuild();
			});
		};
		runToggleWorkInBackground(work);
	}

	/**
	 * ROADMAP GATE119: wie {@code work} (die eigentliche E/A hinter {@link
	 * #onToggleActive}) vom aufrufenden Thread weggeschoben wird --
	 * {@link #backgroundWorkSchedulerForTest}, wenn ein Test ihn gesetzt hat
	 * (siehe {@code LibraryScreenToggleActiveAsyncTest}), sonst exakt dasselbe
	 * Muster wie {@link #onUpdateAll()}/{@link #onUpdateOne(LibraryEntry)}: mit
	 * laufendem {@link #client} ein eigener Daemon-Thread, ohne Client
	 * (kopffreie Tests) synchron im aufrufenden Thread -- identisches Ergebnis,
	 * kein neuer Nebenlaeufigkeitsweg nur fuer diesen einen Aufrufer.
	 */
	private void runToggleWorkInBackground(Runnable work) {
		if (backgroundWorkSchedulerForTest != null) {
			backgroundWorkSchedulerForTest.accept(work);
			return;
		}
		if (this.client != null) {
			Thread worker = new Thread(work, "Vitrine-Toggle-Active");
			worker.setDaemon(true);
			worker.start();
		} else {
			work.run();
		}
	}

	/**
	 * Nur fuer Tests (ROADMAP GATE119): ersetzt {@link #runToggleWorkInBackground}
	 * -- siehe Feld-Doku von {@link #backgroundWorkSchedulerForTest}.
	 */
	void setBackgroundWorkSchedulerForTest(Consumer<Runnable> scheduler) {
		this.backgroundWorkSchedulerForTest = scheduler;
	}

	/**
	 * Baut den sichtbaren Satz aus {@link PackInstallException#displayKey()}
	 * (Grund) und dem Paketnamen (welche Zeile betroffen ist) -- nie
	 * {@code e.getMessage()}, das kann absolute Pfade/rohe Ursachen enthalten
	 * (GATE48). Ohne {@code displayKey} (aeltere Konstruktoren) faellt der
	 * Grund auf einen generischen, ebenso sicheren Schluessel zurueck.
	 */
	private static String resolveToggleErrorText(String packName, PackInstallException e) {
		String reasonKey = e.displayKey() != null ? e.displayKey() : "vitrine.detail.error.generic";
		Object[] reasonArgs = e.displayKey() != null ? e.displayArgs() : new Object[] { e.getClass().getSimpleName() };
		String reason = resolveDisplayText(reasonKey, reasonArgs);
		return resolveDisplayText("vitrine.library.toggle_error", packName, reason);
	}

	/**
	 * Wie {@code PackageDetailScreen#resolveDisplayText}: uebersetzt ueber
	 * Minecrafts {@link Text#translatable}, faellt aber auf {@link
	 * Translations#format} zurueck, solange Minecrafts Sprachdateien noch
	 * nicht geladen sind (kopffreie Unit-Tests, GATE66).
	 */
	private static String resolveDisplayText(String key, Object... args) {
		String fromLanguage = Text.translatable(key, args).getString();
		if (!fromLanguage.equals(key)) {
			return fromLanguage;
		}
		return Translations.format(Translations.FALLBACK_LOCALE, key, args);
	}

	/** Nur fuer Tests/Anbindung: der zuletzt sichtbar gemachte Aktiv-Schalter-Fehler, oder leer im Ruhezustand. */
	public Optional<String> libraryError() {
		return libraryError;
	}

	/**
	 * Nur fuer Tests/Anbindung (ROADMAP GATE112): ob der An/Aus-Schalter der
	 * Zeile {@code packId} beim letzten {@link #init()} bedienbar war --
	 * {@code false} waehrend "Alle aktualisieren" laeuft ({@link #updatingAll}).
	 */
	public boolean isToggleActiveFor(String packId) {
		ButtonWidget toggle = toggleButtonsByPackId.get(packId);
		return toggle != null && toggle.active;
	}

	/** Nur fuer Tests/Anbindung (ROADMAP GATE112): setzt den "Alle aktualisieren laeuft"-Zustand direkt. */
	void setUpdatingAllForTest(boolean updatingAll) {
		this.updatingAll = updatingAll;
	}

	/**
	 * Nur fuer Tests/Anbindung (ROADMAP GATE80): der beim letzten {@link #init()}
	 * geholte Update-Status je Bibliothekszeile ({@link LibraryModel#updateStatuses()}),
	 * nach {@link LibraryEntry#packId()} indiziert -- derselbe Stand, aus dem
	 * {@link #renderListRowText} die sichtbare Versions-Spanne je Zeile baut.
	 */
	public Map<String, UpdateStatus> updateStatusByPackId() {
		return updateStatusByPackId;
	}

	/**
	 * Nur fuer Tests/Anbindung (ROADMAP GATE81): die beim letzten {@link #init()}
	 * geholten Paket-IDs mit zuletzt gescheitertem Update ({@link
	 * LibraryModel#failedUpdatePackIds()}) -- derselbe Stand, aus dem
	 * {@link #renderListRowText} den sichtbaren Zeilen-Marker baut.
	 */
	public Set<String> failedUpdatePackIds() {
		return failedUpdatePackIds;
	}

	/** ROADMAP GATE49: zusaetzlicher vertikaler Platz fuer die Fehlerzeile, oder 0 im Ruhezustand. */
	private int libraryErrorHeight() {
		return libraryError.isPresent() ? lineHeight() + VitrineTheme.DETAIL_SECTION_GAP : 0;
	}

	private void onOverflowClicked(LibraryEntry entry) {
		openMenuPackId = entry.packId().equals(openMenuPackId) ? null : entry.packId();
		rebuild();
	}

	/**
	 * ROADMAP GATE20: Deinstallieren aus dem Ueberlaufmenue fragt zuerst nach.
	 * Vorher lief {@code model.uninstall(...)} direkt beim Klick -- ein
	 * Fehlgriff im Menue war nicht mehr zurueckzunehmen. Jetzt entsteht hier
	 * nur die offene Entscheidung ({@link UninstallConfirmModel}); ausgefuehrt
	 * wird sie erst, wenn {@link ConfirmUninstallDialog} bestaetigt wird --
	 * Abbrechen (Knopf oder ESC) laesst alles, wie es ist.
	 */
	private void onUninstall(LibraryEntry entry) {
		UninstallConfirmModel confirmation =
				new UninstallConfirmModel(entry.packId(), entry.name(), () -> performUninstall(entry));
		pendingUninstall = confirmation;
		openMenuPackId = null;
		if (this.client != null) {
			this.client.setScreen(new ConfirmUninstallDialog(this, confirmation));
			return;
		}
		rebuild();
	}

	/** Das tatsaechliche Deinstallieren -- laeuft nur nach dem Bestaetigen (ROADMAP GATE20). */
	private void performUninstall(LibraryEntry entry) {
		model.uninstall(entry.packId());
		Vitrine.LOGGER.info("Bibliothek: Deinstallieren von '{}' nach Rueckfrage bestaetigt und angestossen.",
				entry.packId());
		if (this.client != null) {
			rebuild();
		}
	}

	/**
	 * Nur fuer Tests/Anbindung: die zuletzt angestossene, noch offene oder
	 * bereits entschiedene Deinstallations-Rueckfrage (ROADMAP GATE20).
	 */
	public Optional<UninstallConfirmModel> pendingUninstall() {
		return Optional.ofNullable(pendingUninstall);
	}

	private void onMove(LibraryEntry entry, int delta) {
		activeOrderPackId = entry.packId();
		boolean changed = delta < 0 ? model.moveUp(entry.packId()) : model.moveDown(entry.packId());
		if (changed) {
			Vitrine.LOGGER.info("Bibliothek: '{}' {} verschoben.", entry.packId(), delta < 0 ? "nach oben" : "nach unten");
		}
		openMenuPackId = null;
		rebuild();
	}

	private void rebuild() {
		rebuildCountForTest++;
		this.clearChildren();
		this.init();
	}

	// --- Ziehen (Abschnitt "Reihenfolge") ------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		OrderRow hit = orderRowAt(mouseY);
		if (hit != null) {
			draggingPackId = hit.packId();
			activeOrderPackId = hit.packId();
			dragPointerY = (int) mouseY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingPackId != null) {
			dragPointerY = (int) mouseY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (draggingPackId != null) {
			int targetIndex = orderIndexForPointer(mouseY);
			String moved = draggingPackId;
			draggingPackId = null;
			if (targetIndex >= 0) {
				model.moveTo(moved, targetIndex);
				rebuild();
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private OrderRow orderRowAt(double screenY) {
		for (OrderRow row : orderRows) {
			int top = row.baseY() - scrollY;
			if (screenY >= top && screenY <= top + row.height()) {
				return row;
			}
		}
		return null;
	}

	/** 0-basierter Index im Abschnitt "Reihenfolge", auf den der Zeiger zeigt (-1, wenn ausserhalb). */
	private int orderIndexForPointer(double screenY) {
		if (orderRows.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < orderRows.size(); i++) {
			OrderRow row = orderRows.get(i);
			int mid = row.baseY() - scrollY + row.height() / 2;
			if (screenY < mid) {
				return i;
			}
		}
		return orderRows.size() - 1;
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

	private int lineHeight() {
		return VitrineTheme.TYPE_BODY + VitrineTheme.DETAIL_LINE_GAP;
	}

	// --- Tastatur ------------------------------------------------------

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
		if (alt && activeOrderPackId != null
				&& (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
			boolean changed = keyCode == GLFW.GLFW_KEY_UP
					? model.moveUp(activeOrderPackId)
					: model.moveDown(activeOrderPackId);
			if (changed) {
				rebuild();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (openMenuPackId != null) {
				openMenuPackId = null;
				rebuild();
				return true;
			}
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

	// --- Zeichnen ----------------------------------------------------

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

		if (entries.isEmpty()) {
			StoreStatePanel.render(context, this.textRenderer, emptyStateView, this.width / 2, y,
					this.width - VitrineTheme.SPACE_PAGE * 2);
			return;
		}

		int cursorY = y;

		// Updates-Kopfzeile (ehrlich: Zahl aus derselben Momentaufnahme, die auch
		// die Zeilen-Marker unten speist -- GATE80, kein zweiter, abweichender Zaehler).
		long updateCount = updateCount();
		if (updateCount > 0) {
			ScaledText.draw(context, this.textRenderer,
					Text.translatable("vitrine.library.updates_available", updateCount),
					x, cursorY, ActiveThemeColors.accent(), VitrineTheme.TYPE_SUBTITLE);
			cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		}

		cursorY = renderLibraryError(context, x, cursorY);

		// Liste
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.library.section.list"),
				x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		for (LibraryEntry entry : entries) {
			// Jede Zeile als Karte: klar getrennt, Knoepfe sitzen sichtbar darin.
			int rowW = this.width - x - VitrineTheme.SPACE_PAGE;
			// Microsoft-Store-Bibliothek (umgekocht, 2026-10-07): je Paket eine eigene, runde
			// Zeile; Spalten Name/Autor, Art/Zustand, Datum, rechts Aktion und Mehr.
			de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, x - ROW_PAD, cursorY - ROW_PAD / 2, rowW + ROW_PAD,
					VitrineTheme.LIBRARY_ROW_HEIGHT + ROW_PAD, 8, ROW_COLOR);
			renderListRowThumbnail(context, entry, x, cursorY);
			renderListRowText(context, entry, x + rowTextIndent(), cursorY);
			renderListRowColumns(context, entry, x - ROW_PAD, rowW + ROW_PAD, cursorY);
			cursorY += VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
			if (entry.packId().equals(openMenuPackId)) {
				cursorY += OVERFLOW_MENU_ENTRY_COUNT * (VitrineTheme.DETAIL_ACTION_BUTTON_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP);
			}
		}

		cursorY += VitrineTheme.LIBRARY_SECTION_GAP;

		// Reihenfolge (nur ab zwei Ressourcen-Paketen, wie in init)
		if (!orderRows.isEmpty()) {
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.library.order.title"),
				x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
		cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
		ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.library.order.hint"),
				x, cursorY, ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
		cursorY += VitrineTheme.TYPE_CAPTION + VitrineTheme.DETAIL_HEADING_GAP;
		for (OrderRow row : orderRows) {
			LibraryEntry entry = findEntry(row.packId());
			if (entry != null) {
				int drawY = row.packId().equals(draggingPackId) ? (dragPointerY + scrollY) : cursorY;
				int color = row.packId().equals(draggingPackId) ? ActiveThemeColors.accent() : ActiveThemeColors.text();
				ScaledText.draw(context, this.textRenderer,
						Text.literal((entry.position() + 1) + ". " + entry.name()),
						x, drawY, color, VitrineTheme.TYPE_BODY);
			}
			cursorY += VitrineTheme.LIBRARY_ROW_HEIGHT + VitrineTheme.LIBRARY_ROW_GAP;
		}
		cursorY += VitrineTheme.LIBRARY_SECTION_GAP;
		}

		// Konflikthinweis (nur wenn es Konflikte gibt)
		if (!conflicts.isEmpty()) {
			ScaledText.draw(context, this.textRenderer, Text.translatable("vitrine.library.conflict.heading"),
					x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_SUBTITLE);
			cursorY += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.DETAIL_HEADING_GAP;
			for (LibraryConflict conflict : conflicts) {
				ScaledText.draw(context, this.textRenderer,
						Text.translatable("vitrine.library.conflict", conflict.resourcePath(),
								conflict.winner().name()),
						x, cursorY, ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
				cursorY += lineHeight();
			}
		}
	}

	/**
	 * ROADMAP GATE49: zeigt {@link #libraryError} als Klartext in
	 * {@link ActiveThemeColors#danger()} -- oder ruehrt {@code cursorY} gar
	 * nicht an, wenn kein Fehler vorliegt (gleiches Muster wie
	 * {@code PackageDetailScreen#renderInstallError}, GATE34).
	 */
	private int renderLibraryError(DrawContext context, int x, int cursorY) {
		if (libraryError.isEmpty()) {
			return cursorY;
		}
		ScaledText.draw(context, this.textRenderer, Text.literal(libraryError.get()), x, cursorY,
				ActiveThemeColors.danger(), VitrineTheme.TYPE_BODY);
		return cursorY + libraryErrorHeight();
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": "Bild klein" je Zeile) --
	 * Kantenlaenge des quadratischen Vorschaubilds, wiederverwendet aus dem
	 * bestehenden {@link VitrineTheme#LIBRARY_ROW_HEIGHT}-Token statt eines
	 * neuen, nicht themebaren Masses (Charter-Regel 7).
	 */
	private static int rowThumbSize() {
		return VitrineTheme.LIBRARY_ROW_HEIGHT;
	}

	/** Horizontaler Platz, den das Vorschaubild plus Abstand vor dem Zeilentext einnimmt. */
	private static int rowTextIndent() {
		return rowThumbSize() + VitrineTheme.CONTROL_GAP;
	}

	/**
	 * ROADMAP GATE83: das Vorschaubild einer Bibliothekszeile -- aus
	 * {@code manifest.icon()} im installierten {@code .vpkg} ({@link
	 * LibraryEntry#iconEntryPath()}/{@link LibraryEntry#file()}), seitenrichtig
	 * eingepasst wie auf der Detailseite ({@code PackageDetailScreen#renderHeaderBlock}).
	 * Ohne Icon (oder ungueltiges Bild) ein Platzhalter mit dem Typ-Symbol --
	 * nie eine leere Flaeche ohne jedes Zeichen (Barrierefreiheit).
	 */
	private void renderListRowThumbnail(DrawContext context, LibraryEntry entry, int x, int y) {
		int size = rowThumbSize();
		Optional<PackageImageTextures.Texture> icon = entry.iconEntryPath()
				.flatMap(p -> PackageImageTextures.get(entry.file(), p));
		if (icon.isPresent()) {
			PackageImageTextures.drawFitted(context, icon.get(), x, y, size, size);
		} else {
			context.fill(x, y, x + size, y + size, ActiveThemeColors.surfaceSolid());
			int drawSize = VitrineTheme.TYPE_ICON_PLACEHOLDER_DRAW_SIZE;
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			// ROADMAP GATE115: ein kaputter/fehlender Eintrag hat kein lesbares
			// Manifest und damit keinen Typ -- dasselbe Warnzeichen wie an jeder
			// anderen Stelle, an der ein kaputtes Paket angezeigt wird (siehe
			// StateSymbol/TileStatus#RESTART_REQUIRED).
			String iconPath = entry.isMissing() ? "textures/gui/icons/ui_warning.png" : entry.type().iconPath();
			context.drawTexture(Identifier.of("vitrine", iconPath),
					x + (size - drawSize) / 2, y + (size - drawSize) / 2, drawSize, drawSize, 0.0F, 0.0F,
					VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
					VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
		}
		RoundedRect.maskCorners(context, x, y, size, size, VitrineTheme.RADIUS_CARD, ROW_COLOR);
	}

	private static final int ROW_COLOR = 0xFF2A2C33;

	/** Spalten wie im Microsoft Store: Art + Zustand, dann das Datum der letzten Aenderung. */
	private void renderListRowColumns(DrawContext context, LibraryEntry entry, int rowX, int rowW, int y) {
		int mid = y + (VitrineTheme.LIBRARY_ROW_HEIGHT - VitrineTheme.TYPE_BODY) / 2;
		int typeX = rowX + rowW * 32 / 100;
		// ROADMAP GATE115: ohne lesbares Manifest gibt es keine Kategorie --
		// derselbe Klartext-Marker wie in der Metazeile, statt entry.type() zu
		// erzwingen.
		Text typeText = entry.isMissing() ? Text.translatable("vitrine.library.broken_missing")
				: Text.translatable(entry.type().translationKey());
		ScaledText.draw(context, this.textRenderer, typeText, typeX,
				mid - VitrineTheme.TYPE_CAPTION / 2 - 2,
				entry.isMissing() ? ActiveThemeColors.danger() : ActiveThemeColors.text(),
				VitrineTheme.TYPE_CAPTION + 1);
		// Addon-Plattform: was ohne Neustart nicht geht, ist deutlich gekennzeichnet
		String restart = de.spassglas.vitrine.platform.PlatformHost.restartReason(entry.packId());
		ScaledText.draw(context, this.textRenderer,
				restart != null ? Text.translatable("vitrine.platform.restart_label")
						: Text.translatable(entry.active() ? "vitrine.library.state.on" : "vitrine.library.state.off"),
				typeX, mid + VitrineTheme.TYPE_BODY / 2 + 2,
				restart != null ? 0xFFFFC857
						: entry.active() ? ActiveThemeColors.accent() : ActiveThemeColors.disabledText(),
				VitrineTheme.TYPE_CAPTION);
		int dateX = rowX + rowW * 52 / 100;
		String date = "";
		try {
			var t = java.nio.file.Files.getLastModifiedTime(entry.file()).toInstant()
					.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
			date = Text.translatable("vitrine.library.changed",
					t.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))).getString();
		} catch (java.io.IOException | RuntimeException ignored) {
			// ohne Datei kein Datum
		}
		ScaledText.draw(context, this.textRenderer, Text.literal(date), dateX, mid + 1, ActiveThemeColors.text(),
				VitrineTheme.TYPE_CAPTION + 1);
	}

	private void renderListRowText(DrawContext context, LibraryEntry entry, int x, int y) {
		y += (VitrineTheme.LIBRARY_ROW_HEIGHT - VitrineTheme.TYPE_BODY - VitrineTheme.TYPE_CAPTION - 4) / 2;
		ScaledText.draw(context, this.textRenderer, Text.literal(entry.name()),
				x, y, ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
		// ROADMAP GATE80: ist fuer GENAU dieses Paket ein Update verfuegbar (echter
		// Katalog-Versionsabgleich, K5), haengt die Metazeile die beiden Versionen
		// an -- die Zeile nennt damit, welches Paket betroffen ist (sie IST die
		// Zeile des betroffenen Pakets), statt nur die Gesamtzahl oben zu erhoehen.
		UpdateStatus updateStatus = updateStatusByPackId.get(entry.packId());
		boolean hasUpdate = updateStatus != null && updateStatus.updateAvailable();
		// ROADMAP GATE81: ein zuletzt gescheitertes Update ist sichtbar direkt
		// auf dieser Zeile, kein Flyout-Zwang -- und geht farblich vor der
		// gewoehnlichen Update-Markierung (danger statt accent).
		boolean failedUpdate = failedUpdatePackIds.contains(entry.packId());
		// ROADMAP GATE115: ein kaputter/fehlender Eintrag hat kein Autor/Version
		// aus dem Manifest -- die Metazeile zeigt stattdessen ausschliesslich den
		// Klartext-Marker, danger-farbig wie ein gescheitertes Update.
		if (entry.isMissing()) {
			String shownMissing = de.spassglas.vitrine.ui.tile.TileLabel.truncateToWidth(
					resolveDisplayText("vitrine.library.broken_missing"), Math.max(40, this.width * 30 / 100 - rowTextIndent()),
					t -> ScaledText.width(this.textRenderer, t, VitrineTheme.TYPE_CAPTION));
			ScaledText.draw(context, this.textRenderer, Text.literal(shownMissing),
					x, y + VitrineTheme.TYPE_BODY + 4, ActiveThemeColors.danger(), VitrineTheme.TYPE_CAPTION);
			return;
		}
		// zweite Zeile: Autor und Version (wie "Microsoft Corporation"), Update-Hinweis farbig
		String meta = entry.author() + "  ·  v" + entry.version();
		if (failedUpdate) {
			meta += "  ·  " + resolveDisplayText("vitrine.library.update_failed");
		} else if (hasUpdate) {
			meta += "  ·  " + resolveDisplayText("vitrine.library.row.update_available",
					updateStatus.installedVersion(), updateStatus.latestVersion());
		}
		int metaColor = failedUpdate ? ActiveThemeColors.danger()
				: hasUpdate ? ActiveThemeColors.accent() : ActiveThemeColors.textMuted();
		int maxW = this.width * 30 / 100 - rowTextIndent();
		String shown = de.spassglas.vitrine.ui.tile.TileLabel.truncateToWidth(meta, Math.max(40, maxW),
				t -> ScaledText.width(this.textRenderer, t, VitrineTheme.TYPE_CAPTION));
		ScaledText.draw(context, this.textRenderer, Text.literal(shown),
				x, y + VitrineTheme.TYPE_BODY + 4, metaColor, VitrineTheme.TYPE_CAPTION);
	}

	/**
	 * Baut die Metazeile einer Bibliothekszeile: Version, Typ, Autor -- und, nur
	 * wenn {@code updateStatus} fuer GENAU dieses Paket ein Update meldet
	 * ({@link UpdateStatus#updateAvailable()}), die Versions-Spanne (ROADMAP
	 * GATE80). Reine Textfunktion (kein {@code DrawContext}), damit sie ohne
	 * laufenden Client testbar ist.
	 */
	static String buildMetaText(LibraryEntry entry, UpdateStatus updateStatus) {
		return buildMetaText(entry, updateStatus, false);
	}

	/**
	 * Wie {@link #buildMetaText(LibraryEntry, UpdateStatus)}, aber haengt bei
	 * {@code failedUpdate} zusaetzlich den Klartext-Marker
	 * {@code vitrine.library.update_failed} an (ROADMAP GATE81) -- sichtbar auf
	 * der eigenen Zeile, ohne dass ein Flyout geoeffnet werden muss.
	 */
	static String buildMetaText(LibraryEntry entry, UpdateStatus updateStatus, boolean failedUpdate) {
		String meta = "v" + entry.version() + "  ·  "
				+ resolveDisplayText(entry.type().translationKey())
				+ "  ·  " + entry.author();
		// ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Zeile zeigt die
		// Größe): dieselbe Formatierung wie auf der Detailseite (HumanReadableSize).
		Optional<String> size = HumanReadableSize.format(entry.sizeBytes());
		if (size.isPresent()) {
			meta += "  ·  " + size.get();
		}
		if (updateStatus != null && updateStatus.updateAvailable()) {
			meta += "  ·  " + resolveDisplayText("vitrine.library.row.update_available",
					updateStatus.installedVersion(), updateStatus.latestVersion());
		}
		if (failedUpdate) {
			meta += "  ·  " + resolveDisplayText("vitrine.library.update_failed");
		}
		return meta;
	}

	private LibraryEntry findEntry(String packId) {
		for (LibraryEntry entry : entries) {
			if (entry.packId().equals(packId)) {
				return entry;
			}
		}
		return null;
	}

	private void renderFocusRing(DrawContext context) {
		if (!(this.getFocused() instanceof ClickableWidget widget) || widget instanceof ButtonWidget) {
			return;
		}
		FocusRing.drawFor(context, widget);
	}
}
