package de.spassglas.vitrine.hotapply;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Echte Umsetzung von {@link ClientThreadExecutor} ueber {@code
 * MinecraftClient#execute(Runnable)} (ROADMAP GATE112).
 *
 * <p>{@code client.execute} reiht nur ein, sie wartet nicht -- deshalb haengt
 * diese Klasse das Ergebnis an eine eigene {@link CompletableFuture} und
 * blockiert den aufrufenden Thread mit {@code join()}, bis der Client-Thread
 * die Aufgabe abgearbeitet hat. Das macht aus {@code manager.enable(...)}/
 * {@code manager.disable(...)} (in {@link MinecraftPackActivation}) wieder
 * einen synchronen Aufruf fuer den Rest der Kette ({@link HotApplyCoordinator}
 * liest den zurueckgegebenen {@code boolean} sofort), aendert aber, <b>wo</b>
 * der eigentliche Lese-Aendere-Schreibe-Zugriff auf den {@code
 * ResourcePackManager} passiert: nie mehr auf einem Fremd-Thread, immer
 * seriell auf dem Client-Thread -- derselbe Thread, auf dem Vanilla selbst
 * den Manager anfasst.</p>
 */
public final class MinecraftClientThreadExecutor implements ClientThreadExecutor {

	@Override
	public <T> T runAndWait(Supplier<T> task) {
		MinecraftClient client = MinecraftClient.getInstance();
		CompletableFuture<T> result = new CompletableFuture<>();
		// client.execute fuehrt sofort aus (noch auf diesem Thread), wenn wir
		// bereits auf dem Client-Thread sind -- siehe MinecraftResourceReloadTrigger
		// fuer dasselbe Muster -- und reiht sonst in dessen Aufgabenschlange ein.
		client.execute(() -> {
			try {
				result.complete(task.get());
			} catch (Throwable t) {
				// Kein verschluckter Fehler und keine Future, die nie
				// abschliesst (Charter-Regel 2).
				result.completeExceptionally(t);
			}
		});
		return result.join();
	}
}
