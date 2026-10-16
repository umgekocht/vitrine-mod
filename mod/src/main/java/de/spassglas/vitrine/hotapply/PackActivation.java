package de.spassglas.vitrine.hotapply;

/**
 * Abstraktion ueber "aktiviere ein bereits angemeldetes Ressourcen-Pack-Profil"
 * (ROADMAP H2, der "aktivieren"-Schritt). Getrennt von {@link
 * ResourceReloadTrigger}, weil Aktivieren (Zustandsaenderung in der
 * Pack-Auswahl) und Neuladen (teurer Ressourcen-Reload) unterschiedlich oft
 * passieren: mehrere Aktivierungen kurz hintereinander sollen sich einen
 * einzigen Reload teilen (siehe {@link Debouncer}).
 */
public interface PackActivation {

	/**
	 * Aktiviert das Ressourcen-Pack-Profil mit der ID {@code packId}, falls
	 * es existiert und noch nicht aktiv ist.
	 *
	 * @return {@code true}, wenn der aktive Zustand dadurch tatsaechlich
	 *         geaendert wurde; {@code false}, wenn die ID unbekannt ist oder
	 *         das Profil schon aktiv war (in beiden Faellen kein Fehler).
	 */
	boolean activate(String packId);

	/**
	 * Deaktiviert das Ressourcen-Pack-Profil mit der ID {@code packId}, falls
	 * es aktiv ist (ROADMAP H3, der "Paket deaktivieren"-Schritt des
	 * Auto-Rollbacks: schlaegt ein Reload fehl, nimmt {@link
	 * HotApplyCoordinator} die zuletzt aktivierten, offenbar defekten Pakete
	 * damit automatisch zurueck).
	 *
	 * @return {@code true}, wenn der aktive Zustand dadurch tatsaechlich
	 *         geaendert wurde; {@code false}, wenn die ID unbekannt ist oder
	 *         das Profil ohnehin schon inaktiv war (in beiden Faellen kein
	 *         Fehler).
	 */
	boolean deactivate(String packId);
}
