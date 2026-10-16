package de.spassglas.vitrine.hotapply;

import java.util.Set;

/**
 * Abstraktion ueber den "Toast mit Fortschritt" aus dem Fertig-Kriterium von
 * H2. Getrennt von Minecrafts {@code SystemToast}, damit {@link
 * HotApplyCoordinator} in Tests ohne Rendering und ohne laufenden Client
 * geprueft werden kann -- ein Test-Double zeichnet nur die
 * Zustandsuebergaenge auf (aufgerufen: anwenden -&gt; angewendet/
 * fehlgeschlagen).
 *
 * <p><b>Charter-Regel 2 ("Kein Fortschrittsbalken, der nichts tut"):</b> die
 * echte Anzeige zeigt bewusst keinen erfundenen Prozentwert -- Minecrafts
 * oeffentliches {@code MinecraftClient#reloadResources()} liefert keinen
 * (das interne {@code ResourceReload#getProgress()} ist nicht erreichbar,
 * ohne an privaten Vanilla-Interna vorbeizugreifen). Stattdessen zeigt der
 * Toast die einzige Zahl, die wirklich feststeht: wie viele Pakete in diesem
 * Reload-Stapel stecken -- vom Anstossen bis zum tatsaechlichen Abschluss.</p>
 */
public interface HotApplyToastPresenter {

	/** Ein Reload fuer genau {@code packIds} wurde angestossen und laeuft noch. */
	void showApplying(Set<String> packIds);

	/** Der Reload fuer {@code packIds} ist erfolgreich abgeschlossen. */
	void showApplied(Set<String> packIds);

	/**
	 * Der Reload fuer {@code packLabels} ist fehlgeschlagen. {@code packLabels}
	 * sind Anzeigenamen (ROADMAP GATE81: {@link PackDisplayNameLookup}), keine
	 * technischen Profil-IDs mehr. {@code reasonKey} ist ein
	 * <em>Uebersetzungs-Key</em> aus {@link HotApplyFailure}
	 * ({@code vitrine.hotapply.reason.*}) -- nie ein Klassenname und nie eine
	 * roh durchgereichte Exception-Meldung (ROADMAP GATE11). Die Umsetzung
	 * uebersetzt ihn erst beim Anzeigen.
	 */
	void showFailed(Set<String> packLabels, String reasonKey);

	/**
	 * ROADMAP GATE81 ("Fehlgeschlagenes Update/Rollback bleibt unerklärt"):
	 * der Auto-Rollback (H3) hat {@code packLabels} (Anzeigenamen, keine
	 * Profil-IDs) soeben automatisch deaktiviert, weil ihr Reload
	 * fehlgeschlagen ist -- sichtbar <em>ohne</em> ein Flyout zu oeffnen, mit
	 * Klartext-Namen und uebersetztem Grund ({@code reasonKey}, siehe
	 * {@link #showFailed}).
	 */
	void showRolledBack(Set<String> packLabels, String reasonKey);
}
