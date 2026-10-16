package de.spassglas.vitrine.catalog;

import java.util.Optional;

/**
 * Seam auf die tatsaechlich installierte Version eines Pakets (ROADMAP GATE80):
 * liefert je Manifest-ID die Version, die gerade in {@code config/vitrine/packs/}
 * liegt (aus dem {@code .vpkg}-Manifest), oder leer, wenn das Paket nicht
 * installiert ist oder seine Datei sich nicht lesen liess.
 *
 * <p>Pendant zu {@code LibraryModel.CatalogVersions} ("welche Version fuehrt der
 * Katalog"), nur umgekehrt ("welche Version liegt lokal") -- zusammen ergeben
 * beide den Versionsvergleich, den {@code IndexPackageTiles} braucht, um einer
 * Katalog-Kachel {@code TileStatus.UPDATE_AVAILABLE} statt nur {@code
 * INSTALLED} zuzuweisen.</p>
 */
@FunctionalInterface
public interface InstalledVersionLookup {

	Optional<String> installedVersionFor(String manifestId);

	/** Keine lokale Version bekannt -- der Vor-GATE80-Zustand (immer "kein Update"). */
	InstalledVersionLookup NONE = manifestId -> Optional.empty();
}
