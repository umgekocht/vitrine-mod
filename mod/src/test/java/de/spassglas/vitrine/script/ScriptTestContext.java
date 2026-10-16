package de.spassglas.vitrine.script;

/** Fester {@link ScriptContext} fuer Tests -- keine Abhaengigkeit vom Client. */
record ScriptTestContext(double installedPackageCount, double activePackageCount, String activeThemeId,
		double fps, double playerX, double playerY, double playerZ, String biomeId, long dayTimeTicks)
		implements ScriptContext {

	static final ScriptTestContext DEFAULT =
			new ScriptTestContext(5, 3, "klarglas", 60, 10, 64, -20, "minecraft:plains", 1000);
}
