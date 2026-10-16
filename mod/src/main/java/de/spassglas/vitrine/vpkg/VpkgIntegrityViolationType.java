package de.spassglas.vitrine.vpkg;

/**
 * Die einzelnen Verstoss-Arten, gegen die {@link VpkgIntegrityValidator} (P4)
 * prueft. Jeder Wert entspricht genau einem Grund, ein {@code .vpkg} als
 * nicht vertrauenswuerdig einzustufen -- damit ein Aufrufer (und ein Test)
 * eindeutig sagen kann, WAS genau fehlte oder nicht passte.
 */
public enum VpkgIntegrityViolationType {

	/** Die Datei liess sich nicht lesen (fehlt, kein Zugriff, I/O-Fehler). */
	FILE_UNREADABLE,

	/** Es wurde keine erwartete SHA-256-Pruefsumme angegeben. */
	CHECKSUM_MISSING,

	/** Die tatsaechliche SHA-256-Pruefsumme der Datei weicht von der erwarteten ab. */
	CHECKSUM_MISMATCH,

	/** Es wurde keine Ed25519-Signatur angegeben. */
	SIGNATURE_MISSING,

	/** Es wurde kein oeffentlicher Schluessel zur Signaturpruefung angegeben. */
	PUBLIC_KEY_MISSING,

	/** Die Ed25519-Signatur ist kryptographisch ungueltig oder liess sich nicht pruefen. */
	SIGNATURE_INVALID
}
