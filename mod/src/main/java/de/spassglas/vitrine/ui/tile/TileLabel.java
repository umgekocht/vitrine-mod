package de.spassglas.vitrine.ui.tile;

import java.util.Objects;

/**
 * Kuerzt den Kachel-Namen auf eine Zeile mit Auslassungspunkten
 * (docs/UI-SPEC.md "4. Kachel": "Name (einzeilig, mit Auslassungspunkten)").
 *
 * <p>Reine Funktion ohne Minecraft-Abhaengigkeit: die Breitenmessung wird als
 * {@link WidthFn} hereingereicht (im Client {@code ScaledText::width}, im Test
 * eine feste Ersatzmessung), damit die Kuerzungs-Logik ohne laufenden
 * {@code TextRenderer} beweisbar ist.</p>
 */
public final class TileLabel {

	/** Das angehaengte Auslassungszeichen (U+2026 HORIZONTAL ELLIPSIS). */
	public static final String ELLIPSIS = "…";

	/** Misst die Pixelbreite eines Texts in der Zielschriftgroesse der Kachel. */
	@FunctionalInterface
	public interface WidthFn {
		int widthOf(String text);
	}

	private TileLabel() {
	}

	/**
	 * Gibt {@code text} unveraendert zurueck, wenn er in {@code maxWidth} passt;
	 * sonst den laengsten Praefix, dessen Breite samt {@link #ELLIPSIS} noch
	 * hineinpasst. Passt nicht einmal das Auslassungszeichen (oder ist
	 * {@code maxWidth <= 0}), kommt ein leerer String zurueck.
	 */
	public static String truncateToWidth(String text, int maxWidth, WidthFn width) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(width, "width");
		if (maxWidth <= 0) {
			return "";
		}
		if (width.widthOf(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = width.widthOf(ELLIPSIS);
		if (ellipsisWidth > maxWidth) {
			return "";
		}
		// Groesster Praefix, dessen Breite + Auslassungszeichen noch passt.
		// Bedingung ist mit wachsender Laenge monoton -> Binaersuche.
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (width.widthOf(text.substring(0, mid)) + ellipsisWidth <= maxWidth) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		if (low == 0) {
			return ELLIPSIS;
		}
		String head = text.substring(0, low).stripTrailing();
		return head.isEmpty() ? ELLIPSIS : head + ELLIPSIS;
	}

	/** Ob {@code text} ohne Kuerzung in {@code maxWidth} passt. */
	public static boolean fits(String text, int maxWidth, WidthFn width) {
		return width.widthOf(Objects.requireNonNull(text, "text")) <= maxWidth;
	}
}
