package de.spassglas.vitrine.classb;

/**
 * ROADMAP J3 -- Andock-Seam fuer die Konflikt- und Abhaengigkeitspruefung
 * gegen bereits installierte Mods. Prueft vor dem Kopieren, ob eine Mod-ID
 * doppelt waere oder eine harte Abhaengigkeit fehlt. {@link JavaModStager#stage}
 * ruft {@link #check} an genau der richtigen Stelle -- nach der Pruefsumme,
 * aber <em>vor</em> dem {@code ATOMIC_MOVE} nach {@code mods/}.
 *
 * <p>Die produktive Implementierung ist {@link FabricInstalledModConflictCheck}
 * (installierte Mods ueber Fabric Loader, vorgemerkte ueber
 * {@code staged-mods.json}). Der Default {@link #NONE} tut nichts -- fuer
 * Tests, die die Konfliktpruefung bewusst umgehen wollen.</p>
 */
@FunctionalInterface
public interface InstalledModConflictCheck {

	/** Tut nichts -- Platzhalter, bis J3 die echte Pruefung liefert. */
	InstalledModConflictCheck NONE = offer -> {
	};

	/**
	 * @throws JavaModStagingException wenn {@code offer} mit einem bereits
	 *                                 installierten Mod kollidiert (J3).
	 */
	void check(JavaModOffer offer) throws JavaModStagingException;
}
