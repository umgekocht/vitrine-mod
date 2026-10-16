package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TileData;

import java.util.Comparator;

/**
 * Sortierung der Suchergebnisse (ROADMAP S6): mindestens Name A-Z, Autor A-Z
 * und Typ. Jede Ordnung ist <em>stabil</em> -- {@link #comparator()} vergleicht
 * nur das genannte Feld, gleiche Schluessel behalten ihre Eingabereihenfolge
 * (Javas {@code List.sort} ist ein stabiles TimSort).
 *
 * <p>Jeder Wert traegt einen Uebersetzungs-Key fuer die Beschriftung des
 * Sortier-Knopfs im Inhaltsbereich; die Keys liegen in beiden Sprachdateien.</p>
 */
public enum SearchSort {

	/** Name aufsteigend, ohne Beachtung der Gross-/Kleinschreibung. */
	NAME_ASC("vitrine.search.sort.name") {
		@Override
		public Comparator<TileData> comparator() {
			return Comparator.comparing(TileData::name, String.CASE_INSENSITIVE_ORDER);
		}
	},

	/** Autor aufsteigend, ohne Beachtung der Gross-/Kleinschreibung. */
	AUTHOR_ASC("vitrine.search.sort.author") {
		@Override
		public Comparator<TileData> comparator() {
			return Comparator.comparing(TileData::author, String.CASE_INSENSITIVE_ORDER);
		}
	},

	/** Nach Pakettyp gruppiert, in der Reihenfolge der {@code TilePackageType}-Aufzaehlung. */
	TYPE("vitrine.search.sort.type") {
		@Override
		public Comparator<TileData> comparator() {
			return Comparator.comparingInt(tile -> tile.type().ordinal());
		}
	};

	private final String translationKey;

	SearchSort(String translationKey) {
		this.translationKey = translationKey;
	}

	/** Uebersetzungs-Key der Knopf-Beschriftung, z. B. {@code vitrine.search.sort.name}. */
	public String translationKey() {
		return translationKey;
	}

	/** Die naechste Sortierung im Ring -- fuer den durchschaltenden Sortier-Knopf. */
	public SearchSort next() {
		SearchSort[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	/** Vergleicht nur nach dem sortierrelevanten Feld; gleiche Schluessel bleiben stabil in Eingabereihenfolge. */
	public abstract Comparator<TileData> comparator();
}
