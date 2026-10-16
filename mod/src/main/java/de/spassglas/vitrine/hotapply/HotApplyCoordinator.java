package de.spassglas.vitrine.hotapply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Klebeklasse fuer ROADMAP H2 ("Anwenden zur Laufzeit"): nimmt einzelne
 * Aktivierungs-Ereignisse (eine {@code packId} je Aufruf von {@link
 * #requestActivation}) entgegen, aktiviert das jeweilige Profil sofort ueber
 * {@link PackActivation}, sammelt die zugehoerigen {@code packId}s aber ueber
 * einen {@link Debouncer} (feste {@link #DEBOUNCE_DELAY_MILLIS}, ROADMAP-
 * Vorgabe "200-ms-Debounce fuer Mehrfachinstallationen"), damit mehrere kurz
 * hintereinander eintreffende Aktivierungen (z. B. eine Mehrfachinstallation)
 * sich einen einzigen, teuren {@link ResourceReloadTrigger#reloadResources()}
 * teilen, statt fuer jede einzelne Paket-ID neu zu laden.
 *
 * <p>Zeigt den Ablauf ausschliesslich ueber {@link HotApplyToastPresenter} an
 * -- {@code showApplying} beim Anstossen des gesammelten Reloads, danach je
 * nach Ausgang entweder {@code showApplied} oder {@code showFailed} mit einem
 * uebersetzbaren Grund-Lang-Key aus {@link HotApplyFailure} (kein
 * verschluckter Fehler; Charter-Regel 2: kein erfundener
 * Fortschritt, nur echte Zustandsuebergaenge).</p>
 *
 * <p><b>ROADMAP H3 ("Auto-Rollback"):</b> schlaegt der Reload fuer einen
 * Stapel fehl, deaktiviert {@link #rollBackAndRetry} automatisch genau die
 * Paket-IDs, die in diesem gescheiterten Stapel <em>neu</em> aktiviert worden
 * waren (GATE35: nicht die nur zur Anzeige mitgezaehlten IDs einer
 * Reihenfolgeaenderung, nicht die schon vorher aktiven), haelt das ueber
 * {@link RollbackStatePersister} in {@code state.json} fest (GATE36) und
 * stoesst danach genau einen weiteren, "sauberen" Reload an -- ohne die
 * defekten Pakete sollte dieser durchlaufen und das Spiel bleibt bedienbar.
 * Nur dieser eine automatische Wiederholungsversuch: schlaegt auch er fehl,
 * wird das nur geloggt (kein zweiter Rollback-Zyklus, um keine Endlosschleife
 * zu riskieren, siehe {@link #onReloadFailed}).</p>
 *
 * <p>Kennt keine Minecraft-Typen -- alle vier Abhaengigkeiten sind die in
 * diesem Paket definierten Schnittstellen, austauschbar gegen Test-Doubles
 * (siehe {@code HotApplyCoordinatorTest}) oder gegen die echten, an
 * {@code MinecraftClient} angebundenen Umsetzungen (siehe
 * {@link HotApplyService}).</p>
 */
public final class HotApplyCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/HotApply");

	/** ROADMAP H2: "200-ms-Debounce fuer Mehrfachinstallationen". */
	public static final long DEBOUNCE_DELAY_MILLIS = 200;

	/**
	 * ROADMAP X3 (Diagnose-Bildschirm, "was hat den Reload gekostet"): echt
	 * gemessene Dauer und Groesse des zuletzt <em>abgeschlossenen</em>
	 * gesammelten Reloads -- kein erfundener Wert (Charter-Regel 2). {@link
	 * #packageCount()} ist die Anzahl der Paket-IDs im zugehoerigen Stapel
	 * (siehe {@link #requestActivation}/{@link #requestReload}), {@link
	 * #durationMillis()} die Wanduhrzeit zwischen dem Anstossen von {@link
	 * ResourceReloadTrigger#reloadResources()} und dessen Abschluss (Erfolg
	 * oder Fehlschlag).
	 */
	public record ReloadStats(int packageCount, long durationMillis, boolean success) {
	}

	private final PackActivation packActivation;
	private final ResourceReloadTrigger reloadTrigger;
	private final HotApplyToastPresenter toastPresenter;
	private final RollbackStatePersister rollbackStatePersister;
	/** ROADMAP GATE81: loest eine Paket-ID in einen Klartext-Anzeigenamen auf (sichtbare Fehlschlag-/Rollback-Meldungen). */
	private final PackDisplayNameLookup displayNames;
	private final Debouncer debouncer;

	/** Nur unter {@link #batchLock} gelesen/geschrieben -- {@link #requestActivation} kann von mehreren Threads kommen. */
	private Set<String> pendingPackIds = new LinkedHashSet<>();
	/**
	 * GATE35: nur die IDs, die in diesem Stapel tatsaechlich <em>neu</em>
	 * aktiviert wurden ({@link PackActivation#activate} meldete eine Aenderung).
	 * Nur sie darf ein Rollback zuruecknehmen. Unter {@link #batchLock}.
	 */
	private Set<String> pendingNewlyActivatedIds = new LinkedHashSet<>();
	/** {@code true}, sobald {@link #requestReload()} einen Reload ohne Aktivierungswechsel angefordert hat. */
	private boolean plainReloadRequested;
	private final Object batchLock = new Object();

	/** ROADMAP X3: die Kennzahlen des zuletzt abgeschlossenen Reloads, oder {@code null} vor dem ersten. */
	private final AtomicReference<ReloadStats> lastReloadStats = new AtomicReference<>();

	/** Ohne Zustandsdatei -- ein Rollback wirkt nur im laufenden Spiel (Tests, die das nicht pruefen). */
	public HotApplyCoordinator(PackActivation packActivation, ResourceReloadTrigger reloadTrigger,
			HotApplyToastPresenter toastPresenter, DelayedTaskScheduler scheduler) {
		this(packActivation, reloadTrigger, toastPresenter, scheduler, RollbackStatePersister.none(),
				PackDisplayNameLookup.IDENTITY);
	}

	public HotApplyCoordinator(PackActivation packActivation, ResourceReloadTrigger reloadTrigger,
			HotApplyToastPresenter toastPresenter, DelayedTaskScheduler scheduler,
			RollbackStatePersister rollbackStatePersister) {
		this(packActivation, reloadTrigger, toastPresenter, scheduler, rollbackStatePersister,
				PackDisplayNameLookup.IDENTITY);
	}

	/**
	 * ROADMAP GATE81: voller Konstruktor mit echtem Anzeigename-Nachschlagen
	 * ({@link PackDisplayNameLookup}) fuer die sichtbaren Fehlschlag-/Rollback-
	 * Meldungen (Produktion: {@link ManifestPackDisplayNameLookup}).
	 */
	public HotApplyCoordinator(PackActivation packActivation, ResourceReloadTrigger reloadTrigger,
			HotApplyToastPresenter toastPresenter, DelayedTaskScheduler scheduler,
			RollbackStatePersister rollbackStatePersister, PackDisplayNameLookup displayNames) {
		this.packActivation = Objects.requireNonNull(packActivation, "packActivation");
		this.reloadTrigger = Objects.requireNonNull(reloadTrigger, "reloadTrigger");
		this.toastPresenter = Objects.requireNonNull(toastPresenter, "toastPresenter");
		this.rollbackStatePersister = Objects.requireNonNull(rollbackStatePersister, "rollbackStatePersister");
		this.displayNames = Objects.requireNonNull(displayNames, "displayNames");
		Objects.requireNonNull(scheduler, "scheduler");
		this.debouncer = new Debouncer(DEBOUNCE_DELAY_MILLIS, scheduler);
	}

	/**
	 * Aktiviert {@code packId} sofort (siehe {@link PackActivation#activate})
	 * und reiht es in den naechsten, debounced Reload-Stapel ein. Sicher von
	 * mehreren Threads gleichzeitig aufzurufen (z. B. mehrere schnelle
	 * Installationen hintereinander).
	 */
	public void requestActivation(String packId) {
		Objects.requireNonNull(packId, "packId");
		boolean changed = packActivation.activate(packId);
		LOGGER.debug("Vitrine-Paket '{}' aktiviert (Zustand geaendert: {}), fuer den naechsten Reload vorgemerkt.",
				packId, changed);
		synchronized (batchLock) {
			pendingPackIds.add(packId);
			if (changed) {
				pendingNewlyActivatedIds.add(packId);
			}
		}
		debouncer.trigger(this::runPendingReload);
	}

	/**
	 * Deaktiviert {@code packId} sofort (siehe {@link PackActivation#deactivate})
	 * und reiht es in den naechsten, debounced Reload-Stapel ein -- ROADMAP H5,
	 * der "nur deaktivieren, ohne zu deinstallieren"-Fall (das Paket bleibt in
	 * {@code config/vitrine/packs/} liegen und laesst sich jederzeit ueber
	 * {@link #requestActivation} wieder anschalten). Fuer das vollstaendige
	 * Deinstallieren (Datei in den Papierkorb, Zustand entfernen) siehe {@code
	 * de.spassglas.vitrine.pack.PackUninstaller}, das denselben {@link
	 * PackActivation}/{@link ResourceReloadTrigger}-Weg nutzt, aber zusaetzlich
	 * auf den Abschluss des Reloads wartet, bevor es die Datei verschiebt.
	 */
	public void requestDeactivation(String packId) {
		Objects.requireNonNull(packId, "packId");
		boolean changed = packActivation.deactivate(packId);
		LOGGER.debug("Vitrine-Paket '{}' deaktiviert (Zustand geaendert: {}), fuer den naechsten Reload vorgemerkt.",
				packId, changed);
		synchronized (batchLock) {
			pendingPackIds.add(packId);
			// Im selben Fenster erst an-, dann wieder abgeschaltet: nichts mehr zurueckzunehmen.
			pendingNewlyActivatedIds.remove(packId);
		}
		debouncer.trigger(this::runPendingReload);
	}

	/**
	 * ROADMAP S5 ("Bibliothek: Abschnitt Reihenfolge" / "Alle aktualisieren"):
	 * die Menge der aktiven Pakete aendert sich <em>nicht</em>, nur ihre
	 * Reihenfolge -- also, welche Ressource bei gleichem Pfad gewinnt. Loest
	 * ueber genau denselben {@link Debouncer}/{@link ResourceReloadTrigger}
	 * wie {@link #requestActivation} einen einzelnen, gesammelten Reload aus
	 * (kein zweiter Reload-Pfad), aktiviert oder deaktiviert aber nichts.
	 * Trifft er mit anderen {@link #requestActivation}-Aufrufen im selben
	 * Debounce-Fenster zusammen, teilen sie sich weiterhin einen Reload.
	 */
	public void requestReload(Collection<String> affectedPackIds) {
		Objects.requireNonNull(affectedPackIds, "affectedPackIds");
		synchronized (batchLock) {
			plainReloadRequested = true;
			// Nur fuer die Toast-Anzeige mitgezaehlt -- KEIN packActivation.activate,
			// die Aktiv-/Inaktiv-Menge bleibt unangetastet.
			pendingPackIds.addAll(affectedPackIds);
		}
		debouncer.trigger(this::runPendingReload);
	}

	/**
	 * ROADMAP X3 (Diagnose-Bildschirm, "was hat den Reload gekostet"): die
	 * Kennzahlen des zuletzt abgeschlossenen gesammelten Reloads -- leer, wenn
	 * seit dem Start dieses {@link HotApplyCoordinator} noch keiner
	 * abgeschlossen wurde.
	 */
	public Optional<ReloadStats> lastReloadStats() {
		return Optional.ofNullable(lastReloadStats.get());
	}

	/** Laeuft erst, wenn fuer {@link #DEBOUNCE_DELAY_MILLIS} kein weiterer {@link #requestActivation}-Aufruf mehr kam. */
	private void runPendingReload() {
		Set<String> packIds;
		Set<String> newlyActivatedIds;
		synchronized (batchLock) {
			if (pendingPackIds.isEmpty() && !plainReloadRequested) {
				return;
			}
			packIds = Collections.unmodifiableSet(pendingPackIds);
			newlyActivatedIds = Collections.unmodifiableSet(pendingNewlyActivatedIds);
			pendingPackIds = new LinkedHashSet<>();
			pendingNewlyActivatedIds = new LinkedHashSet<>();
			plainReloadRequested = false;
		}
		triggerReload(packIds, newlyActivatedIds);
	}

	/** Stoesst den eigentlichen Reload fuer {@code packIds} an und zeigt ihn ueber {@link #toastPresenter} an. */
	private void triggerReload(Set<String> packIds, Set<String> newlyActivatedIds) {
		LOGGER.info("Vitrine loest einen gesammelten Ressourcen-Reload fuer {} Paket(e) aus: {}",
				packIds.size(), packIds);
		toastPresenter.showApplying(packIds);

		// ROADMAP X3: echte Wanduhrzeit zwischen Anstossen und Abschluss --
		// kein geschaetzter/erfundener Wert (Charter-Regel 2).
		long startNanos = System.nanoTime();
		try {
			reloadTrigger.reloadResources().whenComplete((ignored, throwable) -> {
				recordReloadStats(packIds, startNanos, throwable == null);
				if (throwable != null) {
					onReloadFailed(packIds, newlyActivatedIds, throwable);
				} else {
					LOGGER.info("Vitrine-Ressourcen-Reload fuer {} erfolgreich abgeschlossen.", packIds);
					toastPresenter.showApplied(packIds);
				}
			});
		} catch (RuntimeException e) {
			// reloadResources() selbst darf laut Schnittstelle keine gepruefte
			// Exception werfen, aber eine Laufzeit-Exception beim blossen
			// Anstossen (statt erst in der Future) ist kein verschluckter
			// Fehler wert -- derselbe onReloadFailed-Pfad wie bei einer
			// fehlgeschlagenen Future.
			recordReloadStats(packIds, startNanos, false);
			onReloadFailed(packIds, newlyActivatedIds, e);
		}
	}

	private void recordReloadStats(Set<String> packIds, long startNanos, boolean success) {
		long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
		lastReloadStats.set(new ReloadStats(packIds.size(), durationMillis, success));
	}

	/**
	 * Ein Reload fuer {@code packIds} ist fehlgeschlagen (entweder ueber die
	 * Future oder weil {@code reloadResources()} selbst geworfen hat). Zeigt
	 * den Fehler im Klartext (Paket-IDs + Grund) und stoesst danach den
	 * ROADMAP-H3-Auto-Rollback an, statt das Spiel im kaputten Reload-Zustand
	 * haengen zu lassen.
	 */
	private void onReloadFailed(Set<String> packIds, Set<String> newlyActivatedIds, Throwable throwable) {
		// ROADMAP GATE11: getrennte Wege -- der Spieler bekommt einen
		// uebersetzten Satz (Lang-Key), der Klassenname samt Stapelspur bleibt
		// im Log.
		String reasonKey = HotApplyFailure.reasonKey(throwable);
		String technicalDetail = HotApplyFailure.technicalDetail(throwable);
		LOGGER.warn("Vitrine-Ressourcen-Reload fuer {} ist fehlgeschlagen ({}): {}", packIds, reasonKey,
				technicalDetail, throwable);
		// ROADMAP GATE81: der Spieler sieht Klartext-Anzeigenamen, nie die
		// technische Profil-ID.
		toastPresenter.showFailed(labelsFor(packIds), reasonKey);
		rollBackAndRetry(newlyActivatedIds, reasonKey, technicalDetail);
	}

	/** ROADMAP GATE81: loest jede Paket-ID ueber {@link #displayNames} in einen Klartext-Anzeigenamen auf. */
	private Set<String> labelsFor(Set<String> packIds) {
		Set<String> labels = new LinkedHashSet<>();
		for (String packId : packIds) {
			labels.add(displayNames.displayNameFor(packId));
		}
		return labels;
	}

	/**
	 * ROADMAP H3 ("Auto-Rollback"): deaktiviert jede {@code packId} aus dem
	 * soeben fehlgeschlagenen Stapel (der wahrscheinlichste Grund fuer den
	 * gescheiterten Reload) und stoesst danach genau einen weiteren Reload an
	 * -- diesmal ohne die zuvor aktivierten, offenbar defekten Pakete, damit
	 * das Spiel in einem sauberen, benutzbaren Zustand landet. Schlaegt auch
	 * dieser Wiederholungsversuch fehl, wird das nur geloggt: kein zweiter
	 * Rollback-Zyklus, um keine Endlosschleife zu riskieren.
	 *
	 * <p>ROADMAP GATE81 ("Fehlgeschlagenes Update/Rollback bleibt unerklärt"):
	 * vorher wurde dieses automatische Zurueck- und Abschalten nur geloggt --
	 * der Spieler sah hoechstens das Wort "Fehlgeschlagen" im Download-Flyout,
	 * das er erst oeffnen musste. Jetzt zeigt {@link #toastPresenter} zusaetzlich
	 * einen eigenen, sofort sichtbaren Toast mit Klartext-Anzeigenamen (ueber
	 * {@link #displayNames}, keine Profil-IDs) und demselben uebersetzten
	 * Grund wie der Fehlschlag-Toast.</p>
	 */
	private void rollBackAndRetry(Set<String> failedPackIds, String reasonKey, String technicalDetail) {
		if (failedPackIds.isEmpty()) {
			// GATE35: in diesem Stapel wurde nichts neu aktiviert (z. B. nur eine
			// Reihenfolge geaendert) -- es gibt kein Paket, das den Fehler
			// eingebracht haben koennte. Nichts abschalten, was vorher lief, und
			// keinen Wiederholungs-Reload, der mit unveraendertem Stand nur
			// erneut scheitern wuerde.
			LOGGER.warn("Vitrine: Reload fehlgeschlagen ({}), aber in diesem Stapel wurde kein Paket neu aktiviert "
					+ "-- nichts zurueckzunehmen, aktive Pakete bleiben unveraendert.", technicalDetail);
			return;
		}
		LOGGER.warn("Vitrine nimmt {} nach dem fehlgeschlagenen Reload automatisch zurueck (Grund: {}) "
				+ "und stoesst einen bereinigten Wiederholungs-Reload an.", failedPackIds, technicalDetail);
		// GATE81: die Anzeigenamen vor dem Deaktivieren auflosen -- das Manifest
		// liegt unveraendert in packs/, der Aktiv-Zustand spielt dafuer keine Rolle.
		Set<String> labels = labelsFor(failedPackIds);
		for (String packId : failedPackIds) {
			boolean changed = packActivation.deactivate(packId);
			LOGGER.debug("Vitrine-Paket '{}' automatisch deaktiviert (Zustand geaendert: {}).", packId, changed);
		}
		// GATE36: dauerhaft festhalten -- sonst zeigt die Bibliothek die Pakete
		// weiter als aktiv, und der naechste Start aktiviert sie erneut.
		rollbackStatePersister.markInactive(failedPackIds);
		// GATE81: sichtbar ohne ein Flyout zu oeffnen -- "<Name> wurde
		// zurueckgenommen und ist jetzt aus. Grund: <Grund>".
		toastPresenter.showRolledBack(labels, reasonKey);

		try {
			reloadTrigger.reloadResources().whenComplete((ignored, retryThrowable) -> {
				if (retryThrowable != null) {
					String retryReason = HotApplyFailure.technicalDetail(retryThrowable);
					LOGGER.error("Vitrine: auch der automatische Wiederholungs-Reload nach dem Rollback von {} ist "
							+ "fehlgeschlagen: {}. Kein weiterer automatischer Versuch -- die zuvor defekten Pakete "
							+ "bleiben deaktiviert, das Spiel bleibt in diesem bereinigten Zustand nutzbar.",
							failedPackIds, retryReason);
				} else {
					LOGGER.info("Vitrine: automatischer Wiederholungs-Reload nach Rollback von {} war erfolgreich -- "
							+ "das Spiel ist wieder in einem sauberen Zustand.", failedPackIds);
				}
			});
		} catch (RuntimeException e) {
			LOGGER.error("Vitrine: der automatische Wiederholungs-Reload nach dem Rollback von {} liess sich nicht "
					+ "einmal anstossen: {}", failedPackIds, HotApplyFailure.technicalDetail(e), e);
		}
	}
}
