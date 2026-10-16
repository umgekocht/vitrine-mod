package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.classb.InstalledModConflictCheck;
import de.spassglas.vitrine.classb.JarSource;
import de.spassglas.vitrine.classb.JavaModOffer;
import de.spassglas.vitrine.classb.JavaModStager;
import de.spassglas.vitrine.classb.JavaModStagingException;
import de.spassglas.vitrine.classb.StagedMod;
import de.spassglas.vitrine.fixtures.VpkgFixtures;
import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.vpkg.SigningKeyTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * ROADMAP GATE50-Beweis: vorher fing {@link RestartRequiredBannerWidget#takeBack}
 * jede {@code JavaModStagingException} nur mit {@code LOGGER.warn} ab und rief
 * danach trotzdem {@code onChanged.run()} -- der "Zuruecknehmen"-Knopf wirkte
 * optisch wirkungslos, ohne je zu sagen, warum. Dieser Test treibt einen
 * echten {@link JavaModStager} (kein Fake der Ausnahme selbst) in genau die
 * Lage, die {@code JavaModStagerTest#takeBackAfterRestartIsRefused...} bereits
 * fuer den Kern beweist (eine bereits als geladen markierte Jar lehnt die
 * Ruecknahme ab) und beweist, dass {@link RestartRequiredBannerWidget} diesen
 * Fehlschlag jetzt sichtbar macht, statt ihn stumm zu verschlucken.
 */
public final class RestartRequiredBannerWidgetTest {

	private Path gameDir;
	private KeyPair trustedKeyPair;

	@BeforeMethod
	public void setUp() throws IOException {
		gameDir = Files.createTempDirectory("vitrine-restartbanner-widget-test-");
		trustedKeyPair = VpkgFixtures.generateEd25519KeyPair();
		SigningKeyTestSupport.install(trustedKeyPair.getPublic());
	}

	@AfterMethod
	public void tearDown() throws IOException {
		SigningKeyTestSupport.reset();
		if (gameDir == null || !Files.exists(gameDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(gameDir)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	@Test
	public void failedTakeBackShowsPlainTextErrorInsteadOfSilentRebuild() throws Exception {
		byte[] jarBytes = "jar-fuer-gate50".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = JavaModStager.forGameDir(gameDir, new FixedJarSource(jarBytes), () -> {
		}, InstalledModConflictCheck.NONE);
		JavaModOffer offer = signedOffer("de.autor.gate50", "gate50-1.0.0.jar", jarBytes, trustedKeyPair);
		stager.stage(offer);

		RestartRequiredBannerWidget widget = new RestartRequiredBannerWidget(stager);
		assertTrue(widget.isVisible(), "eine frisch gestagete Jar macht das Band sichtbar");
		assertNull(widget.takeBackError(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");
		int heightBeforeFailure = widget.reservedHeight();

		StagedMod snapshotEntry = stager.pendingRestarts().get(0);
		// Zwischenzeitlich (z. B. echter Neustart) ist die Jar schon geladen --
		// die Momentaufnahme im Band weiss davon noch nichts, der naechste
		// "Zuruecknehmen"-Versuch muss dennoch real scheitern.
		stager.markAllPendingAsLoaded();

		AtomicInteger onChangedCalls = new AtomicInteger();
		widget.takeBack(snapshotEntry, onChangedCalls::incrementAndGet);

		assertNotNull(widget.takeBackError(),
				"ein gescheitertes 'Zuruecknehmen' muss einen sichtbaren Klartextfehler setzen, statt nur zu "
						+ "loggen (vorher: nur LOGGER.warn + onChanged.run())");
		assertFalse(widget.takeBackError().isBlank());
		assertEquals(onChangedCalls.get(), 0,
				"bei einem Fehlschlag darf der Bildschirm NICHT neu gebaut werden -- das wuerfe diese "
						+ "Widget-Instanz und damit den gerade gesetzten Fehler sofort wieder weg");
		assertTrue(widget.reservedHeight() > heightBeforeFailure,
				"das Band muss nach einem Fehlschlag zusaetzlichen Platz fuer die Fehlerzeile reservieren");
	}

	/**
	 * ROADMAP GATE71-Fertig-Kriterium: der "Papierkorb"-Fehlerfall -- die
	 * gestagete Jar laesst sich beim Zuruecknehmen nicht mehr in den
	 * Papierkorb verschieben (hier simuliert, indem eine gewoehnliche Datei
	 * den Papierkorb-Pfad blockiert, wie es z. B. eine bereits verschobene/
	 * gesperrte Datei taete). Vorher landete {@code e.getMessage()} (voller
	 * Dateipfad, Java-Klassenname) direkt im sichtbaren Text; jetzt laeuft der
	 * Grund strukturiert ueber {@link JavaModStagingException#displayKey()}
	 * durch {@code lang/*.json}.
	 */
	@Test
	public void failedTakeBackBecauseTrashIsBlockedShowsTranslatedTextWithoutPathOrClassName() throws Exception {
		byte[] jarBytes = "jar-papierkorb-blockiert".getBytes(StandardCharsets.UTF_8);
		Path modsDir = gameDir.resolve("mods");
		Path vitrineDir = gameDir.resolve("config").resolve("vitrine");
		Path trashDir = vitrineDir.resolve("trash");
		JavaModStager stager = JavaModStager.forGameDir(gameDir, new FixedJarSource(jarBytes), () -> {
		}, InstalledModConflictCheck.NONE);
		JavaModOffer offer = signedOffer("de.autor.papierkorb", "papierkorb-1.0.0.jar", jarBytes, trustedKeyPair);
		stager.stage(offer);
		assertTrue(Files.exists(modsDir.resolve("papierkorb-1.0.0.jar")));

		// Den Papierkorb-Pfad mit einer gewoehnlichen Datei blockieren --
		// JavaModStager#moveToTrash kann dort dann keinen Ordner anlegen.
		Files.createDirectories(vitrineDir);
		Files.write(trashDir, "blockiert".getBytes(StandardCharsets.UTF_8));

		RestartRequiredBannerWidget widget = new RestartRequiredBannerWidget(stager);
		StagedMod entry = stager.pendingRestarts().get(0);

		AtomicInteger onChangedCalls = new AtomicInteger();
		widget.takeBack(entry, onChangedCalls::incrementAndGet);

		String shown = widget.takeBackError();
		assertNotNull(shown, "ein durch den blockierten Papierkorb gescheitertes 'Zuruecknehmen' muss einen "
				+ "sichtbaren Fehler setzen");
		assertFalse(shown.isBlank());
		assertEquals(onChangedCalls.get(), 0, "bei einem Fehlschlag darf der Bildschirm nicht neu gebaut werden");
		assertFalse(shown.contains(gameDir.toString()), "der sichtbare Text darf keinen Dateipfad enthalten");
		assertFalse(shown.contains(trashDir.toString()), "der sichtbare Text darf keinen Dateipfad enthalten");
		assertFalse(shown.toLowerCase(Locale.ROOT).contains("exception"),
				"der sichtbare Text darf keinen Java-Klassennamen enthalten");

		// Dieselbe strukturierte Ursache tatsaechlich in beiden Locales
		// uebersetzt (nicht nur im Fallback, den der Widget-Pfad kopffrei
		// nutzt) -- beweist, dass die Uebersetzung ueber lang/*.json laeuft,
		// nicht ueber e.getMessage().
		JavaModStagingException ex = expectThrows(JavaModStagingException.class, () -> stager.unstage(entry.id()));
		assertEquals(ex.displayKey(), "vitrine.classb.error.trash_move_failed");
		String en = Translations.format("en_us", ex.displayKey(), ex.displayArgs());
		String de = Translations.format("de_de", ex.displayKey(), ex.displayArgs());
		assertFalse(en.equals(ex.displayKey()), "en_us muss den Schluessel uebersetzen, nicht den rohen Key liefern");
		assertFalse(de.equals(ex.displayKey()), "de_de muss den Schluessel uebersetzen, nicht den rohen Key liefern");
		assertFalse(en.equals(de), "beide Locales muessen tatsaechlich unterschiedlichen Text liefern");
		assertFalse(en.contains(gameDir.toString()));
		assertFalse(de.contains(gameDir.toString()));
		assertFalse(en.toLowerCase(Locale.ROOT).contains("exception"));
		assertFalse(de.toLowerCase(Locale.ROOT).contains("exception"));
	}

	@Test
	public void successfulTakeBackClearsAnyPriorErrorAndRebuilds() throws Exception {
		byte[] jarBytes = "jar-fuer-gate50-erfolg".getBytes(StandardCharsets.UTF_8);
		JavaModStager stager = JavaModStager.forGameDir(gameDir, new FixedJarSource(jarBytes), () -> {
		}, InstalledModConflictCheck.NONE);
		stager.stage(signedOffer("de.autor.gate50b", "gate50b-1.0.0.jar", jarBytes, trustedKeyPair));

		RestartRequiredBannerWidget widget = new RestartRequiredBannerWidget(stager);
		StagedMod entry = stager.pendingRestarts().get(0);

		AtomicInteger onChangedCalls = new AtomicInteger();
		widget.takeBack(entry, onChangedCalls::incrementAndGet);

		assertNull(widget.takeBackError(), "eine erfolgreiche Ruecknahme darf keinen Fehler hinterlassen");
		assertEquals(onChangedCalls.get(), 1, "eine erfolgreiche Ruecknahme baut den Bildschirm wie bisher neu auf");
	}

	private static JavaModOffer signedOffer(String id, String fileName, byte[] jarBytes, KeyPair signer) {
		byte[] signature = VpkgFixtures.signSha256Digest(jarBytes, signer.getPrivate());
		return JavaModOffer.signed(id, id, "Autorin", fileName, VpkgFixtures.sha256Hex(jarBytes), signature,
				List.of());
	}

	private static final class FixedJarSource implements JarSource {
		private final byte[] content;

		FixedJarSource(byte[] content) {
			this.content = content.clone();
		}

		@Override
		public Path fetch(JavaModOffer offer, Path tempDir) throws IOException {
			Path out = tempDir.resolve(offer.fileName());
			Files.write(out, content);
			return out;
		}
	}
}
