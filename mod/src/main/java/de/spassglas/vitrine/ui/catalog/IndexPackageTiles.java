package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.IndexPackage;
import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.catalog.UpdateStatus;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Uebersetzt einen einzelnen Katalog-Kurzeintrag ({@link IndexPackage}) in
 * eine Kachel ({@link TileData}) -- gemeinsamer Baustein von
 * {@link StoreCatalogController} (alle Pakete) und {@link CuratedHomeSections}
 * (ROADMAP X2: featured/neu), damit die Kategorie-/Klasse-B-Uebersetzung an
 * genau einer Stelle steht.
 */
final class IndexPackageTiles {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/StoreCatalog");

	/**
	 * Praefix, mit dem installierte Katalog-Pakete in {@code state.json}
	 * stehen ({@code VitrinePackProvider.profileId}) -- genau derselbe
	 * Abgleich wie {@code LocalLibrarySource#isInstalled}, damit eine
	 * Katalog-Kachel nach der Installation ebenso "Installiert" zeigt wie
	 * die lokale Bibliothek (ROADMAP GATE46).
	 */
	private static final String STATE_PACK_ID_PREFIX = "vitrine/";

	private IndexPackageTiles() {
	}

	/**
	 * {@link Optional#empty()}, wenn {@code pkg} eine dem Store unbekannte
	 * Kategorie nennt -- geloggt, das Paket wird ausgelassen statt den Store
	 * abstuerzen zu lassen. Kein eigenes Vorschaubild: {@code pkg.icon()} ist
	 * eine entfernte URL, die K4 nicht laedt (nur lesen/anzeigen).
	 *
	 * @param installedState der aktuelle Paket-Zustand (aus {@code
	 *                        PackStateStore.load}) fuer den Abgleich
	 *                        "installiert" -&gt; {@link TileStatus#INSTALLED}
	 *                        (ROADMAP GATE46); {@code null} ist erlaubt und
	 *                        bedeutet "nichts installiert".
	 */
	static Optional<TileData> toTile(IndexPackage pkg, PackState installedState) {
		return toTile(pkg, installedState, InstalledVersionLookup.NONE);
	}

	/**
	 * Wie {@link #toTile(IndexPackage, PackState)}, mit einem zusaetzlichen
	 * Versionsabgleich (ROADMAP GATE80): ist {@code pkg} installiert und liefert
	 * {@code installedVersionLookup} dafuer eine lokale Version, die nach SemVer
	 * echt aelter ist als {@link IndexPackage#latestVersion()}, zeigt die Kachel
	 * {@link TileStatus#UPDATE_AVAILABLE} statt {@link TileStatus#INSTALLED}
	 * (derselbe Versionsvergleich wie {@code LibraryModel.updateStatuses()},
	 * {@link UpdateStatus#evaluate}). Ohne bekannte lokale Version ({@link
	 * InstalledVersionLookup#NONE}) bleibt es ehrlich bei {@code INSTALLED} --
	 * kein Update vorgaukeln, wenn der Vergleich gar nicht moeglich ist
	 * (Charter-Regel 2).
	 */
	static Optional<TileData> toTile(IndexPackage pkg, PackState installedState,
			InstalledVersionLookup installedVersionLookup) {
		Optional<TilePackageType> type = TilePackageType.byId(pkg.category());
		if (type.isEmpty()) {
			LOGGER.warn("Katalog-Paket '{}' nennt eine unbekannte Kategorie '{}', wird ausgelassen.",
					pkg.id(), pkg.category());
			return Optional.empty();
		}
		if (pkg.javaMod()) {
			// ROADMAP J1: Klasse-B-Angebot -- sichtbar (falls durchgelassen)
			// ehrlich als "Neustart noetig" gekennzeichnet (Charter-Regel 2).
			// Ein Klasse-B-Angebot wird erst nach einem Neustart wirksam --
			// bis dahin bleibt "Neustart noetig" der ehrliche Status, auch
			// wenn es schon auf die Platte kopiert wurde.
			return Optional.of(TileData.javaModOffer(pkg.id(), pkg.name(), pkg.author(), type.get(),
					TileStatus.RESTART_REQUIRED));
		}
		TileStatus status = resolveStatus(pkg, installedState, installedVersionLookup);
		// Vorschaubild aus dem Katalog (https-URL); geladen und geprueft wird es
		// erst beim Zeichnen ueber CatalogImageCache, bis dahin Kategoriebild.
		String preview = de.spassglas.vitrine.ui.image.CatalogImageCache.isAllowedUrl(pkg.icon()) ? pkg.icon() : null;
		return Optional.of(new TileData(pkg.id(), pkg.name(), pkg.author(), type.get(), status, preview, false));
	}

	private static TileStatus resolveStatus(IndexPackage pkg, PackState installedState,
			InstalledVersionLookup installedVersionLookup) {
		if (!isInstalled(installedState, pkg.id())) {
			return TileStatus.NONE;
		}
		Optional<String> installedVersion = installedVersionLookup.installedVersionFor(pkg.id());
		if (installedVersion.isEmpty()) {
			return TileStatus.INSTALLED;
		}
		UpdateStatus update = UpdateStatus.evaluate(pkg.id(), installedVersion.get(), pkg.latestVersion());
		return update.updateAvailable() ? TileStatus.UPDATE_AVAILABLE : TileStatus.INSTALLED;
	}

	private static boolean isInstalled(PackState state, String manifestId) {
		if (state == null) {
			return false;
		}
		return state.isInstalled(manifestId) || state.isInstalled(STATE_PACK_ID_PREFIX + manifestId);
	}
}
