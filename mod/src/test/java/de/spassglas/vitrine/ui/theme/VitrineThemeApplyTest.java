package de.spassglas.vitrine.ui.theme;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * GATE21: die Masze in {@link VitrineTheme} folgen dem aktiven Theme.
 *
 * <p>Vorher waren sie {@code static final} und damit vom Compiler fest in jede
 * Klasse eingesetzt -- ein Theme konnte nur Farben aendern. Diese Tests halten
 * fest, dass ein Groessen-Token wirklich im Feld ankommt, dass das Standard-
 * Theme sich davon nicht anstecken laesst und dass kein themebares Feld mehr
 * {@code final} ist.</p>
 */
public class VitrineThemeApplyTest {

	@AfterMethod
	public void restoreDefaults() {
		VitrineTheme.applyFrom(DefaultThemeTokens.INSTANCE::size);
	}

	@Test
	public void aThemeSizeTokenReachesTheField() {
		int before = VitrineTheme.HEADER_HEIGHT;
		VitrineTheme.applyFrom(token -> token.equals("header.height")
				? before + 17 : DefaultThemeTokens.INSTANCE.size(token));
		assertEquals(VitrineTheme.HEADER_HEIGHT, before + 17);
	}

	@Test
	public void theDefaultThemeKeepsItsOriginalValues() {
		int original = DefaultThemeTokens.INSTANCE.size("header.height");
		VitrineTheme.applyFrom(token -> 3);
		assertEquals(DefaultThemeTokens.INSTANCE.size("header.height"), original,
				"Standard-Theme darf die Werte eines geladenen Themes nicht uebernehmen");
		VitrineTheme.applyFrom(DefaultThemeTokens.INSTANCE::size);
		assertEquals(VitrineTheme.HEADER_HEIGHT, original);
	}

	@Test
	public void switchingTheManagerUpdatesTheFields() {
		ThemeManager manager = ThemeService.instance();
		int original = VitrineTheme.HEADER_HEIGHT;
		manager.switchTo(new ThemeTokens() {
			@Override
			public int size(String token) {
				return token.equals("header.height") ? original * 2 : DefaultThemeTokens.INSTANCE.size(token);
			}

			@Override
			public int color(String token) {
				return DefaultThemeTokens.INSTANCE.color(token);
			}
		});
		try {
			assertEquals(VitrineTheme.HEADER_HEIGHT, original * 2);
		} finally {
			manager.resetToDefault();
		}
		assertEquals(VitrineTheme.HEADER_HEIGHT, original);
	}

	@Test
	public void everyThemeableSizeFieldIsMutable() throws Exception {
		Set<String> stillFinal = new TreeSet<>();
		for (String token : DefaultThemeTokens.INSTANCE.sizeTokens()) {
			String fieldName = fieldFor(token);
			if (fieldName == null) {
				continue;
			}
			Field f = VitrineTheme.class.getField(fieldName);
			if (Modifier.isFinal(f.getModifiers())) {
				stillFinal.add(fieldName);
			}
		}
		assertTrue(stillFinal.isEmpty(), "noch vom Compiler eingesetzt: " + stillFinal);
	}

	/** Feldname nach der Namensregel von DefaultThemeTokens, sofern es das Feld gibt. */
	private static String fieldFor(String token) {
		String name = token.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replace('.', '_').toUpperCase();
		try {
			VitrineTheme.class.getField(name);
			return name;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	@Test
	public void focusRingColourComesFromTheActiveTheme() {
		assertFalse(ActiveThemeColors.focusRing() == 0, "focus.ring.color muss gesetzt sein");
		assertEquals(ActiveThemeColors.focusRing(), DefaultThemeTokens.INSTANCE.color("focus.ring.color"));
	}

	/**
	 * GATE93: die drei HUD-Rueckfallfarben laufen wie {@code focus.ring.color}
	 * ueber {@link ActiveThemeColors} -- ein Theme-Wechsel faerbt sie um, statt
	 * (wie vorher {@code VitrineTheme.HUD_TEXT_COLOR} &amp; Co.) fest im
	 * Compiler-Bytecode zu stecken.
	 */
	@Test
	public void hudFallbackColoursComeFromTheActiveTheme() {
		ThemeManager manager = ThemeService.instance();
		int customText = 0xFF112233;
		int customTrack = 0xFF445566;
		int customFill = 0xFF778899;
		manager.switchTo(new ThemeTokens() {
			@Override
			public int size(String token) {
				return DefaultThemeTokens.INSTANCE.size(token);
			}

			@Override
			public int color(String token) {
				return switch (token) {
					case "hud.text.color" -> customText;
					case "hud.bar.track.color" -> customTrack;
					case "hud.bar.fill.color" -> customFill;
					default -> DefaultThemeTokens.INSTANCE.color(token);
				};
			}
		});
		try {
			assertEquals(ActiveThemeColors.hudText(), customText);
			assertEquals(ActiveThemeColors.hudBarTrack(), customTrack);
			assertEquals(ActiveThemeColors.hudBarFill(), customFill);
		} finally {
			manager.resetToDefault();
		}
		assertEquals(ActiveThemeColors.hudText(), DefaultThemeTokens.INSTANCE.color("hud.text.color"));
		assertEquals(ActiveThemeColors.hudBarTrack(), DefaultThemeTokens.INSTANCE.color("hud.bar.track.color"));
		assertEquals(ActiveThemeColors.hudBarFill(), DefaultThemeTokens.INSTANCE.color("hud.bar.fill.color"));
	}
}
