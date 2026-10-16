package de.spassglas.vitrine.ui.source;

import de.spassglas.vitrine.catalog.InstalledVersionLookup;
import de.spassglas.vitrine.catalog.UpdateStatus;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Quelle "Lokaler Ordner" (ROADMAP S2): liest alles aus
 * {@code config/vitrine/library/} und uebersetzt jede gueltige
 * {@code .vpkg}-Datei in ein Kachelmodell ({@link TileData}) fuer die
 * Store-Startseite. Anders als {@code de.spassglas.vitrine.pack.VitrinePackProvider}
 * (H1, {@code config/vitrine/packs/} = aktive Ressourcen-Packs) sind das hier
 * reine <em>Angebote</em> zum Installieren -- es wird nichts angewendet, nichts
 * entpackt, nur das Manifest gelesen (das eigentliche Installieren ist S4).
 *
 * <p><b>Frisch bei jedem Aufruf:</b> {@link #scan(PackState)} listet den Ordner
 * jedes Mal neu -- kein statischer Cache beim Modstart. Der Store ruft die
 * Methode bei jedem Aufbau seines Inhalts auf, damit eine neu hineingelegte
 * Datei ohne Neustart erscheint, sobald der Store erneut geoeffnet wird
 * (S2-Fertig-Kriterium).</p>
 *
 * <p><b>Robust gegen kaputte Dateien (Adversary-Fall):</b> eine unlesbare,
 * kein-ZIP-, sicherheitsverletzende oder schema-widrige Datei im Ordner wird
 * uebersprungen und als SLF4J-Warnung geloggt -- der Rest der Liste erscheint
 * trotzdem, es fliegt nie eine Exception aus {@link #scan(PackState)}.</p>
 *
 * <p><b>Fehlender Ordner:</b> {@code config/vitrine/library/} existiert auf
 * einer Frischinstallation nicht (Charter-Regel 12) -- dann ist das Ergebnis
 * einfach leer, das ist kein Fehler.</p>
 */
public final class LocalLibrarySource {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/LocalLibrary");

	private static final String LIBRARY_SUBDIR = "library";
	private static final String VPKG_EXTENSION = ".vpkg";

	/**
	 * Praefix, mit dem Ressourcen-Pack-Profile ihre Manifest-ID fuehren (siehe
	 * {@code VitrinePackProvider}). {@code state.json} kann eine Paket-ID in
	 * roher ({@code beispiel.paket}) oder in Profil-Form ({@code vitrine/beispiel.paket})
	 * vermerkt haben -- der Abgleich unten prueft beide, damit die
	 * "Installiert"-Markierung unabhaengig davon greift, welche Form der
	 * spaetere Installationsweg (S4) waehlt.
	 */
	private static final String STATE_PACK_ID_PREFIX = "vitrine/";

	private final Path libraryDir;

	/**
	 * GATE17: macht aus (Paketdatei, Bildpfad im Paket) eine Textur-Kennung fuer
	 * die Kachel. Der Aufbau ohne laufendes Spiel (Tests) hat keinen und liefert
	 * Kacheln mit Platzhalter -- Texturen gibt es nur im Client.
	 */
	@FunctionalInterface
	public interface ImageResolver {
		Optional<String> resolve(Path vpkgFile, String entryPath);
	}

	private ImageResolver imageResolver = (file, entry) -> Optional.empty();

	/** GATE17: Kacheln zeigen das Paketbild, wenn {@code resolver} eins liefert. */
	public LocalLibrarySource withImageResolver(ImageResolver resolver) {
		this.imageResolver = java.util.Objects.requireNonNull(resolver, "resolver");
		return this;
	}

	private LocalLibrarySource(Path libraryDir) {
		this.libraryDir = Objects.requireNonNull(libraryDir, "libraryDir");
	}

	/** Quelle fuer {@code vitrineConfigDir/library} (im echten Spiel {@code config/vitrine/library}). */
	public static LocalLibrarySource forConfigDir(Path vitrineConfigDir) {
		return new LocalLibrarySource(vitrineConfigDir.resolve(LIBRARY_SUBDIR));
	}

	/** Quelle fuer einen beliebigen Ordner -- gedacht fuer Tests ohne echten Konfigurationsordner. */
	public static LocalLibrarySource forLibraryDir(Path libraryDir) {
		return new LocalLibrarySource(libraryDir);
	}

	/** Der Ordner, den diese Quelle durchsucht. */
	public Path libraryDir() {
		return libraryDir;
	}

	/**
	 * Liest den Bibliotheksordner frisch aus und liefert je gueltiger
	 * {@code .vpkg}-Datei ein Kachelmodell. Wirft nie -- ein fehlender Ordner
	 * oder eine kaputte Datei ergibt hoechstens ein kuerzeres Ergebnis, nie
	 * einen Abbruch.
	 *
	 * @param installedState der aktuelle Paket-Zustand (aus {@code PackStateStore.load})
	 *                        fuer den Abgleich "installiert" -&gt; {@link TileStatus#INSTALLED};
	 *                        {@code null} ist erlaubt und bedeutet "nichts installiert".
	 */
	public List<TileData> scan(PackState installedState) {
		return scan(installedState, InstalledVersionLookup.NONE);
	}

	/**
	 * Wie {@link #scan(PackState)}, mit dem zusaetzlichen Versionsabgleich
	 * (ROADMAP GATE80): ist {@code manifestId} bereits installiert und liefert
	 * {@code installedVersionLookup} dafuer eine Version, die nach SemVer echt
	 * aelter ist als die Version der Datei in {@code library/} (die ja selbst
	 * das jeweils neueste Angebot ist), zeigt die Kachel
	 * {@link TileStatus#UPDATE_AVAILABLE} statt {@link TileStatus#INSTALLED} --
	 * derselbe Vergleich wie {@code IndexPackageTiles#toTile}.
	 */
	public List<TileData> scan(PackState installedState, InstalledVersionLookup installedVersionLookup) {
		if (!Files.isDirectory(libraryDir)) {
			LOGGER.debug("Vitrine-Bibliotheksordner '{}' existiert nicht, keine lokalen Angebote.", libraryDir);
			return List.of();
		}

		List<Path> candidates;
		try (Stream<Path> stream = Files.list(libraryDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			LOGGER.error("Vitrine-Bibliotheksordner '{}' liess sich nicht auflisten: {}", libraryDir, e.toString(), e);
			return List.of();
		}

		List<TileData> offers = new ArrayList<>(candidates.size());
		for (Path vpkgFile : candidates) {
			TileData tile = toTile(vpkgFile, installedState, imageResolver, installedVersionLookup);
			if (tile != null) {
				offers.add(tile);
			}
		}
		return List.copyOf(offers);
	}

	/** Ein einzelnes Angebot -&gt; Kachelmodell, oder {@code null} (plus Warnung), wenn die Datei nicht taugt. */
	private static TileData toTile(Path vpkgFile, PackState installedState, ImageResolver imageResolver,
			InstalledVersionLookup installedVersionLookup) {
		Manifest manifest = openAndReadManifest(vpkgFile);
		if (manifest == null) {
			return null;
		}

		Optional<TilePackageType> type = TilePackageType.byId(manifest.category());
		if (type.isEmpty()) {
			LOGGER.warn("Lokales Angebot '{}' nennt eine unbekannte Kategorie '{}', wird uebersprungen.",
					vpkgFile, manifest.category());
			return null;
		}

		TileStatus status = resolveStatus(manifest, installedState, installedVersionLookup);
		// GATE17: das Paketbild (icon, sonst erster Screenshot), wenn es sich laden laesst.
		String imagePath = de.spassglas.vitrine.ui.image.PackageImageBytes.tileImageEntry(manifest)
				.flatMap(entry -> imageResolver.resolve(vpkgFile, entry))
				.orElse(null);
		return new TileData(manifest.id(), manifest.name(), manifest.author(), type.get(), status, imagePath, false);
	}

	private static TileStatus resolveStatus(Manifest manifest, PackState installedState,
			InstalledVersionLookup installedVersionLookup) {
		if (!isInstalled(installedState, manifest.id())) {
			return TileStatus.NONE;
		}
		Optional<String> installedVersion = installedVersionLookup.installedVersionFor(manifest.id());
		if (installedVersion.isEmpty()) {
			return TileStatus.INSTALLED;
		}
		UpdateStatus update = UpdateStatus.evaluate(manifest.id(), installedVersion.get(), manifest.version());
		return update.updateAvailable() ? TileStatus.UPDATE_AVAILABLE : TileStatus.INSTALLED;
	}

	private static boolean isInstalled(PackState state, String manifestId) {
		if (state == null) {
			return false;
		}
		return state.isInstalled(manifestId) || state.isInstalled(STATE_PACK_ID_PREFIX + manifestId);
	}

	/**
	 * Oeffnet {@code vpkgFile} rein lesend, prueft die Zip-Sicherheit (P3) und
	 * parst das Manifest (P1). Liefert {@code null} und loggt eine Warnung bei
	 * jedem Fehlerfall -- nichts wird angewendet oder entpackt.
	 */
	private static Manifest openAndReadManifest(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			return ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			LOGGER.warn("Lokales Angebot '{}' liess sich nicht als .vpkg-Archiv oeffnen oder lesen, "
					+ "wird uebersprungen: {}", vpkgFile, e.toString());
			return null;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Lokales Angebot '{}' hat die Sicherheitspruefung nicht bestanden, wird uebersprungen: {}",
					vpkgFile, e.getMessage());
			return null;
		} catch (ManifestParseException e) {
			LOGGER.warn("Lokales Angebot '{}' hat ein ungueltiges Manifest, wird uebersprungen: {}",
					vpkgFile, e.getMessage());
			return null;
		} catch (RuntimeException e) {
			// Letzte Auffanglinie: ein von java.util.zip nicht als IOException
			// gemeldeter Formfehler darf den ganzen Scan nicht abreissen
			// (Adversary-Fall). Geloggt, nicht verschluckt.
			LOGGER.warn("Lokales Angebot '{}' liess sich unerwartet nicht auswerten, wird uebersprungen: {}",
					vpkgFile, e.toString(), e);
			return null;
		}
	}
}
