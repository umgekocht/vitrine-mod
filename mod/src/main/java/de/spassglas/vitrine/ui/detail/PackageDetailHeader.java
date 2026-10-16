package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.catalog.github.GitHubRepoStats;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import de.spassglas.vitrine.ui.tile.TileStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Der Kopfbereich der Store-Detailseite (docs/UI-SPEC.md "5. Detailseite":
 * "großes Bild links (16:9), rechts Name (title.large), Autor als Link,
 * Typ-Badge, Sterne/Downloads aus der GitHub-API (read-only), Größe, Lizenz,
 * Version, MC-Versionen.").
 *
 * @param name              Anzeigename ({@code manifest.name()}), paket-eigener Freitext
 * @param author            Autor/Handle ({@code manifest.author()}), paket-eigener Freitext
 * @param type              Typ-Badge, abgeleitet aus {@code manifest.category()}
 * @param status            Installationszustand fuer das Statuszeichen im Kopf
 * @param iconPath          relativer Icon-Pfad im {@code .vpkg} ({@code manifest.icon()}),
 *                          oder leer -- dann zeichnet die Seite den Typ-Platzhalter
 * @param version           Paketversion ({@code manifest.version()}), Pflichtfeld im Manifest
 * @param license           Lizenz-Kurzbezeichner ({@code manifest.license()}), oder leer -&gt; "keine Angaben"
 * @param size              menschenlesbare Dateigroesse ({@link HumanReadableSize}), oder leer -&gt; "keine Angaben"
 * @param minecraftVersions freigegebene Minecraft-Versionen (nur bei Klasse-B-Angeboten ueberhaupt
 *                          bekannt -- Klasse A ist versionsunabhaengig), zusammengefasst, oder leer
 * @param repoStats         Sterne/Downloads des Autoren-Repos (ROADMAP GATE70), anfangs immer
 *                          {@link GitHubRepoStats#UNKNOWN} -- erst nach dem asynchronen GitHub-Abruf
 *                          ueber {@link #withRepoStats} echt befuellt
 */
public record PackageDetailHeader(
		String name,
		String author,
		TilePackageType type,
		TileStatus status,
		Optional<String> iconPath,
		String version,
		Optional<String> license,
		Optional<String> size,
		Optional<String> minecraftVersions,
		GitHubRepoStats repoStats) {

	public PackageDetailHeader {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(author, "author");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(iconPath, "iconPath");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(license, "license");
		Objects.requireNonNull(size, "size");
		Objects.requireNonNull(minecraftVersions, "minecraftVersions");
		Objects.requireNonNull(repoStats, "repoStats");
	}

	/** Ob ein eigenes Icon vorliegt, oder der Typ-Platzhalter gezeichnet werden muss (Charter-Regel 6). */
	public boolean hasIcon() {
		return iconPath.isPresent();
	}

	/**
	 * GATE70: derselbe Kopf, mit den frisch von der GitHub-API geholten
	 * Kennzahlen (oder -- bei Fehlschlag/keinem erkennbaren Repo -- weiterhin
	 * {@link GitHubRepoStats#UNKNOWN}). {@link de.spassglas.vitrine.ui.screen.PackageDetailScreen}
	 * ruft das nach einem erfolgreich beendeten Hintergrund-Abruf auf.
	 */
	public PackageDetailHeader withRepoStats(GitHubRepoStats stats) {
		return new PackageDetailHeader(name, author, type, status, iconPath, version, license, size,
				minecraftVersions, Objects.requireNonNull(stats, "stats"));
	}
}
