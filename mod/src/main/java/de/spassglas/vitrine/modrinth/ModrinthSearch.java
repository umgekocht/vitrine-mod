package de.spassglas.vitrine.modrinth;

import de.spassglas.vitrine.Vitrine;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live-Suche auf Modrinth fuer Vorschlaege und Ergebnisseite. Ergebnisse je
 * Suchtext werden fuer die Sitzung gemerkt; {@link #get} blockiert nie.
 */
public final class ModrinthSearch {

	private static final Map<String, List<ModrinthClient.Hit>> RESULTS = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> RUNNING = new ConcurrentHashMap<>();

	private ModrinthSearch() {
	}

	private static String key(String text, boolean mods) {
		return text.trim().toLowerCase(Locale.ROOT) + (mods ? "|m" : "");
	}

	/** Treffer fuer {@code text}, oder leer -- dann laeuft die Suche an und {@code onLoaded} meldet sich. */
	public static List<ModrinthClient.Hit> get(String text, boolean mods, Runnable onLoaded) {
		String q = text.trim();
		if (q.isEmpty()) {
			return List.of();
		}
		String k = key(q, mods);
		List<ModrinthClient.Hit> hits = RESULTS.get(k);
		if (hits != null) {
			return hits;
		}
		if (RUNNING.putIfAbsent(k, Boolean.TRUE) == null) {
			Thread t = new Thread(() -> {
				try {
					RESULTS.put(k, ModrinthClient.query(q, mods, 24));
					onLoaded.run();
				} catch (Exception e) {
					Vitrine.LOGGER.info("Modrinth-Suche '{}' fehlgeschlagen: {}", q, e.toString());
				} finally {
					RUNNING.remove(k);
				}
			}, "Vitrine-Modrinth-Search");
			t.setDaemon(true);
			t.start();
		}
		return List.of();
	}

	/** Schon geladene Treffer, ohne eine Suche auszuloesen. */
	public static List<ModrinthClient.Hit> cached(String text, boolean mods) {
		return RESULTS.getOrDefault(key(text, mods), List.of());
	}
}
