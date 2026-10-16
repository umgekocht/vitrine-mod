package de.spassglas.vitrine.ui.dialog;

import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.vpkg.VpkgCapability;

import java.util.List;
import java.util.Objects;

/**
 * Client-freies Zustandsmodell des Fähigkeiten-Dialogs (ROADMAP S4,
 * docs/UI-SPEC.md "8. Fähigkeiten-Dialog").
 *
 * <p>Sagt für ein Paket, ob der Dialog <em>vor</em> der Installation gezeigt
 * werden muss ({@link #requiresConsent()}) -- nämlich dann, wenn den im
 * Manifest angemeldeten Fähigkeiten noch nicht (bzw. nicht in genau dieser
 * Menge) zugestimmt wurde. Ein Paket ohne angemeldete Fähigkeit braucht keinen
 * Dialog (Charter-Regel 4: Default ist Verbot, es gibt schlicht nichts zu
 * erlauben).</p>
 *
 * <p>Die Fähigkeiten werden als Klartext-Liste geführt ({@link
 * #requestedCapabilities()}); der Dialog übersetzt sie über
 * {@link VpkgCapability#translationKey()}.</p>
 */
public final class CapabilityConsentModel {

	private final String manifestId;
	private final List<VpkgCapability> requestedCapabilities;
	private final boolean requiresConsent;

	private CapabilityConsentModel(String manifestId, List<VpkgCapability> requestedCapabilities,
			boolean requiresConsent) {
		this.manifestId = manifestId;
		this.requestedCapabilities = requestedCapabilities;
		this.requiresConsent = requiresConsent;
	}

	/**
	 * @param manifestId            Manifest-ID des Pakets (Schlüssel der
	 *                              Zustimmung im {@code state.json})
	 * @param requestedCapabilities die im Manifest angemeldeten Fähigkeiten, in
	 *                              Manifest-Reihenfolge
	 * @param state                 aktueller Paket-Zustand (aus {@code
	 *                              PackStateStore.load}); {@code null} =
	 *                              "nichts zugestimmt"
	 */
	public static CapabilityConsentModel of(String manifestId, List<VpkgCapability> requestedCapabilities,
			PackState state) {
		Objects.requireNonNull(manifestId, "manifestId");
		Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
		List<VpkgCapability> caps = List.copyOf(requestedCapabilities);
		List<String> ids = caps.stream().map(VpkgCapability::id).sorted().toList();
		boolean alreadyConsented = state != null && state.hasCapabilityConsent(manifestId, ids);
		return new CapabilityConsentModel(manifestId, caps, !caps.isEmpty() && !alreadyConsented);
	}

	public String manifestId() {
		return manifestId;
	}

	/** Die im Manifest angemeldeten Fähigkeiten als Klartext-Liste (evtl. leer). */
	public List<VpkgCapability> requestedCapabilities() {
		return requestedCapabilities;
	}

	/** Fähigkeits-Bezeichner (sortiert) -- die Menge, der zugestimmt wird. */
	public List<String> capabilityIds() {
		return requestedCapabilities.stream().map(VpkgCapability::id).sorted().toList();
	}

	/** {@code true}, wenn der Dialog vor der Installation gezeigt werden muss ("muss zustimmen"). */
	public boolean requiresConsent() {
		return requiresConsent;
	}
}
