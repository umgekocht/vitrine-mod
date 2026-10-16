package de.spassglas.vitrine.i18n;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Deckt den zweiten Teil des Fertig-Kriteriums von F3 ab: "Test findet
 * keinen im UI hartkodierten Anzeigetext."
 *
 * <p>Es gibt heute (F3, vor M3) noch keine eigenen UI-Klassen im Mod --
 * {@link #realSourceTreeHasNoHardcodedUiText()} beweist deshalb zunaechst
 * nur, dass der aktuelle Quellbaum (Config-Schicht, Einstiegspunkt) sauber
 * ist. Damit das kein Placebo-Test ist, beweisen die beiden anderen Tests an
 * bewusst konstruierten Beispielen, dass {@link UiTextScanner} echte
 * Verstoesse erkennt und den korrekten Weg (Uebersetzungs-Keys) nicht
 * faelschlich meldet. Sobald M3 echten UI-Code hinzufuegt, greift derselbe
 * Scanner automatisch gegen den dann gewachsenen Baum.</p>
 */
public final class UiTextScanTest {

	private Path tempDir;

	@AfterMethod
	public void cleanupTempDir() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							throw new UncheckedIOExceptionForTest(e);
						}
					});
		}
	}

	private static final class UncheckedIOExceptionForTest extends RuntimeException {
		UncheckedIOExceptionForTest(IOException cause) {
			super(cause);
		}
	}

	@Test
	public void scannerDetectsHardcodedLiteralUiText() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-scan-fixture-bad-");
		Path badFile = tempDir.resolve("BadScreen.java");
		Files.writeString(badFile, """
				package example;

				import net.minecraft.text.Text;

				class BadScreen {
					void init() {
						Text label = Text.literal("Installieren");
					}
				}
				""", StandardCharsets.UTF_8);

		List<UiTextScanner.Violation> violations = UiTextScanner.scan(tempDir);

		assertEquals(violations.size(), 1,
				"Scanner muss genau den hartkodierten Text.literal(...)-Aufruf erkennen: " + violations);
		assertTrue(violations.get(0).snippet().contains("Installieren"));
	}

	@Test
	public void scannerIgnoresTranslatableKeysAndSpacerLiterals() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-scan-fixture-good-");
		Path goodFile = tempDir.resolve("GoodScreen.java");
		Files.writeString(goodFile, """
				package example;

				import net.minecraft.text.Text;

				class GoodScreen {
					void init() {
						Text label = Text.translatable("vitrine.store.title");
						Text spacer = Text.literal(" ");
						Text empty = Text.literal("");
					}
				}
				""", StandardCharsets.UTF_8);

		List<UiTextScanner.Violation> violations = UiTextScanner.scan(goodFile.getParent());

		assertTrue(violations.isEmpty(),
				"Text.translatable(...) sowie leere/reine Leerzeichen-Literale sind kein Verstoss: " + violations);
	}

	@Test
	public void realSourceTreeHasNoHardcodedUiText() throws IOException {
		Path javaRoot = resolveMainJavaRoot();

		List<UiTextScanner.Violation> violations = UiTextScanner.scan(javaRoot);

		assertTrue(violations.isEmpty(), "Hartkodierter UI-Text im echten Quellbaum gefunden: " + violations);
	}

	/**
	 * Der Gradle-{@code test}-Task laeuft mit dem Projektverzeichnis
	 * ({@code mod/}) als Arbeitsverzeichnis, ein direkter Aufruf aus dem
	 * Repo-Root wuerde stattdessen {@code mod/src/main/java} brauchen --
	 * beide Faelle relativ, ohne absoluten Pfad.
	 */
	private static Path resolveMainJavaRoot() {
		Path direct = Path.of("src", "main", "java");
		if (Files.exists(direct)) {
			return direct;
		}
		Path viaModuleRoot = Path.of("mod", "src", "main", "java");
		if (Files.exists(viaModuleRoot)) {
			return viaModuleRoot;
		}
		throw new IllegalStateException(
				"Konnte src/main/java nicht finden, Arbeitsverzeichnis: " + Path.of("").toAbsolutePath());
	}
}
