package de.spassglas.vitrine.ui.tile;

import de.spassglas.vitrine.ui.theme.VitrineTheme;

/**
 * Horizontaler Scroll-Zustand einer Kachelreihe (docs/UI-SPEC.md "3. Start",
 * Punkt 3: "Horizontal scrollen mit Mausrad, Pfeiltasten und Ziehen; Chevron-
 * Knoepfe erscheinen ... an den Raendern."). Reine Logik ohne Minecraft-
 * Abhaengigkeit -- {@code TileRowWidget} haelt genau eine Instanz und ruft
 * {@link #scrollByWheel(double)}/{@link #scrollByStep(int)}/{@link #dragBy(double)}
 * je nach Eingabeart auf, dann {@link #offset()} fuer die tatsaechliche
 * Kachel-Positionierung sowie {@link #canScrollLeft()}/{@link #canScrollRight()}
 * fuer die Chevron-Sichtbarkeit.
 *
 * <p><b>Richtungs-Konventionen</b> (fuer Tests wichtig): ein positiver
 * {@link #offset()} bedeutet, die Reihe ist nach rechts gescrollt (spaetere
 * Kacheln sichtbar). Mausrad "nach oben" (positiver {@code wheelAmount}, GLFW-
 * Konvention) scrollt nach links (verringert den Offset) -- wie ein Blaettern
 * zurueck zum Anfang; Mausrad "nach unten" scrollt nach rechts. Ziehen folgt
 * der Fingerbewegung: eine positive {@code mouseDeltaX} (Ziehen nach rechts)
 * verringert den Offset (fruehere Kacheln erscheinen, wie beim Ziehen eines
 * Papierstreifens nach rechts).</p>
 */
public final class TileRowScrollState {

	private final int tileCount;
	private final int cardWidth;
	private final int gutter;

	private int viewportWidth;
	private int offset;

	public TileRowScrollState(int tileCount) {
		this(tileCount, VitrineTheme.CARD_WIDTH, VitrineTheme.SPACE_GUTTER);
	}

	public TileRowScrollState(int tileCount, int cardWidth, int gutter) {
		if (tileCount < 0) {
			throw new IllegalArgumentException("tileCount darf nicht negativ sein: " + tileCount);
		}
		if (cardWidth <= 0) {
			throw new IllegalArgumentException("cardWidth muss positiv sein: " + cardWidth);
		}
		if (gutter < 0) {
			throw new IllegalArgumentException("gutter darf nicht negativ sein: " + gutter);
		}
		this.tileCount = tileCount;
		this.cardWidth = cardWidth;
		this.gutter = gutter;
	}

	/** Gesamtbreite aller Kacheln inklusive der Zwischenraeume, ohne Betrachtungsfenster-Begrenzung. */
	public int contentWidth() {
		if (tileCount == 0) {
			return 0;
		}
		return tileCount * cardWidth + (tileCount - 1) * gutter;
	}

	/** Muss bei jeder Fenstergroessen-/Layout-Aenderung neu gesetzt werden, bevor gescrollt wird. */
	public void setViewportWidth(int viewportWidth) {
		this.viewportWidth = Math.max(0, viewportWidth);
		this.offset = clamp(this.offset);
	}

	public int viewportWidth() {
		return viewportWidth;
	}

	/** Groesster gueltiger Offset -- 0, wenn alle Kacheln bereits ins Betrachtungsfenster passen. */
	public int maxOffset() {
		return Math.max(0, contentWidth() - viewportWidth);
	}

	public int offset() {
		return offset;
	}

	/** Ob die Chevron-Schaltflaeche nach links etwas zu tun haette (docs/UI-SPEC.md "3. Start"). */
	public boolean canScrollLeft() {
		return offset > 0;
	}

	/** Ob die Chevron-Schaltflaeche nach rechts etwas zu tun haette. */
	public boolean canScrollRight() {
		return offset < maxOffset();
	}

	/** Schrittweite fuer Pfeiltasten und Chevron-Klick: eine Kachel plus deren Abstand zur naechsten. */
	public int stepSize() {
		return cardWidth + gutter;
	}

	/**
	 * Mausrad-Eingabe, GLFW-Vorzeichenkonvention (positiv = "nach oben"/vom
	 * Nutzer weg). Ein Notch scrollt um {@link #stepSize()} Pixel.
	 */
	public void scrollByWheel(double wheelAmount) {
		scrollBy((int) Math.round(-wheelAmount * stepSize()));
	}

	/** Pfeiltasten-Schritt: {@code direction < 0} scrollt links, {@code direction > 0} scrollt rechts. */
	public void scrollByStep(int direction) {
		scrollBy(Integer.signum(direction) * stepSize());
	}

	/**
	 * Seitensprung (Bild-auf/Bild-ab): {@code direction < 0} etwa eine
	 * Betrachtungsfenster-Breite nach links, {@code direction > 0} nach rechts.
	 * Der Sprung ueberlappt bewusst um {@link #stepSize()}, damit die am Rand
	 * halb sichtbare Kachel danach ganz sichtbar ist (wie "Bild ab" in einem
	 * Textfenster); er bewegt aber nie weniger als eine {@link #stepSize()} und
	 * bleibt an den Raendern stehen.
	 */
	public void scrollByPage(int direction) {
		int page = Math.max(stepSize(), viewportWidth - stepSize());
		scrollBy(Integer.signum(direction) * page);
	}

	/**
	 * Scrollt so wenig wie noetig, dass die Kachel mit dem angegebenen Index
	 * vollstaendig im Betrachtungsfenster liegt (ROADMAP S9: Pfeiltasten wandern
	 * kachelweise durch die Reihe, die fokussierte Kachel muss dabei sichtbar
	 * bleiben). Ausserhalb von {@code [0, tileCount)} passiert nichts.
	 */
	public void revealIndex(int index) {
		if (index < 0 || index >= tileCount) {
			return;
		}
		int left = index * stepSize();
		int right = left + cardWidth;
		if (left < offset) {
			offset = clamp(left);
		} else if (right > offset + viewportWidth) {
			offset = clamp(right - viewportWidth);
		}
	}

	/** Springt an den linken Anfang der Reihe (Pos1/Home). */
	public void scrollToStart() {
		offset = 0;
	}

	/** Springt ans rechte Ende der Reihe (Ende/End). */
	public void scrollToEnd() {
		offset = maxOffset();
	}

	/** Ziehen (Maus gedrueckt halten und bewegen): Inhalt folgt der Mausbewegung. */
	public void dragBy(double mouseDeltaX) {
		scrollBy((int) Math.round(-mouseDeltaX));
	}

	private void scrollBy(int delta) {
		offset = clamp(offset + delta);
	}

	private int clamp(int value) {
		return Math.max(0, Math.min(value, maxOffset()));
	}
}
