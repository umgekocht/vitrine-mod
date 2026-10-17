package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.FocusRing;
import de.spassglas.vitrine.ui.theme.ScaledText;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Eine horizontale Kachelreihe (ROADMAP S1; docs/UI-SPEC.md "3. Start" +
 * "4. Kachel"): Kopfbereich mit Ueberschrift und Unterzeile, darunter ein
 * waagerecht scrollbarer Streifen aus {@link TileData}-Kacheln mit Bild bzw.
 * Typ-Platzhalter, Name (einzeilig gekuerzt via {@link TileLabel}), Autor,
 * Typ-Badge und -- falls vorhanden -- Statuszeichen. Ueberfahren hebt die
 * Kachel leicht an ({@link TileElevation}) und hellt den Rahmen auf.
 *
 * <p>Alle sichtbaren Chrome-Texte laufen ueber Uebersetzungs-Keys; Name und
 * Autor sind paket-eigene Freitexte (siehe {@link TileData}) und daher als
 * {@code Text.literal(<variable>)} gesetzt -- kein hartkodiertes Literal.
 * Jede Zahl, jede Farbe stammt aus {@link VitrineTheme} oder ist daraus
 * abgeleitet.</p>
 *
 * <p>Ueberschrift und Unterzeile der Reihe werden <b>immer</b> als
 * Uebersetzungs-Key uebergeben (ROADMAP S2: die Startseite zeigt mehrere
 * Reihen aus verschiedenen Quellen, jede mit eigener Beschriftung).
 * ROADMAP GATE14: es gibt bewusst keinen Konstruktor mehr, der stillschweigend
 * auf die Demo-Beschriftung zurueckfaellt -- genau so trug frueher die echte
 * Katalogreihe die Aufschrift "Beispielkacheln/Platzhalter-Reihe". Wer eine
 * Reihe baut, muss sagen, was drin ist; echte und Demo-Inhalte sind dadurch in
 * der Oberflaeche klar unterscheidbar (Charter-Regel 2).</p>
 *
 * <p>Die reine Scroll-/Bedienlogik liegt in {@link TileRowController} und ist
 * dort ohne laufenden Client getestet; dieses Widget bindet nur Minecrafts
 * Eingabe-Callbacks und das Zeichnen daran.</p>
 */
public final class TileRowWidget extends ClickableWidget {

	private static final Identifier PLACEHOLDER_CARD =
			Identifier.of("vitrine", "textures/gui/placeholder_card.png");
	private static final Identifier CHEVRON_LEFT =
			Identifier.of("vitrine", "textures/gui/icons/ui_chevron_left.png");
	private static final Identifier CHEVRON_RIGHT =
			Identifier.of("vitrine", "textures/gui/icons/ui_chevron_right.png");

	/** Bildhoehe der Kachel bei {@link VitrineTheme#CARD_WIDTH} und 16:9 (docs/UI-SPEC.md "4. Kachel"). */
	private static int imageHeight() {
		return VitrineTheme.CARD_WIDTH * VitrineTheme.CARD_ASPECT_HEIGHT / VitrineTheme.CARD_ASPECT_WIDTH;
	}
	/** Gesamthoehe einer Kachel: Bild plus Textblock (Name/Autor/Badge). */
	private static int cardHeight() {
		return imageHeight() + VitrineTheme.CARD_TEXT_BLOCK_HEIGHT;
	}
	/** Streifenhoehe mit Kopfraum fuer die maximale Ueberfahren-Anhebung. */
	private static int stripHeight() {
		return cardHeight() + VitrineTheme.ELEVATION_HOVER;
	}

	private final List<TileData> tiles;
	private final TextRenderer textRenderer;
	private final TileRowController controller;
	private final String titleKey;
	private final String subtitleKey;

	private String hoveredTileId;
	private long hoverStartMs;
	private String pressedTileId;
	private Consumer<TileData> onTileActivated;
	/** umgekocht (2026-10-07): "Holen"-Knopf direkt auf der Kachel, ohne Umweg ueber die Detailseite. */
	private Consumer<TileData> onTileDownload;
	private static final int GET_W = 54;
	private static final int GET_H = 18;
	/**
	 * ROADMAP GATE73 (docs/UI-SPEC.md "3. Start": "Alle anzeigen ›" rechts je
	 * Reihe) -- {@code null}, solange die Reihe kein echtes Ziel dafuer hat
	 * (Charter-Regel 2: kein Knopf ohne Wirkung). Breite/Hoehe der Trefferflaeche
	 * sind wie {@link #GET_W}/{@link #GET_H} bewusst feste Pixelwerte fuer diese
	 * eine kleine Kopfzeilen-Trefferflaeche, kein Textmass-abhaengiges Layout.
	 */
	private Runnable onSeeAll;
	private static final int SEE_ALL_W = 108;

	/** ROADMAP S9: Schalter "Animationen reduzieren" -- schaltet die Ueberfahren-Anhebung auf sofort. */
	private boolean reduceMotion;
	/**
	 * ROADMAP S9: die per Pfeiltaste innerhalb der Reihe fokussierte Kachel
	 * ({@code -1} = die Reihe hat den Tastaturfokus nicht). Traegt bei Fokus den
	 * sichtbaren Fokusrahmen und ist das Ziel von Enter/Leertaste.
	 */
	private int focusedTileIndex = -1;

	/**
	 * ROADMAP GATE74 (docs/UI-SPEC.md:112-114): Rechtsklick auf eine Kachel
	 * oeffnet ein kleines Menue (Installieren, Details, Autor, Melden). Die
	 * Kachel, fuer die das Menue gerade offen ist ({@code null} = geschlossen),
	 * plus die vier gezeichneten Eintraege und die geklemmte Fensterposition.
	 */
	private TileData contextMenuTile;
	private List<TileContextMenuEntry> contextMenuEntries = List.of();
	private int contextMenuX;
	private int contextMenuY;

	public TileRowWidget(int x, int y, int width, List<TileData> tiles, TextRenderer textRenderer,
			String titleKey, String subtitleKey) {
		super(x, y, width, preferredHeight(), Text.translatable(titleKey));
		this.tiles = List.copyOf(tiles);
		this.textRenderer = textRenderer;
		this.titleKey = titleKey;
		this.subtitleKey = subtitleKey;
		this.controller = new TileRowController(this.tiles.size());
		this.controller.setViewportWidth(width);
	}

	/**
	 * Callback, das eine Kachel-Aktivierung meldet: Klick auf eine Kachel (nicht
	 * Chevron, nicht Ziehen) oder Enter/Leertaste bei fokussierter Reihe
	 * (aktiviert dann die erste sichtbare Kachel). ROADMAP S3: "Öffnen: aus
	 * einer S1-Kachel (Klick/Enter) → Detailseite".
	 */
	public void setOnTileDownload(Consumer<TileData> onTileDownload) {
		this.onTileDownload = onTileDownload;
	}

	private boolean showsGet(TileData tile) {
		return onTileDownload != null && tile.status() == TileStatus.NONE;
	}

	/** Lage des "Holen"-Knopfs unten rechts im Textbereich der Kachel: x, y, Breite, Hoehe. */
	private int[] getRect(int cardX, int cardTop) {
		int bottom = cardTop + cardHeight();
		return new int[] {cardX + VitrineTheme.CARD_WIDTH - VitrineTheme.SPACE_UNIT * 2 - GET_W,
				bottom - VitrineTheme.SPACE_UNIT * 2 - GET_H, GET_W, GET_H};
	}

	private void renderGet(DrawContext context, TileData tile, int cardX, int cardTop, boolean hovered) {
		int[] r = getRect(cardX, cardTop);
		boolean over = mouseInside(r);
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, r[0], r[1], r[2], r[3], r[3] / 2,
				over ? 0xFF474B55 : 0xFF3A3D45);
		Text label = Text.translatable("vitrine.tile.get");
		int tw = ScaledText.width(textRenderer, label.getString(), VitrineTheme.TYPE_CAPTION);
		ScaledText.draw(context, textRenderer, label, r[0] + (r[2] - tw) / 2, r[1] + (r[3] - VitrineTheme.TYPE_CAPTION) / 2,
				ActiveThemeColors.text(), VitrineTheme.TYPE_CAPTION);
	}

	private double lastMouseX;
	private double lastMouseY;

	private boolean mouseInside(int[] r) {
		return lastMouseX >= r[0] && lastMouseX < r[0] + r[2] && lastMouseY >= r[1] && lastMouseY < r[1] + r[3];
	}

	/** Liegt der Punkt auf dem "Holen"-Knopf der Kachel {@code tile}? */
	private boolean onGet(TileData tile, double mx, double my) {
		int index = tiles.indexOf(tile);
		if (index < 0 || !showsGet(tile)) {
			return false;
		}
		int step = VitrineTheme.CARD_WIDTH + VitrineTheme.SPACE_GUTTER;
		int cardX = getX() - controller.offset() + index * step;
		int[] r = getRect(cardX, baseCardTop() - VitrineTheme.ELEVATION_REST);
		return mx >= r[0] - 2 && mx < r[0] + r[2] + 2 && my >= r[1] - 4 && my < r[1] + r[3] + 6;
	}

	public void setOnTileActivated(Consumer<TileData> onTileActivated) {
		this.onTileActivated = onTileActivated;
	}

	/**
	 * ROADMAP GATE73: Rueckruf fuer den Link "Alle anzeigen ›" am rechten Rand
	 * des Reihenkopfs. {@code null} (Default) zeigt den Link gar nicht --
	 * besser keine Zeile als eine, die auf nichts fuehrt.
	 */
	public void setOnSeeAll(Runnable onSeeAll) {
		this.onSeeAll = onSeeAll;
	}

	/** Trefferflaeche des "Alle anzeigen ›"-Links: x, y, Breite, Hoehe. */
	private int[] seeAllRect() {
		return new int[] {getRight() - SEE_ALL_W, getY(), SEE_ALL_W, VitrineTheme.ROW_HEADER_HEIGHT};
	}

	private boolean insideSeeAll(double mouseX, double mouseY) {
		if (onSeeAll == null) {
			return false;
		}
		int[] r = seeAllRect();
		return mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
	}

	/** ROADMAP S9: uebernimmt den Schalter "Animationen reduzieren" (docs/UI-SPEC.md "Bewegung"). */
	public void setReduceMotion(boolean reduceMotion) {
		this.reduceMotion = reduceMotion;
	}

	/** Gesamthoehe, die das Widget im Inhaltsbereich beansprucht (Kopf + Abstand + Streifen). */
	public static int preferredHeight() {
		return VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP + stripHeight();
	}

	private int stripContentTop() {
		return getY() + VitrineTheme.ROW_HEADER_HEIGHT + VitrineTheme.ROW_HEADER_GAP;
	}

	/** Obere Kante einer ruhenden Kachel -- die Anhebung zieht sie von hier aus nach oben. */
	private int baseCardTop() {
		return stripContentTop() + VitrineTheme.ELEVATION_HOVER;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		controller.setViewportWidth(getWidth());
		controller.setHovered(isHovered() || isFocused());

		renderHeader(context);

		context.enableScissor(getX(), stripContentTop(), getRight(), getBottom());
		int step = VitrineTheme.CARD_WIDTH + VitrineTheme.SPACE_GUTTER;
		int firstCardX = getX() - controller.offset();
		String nextHoveredId = null;
		int focusRingX = 0;
		int focusRingTop = 0;
		boolean focusRingVisible = false;
		for (int i = 0; i < tiles.size(); i++) {
			int cardX = firstCardX + i * step;
			if (cardX + VitrineTheme.CARD_WIDTH < getX() || cardX > getRight()) {
				continue;
			}
			TileData tile = tiles.get(i);
			boolean tileHovered = controller.isHovered() && !controller.isDragging()
					&& mouseX >= cardX && mouseX <= cardX + VitrineTheme.CARD_WIDTH
					&& mouseY >= baseCardTop() - VitrineTheme.ELEVATION_REST
					&& mouseY <= baseCardTop() - VitrineTheme.ELEVATION_REST + cardHeight();
			if (tileHovered) {
				nextHoveredId = tile.id();
			}
			int lift = liftFor(tile, tileHovered);
			int cardTop = baseCardTop() - lift;
			renderCard(context, tile, cardX, cardTop, tileHovered);
			if (isFocused() && i == focusedTileIndex) {
				focusRingX = cardX;
				focusRingTop = cardTop;
				focusRingVisible = true;
			}
		}
		if (nextHoveredId == null) {
			hoveredTileId = null;
		} else if (!nextHoveredId.equals(hoveredTileId)) {
			hoveredTileId = nextHoveredId;
			hoverStartMs = Util.getMeasuringTimeMs();
		}
		// ROADMAP GATE87: Hover ueber dem "Neustart noetig"-Statuszeichen zeigt
		// eine Klartext-Erklaerung statt nur das Icon stumm zu lassen.
		this.setTooltip(restartTooltipAt(mouseX, mouseY).map(Tooltip::of).orElse(null));
		context.disableScissor();

		// ROADMAP S9: sichtbarer Fokusrahmen um die per Pfeiltaste gewaehlte
		// Kachel (docs/UI-SPEC.md "Barrierefreiheit"). Nach disableScissor
		// gezeichnet, damit der Rahmen nicht am Streifenrand abgeschnitten wird.
		if (focusRingVisible) {
			FocusRing.draw(context, focusRingX, focusRingTop, VitrineTheme.CARD_WIDTH, cardHeight());
		}

		renderChevrons(context);
		renderContextMenu(context, mouseX, mouseY);
	}

	private int liftFor(TileData tile, boolean tileHovered) {
		if (tileHovered && tile.id().equals(hoveredTileId)) {
			return Math.round(TileElevation.liftPx(true, Util.getMeasuringTimeMs() - hoverStartMs, reduceMotion));
		}
		return tileHovered ? VitrineTheme.ELEVATION_HOVER : VitrineTheme.ELEVATION_REST;
	}

	private void renderHeader(DrawContext context) {
		ScaledText.draw(context, textRenderer, Text.translatable(titleKey),
				getX(), getY(), ActiveThemeColors.text(), VitrineTheme.TYPE_TITLE);
		ScaledText.draw(context, textRenderer, Text.translatable(subtitleKey),
				getX(), getY() + VitrineTheme.TYPE_TITLE + VitrineTheme.CARD_TEXT_LINE_GAP,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
		if (onSeeAll != null) {
			renderSeeAll(context);
		}
	}

	/** ROADMAP GATE73: Textlink "Alle anzeigen ›" rechtsbuendig im Reihenkopf. */
	private void renderSeeAll(DrawContext context) {
		int[] r = seeAllRect();
		boolean over = insideSeeAll(lastMouseX, lastMouseY);
		Text label = Text.translatable("vitrine.row.see_all");
		int tw = ScaledText.width(textRenderer, label.getString(), VitrineTheme.TYPE_BODY);
		int tx = r[0] + r[2] - tw;
		int ty = r[1] + (VitrineTheme.TYPE_TITLE - VitrineTheme.TYPE_BODY) / 2;
		ScaledText.draw(context, textRenderer, label, tx, ty,
				over ? ActiveThemeColors.accent() : ActiveThemeColors.textMuted(), VitrineTheme.TYPE_BODY);
	}

	private void renderCard(DrawContext context, TileData tile, int cardX, int cardTop, boolean tileHovered) {
		int cardRight = cardX + VitrineTheme.CARD_WIDTH;
		int cardBottom = cardTop + cardHeight();
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, cardX, cardTop, VitrineTheme.CARD_WIDTH, cardHeight(), VitrineTheme.RADIUS_CARD,
				ActiveThemeColors.surfaceSolid());

		int imageBottom = cardTop + imageHeight();
		renderImage(context, tile, cardX, cardTop);
		de.spassglas.vitrine.ui.theme.RoundedRect.maskTopCorners(context, cardX, cardTop, VitrineTheme.CARD_WIDTH, cardHeight(),
				VitrineTheme.RADIUS_CARD, ActiveThemeColors.bg());
		renderTypeBadge(context, tile, cardX, cardTop);
		renderStatus(context, tile, cardRight, imageBottom);
		renderText(context, tile, cardX, imageBottom);
		if (showsGet(tile)) {
			renderGet(context, tile, cardX, cardTop, tileHovered);
		}

		int borderColor = tileHovered ? ActiveThemeColors.textMuted() : ActiveThemeColors.borderTranslucent();
		de.spassglas.vitrine.ui.theme.RoundedRect.border(context, cardX, cardTop, VitrineTheme.CARD_WIDTH, cardHeight(), VitrineTheme.RADIUS_CARD,
				borderColor);
	}

	private void renderImage(DrawContext context, TileData tile, int imgX, int imgY) {
		// Vorschaubild aus dem Katalog (https-URL), sobald es geladen ist.
		if (tile.hasImage() && tile.imagePath().startsWith("https://")) {
			var remote = de.spassglas.vitrine.ui.image.CatalogImageCache.get(tile.imagePath());
			if (remote.isPresent()) {
				de.spassglas.vitrine.ui.theme.GuiBlend.on();
				de.spassglas.vitrine.ui.image.PackageImageTextures.drawCover(context, remote.get(), imgX, imgY,
						VitrineTheme.CARD_WIDTH, imageHeight());
				return;
			}
		}
		Identifier custom = tile.hasImage() && !tile.imagePath().startsWith("https://")
				? Identifier.tryParse(tile.imagePath()) : null;
		// GATE17: ein Paketbild kennt seine echten Masze -- eingepasst statt gestaucht.
		java.util.Optional<de.spassglas.vitrine.ui.image.PackageImageTextures.Texture> packageImage =
				de.spassglas.vitrine.ui.image.PackageImageTextures.byId(custom);
		if (packageImage.isPresent()) {
			de.spassglas.vitrine.ui.image.PackageImageTextures.drawFitted(context, packageImage.get(), imgX, imgY,
					VitrineTheme.CARD_WIDTH, imageHeight());
			return;
		}
		if (custom != null) {
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(custom, imgX, imgY, VitrineTheme.CARD_WIDTH, imageHeight(),
					0.0F, 0.0F, VitrineTheme.CARD_WIDTH, imageHeight(),
					VitrineTheme.CARD_WIDTH, imageHeight());
			return;
		}
		// Paket ohne eigenes Bild: Vorschau der Kategorie (Farbverlauf + Symbol,
		// scripts/make_category_placeholders.py) statt fuer alle dasselbe Logo.
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(categoryPlaceholder(tile.type()), imgX, imgY, VitrineTheme.CARD_WIDTH, imageHeight(),
				0.0F, 0.0F, 16, 9, 16, 9);
	}

	/** Kategorie-Vorschaubild ({@code textures/gui/placeholder_<kategorie>.png}, 16:9). */
	static Identifier categoryPlaceholder(de.spassglas.vitrine.ui.tile.TilePackageType type) {
		return Identifier.of("vitrine", "textures/gui/placeholder_" + type.id() + ".png");
	}

	private void renderTypeBadge(DrawContext context, TileData tile, int imgX, int imgY) {
		int size = VitrineTheme.TYPE_ICON_DRAW_SIZE;
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(Identifier.of("vitrine", tile.type().iconPath()),
				imgX + VitrineTheme.CARD_BADGE_INSET, imgY + VitrineTheme.CARD_BADGE_INSET,
				size, size, 0.0F, 0.0F,
				VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE,
				VitrineTheme.TYPE_ICON_SOURCE_SIZE, VitrineTheme.TYPE_ICON_SOURCE_SIZE);
	}

	private void renderStatus(DrawContext context, TileData tile, int cardRight, int imageBottom) {
		if (!tile.status().hasIndicator()) {
			return;
		}
		String icon = tile.status().iconPath().orElseThrow();
		int[] r = statusIconRect(cardRight, imageBottom);
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(Identifier.of("vitrine", icon), r[0], r[1], r[2], r[3], 0.0F, 0.0F,
				VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE,
				VitrineTheme.STATUS_ICON_SOURCE_SIZE, VitrineTheme.STATUS_ICON_SOURCE_SIZE);
	}

	/** Lage des Statuszeichen-Icons unten rechts im Bild einer Kachel: x, y, Breite, Hoehe. */
	private static int[] statusIconRect(int cardRight, int imageBottom) {
		int size = VitrineTheme.STATUS_ICON_DRAW_SIZE;
		return new int[] {cardRight - VitrineTheme.CARD_BADGE_INSET - size,
				imageBottom - VitrineTheme.CARD_BADGE_INSET - size, size, size};
	}

	/** Liegt {@code (x, y)} innerhalb des Rechtecks {@code rect} ({x, y, width, height})? */
	private static boolean pointInsideRect(double x, double y, int[] rect) {
		return x >= rect[0] && x < rect[0] + rect[2] && y >= rect[1] && y < rect[1] + rect[3];
	}

	private void renderText(DrawContext context, TileData tile, int cardX, int imageBottom) {
		int textX = cardX + VitrineTheme.SPACE_UNIT;
		int maxTextWidth = VitrineTheme.CARD_WIDTH - VitrineTheme.SPACE_UNIT * 2;
		int nameTop = imageBottom + VitrineTheme.SPACE_UNIT;

		String name = TileLabel.truncateToWidth(tile.name(), maxTextWidth,
				text -> ScaledText.width(textRenderer, text, VitrineTheme.TYPE_BODY));
		ScaledText.draw(context, textRenderer, Text.literal(name), textX, nameTop,
				ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);

		String author = TileLabel.truncateToWidth(tile.author(), maxTextWidth,
				text -> ScaledText.width(textRenderer, text, VitrineTheme.TYPE_CAPTION));
		ScaledText.draw(context, textRenderer, Text.literal(author), textX,
				nameTop + VitrineTheme.TYPE_BODY + VitrineTheme.CARD_TEXT_LINE_GAP,
				ActiveThemeColors.textMuted(), VitrineTheme.TYPE_CAPTION);
	}

	private void renderChevrons(DrawContext context) {
		int zoneTop = baseCardTop();
		int zoneBottom = zoneTop + imageHeight();
		int iconOffset = (VitrineTheme.CHEVRON_WIDTH - VitrineTheme.CHEVRON_ICON_SOURCE_SIZE) / 2;
		int iconY = zoneTop + (imageHeight() - VitrineTheme.CHEVRON_ICON_SOURCE_SIZE) / 2;

		if (controller.showLeftChevron()) {
			context.fill(getX(), zoneTop, getX() + VitrineTheme.CHEVRON_WIDTH, zoneBottom,
					ActiveThemeColors.surfaceTranslucent());
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(CHEVRON_LEFT, getX() + iconOffset, iconY,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, 0.0F, 0.0F,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE);
		}
		if (controller.showRightChevron()) {
			int zoneLeft = getRight() - VitrineTheme.CHEVRON_WIDTH;
			context.fill(zoneLeft, zoneTop, getRight(), zoneBottom, ActiveThemeColors.surfaceTranslucent());
			de.spassglas.vitrine.ui.theme.GuiBlend.on();
			context.drawTexture(CHEVRON_RIGHT, zoneLeft + iconOffset, iconY,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, 0.0F, 0.0F,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE,
					VitrineTheme.CHEVRON_ICON_SOURCE_SIZE, VitrineTheme.CHEVRON_ICON_SOURCE_SIZE);
		}
	}

	private boolean inLeftChevronZone(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX <= getX() + VitrineTheme.CHEVRON_WIDTH
				&& mouseY >= baseCardTop() && mouseY <= baseCardTop() + imageHeight();
	}

	private boolean inRightChevronZone(double mouseX, double mouseY) {
		return mouseX >= getRight() - VitrineTheme.CHEVRON_WIDTH && mouseX <= getRight()
				&& mouseY >= baseCardTop() && mouseY <= baseCardTop() + imageHeight();
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		pressedTileId = null;
		if (insideSeeAll(mouseX, mouseY)) {
			onSeeAll.run();
			return;
		}
		if (controller.showLeftChevron() && inLeftChevronZone(mouseX, mouseY)) {
			controller.scrollByChevron(-1);
			return;
		}
		if (controller.showRightChevron() && inRightChevronZone(mouseX, mouseY)) {
			controller.scrollByChevron(1);
			return;
		}
		TileData pressed = tileAt(mouseX, mouseY);
		pressedTileId = pressed == null ? null : pressed.id();
		controller.beginDrag(mouseX);
	}

	/** Die Kachel unter {@code (mouseX, mouseY)}, oder {@code null} ausserhalb jeder Kachel. */
	private TileData tileAt(double mouseX, double mouseY) {
		int top = baseCardTop() - VitrineTheme.ELEVATION_REST;
		if (mouseY < top || mouseY > top + cardHeight()) {
			return null;
		}
		int step = VitrineTheme.CARD_WIDTH + VitrineTheme.SPACE_GUTTER;
		int firstCardX = getX() - controller.offset();
		for (int i = 0; i < tiles.size(); i++) {
			int cardX = firstCardX + i * step;
			if (mouseX >= cardX && mouseX <= cardX + VitrineTheme.CARD_WIDTH
					&& mouseX >= getX() && mouseX <= getRight()) {
				return tiles.get(i);
			}
		}
		return null;
	}

	/**
	 * ROADMAP GATE87: der Tooltip-Text, der bei {@code (mouseX, mouseY)}
	 * erscheinen wuerde, oder leer, wenn der Punkt auf keinem "Neustart
	 * noetig"-Statuszeichen liegt. Reine Geometrie, kein {@link DrawContext}
	 * noetig -- {@link #renderWidget} ruft das genau hiermit auf, ein Test
	 * kann es ohne laufenden Client direkt pruefen (Muster wie {@link
	 * #onClick}/{@code tileAt}: dieselbe Ruhe-Lage ({@code
	 * baseCardTop() - ELEVATION_REST}) wie jeder andere Trefferflaechen-Test
	 * dieser Klasse, unabhaengig von der aktuellen Anheb-Animation).
	 */
	Optional<Text> restartTooltipAt(double mouseX, double mouseY) {
		int step = VitrineTheme.CARD_WIDTH + VitrineTheme.SPACE_GUTTER;
		int firstCardX = getX() - controller.offset();
		int cardTop = baseCardTop() - VitrineTheme.ELEVATION_REST;
		for (int i = 0; i < tiles.size(); i++) {
			TileData tile = tiles.get(i);
			if (tile.status() != TileStatus.RESTART_REQUIRED) {
				continue;
			}
			int cardX = firstCardX + i * step;
			int[] rect = statusIconRect(cardX + VitrineTheme.CARD_WIDTH, cardTop + imageHeight());
			if (pointInsideRect(mouseX, mouseY, rect)) {
				return Optional.of(Text.translatable("vitrine.tile.status.restart_required.tooltip"));
			}
		}
		return Optional.empty();
	}

	/** Die erste mindestens teilweise sichtbare Kachel (fuer die Tastatur-Aktivierung). */
	private TileData firstVisibleTile() {
		int index = firstVisibleTileIndex();
		return index < 0 ? null : tiles.get(index);
	}

	/** Index der ersten mindestens teilweise sichtbaren Kachel, oder {@code -1} bei leerer Reihe. */
	private int firstVisibleTileIndex() {
		if (tiles.isEmpty()) {
			return -1;
		}
		int step = VitrineTheme.CARD_WIDTH + VitrineTheme.SPACE_GUTTER;
		int firstCardX = getX() - controller.offset();
		for (int i = 0; i < tiles.size(); i++) {
			if (firstCardX + i * step + VitrineTheme.CARD_WIDTH > getX()) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * ROADMAP S9: bekommt die Reihe den Tastaturfokus, wandert der Fokus auf die
	 * erste sichtbare Kachel; verliert sie ihn, wird die Kachel-Auswahl
	 * zurueckgesetzt.
	 */
	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (focused) {
			if (focusedTileIndex < 0) {
				focusedTileIndex = firstVisibleTileIndex();
			}
		} else {
			focusedTileIndex = -1;
		}
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
		controller.dragTo(mouseX);
	}

	@Override
	public void onRelease(double mouseX, double mouseY) {
		boolean wasDrag = controller.draggedPastClickThreshold();
		controller.endDrag();
		if (wasDrag || pressedTileId == null || onTileActivated == null) {
			pressedTileId = null;
			return;
		}
		TileData released = tileAt(mouseX, mouseY);
		if (released != null && released.id().equals(pressedTileId)) {
			if (onGet(released, mouseX, mouseY)) {
				onTileDownload.accept(released);
				pressedTileId = null;
				return;
			}
			onTileActivated.accept(released);
		}
		pressedTileId = null;
	}

	/**
	 * ROADMAP GATE74: Rechtsklick auf eine Kachel oeffnet das Menue aus
	 * {@link TileContextMenu#entriesFor(TileData)}; waehrend es offen ist,
	 * schliesst (und bei Treffer auf einen Eintrag: fuehrt aus) jeder weitere
	 * Klick es -- wie jedes andere kleine Ausklappmenue im Store (vgl.
	 * {@code AccountMenu}). Linksklicks ausserhalb eines offenen Menues laufen
	 * unveraendert ueber {@link #onClick}/{@link #onDrag}/{@link #onRelease}.
	 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (contextMenuTile != null) {
			TileContextMenuAction action = contextMenuActionAt(mouseX, mouseY);
			TileData tile = contextMenuTile;
			closeContextMenu();
			if (action != null) {
				performContextMenuAction(action, tile);
			}
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			TileData target = tileAt(mouseX, mouseY);
			if (target != null) {
				openContextMenu(target, mouseX, mouseY);
				return true;
			}
			return false;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Breite/Hoehe des Rechtsklick-Menues -- wie der Menue-Knopf (docs/UI-SPEC.md nennt keine eigenen Masze dafuer). */
	private static int contextMenuWidth() {
		return VitrineTheme.MENU_BUTTON_WIDTH;
	}

	private static int contextMenuHeight(int entryCount) {
		return VitrineTheme.EDGE_PADDING * 2 + entryCount * VitrineTheme.MENU_BUTTON_HEIGHT;
	}

	/** Oeffnet das Menue bei {@code (anchorX, anchorY)}, geklemmt auf die eigenen Widget-Grenzen. */
	private void openContextMenu(TileData tile, double anchorX, double anchorY) {
		List<TileContextMenuEntry> entries = TileContextMenu.entriesFor(tile);
		int w = contextMenuWidth();
		int h = contextMenuHeight(entries.size());
		int x = (int) Math.round(Math.max(getX(), Math.min(anchorX, getRight() - w)));
		int y = (int) Math.round(Math.max(getY(), Math.min(anchorY, getBottom() - h)));
		this.contextMenuTile = tile;
		this.contextMenuEntries = entries;
		this.contextMenuX = x;
		this.contextMenuY = y;
	}

	private void closeContextMenu() {
		contextMenuTile = null;
		contextMenuEntries = List.of();
	}

	/** Der Eintrag unter {@code (mouseX, mouseY)} im offenen Menue, oder {@code null}. */
	private TileContextMenuAction contextMenuActionAt(double mouseX, double mouseY) {
		int w = contextMenuWidth();
		int itemY = contextMenuY + VitrineTheme.EDGE_PADDING;
		for (TileContextMenuEntry entry : contextMenuEntries) {
			if (mouseX >= contextMenuX && mouseX < contextMenuX + w
					&& mouseY >= itemY && mouseY < itemY + VitrineTheme.MENU_BUTTON_HEIGHT) {
				return entry.action();
			}
			itemY += VitrineTheme.MENU_BUTTON_HEIGHT;
		}
		return null;
	}

	/**
	 * Fuehrt einen Menuepunkt aus. {@code INSTALL} laeuft ueber denselben Pfad
	 * wie der "Holen"-Knopf, solange die Kachel noch nicht installiert ist --
	 * sonst (die Beschriftung ist dann "Aktualisieren"/"Deinstallieren") ueber
	 * die Detailseite, wo diese Aktionen tatsaechlich existieren.
	 * {@code DETAILS}/{@code AUTHOR} oeffnen beide die Detailseite -- die
	 * einzige bestehende Autorenansicht im Store (Kopf plus "Weitere Pakete
	 * dieses Autors"). {@code REPORT} gibt es als Mechanismus noch nicht --
	 * ein ehrlicher Hinweis statt einer vorgegaukelten Funktion.
	 */
	private void performContextMenuAction(TileContextMenuAction action, TileData tile) {
		switch (action) {
			case INSTALL -> {
				if (tile.status() == TileStatus.NONE && onTileDownload != null) {
					onTileDownload.accept(tile);
				} else if (onTileActivated != null) {
					onTileActivated.accept(tile);
				}
			}
			case DETAILS, AUTHOR -> {
				if (onTileActivated != null) {
					onTileActivated.accept(tile);
				}
			}
			case REPORT -> showReportUnavailableNotice();
		}
	}

	/** ROADMAP GATE74: kein Melde-Mechanismus vorhanden -- ehrlicher Hinweis statt stiller Wirkungslosigkeit. */
	private static void showReportUnavailableNotice() {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		if (mc == null) {
			return;
		}
		net.minecraft.client.toast.SystemToast.show(mc.getToastManager(),
				net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("vitrine.tile.report.unavailable.title"),
				Text.translatable("vitrine.tile.report.unavailable.description"));
	}

	private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
		if (contextMenuTile == null) {
			return;
		}
		int w = contextMenuWidth();
		int h = contextMenuHeight(contextMenuEntries.size());
		de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, contextMenuX, contextMenuY, w, h,
				VitrineTheme.RADIUS_CONTROL, ActiveThemeColors.surfaceSolid());
		de.spassglas.vitrine.ui.theme.RoundedRect.border(context, contextMenuX, contextMenuY, w, h,
				VitrineTheme.RADIUS_CONTROL, ActiveThemeColors.borderTranslucent());
		int itemY = contextMenuY + VitrineTheme.EDGE_PADDING;
		for (TileContextMenuEntry entry : contextMenuEntries) {
			boolean over = mouseX >= contextMenuX && mouseX < contextMenuX + w
					&& mouseY >= itemY && mouseY < itemY + VitrineTheme.MENU_BUTTON_HEIGHT;
			if (over) {
				de.spassglas.vitrine.ui.theme.RoundedRect.fill(context, contextMenuX + VitrineTheme.CARD_TEXT_LINE_GAP,
						itemY, w - VitrineTheme.CARD_TEXT_LINE_GAP * 2, VitrineTheme.MENU_BUTTON_HEIGHT,
						VitrineTheme.RADIUS_CONTROL, ActiveThemeColors.surfaceTranslucent());
			}
			Text label = entry.argument().isPresent()
					? Text.translatable(entry.translationKey(), entry.argument().get())
					: Text.translatable(entry.translationKey());
			ScaledText.draw(context, textRenderer, label, contextMenuX + VitrineTheme.SPACE_UNIT * 3,
					itemY + (VitrineTheme.MENU_BUTTON_HEIGHT - VitrineTheme.TYPE_BODY) / 2,
					ActiveThemeColors.text(), VitrineTheme.TYPE_BODY);
			itemY += VitrineTheme.MENU_BUTTON_HEIGHT;
		}
	}

	/** Nur fuer Tests: die Kachel, fuer die das Rechtsklick-Menue gerade offen ist, oder {@code null}. */
	TileData openContextMenuTile() {
		return contextMenuTile;
	}

	/** Nur fuer Tests: die Eintraege des gerade offenen Rechtsklick-Menues, oder leer, wenn keines offen ist. */
	List<TileContextMenuEntry> openContextMenuEntries() {
		return contextMenuEntries;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}
		// Senkrechtes Mausrad scrollt die Seite (StoreScreen), nicht die Reihe --
		// sonst bleibt man beim Scrollen an jeder Kachelreihe haengen. Seitlich
		// laeuft die Reihe ueber das horizontale Rad/Touchpad und die Pfeile.
		if (horizontalAmount == 0.0) {
			return false;
		}
		controller.scrollByWheel(horizontalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ROADMAP S9 / docs/UI-SPEC.md "Barrierefreiheit": "Pfeiltasten innerhalb
		// einer Reihe". Die Pfeiltaste bewegt den Kachel-Fokus um eine Kachel und
		// scrollt den Streifen so, dass die fokussierte Kachel sichtbar bleibt;
		// Tab (nicht hier behandelt) springt zur naechsten Reihe.
		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			return moveTileFocus(-1);
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			return moveTileFocus(1);
		}
		if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			controller.scrollByPage(-1);
			focusedTileIndex = firstVisibleTileIndex();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			controller.scrollByPage(1);
			focusedTileIndex = firstVisibleTileIndex();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_HOME) {
			controller.scrollToStart();
			focusedTileIndex = tiles.isEmpty() ? -1 : 0;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_END) {
			controller.scrollToEnd();
			focusedTileIndex = tiles.isEmpty() ? -1 : tiles.size() - 1;
			return true;
		}
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| keyCode == GLFW.GLFW_KEY_SPACE) && onTileActivated != null) {
			TileData target = focusedTileIndex >= 0 && focusedTileIndex < tiles.size()
					? tiles.get(focusedTileIndex)
					: firstVisibleTile();
			if (target != null) {
				onTileActivated.accept(target);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Bewegt den Kachel-Fokus um {@code direction} Kacheln, geklemmt, und haelt ihn sichtbar. */
	private boolean moveTileFocus(int direction) {
		if (tiles.isEmpty()) {
			return true;
		}
		int from = focusedTileIndex < 0 ? firstVisibleTileIndex() : focusedTileIndex;
		int next = Math.max(0, Math.min(tiles.size() - 1, from + direction));
		focusedTileIndex = next;
		controller.revealTile(next);
		return true;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}

	/** Nur fuer Tests/Anbindung: der aktuelle horizontale Scroll-Versatz in Pixeln. */
	public int scrollOffset() {
		return controller.offset();
	}
}
