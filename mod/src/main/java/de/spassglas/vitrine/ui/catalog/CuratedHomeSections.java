package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.Featured;
import de.spassglas.vitrine.catalog.FeaturedItem;
import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.classb.JavaModOfferGate;
import de.spassglas.vitrine.ui.tile.TileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Baut die kuratierten Start-Reihen aus echten Katalogdaten (ROADMAP X2:
 * "Kuratierte Startseite (featured, neu, beliebt) aus dem Index"). Client-frei
 * -- ohne laufenden Minecraft-Client testbar, keine Exception nach aussen.
 *
 * <ul>
 *   <li>{@link #featured(List, Featured, JavaModOfferGate)} -- aus
 *       {@code featured.json}s {@code items[]} (ROADMAP K1), sortiert nach
 *       {@code order} aufsteigend.</li>
 *   <li>{@link #newest(List, JavaModOfferGate)} -- alle Pakete der Index-Seite,
 *       sortiert nach {@code updatedAt} absteigend (neueste zuerst). Ein
 *       Paket mit unlesbarem Datum faellt ans Ende, statt den Aufbau
 *       abzubrechen.</li>
 *   <li>{@link #popular(List, Featured, JavaModOfferGate)} -- aus
 *       {@code featured.json}s {@code popular[]} (ROADMAP X2), dieselbe Form
 *       und dasselbe Verhalten wie {@code items[]}, aber eine eigenstaendige,
 *       unabhaengig gepflegte Liste: ein Paket kann in der einen Liste
 *       stehen, in der anderen fehlen, in beiden stehen oder in keiner.</li>
 * </ul>
 *
 * <p>Fuer "Featured" und "Beliebt" gilt gleichermassen: ein kuratierter
 * Eintrag, dessen {@code id} auf der geladenen Index-Seite nicht (mehr)
 * auftaucht, wird geloggt und ausgelassen -- kein Absturz, keine leere
 * Kachel.</p>
 */
public final class CuratedHomeSections {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/StoreCatalog");

	private CuratedHomeSections() {
	}

	/**
	 * Kacheln fuer die "Featured"-Reihe, sortiert nach
	 * {@link FeaturedItem#order()} aufsteigend. {@code featured} darf
	 * {@code null} sein (kein Abruf, Abruf fehlgeschlagen) -- dann leer, ohne
	 * Fehlermeldung, damit {@code StoreScreen} die Reihe einfach auslaesst
	 * (wie {@code buildLocalLibraryRow} bei einer leeren lokalen Bibliothek).
	 */
	public static List<TileData> featured(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate) {
		return featured(packages, featured, gate, null);
	}

	/**
	 * Wie {@link #featured(List, Featured, JavaModOfferGate)}, mit dem
	 * aktuellen Paket-Zustand fuer den "Installiert"-Abgleich (ROADMAP
	 * GATE46, siehe {@link IndexPackageTiles#toTile}). {@code installedState}
	 * darf {@code null} sein ("nichts installiert").
	 */
	public static List<TileData> featured(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate,
			PackState installedState) {
		return featured(packages, featured, gate, installedState, InstalledVersionLookup.NONE);
	}

	/**
	 * Wie {@link #featured(List, Featured, JavaModOfferGate, PackState)}, mit
	 * dem zusaetzlichen Versionsabgleich (ROADMAP GATE80, siehe
	 * {@link IndexPackageTiles#toTile(IndexPackage, PackState, InstalledVersionLookup)}).
	 */
	public static List<TileData> featured(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate,
			PackState installedState, InstalledVersionLookup installedVersionLookup) {
		return curatedRow(packages, featured == null ? null : featured.items(), gate, installedState,
				installedVersionLookup, "Featured");
	}

	/**
	 * Kacheln fuer die "Beliebt"-Reihe, sortiert nach
	 * {@link FeaturedItem#order()} aufsteigend, aus {@code featured.json}s
	 * {@code popular[]} -- redaktionell kuratiert wie "Featured", aber eine
	 * eigenstaendige Liste (ROADMAP X2). {@code featured} darf {@code null}
	 * sein -- dann leer, ohne Fehlermeldung, wie bei {@link #featured}.
	 */
	public static List<TileData> popular(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate) {
		return popular(packages, featured, gate, null);
	}

	/**
	 * Wie {@link #popular(List, Featured, JavaModOfferGate)}, mit dem
	 * aktuellen Paket-Zustand fuer den "Installiert"-Abgleich (ROADMAP GATE46).
	 */
	public static List<TileData> popular(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate,
			PackState installedState) {
		return popular(packages, featured, gate, installedState, InstalledVersionLookup.NONE);
	}

	/**
	 * Wie {@link #popular(List, Featured, JavaModOfferGate, PackState)}, mit
	 * dem zusaetzlichen Versionsabgleich (ROADMAP GATE80).
	 */
	public static List<TileData> popular(List<IndexPackage> packages, Featured featured, JavaModOfferGate gate,
			PackState installedState, InstalledVersionLookup installedVersionLookup) {
		return curatedRow(packages, featured == null ? null : featured.popular(), gate, installedState,
				installedVersionLookup, "Beliebt");
	}

	private static List<TileData> curatedRow(List<IndexPackage> packages, List<FeaturedItem> curatedItems,
			JavaModOfferGate gate, PackState installedState, InstalledVersionLookup installedVersionLookup,
			String rowNameForLog) {
		Objects.requireNonNull(packages, "packages");
		Objects.requireNonNull(gate, "gate");
		if (curatedItems == null || curatedItems.isEmpty()) {
			return List.of();
		}
		Map<String, IndexPackage> byId = indexById(packages);
		List<FeaturedItem> sortedItems = curatedItems.stream()
				.sorted(Comparator.comparingInt(FeaturedItem::order))
				.toList();

		List<TileData> tiles = new ArrayList<>(sortedItems.size());
		for (FeaturedItem item : sortedItems) {
			IndexPackage pkg = byId.get(item.id());
			if (pkg == null) {
				LOGGER.debug("Kuratiertes Paket '{}' ist nicht (mehr) auf der geladenen Index-Seite -- "
						+ "wird in der {}-Reihe ausgelassen.", item.id(), rowNameForLog);
				continue;
			}
			IndexPackageTiles.toTile(pkg, installedState, installedVersionLookup).ifPresent(tiles::add);
		}
		return gate.apply(List.copyOf(tiles));
	}

	/** Kacheln fuer die "Neu"-Reihe, sortiert nach {@code updatedAt} absteigend (neueste zuerst). */
	public static List<TileData> newest(List<IndexPackage> packages, JavaModOfferGate gate) {
		return newest(packages, gate, null);
	}

	/**
	 * Wie {@link #newest(List, JavaModOfferGate)}, mit dem aktuellen
	 * Paket-Zustand fuer den "Installiert"-Abgleich (ROADMAP GATE46).
	 */
	public static List<TileData> newest(List<IndexPackage> packages, JavaModOfferGate gate, PackState installedState) {
		return newest(packages, gate, installedState, InstalledVersionLookup.NONE);
	}

	/**
	 * Wie {@link #newest(List, JavaModOfferGate, PackState)}, mit dem
	 * zusaetzlichen Versionsabgleich (ROADMAP GATE80).
	 */
	public static List<TileData> newest(List<IndexPackage> packages, JavaModOfferGate gate, PackState installedState,
			InstalledVersionLookup installedVersionLookup) {
		Objects.requireNonNull(packages, "packages");
		Objects.requireNonNull(gate, "gate");

		List<IndexPackage> sorted = packages.stream()
				.sorted(Comparator.comparing(CuratedHomeSections::parseUpdatedAt).reversed())
				.toList();

		List<TileData> tiles = new ArrayList<>(sorted.size());
		for (IndexPackage pkg : sorted) {
			IndexPackageTiles.toTile(pkg, installedState, installedVersionLookup).ifPresent(tiles::add);
		}
		return gate.apply(List.copyOf(tiles));
	}

	private static Map<String, IndexPackage> indexById(List<IndexPackage> packages) {
		Map<String, IndexPackage> byId = new HashMap<>(packages.size());
		for (IndexPackage pkg : packages) {
			byId.put(pkg.id(), pkg);
		}
		return byId;
	}

	private static Instant parseUpdatedAt(IndexPackage pkg) {
		try {
			return Instant.parse(pkg.updatedAt());
		} catch (DateTimeParseException e) {
			LOGGER.warn("Paket '{}' hat ein unlesbares 'updatedAt' ('{}'), wird in der Neu-Reihe ans Ende einsortiert: {}",
					pkg.id(), pkg.updatedAt(), e.toString());
			return Instant.MIN;
		}
	}
}
