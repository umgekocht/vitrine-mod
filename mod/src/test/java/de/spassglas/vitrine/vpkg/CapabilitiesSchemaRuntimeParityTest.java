package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

/**
 * Das woertliche Fertig-Kriterium von ROADMAP G2 ("Charter-8-Bruch:
 * {@code capabilities} im Schema != Mod-Laufzeit"): dasselbe
 * {@code capabilities}-Manifest muss vom Schema-Validator (hier vertreten
 * durch {@link ManifestParser}, der gegen die im Jar eingebettete Kopie von
 * {@code registry/schema/vitrine.schema.json} validiert) und von der
 * Laufzeit-Whitelist ({@link VpkgCapabilityGrant} / {@link VpkgCapability})
 * <b>gleich</b> beurteilt werden.
 *
 * <p>Beide Wege kennen dieselben neun punktlosen Bezeichner
 * ({@code texturen, theme, hud, shader, sound, sprache, cosmetics, verhalten,
 * skript} -- die letzten beiden seit ROADMAP AD4 fuer Addons). Frueher
 * beschrieb das Schema {@code capabilities} als punktgetrennte Namespaces
 * (z. B. {@code hud.read}), die die Laufzeit hart abwies -- dieser Test haelt
 * beide Seiten dauerhaft deckungsgleich.</p>
 */
public final class CapabilitiesSchemaRuntimeParityTest {

	@Test
	public void sameCapabilityInputsGetSameVerdictFromSchemaAndRuntime() {
		assertBothAccept(List.of("hud"));
		assertBothAccept(List.of("theme", "hud"));
		assertBothAccept(List.of());
		assertBothAccept(List.of("verhalten"));
		assertBothAccept(List.of("skript"));
		assertBothAccept(List.of("verhalten", "skript"));

		assertBothReject(List.of("hud.read"));
		assertBothReject(List.of("unbekannt"));
		assertBothReject(List.of("texturen.override"));
		assertBothReject(List.of("HUD Read!"));
	}

	// --- Behauptungen ------------------------------------------------------

	private static void assertBothAccept(List<String> capabilities) {
		Manifest parsed = parseViaSchemaOrNull(capabilities);
		assertNotNull(parsed, "Schema-Validator haette " + capabilities + " akzeptieren muessen");
		assertEquals(parsed.capabilities(), capabilities,
				"Schema-Weg muss die Capability-Liste unveraendert durchreichen");

		try {
			VpkgCapabilityGrant grant = VpkgCapabilityGrant.of("beispiel.paritaet", capabilities);
			assertEquals(grant.granted().size(), capabilities.size(),
					"Laufzeit-Weg muss genau die angemeldeten Faehigkeiten gewaehren");
		} catch (VpkgCapabilityException e) {
			fail("Laufzeit-Weg haette " + capabilities + " akzeptieren muessen, warf aber: " + e.getMessage());
		}
	}

	private static void assertBothReject(List<String> capabilities) {
		Manifest parsed = parseViaSchemaOrNull(capabilities);
		if (parsed != null) {
			fail("Schema-Validator haette " + capabilities + " ablehnen muessen, ergab aber: " + parsed);
		}

		try {
			VpkgCapabilityGrant.of("beispiel.paritaet", capabilities);
			fail("Laufzeit-Weg haette " + capabilities + " ablehnen muessen (VpkgCapabilityGrant.of)");
		} catch (VpkgCapabilityException e) {
			assertEquals(e.type(), VpkgCapabilityViolationType.UNKNOWN_CAPABILITY,
					"Nicht-Whitelist-Bezeichner muss als UNKNOWN_CAPABILITY abgelehnt werden");
		}
	}

	// --- Hilfsfunktionen -------------------------------------------------

	private static Manifest parseViaSchemaOrNull(List<String> capabilities) {
		try {
			return ManifestParser.parse(manifestJson(capabilities));
		} catch (ManifestParseException e) {
			return null;
		}
	}

	private static String manifestJson(List<String> capabilities) {
		StringBuilder caps = new StringBuilder();
		for (int i = 0; i < capabilities.size(); i++) {
			if (i > 0) {
				caps.append(", ");
			}
			caps.append('"').append(capabilities.get(i)).append('"');
		}
		return "{\n"
				+ "  \"schemaVersion\": 1,\n"
				+ "  \"id\": \"beispiel.paritaet\",\n"
				+ "  \"name\": \"Paritaetstest\",\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"category\": \"hud\",\n"
				+ "  \"author\": \"Vitrine-Testfixture\",\n"
				+ "  \"license\": \"CC0-1.0\",\n"
				+ "  \"capabilities\": [" + caps + "]\n"
				+ "}";
	}
}
