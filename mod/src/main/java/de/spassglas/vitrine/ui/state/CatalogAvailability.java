package de.spassglas.vitrine.ui.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Prozess-Halter fuer die Verfuegbarkeit des Store-Katalogbereichs (ROADMAP
 * S7). Der echte Katalog-Client (K2) und der Offline-Modus (K4,
 * {@code ui.catalog.StoreCatalogController}) melden ueber
 * {@link #report(State, LocalDate)} den tatsaechlichen Zustand; solange kein
 * Abruf lief, ist der Standard {@link State#READY} und der Store zeigt seine
 * Kacheln, nichts wird vorgegaukelt (Charter-Regel 2).
 *
 * <p>Die Abbildung Zustand -&gt; {@link StoreStateView}
 * ({@link #stateViewFor(State, LocalDate)}) ist eine reine Funktion und ohne
 * laufenden Client testbar; nur der Prozess-Halter ({@link #report}/
 * {@link #reset}) ist veraenderlich.</p>
 */
public final class CatalogAvailability {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/CatalogAvailability");

	/** Zustand des Katalogbereichs. */
	public enum State {
		/** Katalog verfuegbar -- der Inhaltsbereich zeigt Kacheln, kein Zustands-Panel. */
		READY,
		/** Katalog wird geladen -- Lade-Skeleton. */
		LOADING,
		/** Kein Netz -- Cache-Ansicht, "Stand von &lt;Datum&gt;". */
		OFFLINE,
		/**
		 * Kein Netz <em>und</em> kein Katalog-Cache (ROADMAP K4): der Store zeigt
		 * nur lokale/installierte Pakete, ohne ein "Stand von"-Datum -- es gibt
		 * schlicht keinen ehrlichen Stand (Charter-Regel 2).
		 */
		OFFLINE_NO_CACHE,
		/** Katalogdatei beschaedigt/unvollstaendig, und es gibt keinen Cache-Stand. */
		CATALOG_BROKEN,
		/**
		 * Katalogdatei beschaedigt, aber ein frueher geladener Stand liegt im
		 * Cache (GATE40): der Store zeigt diesen Stand mit Datum, statt wegen
		 * einer kaputten Datei auf dem Server ganz leer zu bleiben. Ehrlich
		 * beschriftet -- "kaputt, du siehst den Stand von &lt;Datum&gt;" --, nicht
		 * als "kein Netz" getarnt (Charter-Regel 2).
		 */
		CATALOG_BROKEN_CACHED
	}

	private static volatile State current = State.READY;

	/**
	 * Stand des zuletzt geladenen Katalog-Cache, wie ihn K4 aus der
	 * Cache-Datei-Aenderungszeit meldet. Bis der erste {@link #report} laeuft,
	 * ist das der Tag des Klassen-Ladens -- ein ehrlicher Platzhalter, kein
	 * erfundenes Datum; er wird nur bei {@link State#OFFLINE} ueberhaupt
	 * angezeigt (und dann von K4 zuvor gesetzt).
	 */
	private static volatile LocalDate cacheAsOf = LocalDate.now();

	private CatalogAvailability() {
	}

	/** Meldet einen neuen Katalog-Zustand (K2/K4). {@code cacheAsOf} darf {@code null} sein -> unveraendert. */
	public static void report(State state, LocalDate newCacheAsOf) {
		current = Objects.requireNonNull(state, "state");
		if (newCacheAsOf != null) {
			cacheAsOf = newCacheAsOf;
		}
		LOGGER.info("Katalog-Verfuegbarkeit: {} (Cache-Stand {})", current, cacheAsOf);
	}

	/** Setzt auf {@link State#READY} zurueck (Tests, Store-Neustart). */
	public static void reset() {
		current = State.READY;
	}

	public static State current() {
		return current;
	}

	public static LocalDate cacheAsOf() {
		return cacheAsOf;
	}

	/** Der aktuell anzuzeigende Zustand des Katalogbereichs, oder {@link Optional#empty()} bei {@link State#READY}. */
	public static Optional<StoreStateView> currentStateView() {
		return stateViewFor(current, cacheAsOf);
	}

	/**
	 * Reine Abbildung Zustand -&gt; Zustands-Panel. {@link State#READY} hat kein
	 * Panel (der Inhaltsbereich zeigt Kacheln). {@code cacheAsOf} wird nur bei
	 * {@link State#OFFLINE} gebraucht und darf dort nicht {@code null} sein.
	 */
	public static Optional<StoreStateView> stateViewFor(State state, LocalDate cacheAsOf) {
		Objects.requireNonNull(state, "state");
		return switch (state) {
			case READY -> Optional.empty();
			case LOADING -> Optional.of(StoreStateView.loading());
			case OFFLINE -> Optional.of(StoreStateView.offline(
					Objects.requireNonNull(cacheAsOf, "cacheAsOf fuer OFFLINE")));
			case OFFLINE_NO_CACHE -> Optional.of(StoreStateView.offlineNoCache());
			case CATALOG_BROKEN -> Optional.of(StoreStateView.catalogBroken());
			case CATALOG_BROKEN_CACHED -> Optional.of(StoreStateView.catalogBrokenCached(
					Objects.requireNonNull(cacheAsOf, "cacheAsOf fuer CATALOG_BROKEN_CACHED")));
		};
	}
}
