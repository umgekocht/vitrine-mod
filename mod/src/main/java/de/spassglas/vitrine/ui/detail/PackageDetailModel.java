package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.catalog.github.GitHubRepoStats;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.ui.tile.TileData;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;
import de.spassglas.vitrine.vpkg.VpkgCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reines Datenmodell der Store-Detailseite (ROADMAP S3). Baut aus einem
 * bereits geparsten {@link Manifest} (P1) plus Laufzeit-Kontext
 * (Installationszustand, Vorgangsfortschritt, Dateigroesse, Changelog,
 * restlicher Katalog fuer "weitere Pakete des Autors") die Abschnitte der
 * Detailseite in der von {@code docs/UI-SPEC.md} ("5. Detailseite")
 * vorgegebenen Reihenfolge auf -- ohne eine einzige Minecraft-Klasse, damit
 * die Reihenfolge, die Faehigkeitszeilen und die Aktionsknopf-Ableitung ohne
 * laufenden Client beweisbar sind.
 *
 * <p>Das {@code .vpkg} wird hier <b>nicht</b> geoeffnet und schon gar nicht
 * angewendet -- das Manifest kommt fertig geparst herein (der Aufrufer hat
 * es ueber {@code VpkgArchive} + {@code VpkgSecurityValidator} +
 * {@code ManifestParser} gelesen, siehe {@code LocalPackageDetailSource}).</p>
 *
 * <p>Jeder Abschnitt aus {@link #sections()} ist immer vorhanden -- fehlt der
 * Inhalt (keine Beschreibung, keine Screenshots, keine weiteren Autor-Pakete,
 * kein Changelog), meldet das jeweilige {@code hasXxx()} das ehrlich, und die
 * Seite zeigt eine ruhige Leerzeile statt Blindtext.</p>
 */
public final class PackageDetailModel {

	/** Ab dieser Zeilenzahl wird die Beschreibung eingeklappt ("Mehr anzeigen", docs/UI-SPEC.md). */
	public static final int DESCRIPTION_COLLAPSE_LINES = 6;

	private static final List<PackageDetailSection> SECTION_ORDER = List.of(
			PackageDetailSection.HEADER,
			PackageDetailSection.ACTION_BUTTON,
			PackageDetailSection.SCREENSHOTS,
			PackageDetailSection.DESCRIPTION,
			PackageDetailSection.CAPABILITIES,
			PackageDetailSection.DETAILS,
			PackageDetailSection.CHANGELOG,
			PackageDetailSection.MORE_FROM_AUTHOR);

	private final String manifestId;
	private final PackageDetailHeader header;
	private final PackageActionState actionState;
	private final List<String> screenshotPaths;
	private final Optional<String> description;
	private final boolean descriptionNeedsCollapse;
	private final List<VpkgCapability> capabilities;
	/**
	 * GATE76: ob {@link #capabilities} echte Daten sind oder nur eine leere
	 * Liste, weil die Quelle sie (noch) nicht kennt -- siehe
	 * {@link #capabilitiesKnown()}.
	 */
	private final boolean capabilitiesKnown;
	private final List<PackageDetailFact> details;
	private final Optional<String> changelog;
	private final List<TileData> moreFromAuthor;
	/**
	 * GATE77: ob dieses Angebot Klasse B ist (ein {@code .jar}-Java-Mod, der
	 * einen Neustart braucht und Programmcode ausfuehrt) -- siehe
	 * {@link #runsNoProgramCode()}. {@code false} (Klasse A) ist der
	 * konservative Default fuer jeden Aufrufer, der die Unterscheidung nicht
	 * kennt, analog zu {@code IndexPackage#javaMod()}.
	 */
	private final boolean javaMod;
	/** GATE70: Autoren-Repo fuer die GitHub-Kennzahlen im Kopf, oder leer -- siehe {@link #repositoryUrl()}. */
	private final String repositoryUrl;
	/**
	 * GATE17: die {@code .vpkg}, aus der Icon und Screenshots stammen -- nur
	 * bei lokalen Paketen bekannt. Die Pfade in {@link #header()} und
	 * {@link #screenshotPaths()} sind Pfade <em>im</em> Paket, keine
	 * Ressourcen des Mods; ohne diese Datei laesst sich kein Bild laden.
	 */
	private Optional<java.nio.file.Path> sourceFile = Optional.empty();

	/** GATE17: dasselbe Modell, mit der Paketdatei, aus der die Bilder geladen werden. */
	public PackageDetailModel withSourceFile(java.nio.file.Path vpkgFile) {
		PackageDetailModel copy = new PackageDetailModel(manifestId, header, actionState, screenshotPaths,
				description, descriptionNeedsCollapse, capabilities, capabilitiesKnown, details, changelog,
				moreFromAuthor, repositoryUrl, javaMod);
		copy.sourceFile = Optional.of(Objects.requireNonNull(vpkgFile, "vpkgFile"));
		return copy;
	}

	/**
	 * GATE70: dasselbe Modell, mit frisch von der GitHub-API geholten (oder --
	 * bei Fehlschlag/keinem erkennbaren Repo -- weiterhin unbekannten)
	 * Kennzahlen im Kopf. {@code PackageDetailScreen} ruft das asynchron nach
	 * einem beendeten Hintergrund-Abruf auf (kein Aufruf vom Render-Thread).
	 */
	public PackageDetailModel withRepoStats(GitHubRepoStats stats) {
		PackageDetailModel copy = new PackageDetailModel(manifestId, header.withRepoStats(stats), actionState,
				screenshotPaths, description, descriptionNeedsCollapse, capabilities, capabilitiesKnown, details,
				changelog, moreFromAuthor, repositoryUrl, javaMod);
		copy.sourceFile = this.sourceFile;
		return copy;
	}

	/** GATE17: die Paketdatei fuer Icon/Screenshots, leer bei Katalog-Angeboten. */
	public Optional<java.nio.file.Path> sourceFile() {
		return sourceFile;
	}

	/**
	 * GATE70: das Autoren-Repo (Registry-Feld {@code repository} bzw.
	 * Manifest-Feld {@code homepage}), oder leer, wenn keines bekannt ist --
	 * dann startet {@code PackageDetailScreen} erst gar keinen GitHub-Abruf.
	 */
	public Optional<String> repositoryUrl() {
		return Optional.ofNullable(repositoryUrl);
	}

	private PackageDetailModel(String manifestId, PackageDetailHeader header, PackageActionState actionState,
			List<String> screenshotPaths, Optional<String> description, boolean descriptionNeedsCollapse,
			List<VpkgCapability> capabilities, boolean capabilitiesKnown, List<PackageDetailFact> details,
			Optional<String> changelog, List<TileData> moreFromAuthor, String repositoryUrl, boolean javaMod) {
		this.manifestId = manifestId;
		this.header = header;
		this.actionState = actionState;
		this.screenshotPaths = screenshotPaths;
		this.description = description;
		this.descriptionNeedsCollapse = descriptionNeedsCollapse;
		this.capabilities = capabilities;
		this.capabilitiesKnown = capabilitiesKnown;
		this.details = details;
		this.changelog = changelog;
		this.moreFromAuthor = moreFromAuthor;
		this.repositoryUrl = repositoryUrl;
		this.javaMod = javaMod;
	}

	/**
	 * Kurzform fuer Tests/Aufrufer ohne Laufzeit-Kontext: kein laufender
	 * Vorgang, unbekannte Groesse, kein Changelog, leerer Restkatalog.
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status) {
		return of(manifest, status, PackageInstallPhase.IDLE, -1L, null, List.of());
	}

	/**
	 * @param manifest   bereits gegen das Schema validiertes Manifest (P1)
	 * @param status     Installationszustand fuer Kopf-Statuszeichen und Aktionsknopf
	 * @param phase      Fortschritt eines echten Vorgangs (S4); in S3 immer {@link PackageInstallPhase#IDLE}
	 * @param sizeBytes  Groesse der {@code .vpkg}-Datei, oder ein negativer Wert fuer "unbekannt"
	 * @param changelog  Changelog-Text aus Manifest/Registry, oder {@code null}/leer -> "keine Angaben"
	 * @param catalog    alle bekannten Kachelangebote (fuer "weitere Pakete des Autors"); {@code null} erlaubt
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status, PackageInstallPhase phase,
			long sizeBytes, String changelog, List<TileData> catalog) {
		return of(manifest, status, phase, sizeBytes, changelog, catalog, null, null, List.of(), List.of());
	}

	/**
	 * GATE69: wie {@link #of(Manifest, TileStatus, PackageInstallPhase, long, String, List)}, mit den
	 * drei zusaetzlichen, nicht im {@link Manifest} selbst steckenden Registry-Angaben aus
	 * {@code docs/UI-SPEC.md} ("Details": "veröffentlicht am … Abhängigkeiten, Konflikte,
	 * Prüfsumme"). Jeder Aufrufer, der eine Angabe nicht kennt, uebergibt {@code null}/eine leere
	 * Liste -- die Seite zeigt dann ehrlich "keine Angaben" statt etwas zu erfinden.
	 *
	 * @param publishedAt  Veroeffentlichungszeitpunkt der angezeigten Version (ISO-8601), oder {@code null}
	 * @param sha256       SHA-256-Pruefsumme des Archivs, hex, oder {@code null} -- bekommt den Kopier-Knopf
	 * @param dependencies harte Abhaengigkeiten dieser Version, oder leer
	 * @param conflicts    bekannte Konflikte dieser Version, oder leer
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status, PackageInstallPhase phase,
			long sizeBytes, String changelog, List<TileData> catalog, String publishedAt, String sha256,
			List<String> dependencies, List<String> conflicts) {
		return of(manifest, status, phase, sizeBytes, changelog, catalog, publishedAt, sha256, dependencies,
				conflicts, null, List.of());
	}

	/**
	 * GATE70: wie {@link #of(Manifest, TileStatus, PackageInstallPhase, long, String, List, String, String, List, List)},
	 * mit den zwei zusaetzlichen Angaben, die der Kopfbereich zeigt (docs/UI-SPEC.md:118-120:
	 * "Sterne/Downloads aus der GitHub-API (read-only) … MC-Versionen"). Beide sind optional --
	 * fehlen sie, bleiben die GitHub-Kennzahlen dauerhaft {@link GitHubRepoStats#UNKNOWN} (kein
	 * Abruf ohne erkennbares Repo) bzw. zeigt der Kopf "keine Angaben" fuer die Minecraft-Versionen.
	 *
	 * @param repositoryUrl      https-URL des Autoren-Repos (Registry-Feld {@code repository} bzw.
	 *                           Manifest-Feld {@code homepage}), oder {@code null}
	 * @param minecraftVersions  freigegebene Minecraft-Versionen (nur bei Klasse-B-Angeboten ueberhaupt
	 *                           bekannt), oder leer
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status, PackageInstallPhase phase,
			long sizeBytes, String changelog, List<TileData> catalog, String publishedAt, String sha256,
			List<String> dependencies, List<String> conflicts, String repositoryUrl,
			List<String> minecraftVersions) {
		return of(manifest, status, phase, sizeBytes, changelog, catalog, publishedAt, sha256, dependencies,
				conflicts, repositoryUrl, minecraftVersions, true);
	}

	/**
	 * GATE76: wie {@link #of(Manifest, TileStatus, PackageInstallPhase, long, String, List, String, String, List, List, String, List)},
	 * mit der Unterscheidung zwischen "wirklich keine Faehigkeiten" und
	 * "unbekannt, wird beim Installieren geprueft" (Charter-Regel 2 --
	 * ROADMAP GATE76). {@code manifest.capabilities()} ist bei Katalog-Angeboten
	 * immer eine leere Liste, weil {@code registry-package.schema.json} das
	 * Feld gar nicht fuehrt -- echte Faehigkeiten sind dort erst nach dem
	 * Download der echten {@code vitrine.json} bekannt ({@code
	 * CatalogPackInstaller}). Nur eine Quelle, die ein echtes Manifest gelesen
	 * hat ({@code LocalPackageDetailSource}), darf {@code capabilitiesKnown}
	 * {@code true} uebergeben, wenn die Liste leer ist.
	 *
	 * @param capabilitiesKnown ob {@code manifest.capabilities()} echte Daten sind ({@code true},
	 *                          auch wenn leer) oder die Quelle Faehigkeiten schlicht nicht kennt
	 *                          ({@code false} -- die Seite zeigt dann "unbekannt" statt "keine")
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status, PackageInstallPhase phase,
			long sizeBytes, String changelog, List<TileData> catalog, String publishedAt, String sha256,
			List<String> dependencies, List<String> conflicts, String repositoryUrl,
			List<String> minecraftVersions, boolean capabilitiesKnown) {
		return of(manifest, status, phase, sizeBytes, changelog, catalog, publishedAt, sha256, dependencies,
				conflicts, repositoryUrl, minecraftVersions, capabilitiesKnown, false);
	}

	/**
	 * GATE77: wie {@link #of(Manifest, TileStatus, PackageInstallPhase, long, String, List, String, String, List, List, String, List, boolean)},
	 * mit der expliziten Klasse-A/Klasse-B-Angabe (Charter-Regel 2 -- ROADMAP
	 * GATE77). {@code manifest.category()} allein unterscheidet das nicht --
	 * die acht {@code TilePackageType}-Kategorien sind orthogonal zur
	 * Inhaltsklasse ({@code registry-package.schema.json}, Feld {@code
	 * contentClass}). Nur eine Quelle, die die Inhaltsklasse tatsaechlich kennt
	 * ({@code CatalogPackageDetailSource} ueber {@code
	 * RegistryPackageEntry#isJavaMod()}), darf {@code isJavaMod=true}
	 * uebergeben; jeder andere Aufrufer (lokale {@code .vpkg}-Installation --
	 * das Format kennt heute nur Klasse A) bleibt beim konservativen Default
	 * {@code false} der kuerzeren Overloads.
	 *
	 * @param isJavaMod ob dieses Angebot Klasse B ist (ein {@code .jar}, das
	 *                  Programmcode ausfuehrt und einen Neustart braucht) --
	 *                  bestimmt {@link #runsNoProgramCode()}
	 */
	public static PackageDetailModel of(Manifest manifest, TileStatus status, PackageInstallPhase phase,
			long sizeBytes, String changelog, List<TileData> catalog, String publishedAt, String sha256,
			List<String> dependencies, List<String> conflicts, String repositoryUrl,
			List<String> minecraftVersions, boolean capabilitiesKnown, boolean isJavaMod) {
		Objects.requireNonNull(manifest, "manifest");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(dependencies, "dependencies");
		Objects.requireNonNull(conflicts, "conflicts");

		TilePackageType type = TilePackageType.byId(manifest.category())
				.orElseThrow(() -> new IllegalArgumentException(
						"Manifest-Kategorie '" + manifest.category() + "' ist keine bekannte TilePackageType -- "
								+ "das Angebot haette LocalLibrarySource gar nicht als Kachel geliefert"));

		// GATE70: einmal berechnet, zweimal benutzt (Kopfbereich + "Details"-Zeile) --
		// docs/UI-SPEC.md nennt Groesse/Lizenz in beiden Abschnitten.
		Optional<String> sizeDisplay = HumanReadableSize.format(sizeBytes);
		Optional<String> licenseDisplay = blankToEmpty(manifest.license());
		List<String> mcVersions = minecraftVersions == null ? List.of() : minecraftVersions;
		Optional<String> minecraftVersionsDisplay =
				mcVersions.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", mcVersions));

		PackageDetailHeader header = new PackageDetailHeader(manifest.name(), manifest.author(), type, status,
				blankToEmpty(manifest.icon()), manifest.version(), licenseDisplay, sizeDisplay,
				minecraftVersionsDisplay, GitHubRepoStats.UNKNOWN);

		PackageActionState actionState = PackageActionState.derive(status, phase);

		List<String> screenshots = List.copyOf(manifest.screenshots());

		Optional<String> description = blankToEmpty(manifest.description());
		boolean needsCollapse = description.map(PackageDetailModel::countLines)
				.map(lines -> lines > DESCRIPTION_COLLAPSE_LINES)
				.orElse(false);

		List<VpkgCapability> capabilities = resolveCapabilities(manifest.capabilities());

		// GATE69: alle sieben UI-SPEC-Felder, in der dort vorgegebenen Reihenfolge --
		// "Homepage" stand nicht in dieser Liste (docs/UI-SPEC.md:133-134) und ist
		// deshalb entfernt, nicht nur ergaenzt.
		List<PackageDetailFact> details = List.of(
				PackageDetailFact.of("vitrine.detail.field.version", manifest.version()),
				PackageDetailFact.of("vitrine.detail.field.size", sizeDisplay),
				PackageDetailFact.of("vitrine.detail.field.published_at", publishedAt),
				PackageDetailFact.of("vitrine.detail.field.license", licenseDisplay),
				PackageDetailFact.of("vitrine.detail.field.dependencies", joinOrNull(dependencies)),
				PackageDetailFact.of("vitrine.detail.field.conflicts", joinOrNull(conflicts)),
				PackageDetailFact.of("vitrine.detail.field.checksum", sha256, true));

		Optional<String> changelogText = blankToEmpty(changelog);

		List<TileData> more = filterByAuthor(catalog, manifest.author(), manifest.id());

		return new PackageDetailModel(manifest.id(), header, actionState, screenshots, description, needsCollapse,
				capabilities, capabilitiesKnown, details, changelogText, more,
				blankToEmpty(repositoryUrl).orElse(null), isJavaMod);
	}

	private static String joinOrNull(List<String> values) {
		return values == null || values.isEmpty() ? null : String.join(", ", values);
	}

	/** Manifest-ID des Pakets ({@code vitrine.json}, {@code id}) -- Schlüssel für Installation und Fähigkeiten-Zustimmung (S4). */
	public String manifestId() {
		return manifestId;
	}

	/** Alle Abschnitte, immer vollstaendig und in Spec-Reihenfolge (docs/UI-SPEC.md "5. Detailseite"). */
	public List<PackageDetailSection> sections() {
		return SECTION_ORDER;
	}

	public PackageDetailHeader header() {
		return header;
	}

	/** Zustand des einen gefuellten Aktionsknopfs, abgeleitet aus {@code status} und {@code phase}. */
	public PackageActionState actionState() {
		return actionState;
	}

	/** Screenshot-Pfade innerhalb des {@code .vpkg} ({@code manifest.screenshots()}), evtl. leer. */
	public List<String> screenshotPaths() {
		return screenshotPaths;
	}

	public boolean hasScreenshots() {
		return !screenshotPaths.isEmpty();
	}

	/** {@code manifest.description()}, oder leer. */
	public Optional<String> description() {
		return description;
	}

	public boolean hasDescription() {
		return description.isPresent();
	}

	/** Ob die Beschreibung laenger als {@link #DESCRIPTION_COLLAPSE_LINES} Zeilen ist und "Mehr anzeigen" bekommt. */
	public boolean descriptionNeedsCollapse() {
		return descriptionNeedsCollapse;
	}

	/** Die im Manifest angemeldeten Faehigkeiten, in Manifest-Reihenfolge (docs/UI-SPEC.md "Das darf dieses Paket"). */
	public List<VpkgCapability> capabilities() {
		return capabilities;
	}

	/**
	 * Ehrlicher Default-Verbot-Hinweis: das Paket hat gar keine Faehigkeit
	 * angemeldet (Charter-Regel 4) -- und das ist auch tatsaechlich bekannt,
	 * nicht nur eine leere Liste mangels Daten (GATE76, siehe
	 * {@link #capabilitiesKnown()}). Ein Katalog-Angebot ohne Capability-Daten
	 * liefert hier {@code false}, auch wenn {@link #capabilities()} leer ist.
	 */
	public boolean declaresNoCapabilities() {
		return capabilitiesKnown && capabilities.isEmpty();
	}

	/**
	 * GATE76: ob {@link #capabilities()} echte Daten sind. {@code false} nur
	 * bei Katalog-Angeboten, deren Registry-Eintrag kein {@code capabilities}-
	 * Feld fuehrt -- die echten Faehigkeiten sind dort erst nach dem Download
	 * bekannt ({@code CatalogPackInstaller}). Die Seite zeigt fuer diesen Fall
	 * "unbekannt, wird beim Installieren geprueft" statt der falschen
	 * Verneinung "fordert keine Faehigkeiten an".
	 */
	public boolean capabilitiesKnown() {
		return capabilitiesKnown;
	}

	/**
	 * Klasse-A-Zusatz aus docs/UI-SPEC.md: "Bei Klasse A zusätzlich der Satz:
	 * „Führt keinen Programmcode aus."" GATE77: {@code false} fuer ein
	 * Klasse-B-Angebot ({@code .jar}-Java-Mod, ROADMAP J1) -- so ein Paket
	 * fuehrt tatsaechlich Programmcode aus und braucht einen Neustart; die
	 * Seite zeigt fuer Klasse B nur die ehrliche "Neustart nötig"-Warnung,
	 * nie die Code-Verneinung daneben (Charter-Regel 2).
	 */
	public boolean runsNoProgramCode() {
		return !javaMod;
	}

	/**
	 * Zeilen des "Details"-Abschnitts in UI-SPEC-Reihenfolge: Version, Groesse,
	 * veroeffentlicht am, Lizenz, Abhaengigkeiten, Konflikte, Pruefsumme (GATE69).
	 */
	public List<PackageDetailFact> details() {
		return details;
	}

	/** GATE69: ob die Pruefsumme bekannt ist und damit einen Kopier-Knopf bekommt. */
	public boolean hasCopyableChecksum() {
		for (PackageDetailFact fact : details) {
			if (fact.copyable() && fact.isKnown()) {
				return true;
			}
		}
		return false;
	}

	/** Changelog-Text, oder leer -> die Seite zeigt "keine Angaben". */
	public Optional<String> changelog() {
		return changelog;
	}

	public boolean hasChangelog() {
		return changelog.isPresent();
	}

	/** Weitere Angebote desselben Autors (ohne dieses Paket selbst), evtl. leer. */
	public List<TileData> moreFromAuthor() {
		return moreFromAuthor;
	}

	public boolean hasMoreFromAuthor() {
		return !moreFromAuthor.isEmpty();
	}

	private static Optional<String> blankToEmpty(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	private static int countLines(String text) {
		int lines = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines;
	}

	/**
	 * Bildet die rohen Manifest-Bezeichner auf {@link VpkgCapability} ab,
	 * Reihenfolge bleibt erhalten. Ein unbekannter Bezeichner wird hier nur
	 * uebersprungen -- ein Angebot mit unbekannter Faehigkeit haette
	 * {@code LocalLibrarySource}/{@code VpkgCapabilityGrant} schon vor dem
	 * Bau dieses Modells abgelehnt.
	 */
	private static List<VpkgCapability> resolveCapabilities(List<String> ids) {
		List<VpkgCapability> resolved = new ArrayList<>(ids.size());
		for (String id : ids) {
			VpkgCapability.byId(id).ifPresent(resolved::add);
		}
		return List.copyOf(resolved);
	}

	private static List<TileData> filterByAuthor(List<TileData> catalog, String author, String selfId) {
		if (catalog == null || catalog.isEmpty()) {
			return List.of();
		}
		List<TileData> matches = new ArrayList<>();
		for (TileData tile : catalog) {
			if (tile.author().equals(author) && !tile.id().equals(selfId)) {
				matches.add(tile);
			}
		}
		return List.copyOf(matches);
	}
}
