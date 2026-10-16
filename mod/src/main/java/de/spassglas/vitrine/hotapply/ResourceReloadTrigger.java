package de.spassglas.vitrine.hotapply;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraktion ueber den eigentlichen Ressourcen-Reload (ROADMAP H2:
 * {@code reloadResources()}). In der echten Umsetzung ist das ein direkter
 * Aufruf von {@code MinecraftClient#reloadResources()} -- als eigene
 * Schnittstelle formuliert, damit {@link HotApplyCoordinator} in Tests ohne
 * laufenden Client geprueft werden kann (die {@link CompletableFuture} laesst
 * sich dort von Hand erfolgreich oder fehlschlagend abschliessen).
 */
public interface ResourceReloadTrigger {

	/**
	 * Stoesst den Ressourcen-Reload an und liefert eine Future, die
	 * abschliesst, sobald er fertig ist (erfolgreich oder mit Fehler).
	 */
	CompletableFuture<Void> reloadResources();
}
