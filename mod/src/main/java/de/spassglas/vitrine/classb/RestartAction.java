package de.spassglas.vitrine.classb;

/**
 * ROADMAP J2 -- Seam fuer den Sammelknopf "Jetzt neu starten": {@link JavaModStager}
 * ist client-frei und ruft <b>nie</b> direkt {@code MinecraftClient.scheduleStop()}
 * o. ae. Die Absichts-Aktion des Knopfes ist hinter diese Schnittstelle
 * gekapselt; die Produktions-Implementierung lebt im Client-Code (Mixin-/
 * Screen-Schicht), im Test ein Fake, der nur mitzaehlt.
 *
 * <p>J2 fuehrt selbst <b>keine</b> Jar aus und stoesst keinen echten Neustart
 * an -- der Knopf ist die Absichts-Aktion, mehr nicht.</p>
 */
@FunctionalInterface
public interface RestartAction {

	/** Loest den (echten) Neustart des Clients aus. Wird von {@link JavaModStager#restartNow()} genau einmal gerufen. */
	void restart();
}
