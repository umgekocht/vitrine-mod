package de.spassglas.vitrine.ui.dialog;

import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt den Teil des S4-Fertig-Kriteriums ab, der ohne laufenden Client
 * pruefbar ist: {@link CapabilityConsentModel} entscheidet korrekt, ob der
 * Faehigkeiten-Dialog <em>vor</em> der ersten Installation gezeigt werden muss
 * (Charter-Regel 4: "Default ist Verbot").
 */
public final class CapabilityConsentModelTest {

	private static final String MANIFEST_ID = "beispiel.faehigkeitspaket";

	// --- muss zustimmen: angemeldete Faehigkeiten, keine Zustimmung ---------

	@Test
	public void requiresConsentWhenCapabilitiesRequestedAndNothingConsentedYet() {
		CapabilityConsentModel model = CapabilityConsentModel.of(
				MANIFEST_ID, List.of(VpkgCapability.HUD, VpkgCapability.TEXTUREN), null);

		assertTrue(model.requiresConsent(),
				"ohne jede Zustimmung muss der Dialog vor der Installation erscheinen");
	}

	@Test
	public void requiresConsentWhenConsentedSetDiffersFromRequestedSet() {
		PackState state = new PackState();
		state.recordCapabilityConsent(MANIFEST_ID, List.of("hud"));

		CapabilityConsentModel model = CapabilityConsentModel.of(
				MANIFEST_ID, List.of(VpkgCapability.HUD, VpkgCapability.TEXTUREN), state);

		assertTrue(model.requiresConsent(),
				"weicht die angemeldete Menge von der zuletzt zugestimmten ab, muss erneut gefragt werden");
	}

	// --- muss nicht zustimmen ---------------------------------------------

	@Test
	public void noConsentRequiredWhenNoCapabilitiesRequested() {
		CapabilityConsentModel model = CapabilityConsentModel.of(MANIFEST_ID, List.of(), null);

		assertFalse(model.requiresConsent(),
				"ein Paket ohne angemeldete Faehigkeit hat nichts zu erlauben -- kein Dialog");
		assertTrue(model.capabilityIds().isEmpty());
	}

	@Test
	public void noConsentRequiredWhenStateAlreadyConsentedToExactlyThisSet() {
		PackState state = new PackState();
		state.recordCapabilityConsent(MANIFEST_ID, List.of("texturen", "hud"));

		CapabilityConsentModel model = CapabilityConsentModel.of(
				MANIFEST_ID, List.of(VpkgCapability.HUD, VpkgCapability.TEXTUREN), state);

		assertFalse(model.requiresConsent(),
				"wurde genau dieser Faehigkeitsmenge bereits zugestimmt, erscheint der Dialog nicht erneut");
	}

	// --- capabilityIds() sortiert ---------------------------------------

	@Test
	public void capabilityIdsAreSortedRegardlessOfManifestOrder() {
		CapabilityConsentModel model = CapabilityConsentModel.of(
				MANIFEST_ID,
				List.of(VpkgCapability.THEME, VpkgCapability.HUD, VpkgCapability.COSMETICS),
				null);

		assertEquals(model.capabilityIds(), List.of("cosmetics", "hud", "theme"),
				"die Bezeichner-Menge, der zugestimmt wird, muss sortiert geliefert werden");
	}
}
