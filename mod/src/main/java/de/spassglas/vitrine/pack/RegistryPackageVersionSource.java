package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.RegistryPackageClient;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;
import de.spassglas.vitrine.catalog.RegistryPackageVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Produktions-{@link PackUpdater.PackageVersionSource} (ROADMAP K5, jetzt
 * verdrahtet ueber den im Rahmen von G3 gebauten {@link RegistryPackageClient}):
 * holt {@code packages/&lt;id&gt;.json}, validiert es gegen die eingebettete
 * Schema-Kopie und liefert den zu {@code latestVersion} passenden {@code
 * versions[]}-Eintrag als {@link PackUpdater.PackageVersion}.
 *
 * <p>Ist der Katalog nicht erreichbar/defekt oder fuehrt er {@code manifestId}
 * nicht (mehr), liefert {@link #latestFor} ehrlich {@link Optional#empty()} --
 * {@link PackUpdater} behandelt das bereits als "kein Update" bzw. "kein
 * Katalog-Eintrag", kein Sonderfall noetig.</p>
 */
public final class RegistryPackageVersionSource implements PackUpdater.PackageVersionSource {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Updates");

	private final RegistryPackageClient client;

	public RegistryPackageVersionSource(RegistryPackageClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	/** Produktions-Verdrahtung: derselbe Katalog-Host/-Cache wie {@code CatalogClient}. */
	public static RegistryPackageVersionSource forConfigDir(Path vitrineConfigDir) {
		return new RegistryPackageVersionSource(RegistryPackageClient.production(vitrineConfigDir));
	}

	@Override
	public Optional<PackUpdater.PackageVersion> latestFor(String manifestId) {
		Objects.requireNonNull(manifestId, "manifestId");
		CatalogResult<RegistryPackageEntry> result = client.fetchPackage(manifestId);
		if (!result.isOnline()) {
			LOGGER.info("Update-Pruefung fuer '{}': Registry-Eintrag nicht verfuegbar ({}): {}", manifestId,
					result.availability(), result.message().orElse("keine Meldung"));
			return Optional.empty();
		}
		RegistryPackageEntry entry = result.value().orElseThrow();
		Optional<RegistryPackageVersion> latest = entry.latestVersionEntry();
		if (latest.isEmpty()) {
			LOGGER.warn("Update-Pruefung fuer '{}': latestVersion '{}' hat keinen passenden versions[]-Eintrag.",
					manifestId, entry.latestVersion());
			return Optional.empty();
		}
		RegistryPackageVersion version = latest.get();
		return Optional.of(new PackUpdater.PackageVersion(version.version(), version.vpkgUrl(), version.sha256Hex(),
				version.signatureBase64(), version.size()));
	}
}
