package de.spassglas.vitrine.ui.library;

import de.spassglas.vitrine.ui.tile.TilePackageType;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Eintrag in der Bibliotheks-Liste (ROADMAP S5, docs/UI-SPEC.md
 * "6. Bibliothek") -- ein installiertes Vitrine-Paket samt seinem
 * Aktiv-Zustand und seiner Position in der massgeblichen Reihenfolge aus
 * {@code state.json} (H4). Reines Datenobjekt, kein Minecraft-Typ: der
 * {@code LibraryModel} baut die Liste ohne laufenden Client, damit
 * Reihenfolge, Aktiv-Schalter und Konflikterkennung testbar sind.
 *
 * <p>{@link #resourcePaths()} sind die {@code assets/...}-Eintragspfade im
 * {@code .vpkg} (rein lesend, nach {@code VpkgSecurityValidator.requireSafe}
 * ermittelt). Sie dienen der Konflikterkennung: ueberschreiben zwei aktive
 * Pakete denselben Pfad, gewinnt bei der aktuellen Reihenfolge das weiter
 * oben stehende (kleinerer {@link #position()}).</p>
 */
public final class LibraryEntry {

	private final String packId;
	private final String manifestId;
	private final String name;
	private final String author;
	private final String version;
	/** {@code null} nur fuer einen {@link #missing(String, String, int)}-Eintrag (kein Manifest lesbar). */
	private final TilePackageType type;
	private final boolean active;
	private final int position;
	private final List<String> resourcePaths;
	private final Path file;
	private final String iconEntryPath;
	private final long sizeBytes;
	private final boolean missing;

	public LibraryEntry(String packId, String manifestId, String name, String author, String version,
			TilePackageType type, boolean active, int position, List<String> resourcePaths) {
		this(packId, manifestId, name, author, version, type, active, position, resourcePaths, null, null, -1L);
	}

	/**
	 * ROADMAP GATE83 (docs/UI-SPEC.md "6. Bibliothek": Zeile zeigt Bild + Größe,
	 * Überlaufmenü-Eintrag "Details"): zusätzlich die {@code .vpkg}-Datei selbst
	 * ({@link #file()}, für das Vorschaubild und "Ordner öffnen"/"Details"), der
	 * Icon-Pfad <em>im</em> Paket ({@link #iconEntryPath()}, aus {@code
	 * manifest.icon()}, leer wenn das Manifest keines nennt) und die Dateigröße
	 * in Bytes ({@link #sizeBytes()}, {@code -1} wenn nicht ermittelbar).
	 */
	public LibraryEntry(String packId, String manifestId, String name, String author, String version,
			TilePackageType type, boolean active, int position, List<String> resourcePaths, Path file,
			String iconEntryPath, long sizeBytes) {
		this(packId, manifestId, name, author, version, type, active, position, resourcePaths, file, iconEntryPath,
				sizeBytes, false);
	}

	/**
	 * ROADMAP GATE115: zusätzlich der {@code missing}-Zustand ({@link
	 * #missing(String, String, int)}) -- ein Eintrag, dessen {@code .vpkg}
	 * nicht gefunden wurde, hat kein lesbares Manifest und darf deshalb den
	 * sonst erzwungenen {@code type} nicht mitbringen.
	 */
	private LibraryEntry(String packId, String manifestId, String name, String author, String version,
			TilePackageType type, boolean active, int position, List<String> resourcePaths, Path file,
			String iconEntryPath, long sizeBytes, boolean missing) {
		this.packId = Objects.requireNonNull(packId, "packId");
		this.manifestId = Objects.requireNonNull(manifestId, "manifestId");
		this.name = Objects.requireNonNull(name, "name");
		this.author = Objects.requireNonNull(author, "author");
		this.version = Objects.requireNonNull(version, "version");
		this.type = missing ? type : Objects.requireNonNull(type, "type");
		this.active = active;
		this.position = position;
		this.resourcePaths = List.copyOf(resourcePaths);
		this.file = file;
		this.iconEntryPath = iconEntryPath;
		this.sizeBytes = sizeBytes;
		this.missing = missing;
	}

	/**
	 * ROADMAP GATE115 (Fertig-Kriterium: "zeigt den Zustand sichtbar in
	 * Bibliothek/Diagnose statt ihn zu verschlucken"): ein Paket, das {@code
	 * state.json} weiterhin als installiert führt, dessen {@code .vpkg} aber
	 * weder in {@code packs/} noch -- nach dem Wiederherstellungsversuch beim
	 * Start, siehe {@code OrphanedUpdateBackupRecovery} -- im Update-Backup
	 * gefunden wurde. Ohne Manifest gibt es keinen Namen/Autor/Typ: {@link
	 * #name()} liefert deshalb die Roh-{@code manifestId}, {@link #type()}
	 * {@code null}, {@link #resourcePaths()} ist leer (taucht also nicht im
	 * Abschnitt "Reihenfolge" auf).
	 */
	public static LibraryEntry missing(String packId, String manifestId, int position) {
		return new LibraryEntry(packId, manifestId, manifestId, "", "", null, false, position, List.of(), null,
				null, -1L, true);
	}

	/** {@code true}, wenn dieser Eintrag ueber {@link #missing(String, String, int)} entstanden ist (ROADMAP GATE115). */
	public boolean isMissing() {
		return missing;
	}

	/** Ressourcen-Pack-Profil-ID ({@code vitrine/<manifestId>}) -- Schluessel in {@code state.json} und beim HotApply. */
	public String packId() {
		return packId;
	}

	/** Roh-ID aus {@code vitrine.json}. */
	public String manifestId() {
		return manifestId;
	}

	public String name() {
		return name;
	}

	public String author() {
		return author;
	}

	public String version() {
		return version;
	}

	/** {@code null} nur fuer {@link #isMissing()} (ROADMAP GATE115, kein Manifest lesbar). */
	public TilePackageType type() {
		return type;
	}

	public boolean active() {
		return active;
	}

	/** 0-basierte Position in der Reihenfolge; 0 = ganz oben, gewinnt bei gleicher Ressource. */
	public int position() {
		return position;
	}

	/** {@code assets/...}-Dateipfade im Paket, sortiert (fuer die Konflikterkennung). */
	public List<String> resourcePaths() {
		return resourcePaths;
	}

	/** Ob dieses Paket ueberhaupt Ressourcen ({@code assets/...}) mitbringt und damit im Abschnitt "Reihenfolge" auftaucht. */
	public boolean isResourcePack() {
		return !resourcePaths.isEmpty();
	}

	/** Die installierte {@code .vpkg}-Datei in {@code packs/}, oder {@code null} wenn unbekannt (aeltere Konstruktoren). */
	public Path file() {
		return file;
	}

	/** Icon-Pfad <em>im</em> Paket ({@code manifest.icon()}), oder leer wenn das Manifest keines nennt. */
	public Optional<String> iconEntryPath() {
		return Optional.ofNullable(iconEntryPath);
	}

	/** Dateigroesse der {@code .vpkg} in Bytes, oder {@code -1}, wenn nicht ermittelbar. */
	public long sizeBytes() {
		return sizeBytes;
	}
}
