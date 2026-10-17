package de.spassglas.vitrine.ui.dialog;

import java.util.Objects;

/**
 * ROADMAP GATE86: das client-freie Zustandsmodell hinter der Rueckfrage,
 * bevor ein Download-Flyout-Eintrag abgebrochen/entfernt wird. Vorher
 * reichte ein Klick irgendwo auf die ganze Zeile ({@code
 * StoreScreen#flyoutRowAt}), um {@code DownloadFlyoutModel#cancel} sofort
 * auszufuehren -- kein Weg zurueck bei einem Fehlgriff.
 *
 * <p>Genau dasselbe Muster wie {@link UninstallConfirmModel}: {@link
 * #confirm()} fuehrt die hinterlegte Aktion <b>genau einmal</b> aus, {@link
 * #cancel()} fuehrt sie <b>nie</b> aus. Ein eigener Typ statt
 * Wiederverwendung von {@code UninstallConfirmModel}, weil in diesem Code
 * jeder Rueckfrage-Dialog sein eigenes, nach der Aktion benanntes Modell
 * bekommt (siehe auch {@code CapabilityConsentModel}) -- das haelt die
 * Bezeichner ehrlich zu dem, was sie tatsaechlich bestaetigen.</p>
 */
public final class CancelDownloadConfirmModel {

	private final String packId;
	private final String displayName;
	private final Runnable onConfirm;

	private boolean decided;
	private boolean confirmed;

	/**
	 * @param packId      Profil-Schreibweise der Paket-ID der Download-Zeile
	 * @param displayName Anzeigename aus der Download-Zeile -- Paket-Freitext, kein Lang-Key
	 * @param onConfirm   was beim Bestaetigen passiert (das eigentliche Abbrechen/Entfernen)
	 */
	public CancelDownloadConfirmModel(String packId, String displayName, Runnable onConfirm) {
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
	 * Bestaetigt das Abbrechen und fuehrt die hinterlegte Aktion aus. Ein
	 * zweiter Aufruf (Doppelklick, Enter plus Klick) tut nichts mehr.
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
	 * Bricht ab. Die hinterlegte Aktion laeuft <b>nicht</b> -- weder jetzt
	 * noch spaeter.
	 */
	public void cancel() {
		if (decided) {
			return;
		}
		decided = true;
		confirmed = false;
	}
}
