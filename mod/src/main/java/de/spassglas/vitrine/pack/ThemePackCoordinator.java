package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.ui.theme.ThemeManager;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * ROADMAP T4 ("Theme-Pakete aus dem Store, hot"): der client-freie Koordinator,
 * der ein Klasse-A-Theme-Paket ({@code category: "theme"}) beim Aktivieren
 * <em>ohne</em> Ressourcen-Reload wirksam macht -- nur Token-Wechsel plus
 * Zuhoerer-Benachrichtigung ueber {@link ThemeManager}.
 *
 * <h2>Konvention fuer die {@code theme.json} im {@code .vpkg}</h2>
 * Die Theme-Tokens liegen als <b>{@code theme.json} im Wurzelverzeichnis</b> des
 * {@code .vpkg} (siehe {@code docs/PLAN.md} &sect;2 -- dieselbe Stelle wie
 * {@code vitrine.json}). Kein zusaetzliches Manifestfeld: der Pfad ist fest,
 * {@link #THEME_ENTRY_NAME}. Fehlt der Eintrag oder ist die Datei kaputt, bleibt
 * die Paket-Aktivierung trotzdem erfolgreich (das Paket ist installiert), nur das
 * Theme greift nicht -- {@link ThemeManager#loadAndSwitch} meldet den Grund
 * bereits als Klartext, hier wird zusaetzlich ehrlich geloggt.
 *
 * <h2>Ablauf</h2>
 * <ul>
 *   <li><b>Aktivieren</b> ({@code PackInstaller#install} bzw. {@code
 *       setActive(..., true)}): {@link #onThemePackActivated(String)} oeffnet die
 *       {@code .vpkg} rein lesend ueber {@link VpkgArchive}, prueft sie mit
 *       {@link VpkgSecurityValidator#requireSafe} (Zip-Slip, Zip-Bombe,
 *       verbotene Typen, PNG-Masze) <em>bevor</em> irgendein Eintrag gelesen
 *       wird, prueft danach (GATE103, Charter-Regel 3) ueber
 *       {@link VitrinePackProvider#hasValidIntegrity} die SHA-256-Pruefsumme
 *       und Ed25519-Signatur -- derselbe Pfad wie Resource-Packs (GATE29),
 *       <em>nicht</em> ein zweiter, schwaecherer --, dann (GATE32,
 *       Charter-Regel 4) ueber {@link VpkgCapabilityGrant#of(Manifest)}, dass
 *       das Manifest die Faehigkeit {@link VpkgCapability#THEME} angemeldet
 *       hat, dann (GATE105, Charter-Regel 4, wie GATE60) dass der Nutzer
 *       dieser Faehigkeit im Faehigkeiten-Dialog auch tatsaechlich zugestimmt
 *       hat ({@code PackState#hasCapabilityConsent}), liest dann
 *       {@link #THEME_ENTRY_NAME} und ruft
 *       {@link ThemeManager#loadAndSwitch(byte[], String)}. <b>Kein</b>
 *       {@code reloadResources()}.</li>
 *   <li><b>Deaktivieren/Deinstallieren</b> ({@code setActive(..., false)} bzw.
 *       {@code PackUninstaller#uninstall}): {@link #onThemePackDeactivated(String)}
 *       -- war das Paket das aktive Theme, wird auf das naechste noch aktive
 *       Theme-Paket in {@code state.json}-Reihenfolge gewechselt, sonst auf das
 *       Standard-Theme ({@link ThemeManager#resetToDefault()}).</li>
 * </ul>
 *
 * <p>Client-frei testbar: {@link ThemeManager} ist client-frei, der Zugriff auf
 * die {@code .vpkg}-Datei laeuft ueber einen {@link Function}-Seam
 * ({@code profileId -> Optional<Path>}, im echten Client
 * {@code VitrinePackProvider::findPackFile}).</p>
 */
public final class ThemePackCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Theme");

	/** Fester Pfad der Theme-Tokens im {@code .vpkg} (Wurzelverzeichnis). */
	public static final String THEME_ENTRY_NAME = "theme.json";

	/** {@code category}-Wert eines Theme-Pakets im {@code vitrine.json} (siehe {@code vitrine.schema.json}). */
	public static final String THEME_CATEGORY = "theme";

	private final ThemeManager themeManager;
	private final Path vitrineConfigDir;
	private final Function<String, Optional<Path>> packFileResolver;

	/** Profil-ID des Theme-Pakets, dessen Tokens gerade aktiv sind, oder {@code null} (= Standard-Theme). */
	private volatile String activeThemePackId;

	public ThemePackCoordinator(ThemeManager themeManager, Path vitrineConfigDir,
			Function<String, Optional<Path>> packFileResolver) {
		this.themeManager = Objects.requireNonNull(themeManager, "themeManager");
		this.vitrineConfigDir = Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		this.packFileResolver = Objects.requireNonNull(packFileResolver, "packFileResolver");
	}

	/** {@code true}, wenn {@code manifest} ein Klasse-A-Theme-Paket beschreibt ({@code category == "theme"}). */
	public static boolean isThemePackage(Manifest manifest) {
		return manifest != null && THEME_CATEGORY.equalsIgnoreCase(manifest.category());
	}

	/** Die Profil-ID des aktuell wirksamen Theme-Pakets, oder leer, wenn das Standard-Theme aktiv ist. */
	public Optional<String> activeThemePackId() {
		return Optional.ofNullable(activeThemePackId);
	}

	/**
	 * Ein Theme-Paket wurde aktiviert: seine {@code theme.json} lesen und zum
	 * aktiven Token-Satz machen -- ohne Ressourcen-Reload. Greift das Theme nicht
	 * (kein/kaputter {@code theme.json}-Eintrag, Sicherheitsverstosz), bleibt das
	 * bisherige Theme aktiv; die Paket-Aktivierung selbst gilt trotzdem als
	 * erfolgreich.
	 */
	public synchronized void onThemePackActivated(String profileId) {
		Objects.requireNonNull(profileId, "profileId");
		Optional<Path> file = packFileResolver.apply(profileId);
		if (file.isEmpty()) {
			LOGGER.warn("Theme-Paket '{}' hat keine Datei in packs/ -- kein Token-Wechsel.", profileId);
			return;
		}
		if (applyThemeFromPack(file.get())) {
			activeThemePackId = profileId;
		}
	}

	/**
	 * Ein Theme-Paket wurde deaktiviert oder deinstalliert. War es das aktive
	 * Theme, wird auf das naechste noch aktive Theme-Paket in
	 * {@code state.json}-Reihenfolge gewechselt; gibt es keins, auf das
	 * Standard-Theme. War es nicht das aktive Theme, passiert nichts.
	 */
	public synchronized void onThemePackDeactivated(String profileId) {
		Objects.requireNonNull(profileId, "profileId");
		if (!profileId.equals(activeThemePackId)) {
			return;
		}
		activeThemePackId = null;

		PackState state = PackStateStore.load(vitrineConfigDir);
		for (String candidate : state.activePackIdsInOrder()) {
			if (candidate.equals(profileId)) {
				continue;
			}
			Optional<Path> file = packFileResolver.apply(candidate);
			if (file.isEmpty() || !isThemePackFile(file.get())) {
				continue;
			}
			if (applyThemeFromPack(file.get())) {
				activeThemePackId = candidate;
				LOGGER.info("Theme-Paket '{}' deaktiviert -- '{}' als naechstes aktives Theme uebernommen.",
						profileId, candidate);
				return;
			}
		}

		themeManager.resetToDefault();
		LOGGER.info("Theme-Paket '{}' deaktiviert -- kein weiteres aktives Theme-Paket, zurueck auf das "
				+ "Standard-Theme.", profileId);
	}

	private boolean applyThemeFromPack(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			Manifest manifest = readManifestOrNull(archive, vpkgFile);
			String name = manifest != null ? manifest.name() : vpkgFile.getFileName().toString();

			if (manifest == null) {
				LOGGER.warn("Theme-Paket '{}': Manifest nicht lesbar -- kein Token-Wechsel.", name);
				return false;
			}

			// GATE103 (Charter-Regel 3, "Sicherheit vor Bequemlichkeit"): derselbe
			// Pruefsummen-/Signaturpfad wie Resource-Packs (GATE29) -- VOR jeder
			// anderen Pruefung, nicht nur die Zip-Sicherheit oben.
			if (!VitrinePackProvider.hasValidIntegrity(vpkgFile)) {
				LOGGER.warn("Theme-Paket '{}' hat die Pruefsummen-/Signaturpruefung nicht bestanden -- kein "
						+ "Token-Wechsel (GATE103, Charter-Regel 3).", name);
				return false;
			}

			// GATE32 (Charter-Regel 4, "Default ist Verbot"): ein Theme-Paket
			// bekommt den Token-Wechsel nur, wenn es die Faehigkeit "theme" im
			// Manifest angemeldet hat -- genau wie HudPackageLoader es fuer "hud"
			// tut. Fehlt sie (oder ist das Manifest gar nicht lesbar/ungueltig),
			// bleibt das Paket installiert, das Theme greift schlicht nicht.
			if (!hasThemeCapability(manifest)) {
				LOGGER.warn("Theme-Paket '{}' hat die Faehigkeit '{}' nicht im Manifest angemeldet -- kein "
						+ "Token-Wechsel (Charter-Regel 4).", name, VpkgCapability.THEME.id());
				return false;
			}

			// GATE105 (Charter-Regel 4, wie GATE60 fuer Resource-Packs): die im
			// Manifest angemeldete Faehigkeit allein reicht nicht -- der Nutzer
			// muss ihr im Faehigkeiten-Dialog tatsaechlich zugestimmt haben.
			if (!hasCapabilityConsent(vpkgFile, manifest)) {
				LOGGER.warn("Theme-Paket '{}' meldet Faehigkeiten an, denen nie zugestimmt wurde -- kein "
						+ "Token-Wechsel (GATE105, Charter-Regel 4).", name);
				return false;
			}

			if (!archive.hasEntry(THEME_ENTRY_NAME)) {
				LOGGER.warn("Theme-Paket '{}' enthaelt kein '{}' im Wurzelverzeichnis -- das Paket bleibt "
						+ "installiert, das Theme greift nicht.", name, THEME_ENTRY_NAME);
				return false;
			}
			byte[] themeJson = archive.readEntryBytes(THEME_ENTRY_NAME);
			// loadAndSwitch meldet ein kaputtes Theme selbst als Klartext und
			// laesst das bisherige Theme aktiv -- der Rueckgabewert steuert, ob
			// dieses Paket als "aktives Theme" gemerkt wird.
			return themeManager.loadAndSwitch(themeJson, name);
		} catch (IOException e) {
			LOGGER.warn("Theme aus '{}' liess sich nicht lesen: {} -- das Paket bleibt installiert, altes Theme "
					+ "aktiv.", vpkgFile, e.toString());
			return false;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Zip-Sicherheitspruefung fuer Theme-Paket '{}' fehlgeschlagen: {} -- kein Token-Wechsel.",
					vpkgFile, e.getMessage());
			return false;
		}
	}

	/** {@code true}, wenn {@code manifest} die Faehigkeit {@link VpkgCapability#THEME} angemeldet hat. */
	private static boolean hasThemeCapability(Manifest manifest) {
		try {
			return VpkgCapabilityGrant.of(manifest).isGranted(VpkgCapability.THEME);
		} catch (VpkgCapabilityException e) {
			LOGGER.warn("Theme-Paket '{}' meldet ungueltige Faehigkeiten an: {}", manifest.id(), e.getMessage());
			return false;
		}
	}

	/**
	 * GATE105: {@code true}, wenn der Nutzer den im Manifest angemeldeten
	 * Faehigkeiten von {@code manifest} bereits zugestimmt hat ({@link
	 * PackState#hasCapabilityConsent}) -- dieselbe Pruefung wie {@link
	 * VitrinePackProvider#hasCapabilityConsent} fuer Resource-Packs (GATE60).
	 */
	private boolean hasCapabilityConsent(Path vpkgFile, Manifest manifest) {
		List<String> capabilityIds;
		try {
			capabilityIds = PackInstaller.capabilityIdsOf(manifest);
		} catch (PackInstallException e) {
			LOGGER.warn("Theme-Paket '{}': Faehigkeiten nicht lesbar -- kein Token-Wechsel: {}",
					vpkgFile, e.getMessage());
			return false;
		}
		PackState state = PackStateStore.load(vitrineConfigDir);
		return state.hasCapabilityConsent(manifest.id(), capabilityIds);
	}

	private static Manifest readManifestOrNull(VpkgArchive archive, Path vpkgFile) {
		try {
			return ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException | ManifestParseException e) {
			LOGGER.debug("Manifest von '{}' nicht lesbar ({}).", vpkgFile, e.toString());
			return null;
		}
	}

	private static boolean isThemePackFile(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			return isThemePackage(ManifestParser.parse(archive.readManifestBytes()));
		} catch (IOException | ManifestParseException | RuntimeException e) {
			LOGGER.debug("Kategorie von '{}' nicht lesbar: {}", vpkgFile, e.toString());
			return false;
		}
	}
}
