package de.spassglas.vitrine.modrinth;

import de.spassglas.vitrine.Vitrine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt die Modrinth-Listen je Typ ({@code resourcepack}, {@code mod}) fuer die
 * Sitzung. Laedt einmalig im Hintergrund; {@link #get} blockiert nie und
 * liefert leer, bis die Liste da ist ({@code onLoaded} baut dann neu auf).
 */
public final class ModrinthCatalog {

	private static final Map<String, List<ModrinthClient.Hit>> LOADED = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();

	private ModrinthCatalog() {
	}

	public static List<ModrinthClient.Hit> get(String projectType, Runnable onLoaded) {
		List<ModrinthClient.Hit> hits = LOADED.get(projectType);
		if (hits != null) {
			return hits;
		}
		if (LOADING.putIfAbsent(projectType, Boolean.TRUE) == null) {
			Thread t = new Thread(() -> {
				try {
					LOADED.put(projectType, "featured".equals(projectType)
							? ModrinthClient.featured(8)
							: projectType.endsWith("_50")
									? ModrinthClient.popular(projectType.substring(0, projectType.length() - 3), 50)
									: ModrinthClient.popular(projectType, 24));
					onLoaded.run();
				} catch (Exception e) {
					Vitrine.LOGGER.info("Modrinth-Liste '{}' nicht geladen: {}", projectType, e.toString());
					LOADING.remove(projectType);
				}
			}, "Vitrine-Modrinth");
			t.setDaemon(true);
			t.start();
		}
		return List.of();
	}

	public static ModrinthClient.Hit byTileId(String tileId) {
		for (List<ModrinthClient.Hit> list : LOADED.values()) {
			for (ModrinthClient.Hit h : list) {
				if (("modrinth:" + h.id()).equals(tileId)) {
					return h;
				}
			}
		}
		return null;
	}
}
