package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.manifest.Manifest;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Deckt das Fertig-Kriterium von P5 ab: "Zugriff auf eine nicht angemeldete
 * API wirft und wird geloggt" (ROADMAP.md, Charter-Regel 4: "Faehigkeiten
 * sind eine Whitelist. Default ist Verbot."). Prueft {@link VpkgCapabilityGrant}
 * gegen die vier relevanten Faelle: angemeldeter Zugriff geht durch, nicht
 * angemeldeter Zugriff wirft, unbekannter Bezeichner im Manifest wird
 * abgelehnt, leere Faehigkeiten-Liste erlaubt gar nichts.
 */
public final class VpkgCapabilityGrantTest {

	// --- das woertliche Fertig-Kriterium -----------------------------------

	@Test
	public void accessToUngrantedCapabilityThrows() throws VpkgCapabilityException {
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(testManifest("beispiel.texturpaket", List.of("texturen")));

		try {
			grant.requireGranted(VpkgCapability.HUD);
			fail("requireGranted(HUD) haette werfen muessen -- HUD ist im Manifest nicht angemeldet");
		} catch (VpkgCapabilityException e) {
			assertEquals(e.type(), VpkgCapabilityViolationType.NOT_GRANTED);
			assertTrue(e.capabilityIds().contains("hud"));
			assertTrue(e.getMessage().contains("hud"));
		}
	}

	// --- Randfaelle ----------------------------------------------------------

	@Test
	public void grantedCapabilityPassesThrough() throws VpkgCapabilityException {
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(
				testManifest("beispiel.hudpaket", List.of("hud", "texturen")));

		assertTrue(grant.isGranted(VpkgCapability.HUD));
		assertTrue(grant.isGranted(VpkgCapability.TEXTUREN));
		assertFalse(grant.isGranted(VpkgCapability.SOUND));

		// wirft nicht -- das ist der eigentliche Test
		grant.requireGranted(VpkgCapability.HUD);
		grant.requireGranted(VpkgCapability.TEXTUREN);
	}

	@Test
	public void unknownCapabilityStringInManifestIsRejected() {
		try {
			VpkgCapabilityGrant.of(testManifest("beispiel.tippfehler", List.of("texturen", "texturenn")));
			fail("of() haette wegen des unbekannten Bezeichners 'texturenn' werfen muessen");
		} catch (VpkgCapabilityException e) {
			assertEquals(e.type(), VpkgCapabilityViolationType.UNKNOWN_CAPABILITY);
			assertEquals(e.capabilityIds(), List.of("texturenn"));
		}
	}

	@Test
	public void emptyCapabilityListGrantsNothing() throws VpkgCapabilityException {
		VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(testManifest("beispiel.leer", List.of()));

		assertTrue(grant.granted().isEmpty());
		for (VpkgCapability capability : VpkgCapability.values()) {
			assertFalse(grant.isGranted(capability), capability + " haette bei leerer Liste nicht gewaehrt sein duerfen");
			try {
				grant.requireGranted(capability);
				fail("requireGranted(" + capability + ") haette bei leerer Liste werfen muessen");
			} catch (VpkgCapabilityException expected) {
				assertEquals(expected.type(), VpkgCapabilityViolationType.NOT_GRANTED);
			}
		}
	}

	@Test
	public void allKnownCapabilityIdsResolve() {
		for (VpkgCapability capability : VpkgCapability.values()) {
			assertTrue(VpkgCapability.byId(capability.id()).isPresent(),
					"VpkgCapability.byId(" + capability.id() + ") haette " + capability + " liefern muessen");
			assertFalse(capability.describe().isBlank(), capability + " braucht einen nicht-leeren Klartexttext");
		}
		assertFalse(VpkgCapability.byId("does-not-exist").isPresent());
		assertFalse(VpkgCapability.byId(null).isPresent());
	}

	// --- Hilfsfunktionen ----------------------------------------------------

	private static Manifest testManifest(String id, List<String> capabilities) {
		return new Manifest(1, id, "Testpaket", "1.0.0", "texturen", "Vitrine-Testfixture",
				null, capabilities, null, List.of(), null, null);
	}
}
