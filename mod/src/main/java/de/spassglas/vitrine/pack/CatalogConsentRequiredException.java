package de.spassglas.vitrine.pack;

import java.util.List;

/**
 * ROADMAP G3: wird von {@link CatalogPackInstaller#install} geworfen, wenn das
 * frisch heruntergeladene (und bereits pruefsummen-/signaturgeprueften)
 * Katalog-Paket Faehigkeiten anmeldet, denen im {@code state.json} noch nicht
 * zugestimmt wurde (Charter-Regel 4: "Default ist Verbot"). Anders als bei
 * einem lokalen Bibliotheks-Paket ({@link PackInstaller}) sind die
 * angemeldeten Faehigkeiten eines Katalog-Angebots erst nach dem Download
 * bekannt -- {@code packages/&lt;id&gt;.json} traegt sie nicht (siehe
 * {@code registry-package.schema.json}: kein {@code capabilities}-Feld).
 *
 * <p>Der Aufrufer (Store-Oberflaeche) faengt diese Ausnahme, zeigt den
 * Faehigkeiten-Dialog mit {@link #capabilityIds()} und ruft danach
 * {@link CatalogPackInstaller#confirmCapabilitiesAndInstall} erneut auf.</p>
 */
public final class CatalogConsentRequiredException extends PackInstallException {

	private final List<String> capabilityIds;

	public CatalogConsentRequiredException(String manifestId, List<String> capabilityIds) {
		super("Katalog-Paket '" + manifestId + "' meldet Faehigkeiten an, denen noch nicht zugestimmt wurde: "
				+ capabilityIds);
		this.capabilityIds = List.copyOf(capabilityIds);
	}

	/** Die angemeldeten, noch nicht zugestimmten Faehigkeits-Bezeichner, in Manifest-Reihenfolge. */
	public List<String> capabilityIds() {
		return capabilityIds;
	}
}
