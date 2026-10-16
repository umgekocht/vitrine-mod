package de.spassglas.vitrine.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Test-Werkzeug fuer F3 ("Test findet keinen im UI hartkodierten
 * Anzeigetext"): durchsucht einen Java-Quellbaum nach Aufrufen, die ein
 * Minecraft-Text-Objekt aus einem woertlichen String-Literal bauen statt aus
 * einem Uebersetzungs-Key.
 *
 * <p><b>Abgrenzung (wichtig, um Fehlalarme zu vermeiden):</b> Es wird
 * <em>nicht</em> jedes String-Literal im Code geprueft -- das wuerde sofort
 * gegen Log-Nachrichten ({@code LOGGER.info("...")}), Property-/Feldnamen,
 * JSON-Keys, Ressourcen-Pfade usw. anschlagen. Geflaggt wird ausschliesslich
 * ein Aufruf der bekannten Minecraft-Text-Fabriken {@code Text.literal(...)}
 * bzw. {@code Component.literal(...)} (die Text-Klassen, mit denen im
 * Fabric-Client sichtbarer UI-Text gebaut wird), dessen Argument ein
 * woertliches String-Literal mit mindestens einem Buchstaben ist. Reine
 * Leerzeichen-/Formatierungs-Literale (z. B. {@code Text.literal(" ")} als
 * Abstandshalter) bleiben erlaubt. {@code Text.translatable("vitrine....")}
 * wird bewusst <em>nicht</em> geflaggt -- das ist genau der gewuenschte Weg
 * ueber Uebersetzungs-Keys.</p>
 */
final class UiTextScanner {

	private static final Pattern LITERAL_CALL = Pattern.compile(
			"\\b(?:Text|Component)\\.literal\\(\\s*\"([^\"]*)\"");
	private static final Pattern CONTAINS_LETTER = Pattern.compile("\\p{L}");

	private UiTextScanner() {
	}

	record Violation(Path file, int line, String snippet) {
		@Override
		public String toString() {
			return file + ":" + line + ": " + snippet;
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
					Matcher matcher = LITERAL_CALL.matcher(line);
					while (matcher.find()) {
						String literalText = matcher.group(1);
						if (CONTAINS_LETTER.matcher(literalText).find()) {
							violations.add(new Violation(file, i + 1, line.trim()));
						}
					}
				}
			}
		}
		return violations;
	}
}
