package de.spassglas.vitrine.hotapply;

import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * ROADMAP-GATE11-Beweis fuer die Einstufung: {@link HotApplyFailure} liefert
 * fuer jede der vier Fehlerarten (I/O, kaputtes Paket, Speicher, unbekannt)
 * einen <em>uebersetzten</em> Lang-Key und niemals einen Java-Klassennamen.
 * Vorher stand bei einer Exception ohne Meldung woertlich
 * "NullPointerException" im Toast.
 */
public final class HotApplyFailureTest {

	private static final List<String> ALL_REASON_KEYS = List.of(
			HotApplyFailure.REASON_IO,
			HotApplyFailure.REASON_PACKAGE_BROKEN,
			HotApplyFailure.REASON_OUT_OF_MEMORY,
			HotApplyFailure.REASON_UNKNOWN);

	@Test
	public void ioErrorBecomesTheIoReasonKey() {
		assertEquals(HotApplyFailure.reasonKey(new IOException("Datei gesperrt")), HotApplyFailure.REASON_IO);
		assertEquals(HotApplyFailure.reasonKey(new UncheckedIOException(new IOException("Platte voll"))),
				HotApplyFailure.REASON_IO);
	}

	@Test
	public void brokenArchiveAndBrokenManifestBecomeThePackageBrokenReasonKey() {
		assertEquals(HotApplyFailure.reasonKey(new ZipException("central directory kaputt")),
				HotApplyFailure.REASON_PACKAGE_BROKEN);
		// Echte, vom Produktionsparser geworfene Ablehnung -- der Konstruktor
		// von ManifestParseException ist bewusst paketprivat.
		ManifestParseException parseFailure = expectThrows(ManifestParseException.class,
				() -> ManifestParser.parse("kein json".getBytes(StandardCharsets.UTF_8)));
		assertEquals(HotApplyFailure.reasonKey(parseFailure), HotApplyFailure.REASON_PACKAGE_BROKEN);
	}

	/** ZipException ist eine IOException -- die speziellere Einstufung muss gewinnen. */
	@Test
	public void brokenArchiveBeatsTheGenericIoClassification() {
		assertNotEquals(HotApplyFailure.reasonKey(new ZipException("kaputt")), HotApplyFailure.REASON_IO);
	}

	@Test
	public void outOfMemoryBecomesTheMemoryReasonKey() {
		assertEquals(HotApplyFailure.reasonKey(new OutOfMemoryError("Java heap space")),
				HotApplyFailure.REASON_OUT_OF_MEMORY);
	}

	@Test
	public void anythingElseBecomesTheUnknownReasonKey() {
		assertEquals(HotApplyFailure.reasonKey(new RuntimeException("Ressourcen-Manager kaputt")),
				HotApplyFailure.REASON_UNKNOWN);
		assertEquals(HotApplyFailure.reasonKey(new NullPointerException()), HotApplyFailure.REASON_UNKNOWN);
		assertEquals(HotApplyFailure.reasonKey(null), HotApplyFailure.REASON_UNKNOWN);
	}

	@Test
	public void transportWrappersAreUnwrappedBeforeClassifying() {
		assertEquals(HotApplyFailure.reasonKey(new CompletionException(new ZipException("kaputt"))),
				HotApplyFailure.REASON_PACKAGE_BROKEN);
		assertEquals(HotApplyFailure.reasonKey(new ExecutionException(new IOException("weg"))),
				HotApplyFailure.REASON_IO);
		assertEquals(HotApplyFailure.reasonKey(new CompletionException(new CompletionException(
				new OutOfMemoryError("heap")))), HotApplyFailure.REASON_OUT_OF_MEMORY);
	}

	/** Das Kernversprechen von GATE11: nirgends ein Klassenname im sichtbaren Weg. */
	@Test
	public void noReasonKeyEverCarriesAJavaClassName() {
		List<Throwable> failures = List.of(
				new NullPointerException(),
				new IllegalStateException(),
				new ZipException("x"),
				new IOException(),
				new OutOfMemoryError());
		for (Throwable failure : failures) {
			String key = HotApplyFailure.reasonKey(failure);
			assertTrue(ALL_REASON_KEYS.contains(key), "unerwarteter Key: " + key);
			for (String locale : List.of("de_de", "en_us")) {
				String visible = Translations.get(locale, key);
				assertNotEquals(visible, key, locale + ": '" + key + "' hat keinen uebersetzten Text");
				assertFalse(visible.contains(failure.getClass().getSimpleName()),
						locale + ": der sichtbare Satz nennt den Klassennamen '"
								+ failure.getClass().getSimpleName() + "'");
				assertFalse(visible.contains("Exception") || visible.contains("Error"),
						locale + ": der sichtbare Satz klingt nach Java-Exception: " + visible);
			}
		}
	}

	/** Der Klassenname gehoert genau hierher -- ins Log, nicht in die Oberflaeche. */
	@Test
	public void technicalDetailKeepsTheClassNameForTheLog() {
		assertTrue(HotApplyFailure.technicalDetail(new NullPointerException())
				.contains("java.lang.NullPointerException"));
		String withMessage = HotApplyFailure.technicalDetail(new IOException("Platte voll"));
		assertTrue(withMessage.contains("java.io.IOException"));
		assertTrue(withMessage.contains("Platte voll"));
	}

	@Test
	public void unwrapReturnsTheRealCauseAndSurvivesSelfReferencingChains() {
		IOException real = new IOException("echt");
		assertEquals(HotApplyFailure.unwrap(new CompletionException(real)), real);
		assertEquals(HotApplyFailure.unwrap(real), real);
		assertEquals(HotApplyFailure.unwrap(null), null);
	}
}
