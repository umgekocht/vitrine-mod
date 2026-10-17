package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.catalog.CatalogResult;
import de.spassglas.vitrine.catalog.RegistryJavaModVersion;
import de.spassglas.vitrine.catalog.RegistryPackageClient;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;
import de.spassglas.vitrine.catalog.RegistryPackageVersion;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Laedt das {@link PackageDetailModel} fuer ein Angebot, das nur als
 * Katalog-Eintrag bekannt ist -- NICHT in {@code config/vitrine/library/}
 * (ROADMAP G3). Schwester von {@link LocalPackageDetailSource}, die stattdessen
 * eine lokale {@code .vpkg}-Datei liest.
 *
 * <p>Holt den vollen Registry-Paketeintrag ({@code packages/&lt;id&gt;.json},
 * {@link RegistryPackageClient}) und baut daraus ein synthetisches {@link
 * Manifest} fuer die Anzeige. {@code screenshots} ist dabei ehrlich leer, weil
 * das Registry-Schema das Feld nicht traegt (liegt im {@code .vpkg} selbst) --
 * die Detailseite zeigt dafuer ihren regulaeren "keine Angaben"-Zustand.
 * {@code capabilities} fuehrt die Registry seit GATE90-FOLLOW
 * (registry-package.schema.json) tatsaechlich: nennt der Paketeintrag welche,
 * uebernimmt dieses Modell sie 1:1 und meldet {@code capabilitiesKnown=true},
 * damit {@link de.spassglas.vitrine.pack.CatalogPackInstaller} den
 * Faehigkeiten-Dialog schon vor dem Download zeigen kann (docs/UI-SPEC.md
 * Abschnitt 8). Fehlt das Feld (aeltere Eintraege), bleibt {@code
 * capabilitiesKnown=false} -- dieselbe Unterscheidung "unbekannt" vs. "wirklich
 * leer" wie schon fuer lokale Pakete (GATE76), nur eben jetzt auch fuer
 * Katalog-Angebote erreichbar. Die endgueltige, bindende Pruefung bleibt in
 * jedem Fall die echte {@code vitrine.json} nach dem Download
 * (Charter-Regel 4).</p>
 */
public final class CatalogPackageDetailSource {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/CatalogPackageDetail");

	private final RegistryPackageClient client;

	private CatalogPackageDetailSource(RegistryPackageClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	/** Quelle fuer den echten Katalog ueber {@code vitrineConfigDir} (ETag-Cache, Produktions-Host). */
	public static CatalogPackageDetailSource forConfigDir(Path vitrineConfigDir) {
		return new CatalogPackageDetailSource(RegistryPackageClient.production(vitrineConfigDir));
	}

	/** Quelle fuer einen beliebigen (z. B. {@code file://}-gestuetzten) Client -- fuer Tests. */
	public static CatalogPackageDetailSource forTesting(RegistryPackageClient client) {
		return new CatalogPackageDetailSource(client);
	}

	/**
	 * @param packageId Manifest-Id des gesuchten Katalog-Angebots
	 * @param status    Installationszustand der Kachel (fuer Kopf-Statuszeichen und Aktionsknopf)
	 * @param catalog   alle bekannten Kachelangebote fuer "weitere Pakete des Autors" ({@code null} erlaubt)
	 * @return das Modell, oder leer, wenn der Katalog nicht erreichbar/defekt ist oder das Schema
	 *         inkonsistent ist ({@code latestVersion} ohne passenden Eintrag in {@code versions[]}
	 *         bzw. {@code javaModVersions[]})
	 */
	public Optional<PackageDetailModel> load(String packageId, TileStatus status, List<TileData> catalog) {
		Objects.requireNonNull(packageId, "packageId");
		Objects.requireNonNull(status, "status");

		CatalogResult<RegistryPackageEntry> result = client.fetchPackage(packageId);
		if (!result.isOnline()) {
			LOGGER.warn("Katalog-Paketeintrag '{}' liess sich nicht laden ({}): {}", packageId, result.availability(),
					result.message().orElse("keine Meldung"));
			return Optional.empty();
		}
		RegistryPackageEntry entry = result.value().orElseThrow();

		Manifest manifest = new Manifest(1, entry.id(), entry.name(), entry.latestVersion(), entry.category(),
				entry.authorName(), entry.description(), entry.capabilities(), null, List.of(), entry.homepage(),
				entry.license());
		// GATE90-FOLLOW: nur eine Quelle, die den Registry-Eintrag tatsaechlich
		// mit angemeldeten Faehigkeiten gelesen hat, darf capabilitiesKnown=true
		// melden (dasselbe Muster wie GATE76 bei LocalPackageDetailSource) --
		// fehlt das optionale 'capabilities'-Feld (aeltere Eintraege), bleibt die
		// Liste leer und die Angabe ehrlich "unbekannt", statt eine
		// faelschliche Verneinung zu zeigen.
		boolean capabilitiesKnown = !entry.capabilities().isEmpty();

		// GATE70: "repository" ist im Registry-Schema Pflicht (im Gegensatz zum
		// optionalen Manifest-Feld "homepage") -- das Autoren-Repo fuer die
		// GitHub-Kennzahlen im Kopf ist bei einem Katalog-Angebot also immer
		// bekannt (muss aber trotzdem erst GitHubRepositoryReference.parse
		// bestehen, z. B. echtes github.com, bevor ein Abruf stattfindet).
		// GATE77: Klasse B (contentClass "javamod") hat kein 'versions[]' --
		// die Versionsdaten stecken in 'javaModVersions[]'
		// ($defs/javamodVersionItem). isJavaMod=true sorgt dafuer, dass die
		// Detailseite nie "Führt keinen Programmcode aus." neben die
		// "Neustart nötig"-Warnung stellt (Charter-Regel 2).
		if (entry.isJavaMod()) {
			Optional<RegistryJavaModVersion> latest = entry.latestJavaModVersion();
			if (latest.isEmpty()) {
				LOGGER.warn("Katalog-Paketeintrag '{}': latestVersion '{}' hat keinen passenden Eintrag in "
						+ "javaModVersions[].", packageId, entry.latestVersion());
				return Optional.empty();
			}
			RegistryJavaModVersion version = latest.get();
			return Optional.of(PackageDetailModel.of(manifest, status, PackageInstallPhase.IDLE, version.size(),
					null, catalog == null ? List.of() : catalog, version.publishedAt(), version.sha256Hex(),
					version.dependencies(), List.of(), entry.repository(), entry.minecraftVersions(),
					capabilitiesKnown, true));
		}

		// GATE69: publishedAt/sha256 stammen aus dem Registry-Versions-Eintrag
		// (registry-package.schema.json $defs/resourceVersionItem). Ein
		// "dependencies"/"conflicts"-Feld gibt es fuer Klasse-A-Versionen (anders
		// als fuer Klasse B, $defs/javamodVersionItem) im Schema nicht -- hier
		// ehrlich leer lassen statt etwas zu erfinden (Charter-Regel 2).
		Optional<RegistryPackageVersion> latest = entry.latestVersionEntry();
		if (latest.isEmpty()) {
			LOGGER.warn("Katalog-Paketeintrag '{}': latestVersion '{}' hat keinen passenden Eintrag in versions[].",
					packageId, entry.latestVersion());
			return Optional.empty();
		}
		RegistryPackageVersion version = latest.get();
		return Optional.of(PackageDetailModel.of(manifest, status, PackageInstallPhase.IDLE, version.size(),
				null, catalog == null ? List.of() : catalog, version.publishedAt(), version.sha256Hex(),
				List.of(), List.of(), entry.repository(), entry.minecraftVersions(), capabilitiesKnown, false));
	}
}
