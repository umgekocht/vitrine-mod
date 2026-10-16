package de.spassglas.vitrine.catalog.download;

import de.spassglas.vitrine.vpkg.VpkgIntegrityException;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Predicate;

/**
 * Orchestriert den Paket-Download (ROADMAP K3): Fortschritt, Abbruch,
 * Wiederaufnahme ueber eine {@code .part}-Datei, atomares Umbenennen und erst
 * <em>danach</em> Pruefsumme + Signatur.
 *
 * <p>Verdrahtet die vorhandenen Bausteine ({@link DownloadTransport},
 * {@link DownloadHostWhitelist}, {@link CancellationToken}, {@link DownloadProgress},
 * {@code VpkgIntegrityValidator}) zu genau einem Ablauf:</p>
 *
 * <ol>
 *   <li>Liegt {@code targetVpkg} schon verifiziert vor: sofort
 *       {@link DownloadOutcome#alreadyPresent()}, der Transport wird nie
 *       geoeffnet.</li>
 *   <li>Sonst wird in {@code &lt;ziel&gt;.vpkg.part} geschrieben: eine bereits
 *       vorhandene {@code .part} wird per {@code Range} fortgesetzt (HTTP 206),
 *       ansonsten von vorn (HTTP 200).</li>
 *   <li>Weiterleitungen werden nur verfolgt, solange jedes Ziel wieder auf der
 *       {@code hostRule} liegt -- ein fremder Host wird hart abgelehnt.</li>
 *   <li>Vor und waehrend des Streamens greifen die Groessenschranken
 *       ({@link DownloadEndpoints#MAX_PACKAGE_BYTES}, {@code declaredSize},
 *       {@code Content-Length}).</li>
 *   <li>Nach vollstaendigem Schreiben wird die {@code .part} atomar zu
 *       {@code targetVpkg} umbenannt und dann geprueft. Schlaegt die Pruefung
 *       fehl, wird die umbenannte Datei wieder geloescht.</li>
 * </ol>
 *
 * <p><b>Fertig-Kriterium der Zeile:</b> ein Abbruch ueber das
 * {@link CancellationToken} -- egal ob vor, waehrend oder kurz nach dem Streamen
 * -- hinterlaesst <em>weder</em> eine {@code .part} <em>noch</em> eine fertige
 * Datei. Nur ein Netz-Abbruch ({@link DownloadException.Kind#NETWORK}) laesst die
 * {@code .part} fuer eine spaetere Wiederaufnahme liegen.</p>
 */
public final class PackageDownloader {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Download");

	private final DownloadTransport transport;
	private final Predicate<URI> hostRule;
	private final PublicKey signingPublicKey;

	private PackageDownloader(DownloadTransport transport, Predicate<URI> hostRule, PublicKey signingPublicKey) {
		this.transport = Objects.requireNonNull(transport, "transport");
		this.hostRule = Objects.requireNonNull(hostRule, "hostRule");
		// signingPublicKey darf null sein: dann schlaegt die Verifikation
		// ausdruecklich fehl (Default ist Ablehnung, Charter-Regel 3).
		this.signingPublicKey = signingPublicKey;
	}

	/**
	 * Produktions-Verdrahtung: echter {@link JdkDownloadTransport} und die
	 * GitHub-Release-Asset-Whitelist. Der oeffentliche Signaturschluessel kommt
	 * laut Charter aus dem Jar; ihn hierher zu reichen (statt ihn selbst zu
	 * laden) ist Sache des Aufrufers -- diese Verdrahtung an die Store-UI ist K4.
	 */
	public static PackageDownloader production(PublicKey signingPublicKey) {
		return new PackageDownloader(new JdkDownloadTransport(), DownloadHostWhitelist.RELEASE_ASSETS, signingPublicKey);
	}

	/**
	 * Fabrik fuer Tests: beliebiger {@link DownloadTransport}, beliebige
	 * Host-Regel (z. B. {@code uri -> true} fuer eine lokale {@code file://}-Quelle)
	 * und ein Test-Schluesselpaar. Bewusst {@code public} (statt paket-privat):
	 * {@code CatalogPackInstaller} (ROADMAP G3) baut sich damit in seinen
	 * eigenen Tests einen Downloader gegen eine lokale {@code file://}-Quelle,
	 * ohne dafuer im Paket {@code catalog.download} liegen zu muessen.
	 */
	public static PackageDownloader forTesting(DownloadTransport transport, Predicate<URI> hostRule,
			PublicKey signingPublicKey) {
		return new PackageDownloader(transport, hostRule, signingPublicKey);
	}

	/**
	 * Laedt {@code request.vpkgUrl()} nach {@code targetVpkg}. Blockiert bis der
	 * Download fertig, abgebrochen oder fehlgeschlagen ist.
	 *
	 * @param request    Ziel-URL, erwartete Pruefsumme, Signatur, ggf. Groesse.
	 * @param targetVpkg endgueltiger Pfad der fertigen, verifizierten Datei.
	 * @param token      Abbruch-Token; {@link CancellationToken#cancel()} aus
	 *                   einem anderen Thread beendet den Download sauber.
	 * @param progress   Fortschritts-Rueckruf mit <em>echt gezaehlten</em> Bytes;
	 *                   {@code null} wird als {@link DownloadProgress#NOOP}
	 *                   behandelt.
	 * @return {@link DownloadOutcome.Status#COMPLETED} (Datei liegt verifiziert)
	 *         oder {@link DownloadOutcome.Status#CANCELLED} (nichts bleibt liegen).
	 * @throws DownloadException bei Netz-, Protokoll-/Groessen- oder
	 *                           Verifikationsfehler.
	 */
	public DownloadOutcome download(DownloadRequest request, Path targetVpkg, CancellationToken token,
			DownloadProgress progress) throws DownloadException {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(targetVpkg, "targetVpkg");
		Objects.requireNonNull(token, "token");
		DownloadProgress prog = progress != null ? progress : DownloadProgress.NOOP;

		// --- 1. Idempotenz: schon verifiziert da -> Transport nie oeffnen ----
		if (Files.isRegularFile(targetVpkg) && VpkgIntegrityValidator
				.validate(targetVpkg, request.sha256Hex(), request.signatureBytes(), signingPublicKey)
				.isValid()) {
			LOGGER.info("Paket '{}' liegt bereits verifiziert vor -- kein Download noetig.", targetVpkg);
			return DownloadOutcome.completed(targetVpkg, true);
		}

		// --- 2. .part-Pfad + Zielordner ------------------------------------
		Path part = targetVpkg.resolveSibling(targetVpkg.getFileName().toString() + DownloadEndpoints.PART_SUFFIX);
		Path parent = targetVpkg.toAbsolutePath().getParent();
		try {
			if (parent != null) {
				Files.createDirectories(parent);
			}
		} catch (IOException e) {
			throw new DownloadException(DownloadException.Kind.NETWORK,
					"Zielordner '" + parent + "' liess sich nicht anlegen: " + e.getMessage(), e);
		}

		// --- 3. Resume-Offset --------------------------------------------
		long resumeFromOffset;
		try {
			resumeFromOffset = Files.exists(part) ? Files.size(part) : 0L;
		} catch (IOException e) {
			throw new DownloadException(DownloadException.Kind.NETWORK,
					"Groesse der Teil-Datei '" + part + "' liess sich nicht bestimmen: " + e.getMessage(), e);
		}
		if (isCancelled(token)) {
			deleteQuietly(part);
			LOGGER.info("Download von '{}' schon vor dem Verbindungsaufbau abgebrochen.", request.vpkgUrl());
			return DownloadOutcome.cancelled();
		}

		// --- 4./5. Redirect-Schleife + Statuscode -----------------------
		URI currentUri = request.vpkgUrl();
		int redirects = 0;
		boolean retriedFrom416 = false;

		while (true) {
			if (!hostRule.test(currentUri)) {
				deleteQuietly(part);
				LOGGER.warn("Download-URL '{}' steht nicht auf der Whitelist -- abgelehnt.", currentUri);
				throw new DownloadException(DownloadException.Kind.BROKEN,
						"Download-URL nicht erlaubt: " + currentUri);
			}
			LOGGER.info("Download {} (ab Byte {})", currentUri, resumeFromOffset);

			DownloadResponse opened;
			try {
				opened = transport.open(currentUri, resumeFromOffset);
			} catch (IOException e) {
				LOGGER.warn("Verbindung zu '{}' fehlgeschlagen: {} -- .part bleibt fuer Wiederaufnahme liegen.",
						currentUri, e.toString());
				throw new DownloadException(DownloadException.Kind.NETWORK,
						"Verbindung zu '" + currentUri + "' fehlgeschlagen: " + e.getMessage(), e);
			}

			DownloadResponse response = opened;
			try {
				int status = response.status();

				if (response.isRedirect()) {
					redirects++;
					if (redirects > DownloadEndpoints.MAX_REDIRECTS) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN,
								"Zu viele Weiterleitungen (> " + DownloadEndpoints.MAX_REDIRECTS + ") ab "
										+ request.vpkgUrl());
					}
					String location = response.location().map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
					if (location == null) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN,
								"Weiterleitung (HTTP " + status + ") ohne Location-Header von " + currentUri);
					}
					URI next;
					try {
						next = currentUri.resolve(location);
					} catch (IllegalArgumentException e) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN,
								"Weiterleitungsziel '" + location + "' ist keine gueltige URI: " + e.getMessage(), e);
					}
					if (!hostRule.test(next)) {
						deleteQuietly(part);
						LOGGER.warn("Weiterleitung von '{}' auf nicht erlaubtes Ziel '{}' -- nicht verfolgt.",
								currentUri, next);
						throw new DownloadException(DownloadException.Kind.BROKEN,
								"Weiterleitung auf nicht erlaubtes Ziel: " + next);
					}
					LOGGER.info("Weiterleitung {} -> {} (geprueft, auf der Whitelist)", currentUri, next);
					currentUri = next;
					continue;
				}

				if (status == 416) {
					// .part ist (mindestens) vollstaendig -- einmal von vorn.
					if (retriedFrom416) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN,
								"Server antwortet wiederholt mit HTTP 416 fuer " + currentUri);
					}
					retriedFrom416 = true;
					LOGGER.info("HTTP 416 fuer '{}' -- .part verwerfen und von vorn anfordern.", currentUri);
					deleteQuietly(part);
					resumeFromOffset = 0L;
					continue;
				}

				if (status != 200 && status != 206) {
					deleteQuietly(part);
					throw new DownloadException(DownloadException.Kind.BROKEN, "HTTP " + status + " von " + currentUri);
				}

				long writeStart;
				boolean append;
				if (status == 206) {
					long rangeStart = response.contentRangeStart().orElse(-1L);
					if (rangeStart != resumeFromOffset) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN, "HTTP 206 mit Content-Range-Start "
								+ rangeStart + ", erwartet wurde " + resumeFromOffset);
					}
					writeStart = resumeFromOffset;
					append = true;
				} else {
					if (resumeFromOffset > 0) {
						LOGGER.info("HTTP 200 trotz Range-Anfrage -- .part verwerfen und ganz neu schreiben.");
						deleteQuietly(part);
					}
					writeStart = 0L;
					append = false;
				}

				// --- 6. Groessenschranke vor dem Streamen ----------------
				long expectedTotal = -1L;
				OptionalLong contentLength = response.contentLength();
				if (contentLength.isPresent()) {
					expectedTotal = writeStart + contentLength.getAsLong();
				}
				if (request.hasDeclaredSize()) {
					long declared = request.declaredSize();
					if (expectedTotal >= 0 && expectedTotal != declared) {
						deleteQuietly(part);
						throw new DownloadException(DownloadException.Kind.BROKEN, "Erwartete Gesamtgroesse "
								+ expectedTotal + " weicht von der angemeldeten Groesse " + declared + " ab");
					}
					expectedTotal = declared;
				}
				if (expectedTotal > DownloadEndpoints.MAX_PACKAGE_BYTES) {
					deleteQuietly(part);
					throw new DownloadException(DownloadException.Kind.BROKEN, "Erwartete Gesamtgroesse " + expectedTotal
							+ " ueberschreitet die Obergrenze " + DownloadEndpoints.MAX_PACKAGE_BYTES);
				}
				final long total = expectedTotal;

				// --- 7./8./9. Streamen in die .part ---------------------
				long bytesWritten = writeStart;
				InputStream body = response.body();
				Runnable closeBody = () -> {
					try {
						body.close();
					} catch (IOException e) {
						LOGGER.debug("Body-Stream nach Abbruch schliessen: {}", e.toString());
					}
				};
				token.onCancel(closeBody);
				OpenOption[] opts = append
						? new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
								StandardOpenOption.APPEND }
						: new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
								StandardOpenOption.TRUNCATE_EXISTING };
				try (OutputStream out = Files.newOutputStream(part, opts)) {
					token.throwIfCancelled();
					byte[] buffer = new byte[DownloadEndpoints.TRANSFER_BUFFER_BYTES];
					prog.onProgress(bytesWritten, total);
					int read;
					while ((read = body.read(buffer)) != -1) {
						out.write(buffer, 0, read);
						bytesWritten += read;
						if (bytesWritten > DownloadEndpoints.MAX_PACKAGE_BYTES || (total >= 0 && bytesWritten > total)) {
							throw new OversizeSignal(bytesWritten);
						}
						token.throwIfCancelled();
						prog.onProgress(bytesWritten, total);
					}
					out.flush();
				} catch (DownloadCancelledException e) {
					deleteQuietly(part);
					LOGGER.info("Download '{}' vom Nutzer abgebrochen -- .part entfernt, keine halbe Installation.",
							currentUri);
					return DownloadOutcome.cancelled();
				} catch (OversizeSignal e) {
					deleteQuietly(part);
					throw new DownloadException(DownloadException.Kind.BROKEN,
							"Server lieferte mehr Bytes als angekuendigt (mindestens " + e.bytes + ")");
				} catch (IOException e) {
					if (isCancelled(token)) {
						// cancel() hat den Body-Stream geschlossen -> das
						// blockierende read brach mit IOException ab. Das ist ein
						// Nutzer-Abbruch, kein Netz-Abbruch: nichts bleibt liegen.
						deleteQuietly(part);
						LOGGER.info("Download '{}' waehrend des Lesens abgebrochen -- .part entfernt.", currentUri);
						return DownloadOutcome.cancelled();
					}
					LOGGER.warn("Uebertragung von '{}' brach ab: {} -- .part bleibt fuer Wiederaufnahme liegen.",
							currentUri, e.toString());
					throw new DownloadException(DownloadException.Kind.NETWORK,
							"Uebertragung von '" + currentUri + "' brach ab: " + e.getMessage(), e);
				} finally {
					token.removeListener(closeBody);
				}

				// --- 10. Verifizieren VOR dem Umbenennen (GATE63) ------
				// Unter dem endgueltigen Namen liegt nie eine ungepruefte Datei,
				// auch nicht fuer einen Augenblick.
				try {
					VpkgIntegrityValidator.requireValid(part, request.sha256Hex(), request.signatureBytes(),
							signingPublicKey);
				} catch (VpkgIntegrityException e) {
					deleteQuietly(part);
					LOGGER.warn("Heruntergeladenes Paket '{}' nicht verifizierbar: {} -- Teil-Datei entfernt.",
							part, e.getMessage());
					throw new DownloadException(DownloadException.Kind.VERIFICATION,
							"Pruefsumme/Signatur des heruntergeladenen Pakets ungueltig: " + e.getMessage(), e);
				}

				// --- 11. atomares Umbenennen ---------------------------
				try {
					moveIntoPlace(part, targetVpkg);
				} catch (IOException e) {
					LOGGER.warn("Umbenennen von '{}' nach '{}' fehlgeschlagen: {}", part, targetVpkg, e.toString());
					throw new DownloadException(DownloadException.Kind.NETWORK, "Fertige Teil-Datei liess sich nicht "
							+ "nach '" + targetVpkg + "' umbenennen: " + e.getMessage(), e);
				}

				// --- 12. Erfolg --------------------------------------
				LOGGER.info("Download abgeschlossen und verifiziert: {} ({} Bytes) von {}", targetVpkg, bytesWritten,
						currentUri);
				return DownloadOutcome.completed(targetVpkg, false);
			} finally {
				closeQuietly(response);
			}
		}
	}

	private static boolean isCancelled(CancellationToken token) {
		return token.isCancelled();
	}

	private static void closeQuietly(DownloadResponse response) {
		try {
			response.close();
		} catch (IOException e) {
			LOGGER.debug("Antwort-Stream schliessen: {}", e.toString());
		}
	}

	/**
	 * Verschiebt {@code source} nach {@code target} -- bevorzugt atomar; wo das
	 * Dateisystem {@code ATOMIC_MOVE} nicht unterstuetzt, mit
	 * {@code REPLACE_EXISTING} (Muster aus {@code PackInstaller.copyAtomically}).
	 */
	private static void moveIntoPlace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomares Verschieben von '{}' nach '{}' nicht unterstuetzt ({}) -- gewoehnlicher Move mit "
					+ "REPLACE_EXISTING.", source, target, e.toString());
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("Aufraeumen von '{}' fehlgeschlagen: {}", path, e.toString());
		}
	}

	/** Internes Signal: der Server hat mehr Bytes geliefert als angekuendigt. */
	private static final class OversizeSignal extends RuntimeException {
		private final long bytes;

		OversizeSignal(long bytes) {
			super("Body ueberschreitet die erwartete Gesamtgroesse bei " + bytes + " Bytes");
			this.bytes = bytes;
		}
	}
}
