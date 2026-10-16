package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.Vitrine;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Haelt den einen {@link CatalogPackInstaller} fuer den echten Client
 * (ROADMAP G3) -- dasselbe "initialization-on-demand holder"-Muster (JLS
 * 12.4.1) wie {@link PackInstallService}, aus demselben Grund: kein
 * {@code FabricLoader.getInstance()} bei jeder Klasseninitialisierung.
 *
 * <p>Oeffentlicher Einstiegspunkt fuer die Store-Detailseite eines
 * Katalog-Angebots (Aktionsknopf). Die eigentliche Lade-/Pruef-/Installations-
 * Kette ist vollstaendig in {@link CatalogPackInstaller} getestet.</p>
 */
public final class CatalogPackInstallService {

	private static final class InstanceHolder {
		static final CatalogPackInstaller INSTANCE =
				CatalogPackInstaller.production(FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID));
	}

	private CatalogPackInstallService() {
	}

	/** Der eine {@link CatalogPackInstaller} fuer den echten Client. */
	public static CatalogPackInstaller instance() {
		return InstanceHolder.INSTANCE;
	}
}
