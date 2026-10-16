package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.nav.NavRailEntry;
import de.spassglas.vitrine.ui.tile.TilePackageType;

import java.util.Set;

/**
 * Ordnet einen Nav-Rail-Eintrag (docs/UI-SPEC.md „2. Nav-Rail links") der
 * Menge der Pakettypen zu, die dieser Bereich zeigt (ROADMAP S6: „Kategorien
 * ueber die Nav-Rail"). „Start", „Bibliothek" und „Einstellungen" sind keine
 * Kategorien und liefern die leere Menge (= keine Einschraenkung).
 *
 * <p>„Sound &amp; Sprache" buendelt zwei Pakettypen ({@code SOUND} und
 * {@code SPRACHE}), deshalb eine Menge statt eines einzelnen Typs.</p>
 */
public final class NavRailCategories {

	private NavRailCategories() {
	}

	/** Die Pakettypen des Bereichs, oder eine leere Menge, wenn der Eintrag keine Kategorie ist. */
	public static Set<TilePackageType> typesFor(NavRailEntry entry) {
		return switch (entry) {
			case TEXTURES -> Set.of(TilePackageType.TEXTUREN);
			case THEMES -> Set.of(TilePackageType.THEME);
			case HUD -> Set.of(TilePackageType.HUD);
			case SOUND_LANGUAGE -> Set.of(TilePackageType.SOUND, TilePackageType.SPRACHE);
			case COSMETICS -> Set.of(TilePackageType.COSMETICS);
			case SHADER -> Set.of(TilePackageType.SHADER);
			// wie der Bedrock-Marketplace: Addons umfassen auch HUD-Module (umgekocht, 2026-10-06)
			case ADDONS -> Set.of(TilePackageType.ADDON, TilePackageType.HUD);
			case MODS -> Set.of(TilePackageType.MOD);
			case NEWS -> Set.of();
			case START, LIBRARY, SETTINGS -> Set.of();
		};
	}

	/** Ob der Eintrag eine gefilterte Kachelmenge zeigt (statt Start/Bibliothek/Einstellungen). */
	public static boolean isCategory(NavRailEntry entry) {
		return !typesFor(entry).isEmpty();
	}
}
