package de.spassglas.vitrine.ui.classb;

import de.spassglas.vitrine.config.SettingsModel;
import de.spassglas.vitrine.ui.tile.TileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * ROADMAP J1: Sichtbarkeits-Schleuse fuer Klasse-B-Angebote ({@code .jar}-Mods,
 * "Neustart noetig", nie hot -- Charter-Regel 2). Solange der Opt-in-Schalter
 * "Java-Mods anzeigen" ({@code config/vitrine/settings.json},
 * {@code showJavaModOffers}, Default aus) nicht gesetzt ist, entfernt
 * {@link #apply(List)} jeden Eintrag mit {@link TileData#javaMod()} aus einer
 * Angebotsliste. Damit taucht ohne Opt-in an keiner Store-Stelle ein Jar auf --
 * Katalogreihen ({@code StoreCatalogController}), Suchergebnisse und
 * Vorschlaege ({@code StoreSearchController}) sowie jede daraus gespeiste
 * Verlinkung.
 *
 * <p>Client-frei: der {@link BooleanSupplier}-Seam macht die Schleuse ohne
 * laufenden Client testbar (Muster wie {@link SettingsModel}). Die
 * Produktions-Fabrik {@link #forConfigDir(Path)} liest den Schalter bei jedem
 * Aufruf frisch von der Platte, damit ein anderswo umgelegter Schalter ohne
 * Neustart des Stores wirkt.</p>
 *
 * <p>Woher ein Angebot weiss, dass es Klasse B ist: heute ausschliesslich aus
 * dem mod-eigenen Flag {@link TileData#javaMod()} bzw.
 * {@code IndexPackage#javaMod()}. Das Registry-Schema kennt bislang nur die
 * sieben Klasse-A-Kategorien; die passende Schema-Erweiterung (ein
 * {@code contentClass}-Feld o. ae.) ist eine spaetere {@code coder-tooling}-
 * Zeile. Bis dahin ist das Flag ein bewusst konservativer Seam: fehlt die
 * Angabe, gilt ein Angebot als Klasse A.</p>
 */
public final class JavaModOfferGate {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/ClassB");

	private final BooleanSupplier optIn;

	public JavaModOfferGate(BooleanSupplier optIn) {
		this.optIn = Objects.requireNonNull(optIn, "optIn");
	}

	/**
	 * Schleuse, die den Opt-in-Schalter bei jedem Aufruf frisch aus
	 * {@code settings.json} im angegebenen Vitrine-Konfigordner liest.
	 */
	public static JavaModOfferGate forConfigDir(Path vitrineConfigDir) {
		Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir");
		return new JavaModOfferGate(() -> SettingsModel.showJavaModOffers(vitrineConfigDir));
	}

	/** Schleuse, die Klasse-B-Angebote immer ausblendet -- der sichere Default ohne Konfigordner. */
	public static JavaModOfferGate hidden() {
		return new JavaModOfferGate(() -> false);
	}

	/** Ob Klasse-B-Angebote derzeit gezeigt werden duerfen (Opt-in-Schalter an). */
	public boolean showsJavaModOffers() {
		return optIn.getAsBoolean();
	}

	/**
	 * Liefert die Angebotsliste ohne Klasse-B-Eintraege, solange der Opt-in-
	 * Schalter aus ist; bei aktivem Schalter unveraendert (als unveraenderliche
	 * Kopie, Reihenfolge erhalten). Ausgeblendete Eintraege werden gezaehlt
	 * geloggt, nie stumm verschluckt.
	 */
	public List<TileData> apply(List<TileData> offers) {
		Objects.requireNonNull(offers, "offers");
		if (optIn.getAsBoolean()) {
			return List.copyOf(offers);
		}
		List<TileData> visible = new ArrayList<>(offers.size());
		int hidden = 0;
		for (TileData offer : offers) {
			if (offer.javaMod()) {
				hidden++;
			} else {
				visible.add(offer);
			}
		}
		if (hidden > 0) {
			LOGGER.debug("{} Klasse-B-Angebot(e) ausgeblendet -- Opt-in \"Java-Mods anzeigen\" ist aus.", hidden);
		}
		return List.copyOf(visible);
	}
}
