package de.spassglas.vitrine.addon.script;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Laedt die Addon-Skripte ({@code scripts/*.vs}) eines einzelnen Pakets
 * (ROADMAP AD2) -- dieselbe Sicherheitskette wie
 * {@link de.spassglas.vitrine.addon.AddonRules#load} fuer {@code regeln/*.json}:
 * Pruefsumme/Signatur ({@link VitrinePackProvider#hasValidIntegrity}),
 * Zip-Sicherheit ({@link VpkgSecurityValidator#requireSafe}), angemeldete
 * <em>und</em> zugestimmte Faehigkeit {@link VpkgCapability#SKRIPT}. Scheitert
 * irgendeine dieser Pruefungen oder eine einzelne {@code .vs}-Datei
 * ({@link ScriptAddonParseException}), bleibt das <em>ganze</em> Paket ohne
 * Skripte (kein Teilerfolg) -- geloggt, nie ein Absturz.
 */
public final class ScriptAddonLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Script");

	/** Ordnerpraefix fuer Addon-Skripte in einem {@code .vpkg}, analog zu {@code AddonRules#RULES_PREFIX}. */
	public static final String SCRIPTS_PREFIX = "scripts/";

	public static final String SCRIPT_EXTENSION = ".vs";

	private ScriptAddonLoader() {
	}

	/** Laedt die Skripte des Pakets {@code packId} ueber den echten Client-Paketordner. */
	public static Optional<List<ScriptAddon>> load(String packId) {
		return load(VitrinePackProvider.clientInstance(), packId);
	}

	/**
	 * Wie {@link #load(String)}, mit injizierbarem {@link VitrinePackProvider}
	 * -- oeffentlich, damit sowohl die Client-Laufzeit
	 * ({@code de.spassglas.vitrine.addon.script.client.ScriptAddons}, anderes
	 * Package) als auch Tests dieselbe Pruefsummen-/Signatur- und
	 * Zustimmungspruefung ohne laufenden Fabric-Loader durchspielen koennen
	 * (siehe {@code de.spassglas.vitrine.hud.client.HudActivation#activate}).
	 */
	public static Optional<List<ScriptAddon>> load(VitrinePackProvider provider, String packId) {
		Optional<Path> file = provider.findPackFile(packId);
		if (file.isEmpty() || !VitrinePackProvider.hasValidIntegrity(file.get())) {
			return Optional.empty();
		}
		try (VpkgArchive archive = VpkgArchive.open(file.get())) {
			List<String> names = new ArrayList<>();
			for (VpkgEntry entry : archive.entries()) {
				String n = entry.name();
				if (!entry.directory() && n.startsWith(SCRIPTS_PREFIX)
						&& n.toLowerCase(Locale.ROOT).endsWith(SCRIPT_EXTENSION)
						&& n.indexOf('/', SCRIPTS_PREFIX.length()) < 0) {
					names.add(n);
				}
			}
			if (names.isEmpty()) {
				return Optional.empty();
			}
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			VpkgSecurityValidator.requireSafe(archive);
			VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(manifest);
			grant.requireGranted(VpkgCapability.SKRIPT);
			if (!provider.hasCapabilityConsent(file.get(), manifest)) {
				LOGGER.warn("Addon '{}': Faehigkeit 'skript' nie zugestimmt -- Skripte bleiben aus.", packId);
				return Optional.empty();
			}
			names.sort(String::compareTo);
			List<ScriptAddon> scripts = new ArrayList<>();
			for (String n : names) {
				scripts.add(ScriptAddonParser.parse(archive.readEntryBytes(n)));
			}
			return Optional.of(List.copyOf(scripts));
		} catch (Exception e) {
			LOGGER.warn("Addon '{}': Skripte nicht geladen: {}", packId, e.getMessage());
			return Optional.empty();
		}
	}
}
