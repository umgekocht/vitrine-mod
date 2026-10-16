package de.spassglas.vitrine.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Prueft den generischen Migrationsmechanismus unabhaengig davon, dass
 * {@code settings.json} bisher nur in Version 1 existiert -- das Konzept
 * (Kette von Schritten je Ausgangsversion) muss vor der ersten echten
 * Migration schon funktionieren, siehe F2-Fertig-Kriterium.
 */
public final class SchemaMigrationsTest {

	@Test
	public void appliesEachStepInOrderAndBumpsVersion() {
		UnaryOperator<JsonObject> v1ToV2 = obj -> {
			obj.addProperty("addedInV2", true);
			return obj;
		};
		UnaryOperator<JsonObject> v2ToV3 = obj -> {
			obj.addProperty("addedInV3", "hallo");
			return obj;
		};
		List<UnaryOperator<JsonObject>> steps = List.of(v1ToV2, v2ToV3);

		JsonObject root = JsonParser.parseString("{ \"schemaVersion\": 1 }").getAsJsonObject();

		JsonObject upgraded = SchemaMigrations.upgrade(root, 1, 3, steps);

		assertEquals(upgraded.get("schemaVersion").getAsInt(), 3);
		assertEquals(upgraded.get("addedInV2").getAsBoolean(), true);
		assertEquals(upgraded.get("addedInV3").getAsString(), "hallo");
	}

	@Test
	public void sameVersionIsANoOp() {
		JsonObject root = JsonParser.parseString("{ \"schemaVersion\": 1, \"x\": 5 }").getAsJsonObject();

		JsonObject result = SchemaMigrations.upgrade(root, 1, 1, List.of());

		assertEquals(result.get("schemaVersion").getAsInt(), 1);
		assertEquals(result.get("x").getAsInt(), 5);
	}

	@Test
	public void missingStepForAVersionFailsLoudly() {
		JsonObject root = JsonParser.parseString("{ \"schemaVersion\": 1 }").getAsJsonObject();

		// Kein Schritt registriert (leere Liste) -- muss werfen, nicht
		// stillschweigend eine unveraenderte oder falsch versionierte
		// Struktur zurueckgeben.
		assertThrows(IllegalStateException.class, () -> SchemaMigrations.upgrade(root, 1, 2, List.of()));
	}

	@Test
	public void fromVersionAfterToVersionIsRejected() {
		JsonObject root = JsonParser.parseString("{ \"schemaVersion\": 5 }").getAsJsonObject();

		assertThrows(IllegalArgumentException.class, () -> SchemaMigrations.upgrade(root, 5, 1, List.of()));
	}

	@Test
	public void currentRegisteredStepsMatchOnlyKnownSchemaVersion() {
		// Solange settings.json nur Version 1 kennt, ist die reale
		// Schritt-Liste leer -- sobald sie waechst, muss dieser Test
		// (bzw. sein Ersatz) mitwachsen.
		assertEquals(SchemaMigrations.STEPS.size(), VitrineSettings.CURRENT_SCHEMA_VERSION - 1);
	}
}
