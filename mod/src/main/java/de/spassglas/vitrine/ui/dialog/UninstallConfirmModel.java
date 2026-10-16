package de.spassglas.vitrine.ui.dialog;

import java.util.Objects;

/**
 * ROADMAP GATE20 / S4: das client-freie Zustandsmodell hinter der
 * Deinstallations-Rueckfrage. Vorher fuehrten sowohl das Ueberlaufmenue der
 * Bibliothek als auch der Knopf auf der Detailseite das Deinstallieren sofort
 * beim Klick aus -- ohne Rueckfrage, ohne Weg zurueck.
 *
 * <p>Das Modell haelt genau eine Entscheidung fest: {@link #confirm()} fuehrt
 * die hinterlegte Aktion <b>genau einmal</b> aus, {@link #cancel()} fuehrt sie
 * <b>nie</b> aus. Beide Wege sind gleich schwer -- es gibt keinen
 * Vorauswahl-Zustand und keinen "nur abbrechbar, wenn ..."-Sonderfall
 * (docs/UI-SPEC.md "8. Fähigkeiten-Dialog": zwei gleichwertige Knoepfe, kein
 * Dunkelmuster).</p>
 *
 * <p>Kennt keinen Minecraft-Typ, damit sich das Verhalten ohne laufenden
 * Client testen laesst (Muster wie {@link CapabilityConsentModel}); das
 * Zeichnen macht {@link ConfirmUninstallDialog}.</p>
 */
public final class UninstallConfirmModel {

	private final String packId;
	private final String displayName;
	private final Runnable onConfirm;

	private boolean decided;
	private boolean confirmed;

	/**
	 * @param packId      Profil-Schreibweise der Paket-ID (wie in {@code state.json})
	 * @param displayName Anzeigename aus dem Manifest -- Paket-Freitext, kein Lang-Key
	 * @param onConfirm   was beim Bestaetigen passiert (das eigentliche Deinstallieren)
	 */
	public UninstallConfirmModel(String packId, String displayName, Runnable onConfirm) {
		this.packId = Objects.requireNonNull(packId, "packId");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
	}

	public String packId() {
		return packId;
	}

	public String displayName() {
		return displayName;
	}

	/** {@code true}, sobald bestaetigt <em>oder</em> abgebrochen wurde. */
	public boolean decided() {
		return decided;
	}

	/** {@code true} nur nach {@link #confirm()} -- der Ruhezustand ist bewusst {@code false}. */
	public boolean confirmed() {
		return confirmed;
	}

	/**
	 * Bestaetigt das Deinstallieren und fuehrt die hinterlegte Aktion aus.
	 * Ein zweiter Aufruf (Doppelklick, Enter plus Klick) tut nichts mehr.
	 */
	public void confirm() {
		if (decided) {
			return;
		}
		decided = true;
		confirmed = true;
		onConfirm.run();
	}

	/**
	 * Bricht ab. Die hinterlegte Aktion laeuft <b>nicht</b> -- weder jetzt noch
	 * spaeter; ein danach folgendes {@link #confirm()} (etwa aus dem
	 * Schliessen-Pfad des Bildschirms) bleibt wirkungslos.
	 */
	public void cancel() {
		if (decided) {
			return;
		}
		decided = true;
		confirmed = false;
	}
}
