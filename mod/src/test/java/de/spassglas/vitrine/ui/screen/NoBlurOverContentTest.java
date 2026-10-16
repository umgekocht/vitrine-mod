package de.spassglas.vitrine.ui.screen;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

/**
 * Im Spiel gefunden (2026-10-05): der Willkommensbildschirm war komplett
 * verschwommen. Vanilla legt in {@code Screen#render} ueber
 * {@code renderBackground} die Menue-Unschaerfe ueber alles, was bis dahin
 * gezeichnet wurde -- und jeder Vitrine-Screen zeichnet seinen Inhalt VOR
 * {@code super.render}. Dieser Test verlangt deshalb von jedem Screen im
 * UI-Paket ein eigenes, leeres {@code renderBackground}.
 */
public class NoBlurOverContentTest {

	@Test
	public void everyVitrineScreenOverridesRenderBackground() throws IOException {
		Path ui = Path.of("src", "main", "java", "de", "spassglas", "vitrine", "ui");
		List<String> missing = new ArrayList<>();
		int screens = 0;
		try (Stream<Path> files = Files.walk(ui)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				String src = Files.readString(file, StandardCharsets.UTF_8);
				if (!src.contains(" extends Screen ")) {
					continue;
				}
				screens++;
				if (!src.contains("public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {\n\t}")
						&& !src.contains("public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {\r\n\t}")) {
					missing.add(file.getFileName().toString());
				}
			}
		}
		assertTrue(screens >= 9, "Testaufbau: zu wenige Screens gefunden (" + screens + ")");
		assertTrue(missing.isEmpty(), "ohne leeres renderBackground (Inhalt waere verschwommen): " + missing);
	}
}
