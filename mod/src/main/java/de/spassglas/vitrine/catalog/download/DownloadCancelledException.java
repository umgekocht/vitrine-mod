package de.spassglas.vitrine.catalog.download;

/**
 * Internes Kontrollfluss-Signal: der Download wurde ueber ein
 * {@link CancellationToken} abgebrochen. Wird von
 * {@link CancellationToken#throwIfCancelled()} geworfen und von
 * {@link PackageDownloader} abgefangen, das daraufhin {@code .part} loescht und
 * ein {@link DownloadOutcome} mit Status {@code CANCELLED} zurueckgibt -- nach
 * aussen fliegt diese Ausnahme nicht.
 */
public final class DownloadCancelledException extends Exception {

	public DownloadCancelledException() {
		super("Download vom Nutzer abgebrochen");
	}
}
