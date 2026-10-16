package de.spassglas.vitrine.vpkg;

/**
 * Die beiden Gruende, aus denen {@link VpkgCapabilityGrant} eine
 * {@link VpkgCapabilityException} wirft (P5, Charter-Regel 4).
 */
public enum VpkgCapabilityViolationType {

	/**
	 * Das Manifest meldet einen Bezeichner unter {@code capabilities} an, der
	 * in {@link VpkgCapability} nicht existiert (Tippfehler oder erfundene
	 * Faehigkeit). Wird beim Aufbau von {@link VpkgCapabilityGrant} geprueft,
	 * bevor ueberhaupt eine Whitelist entsteht.
	 */
	UNKNOWN_CAPABILITY,

	/**
	 * Ein Zugriff wurde fuer eine Faehigkeit versucht, die das Paket nicht in
	 * seinem Manifest angemeldet hat (Default ist Verbot). Wird zur
	 * Zugriffszeit geprueft, jedes Mal wenn Code eine Faehigkeit tatsaechlich
	 * benutzen will.
	 */
	NOT_GRANTED
}
