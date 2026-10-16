package de.spassglas.vitrine.hotapply;

/**
 * ROADMAP GATE81 ("Fehlgeschlagenes Update/Rollback bleibt unerklärt"): Seam,
 * der eine Paket-ID (Profil-Schreibweise, z. B. {@code "vitrine/beispiel"})
 * auf einen fuer Spieler lesbaren Anzeigenamen abbildet.
 *
 * <p>Vorher zeigte der Auto-Rollback-Toast ({@code SystemToastHotApplyPresenter})
 * ausschliesslich die technische Profil-ID -- kein Spielername, kein Manifest-
 * Name, nur z. B. {@code "vitrine/beispiel.paket"}. {@link HotApplyCoordinator}
 * kennt selbst keine Datei-/Manifest-Typen (siehe Klassendoku); dieser Seam
 * haelt die Trennung aufrecht, waehrend die Produktion ({@link
 * ManifestPackDisplayNameLookup}) den echten Namen aus dem Manifest liest.</p>
 */
@FunctionalInterface
public interface PackDisplayNameLookup {

	/**
	 * Menschlich lesbarer Anzeigename fuer {@code packId}, oder {@code packId}
	 * selbst, wenn sich keiner ermitteln liess (kein erfundener Name, Charter-
	 * Regel 2) -- die Umsetzung darf nicht werfen.
	 */
	String displayNameFor(String packId);

	/** Kein Namens-Nachschlagen verfuegbar (bestehende Tests/Aufbauten) -- faellt auf die rohe Paket-ID zurueck. */
	PackDisplayNameLookup IDENTITY = packId -> packId;
}
