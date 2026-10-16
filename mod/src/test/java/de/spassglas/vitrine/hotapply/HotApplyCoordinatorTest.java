package de.spassglas.vitrine.hotapply;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Deckt das Fertig-Kriterium von H2 ab, soweit es ohne laufenden Client
 * pruefbar ist (der wortwoertliche "Textur aendert sich im laufenden Spiel"-
 * Teil bleibt {@code 👁 REVIEW}, siehe ROADMAP.md): {@link HotApplyCoordinator}
 * ruft {@link PackActivation#activate}, sammelt mehrere kurz hintereinander
 * aktivierte Paket-IDs zu genau einem {@link ResourceReloadTrigger#reloadResources()},
 * und zeigt den Ablauf ueber {@link HotApplyToastPresenter} an.
 *
 * <p>{@link FakeDelayedTaskScheduler} ersetzt echtes Warten -- "innerhalb der
 * Debounce-Zeit" wird hier einfach dadurch nachgebildet, dass zwischen
 * mehreren {@link HotApplyCoordinator#requestActivation} nie geflusht wird,
 * bevor der Test es absichtlich tut.</p>
 */
public final class HotApplyCoordinatorTest {

	private RecordingPackActivation packActivation;
	private ControllableReloadTrigger reloadTrigger;
	private RecordingToastPresenter toastPresenter;
	private FakeDelayedTaskScheduler scheduler;
	private HotApplyCoordinator coordinator;

	@BeforeMethod
	public void setUp() {
		packActivation = new RecordingPackActivation();
		reloadTrigger = new ControllableReloadTrigger();
		toastPresenter = new RecordingToastPresenter();
		scheduler = new FakeDelayedTaskScheduler();
		coordinator = new HotApplyCoordinator(packActivation, reloadTrigger, toastPresenter, scheduler);
	}

	// --- eine einzelne Aktivierung loest genau einen Reload aus -------------

	@Test
	public void singleActivationTriggersExactlyOneReload() {
		coordinator.requestActivation("vitrine/beispiel.a");
		scheduler.runAllPending();

		assertEquals(reloadTrigger.callCount(), 1, "eine Aktivierung darf nur einen Reload ausloesen");
		assertEquals(packActivation.activatedIds, List.of("vitrine/beispiel.a"));

		reloadTrigger.completeLastSuccessfully();

		assertEquals(toastPresenter.applyingCalls.size(), 1);
		assertEquals(toastPresenter.applyingCalls.get(0), Set.of("vitrine/beispiel.a"));
		assertEquals(toastPresenter.appliedCalls.size(), 1);
		assertEquals(toastPresenter.appliedCalls.get(0), Set.of("vitrine/beispiel.a"));
		assertTrue(toastPresenter.failedCalls.isEmpty());
	}

	// --- drei Aktivierungen innerhalb der Debounce-Zeit -> ein Reload -------

	@Test
	public void threeActivationsWithinDebounceWindowTriggerExactlyOneReloadForAllThree() {
		coordinator.requestActivation("vitrine/a");
		coordinator.requestActivation("vitrine/b");
		coordinator.requestActivation("vitrine/c");
		// bewusst kein runAllPending() dazwischen -- entspricht "innerhalb
		// der 200-ms-Debounce-Zeit", der letzte Trigger ersetzt jeden zuvor
		// geplanten Lauf (siehe DebouncerTest).
		scheduler.runAllPending();

		assertEquals(reloadTrigger.callCount(), 1,
				"drei Aktivierungen kurz hintereinander duerfen nur einen einzigen Reload ausloesen, nicht drei");
		assertEquals(new LinkedHashSet<>(packActivation.activatedIds),
				new LinkedHashSet<>(List.of("vitrine/a", "vitrine/b", "vitrine/c")),
				"jede einzelne packId muss trotzdem aktiviert worden sein");

		assertEquals(toastPresenter.applyingCalls.size(), 1);
		assertEquals(toastPresenter.applyingCalls.get(0), Set.of("vitrine/a", "vitrine/b", "vitrine/c"),
				"der gesammelte Reload muss fuer alle drei packIds gemeinsam angezeigt werden");

		reloadTrigger.completeLastSuccessfully();

		assertEquals(toastPresenter.appliedCalls.size(), 1);
		assertEquals(toastPresenter.appliedCalls.get(0), Set.of("vitrine/a", "vitrine/b", "vitrine/c"));
	}

	// --- ein fehlgeschlagener Reload zeigt showFailed mit Grund -------------

	@Test
	public void failedReloadShowsFailedWithReason() {
		coordinator.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();

		reloadTrigger.completeLastExceptionally(new RuntimeException("Ressourcen-Manager kaputt"));

		assertEquals(toastPresenter.failedCalls.size(), 1);
		assertEquals(toastPresenter.failedCalls.get(0).packIds(), Set.of("vitrine/kaputt"));
		// ROADMAP GATE11: sichtbar ist ein uebersetzbarer Lang-Key, nie die
		// roh durchgereichte Exception-Meldung und nie ein Klassenname.
		assertEquals(toastPresenter.failedCalls.get(0).reason(), HotApplyFailure.REASON_UNKNOWN);
		assertTrue(toastPresenter.appliedCalls.isEmpty(), "bei einem Fehlschlag darf showApplied nie laufen");
	}

	/**
	 * ROADMAP-GATE11-Beweis am ganzen Weg: eine Exception <em>ohne</em> Meldung
	 * -- genau der Fall, in dem vorher der Java-Klassenname
	 * ("NullPointerException") im Toast landete. Was beim Presenter ankommt,
	 * muss ein Lang-Key sein, den beide Sprachdateien in einen ganzen Satz
	 * uebersetzen.
	 */
	@Test
	public void failedReloadWithoutExceptionMessageShowsATranslatedSentenceNotAClassName() {
		coordinator.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();

		reloadTrigger.completeLastExceptionally(new NullPointerException());

		assertEquals(toastPresenter.failedCalls.size(), 1);
		String reason = toastPresenter.failedCalls.get(0).reason();
		assertEquals(reason, HotApplyFailure.REASON_UNKNOWN);
		for (String locale : java.util.List.of("de_de", "en_us")) {
			String visible = de.spassglas.vitrine.i18n.Translations.get(locale, reason);
			assertNotEquals(visible, reason, locale + ": '" + reason + "' ist nicht uebersetzt");
			assertFalse(visible.contains("NullPointerException"),
					locale + ": der Klassenname darf nicht im Toast stehen, nur im Log");
		}
	}

	// --- ROADMAP H3: Auto-Rollback nach fehlgeschlagenem Reload ------------

	/**
	 * Deckt das woertliche Fertig-Kriterium von H3 ab ("Test mit absichtlich
	 * kaputtem Pack; Spiel bleibt benutzbar"): der erste Reload fuer das
	 * kaputte Paket schlaegt fehl, der Coordinator muss das Paket daraufhin
	 * automatisch deaktivieren, von sich aus einen zweiten Reload anstossen
	 * (diesmal ohne das kaputte Paket) und den Fehler im Klartext (Paket-ID +
	 * Grund) anzeigen -- ohne dass irgendeine Exception nach oben durchbricht.
	 */
	@Test
	public void failedReloadDeactivatesTheBrokenPackAndAutomaticallyRetriesUntilClean() {
		coordinator.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();

		assertEquals(reloadTrigger.callCount(), 1, "der erste (fehlschlagende) Reload muss angestossen worden sein");
		assertTrue(packActivation.deactivatedIds.isEmpty(),
				"vor dem Fehlschlag darf noch nichts automatisch deaktiviert worden sein");

		// Der erste Reload scheitert -- das darf keine Exception aus completeLastExceptionally
		// heraus werfen: die Rollback-/Retry-Logik laeuft synchron im whenComplete-Handler.
		reloadTrigger.completeLastExceptionally(new RuntimeException("Pack-Manifest ist kaputt"));

		assertEquals(toastPresenter.failedCalls.size(), 1, "der Fehlschlag muss dem Nutzer im Klartext gezeigt werden");
		assertEquals(toastPresenter.failedCalls.get(0).packIds(), Set.of("vitrine/kaputt"));
		assertEquals(toastPresenter.failedCalls.get(0).reason(), HotApplyFailure.REASON_UNKNOWN,
				"der Grund muss als uebersetzbarer Lang-Key ankommen (ROADMAP GATE11)");

		assertEquals(packActivation.deactivatedIds, List.of("vitrine/kaputt"),
				"das defekte Paket muss nach dem Fehlschlag automatisch deaktiviert worden sein");

		assertEquals(reloadTrigger.callCount(), 2,
				"nach dem automatischen Deaktivieren muss von sich aus ein zweiter Reload angestossen werden");

		// Der zweite Reload (ohne das kaputte Paket) laeuft sauber durch --
		// das Spiel ist damit wieder in einem benutzbaren Zustand.
		reloadTrigger.completeLastSuccessfully();

		assertEquals(toastPresenter.failedCalls.size(), 1,
				"der erfolgreiche Wiederholungs-Reload darf keinen zweiten Fehlschlag-Toast ausloesen");
		assertTrue(toastPresenter.appliedCalls.isEmpty(),
				"der stille Wiederholungs-Reload zeigt keinen eigenen 'angewendet'-Toast fuer das bereits als kaputt gemeldete Paket");
	}

	/**
	 * Selbst wenn auch der automatische Wiederholungsversuch scheitert, darf
	 * das keine Exception aus dem Coordinator heraus werfen und keinen
	 * zweiten, endlosen Rollback-Zyklus anstossen -- das Spiel bleibt in dem
	 * zuletzt bereinigten (defekte Pakete deaktiviert) Zustand nutzbar.
	 */
	@Test
	public void secondFailureDuringAutoRetryDoesNotThrowOrLoopForever() {
		coordinator.requestActivation("vitrine/immer-kaputt");
		scheduler.runAllPending();

		reloadTrigger.completeLastExceptionally(new RuntimeException("Erster Fehler"));
		assertEquals(reloadTrigger.callCount(), 2, "der automatische Wiederholungsversuch muss angestossen worden sein");
		assertEquals(packActivation.deactivatedIds, List.of("vitrine/immer-kaputt"));

		// Auch der Wiederholungsversuch scheitert -- das darf keine Exception werfen ...
		reloadTrigger.completeLastExceptionally(new RuntimeException("Zweiter Fehler"));

		// ... und keinen dritten Reload-Versuch ausloesen (keine Endlosschleife).
		assertEquals(reloadTrigger.callCount(), 2,
				"nach dem zweiten Fehlschlag darf kein weiterer automatischer Rollback-Zyklus mehr laufen");
		assertEquals(toastPresenter.failedCalls.size(), 1,
				"nur der erste Fehlschlag wird dem Nutzer gezeigt, der stille Retry-Versuch nicht erneut");
	}

	// --- eine unbekannte packId (activate() liefert false) ist kein Fehler --

	@Test
	public void unknownPackIdStillReachesTheReloadBatchWithoutThrowing() {
		packActivation.nextActivateResult = false;

		coordinator.requestActivation("vitrine/unbekannt");
		scheduler.runAllPending();

		assertEquals(reloadTrigger.callCount(), 1,
				"eine unbekannte/bereits aktive packId (activate()==false) ist laut PackActivation kein Fehler");
		reloadTrigger.completeLastSuccessfully();
		assertEquals(toastPresenter.appliedCalls.get(0), Set.of("vitrine/unbekannt"));
	}

	// --- ROADMAP H5: "nur deaktivieren", ohne die Datei anzufassen ----------

	/**
	 * Deckt den H5-Nebenfall ab ("Auch ein 'nur Deaktivieren ohne
	 * Deinstallieren' ... muss weiterhin gehen"): {@link
	 * HotApplyCoordinator#requestDeactivation} deaktiviert sofort und loest
	 * -- genau wie {@link HotApplyCoordinator#requestActivation} -- einen
	 * einzigen, debounced Reload aus, ohne irgendetwas am Zustand der Datei
	 * im Paketordner zu aendern (dafuer ist {@code PackUninstaller}
	 * zustaendig, nicht der Coordinator).
	 */
	@Test
	public void requestDeactivationTriggersExactlyOneReloadAndDeactivatesThePack() {
		coordinator.requestDeactivation("vitrine/beispiel.a");
		scheduler.runAllPending();

		assertEquals(reloadTrigger.callCount(), 1, "eine Deaktivierung darf nur einen Reload ausloesen");
		assertEquals(packActivation.deactivatedIds, List.of("vitrine/beispiel.a"));
		assertTrue(packActivation.activatedIds.isEmpty(), "requestDeactivation darf nichts aktivieren");

		reloadTrigger.completeLastSuccessfully();

		assertEquals(toastPresenter.appliedCalls.size(), 1);
		assertEquals(toastPresenter.appliedCalls.get(0), Set.of("vitrine/beispiel.a"));
	}

	// --- ROADMAP X3: "was hat den Reload gekostet" -------------------------

	/**
	 * Deckt den X3-Diagnose-Meßpunkt ab: vor dem ersten abgeschlossenen Reload
	 * ist {@link HotApplyCoordinator#lastReloadStats()} leer (nichts
	 * vorgegaukelt, Charter-Regel 2); nach einem erfolgreichen Reload traegt er
	 * die tatsaechliche Paketanzahl des Stapels, eine nicht-negative Dauer und
	 * {@code success == true}.
	 */
	@Test
	public void lastReloadStatsIsEmptyBeforeFirstReloadAndFilledAfterSuccess() {
		assertTrue(coordinator.lastReloadStats().isEmpty(), "vor dem ersten Reload gibt es noch keine Kennzahlen");

		coordinator.requestActivation("vitrine/a");
		coordinator.requestActivation("vitrine/b");
		scheduler.runAllPending();
		reloadTrigger.completeLastSuccessfully();

		Optional<HotApplyCoordinator.ReloadStats> stats = coordinator.lastReloadStats();
		assertTrue(stats.isPresent(), "nach einem abgeschlossenen Reload muessen Kennzahlen vorliegen");
		assertEquals(stats.get().packageCount(), 2, "die Paketanzahl muss die des tatsaechlichen Stapels sein");
		assertTrue(stats.get().durationMillis() >= 0, "die gemessene Dauer darf nicht negativ sein");
		assertTrue(stats.get().success(), "ein erfolgreicher Reload muss success == true tragen");
	}

	/** Ein fehlgeschlagener Reload muss ebenfalls Kennzahlen hinterlassen, aber mit {@code success == false}. */
	@Test
	public void lastReloadStatsReflectsAFailedReload() {
		coordinator.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new RuntimeException("kaputt"));

		Optional<HotApplyCoordinator.ReloadStats> stats = coordinator.lastReloadStats();
		assertTrue(stats.isPresent());
		assertFalse(stats.get().success(), "ein fehlgeschlagener Reload muss success == false tragen");
		assertEquals(stats.get().packageCount(), 1);
	}

	// --- GATE35: nur neu aktivierte Pakete werden zurueckgenommen ---------

	@Test
	public void failedReloadAfterPureReorderDeactivatesNothingAndDoesNotRetry() {
		// Nur eine Reihenfolge geaendert -- die IDs sind lediglich zur Anzeige
		// im Stapel, nichts wurde neu aktiviert. Vorher schaltete ein Fehler
		// hier alle diese (laufenden!) Pakete ab.
		coordinator.requestReload(List.of("vitrine/laeuft.a", "vitrine/laeuft.b"));
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new IllegalStateException("Reload kaputt"));

		assertTrue(packActivation.deactivatedIds.isEmpty(),
				"eine reine Reihenfolgeaenderung hat nichts eingebracht, das man zuruecknehmen koennte");
		assertEquals(reloadTrigger.callCount(), 1,
				"kein Wiederholungs-Reload mit unveraendertem Stand -- der wuerde nur erneut scheitern");
		assertEquals(toastPresenter.failedCalls.size(), 1, "der Fehler wird trotzdem angezeigt");
	}

	@Test
	public void failedReloadInMixedBatchRollsBackOnlyTheNewlyActivatedPack() {
		packActivation.nextActivateResult = false; // war schon aktiv -- keine Aenderung
		coordinator.requestActivation("vitrine/schon-aktiv");
		packActivation.nextActivateResult = true;
		coordinator.requestActivation("vitrine/neu");
		coordinator.requestReload(List.of("vitrine/nur-anzeige"));
		scheduler.runAllPending();

		reloadTrigger.completeLastExceptionally(new IllegalStateException("Reload kaputt"));

		assertEquals(packActivation.deactivatedIds, List.of("vitrine/neu"),
				"nur das neu aktivierte Paket kann den Fehler eingebracht haben");
		assertEquals(reloadTrigger.callCount(), 2, "danach genau ein bereinigter Wiederholungs-Reload");
	}

	@Test
	public void activatedAndDeactivatedInTheSameWindowIsNotRolledBackAgain() {
		coordinator.requestActivation("vitrine/kurz");
		coordinator.requestDeactivation("vitrine/kurz");
		scheduler.runAllPending();
		packActivation.deactivatedIds.clear(); // die gewollte Deaktivierung von eben

		reloadTrigger.completeLastExceptionally(new IllegalStateException("Reload kaputt"));

		assertTrue(packActivation.deactivatedIds.isEmpty(),
				"im selben Fenster wieder abgeschaltet -- es gibt nichts mehr zurueckzunehmen");
	}

	// --- GATE36: der Rollback landet in state.json -------------------------

	@Test
	public void rollbackIsHandedToTheStatePersister() {
		List<Set<String>> persisted = new ArrayList<>();
		HotApplyCoordinator withPersister = new HotApplyCoordinator(packActivation, reloadTrigger, toastPresenter,
				scheduler, persisted::add);

		withPersister.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new IllegalStateException("Reload kaputt"));

		assertEquals(persisted, List.of(Set.of("vitrine/kaputt")),
				"ohne Festhalten zeigt die Bibliothek das Paket weiter als aktiv");
	}

	@Test
	public void failedPureReorderPersistsNothing() {
		List<Set<String>> persisted = new ArrayList<>();
		HotApplyCoordinator withPersister = new HotApplyCoordinator(packActivation, reloadTrigger, toastPresenter,
				scheduler, persisted::add);

		withPersister.requestReload(List.of("vitrine/laeuft"));
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new IllegalStateException("Reload kaputt"));

		assertTrue(persisted.isEmpty(), "nichts zurueckgenommen -- also auch nichts festzuhalten");
	}

	// --- GATE81: sichtbare Rollback-Meldung mit echtem Namen + Grund --------

	/**
	 * Woertliches Fertig-Kriterium von GATE81: der Auto-Rollback (H3) zeigt
	 * jetzt einen eigenen, sofort sichtbaren Toast -- "<Name> wurde
	 * zurückgenommen und ist jetzt aus. Grund: <Grund>" -- mit dem echten
	 * Anzeigenamen (ueber {@link PackDisplayNameLookup}), nicht der technischen
	 * Profil-ID, und ohne dass irgendein Flyout geoeffnet werden muss (der
	 * Toast kommt direkt vom {@link HotApplyToastPresenter}, nicht vom
	 * Download-Flyout).
	 */
	@Test
	public void rollbackShowsTheRealPackageNameAndReasonWithoutRequiringAFlyout() {
		PackDisplayNameLookup names = packId -> "vitrine/kaputt".equals(packId) ? "Mein Test-Paket" : packId;
		HotApplyCoordinator withNames = new HotApplyCoordinator(packActivation, reloadTrigger, toastPresenter,
				scheduler, RollbackStatePersister.none(), names);

		withNames.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new RuntimeException("Pack-Manifest ist kaputt"));

		assertEquals(toastPresenter.rolledBackCalls.size(), 1,
				"der Auto-Rollback muss einen eigenen, sofort sichtbaren Toast zeigen");
		FailedCall rolledBack = toastPresenter.rolledBackCalls.get(0);
		assertEquals(rolledBack.packIds(), Set.of("Mein Test-Paket"),
				"der Toast muss den echten Anzeigenamen zeigen, nicht die technische Profil-ID");
		assertEquals(rolledBack.reason(), HotApplyFailure.REASON_UNKNOWN,
				"der Grund muss als uebersetzbarer Lang-Key ankommen (wie beim Fehlschlag-Toast)");

		for (String locale : java.util.List.of("de_de", "en_us")) {
			String reasonText = de.spassglas.vitrine.i18n.Translations.get(locale, rolledBack.reason());
			String visible = de.spassglas.vitrine.i18n.Translations.format(locale,
					"vitrine.hotapply.rolled_back.description", "Mein Test-Paket", reasonText);
			assertTrue(visible.contains("Mein Test-Paket"), locale + ": der Name muss im Satz stehen: " + visible);
			assertFalse(visible.contains("vitrine/kaputt"),
					locale + ": keine technische Profil-ID im sichtbaren Satz: " + visible);
		}
	}

	/** Auch das allgemeine "showFailed" muss den Anzeigenamen zeigen, nicht die Profil-ID (ROADMAP GATE81). */
	@Test
	public void failedReloadShowsTheRealDisplayNameInsteadOfTheProfileId() {
		PackDisplayNameLookup names = packId -> "vitrine/kaputt".equals(packId) ? "Mein Test-Paket" : packId;
		HotApplyCoordinator withNames = new HotApplyCoordinator(packActivation, reloadTrigger, toastPresenter,
				scheduler, RollbackStatePersister.none(), names);

		withNames.requestActivation("vitrine/kaputt");
		scheduler.runAllPending();
		reloadTrigger.completeLastExceptionally(new RuntimeException("Pack-Manifest ist kaputt"));

		assertEquals(toastPresenter.failedCalls.size(), 1);
		assertEquals(toastPresenter.failedCalls.get(0).packIds(), Set.of("Mein Test-Paket"),
				"auch der Fehlschlag-Toast muss den echten Namen zeigen, nicht 'vitrine/kaputt'");
	}

	// --- Test-Doubles fuer alle vier Schnittstellen -------------------------

	private static final class RecordingPackActivation implements PackActivation {
		private final List<String> activatedIds = new ArrayList<>();
		private final List<String> deactivatedIds = new ArrayList<>();
		private boolean nextActivateResult = true;

		@Override
		public boolean activate(String packId) {
			activatedIds.add(packId);
			return nextActivateResult;
		}

		@Override
		public boolean deactivate(String packId) {
			deactivatedIds.add(packId);
			return true;
		}
	}

	private static final class ControllableReloadTrigger implements ResourceReloadTrigger {
		private final List<CompletableFuture<Void>> issuedFutures = new ArrayList<>();

		@Override
		public CompletableFuture<Void> reloadResources() {
			CompletableFuture<Void> future = new CompletableFuture<>();
			issuedFutures.add(future);
			return future;
		}

		int callCount() {
			return issuedFutures.size();
		}

		void completeLastSuccessfully() {
			issuedFutures.get(issuedFutures.size() - 1).complete(null);
		}

		void completeLastExceptionally(Throwable throwable) {
			issuedFutures.get(issuedFutures.size() - 1).completeExceptionally(throwable);
		}
	}

	private record FailedCall(Set<String> packIds, String reason) {
	}

	private static final class RecordingToastPresenter implements HotApplyToastPresenter {
		private final List<Set<String>> applyingCalls = new ArrayList<>();
		private final List<Set<String>> appliedCalls = new ArrayList<>();
		private final List<FailedCall> failedCalls = new ArrayList<>();
		private final List<FailedCall> rolledBackCalls = new ArrayList<>();

		@Override
		public void showApplying(Set<String> packIds) {
			applyingCalls.add(Set.copyOf(packIds));
		}

		@Override
		public void showApplied(Set<String> packIds) {
			appliedCalls.add(Set.copyOf(packIds));
		}

		@Override
		public void showFailed(Set<String> packLabels, String reason) {
			failedCalls.add(new FailedCall(Set.copyOf(packLabels), reason));
		}

		@Override
		public void showRolledBack(Set<String> packLabels, String reason) {
			rolledBackCalls.add(new FailedCall(Set.copyOf(packLabels), reason));
		}
	}
}
