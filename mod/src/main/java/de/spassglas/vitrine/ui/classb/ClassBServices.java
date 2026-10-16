package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.classb.FabricInstalledModConflictCheck;
import de.spassglas.vitrine.classb.HttpJarSource;
import de.spassglas.vitrine.classb.JavaModStager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * GATE6: Produktions-Verdrahtung fuer Klasse B -- der {@link JavaModStager}
 * des laufenden Spiels mit echtem Download ({@link HttpJarSource}), echtem
 * Neustart ({@link ClientRestartAction}) und echter Konfliktpruefung gegen
 * die geladenen und vorgemerkten Fabric-Mods.
 *
 * <p>Jeder Aufruf baut einen frischen Stager; er haelt keinen eigenen
 * Zustand, alles Gestagte steht in {@code config/vitrine/staged-mods.json}.</p>
 */
public final class ClassBServices {

	private ClassBServices() {
	}

	public static JavaModStager stager() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path vitrineConfigDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		return JavaModStager.forGameDir(gameDir, HttpJarSource.production(), new ClientRestartAction(),
				FabricInstalledModConflictCheck.production(vitrineConfigDir));
	}
}
