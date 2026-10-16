package de.spassglas.vitrine.config;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Kette der Umwandlungsschritte fuer aeltere {@code settings.json}-Versionen.
 *
 * <p>{@link #STEPS} ist nach Ausgangsversion sortiert: Eintrag an Index
 * {@code i} wandelt ein Dokument von Schema-Version {@code i + 1} in
 * {@code i + 2} um (reine Datenumformung, kein Feld-Zugriff ohne Pruefung).
 * Aktuell gibt es nur Version 1 von {@code settings.json}, deshalb ist die
 * Liste leer -- sobald Version 2 noetig wird, kommt hier der erste Schritt
 * dazu, ohne dass {@link #upgrade} sich aendern muss.</p>
 */
final class SchemaMigrations {

	static final List<UnaryOperator<JsonObject>> STEPS = List.of();

	private SchemaMigrations() {
	}

	/**
	 * Wendet, beginnend bei {@code fromVersion}, alle registrierten Schritte
	 * bis {@code toVersion} nacheinander an und setzt nach jedem Schritt das
	 * Feld {@code schemaVersion} auf den neuen Stand.
	 *
	 * @throws IllegalArgumentException wenn fromVersion &gt; toVersion.
	 * @throws IllegalStateException    wenn fuer eine der durchlaufenen
	 *                                  Versionen kein Migrationsschritt
	 *                                  registriert ist (z. B. eine Version,
	 *                                  die es nie gegeben hat, oder eine
	 *                                  zukuenftige, fuer die dieser Code noch
	 *                                  zu alt ist).
	 */
	static JsonObject upgrade(JsonObject root, int fromVersion, int toVersion, List<UnaryOperator<JsonObject>> steps) {
		if (fromVersion > toVersion) {
			throw new IllegalArgumentException(
					"fromVersion (" + fromVersion + ") liegt nach toVersion (" + toVersion + ")");
		}
		JsonObject current = root;
		for (int version = fromVersion; version < toVersion; version++) {
			int stepIndex = version - 1;
			if (stepIndex < 0 || stepIndex >= steps.size()) {
				throw new IllegalStateException(
						"Keine Migration von Schema-Version " + version + " nach " + (version + 1) + " registriert.");
			}
			current = steps.get(stepIndex).apply(current);
			current.addProperty("schemaVersion", version + 1);
		}
		return current;
	}
}
