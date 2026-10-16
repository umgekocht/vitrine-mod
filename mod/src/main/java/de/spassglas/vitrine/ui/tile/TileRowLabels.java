package de.spassglas.vitrine.ui.tile;

import java.util.Objects;

/**
 * ROADMAP GATE14: Ueberschrift und Unterzeile einer Kachelreihe als Paar von
 * Uebersetzungs-Keys -- und die eine Stelle, an der entschieden wird, welche
 * Beschriftung zu welchem Inhalt gehoert.
 *
 * <p>Vorher trug die Hauptreihe der Startseite immer die Demo-Beschriftung
 * („Beispielkacheln" / „Platzhalter-Reihe"), weil {@code TileRowWidget} ohne
 * uebergebene Keys stillschweigend darauf zurueckfiel -- auch dann, wenn in
 * der Reihe echte Pakete aus der Registry standen. Das ist ein Bruch von
 * Charter-Regel 2 in beide Richtungen: echte Inhalte duerfen nicht als Demo
 * ausgegeben werden, und Demo-Inhalte nicht als echt.</p>
 *
 * <p>Kennt keinen Minecraft-Typ, damit die Zuordnung ohne laufenden Client
 * geprueft werden kann (siehe {@code TileRowLabelsTest}).</p>
 *
 * @param titleKey    Lang-Key der Ueberschrift
 * @param subtitleKey Lang-Key der Unterzeile
 */
public record TileRowLabels(String titleKey, String subtitleKey) {

	public TileRowLabels {
		Objects.requireNonNull(titleKey, "titleKey");
		Objects.requireNonNull(subtitleKey, "subtitleKey");
	}

	/** Echte Katalog-Kacheln, frisch aus der Registry geladen. */
	public static TileRowLabels catalogRow() {
		return new TileRowLabels("vitrine.row.catalog.title", "vitrine.row.catalog.subtitle");
	}

	/** Echte Katalog-Kacheln, aber aus dem Cache (ROADMAP K4: kein Netz). */
	public static TileRowLabels cachedCatalogRow() {
		return new TileRowLabels("vitrine.row.catalog.cached.title", "vitrine.row.catalog.cached.subtitle");
	}

	/** Die Demo-/Beispielreihe aus {@code DemoCatalog} -- Ids ohne Registry-Entsprechung. */
	public static TileRowLabels demoRow() {
		return new TileRowLabels("vitrine.row.demo.title", "vitrine.row.demo.subtitle");
	}

	/**
	 * Beschriftung der Hauptreihe auf der Startseite: echte Katalog-Kacheln
	 * bekommen die Katalog-Beschriftung, die Rueckfall-Demo-Reihe ihre eigene.
	 *
	 * @param fromRealCatalog {@code true}, wenn die Reihe echte Registry-Kacheln zeigt
	 */
	public static TileRowLabels forStartRow(boolean fromRealCatalog) {
		return fromRealCatalog ? catalogRow() : demoRow();
	}
}
