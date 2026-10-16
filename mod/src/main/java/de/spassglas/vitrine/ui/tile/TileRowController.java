package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;

/**
 * Pro {@code TileRowWidget} langlebiges Bedien-Modell einer Kachelreihe
 * (docs/UI-SPEC.md "3. Start", Punkt 3: "Horizontal scrollen mit Mausrad,
 * Pfeiltasten und Ziehen; Chevron-Knoepfe erscheinen beim Ueberfahren an den
 * Raendern."). Verbindet den reinen {@link TileRowScrollState} mit dem
 * Ueberfahren- und Ziehen-Zustand, analog zu {@code NavRailController} in der
 * Nav-Rail.
 *
 * <p>Reine Logik, keine Minecraft-Klassen -- das Widget ruft je nach Eingabe
 * {@link #scrollByWheel(double)} (Mausrad), {@link #scrollByArrow(int)}
 * (Pfeiltasten), {@link #scrollByPage(int)} (Bild-auf/Bild-ab),
 * {@link #scrollToStart()}/{@link #scrollToEnd()} (Pos1/Ende),
 * {@link #beginDrag(double)}/{@link #dragTo(double)}/{@link #endDrag()} (Ziehen)
 * oder {@link #scrollByChevron(int)} (Chevron-Klick) auf und liest danach
 * {@link #offset()} fuer die Kachel-Positionierung sowie
 * {@link #showLeftChevron()}/{@link #showRightChevron()} fuer die
 * Chevron-Sichtbarkeit.</p>
 */
public final class TileRowController {

	private final TileRowScrollState scroll;

	private boolean hovered;
	private boolean dragging;
	private double lastDragX;
	private double dragDistance;

	public TileRowController(int tileCount) {
		this.scroll = new TileRowScrollState(tileCount);
	}

	public TileRowController(int tileCount, int cardWidth, int gutter) {
		this.scroll = new TileRowScrollState(tileCount, cardWidth, gutter);
	}

	/** Muss bei jeder Layout-/Fensteraenderung gesetzt werden, bevor gescrollt wird. */
	public void setViewportWidth(int viewportWidth) {
		scroll.setViewportWidth(viewportWidth);
	}

	public int offset() {
		return scroll.offset();
	}

	public int contentWidth() {
		return scroll.contentWidth();
	}

	public int maxOffset() {
		return scroll.maxOffset();
	}

	/** Ob die Maus ueber dem Kachel-Betrachtungsfenster liegt oder die Reihe den Tastaturfokus hat. */
	public void setHovered(boolean hovered) {
		this.hovered = hovered;
	}

	public boolean isHovered() {
		return hovered;
	}

	/** Mausrad, GLFW-Vorzeichen (positiv = vom Nutzer weg / nach oben -> blaettert zum Anfang). */
	public void scrollByWheel(double wheelAmount) {
		scroll.scrollByWheel(wheelAmount);
	}

	/** Pfeiltaste: {@code direction < 0} nach links, {@code direction > 0} nach rechts. */
	public void scrollByArrow(int direction) {
		scroll.scrollByStep(direction);
	}

	/** Bild-auf/Bild-ab: Seitensprung um etwa eine Betrachtungsfenster-Breite, an den Raendern geklemmt. */
	public void scrollByPage(int direction) {
		scroll.scrollByPage(direction);
	}

	/** Pos1: an den linken Anfang der Reihe springen. */
	public void scrollToStart() {
		scroll.scrollToStart();
	}

	/** Ende: ans rechte Ende der Reihe springen. */
	public void scrollToEnd() {
		scroll.scrollToEnd();
	}

	/** Klick auf einen Chevron-Knopf am Rand -- selbe Schrittweite wie die Pfeiltaste. */
	public void scrollByChevron(int direction) {
		scroll.scrollByStep(direction);
	}

	/** Haelt die Kachel mit dem angegebenen Index im Betrachtungsfenster (ROADMAP S9, Tastatur-Fokus). */
	public void revealTile(int index) {
		scroll.revealIndex(index);
	}

	public void beginDrag(double mouseX) {
		dragging = true;
		lastDragX = mouseX;
		dragDistance = 0;
	}

	public void dragTo(double mouseX) {
		if (!dragging) {
			return;
		}
		double delta = mouseX - lastDragX;
		dragDistance += Math.abs(delta);
		scroll.dragBy(delta);
		lastDragX = mouseX;
	}

	public void endDrag() {
		dragging = false;
	}

	public boolean isDragging() {
		return dragging;
	}

	/**
	 * Ob seit {@link #beginDrag(double)} weit genug bewegt wurde, dass die
	 * Geste als Scrollen und nicht als Kachel-Klick gilt (Schwelle:
	 * {@link VitrineTheme#SPACE_UNIT} px).
	 */
	public boolean draggedPastClickThreshold() {
		return dragDistance > VitrineTheme.SPACE_UNIT;
	}

	/** Chevron nach links: nur beim Ueberfahren und nur, wenn es links noch etwas zu sehen gibt. */
	public boolean showLeftChevron() {
		return hovered && scroll.canScrollLeft();
	}

	/** Chevron nach rechts: nur beim Ueberfahren und nur, wenn es rechts noch etwas zu sehen gibt. */
	public boolean showRightChevron() {
		return hovered && scroll.canScrollRight();
	}
}
