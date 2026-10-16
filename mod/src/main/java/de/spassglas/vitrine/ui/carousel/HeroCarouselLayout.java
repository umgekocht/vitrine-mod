package de.spassglas.vitrine.ui.carousel;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-freie Geometrie des Hero-Karussells (ROADMAP S8, docs/UI-SPEC.md
 * "3. Start": "21:9 ... Punkte-Anzeige unten rechts, Pfeile links/rechts beim
 * Ueberfahren"). Rechnet aus der linken oberen Ecke und der Breite die
 * 21:9-Flaeche, die Punkt-Rechtecke unten rechts und die Pfeil-Trefferflaechen
 * links/rechts aus und beantwortet, was unter einem Zeigerpunkt liegt -- damit
 * Zeichnen <em>und</em> Klick-Treffer in {@code HeroCarouselWidget} ohne
 * laufenden Client pruefbar sind (Muster wie {@code SearchSuggestionsLayout}).
 *
 * <p>Alle Masse kommen als Konstruktor-Parameter herein (der Aufrufer reicht
 * Theme-Tokens durch) -- diese Klasse enthaelt nur reine Arithmetik, keine
 * nackte Gestaltungszahl.</p>
 */
public final class HeroCarouselLayout {

	/** Ein achsenparalleles Rechteck in Bildschirmkoordinaten. */
	public record Rect(int left, int top, int width, int height) {
		public int right() {
			return left + width;
		}

		public int bottom() {
			return top + height;
		}

		public boolean contains(double x, double y) {
			return x >= left && x < left + width && y >= top && y < top + height;
		}
	}

	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final int slideCount;
	private final int dotSize;
	private final int dotGap;
	private final int inset;
	private final int arrowWidth;

	/**
	 * @param x          linke Kante der Karussell-Flaeche
	 * @param y          obere Kante der Karussell-Flaeche
	 * @param width      Breite der Karussell-Flaeche (&gt; 0)
	 * @param aspectW    Zaehler des Bildverhaeltnisses (docs/UI-SPEC.md {@code hero.aspect} = 21)
	 * @param aspectH    Nenner des Bildverhaeltnisses (= 9)
	 * @param slideCount Anzahl kuratierter Eintraege (&ge; 1) -- so viele Punkte
	 * @param dotSize    Kantenlaenge eines Punkts ({@code carousel.dot.size})
	 * @param dotGap     Abstand zwischen zwei Punkten und zum Rand
	 * @param inset      Innenabstand der Punktreihe zur unteren/rechten Kante
	 * @param arrowWidth Breite der Pfeil-Trefferflaeche ({@code carousel.arrow.width})
	 */
	public HeroCarouselLayout(int x, int y, int width, int aspectW, int aspectH, int slideCount,
			int dotSize, int dotGap, int inset, int arrowWidth) {
		if (width <= 0) {
			throw new IllegalArgumentException("width muss > 0 sein: " + width);
		}
		if (aspectW <= 0 || aspectH <= 0) {
			throw new IllegalArgumentException("Bildverhaeltnis muss positiv sein: " + aspectW + ":" + aspectH);
		}
		if (slideCount < 1) {
			throw new IllegalArgumentException("slideCount muss mindestens 1 sein: " + slideCount);
		}
		if (dotSize <= 0 || arrowWidth <= 0) {
			throw new IllegalArgumentException("dotSize/arrowWidth muessen > 0 sein");
		}
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = width * aspectH / aspectW;
		this.slideCount = slideCount;
		this.dotSize = dotSize;
		this.dotGap = Math.max(0, dotGap);
		this.inset = Math.max(0, inset);
		this.arrowWidth = arrowWidth;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	/** Hoehe der 21:9-Flaeche, aus Breite und Bildverhaeltnis. */
	public int height() {
		return height;
	}

	/** Die gesamte Karussell-Flaeche -- Treffer hierauf bedeutet "Maus liegt auf dem Karussell" (Auto-Wechsel pausiert). */
	public Rect area() {
		return new Rect(x, y, width, height);
	}

	public boolean contains(double mx, double my) {
		return area().contains(mx, my);
	}

	/** Trefferflaeche des Zurueck-Pfeils: voller-hoher Streifen an der linken Kante. */
	public Rect leftArrow() {
		return new Rect(x, y, arrowWidth, height);
	}

	/** Trefferflaeche des Weiter-Pfeils: voller-hoher Streifen an der rechten Kante. */
	public Rect rightArrow() {
		return new Rect(x + width - arrowWidth, y, arrowWidth, height);
	}

	public boolean inLeftArrow(double mx, double my) {
		return leftArrow().contains(mx, my);
	}

	public boolean inRightArrow(double mx, double my) {
		return rightArrow().contains(mx, my);
	}

	/**
	 * Die Punkt-Rechtecke, waagerecht aufgereiht, unten rechts in der Flaeche.
	 * Index 0 ist der linkeste Punkt (= erster Eintrag), Index {@code slideCount-1}
	 * der rechteste.
	 */
	public List<Rect> dots() {
		List<Rect> dots = new ArrayList<>(slideCount);
		int rowWidth = slideCount * dotSize + (slideCount - 1) * dotGap;
		int startX = x + width - inset - rowWidth;
		int dotY = y + height - inset - dotSize;
		for (int i = 0; i < slideCount; i++) {
			dots.add(new Rect(startX + i * (dotSize + dotGap), dotY, dotSize, dotSize));
		}
		return dots;
	}

	/**
	 * Der Punkt-Index unter {@code (mx, my)}, oder {@code -1}, wenn dort kein
	 * Punkt liegt. Die Trefferflaeche ist um {@code dotGap/2} nach jeder Seite
	 * grosszuegiger als der gezeichnete Punkt, damit sie mit der Maus gut
	 * erreichbar ist (docs/UI-SPEC.md "Barrierefreiheit": Bedienelemente).
	 */
	public int dotIndexAt(double mx, double my) {
		List<Rect> dots = dots();
		int pad = dotGap / 2;
		for (int i = 0; i < dots.size(); i++) {
			Rect d = dots.get(i);
			if (mx >= d.left() - pad && mx < d.right() + pad
					&& my >= d.top() - pad && my < d.bottom() + pad) {
				return i;
			}
		}
		return -1;
	}
}
