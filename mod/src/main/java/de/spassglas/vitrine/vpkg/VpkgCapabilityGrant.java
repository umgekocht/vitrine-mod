package de.spassglas.vitrine.vpkg;

import de.spassglas.vitrine.manifest.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Die Whitelist tatsaechlich angemeldeter {@link VpkgCapability}-Werte fuer
 * EIN Paket (ROADMAP P5, Charter-Regel 4: "Faehigkeiten sind eine Whitelist.
 * Default ist Verbot. Kein Paket bekommt eine API, die es nicht im Manifest
 * angemeldet hat."). Baut aus {@link Manifest#capabilities()} eine gepruefte
 * Menge, gegen die spaetere Vitrine-APIs (M2 Hot-Apply, T5 HUD-Laufzeit, ...)
 * jeden einzelnen Zugriff pruefen muessen, BEVOR sie etwas tun.
 *
 * <p><b>Unbekannte Bezeichner im Manifest werden abgelehnt, nicht stillschweigend
 * ignoriert:</b> Ein Manifest, das z. B. {@code "texturenn"} (Tippfehler) oder
 * eine frei erfundene Faehigkeit anmeldet, koennte sonst dem Paketautor
 * vorgaukeln, eine Berechtigung erhalten zu haben, die tatsaechlich nie
 * geprueft wird -- der Autor bemerkt den Tippfehler nie, weil jeder Aufruf
 * einfach mit "nicht angemeldet" abgelehnt wird und aussieht wie ein normaler
 * Whitelist-Verstoss. {@link #of(Manifest)}/{@link #of(String, List)} lehnen
 * das gesamte Paket deshalb schon beim Aufbau der Whitelist ab (analog zu
 * {@link VpkgSecurityValidator}/{@link VpkgIntegrityValidator}: alle
 * unbekannten Bezeichner auf einmal gemeldet, nicht nacheinander per
 * Trial-and-Error), statt eine leere/luecke Berechtigung durchzuwinken.</p>
 */
public final class VpkgCapabilityGrant {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgCapability");

	private final String packageId;
	private final Set<VpkgCapability> granted;

	private VpkgCapabilityGrant(String packageId, Set<VpkgCapability> granted) {
		this.packageId = packageId;
		this.granted = granted;
	}

	/** Baut die Whitelist aus {@code manifest.capabilities()} (nie {@code null}, siehe {@link Manifest}). */
	public static VpkgCapabilityGrant of(Manifest manifest) throws VpkgCapabilityException {
		return of(manifest.id(), manifest.capabilities());
	}

	/**
	 * Baut die Whitelist aus rohen Manifest-Bezeichnern.
	 *
	 * @param packageId               Paket-Id, nur fuer Meldungen/Logging
	 * @param requestedCapabilityIds  Bezeichner aus {@code vitrine.json}, {@code capabilities}
	 * @throws VpkgCapabilityException mit {@link VpkgCapabilityViolationType#UNKNOWN_CAPABILITY},
	 *                                   wenn mindestens ein Bezeichner keiner {@link VpkgCapability}
	 *                                   entspricht
	 */
	public static VpkgCapabilityGrant of(String packageId, List<String> requestedCapabilityIds)
			throws VpkgCapabilityException {
		Set<VpkgCapability> resolved = EnumSet.noneOf(VpkgCapability.class);
		List<String> unknown = new ArrayList<>();
		for (String requested : requestedCapabilityIds) {
			VpkgCapability capability = VpkgCapability.byId(requested).orElse(null);
			if (capability == null) {
				unknown.add(requested);
			} else {
				resolved.add(capability);
			}
		}
		if (!unknown.isEmpty()) {
			String message = "Paket '" + packageId + "' meldet unbekannte Faehigkeit(en) im Manifest an: "
					+ String.join(", ", unknown) + " -- keine davon existiert in der Whitelist bekannter "
					+ "Faehigkeiten (VpkgCapability), Paket wird abgelehnt";
			LOGGER.warn("VpkgCapabilityGrant: {}", message);
			throw new VpkgCapabilityException(VpkgCapabilityViolationType.UNKNOWN_CAPABILITY, unknown, message);
		}
		return new VpkgCapabilityGrant(packageId, resolved);
	}

	/** Ob {@code capability} in diesem Manifest angemeldet ist. Wirft nie, prueft nur. */
	public boolean isGranted(VpkgCapability capability) {
		return granted.contains(capability);
	}

	/**
	 * Der EINE Aufruf, den jeder spaetere Zugriff auf eine Vitrine-API vor dem
	 * eigentlichen Zugriff machen muss (analog zu
	 * {@link VpkgSecurityValidator#requireSafe(VpkgArchive)}/
	 * {@link VpkgIntegrityValidator#requireValid}). Default ist Verbot: eine
	 * nicht angemeldete Faehigkeit wird abgelehnt und geloggt, unabhaengig
	 * davon, ob sie ueberhaupt existiert oder inhaltlich harmlos waere.
	 *
	 * @throws VpkgCapabilityException mit {@link VpkgCapabilityViolationType#NOT_GRANTED},
	 *                                   wenn {@code capability} nicht in diesem Manifest angemeldet ist
	 */
	public void requireGranted(VpkgCapability capability) throws VpkgCapabilityException {
		if (granted.contains(capability)) {
			return;
		}
		String message = "Paket '" + packageId + "' versucht auf die Faehigkeit '" + capability.id()
				+ "' zuzugreifen, hat sie aber nicht im Manifest angemeldet (Charter-Regel 4: Default ist Verbot)";
		LOGGER.warn("VpkgCapabilityGrant: {}", message);
		throw new VpkgCapabilityException(VpkgCapabilityViolationType.NOT_GRANTED, List.of(capability.id()), message);
	}

	/** Alle in diesem Manifest angemeldeten Faehigkeiten (leer, wenn keine angemeldet wurden). */
	public Set<VpkgCapability> granted() {
		return Set.copyOf(granted);
	}

	/** Id des Pakets, fuer das diese Whitelist gilt. */
	public String packageId() {
		return packageId;
	}
}
