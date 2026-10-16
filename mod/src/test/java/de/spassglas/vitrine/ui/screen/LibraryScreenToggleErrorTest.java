package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.pack.PackInstallException;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.ui.library.LibraryEntry;
import de.spassglas.vitrine.ui.library.LibraryModel;
import de.spassglas.vitrine.ui.tile.TilePackageType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE49-Beweis: vorher fing {@code LibraryScreen#onToggleActive}
 * jeden {@link PackInstallException} nur mit einem Log-Eintrag ab (als
 * {@code RuntimeException}) und baute die Liste trotzdem neu auf -- der
 * Aktiv-Schalter sprang optisch kommentarlos zurueck, ohne je einen Grund zu
 * nennen (kein Treffer fuer "Fehler" in der gesamten Datei). Dieser Test
 * treibt {@link LibraryScreen#onToggleActive} gegen ein {@link LibraryModel},
 * dessen {@link LibraryModel.ActiveSetter} eine echte {@link
 * PackInstallException} wirft (wie es {@code PackInstaller#setActive} bei
 * einer abgelehnten Aktivierung tut), und beweist, dass der Bildschirm
 * danach einen sichtbaren Klartextfehler traegt (gleiches Feld-Muster wie
 * {@code PackageDetailScreen#installError}, GATE34/48).
 */
public final class LibraryScreenToggleErrorTest {

	private Path vitrineConfigDir;

	@BeforeMethod
	public void setUp() throws IOException {
		vitrineConfigDir = Files.createTempDirectory("vitrine-libraryscreen-toggleerror-test-");
	}

	@AfterMethod
	public void tearDown() throws IOException {
		if (vitrineConfigDir == null || !Files.exists(vitrineConfigDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(vitrineConfigDir)) {
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
	public void failedToggleShowsVisiblePlainTextErrorInsteadOfSilentlySnappingBack() {
		LibraryModel model = new LibraryModel(vitrineConfigDir, VitrinePackProvider.forConfigDir(vitrineConfigDir),
				affectedPackIds -> {
				},
				(packId, active) -> {
					throw new PackInstallException(
							"roh: /sollte/nie/sichtbar/sein -- java.lang.RuntimeException: geheim",
							"vitrine.detail.error.generic", "BoomException");
				},
				packId -> {
				});
		LibraryScreen screen = new LibraryScreen(null, model);
		LibraryEntry entry = new LibraryEntry("vitrine/beispiel.paket", "beispiel.paket", "Beispielpaket",
				"Autorin", "1.0.0", TilePackageType.TEXTUREN, false, 0, List.of());

		assertTrue(screen.libraryError().isEmpty(), "vor einem Versuch darf kein Fehlerzustand sichtbar sein");

		screen.onToggleActive(entry, true);

		assertTrue(screen.libraryError().isPresent(),
				"ein gescheitertes An/Aus muss einen sichtbaren Klartextfehler setzen, statt nur zu loggen und "
						+ "die Liste still neu aufzubauen (vorher: catch (RuntimeException) + LOGGER.error, kein "
						+ "sichtbarer Text)");
		String shown = screen.libraryError().get();
		assertFalse(shown.isBlank());
		assertTrue(shown.contains("Beispielpaket"), "der Satz muss nennen, welches Paket betroffen ist: " + shown);
		assertFalse(shown.contains("/sollte/nie/sichtbar/sein"),
				"der sichtbare Text darf nicht die rohe Protokoll-Meldung/Pfadangabe der Ausnahme enthalten "
						+ "(GATE48): " + shown);
		assertFalse(shown.contains("RuntimeException"),
				"der sichtbare Text darf keine rohe Throwable-Fassung enthalten: " + shown);

		String expectedReason = Translations.format(Translations.FALLBACK_LOCALE, "vitrine.detail.error.generic",
				"BoomException");
		String expected = Translations.format(Translations.FALLBACK_LOCALE, "vitrine.library.toggle_error",
				"Beispielpaket", expectedReason);
		assertEquals(shown, expected);
	}
}
