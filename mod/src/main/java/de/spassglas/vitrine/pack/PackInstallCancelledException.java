package de.spassglas.vitrine.pack;

/**
 * ROADMAP GATE9/GATE10: der Nutzer hat einen laufenden Katalog-Download ueber
 * das {@code CancellationToken} des Download-Flyouts (S8) abgebrochen, <em>bevor</em>
 * er fertig/verifiziert war. Eine eigene Unterklasse von {@link PackInstallException},
 * damit Aufrufer (z. B. {@code PackageDetailScreen}) einen gewollten Nutzer-Abbruch
 * von einem echten Fehler (Zip-Slip, kaputte Signatur, Netzfehler, ...)
 * unterscheiden koennen, ohne den Klartext-Fehlerzustand ({@code showInstallError})
 * fuer einen simplen "abgebrochen" zu zeigen.
 *
 * <p>Wie bei jedem anderen Abbruch (ROADMAP K3) bleibt {@code packs/}
 * unangetastet -- {@link CatalogPackInstaller} wirft dies, bevor irgendetwas
 * nach {@code config/vitrine/packs/} kopiert wird.</p>
 */
public final class PackInstallCancelledException extends PackInstallException {

	public PackInstallCancelledException(String message) {
		super(message);
	}
}
