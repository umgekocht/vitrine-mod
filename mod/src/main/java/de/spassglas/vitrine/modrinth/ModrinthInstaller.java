package de.spassglas.vitrine.modrinth;

import de.spassglas.vitrine.Vitrine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installiert Modrinth-Inhalte.
 *
 * <ul>
 *   <li>Ressourcenpakete: nach {@code resourcepacks/}, Zip-Pruefung
 *       (kein Zip-Slip, keine absoluten Pfade, Zip-Bomben-Deckel), dann
 *       aktivieren und Ressourcen neu laden -- <b>ohne Neustart</b>.</li>
 *   <li>Mods (Fabric, {@code .jar}): nach {@code mods/}, samt Pflicht-
 *       Abhaengigkeiten. Java-Code wird erst beim naechsten Start geladen --
 *       ehrlich als "Neustart noetig" (Charter-Regel 2).</li>
 * </ul>
 */
public final class ModrinthInstaller {

	private static final long MAX_UNPACKED = 1024L * 1024 * 1024;
	private static final int MAX_ENTRIES = 60_000;

	private ModrinthInstaller() {
	}

	/** Ergebnis fuer die Oberflaeche. */
	public enum Result { APPLIED, RESTART_REQUIRED }

	public static Result install(ModrinthClient.Hit hit) throws IOException, InterruptedException {
		if ("mod".equals(hit.projectType())) {
			installMod(hit.id(), new HashSet<>());
			return Result.RESTART_REQUIRED;
		}
		if ("datapack".equals(hit.projectType())) {
			installDatapack(hit);
			return Result.APPLIED;
		}
		if ("shader".equals(hit.projectType())) {
			// Shaderpakete brauchen Iris/Oculus; sie landen in shaderpacks/ und
			// erscheinen dort sofort in der Shader-Auswahl.
			installShader(hit);
			return Result.APPLIED;
		}
		installResourcePack(hit);
		return Result.APPLIED;
	}

	private static void installResourcePack(ModrinthClient.Hit hit) throws IOException, InterruptedException {
		ModrinthClient.FileRef ref = ModrinthClient.latestFile(hit.id(), hit.projectType())
				.orElseThrow(() -> new IOException("Keine Version fuer " + ModrinthClient.GAME_VERSION));
		MinecraftClient client = MinecraftClient.getInstance();
		Path dir = client.getResourcePackDir();
		Files.createDirectories(dir);
		Path target = dir.resolve(ref.filename());
		if (!target.normalize().startsWith(dir.normalize())) {
			throw new IOException("Unsicherer Zielpfad");
		}
		ModrinthClient.download(ref, target);
		checkZip(target);
		String packId = "file/" + ref.filename();
		client.execute(() -> {
			ResourcePackManager manager = client.getResourcePackManager();
			manager.scanPacks();
			manager.enable(packId);
			client.options.refreshResourcePacks(manager);
			Vitrine.LOGGER.info("Modrinth-Ressourcenpaket '{}' aktiviert, ohne Neustart.", ref.filename());
		});
	}

	/**
	 * Datapacks gehoeren in die geoeffnete Welt: Datei nach {@code <welt>/datapacks},
	 * dann einschalten und die Daten neu laden -- ohne Neustart, ohne Welt-Neuladen.
	 */
	private static void installDatapack(ModrinthClient.Hit hit) throws IOException, InterruptedException {
		MinecraftClient client = MinecraftClient.getInstance();
		var server = client.getServer();
		if (server == null) {
			throw new IOException("Oeffne zuerst eine Einzelspieler-Welt -- Datapacks gehoeren in eine Welt.");
		}
		ModrinthClient.FileRef ref = ModrinthClient.latestFile(hit.id(), hit.projectType())
				.orElseThrow(() -> new IOException("Keine Version fuer " + ModrinthClient.GAME_VERSION));
		Path dir = server.getSavePath(net.minecraft.util.WorldSavePath.DATAPACKS);
		Files.createDirectories(dir);
		Path target = dir.resolve(ref.filename());
		if (!target.normalize().startsWith(dir.normalize())) {
			throw new IOException("Unsicherer Zielpfad");
		}
		ModrinthClient.download(ref, target);
		checkZip(target);
		String packId = "file/" + ref.filename();
		server.execute(() -> {
			var manager = server.getDataPackManager();
			manager.scanPacks();
			java.util.List<String> enabled = new java.util.ArrayList<>(manager.getEnabledIds());
			if (!enabled.contains(packId)) {
				enabled.add(packId);
			}
			server.reloadResources(enabled);
			Vitrine.LOGGER.info("Modrinth-Datapack '{}' in der Welt eingeschaltet, ohne Neustart.", ref.filename());
		});
	}

	private static void installShader(ModrinthClient.Hit hit) throws IOException, InterruptedException {
		ModrinthClient.FileRef ref = ModrinthClient.latestFile(hit.id(), hit.projectType())
				.orElseThrow(() -> new IOException("Keine Version fuer " + ModrinthClient.GAME_VERSION));
		Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
		Files.createDirectories(dir);
		Path target = dir.resolve(ref.filename());
		if (!target.normalize().startsWith(dir.normalize())) {
			throw new IOException("Unsicherer Zielpfad");
		}
		ModrinthClient.download(ref, target);
		checkZip(target);
	}

	private static void installMod(String projectId, Set<String> seen) throws IOException, InterruptedException {
		if (!seen.add(projectId) || seen.size() > 20) {
			return;
		}
		ModrinthClient.FileRef ref = ModrinthClient.latestFile(projectId, "mod")
				.orElseThrow(() -> new IOException("Keine Fabric-Version fuer " + ModrinthClient.GAME_VERSION));
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
		Files.createDirectories(mods);
		Path target = mods.resolve(ref.filename());
		if (!target.normalize().startsWith(mods.normalize())) {
			throw new IOException("Unsicherer Zielpfad");
		}
		if (!Files.exists(target)) {
			ModrinthClient.download(ref, target);
			Vitrine.LOGGER.info("Modrinth-Mod '{}' nach mods/ gelegt -- wirkt nach dem Neustart.", ref.filename());
		}
		if (!de.spassglas.vitrine.config.VitrineConfig.get().installDependencies) {
			return;
		}
		for (String dep : ref.requiredProjects()) {
			if (!isLoaded(dep)) {
				installMod(dep, seen);
			}
		}
	}

	/** Fabric API & Co. sind oft schon da: bekannte Projekt-IDs gegen geladene Mods pruefen. */
	private static boolean isLoaded(String modrinthProjectId) {
		return switch (modrinthProjectId) {
			case "P7dR8mSH" -> FabricLoader.getInstance().isModLoaded("fabric-api");
			default -> false;
		};
	}

	/** Minimale Sicherheitspruefung fuer fremde Ressourcenpakete. */
	static void checkZip(Path zip) throws IOException {
		try (ZipFile z = new ZipFile(zip.toFile())) {
			long total = 0;
			int count = 0;
			Enumeration<? extends ZipEntry> entries = z.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				String n = e.getName().replace('\\', '/');
				if (n.startsWith("/") || n.contains("../") || n.equals("..") || n.matches("^[A-Za-z]:.*")) {
					throw new IOException("Unsicherer Eintrag im Ressourcenpaket: " + n);
				}
				total += Math.max(0, e.getSize());
				if (++count > MAX_ENTRIES || total > MAX_UNPACKED) {
					Files.deleteIfExists(zip);
					throw new IOException("Ressourcenpaket zu gross");
				}
			}
		} catch (IOException e) {
			Files.deleteIfExists(zip);
			throw e;
		}
	}
}
