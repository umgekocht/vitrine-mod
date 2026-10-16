package de.spassglas.vitrine.vpkg;

import java.util.Collections;
import java.util.List;

/**
 * Wird von {@link VpkgCapabilityGrant} geworfen (P5, Charter-Regel 4:
 * "Faehigkeiten sind eine Whitelist. Default ist Verbot."). Zwei Faelle,
 * unterschieden ueber {@link #type()}:
 *
 * <ul>
 *   <li>{@link VpkgCapabilityViolationType#UNKNOWN_CAPABILITY} -- das Manifest
 *       meldet einen Bezeichner an, den es gar nicht gibt.</li>
 *   <li>{@link VpkgCapabilityViolationType#NOT_GRANTED} -- ein Zugriff auf
 *       eine Faehigkeit wurde versucht, die das Paket nicht angemeldet hat.</li>
 * </ul>
 *
 * <p>Geprueft (checked), aus demselben Grund wie {@link VpkgSecurityException}
 * und {@link VpkgIntegrityException}: ein Aufrufer, der eine Faehigkeit
 * benutzen will, muss den Fall "nicht erlaubt" aktiv behandeln, statt dass ein
 * vergessener catch-Block den Zugriff versehentlich zulaesst.</p>
 */
public final class VpkgCapabilityException extends Exception {

	private final VpkgCapabilityViolationType type;
	private final List<String> capabilityIds;

	VpkgCapabilityException(VpkgCapabilityViolationType type, List<String> capabilityIds, String message) {
		super(message);
		this.type = type;
		this.capabilityIds = List.copyOf(capabilityIds);
	}

	/** Ob es sich um eine unbekannte Faehigkeit im Manifest oder einen abgelehnten Zugriff handelt. */
	public VpkgCapabilityViolationType type() {
		return type;
	}

	/**
	 * Die betroffenen Faehigkeits-Bezeichner: bei {@link VpkgCapabilityViolationType#UNKNOWN_CAPABILITY}
	 * alle unbekannten Bezeichner aus dem Manifest (nie leer), bei
	 * {@link VpkgCapabilityViolationType#NOT_GRANTED} genau der eine angefragte
	 * Bezeichner.
	 */
	public List<String> capabilityIds() {
		return Collections.unmodifiableList(capabilityIds);
	}
}
