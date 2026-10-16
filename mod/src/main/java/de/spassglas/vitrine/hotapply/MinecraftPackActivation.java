package de.spassglas.vitrine.hotapply;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Echte Umsetzung von {@link PackActivation} ueber Vanillas eigenen {@link
 * ResourcePackManager} (ROADMAP H2, der "aktivieren"-Schritt) -- denselben
 * Manager, an den {@code de.spassglas.vitrine.pack.VitrinePackProvider} (H1)
 * seine Profile anmeldet. Absichtlich so schmal wie moeglich: die eigentliche
 * Logik (Sammeln mehrerer Aktivierungen, Debounce, Reload, Toast) steckt und
 * ist getestet in {@link HotApplyCoordinator} -- diese Klasse uebersetzt nur
 * einen einzelnen Methodenaufruf auf den echten Client.
 *
 * <p><b>GATE112 (Thread-Modell):</b> {@link ResourcePackManager#enable}/
 * {@code #disable} sind reines Lese-Aendere-Schreibe ohne eigene
 * Synchronisierung. {@link HotApplyCoordinator#requestActivation}/{@code
 * requestDeactivation} werden aber von mehreren Fremd-Threads gleichzeitig
 * aufgerufen (Katalog-Installation, "Alle aktualisieren", Bibliotheks-
 * Aktiv-Schalter) -- ohne Schutz kam bei 40 parallelen Aktivierungen nur ein
 * Bruchteil tatsaechlich am Manager an, obwohl {@code state.json} alle 40 als
 * aktiv auswies (vorgegaukelter Erfolg, Charter-Regel 2). Jeder Aufruf laeuft
 * deshalb jetzt ueber {@link ClientThreadExecutor#runAndWait}, genau wie
 * {@link MinecraftResourceReloadTrigger} seinen Reload ueber {@code
 * client.execute(...)} schiebt -- der Client-Thread arbeitet seine
 * Aufgabenschlange seriell ab, zwei gleichzeitige Aufrufe koennen den Manager
 * nie mehr ueberlappend anfassen.</p>
 */
public final class MinecraftPackActivation implements PackActivation {

	private final ClientThreadExecutor clientThread;
	private final Supplier<ResourcePackManager> managerSupplier;

	public MinecraftPackActivation() {
		this(new MinecraftClientThreadExecutor());
	}

	/** Testbarer Konstruktor -- siehe {@code MinecraftPackActivationRaceTest}. */
	public MinecraftPackActivation(ClientThreadExecutor clientThread) {
		this(clientThread, () -> MinecraftClient.getInstance().getResourcePackManager());
	}

	/**
	 * Voller Testkonstruktor (ROADMAP GATE112): ersetzt zusaetzlich den Zugriff
	 * auf den echten {@link ResourcePackManager}. {@code MinecraftClient.getInstance()}
	 * liefert ausserhalb eines laufenden Spiels {@code null} (kopffreie Unit-Tests
	 * starten keinen echten Client, siehe build.gradle {@code test {}}) -- {@code
	 * MinecraftPackActivationRaceTest} haengt hier einen echten, aber ohne Client
	 * gebauten {@link ResourcePackManager} ein (reine Datenstruktur, siehe dessen
	 * Konstruktor {@code ResourcePackManager(ResourcePackProvider...)}), damit die
	 * Rennbedingung gegen den echten, unsynchronisierten Lese-Aendere-Schreibe-
	 * Zugriff geprueft wird, statt gegen einen nachgebauten Fake.
	 */
	public MinecraftPackActivation(ClientThreadExecutor clientThread, Supplier<ResourcePackManager> managerSupplier) {
		this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
		this.managerSupplier = Objects.requireNonNull(managerSupplier, "managerSupplier");
	}

	@Override
	public boolean activate(String packId) {
		return clientThread.runAndWait(() -> managerSupplier.get().enable(packId));
	}

	@Override
	public boolean deactivate(String packId) {
		return clientThread.runAndWait(() -> managerSupplier.get().disable(packId));
	}
}
