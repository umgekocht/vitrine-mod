package de.spassglas.vitrine.script;

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
 * Beweist strukturell (nicht nur per Verhaltens-Test), dass das
 * Sandbox-Package selbst keinen Datei-/Netzwerk-/Reflection-Zugriff enthaelt
 * (ROADMAP X5, Punkte 1 und 2). {@link #realScriptPackageHasNoForbiddenAccess()}
 * ist der eigentliche Wächter; die beiden anderen Tests beweisen an
 * konstruierten Beispielen, dass {@link ScriptSandboxSourceScanner} echte
 * Verstoesse findet und harmlose Nutzung (Charset-Konstanten) nicht faelschlich meldet.
 */
public final class ScriptSandboxSourceScanTest {

	private Path tempDir;

	@AfterMethod
	public void cleanupTempDir() throws IOException {
		if (tempDir == null || !Files.exists(tempDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(tempDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
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
	public void scannerDetectsFileReflectionAndNetworkAccess() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-script-scan-fixture-bad-");
		Files.writeString(tempDir.resolve("EvilOp.java"), """
				package example;

				import java.io.FileInputStream;
				import java.net.Socket;

				class EvilOp {
					void breakOut() throws Exception {
						new FileInputStream("secret.txt");
						new Socket("example.invalid", 1234);
						Class.forName("java.lang.Runtime");
					}
				}
				""", StandardCharsets.UTF_8);

		List<ScriptSandboxSourceScanner.Violation> violations = ScriptSandboxSourceScanner.scan(tempDir);

		assertTrue(violations.size() >= 3, "Scanner haette IO-, Netz- und Reflection-Zugriff finden muessen: " + violations);
	}

	@Test
	public void scannerIgnoresHarmlessCharsetUsage() throws IOException {
		tempDir = Files.createTempDirectory("vitrine-script-scan-fixture-good-");
		Files.writeString(tempDir.resolve("HarmlessDecoder.java"), """
				package example;

				import java.nio.charset.StandardCharsets;

				class HarmlessDecoder {
					String decode(byte[] bytes) {
						return new String(bytes, StandardCharsets.UTF_8);
					}
				}
				""", StandardCharsets.UTF_8);

		List<ScriptSandboxSourceScanner.Violation> violations = ScriptSandboxSourceScanner.scan(tempDir);

		assertEquals(violations, List.of(), "Reine Charset-Konstanten sind kein IO-Zugriff: " + violations);
	}

	@Test
	public void realScriptPackageHasNoForbiddenAccess() throws IOException {
		Path javaRoot = resolveScriptPackageRoot();

		List<ScriptSandboxSourceScanner.Violation> violations = ScriptSandboxSourceScanner.scan(javaRoot);

		assertTrue(violations.isEmpty(),
				"Datei-/Netz-/Reflection-Zugriff im echten Sandbox-Quellbaum gefunden: " + violations);
	}

	/** Kein absoluter Pfad -- relativ zum Gradle- bzw. Repo-Arbeitsverzeichnis, siehe UiTextScanTest. */
	private static Path resolveScriptPackageRoot() {
		Path direct = Path.of("src", "main", "java", "de", "spassglas", "vitrine", "script");
		if (Files.exists(direct)) {
			return direct;
		}
		Path viaModuleRoot = Path.of("mod", "src", "main", "java", "de", "spassglas", "vitrine", "script");
		if (Files.exists(viaModuleRoot)) {
			return viaModuleRoot;
		}
		throw new IllegalStateException(
				"Konnte das script-Package nicht finden, Arbeitsverzeichnis: " + Path.of("").toAbsolutePath());
	}
}
