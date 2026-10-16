package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.classb.FabricInstalledModConflictCheck;
import de.spassglas.vitrine.classb.HttpJarSource;
import de.spassglas.vitrine.classb.InstalledModConflictCheck;
import de.spassglas.vitrine.classb.JavaModStager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * ROADMAP J2 -- Produktions-Verdrahtung des client-freien {@link JavaModStager}
 * fuer den echten Client: Ziel {@code <gameDir>/mods}, Staging-Zustand in
 * {@code <gameDir>/config/vitrine/staged-mods.json}, Papierkorb
 * {@code <gameDir>/config/vitrine/trash} (alles ueber
 * {@link FabricLoader#getGameDir()}, kein absoluter Pfad), Jar-Bezug ueber
 * {@link HttpJarSource#production()}, Neustart ueber {@link ClientRestartAction}.
 *
 * <p>J3 (Konflikt- und Abhaengigkeitspruefung) laeuft ueber
 * {@link FabricInstalledModConflictCheck#production(Path)} -- installierte
 * Mods ueber den echten {@code FabricLoader}, vorgemerkte ueber dieselbe
 * {@code staged-mods.json}, die {@link JavaModStager} auch fuehrt. Der Seam
 * {@link InstalledModConflictCheck#NONE} bleibt fuer Tests erhalten.</p>
 *
 * <p><b>GATE33:</b> {@link JavaModStager} prueft die Pflicht-Signatur jedes
 * Angebots ausschliesslich gegen den im Jar eingebetteten Registry-Schluessel
 * ({@code EmbeddedSigningKey#load()}) -- es gibt hier deshalb keinen
 * Signaturschluessel-Parameter mehr zu injizieren (vorher faelschlich
 * {@code null}, was ein unsigniertes/fremd signiertes Angebot durchliess).</p>
 */
public final class ClientJavaModStaging {

	private static final String CONFIG_SUBDIR = "config";
	private static final String VITRINE_SUBDIR = "vitrine";

	private ClientJavaModStaging() {
	}

	private static final class Holder {
		private static final Path GAME_DIR = FabricLoader.getInstance().getGameDir();

		static final JavaModStager INSTANCE = JavaModStager.forGameDir(
				GAME_DIR,
				HttpJarSource.production(),
				new ClientRestartAction(),
				FabricInstalledModConflictCheck.production(GAME_DIR.resolve(CONFIG_SUBDIR).resolve(VITRINE_SUBDIR)));
	}

	/** Der eine {@link JavaModStager} des laufenden Clients. */
	public static JavaModStager instance() {
		return Holder.INSTANCE;
	}
}
