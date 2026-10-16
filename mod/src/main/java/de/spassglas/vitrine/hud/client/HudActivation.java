package de.spassglas.vitrine.hud.client;

import de.spassglas.vitrine.hud.ActiveHudRegistry;
import de.spassglas.vitrine.hud.HudPackage;
import de.spassglas.vitrine.hud.HudPackageLoader;
import de.spassglas.vitrine.hud.HudParseException;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Der Aktivierungsweg fuer HUD-Pakete (ROADMAP T5, Punkt "Aktivierungsweg"):
 * findet das {@code .vpkg} zu einer Paket-Id im Vitrine-Paketordner (ueber
 * {@link VitrinePackProvider#findPackFile}, H5), setzt ueber
 * {@link HudPackageLoader} die M1-Kette durch (Zip-Sicherheit, angemeldete
 * {@code hud}-Faehigkeit) und traegt das geladene {@link HudPackage} in die
 * {@link ActiveHudRegistry} ein, aus der der {@link HudWidgetRenderer}
 * zeichnet. {@link #deactivate(String)} nimmt es wieder heraus.
 *
 * <p>H2/H5 ({@code HotApplyCoordinator}, {@code PackUninstaller}) arbeiten
 * ausschliesslich ueber Ressourcen-Pack-Profile und haben keinen Haken, an
 * dem ein {@code VpkgArchive} + Faehigkeiten-Whitelist bereitstuende --
 * deshalb ist der verdrahtete Einstieg vorerst der Brigadier-Unterbefehl
 * {@code /vitrine hud <packId>} ({@code HudCommand}), analog zu
 * {@code /vitrine hotapply}. Eine spaetere Store-Oberflaeche (S4) ruft
 * dieselben zwei Methoden auf.</p>
 *
 * <p><b>GATE104 (Charter-Regel 3, "Sicherheit vor Bequemlichkeit"):</b> vor
 * dem Laden irgendeines HUD-Layouts prueft {@link #activate(String)} ueber
 * {@link VitrinePackProvider#hasValidIntegrity} die SHA-256-Pruefsumme und
 * Ed25519-Signatur -- derselbe Pfad wie Resource-Packs (GATE29), <em>nicht</em>
 * ein zweiter, schwaecherer --, und ueber
 * {@link VitrinePackProvider#hasCapabilityConsent} (wie GATE60), dass der
 * Nutzer der angemeldeten {@code hud}-Faehigkeit im Faehigkeiten-Dialog auch
 * tatsaechlich zugestimmt hat.</p>
 */
public final class HudActivation {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	private HudActivation() {
	}

	/** Laedt und aktiviert das HUD-Paket mit dieser Profil-Id ({@code vitrine/...}). Loggt jede Ablehnung. */
	public static void activate(String packId) {
		activate(VitrinePackProvider.clientInstance(), packId);
	}

	/**
	 * Wie {@link #activate(String)}, mit injizierbarem {@link
	 * VitrinePackProvider} -- package-sichtbar, damit Tests die Pruefsummen-/
	 * Signatur- und Zustimmungspruefung (GATE104) ohne laufenden Fabric-Loader
	 * durchspielen koennen.
	 */
	static void activate(VitrinePackProvider provider, String packId) {
		Optional<Path> file = provider.findPackFile(packId);
		if (file.isEmpty()) {
			LOGGER.warn("HUD-Aktivierung: kein gueltiges Vitrine-Paket mit der Id '{}' im Paketordner gefunden.", packId);
			return;
		}
		Path vpkgFile = file.get();

		// GATE104: Pruefsumme + Signatur vor jedem anderen Schritt -- derselbe
		// Pfad wie Resource-Packs (GATE29), nicht nur die Zip-Sicherheit, die
		// HudPackageLoader weiter unten noch zusaetzlich durchsetzt.
		if (!VitrinePackProvider.hasValidIntegrity(vpkgFile)) {
			LOGGER.warn("HUD-Aktivierung von '{}' abgelehnt: Pruefsummen-/Signaturpruefung fehlgeschlagen "
					+ "(GATE104, Charter-Regel 3).", packId);
			return;
		}

		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());

			// GATE104 (wie GATE60 fuer Resource-Packs, Charter-Regel 4): die im
			// Manifest angemeldete Faehigkeit allein reicht nicht -- der Nutzer
			// muss ihr im Faehigkeiten-Dialog tatsaechlich zugestimmt haben.
			if (!provider.hasCapabilityConsent(vpkgFile, manifest)) {
				LOGGER.warn("HUD-Aktivierung von '{}' abgelehnt: Faehigkeiten nie zugestimmt (GATE104, "
						+ "Charter-Regel 4).", packId);
				return;
			}

			VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(manifest);
			HudPackage hudPackage = HudPackageLoader.load(archive, grant);
			if (hudPackage.layouts().isEmpty()) {
				LOGGER.info("HUD-Aktivierung: Paket '{}' enthaelt kein hud/*.json -- nichts zu aktivieren.", packId);
				return;
			}
			ActiveHudRegistry.instance().activate(hudPackage);
		} catch (IOException | VpkgSecurityException | VpkgCapabilityException | ManifestParseException
				| HudParseException e) {
			LOGGER.warn("HUD-Aktivierung von '{}' abgelehnt: {}", packId, e.getMessage());
		}
	}

	/** Nimmt das HUD-Paket mit dieser Profil-Id wieder aus der {@link ActiveHudRegistry} (Deaktivieren/Deinstallieren). */
	public static void deactivate(String packId) {
		Optional<Path> file = VitrinePackProvider.clientInstance().findPackFile(packId);
		if (file.isPresent()) {
			try (VpkgArchive archive = VpkgArchive.open(file.get())) {
				Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
				ActiveHudRegistry.instance().deactivate(manifest.id());
				return;
			} catch (IOException | ManifestParseException e) {
				LOGGER.debug("HUD-Deaktivierung von '{}': Manifest-Id nicht lesbar ({}), versuche Id direkt.",
						packId, e.getMessage());
			}
		}
		// Rueckfall: Registry ist auf die Manifest-Id verschluesselt; wenn die
		// Datei schon weg ist (Deinstallieren), versuchen wir es mit dem
		// unpraefixierten Rest der Profil-Id.
		ActiveHudRegistry.instance().deactivate(stripProfilePrefix(packId));
		ActiveHudRegistry.instance().deactivate(packId);
	}

	private static String stripProfilePrefix(String packId) {
		int slash = packId.indexOf('/');
		return slash >= 0 && slash + 1 < packId.length() ? packId.substring(slash + 1) : packId;
	}
}
