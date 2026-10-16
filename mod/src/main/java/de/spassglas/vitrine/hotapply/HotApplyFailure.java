package de.spassglas.vitrine.hotapply;

import de.spassglas.vitrine.manifest.ManifestParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipException;

/**
 * ROADMAP GATE11: uebersetzt eine beliebige {@link Throwable} in einen
 * <em>Lang-Key</em> fuer die sichtbare Oberflaeche (Toast, Zustandszeile) und
 * getrennt davon in eine rein technische Zeile fuer das Log.
 *
 * <p>Vorher endete der Weg bei {@code actual.getClass().getSimpleName()}: im
 * Toast stand dann woertlich „NullPointerException". Solche Klassennamen,
 * Stapelspuren und roh durchgereichte Exception-Meldungen sind
 * Entwicklersprache -- sie gehoeren ins Log, nicht vor den Spieler. Sichtbar
 * ist ab jetzt ausschliesslich einer der vier {@code REASON_*}-Keys, deren
 * Text in {@code lang/de_de.json} und {@code lang/en_us.json} steht (F3: kein
 * hartkodierter Anzeigetext).</p>
 *
 * <p>Kennt keine Minecraft-Typen: die Uebersetzung selbst macht erst der
 * Presenter ({@code SystemToastHotApplyPresenter}), damit {@link
 * HotApplyCoordinator} weiterhin ohne laufenden Client testbar bleibt.</p>
 */
public final class HotApplyFailure {

	/** Lese-/Schreibfehler auf der Platte (Datei weg, gesperrt, Rechte, voller Datentraeger). */
	public static final String REASON_IO = "vitrine.hotapply.reason.io";
	/** Das Paket selbst ist beschaedigt: kaputtes ZIP oder unlesbares Manifest. */
	public static final String REASON_PACKAGE_BROKEN = "vitrine.hotapply.reason.package_broken";
	/** Dem Spiel ist der Arbeitsspeicher ausgegangen. */
	public static final String REASON_OUT_OF_MEMORY = "vitrine.hotapply.reason.out_of_memory";
	/** Alles andere -- der Grund steht nur im Log. */
	public static final String REASON_UNKNOWN = "vitrine.hotapply.reason.unknown";

	/** Schutz gegen zyklische/absurd tief verschachtelte Ursachenketten. */
	private static final int MAX_CAUSE_DEPTH = 16;

	private HotApplyFailure() {
	}

	/**
	 * Schaelt die reinen Transport-Huellen ({@link CompletionException},
	 * {@link ExecutionException}) ab und liefert die eigentliche Ursache.
	 */
	public static Throwable unwrap(Throwable throwable) {
		Throwable actual = throwable;
		int depth = 0;
		while (actual != null && depth++ < MAX_CAUSE_DEPTH
				&& (actual instanceof CompletionException || actual instanceof ExecutionException)
				&& actual.getCause() != null && actual.getCause() != actual) {
			actual = actual.getCause();
		}
		return actual;
	}

	/**
	 * Lang-Key des sichtbaren Klartext-Satzes fuer {@code throwable}. Nie
	 * {@code null}, nie ein Klassenname, nie eine roh durchgereichte
	 * Exception-Meldung.
	 */
	public static String reasonKey(Throwable throwable) {
		if (throwable == null) {
			return REASON_UNKNOWN;
		}
		Throwable actual = unwrap(throwable);
		if (containsCause(actual, OutOfMemoryError.class)) {
			return REASON_OUT_OF_MEMORY;
		}
		if (containsCause(actual, ZipException.class) || containsCause(actual, ManifestParseException.class)) {
			return REASON_PACKAGE_BROKEN;
		}
		if (containsCause(actual, IOException.class) || containsCause(actual, UncheckedIOException.class)) {
			return REASON_IO;
		}
		return REASON_UNKNOWN;
	}

	/**
	 * Technische Beschreibung <em>nur fuer das Log</em>: voller Klassenname
	 * plus, falls vorhanden, die Original-Meldung. Diese Zeichenkette wird
	 * bewusst nirgends in die Oberflaeche gereicht.
	 */
	public static String technicalDetail(Throwable throwable) {
		if (throwable == null) {
			return "<keine Exception>";
		}
		Throwable actual = unwrap(throwable);
		String message = actual.getMessage();
		return (message != null && !message.isBlank())
				? actual.getClass().getName() + ": " + message
				: actual.getClass().getName();
	}

	/** {@code true}, wenn {@code type} irgendwo in der Ursachenkette von {@code throwable} steckt. */
	private static boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
		Throwable current = throwable;
		int depth = 0;
		while (current != null && depth++ < MAX_CAUSE_DEPTH) {
			if (type.isInstance(current)) {
				return true;
			}
			Throwable cause = current.getCause();
			current = (cause == current) ? null : cause;
		}
		return false;
	}
}
