package de.spassglas.vitrine.hotapply;

import java.util.Set;

/**
 * GATE36: haelt einen automatischen Rollback (ROADMAP H3) dauerhaft fest.
 *
 * <p>{@link HotApplyCoordinator} nahm bei einem gescheiterten Reload die
 * defekten Pakete zwar im laufenden Spiel zurueck, schrieb das aber nie in
 * {@code state.json}. Die Bibliothek zeigte die Pakete danach weiter als
 * "aktiv", und der naechste Start haette sie erneut aktiviert -- und waere
 * erneut gescheitert.</p>
 *
 * <p>Der Koordinator selbst bleibt frei von Datei- und Minecraft-Typen; die
 * Produktion ({@link HotApplyService}) setzt diese Schnittstelle mit
 * {@code PackStateStore.update(...)} um, Tests mit einem Aufzeichner.</p>
 */
@FunctionalInterface
public interface RollbackStatePersister {

	/**
	 * Vermerkt {@code packIds} als installiert, aber nicht mehr aktiv. Darf
	 * nicht werfen -- ein Fehler beim Schreiben wird von der Umsetzung
	 * geloggt; der Rollback im laufenden Spiel ist zu diesem Zeitpunkt schon
	 * geschehen und bleibt gueltig.
	 */
	void markInactive(Set<String> packIds);

	/** Fuer Aufbauten ohne Zustandsdatei (Tests, die den Rollback nicht pruefen). */
	static RollbackStatePersister none() {
		return packIds -> {
		};
	}
}
