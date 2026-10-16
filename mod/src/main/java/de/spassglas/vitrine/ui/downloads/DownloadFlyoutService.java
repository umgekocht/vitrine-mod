package de.spassglas.vitrine.ui.downloads;

/**
 * Haelt das eine {@link DownloadFlyoutModel} fuer den echten Client (ROADMAP
 * GATE4/GATE9/GATE13) -- dasselbe "initialization-on-demand holder"-Muster
 * (JLS 12.4.1) wie {@code HotApplyService}/{@code PackInstallService}.
 *
 * <p>Vor dieser Zeile baute {@code StoreScreen} sein eigenes, nur je Store-
 * Sitzung gehaltenes {@link DownloadFlyoutModel}, das kein anderer Bildschirm
 * erreichen konnte -- ein Katalog-Download, angestossen von der Detailseite
 * ({@code PackageDetailScreen}) oder der Bibliothek ({@code LibraryScreen}),
 * hatte damit keinen Weg, sich im Flyout zu zeigen. Dieser eine geteilte
 * Zustand ist der Anschlusspunkt: jede Stelle, die einen echten Download
 * anstoesst, meldet ihn hier an, und {@code StoreScreen} zeichnet daraus sein
 * Flyout -- unabhaengig davon, welcher Bildschirm den Download gestartet hat.</p>
 */
public final class DownloadFlyoutService {

	private static final class InstanceHolder {
		static final DownloadFlyoutModel INSTANCE = new DownloadFlyoutModel();
	}

	private DownloadFlyoutService() {
	}

	/** Das eine {@link DownloadFlyoutModel} fuer den echten Client. */
	public static DownloadFlyoutModel instance() {
		return InstanceHolder.INSTANCE;
	}
}
