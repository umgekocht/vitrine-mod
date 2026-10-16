package de.spassglas.vitrine.hotapply;

import de.spassglas.vitrine.state.PackStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * GATE36: Produktions-Umsetzung von {@link RollbackStatePersister} -- schreibt
 * einen automatischen Rollback nach {@code state.json}, ueber denselben
 * gesperrten Weg wie jede andere Zustandsaenderung (GATE41,
 * {@link PackStateStore#update}).
 *
 * <p>Nur Pakete, die {@code state.json} ueberhaupt als installiert kennt,
 * werden auf "inaktiv" gesetzt -- ein Rollback legt nie neue Eintraege an.</p>
 */
public final class StateFileRollbackPersister implements RollbackStatePersister {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/HotApply");

	private final Path vitrineConfigDir;

	private StateFileRollbackPersister(Path vitrineConfigDir) {
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
	}

	public static StateFileRollbackPersister forConfigDir(Path vitrineConfigDir) {
		return new StateFileRollbackPersister(vitrineConfigDir);
	}

	@Override
	public void markInactive(Set<String> packIds) {
		if (packIds.isEmpty()) {
			return;
		}
		try {
			int changed = PackStateStore.update(vitrineConfigDir, state -> {
				int count = 0;
				for (String packId : packIds) {
					if (state.isInstalled(packId) && state.isActive(packId)) {
						state.addOrUpdate(packId, false);
						count++;
					}
				}
				return count;
			});
			LOGGER.info("Rollback in state.json festgehalten: {} von {} Paket(en) als inaktiv vermerkt ({}).",
					changed, packIds.size(), packIds);
		} catch (IOException e) {
			// Der Rollback im laufenden Spiel ist schon geschehen und bleibt
			// gueltig -- nur das Festhalten scheiterte. Laut melden, nicht werfen.
			LOGGER.error("Rollback von {} liess sich nicht in state.json festhalten -- die Bibliothek kann die "
					+ "Pakete bis zum naechsten Schreiben noch als aktiv zeigen: {}", packIds, e.toString(), e);
		}
	}
}
