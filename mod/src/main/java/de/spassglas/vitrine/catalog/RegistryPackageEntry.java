package de.spassglas.vitrine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Eine geparste, gegen {@code registry-package.schema.json} validierte
 * {@code packages/&lt;id&gt;.json} (ROADMAP G3 "Vollstaendigen Registry-
 * Paket-Eintrag holen"). Anders als der Kurzeintrag {@link IndexPackage} aus
 * der Index-Seite traegt dieser Eintrag alle Versionen samt Download-URL,
 * Pruefsumme, Signatur und Groesse -- genug fuer eine echte Installation
 * (G3) und fuer Update-Vergleiche (K5).
 *
 * @param minecraftVersions freigegebene Minecraft-Versionen (GATE70-FOLLOW,
 *                           registry-package.schema.json, am Paket haengend --
 *                           nicht pro Version), oder leer, wenn das optionale
 *                           Feld im JSON fehlt (aeltere Eintraege)
 */
public record RegistryPackageEntry(
		String id,
		String name,
		String category,
		String authorName,
		String authorUrl,
		String description,
		String icon,
		String homepage,
		String license,
		String repository,
		List<RegistryPackageVersion> versions,
		String latestVersion,
		String updatedAt,
		String contentClass,
		List<RegistryJavaModVersion> javaModVersions,
		List<String> minecraftVersions) {

	/** Klasse A -- ein {@code .vpkg}, ohne Neustart wirksam. Gilt auch, wenn das Feld fehlt. */
	public static final String CONTENT_CLASS_RESOURCE = "resource";
	/** Klasse B -- eine {@code .jar}, wirksam erst nach einem Neustart (GATE6). */
	public static final String CONTENT_CLASS_JAVAMOD = "javamod";

	public RegistryPackageEntry {
		versions = List.copyOf(versions);
		contentClass = contentClass == null ? CONTENT_CLASS_RESOURCE : contentClass;
		javaModVersions = javaModVersions == null ? List.of() : List.copyOf(javaModVersions);
		// GATE70-FOLLOW2: aeltere Index-/Paketeintraege ohne 'minecraftVersions'
		// bleiben gueltig -- leere Liste statt Nullpointer.
		minecraftVersions = minecraftVersions == null ? List.of() : List.copyOf(minecraftVersions);
	}

	/** GATE6: ob dieser Eintrag ein Klasse-B-Angebot (Java-Mod) ist. */
	public boolean isJavaMod() {
		return CONTENT_CLASS_JAVAMOD.equals(contentClass);
	}

	/** GATE6: der Versions-Eintrag zu {@link #latestVersion()} eines Klasse-B-Pakets. */
	public Optional<RegistryJavaModVersion> latestJavaModVersion() {
		for (RegistryJavaModVersion version : javaModVersions) {
			if (version.version().equals(latestVersion)) {
				return Optional.of(version);
			}
		}
		return Optional.empty();
	}

	/**
	 * Der Versions-Eintrag, dessen {@code version} zu {@link #latestVersion}
	 * passt. Leer, wenn kein Eintrag passt (Schema-Inkonsistenz zwischen
	 * {@code latestVersion} und {@code versions[]} -- der Index-Builder prueft
	 * das serverseitig, ein Client muss trotzdem ehrlich "nichts Passendes"
	 * melden statt zu raten).
	 */
	public Optional<RegistryPackageVersion> latestVersionEntry() {
		for (RegistryPackageVersion version : versions) {
			if (version.version().equals(latestVersion)) {
				return Optional.of(version);
			}
		}
		return Optional.empty();
	}

	/**
	 * Baut das Modell aus einem bereits schema-validierten JSON-Objekt
	 * (Pruefung vorher in {@link RegistryPackageSchemaValidator}).
	 */
	static RegistryPackageEntry fromJson(JsonObject root) {
		JsonObject author = root.getAsJsonObject("author");
		String contentClass = optionalString(root, "contentClass");
		// GATE70-FOLLOW2: 'minecraftVersions' haengt am Paket (Dokumentebene),
		// nicht an der Version -- so platziert es registry-package.schema.json
		// (dort Zeile ~278, ausserhalb von $defs/*VersionItem).
		List<String> minecraftVersions = new ArrayList<>();
		if (root.has("minecraftVersions") && root.get("minecraftVersions").isJsonArray()) {
			for (JsonElement mcVersion : root.getAsJsonArray("minecraftVersions")) {
				minecraftVersions.add(mcVersion.getAsString());
			}
		}
		boolean javaMod = CONTENT_CLASS_JAVAMOD.equals(contentClass);
		List<RegistryPackageVersion> versions = new ArrayList<>();
		List<RegistryJavaModVersion> javaModVersions = new ArrayList<>();
		JsonArray versionsArray = root.getAsJsonArray("versions");
		for (JsonElement element : versionsArray) {
			JsonObject v = element.getAsJsonObject();
			if (javaMod) {
				// GATE6: Klasse-B-Versionen tragen jarUrl/fileName statt vpkgUrl --
				// vorher lief hier jeder solche Eintrag in eine NullPointerException.
				List<String> dependencies = new ArrayList<>();
				if (v.has("dependencies") && v.get("dependencies").isJsonArray()) {
					for (JsonElement dep : v.getAsJsonArray("dependencies")) {
						dependencies.add(dep.getAsString());
					}
				}
				javaModVersions.add(new RegistryJavaModVersion(
						v.get("version").getAsString(),
						URI.create(v.get("jarUrl").getAsString()),
						v.get("fileName").getAsString(),
						v.get("sha256").getAsString(),
						v.get("signature").getAsString(),
						v.get("size").getAsLong(),
						dependencies,
						optionalString(v, "publishedAt"),
						optionalString(v, "minVitrineVersion")));
				continue;
			}
			versions.add(new RegistryPackageVersion(
					v.get("version").getAsString(),
					URI.create(v.get("vpkgUrl").getAsString()),
					v.get("sha256").getAsString(),
					v.get("signature").getAsString(),
					v.get("size").getAsLong(),
					optionalString(v, "publishedAt"),
					optionalString(v, "minVitrineVersion")));
		}
		return new RegistryPackageEntry(
				root.get("id").getAsString(),
				root.get("name").getAsString(),
				root.get("category").getAsString(),
				author.get("name").getAsString(),
				optionalString(author, "url"),
				optionalString(root, "description"),
				optionalString(root, "icon"),
				optionalString(root, "homepage"),
				optionalString(root, "license"),
				root.get("repository").getAsString(),
				versions,
				root.get("latestVersion").getAsString(),
				root.get("updatedAt").getAsString(),
				contentClass,
				javaModVersions,
				minecraftVersions);
	}

	private static String optionalString(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : null;
	}
}
