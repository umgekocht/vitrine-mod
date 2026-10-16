package de.spassglas.vitrine.hotapply;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

/**
 * Echte Umsetzung von {@link ResourceReloadTrigger} ueber {@code
 * MinecraftClient#reloadResources()} (ROADMAP H2).
 *
 * <p><b>GATE37 (Thread-Modell):</b> {@code reloadResources()} ist eine reine
 * Client-Thread-Methode -- sie fasst den Ressourcen-Manager, die Pack-Liste
 * und den Ladebildschirm an. Angestossen wird ein Hot-Apply aber fast nie vom
 * Client-Thread: der {@link HotApplyCoordinator} ruft hier aus dem
 * Debounce-Timer-Thread heraus auf, Installationen laufen auf
 * Hintergrund-Threads. Deshalb wird der Aufruf -- genau wie in {@code
 * de.spassglas.vitrine.hud.client.HudCommand} -- ueber {@code
 * client.execute(...)} auf den Client-Thread geschoben.
 *
 * <p>Nach aussen bleibt der Vertrag unveraendert: die zurueckgegebene {@link
 * CompletableFuture} schliesst genau dann ab, wenn der Reload fertig ist
 * (erfolgreich oder mit Fehler). Sie ist nur eine <em>eigene</em> Future, die
 * an die vom Client gelieferte angehaengt wird -- die Umschaltung auf den
 * Client-Thread passiert ja erst spaeter. Scheitert schon das Anstossen auf
 * dem Client-Thread, schlaegt auch diese Future fehl, statt fuer immer offen
 * zu bleiben. {@link HotApplyCoordinator} entscheidet weiterhin allein, was
 * bei Erfolg/Fehlschlag passiert.</p>
 */
public final class MinecraftResourceReloadTrigger implements ResourceReloadTrigger {

	@Override
	public CompletableFuture<Void> reloadResources() {
		MinecraftClient client = MinecraftClient.getInstance();
		CompletableFuture<Void> result = new CompletableFuture<>();
		// client.execute fuehrt sofort aus, wenn wir bereits auf dem
		// Client-Thread sind, und reiht sonst in dessen Aufgabenschlange ein.
		client.execute(() -> {
			try {
				// ROADMAP AD1: Verhaltens-Addons in der offenen Welt mitziehen.
				de.spassglas.vitrine.addon.AddonDataPacks.syncNow();
				client.reloadResources().whenComplete((ignored, throwable) -> {
					if (throwable != null) {
						result.completeExceptionally(throwable);
					} else {
						result.complete(null);
					}
				});
			} catch (Throwable t) {
				// Kein verschluckter Fehler und keine Future, die nie
				// abschliesst (Charter-Regel 2).
				result.completeExceptionally(t);
			}
		});
		return result;
	}
}
