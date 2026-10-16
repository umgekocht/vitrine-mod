package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.classb.StagedMod;

import java.util.List;
import java.util.Objects;

/**
 * ROADMAP J2 -- client-freies Modell des "Neustart noetig"-Bandes fuer
 * {@code StoreScreen}/{@code LibraryScreen}: wie viele Klasse-B-Jars auf einen
 * Neustart warten, samt Autorenanzeige je Eintrag. Alle Texte laufen ueber
 * Lang-Keys ({@code vitrine.classb.*}, in beiden Sprachen), es steckt kein
 * Anzeigetext in dieser Klasse.
 *
 * <p>Das Band ist <b>immer</b> ein "Neustart noetig"-Band -- Klasse B ist nie
 * "hot" (Charter-Regel 2). Es ist sichtbar, solange
 * {@link de.spassglas.vitrine.classb.JavaModStager#pendingRestarts()} nicht leer
 * ist. Der Konstruktor nimmt die volle gestagete Liste und zaehlt selbst nur
 * die noch ausstehenden ({@link StagedMod#pendingRestart()}) -- bereits
 * geladene Jars gehoeren nicht mehr aufs Band.</p>
 */
public final class RestartRequiredBanner {

	/** Bandtitel, z. B. "Neustart noetig". */
	public static final String TITLE_KEY = "vitrine.classb.restart_banner.title";
	/** Bandtext mit Platzhalter fuer die Anzahl, z. B. "%s Java-Mod(s) warten auf einen Neustart." */
	public static final String MESSAGE_KEY = "vitrine.classb.restart_banner.message";
	/** Sammelknopf, z. B. "Jetzt neu starten". */
	public static final String RESTART_ALL_KEY = "vitrine.classb.restart_banner.restart_all";
	/** Knopf je Eintrag, z. B. "Zuruecknehmen". */
	public static final String TAKE_BACK_KEY = "vitrine.classb.take_back";
	/** ROADMAP GATE50: Klartext-Fehler mit Platzhalter fuer den Grund, wenn "Zuruecknehmen" scheitert. */
	public static final String TAKE_BACK_ERROR_KEY = "vitrine.classb.take_back_error";
	/** Autorenanzeige je Eintrag mit Platzhalter, z. B. "von %s". */
	public static final String AUTHOR_KEY = "vitrine.classb.author";
	/** Klartextwarnung beim Bestaetigen einer Klasse-B-Installation. */
	public static final String INSTALL_WARNING_KEY = "vitrine.classb.install_warning";

	private final List<StagedMod> pending;

	/**
	 * @param stagedMods die volle gestagete Liste (auch bereits geladene) -- das
	 *                   Band filtert selbst auf {@link StagedMod#pendingRestart()}.
	 */
	public RestartRequiredBanner(List<StagedMod> stagedMods) {
		Objects.requireNonNull(stagedMods, "stagedMods");
		this.pending = stagedMods.stream().filter(StagedMod::pendingRestart).toList();
	}

	/** Band sichtbar? Genau dann, wenn mindestens eine Jar auf einen Neustart wartet. */
	public boolean isVisible() {
		return !pending.isEmpty();
	}

	/** Anzahl der noch auf einen Neustart wartenden Jars (fuer {@link #MESSAGE_KEY}). */
	public int pendingCount() {
		return pending.size();
	}

	/** Die ausstehenden Eintraege in Reihenfolge -- je Eintrag Anzeigename, Autor, "Zuruecknehmen". */
	public List<StagedMod> entries() {
		return pending;
	}
}
