package de.spassglas.vitrine.hud;

import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liest die {@code hud/*.json}-Layouts aus einem {@code .vpkg}-Archiv und
 * baut daraus ein {@link HudPackage} (ROADMAP T5).
 *
 * <p><b>Sicherheit vor Bequemlichkeit (Charter-Regel 3/4):</b> Bevor auch nur
 * ein einziger {@code hud/*.json}-Eintrag gelesen wird, setzt diese Klasse
 * zwei Glieder der M1-Kette selbst durch:</p>
 * <ol>
 *   <li>{@link VpkgSecurityValidator#requireSafe(VpkgArchive)} -- Zip-Slip,
 *       absolute Pfade, {@code ..}, Symlinks, Zip-Bombe, verbotene Dateitypen,
 *       PNG-Maße.</li>
 *   <li>{@link VpkgCapabilityGrant#requireGranted(VpkgCapability)} mit
 *       {@link VpkgCapability#HUD} -- ein Paket ohne angemeldete
 *       {@code hud}-Fähigkeit bekommt die HUD-Laufzeit nicht zu sehen.</li>
 * </ol>
 *
 * <p>Prüfsumme und Signatur (P4) hängen an der heruntergeladenen Datei und
 * ihrem Index-Eintrag, nicht an einem bereits geöffneten Archiv -- sie sind,
 * wie an den übrigen Aufrufstellen auch, Sache des Download-/Aktivierungswegs
 * (K3/H2) und dort schon passiert, wenn ein Paket "aktiv" ist. Ein
 * {@link HudPackage} landet nur über diesen Weg in der
 * {@link ActiveHudRegistry}, aus der der Renderer zeichnet.</p>
 */
public final class HudPackageLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	/** Ordnerpräfix im {@code .vpkg}, unter dem HUD-Layouts liegen (siehe PLAN.md §2). */
	public static final String HUD_ENTRY_PREFIX = "hud/";

	private HudPackageLoader() {
	}

	/**
	 * @param archive   ein geöffnetes {@code .vpkg}-Archiv (P2)
	 * @param grant     die aus dem Manifest gebaute Fähigkeiten-Whitelist (P5)
	 * @throws VpkgSecurityException   wenn das Archiv die Zip-Sicherheitsprüfung nicht besteht
	 * @throws VpkgCapabilityException wenn das Paket {@code hud} nicht angemeldet hat
	 * @throws HudParseException       wenn ein {@code hud/*.json} kein gültiges JSON ist,
	 *                                 das Schema verletzt oder doppelte Widget-Ids hat
	 * @throws IOException             bei einem I/O-Fehler beim Lesen eines Eintrags
	 */
	public static HudPackage load(VpkgArchive archive, VpkgCapabilityGrant grant)
			throws VpkgSecurityException, VpkgCapabilityException, HudParseException, IOException {
		VpkgSecurityValidator.requireSafe(archive);
		grant.requireGranted(VpkgCapability.HUD);

		List<String> layoutEntryNames = new ArrayList<>();
		for (VpkgEntry entry : archive.entries()) {
			if (isHudLayoutEntry(entry)) {
				layoutEntryNames.add(entry.name());
			}
		}
		layoutEntryNames.sort(String::compareTo);

		List<HudPackage.Layout> layouts = new ArrayList<>(layoutEntryNames.size());
		for (String entryName : layoutEntryNames) {
			byte[] json = archive.readEntryBytes(entryName);
			try {
				layouts.add(new HudPackage.Layout(entryName, HudLayoutParser.parse(json)));
			} catch (HudParseException e) {
				throw new HudParseException("HUD-Paket '" + grant.packageId() + "', Eintrag '" + entryName + "': "
						+ e.getMessage(), e.violations());
			}
		}

		LOGGER.info("HUD-Paket '{}' geladen: {} Layout(s), {} Widget(s).", grant.packageId(), layouts.size(),
				layouts.stream().mapToInt(layout -> layout.layout().widgets().size()).sum());
		return new HudPackage(grant.packageId(), layouts);
	}

	private static boolean isHudLayoutEntry(VpkgEntry entry) {
		if (entry.directory()) {
			return false;
		}
		String name = entry.name();
		return name.startsWith(HUD_ENTRY_PREFIX)
				&& name.toLowerCase(Locale.ROOT).endsWith(".json")
				&& name.indexOf('/', HUD_ENTRY_PREFIX.length()) < 0;
	}
}
