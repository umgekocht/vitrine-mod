package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.classb.JavaModStager;
import de.spassglas.vitrine.classb.JavaModStagingException;
import de.spassglas.vitrine.classb.StagedMod;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * ROADMAP J2: das sichtbare "Neustart noetig"-Band fuer {@code StoreScreen} und
 * {@code LibraryScreen} (Charter-Regel 2: Klasse B ist nie "hot"). Zeigt die
 * Anzahl ausstehender Jars, je Eintrag Anzeigename + Autor und einen Knopf
 * "Zuruecknehmen" ({@link JavaModStager#unstage}), oben rechts den Sammelknopf
 * "Jetzt neu starten" ({@link JavaModStager#restartNow}).
 *
 * <p>Reines Zeichnen/Verdrahten -- welche Jars ausstehen und was
 * "Zuruecknehmen"/"Neustart" tun, liegt im client-freien {@link JavaModStager}
 * bzw. {@link RestartRequiredBanner}. Nur Vanilla-{@link ButtonWidget},
 * Farben aus {@link ActiveThemeColors}, Masse aus {@link VitrineTheme}.</p>
 */
public final class RestartRequiredBannerWidget {

	private static int pad() {
		return VitrineTheme.SPACE_UNIT * 2;
	}

	private static int entryRowHeight() {
		return VitrineTheme.MENU_BUTTON_HEIGHT + VitrineTheme.SPACE_UNIT * 2;
	}

	private final JavaModStager stager;
	private final RestartRequiredBanner model;

	private int left;
	private int top;
	private int width;

	/**
	 * ROADMAP GATE50: Klartext-Fehler des zuletzt gescheiterten
	 * "Zuruecknehmen"-Versuchs, oder {@code null} im Ruhezustand. Vorher fing
	 * {@link #takeBack} jeden Fehlschlag nur mit {@code LOGGER.warn} ab und rief
	 * trotzdem {@code onChanged.run()} -- das Band zeichnete den Eintrag optisch
	 * unveraendert weiter, der Knopf wirkte wirkungslos ohne Grund.
	 */
	private String takeBackError;

	public RestartRequiredBannerWidget(JavaModStager stager) {
		this.stager = Objects.requireNonNull(stager, "stager");
		this.model = new RestartRequiredBanner(stager.stagedMods());
	}

	/** Ob das Band ueberhaupt erscheint (mindestens eine Jar wartet auf einen Neustart). */
	public boolean isVisible() {
		return model.isVisible();
	}

	/** Vom Band belegte Hoehe -- der aufrufende Screen schiebt seinen Inhalt entsprechend nach unten. */
	public int reservedHeight() {
		if (!model.isVisible()) {
			return 0;
		}
		int height = pad()
				+ VitrineTheme.TYPE_SUBTITLE + VitrineTheme.SPACE_UNIT
				+ VitrineTheme.TYPE_BODY + VitrineTheme.SPACE_GUTTER
				+ model.pendingCount() * entryRowHeight()
				+ pad();
		// ROADMAP GATE50: eine zusaetzliche Zeile, solange ein "Zuruecknehmen"
		// gescheitert ist -- siehe #takeBackError.
		if (takeBackError != null) {
			height += VitrineTheme.TYPE_BODY + VitrineTheme.SPACE_UNIT;
		}
		return height;
	}

	/**
	 * Legt die Knoepfe an: Sammelknopf "Jetzt neu starten" oben rechts, je
	 * Eintrag "Zuruecknehmen".
	 *
	 * @param sink      {@code screen::addDrawableChild}
	 * @param onChanged wird nach einer Ruecknahme gerufen (Screen neu aufbauen)
	 */
	public void build(int left, int top, int width, Consumer<ClickableWidget> sink, Runnable onChanged) {
		this.left = left;
		this.top = top;
		this.width = width;
		if (!model.isVisible()) {
			return;
		}
		Objects.requireNonNull(sink, "sink");
		Objects.requireNonNull(onChanged, "onChanged");

		int contentRight = left + width - pad();
		int restartX = contentRight - VitrineTheme.MENU_BUTTON_WIDTH;
		ButtonWidget restartAll = ButtonWidget
				.builder(Text.translatable(RestartRequiredBanner.RESTART_ALL_KEY), b -> stager.restartNow())
				.dimensions(restartX, top + pad(), VitrineTheme.MENU_BUTTON_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
				.build();
		sink.accept(restartAll);

		int rowY = top + pad() + VitrineTheme.TYPE_SUBTITLE + VitrineTheme.SPACE_UNIT
				+ VitrineTheme.TYPE_BODY + VitrineTheme.SPACE_GUTTER;
		for (StagedMod entry : model.entries()) {
			int takeBackX = contentRight - VitrineTheme.LIBRARY_TOGGLE_WIDTH;
			ButtonWidget takeBack = ButtonWidget
					.builder(Text.translatable(RestartRequiredBanner.TAKE_BACK_KEY), b -> takeBack(entry, onChanged))
					.dimensions(takeBackX, rowY, VitrineTheme.LIBRARY_TOGGLE_WIDTH, VitrineTheme.MENU_BUTTON_HEIGHT)
					.build();
			sink.accept(takeBack);
			rowY += entryRowHeight();
		}
	}

	/**
	 * ROADMAP GATE50: ein Fehlschlag haelt jetzt {@link #takeBackError}
	 * sichtbar, statt nur zu loggen. {@code onChanged.run()} (der volle
	 * Bildschirm-Neubau) laeuft bewusst <b>nur</b> beim Erfolg -- ein Neubau
	 * wuerde eine frische {@link RestartRequiredBannerWidget}-Instanz erzeugen
	 * (siehe {@code PackageDetailScreen#init}) und damit {@link
	 * #takeBackError} sofort wieder verwerfen, bevor es ueberhaupt ein Bild
	 * zeichnen konnte. {@link #render} zeigt den Satz unter den Eintraegen.
	 *
	 * <p>Paketsichtbar (kein {@code private}), damit {@code
	 * RestartRequiredBannerWidgetTest} den Fehlerpfad ohne Knopf-Klick treiben
	 * kann (gleiches Muster wie {@code PackageDetailScreen#showInstallError},
	 * GATE34).</p>
	 *
	 * <p>ROADMAP GATE71: zeigte vorher {@code e.getMessage()} direkt an --
	 * deutscher Klartext samt Dateipfad, ungeachtet Locale. {@link
	 * JavaModStagingException#displayKey()}/{@link
	 * JavaModStagingException#displayArgs()} liefern jetzt den Grund
	 * strukturiert (gleiches Muster wie {@code PackInstallException}); nur die
	 * uebersetzte Fassung des Grunds wandert als Platzhalter in {@link
	 * RestartRequiredBanner#TAKE_BACK_ERROR_KEY}, nie der rohe Ausnahmetext.</p>
	 */
	void takeBack(StagedMod entry, Runnable onChanged) {
		try {
			stager.unstage(entry.id());
			Vitrine.LOGGER.info("Klasse B: '{}' vor dem Neustart zurueckgenommen.", entry.id());
			takeBackError = null;
			onChanged.run();
		} catch (JavaModStagingException e) {
			Vitrine.LOGGER.warn("Klasse B: Ruecknahme von '{}' abgelehnt: {}", entry.id(), e.getMessage());
			String reasonKey = e.displayKey() != null ? e.displayKey() : "vitrine.classb.error.unstage_generic";
			Object[] reasonArgs = e.displayKey() != null ? e.displayArgs() : new Object[0];
			String reason = resolveDisplayText(reasonKey, reasonArgs);
			takeBackError = resolveDisplayText(RestartRequiredBanner.TAKE_BACK_ERROR_KEY, reason);
		}
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

	/** Nur fuer Tests/Anbindung: der zuletzt sichtbar gemachte "Zuruecknehmen"-Fehler, oder {@code null} im Ruhezustand. */
	public String takeBackError() {
		return takeBackError;
	}

	/** Zeichnet Bandflaeche, Titel, Anzahl-Text und je Eintrag Anzeigename + "von &lt;Autor&gt;". */
	public void render(DrawContext context, TextRenderer textRenderer) {
		if (!model.isVisible()) {
			return;
		}
		int right = left + width;
		int bottom = top + reservedHeight();
		context.fill(left, top, right, bottom, ActiveThemeColors.surfaceSolid());
		context.fill(left, bottom - 1, right, bottom, ActiveThemeColors.borderTranslucent());

		int textX = left + pad();
		int y = top + pad();
		ScaledText.draw(context, textRenderer, Text.translatable(RestartRequiredBanner.TITLE_KEY), textX, y,
				ActiveThemeColors.accent(), VitrineTheme.TYPE_SUBTITLE);
		y += VitrineTheme.TYPE_SUBTITLE + VitrineTheme.SPACE_UNIT;
		ScaledText.draw(context, textRenderer,
				Text.translatable(RestartRequiredBanner.MESSAGE_KEY, model.pendingCount()), textX, y,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
		y += VitrineTheme.TYPE_BODY + VitrineTheme.SPACE_GUTTER;

		for (StagedMod entry : model.entries()) {
			ScaledText.draw(context, textRenderer, Text.literal(entry.displayName()), textX, y,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			ScaledText.draw(context, textRenderer,
					Text.translatable(RestartRequiredBanner.AUTHOR_KEY, entry.author()),
					textX, y + VitrineTheme.TYPE_BODY + VitrineTheme.CARD_TEXT_LINE_GAP,
					ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
			y += entryRowHeight();
		}

		// ROADMAP GATE50: Klartext-Fehler des letzten "Zuruecknehmen"-Versuchs.
		if (takeBackError != null) {
			ScaledText.draw(context, textRenderer, Text.literal(takeBackError), textX, y,
					ActiveThemeColors.danger(), VitrineTheme.TYPE_BODY);
		}
	}
}
