package de.spassglas.vitrine.hotapply;

import java.util.function.Supplier;

/**
 * Abstraktion ueber "fuehre {@code task} auf dem Client-Thread aus und warte
 * auf das Ergebnis" (ROADMAP GATE112). Getrennt von {@link PackActivation},
 * damit {@link MinecraftPackActivation} testbar bleibt, ohne einen echten
 * {@code MinecraftClient} zu brauchen -- Produktion verdrahtet {@link
 * MinecraftClientThreadExecutor} (echtes {@code client.execute(...)}), Tests
 * einen Fake, der mehrere gleichzeitig aufrufende Threads ueber einen
 * einzigen Hintergrund-Thread serialisiert (siehe
 * {@code MinecraftPackActivationRaceTest}).
 */
public interface ClientThreadExecutor {

	/**
	 * Fuehrt {@code task} auf dem Client-Thread aus und blockiert den
	 * aufrufenden Thread, bis das Ergebnis vorliegt. Laeuft der Aufruf bereits
	 * auf dem Client-Thread, wird {@code task} sofort ausgefuehrt (kein
	 * Deadlock durch Selbst-Warten).
	 */
	<T> T runAndWait(Supplier<T> task);
}
