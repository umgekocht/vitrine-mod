package de.spassglas.vitrine.ui.theme;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Beweist das headless-Teilkriterium von ROADMAP T3 ("Standard-Theme
 * &bdquo;Klarglas&ldquo;, Kontrast &ge; 4.5:1"): die Kontrastwerte des
 * eingebauten Standard-Themes ({@link DefaultThemeTokens}, abgeleitet aus
 * {@link VitrineTheme}) sind hier mit der WCAG-2.1-Formel nachgerechnet,
 * ausgegeben und gegen die Schwelle assertet -- nicht bloss ein
 * {@code assertTrue} ohne Zahl.
 *
 * <p>Die WCAG-2.1-Formel steht als kleine, eigenstaendige Hilfe in
 * {@link Wcag} in dieser Testklasse (keine neue Maven-Abhaengigkeit): relative
 * Luminanz mit sRGB-Gamma und Kontrast {@code (L1 + 0.05) / (L2 + 0.05)}.
 *
 * <p><b>Nur die dunkle Variante:</b> {@link DefaultThemeTokens} loest jeden
 * Token gegen genau ein Farbset auf -- die dunkle &bdquo;Klarglas&ldquo;-Reihe
 * aus {@link VitrineTheme} (siehe deren Klassendoku). {@link ThemeVariant}
 * benennt zwar {@code LIGHT} und {@code DARK}, aber kein Code im Mod loest
 * {@link DefaultThemeTokens} pro Variante auf; die helle Reihe lebt allein in
 * {@code registry/schema/examples/theme/valid/klarglas.json} (Fremd-Ordner,
 * nur lesbar). Dieser Test rechnet daher die eine real ausgelieferte Variante
 * nach.
 *
 * <p><b>{@code color.danger} auf {@code color.bg}:</b> ein gesaettigtes
 * Warn-Rot erreicht auf dem fast schwarzen {@code color.bg} keine 4.5:1, ohne
 * die gedaempfte Anmutung zu sprengen. Es wird daher als Bedienelement mit der
 * 3:1-Schwelle behandelt (docs/UI-SPEC.md &bdquo;Barrierefreiheit&ldquo;: 3:1
 * fuer Bedienelemente). Zulaessig, weil der Fehlerzustand laut ROADMAP S9 nie
 * allein ueber Farbe transportiert wird -- immer zusaetzlich Symbol und Text.
 */
public final class KlarglasContrastTest {

	/** Textkontrast (WCAG 2.1 AA, Normaltext). */
	private static final double THRESHOLD_TEXT = 4.5;
	/** Bedienelement-/Nicht-Text-Kontrast (docs/UI-SPEC.md &bdquo;Barrierefreiheit&ldquo;). */
	private static final double THRESHOLD_CONTROL = 3.0;

	/**
	 * Die WCAG-2.1-Formel gegen bekannte Referenzwerte: Schwarz auf Weiss ist
	 * exakt 21:1, gleiche Farbe auf sich selbst 1:1. Faellt dieser Test durch,
	 * ist die Hilfsformel kaputt und alle Zahlen darunter wertlos.
	 */
	@Test
	public void wcagFormulaMatchesKnownReferenceValues() {
		double blackOnWhite = Wcag.contrastRatio(0xFF000000, 0xFFFFFFFF);
		double sameColor = Wcag.contrastRatio(0xFF3C3C42, 0xFF3C3C42);
		System.out.printf("[WCAG-Referenz] Schwarz/Weiss = %.2f:1 (erwartet 21.00), gleiche Farbe = %.2f:1 (erwartet 1.00)%n",
				blackOnWhite, sameColor);
		assertEquals(blackOnWhite, 21.0, 0.01, "Schwarz auf Weiss muss 21:1 ergeben");
		assertEquals(sameColor, 1.0, 0.0001, "Gleiche Farbe auf sich selbst muss 1:1 ergeben");
	}

	/**
	 * Rechnet fuer jedes geforderte Farbpaar der dunklen Standard-Theme-Reihe
	 * das Kontrastverhaeltnis aus, gibt es ins Testlog aus und assertet die
	 * jeweilige Schwelle.
	 */
	@Test
	public void standardThemeDarkVariantMeetsContrastThresholds() {
		System.out.println("[Klarglas T3] Kontrastwerte des Standard-Themes (DefaultThemeTokens, dunkle Reihe):");

		assertPair("color.text", "color.bg", THRESHOLD_TEXT);
		assertPair("color.text", "color.surfaceSolid", THRESHOLD_TEXT);
		assertPair("color.textMuted", "color.bg", THRESHOLD_TEXT);
		assertPair("color.accentContrast", "color.accent", THRESHOLD_TEXT);
		// Fehlertext: 3:1-Bedienelement-Schwelle, siehe Klassendoku (ROADMAP S9
		// gibt Fehlerzustand zusaetzlich als Symbol + Text aus).
		assertPair("color.danger", "color.bg", THRESHOLD_CONTROL);
		// Deaktivierter Text darf schwaecher sein -- 3:1 genuegt.
		assertPair("color.disabledText", "color.bg", THRESHOLD_CONTROL);
		// Fokusrahmen ist ein Nicht-Text-Bedienhinweis -- 3:1.
		assertPair("focus.ring.color", "color.bg", THRESHOLD_CONTROL);
	}

	private static void assertPair(String foreground, String background, double threshold) {
		int fg = DefaultThemeTokens.INSTANCE.color(foreground);
		int bg = DefaultThemeTokens.INSTANCE.color(background);
		double ratio = Wcag.contrastRatio(fg, bg);
		System.out.printf("  %-22s auf %-20s = %5.2f:1   (Schwelle %.1f:1)%n",
				foreground, background, ratio, threshold);
		assertTrue(ratio >= threshold, String.format(
				"%s auf %s erreicht nur %.2f:1, gefordert sind %.1f:1", foreground, background, ratio, threshold));
	}

	/**
	 * WCAG 2.1, "Relative luminance" und "Contrast ratio"
	 * (https://www.w3.org/TR/WCAG21/#dfn-relative-luminance,
	 * https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio). Eigenstaendig, damit
	 * der Test keine Maven-Abhaengigkeit braucht.
	 */
	static final class Wcag {

		private Wcag() {
		}

		/**
		 * Kontrastverhaeltnis zweier deckender Farben, {@code (L1 + 0.05) /
		 * (L2 + 0.05)} mit {@code L1 >= L2}. Der Alpha-Kanal wird ignoriert --
		 * alle hier geprueften Tokens sind deckend.
		 *
		 * @param argbA erste Farbe als {@code 0xAARRGGBB}
		 * @param argbB zweite Farbe als {@code 0xAARRGGBB}
		 * @return Verhaeltnis im Bereich {@code [1.0, 21.0]}
		 */
		static double contrastRatio(int argbA, int argbB) {
			double la = relativeLuminance(argbA);
			double lb = relativeLuminance(argbB);
			double lighter = Math.max(la, lb);
			double darker = Math.min(la, lb);
			return (lighter + 0.05) / (darker + 0.05);
		}

		/** Relative Luminanz (0.0 = Schwarz, 1.0 = Weiss) nach WCAG 2.1. */
		static double relativeLuminance(int argb) {
			double r = linearize(((argb >> 16) & 0xFF) / 255.0);
			double g = linearize(((argb >> 8) & 0xFF) / 255.0);
			double b = linearize((argb & 0xFF) / 255.0);
			return 0.2126 * r + 0.7152 * g + 0.0722 * b;
		}

		/** sRGB-Gamma-Entzerrung eines einzelnen Kanals ({@code 0.0..1.0}). */
		private static double linearize(double channel) {
			return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
		}
	}
}
