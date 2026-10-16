package de.spassglas.vitrine.ui.detail;

/**
 * Der Fortschritt eines echten Installations-/Entfernungsvorgangs fuer EIN
 * Paket, so wie ihn die Detailseite (ROADMAP S3) am Aktionsknopf spiegelt.
 *
 * <p>Charter-Regel 2 ("Nichts vorgaukeln"): dieser Zustand kommt aus einem
 * tatsaechlichen Vorgangsmodell, nicht aus einem Timer. In S3 gibt es noch
 * keinen Installer (das ist S4) -- deshalb ist der einzige real erreichbare
 * Wert hier {@link #IDLE}. {@link #WORKING} und {@link #FAILED} sind bereits
 * modelliert und getestet, damit S4 nur noch den echten Vorgang anschliessen
 * muss, ohne das Zustandsmodell zu aendern.</p>
 */
public enum PackageInstallPhase {

	/** Kein Vorgang laeuft -- der Aktionsknopf zeigt seinen Ruhezustand aus {@link PackageActionState}. */
	IDLE,

	/** Ein Vorgang laeuft gerade (Download/Anwenden) -- Knopf zeigt "arbeitet …", nicht klickbar. */
	WORKING,

	/** Der letzte Vorgang ist gescheitert -- Knopf zeigt "Fehler", bis der Nutzer es erneut versucht. */
	FAILED
}
