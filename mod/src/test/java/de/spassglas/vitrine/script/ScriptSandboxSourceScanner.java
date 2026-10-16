package de.spassglas.vitrine.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Test-Werkzeug fuer ROADMAP X5, Punkte 1 und 2: durchsucht den
 * {@code de.spassglas.vitrine.script}-Quellbaum nach Textmustern, die auf
 * Datei-/Netzwerk-Zugriff oder Reflection hindeuten wuerden. Ergaenzt die
 * strukturelle Garantie (die geschlossene {@link ScriptOp}/{@link ScriptDataSource}-
 * Whitelist laesst so etwas gar nicht zu) um eine Regressionsbremse: sollte
 * spaeter jemand versehentlich einen echten IO-/Reflection-Aufruf in dieses
 * Package einbauen, faellt genau dieser Test.
 *
 * <p>Bewusst <em>nicht</em> pauschal jedes {@code java.nio.*}: der Parser
 * nutzt {@code java.nio.charset.StandardCharsets} rein zur Byte/String-
 * Umwandlung -- das beruehrt weder Datei noch Netzwerk. Verboten sind gezielt
 * {@code java.nio.file} (Dateisystem) und {@code java.nio.channels} (Datei-/
 * Netzwerk-Kanaele).</p>
 */
final class ScriptSandboxSourceScanner {

	private static final List<String> FORBIDDEN_PATTERNS = List.of(
			"java.io.",
			"java.nio.file",
			"java.nio.channels",
			"java.net.",
			"java.lang.reflect",
			"MethodHandle",
			"ClassLoader",
			"ProcessBuilder",
			"Class.forName",
			"Runtime.getRuntime",
			"new Thread(",
			"Unsafe");

	private ScriptSandboxSourceScanner() {
	}

	record Violation(Path file, int line, String pattern, String snippet) {
		@Override
		public String toString() {
			return file + ":" + line + ": verbotenes Muster '" + pattern + "' -- " + snippet;
		}
	}

	static List<Violation> scan(Path javaSourceRoot) throws IOException {
		if (!Files.exists(javaSourceRoot)) {
			throw new IOException("Quellordner nicht gefunden: " + javaSourceRoot.toAbsolutePath());
		}
		List<Violation> violations = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(javaSourceRoot)) {
			List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
			for (Path file : javaFiles) {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					for (String pattern : FORBIDDEN_PATTERNS) {
						if (line.contains(pattern)) {
							violations.add(new Violation(file, i + 1, pattern, line.trim()));
						}
					}
				}
			}
		}
		return violations;
	}
}
