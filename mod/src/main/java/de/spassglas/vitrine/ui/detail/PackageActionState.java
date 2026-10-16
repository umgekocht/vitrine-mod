package de.spassglas.vitrine.ui.detail;

import de.spassglas.vitrine.i18n.Translations;
import de.spassglas.vitrine.ui.tile.TileStatus;

import java.util.Objects;

/**
 * Der Zustand des einen gefuellten Aktionsknopfs auf der Store-Detailseite
 * (ROADMAP S3: "Aktionsknopf mit allen Zuständen aus der Spec (mind.:
 * Installieren / Installiert / Aktualisieren / „Neustart nötig" / arbeitet… /
 * Fehler)").
 *
 * <p>Der Zustand wird aus einem Modell <em>abgeleitet</em>
 * ({@link #derive(TileStatus, PackageInstallPhase)}) -- aus dem
 * Installationszustand der Kachel ({@link TileStatus}) und dem Fortschritt
 * eines echten Vorgangs ({@link PackageInstallPhase}). Kein Fake-Fortschritt,
 * kein Timer (Charter-Regel 2). In S3 aendert der Knopf nur Text/Zustand --
 * das tatsaechliche Installieren ist S4.</p>
 */
public enum PackageActionState {

	/** Noch nicht installiert -> "Installieren". */
	INSTALL("vitrine.action.install"),

	/** Installiert und aktuell -> "Installiert" (S4 wird daraus "Öffnen"/"Aktivieren" plus Menü). */
	INSTALLED("vitrine.tile.status.installed"),

	/** Installiert, aber eine neuere Version liegt vor -> "Aktualisieren". */
	UPDATE("vitrine.action.update"),

	/** Installiert, wird aber erst nach einem Neustart aktiv (Klasse B) -> "Neustart nötig". */
	RESTART_REQUIRED("vitrine.restart.required"),

	/** Ein echter Vorgang laeuft gerade -> "arbeitet …", Knopf nicht klickbar. */
	WORKING("vitrine.detail.action.working"),

	/** Der letzte Vorgang ist gescheitert -> "Fehler". */
	ERROR("vitrine.detail.action.error");

	private final String translationKey;

	PackageActionState(String translationKey) {
		this.translationKey = translationKey;
	}

	/** Uebersetzungs-Key der Knopfbeschriftung. */
	public String translationKey() {
		return translationKey;
	}

	/** Klartext der Knopfbeschriftung in {@code locale} (fuer Tests/Diagnose, siehe {@link Translations}). */
	public String describe(String locale) {
		return Translations.get(locale, translationKey);
	}

	/**
	 * Leitet den Knopfzustand ab. Ein laufender oder gescheiterter Vorgang
	 * ({@code phase}) gewinnt immer gegen den ruhenden Installationszustand
	 * ({@code status}) -- er sagt aus, was gerade wirklich passiert.
	 */
	public static PackageActionState derive(TileStatus status, PackageInstallPhase phase) {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(phase, "phase");
		switch (phase) {
			case FAILED:
				return ERROR;
			case WORKING:
				return WORKING;
			case IDLE:
			default:
				break;
		}
		return switch (status) {
			case NONE -> INSTALL;
			case INSTALLED -> INSTALLED;
			case UPDATE_AVAILABLE -> UPDATE;
			case RESTART_REQUIRED -> RESTART_REQUIRED;
		};
	}
}
